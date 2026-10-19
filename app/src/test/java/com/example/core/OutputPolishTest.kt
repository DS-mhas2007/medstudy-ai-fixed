package com.example.core

import com.example.data.model.*
import com.example.data.remote.MedicalAiResult
import org.junit.Assert.*
import org.junit.Test

class OutputPolishTest {
    private fun base() = MedicalAiResult(
        title = "**Action potential**", topic = "Physiology", mode = "x", originalText = "o", translation = "",
        simpleExplanation = "## Summary\nThe **membrane** depolarizes.", detailedExplanation = "d",
        keyTerms = emptyList(), mechanismSteps = emptyList(), keyPoints = emptyList(), misconceptions = emptyList(),
        comparisonTable = null, diagramType = "mermaid", diagramCode = "", questions = emptyList(), flashcards = emptyList()
    )

    @Test fun markdownSymbolsAreRemovedFromText() {
        val r = OutputPolish.polish(base())
        assertEquals("Action potential", r.title)
        assertEquals("Summary\nThe membrane depolarizes.", r.simpleExplanation)
    }

    @Test fun listItemsLoseNumberingAndDuplicatesAndAreCapped() {
        val r = OutputPolish.polish(
            base().copy(
                mechanismSteps = listOf("1. Stimulus", "2) Na+ influx", "- Na+ influx", "Step 4: Repolarization", ""),
                keyPoints = (1..20).map { "Point $it" }
            )
        )
        assertEquals(listOf("Stimulus", "Na+ influx", "Repolarization"), r.mechanismSteps)
        assertEquals(OutputPolish.MAX_POINTS, r.keyPoints.size)
    }

    @Test fun numbersLikeFiveHtAndDecimalsAreNotTreatedAsNumbering() {
        val r = OutputPolish.polish(base().copy(keyPoints = listOf("5-HT3 receptor blockers", "1.5 mg/kg", "2: Na+")))
        assertEquals(listOf("5-HT3 receptor blockers", "1.5 mg/kg", "Na+"), r.keyPoints)
    }

    @Test fun arabicPunctuationIsFixedButNumbersAreNot() {
        assertEquals("الجهد، والتيار؛ هل؟", OutputPolish.cleanText("الجهد, والتيار; هل?"))
        assertEquals("الجرعة 1,000 ملغ", OutputPolish.cleanText("الجرعة 1,000 ملغ"))
        assertEquals("Na+, K+", OutputPolish.cleanText("Na+, K+"))
    }

    @Test fun quizOptionLabelsAreStrippedOnlyWhenAllHaveThem() {
        val labelled = QuizQuestion("q?", listOf("A) one", "B) two", "C) three", "D) four"), 1, "e")
        val mixed = QuizQuestion("q?", listOf("A. positive", "negative", "B cells", "T cells"), 0, "e")
        val r = OutputPolish.polish(base().copy(questions = listOf(labelled, mixed)))
        assertEquals(listOf("one", "two", "three", "four"), r.questions[0].options)
        assertEquals(listOf("A. positive", "negative", "B cells", "T cells"), r.questions[1].options)
    }

    @Test fun brokenQuestionsAreDropped() {
        val dup = QuizQuestion("q?", listOf("same", "Same", "x", "y"), 0, "")
        val badIndex = QuizQuestion("q?", listOf("a", "b", "c", "d"), 7, "")
        val ok = QuizQuestion("q?", listOf("a", "b", "c", "d"), 2, "")
        assertEquals(listOf(ok), OutputPolish.polish(base().copy(questions = listOf(dup, badIndex, ok))).questions)
    }

    @Test fun termsAndCardsAreDeduplicated() {
        val r = OutputPolish.polish(
            base().copy(
                keyTerms = listOf(MedicalTerm("Threshold", "Threshold", "d"), MedicalTerm("threshold", "عتبة", "d2"), MedicalTerm("", "x", "y")),
                flashcards = listOf(FlashcardItem("Na+", "صوديوم", ""), FlashcardItem("na+", "x", ""), FlashcardItem("same", "same", ""))
            )
        )
        assertEquals(1, r.keyTerms.size)
        assertEquals("", r.keyTerms[0].arabicEquivalent)   // Arabic equal to the term is dropped
        assertEquals(1, r.flashcards.size)
    }

    @Test fun diagramCodeIsRebuiltFromCleanedAndShortenedLabels() {
        val long = "L".repeat(80)
        val nodes = listOf(DiagramNode("A", "**Start**", "", "definition"), DiagramNode("B", long, "", "outcome"))
        val r = OutputPolish.polish(base().copy(diagramNodes = nodes, diagramEdges = listOf(DiagramEdge("A", "B", "x"))))
        assertEquals("Start", r.diagramNodes[0].label)
        assertEquals(OutputPolish.MAX_NODE_LABEL, r.diagramNodes[1].label.length)
        assertTrue(r.diagramCode.startsWith("graph"))
        assertFalse(r.diagramCode.contains("**"))
    }
}
