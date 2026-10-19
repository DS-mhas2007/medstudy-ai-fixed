package com.example.data.remote

import com.example.data.model.ComparisonTable
import com.example.data.model.DiagramEdge
import com.example.data.model.DiagramNode
import com.example.data.model.FlashcardItem
import com.example.data.model.MedicalTerm
import com.example.data.model.QuizQuestion

/**
 * Structured study material produced either by Gemini or by the built-in offline engine.
 *
 * [isOfflineGenerated] is true whenever the content did NOT come from the AI model; the UI must say so
 * and [offlineReason] explains why (no key, no consent, network error...).
 */
data class MedicalAiResult(
    val title: String,
    val topic: String,
    val mode: String,
    val originalText: String,
    val translation: String,
    val simpleExplanation: String,
    val detailedExplanation: String,
    val keyTerms: List<MedicalTerm>,
    val mechanismSteps: List<String>,
    val keyPoints: List<String>,
    val misconceptions: List<String>,
    val comparisonTable: ComparisonTable?,
    val diagramType: String,
    val diagramCode: String,
    val diagramNodes: List<DiagramNode> = emptyList(),
    val diagramEdges: List<DiagramEdge> = emptyList(),
    val questions: List<QuizQuestion>,
    val flashcards: List<FlashcardItem>,
    val translationNotes: String = "",
    val alternativeTranslation: String = "",
    val isOfflineGenerated: Boolean = false,
    val offlineReason: String = "",
    /** Served from the local response cache: no request was sent and no quota was used. */
    val fromCache: Boolean = false
)
