package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiResponseParserTest {
    private val good = """
        {"title":"Action potential","topic":"Physiology","mode":"first_year",
         "translation":"جهد الفعل","simple_explanation":"s","detailed_explanation":"d",
         "key_terms":[{"term":"Threshold","arabic_equivalent":"العتبة","definition":"-55 mV"},{"term":"","arabic_equivalent":"x"}],
         "mechanism_steps":["a","b"," "],"key_points":["p"],"misconceptions":[],
         "comparison_table":{"title":"t","columns":["f","A","B"],"rows":[["1","x","y"],[]]},
         "diagram_nodes":[{"id":"A","label":"Stimulus","category":"mechanism"},{"id":"B","label":"Spike","category":"nope"},{"id":"A","label":"dup"}],
         "diagram_edges":[{"from":"A","to":"B","label":"causes"},{"from":"A","to":"Q"},{"from":"B","to":"B"}],
         "questions":[
           {"question":"q1","options":["w","x","y","z"],"correct_index":0,"explanation":"e"},
           {"question":"q2","options":["w","x"],"correct_index":"B"},
           {"question":"bad","options":["w","x"],"correct_index":9},
           {"question":"dup options","options":["a","a"],"correct_index":0}
         ],
         "flashcards":[{"front":"f","back":"b"},{"front":"only front","back":""}]}
    """.trimIndent()

    private fun ok(raw: String, truncated: Boolean = false, seed: Int = 7) =
        (AiResponseParser.parse(raw, "input", "first_year", truncated, Random(seed)) as ParseOutcome.Success).result

    @Test fun parsesGoodResponseAndFiltersBadParts() {
        val r = ok(good)
        assertEquals("Action potential", r.title)
        assertEquals(1, r.keyTerms.size)
        assertEquals(listOf("a", "b"), r.mechanismSteps)
        assertEquals(2, r.diagramNodes.size)
        assertEquals("mechanism", r.diagramNodes[1].category)
        assertEquals(1, r.diagramEdges.size)
        assertTrue(r.diagramCode.startsWith("graph TD"))
        assertEquals(1, r.comparisonTable!!.rows.size)
        assertEquals(1, r.flashcards.size)
        assertEquals(2, r.questions.size)
        assertEquals(false, r.isOfflineGenerated)
    }

    @Test fun quizShufflePreservesTheCorrectAnswer() {
        for (seed in 0 until 40) {
            val r = ok(good, seed = seed)
            val q1 = r.questions[0]
            assertEquals("w", q1.options[q1.correctIndex])
            val q2 = r.questions[1]
            assertEquals("x", q2.options[q2.correctIndex]) // letter "B" resolved to index 1
        }
        val positions = (0 until 60).map { s -> ok(good, seed = s).questions[0].correctIndex }.toSet()
        assertTrue("correct answer should not always sit in the same slot", positions.size > 1)
    }

    @Test fun toleratesFencesAndSurroundingText() {
        val wrapped = "Sure! Here it is:\n```json\n$good\n```\nHope it helps {not json}"
        assertEquals("Action potential", ok(wrapped).title)
    }

    @Test fun acceptsCamelCaseKeys() {
        val r = ok("""{"simpleExplanation":"hello","keyTerms":[{"term":"a","arabicEquivalent":"أ","definition":"d"}]}""")
        assertEquals("hello", r.simpleExplanation)
        assertEquals("أ", r.keyTerms[0].arabicEquivalent)
        assertEquals("input", r.title)
    }

    @Test fun bracesInsideStringsDoNotConfuseExtraction() {
        val raw = """prefix {"simple_explanation":"use { and } freely","title":"t"} suffix"""
        assertEquals("use { and } freely", ok(raw).simpleExplanation)
    }

    @Test fun failsOnTruncatedEmptyOrGarbage() {
        val cut = good.substring(0, good.length / 2)
        assertTrue(AiResponseParser.parse(cut, "i", "m", truncated = true) is ParseOutcome.Failure)
        assertTrue(AiResponseParser.parse("no json here", "i", "m") is ParseOutcome.Failure)
        assertTrue(AiResponseParser.parse("""{"title":"only a title"}""", "i", "m") is ParseOutcome.Failure)
        assertNull(AiResponseParser.extractJsonObject("{\"a\":"))
        assertNotNull(AiResponseParser.extractJsonObject("x {\"a\":1} y"))
    }

    @Test fun fallsBackToSanitisedModelMermaidWhenNoNodes() {
        val r = ok("""{"simple_explanation":"s","diagram_code":"```\ngraph TD\nA-->B\n```"}""")
        assertEquals("graph TD\nA-->B", r.diagramCode)
        val bad = ok("""{"simple_explanation":"s","diagram_code":"<img onerror=x>"}""")
        assertEquals("", bad.diagramCode)
    }
}

class AiResponseParserSectionTest {
    private val rnd = kotlin.random.Random(1)

    @Test fun diagramSectionBuildsCodeFromNodes() {
        val raw = """{"diagram_nodes":[{"id":"A","label":"Stimulus","category":"definition"},{"id":"B","label":"Na+ influx","category":"mechanism"}],
            "diagram_edges":[{"from":"A","to":"B","label":"opens"},{"from":"A","to":"Z","label":"bad"}]}"""
        val r = AiResponseParser.parseSection(raw, StudySection.DIAGRAM, random = rnd) as ParseOutcome.Success
        assertEquals(2, r.result.diagramNodes.size)
        assertEquals(1, r.result.diagramEdges.size)
        assertTrue(r.result.diagramCode.startsWith("graph"))
    }

    @Test fun quizSectionDropsUnresolvableQuestionsAndFailsWhenEmpty() {
        val good = """{"questions":[{"question":"q","options":["a","b","c","d"],"correct_index":1,"explanation":"e"}]}"""
        val ok = AiResponseParser.parseSection(good, StudySection.QUIZ, random = rnd) as ParseOutcome.Success
        assertEquals(1, ok.result.questions.size)
        val bad = """{"questions":[{"question":"q","options":["a","b"],"correct_index":9}]}"""
        assertTrue(AiResponseParser.parseSection(bad, StudySection.QUIZ, random = rnd) is ParseOutcome.Failure)
    }

    @Test fun flashcardSectionAndWrongSectionContent() {
        val raw = """{"flashcards":[{"front":"f","back":"b"},{"front":"","back":"x"}]}"""
        val ok = AiResponseParser.parseSection(raw, StudySection.FLASHCARDS) as ParseOutcome.Success
        assertEquals(1, ok.result.flashcards.size)
        // asking for a quiz but receiving flashcards is a failure, not an empty success
        assertTrue(AiResponseParser.parseSection(raw, StudySection.QUIZ) is ParseOutcome.Failure)
    }

    @Test fun mergeTouchesOnlyTheRequestedPart() {
        val full = AiResponseParser.parse(
            """{"title":"T","simple_explanation":"s"}""", "in", "first_year"
        ) as ParseOutcome.Success
        val part = (AiResponseParser.parseSection(
            """{"flashcards":[{"front":"f","back":"b"}]}""", StudySection.FLASHCARDS
        ) as ParseOutcome.Success).result
        val merged = AiResponseParser.merge(full.result, StudySection.FLASHCARDS, part)
        assertEquals("T", merged.title)
        assertEquals("s", merged.simpleExplanation)
        assertEquals(1, merged.flashcards.size)
        assertTrue(merged.questions.isEmpty())
    }
}
