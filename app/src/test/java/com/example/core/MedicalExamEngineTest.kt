package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalExamEngineTest {

    private val sampleQuestions = listOf(
        ExamQuestion(
            id = "q1",
            questionEn = "Which valve separates left atrium and left ventricle?",
            questionAr = "ما هو الصمام الذي يفصل الأذين الأيسر عن البطين الأيسر؟",
            options = listOf("Mitral", "Tricuspid", "Aortic", "Pulmonary"),
            correctIndex = 0,
            explanationAr = "الصمام المترالي (Mitral) يفصل الأذين الأيسر عن البطين الأيسر.",
            topic = "Cardiac Anatomy",
            specialty = "Anatomy"
        ),
        ExamQuestion(
            id = "q2",
            questionEn = "Which medication is first-line for anaphylaxis?",
            questionAr = "ما هو الدواء الأساسي لعلاج الصدمة التأقية (Anaphylaxis)؟",
            options = listOf("Diphenhydramine", "Epinephrine", "Albuterol", "Hydrocortisone"),
            correctIndex = 1,
            explanationAr = "الإبينفرين (Epinephrine) عبر العضل هو خط العلاج الأول فوراً.",
            topic = "Emergency Pharmacology",
            specialty = "Pharmacology"
        ),
        ExamQuestion(
            id = "q3",
            questionEn = "What is the primary action of beta-blockers?",
            questionAr = "ما هو التأثير الأساسي لحاصرات بيتا؟",
            options = listOf("Vasodilation", "Negative inotropy and chronotropy", "Bronchodilation", "Glycogenolysis"),
            correctIndex = 1,
            explanationAr = "حاصرات بيتا تقلل معدل ضربات القلب وقوة انقباضه.",
            topic = "Cardiovascular Drugs",
            specialty = "Pharmacology"
        )
    )

    @Test
    fun evaluateExam_allCorrect_gives100ScoreAndPass() {
        val answers = mapOf(0 to 0, 1 to 1, 2 to 1)
        val report = MedicalExamEngine.evaluateExam(
            examTitle = "Mock Test",
            questions = sampleQuestions,
            answers = answers,
            timeSpentSeconds = 120,
            timeLimitSeconds = 300
        )

        assertEquals(3, report.totalQuestions)
        assertEquals(3, report.correctCount)
        assertEquals(100, report.scorePercent)
        assertTrue(report.isPassed)
        assertTrue(report.weakTopics.isEmpty())
        assertTrue(report.strongTopics.contains("Pharmacology"))
        assertTrue(report.strongTopics.contains("Anatomy"))
    }

    @Test
    fun evaluateExam_oneError_identifiesWeakSpecialty() {
        // Anatomy correct (1/1 = 100%), Pharmacology incorrect (0/2 = 0%)
        val answers = mapOf(0 to 0, 1 to 0, 2 to 0)
        val report = MedicalExamEngine.evaluateExam(
            examTitle = "Mock Test",
            questions = sampleQuestions,
            answers = answers,
            timeSpentSeconds = 90,
            timeLimitSeconds = 300
        )

        assertEquals(3, report.totalQuestions)
        assertEquals(1, report.correctCount)
        assertEquals(33, report.scorePercent)
        assertFalse(report.isPassed)
        assertTrue(report.weakTopics.contains("Pharmacology"))
        assertTrue(report.strongTopics.contains("Anatomy"))
    }
}
