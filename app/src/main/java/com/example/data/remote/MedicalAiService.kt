package com.example.data.remote

import com.example.BuildConfig
import com.example.core.AiPrompt
import com.example.core.AiPrompts
import com.example.core.AiCacheKey
import com.example.core.AiResponseParser
import com.example.core.GeminiProtocol
import com.example.core.GeminiReply
import com.example.core.ParseOutcome
import com.example.core.PipelineResult
import com.example.core.RequestPipeline
import com.example.core.StudySection
import com.example.data.model.ExplanationMode
import com.example.data.model.MedicalSpecialty
import com.example.data.model.TranslationDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Everything the service needs to know about the user's AI settings for one request. */
data class AiRequestConfig(
    val apiKey: String,
    val model: String,
    /** The student agreed to send text to Gemini. Without it nothing leaves the device. */
    val consentGranted: Boolean
)

/** Raised when the AI is unavailable and the offline engine has nothing honest to offer for this input. */
class AiUnavailableException(message: String) : Exception(message)

private class HttpReply(val code: Int, val body: String, val retryAfterMillis: Long?)

private class HttpFailure(val code: Int, val userMessage: String) : Exception(userMessage)

/**
 * Talks to Google Gemini and falls back to the small curated offline engine ONLY for topics it really covers.
 *
 *  - The API key is sent in a header (never in the URL, so it cannot end up in logs or proxies).
 *  - The student's text travels in its own delimited block, separate from the instructions.
 *  - 429 / 5xx answers are retried with back-off; every failure is explained in Arabic.
 *  - Offline results are always flagged ([MedicalAiResult.isOfflineGenerated]) with the reason.
 */
class MedicalAiService(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = GeminiProtocol.DEFAULT_BASE_URL,
    private val buildKey: String = defaultBuildKey(),
    private val local: LocalMedicalEngine = LocalMedicalEngine(),
    /** Response cache + request log. Null = no caching or pacing (unit tests, previews). */
    private val store: AiStore? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val pipeline = RequestPipeline<MedicalAiResult>(store?.cache, store?.usage, scope)

    /** Requests sent today (since the Pacific-time quota reset). 0 when there is no store. */
    suspend fun requestsToday(): Int = store?.requestsToday() ?: 0
    suspend fun cacheSize(): Int = store?.cacheSize() ?: 0
    suspend fun clearCache() { store?.clearCache() }

    suspend fun testConnection(config: AiRequestConfig): Result<String> {
        val key = resolveApiKey(config.apiKey)
        if (key.isBlank()) return Result.failure(Exception(NO_KEY_MESSAGE))
        if (!GeminiProtocol.isValidModelName(config.model)) return Result.failure(Exception(BAD_MODEL_MESSAGE))
        return try {
            val body = GeminiProtocol.buildRequestBody(
                systemInstruction = "Reply with the single word: ok",
                userContent = "ping",
                temperature = 0.0,
                jsonMode = false,
                maxOutputTokens = 16
            )
            post(key, config.model, body)
            Result.success("تم الاتصال بنجاح بـ Gemini (${config.model.trim()})")
        } catch (e: HttpFailure) {
            Result.failure(Exception(e.userMessage))
        } catch (e: IOException) {
            Result.failure(Exception(NETWORK_MESSAGE))
        }
    }

    suspend fun translateMedicalText(
        text: String,
        direction: TranslationDirection,
        specialty: MedicalSpecialty,
        config: AiRequestConfig
    ): Result<MedicalAiResult> {
        if (text.isBlank()) return Result.failure(AiUnavailableException("النص فارغ."))
        val prompt = AiPrompts.translation(text, direction, specialty)
        return when (val attempt = callModel(config, prompt, { raw, truncated -> AiResponseParser.parse(raw, text.trim(), "translation", truncated) })) {
            is Attempt.Done -> Result.success(attempt.result)
            is Attempt.Unavailable ->
                if (local.hasCuratedTranslation(text)) {
                    Result.success(local.generateLocalTranslation(text, direction, specialty).copy(offlineReason = attempt.reason))
                } else {
                    Result.failure(AiUnavailableException(attempt.reason + "\n" + TRANSLATION_NEEDS_AI))
                }
        }
    }

    suspend fun explainMedicalConcept(
        textOrTopic: String,
        mode: ExplanationMode,
        explanationLanguage: String,
        sourceDoc: String? = null,
        sourcePage: Int? = null,
        config: AiRequestConfig,
        /** Parts to generate immediately. Default: none, the student asks for them with [expandSection]. */
        sections: Set<StudySection> = emptySet()
    ): Result<MedicalAiResult> {
        if (textOrTopic.isBlank()) return Result.failure(AiUnavailableException("النص فارغ."))
        val prompt = AiPrompts.explanation(textOrTopic, mode, explanationLanguage, sourceDoc, sourcePage, sections)
        return when (val attempt = callModel(config, prompt, { raw, truncated -> AiResponseParser.parse(raw, textOrTopic.trim(), mode.id, truncated) })) {
            is Attempt.Done -> Result.success(attempt.result)
            is Attempt.Unavailable ->
                if (local.hasCuratedExplanation(textOrTopic)) {
                    Result.success(
                        local.generateLocalExplanation(textOrTopic, mode, explanationLanguage, sourceDoc, sourcePage)
                            .copy(offlineReason = attempt.reason)
                    )
                } else {
                    Result.failure(AiUnavailableException(attempt.reason + "\n" + EXPLANATION_NEEDS_AI))
                }
        }
    }

    /**
     * Generates ONE extra part (diagram, quiz or flashcards) of a guide that already exists and returns the guide
     * with that part filled in. It is a small request (small output), cached like every other one.
     */
    suspend fun expandSection(
        guide: MedicalAiResult,
        section: StudySection,
        language: String,
        config: AiRequestConfig
    ): Result<MedicalAiResult> {
        if (guide.originalText.isBlank()) return Result.failure(AiUnavailableException("لا يوجد نص أصلي لتوليد هذا الجزء منه."))
        val prompt = AiPrompts.section(section, guide.originalText, guide.title.ifBlank { guide.topic }, language)
        val attempt = callModel(
            config, prompt,
            { raw, truncated -> AiResponseParser.parseSection(raw, section, truncated) },
            maxOutputTokens = SECTION_MAX_TOKENS
        )
        return when (attempt) {
            is Attempt.Done -> Result.success(AiResponseParser.merge(guide, section, attempt.result).copy(fromCache = false))
            is Attempt.Unavailable -> Result.failure(AiUnavailableException(attempt.reason))
        }
    }

    suspend fun generateMedicalDiagram(
        topic: String,
        @Suppress("UNUSED_PARAMETER") diagramStyle: String,
        language: String,
        config: AiRequestConfig
    ): Result<MedicalAiResult> {
        if (topic.isBlank()) return Result.failure(AiUnavailableException("النص فارغ."))
        val prompt = AiPrompts.diagram(topic, language)
        return when (val attempt = callModel(config, prompt, { raw, truncated -> AiResponseParser.parse(raw, topic.trim(), "diagram", truncated) })) {
            is Attempt.Done -> Result.success(attempt.result)
            is Attempt.Unavailable ->
                if (local.hasCuratedExplanation(topic)) {
                    Result.success(local.generateLocalDiagram(topic, language).copy(offlineReason = attempt.reason))
                } else {
                    Result.failure(AiUnavailableException(attempt.reason + "\n" + EXPLANATION_NEEDS_AI))
                }
        }
    }

    // Offline content used for the first-run sample library and unit tests.
    fun generateLocalTranslation(text: String, direction: TranslationDirection, specialty: MedicalSpecialty) =
        local.generateLocalTranslation(text, direction, specialty)

    fun generateLocalExplanation(topic: String, mode: ExplanationMode, lang: String, sourceDoc: String?, sourcePage: Int?) =
        local.generateLocalExplanation(topic, mode, lang, sourceDoc, sourcePage)

    fun generateLocalDiagram(topic: String, lang: String) = local.generateLocalDiagram(topic, lang)

    // ---------------------------------------------------------------- internals

    private sealed interface Attempt {
        data class Done(val result: MedicalAiResult) : Attempt
        data class Unavailable(val reason: String) : Attempt
    }

    private suspend fun callModel(
        config: AiRequestConfig,
        prompt: AiPrompt,
        parser: (String, Boolean) -> ParseOutcome,
        maxOutputTokens: Int = DEFAULT_MAX_TOKENS
    ): Attempt {
        val cacheKey = AiCacheKey.of(config.model, prompt.systemInstruction, prompt.userContent)
        val key = resolveApiKey(config.apiKey)
        val blocked = when {
            key.isBlank() -> NO_KEY_MESSAGE
            !config.consentGranted -> NO_CONSENT_MESSAGE
            !GeminiProtocol.isValidModelName(config.model) -> BAD_MODEL_MESSAGE
            else -> null
        }
        if (blocked != null) {
            // Nothing is sent, but an answer we already stored is still free to show.
            val cached = try { store?.cache?.get(cacheKey) } catch (e: Exception) { null }
            return if (cached != null) Attempt.Done(cached.copy(fromCache = true)) else Attempt.Unavailable(blocked)
        }
        val outcome = pipeline.run(cacheKey) {
            when (val attempt = requestModel(key, config.model, prompt, parser, maxOutputTokens)) {
                is Attempt.Done -> PipelineResult.Ok(attempt.result)
                is Attempt.Unavailable -> PipelineResult.Fail(attempt.reason)
            }
        }
        return when (outcome) {
            is PipelineResult.Ok -> Attempt.Done(outcome.value.copy(fromCache = outcome.fromCache))
            is PipelineResult.Fail -> Attempt.Unavailable(outcome.reason)
        }
    }

    private suspend fun requestModel(
        key: String,
        model: String,
        prompt: AiPrompt,
        parser: (String, Boolean) -> ParseOutcome,
        maxOutputTokens: Int
    ): Attempt {
        return try {
            val body = GeminiProtocol.buildRequestBody(prompt.systemInstruction, prompt.userContent, maxOutputTokens = maxOutputTokens)
            when (val reply = GeminiProtocol.parseResponse(post(key, model, body))) {
                is GeminiReply.Text -> when (val parsed = parser(reply.text, reply.truncated)) {
                    is ParseOutcome.Success -> Attempt.Done(parsed.result)
                    is ParseOutcome.Failure -> Attempt.Unavailable(parsed.reason + " حاول مرة أخرى.")
                }
                is GeminiReply.Blocked -> Attempt.Unavailable(GeminiProtocol.blockedMessage(reply.reason))
                GeminiReply.Empty -> Attempt.Unavailable("لم يرجع النموذج أي رد. حاول مرة أخرى.")
            }
        } catch (e: HttpFailure) {
            Attempt.Unavailable(e.userMessage)
        } catch (e: IOException) {
            Attempt.Unavailable(NETWORK_MESSAGE)
        }
    }

    /** POST with up to two retries on 429 / 5xx / dropped connections. Returns the response body. */
    private suspend fun post(apiKey: String, model: String, jsonBody: String): String {
        var attempt = 0
        while (true) {
            try {
                val reply = execute(apiKey, model, jsonBody)
                val code = reply.code
                val text = reply.body
                if (code in 200..299) return text
                if (GeminiProtocol.isRetryable(code) && attempt < MAX_RETRIES) {
                    delay(maxOf(RETRY_DELAYS_MS[attempt], reply.retryAfterMillis ?: 0L))
                    attempt++
                    continue
                }
                throw HttpFailure(code, GeminiProtocol.errorMessage(code, text))
            } catch (e: IOException) {
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS[attempt])
                    attempt++
                    continue
                }
                throw e
            }
        }
    }

    private suspend fun execute(apiKey: String, model: String, jsonBody: String): HttpReply =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(GeminiProtocol.endpoint(model, baseUrl))
                .header("x-goog-api-key", apiKey)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { r ->
                            val text = r.body?.string().orEmpty()
                            if (cont.isActive) cont.resume(HttpReply(r.code, text, GeminiProtocol.parseRetryAfter(r.header("Retry-After"))))
                        }
                    } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            })
        }

    private fun resolveApiKey(userKey: String): String {
        val user = userKey.trim()
        if (user.isNotEmpty()) return user
        return if (buildKey.isBlank() || buildKey == PLACEHOLDER_KEY) "" else buildKey
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val DEFAULT_MAX_TOKENS = 8192
        private const val SECTION_MAX_TOKENS = 3072
        private val RETRY_DELAYS_MS = longArrayOf(1_500L, 4_000L)
        private const val PLACEHOLDER_KEY = "MY_GEMINI_API_KEY"

        const val NO_KEY_MESSAGE =
            "لم يتم ضبط مفتاح Gemini. أضف مفتاحك من الإعدادات لتفعيل الذكاء الاصطناعي."
        const val NO_CONSENT_MESSAGE =
            "لم تمنح الموافقة على إرسال النص إلى Gemini. فعّلها من الإعدادات عندما تكون جاهزاً."
        const val BAD_MODEL_MESSAGE = "اسم النموذج غير صالح. صحّحه من الإعدادات."
        const val NETWORK_MESSAGE = "تعذر الاتصال بالإنترنت. تحقق من الشبكة وحاول مرة أخرى."
        private const val TRANSLATION_NEEDS_AI =
            "الترجمة الطبية تحتاج الذكاء الاصطناعي، ولا يوجد بديل محلي لهذا النص."
        private const val EXPLANATION_NEEDS_AI =
            "لا يوجد شرح محلي جاهز لهذا الموضوع. المحتوى المحلي يغطي موضوعات محدودة فقط."

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()

        private fun defaultBuildKey(): String = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
    }
}
