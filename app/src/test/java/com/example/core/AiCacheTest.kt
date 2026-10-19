package com.example.core

import com.example.data.model.*
import com.example.data.remote.MedicalAiResult
import org.junit.Assert.*
import org.junit.Test

class AiCacheTest {
    @Test
    fun keyIsStableAndSensitiveToEveryPart() {
        val base = AiCacheKey.of("m", "sys", "user")
        assertEquals(base, AiCacheKey.of("m", "sys", "user"))
        assertEquals(base, AiCacheKey.of(" m ", "sys", "user"))
        assertNotEquals(base, AiCacheKey.of("m2", "sys", "user"))
        assertNotEquals(base, AiCacheKey.of("m", "sys2", "user"))
        assertNotEquals(base, AiCacheKey.of("m", "sys", "user2"))
        assertNotEquals(AiCacheKey.of("m", "ab", "c"), AiCacheKey.of("m", "a", "bc"))
        assertEquals(64, base.length)
    }

    private fun sample() = MedicalAiResult(
        title = "جهد الفعل", topic = "Action potential", mode = "first_year", originalText = "text",
        translation = "ترجمة", simpleExplanation = "بسيط", detailedExplanation = "مفصل",
        keyTerms = listOf(MedicalTerm("Depolarization", "إزالة الاستقطاب", "def")),
        mechanismSteps = listOf("1", "2"), keyPoints = listOf("k"), misconceptions = listOf("m"),
        comparisonTable = ComparisonTable("t", listOf("a", "b"), listOf(listOf("1", "2"))),
        diagramType = "mermaid", diagramCode = "graph TD\nA-->B",
        diagramNodes = listOf(DiagramNode("A", "Start", "بداية", "definition", "d")),
        diagramEdges = listOf(DiagramEdge("A", "B", "x")),
        questions = listOf(QuizQuestion("q?", listOf("a", "b", "c", "d"), 2, "why")),
        flashcards = listOf(FlashcardItem("f", "b", "e")),
        translationNotes = "n", alternativeTranslation = "alt", isOfflineGenerated = false
    )

    @Test
    fun resultSurvivesTheJsonRoundTrip() {
        val original = sample()
        assertEquals(original, AiResultJson.decode(AiResultJson.encode(original)))
    }

    @Test
    fun resultWithoutTableRoundTrips() {
        val original = sample().copy(comparisonTable = null, questions = emptyList())
        assertEquals(original, AiResultJson.decode(AiResultJson.encode(original)))
    }

    @Test
    fun corruptOrForeignJsonDecodesToNull() {
        assertNull(AiResultJson.decode("not json"))
        assertNull(AiResultJson.decode("{\"v\":99,\"title\":\"x\"}"))
        assertNull(AiResultJson.decode("[]"))
    }

    @Test
    fun retryAfterHeaderParsing() {
        assertEquals(5_000L, GeminiProtocol.parseRetryAfter("5"))
        assertEquals(30_000L, GeminiProtocol.parseRetryAfter("120"))
        assertNull(GeminiProtocol.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(GeminiProtocol.parseRetryAfter(null))
        assertNull(GeminiProtocol.parseRetryAfter("-3"))
    }
}
