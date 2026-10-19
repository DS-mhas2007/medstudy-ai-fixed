package com.example.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** What came back from one generateContent call. */
sealed interface GeminiReply {
    data class Text(val text: String, val truncated: Boolean = false) : GeminiReply
    data class Blocked(val reason: String) : GeminiReply
    data object Empty : GeminiReply
}

/**
 * Pure helpers for the Gemini REST protocol: request body, response extraction and human readable
 * (Arabic) errors. Networking lives in MedicalAiService; everything testable lives here.
 */
object GeminiProtocol {
    const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private val modelPattern = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$""")
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isValidModelName(model: String): Boolean = modelPattern.matches(model.trim())

    fun endpoint(model: String, baseUrl: String = DEFAULT_BASE_URL): String =
        "$baseUrl/models/${model.trim()}:generateContent"

    fun buildRequestBody(
        systemInstruction: String,
        userContent: String,
        temperature: Double = 0.3,
        jsonMode: Boolean = true,
        maxOutputTokens: Int = 8192
    ): String {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", systemInstruction) }) }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", userContent) }) }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", maxOutputTokens)
                if (jsonMode) put("responseMimeType", "application/json")
            }
            // Medical vocabulary (anatomy, pharmacology, toxicology) trips the default filters; keep only the
            // high-confidence blocks.
            putJsonArray("safetySettings") {
                for (category in listOf(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT"
                )) {
                    add(buildJsonObject {
                        put("category", category)
                        put("threshold", "BLOCK_ONLY_HIGH")
                    })
                }
            }
        }
        return body.toString()
    }

    fun parseResponse(body: String): GeminiReply {
        val root = try {
            lenient.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return GeminiReply.Empty

        val blockReason = ((root["promptFeedback"] as? JsonObject)?.get("blockReason") as? JsonPrimitive)?.contentOrNull
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
        if (candidate == null) {
            return if (blockReason != null) GeminiReply.Blocked(blockReason) else GeminiReply.Empty
        }
        val finish = (candidate["finishReason"] as? JsonPrimitive)?.contentOrNull
        val parts = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray) ?: JsonArray(emptyList())
        val text = parts.mapNotNull { part ->
            val obj = part as? JsonObject ?: return@mapNotNull null
            val isThought = (obj["thought"] as? JsonPrimitive)?.booleanOrNull == true
            if (isThought) null else (obj["text"] as? JsonPrimitive)?.contentOrNull
        }.joinToString("")

        if (text.isBlank()) {
            return when {
                finish == "SAFETY" || finish == "RECITATION" || finish == "PROHIBITED_CONTENT" ->
                    GeminiReply.Blocked(finish)
                blockReason != null -> GeminiReply.Blocked(blockReason)
                else -> GeminiReply.Empty
            }
        }
        return GeminiReply.Text(text, truncated = finish == "MAX_TOKENS")
    }

    /** Seconds in a Retry-After header as milliseconds (HTTP dates are ignored), capped at [maxMillis]. */
    fun parseRetryAfter(header: String?, maxMillis: Long = 30_000L): Long? {
        val seconds = header?.trim()?.toLongOrNull() ?: return null
        if (seconds < 0) return null
        return (seconds * 1000).coerceAtMost(maxMillis)
    }

    fun isRetryable(httpCode: Int): Boolean = httpCode == 429 || httpCode == 500 || httpCode == 502 || httpCode == 503 || httpCode == 504

    /** Arabic, user facing explanation of an HTTP failure. Never echoes the API key or the raw body. */
    fun errorMessage(httpCode: Int, body: String): String {
        val apiMessage = try {
            val err = (lenient.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject
            (err?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
        } catch (e: Exception) {
            ""
        }
        val lower = apiMessage.lowercase()
        return when {
            httpCode == 400 && ("api key" in lower || "api_key" in lower) ->
                "مفتاح Gemini غير صالح. تأكد من نسخه كاملاً من Google AI Studio."
            httpCode == 400 && "location" in lower ->
                "الخدمة غير متاحة في منطقتك الحالية."
            httpCode == 400 ->
                "الطلب غير مقبول (400). تحقق من اسم النموذج في الإعدادات."
            httpCode == 401 || httpCode == 403 ->
                "المفتاح غير مصرّح له باستخدام هذا النموذج. تحقق من صلاحيات المفتاح."
            httpCode == 404 ->
                "النموذج المحدد غير موجود. غيّر اسم النموذج في الإعدادات."
            httpCode == 429 ->
                "تم تجاوز حصة الاستخدام المجانية مؤقتاً. انتظر قليلاً ثم حاول مرة أخرى."
            httpCode >= 500 ->
                "خادم Gemini مشغول حالياً. حاول بعد لحظات."
            else -> "تعذر إكمال الطلب (رمز $httpCode)."
        }
    }

    fun blockedMessage(reason: String): String =
        "رفض النموذج توليد الرد لأسباب تتعلق بالسلامة ($reason). جرّب صياغة النص بشكل مختلف."
}
