package com.example.core

import com.example.data.model.*
import com.example.data.remote.MedicalAiResult
import org.junit.Assert.*
import org.junit.Test

class StudyNoteFormatterTest {
    private val r = MedicalAiResult(
        title = "جهد الفعل", topic = "t", mode = "m", originalText = "o", translation = "",
        simpleExplanation = "ملخص", detailedExplanation = "",
        keyTerms = listOf(MedicalTerm("Threshold", "العتبة", "قيمة | حرجة")),
        mechanismSteps = listOf("a", "b"), keyPoints = listOf("k"), misconceptions = emptyList(),
        comparisonTable = ComparisonTable("مقارنة", listOf("x", "y"), listOf(listOf("1"))),
        diagramType = "mermaid", diagramCode = "",
        questions = listOf(QuizQuestion("q?", listOf("o1", "o2", "o3", "o4"), 2, "لأن")),
        flashcards = listOf(FlashcardItem("f", "b", ""))
    )

    @Test fun noteHasAllSectionsInOrder() {
        val md = StudyNoteFormatter.toMarkdown(r)
        val order = listOf("# جهد الفعل", "## الخلاصة", "## المصطلحات", "## الآلية", "## نقاط مهمة", "## مقارنة", "## أسئلة للمراجعة", "## بطاقات الحفظ")
        var last = -1
        order.forEach { h -> val i = md.indexOf(h); assertTrue("missing $h", i > last); last = i }
        assertTrue(md.contains("1. a"))
        assertTrue(md.contains("الإجابة: C"))
    }

    @Test fun pipesInCellsAreEscapedAndShortRowsPadded() {
        val md = StudyNoteFormatter.toMarkdown(r)
        assertTrue(md.contains("قيمة \\| حرجة"))
        assertTrue(md.contains("| 1 |  |"))
    }

    @Test fun answersCanBeHidden() {
        assertFalse(StudyNoteFormatter.toMarkdown(r, includeAnswers = false).contains("الإجابة:"))
    }

    @Test fun offlineNoteIsLabelled() {
        assertTrue(StudyNoteFormatter.toMarkdown(r.copy(isOfflineGenerated = true)).contains("ليس ناتجاً عن الذكاء الاصطناعي"))
    }
}
