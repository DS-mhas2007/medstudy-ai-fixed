package com.example.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiProtocolTest {
    @Test fun requestBodyIsValidJsonWithSystemInstruction() {
        val body = GeminiProtocol.buildRequestBody("SYS \"q\"", "user\nline")
        val root = Json.parseToJsonElement(body).jsonObject
        assertEquals("SYS \"q\"", root["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("user\nline", root["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("application/json", root["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals(4, root["safetySettings"]!!.jsonArray.size)
    }

    @Test fun modelNameValidationBlocksUrlTricks() {
        assertTrue(GeminiProtocol.isValidModelName("gemini-3.5-flash"))
        assertFalse(GeminiProtocol.isValidModelName("x?key=1"))
        assertFalse(GeminiProtocol.isValidModelName("../models"))
        assertFalse(GeminiProtocol.isValidModelName(""))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/m1:generateContent", GeminiProtocol.endpoint(" m1 "))
    }

    @Test fun extractsTextAndSkipsThoughtParts() {
        val body = """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"hidden","thought":true},{"text":"{\"a\":"},{"text":"1}"}]}}]}"""
        val r = GeminiProtocol.parseResponse(body) as GeminiReply.Text
        assertEquals("{\"a\":1}", r.text)
        assertFalse(r.truncated)
    }

    @Test fun flagsTruncationAndBlocks() {
        val cut = """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"{\"a\""}]}}]}"""
        assertTrue((GeminiProtocol.parseResponse(cut) as GeminiReply.Text).truncated)
        val blocked = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        assertEquals(GeminiReply.Blocked("SAFETY"), GeminiProtocol.parseResponse(blocked))
        val safety = """{"candidates":[{"finishReason":"SAFETY"}]}"""
        assertEquals(GeminiReply.Blocked("SAFETY"), GeminiProtocol.parseResponse(safety))
        assertEquals(GeminiReply.Empty, GeminiProtocol.parseResponse("garbage"))
        assertEquals(GeminiReply.Empty, GeminiProtocol.parseResponse("{}"))
    }

    @Test fun errorMessagesAreFriendlyAndNeverLeakBody() {
        val keyErr = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key."}}"""
        assertTrue(GeminiProtocol.errorMessage(400, keyErr).contains("مفتاح"))
        assertTrue(GeminiProtocol.errorMessage(429, "").contains("حصة"))
        assertTrue(GeminiProtocol.errorMessage(404, "").contains("النموذج"))
        assertTrue(GeminiProtocol.errorMessage(503, "").contains("مشغول"))
        assertFalse(GeminiProtocol.errorMessage(400, keyErr).contains("pass a valid"))
        assertTrue(GeminiProtocol.isRetryable(503))
        assertFalse(GeminiProtocol.isRetryable(400))
    }
}
