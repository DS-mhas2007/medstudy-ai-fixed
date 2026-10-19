package com.example.core

import com.example.data.model.ComparisonTable
import com.example.data.model.DiagramEdge
import com.example.data.model.DiagramNode
import com.example.data.model.FlashcardItem
import com.example.data.model.MedicalTerm
import com.example.data.model.QuizQuestion
import com.example.data.remote.MedicalAiResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.random.Random

sealed interface ParseOutcome {
    data class Success(val result: MedicalAiResult) : ParseOutcome
    data class Failure(val reason: String) : ParseOutcome
}

/**
 * Turns the model's JSON into a [MedicalAiResult].
 *
 * Lenient about what models get wrong (code fences, text around the JSON, camelCase vs snake_case keys,
 * answer given as a letter) and strict about what would mislead a student: quiz questions whose answer
 * cannot be resolved are dropped, edges to unknown nodes are dropped, and an answer-less/empty reply is a failure.
 * Quiz options are shuffled because LLMs put the correct answer first far too often.
 */
object AiResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(
        raw: String,
        originalInput: String,
        fallbackMode: String,
        truncated: Boolean = false,
        random: Random = Random.Default
    ): ParseOutcome {
        val jsonText = extractJsonObject(raw)
            ?: return ParseOutcome.Failure(if (truncated) "الرد انقطع قبل اكتماله." else "لم يرجع النموذج بيانات منظمة.")
        val obj = try {
            json.parseToJsonElement(jsonText) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return ParseOutcome.Failure("تعذر قراءة رد النموذج.")

        val terms = obj.array("key_terms", "keyTerms").mapNotNull { el ->
            val t = el as? JsonObject ?: return@mapNotNull null
            val term = t.str("term")
            if (term.isBlank()) null
            else MedicalTerm(term, t.str("arabic_equivalent", "arabicEquivalent", "arabic"), t.str("definition"))
        }

        val nodes = parseNodes(obj)
        val ids = nodes.map { it.id }.toSet()
        val edges = obj.array("diagram_edges", "diagramEdges", "edges").mapNotNull { el ->
            val e = el as? JsonObject ?: return@mapNotNull null
            val from = e.str("from", "source")
            val to = e.str("to", "target")
            if (from in ids && to in ids && from != to) DiagramEdge(from, to, e.str("label")) else null
        }.distinctBy { Triple(it.from, it.to, it.label) }

        val modelMermaid = MermaidBuilder.sanitizeModelCode(obj.str("diagram_code", "diagramCode"))
        val diagramCode = MermaidBuilder.fromNodes(nodes, edges).ifBlank { modelMermaid }

        val result = MedicalAiResult(
            title = obj.str("title").ifBlank { originalInput.trim().take(40).ifBlank { "نتيجة دراسية" } },
            topic = obj.str("topic").ifBlank { originalInput.trim().take(40) },
            mode = obj.str("mode").ifBlank { fallbackMode },
            originalText = originalInput,
            translation = obj.str("translation"),
            simpleExplanation = obj.str("simple_explanation", "simpleExplanation"),
            detailedExplanation = obj.str("detailed_explanation", "detailedExplanation"),
            keyTerms = terms,
            mechanismSteps = obj.strings("mechanism_steps", "mechanismSteps"),
            keyPoints = obj.strings("key_points", "keyPoints"),
            misconceptions = obj.strings("misconceptions"),
            comparisonTable = parseTable(obj),
            diagramType = "mermaid",
            diagramCode = diagramCode,
            diagramNodes = nodes,
            diagramEdges = edges,
            questions = parseQuestions(obj, random),
            flashcards = obj.array("flashcards").mapNotNull { el ->
                val f = el as? JsonObject ?: return@mapNotNull null
                val front = f.str("front")
                val back = f.str("back")
                if (front.isBlank() || back.isBlank()) null else FlashcardItem(front, back, f.str("explanation"))
            },
            translationNotes = obj.str("translation_notes", "translationNotes"),
            alternativeTranslation = obj.str("alternative_translation", "alternativeTranslation"),
            isOfflineGenerated = false
        )

        val meaningful = result.translation.isNotBlank() || result.simpleExplanation.isNotBlank() ||
            result.detailedExplanation.isNotBlank() || result.keyTerms.isNotEmpty() || result.mechanismSteps.isNotEmpty()
        return if (meaningful) ParseOutcome.Success(OutputPolish.polish(result))
        else ParseOutcome.Failure("رد النموذج جاء فارغاً من المحتوى.")
    }

    /**
     * Parses the answer to [AiPrompts.section]. The result is a "partial" [MedicalAiResult] that only carries the
     * requested part (nodes/edges/diagram code, questions or flashcards); [merge] puts it into the full guide.
     */
    fun parseSection(
        raw: String,
        section: StudySection,
        truncated: Boolean = false,
        random: Random = Random.Default
    ): ParseOutcome {
        val jsonText = extractJsonObject(raw)
            ?: return ParseOutcome.Failure(if (truncated) "الرد انقطع قبل اكتماله." else "لم يرجع النموذج بيانات منظمة.")
        val obj = try {
            json.parseToJsonElement(jsonText) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return ParseOutcome.Failure("تعذر قراءة رد النموذج.")

        val nodes = if (section == StudySection.DIAGRAM) parseNodes(obj) else emptyList()
        val ids = nodes.map { it.id }.toSet()
        val edges = if (section == StudySection.DIAGRAM) {
            obj.array("diagram_edges", "diagramEdges", "edges").mapNotNull { el ->
                val e = el as? JsonObject ?: return@mapNotNull null
                val from = e.str("from", "source")
                val to = e.str("to", "target")
                if (from in ids && to in ids && from != to) DiagramEdge(from, to, e.str("label")) else null
            }.distinctBy { Triple(it.from, it.to, it.label) }
        } else emptyList()
        val questions = if (section == StudySection.QUIZ) parseQuestions(obj, random) else emptyList()
        val cards = if (section == StudySection.FLASHCARDS) {
            obj.array("flashcards").mapNotNull { el ->
                val f = el as? JsonObject ?: return@mapNotNull null
                val front = f.str("front")
                val back = f.str("back")
                if (front.isBlank() || back.isBlank()) null else FlashcardItem(front, back, f.str("explanation"))
            }
        } else emptyList()

        val ok = when (section) {
            StudySection.DIAGRAM -> nodes.size >= 2
            StudySection.QUIZ -> questions.isNotEmpty()
            StudySection.FLASHCARDS -> cards.isNotEmpty()
        }
        if (!ok) return ParseOutcome.Failure("لم يحتوِ رد النموذج على هذا الجزء بشكل صالح.")

        val partial = MedicalAiResult(
            title = "", topic = "", mode = "section", originalText = "", translation = "",
            simpleExplanation = "", detailedExplanation = "",
            keyTerms = emptyList(), mechanismSteps = emptyList(), keyPoints = emptyList(), misconceptions = emptyList(),
            comparisonTable = null, diagramType = "mermaid",
            diagramCode = if (section == StudySection.DIAGRAM) MermaidBuilder.fromNodes(nodes, edges) else "",
            diagramNodes = nodes, diagramEdges = edges, questions = questions, flashcards = cards
        )
        return ParseOutcome.Success(OutputPolish.polish(partial))
    }

    /** Puts the part produced by [parseSection] into the full guide, leaving everything else untouched. */
    fun merge(full: MedicalAiResult, section: StudySection, part: MedicalAiResult): MedicalAiResult = when (section) {
        StudySection.DIAGRAM -> full.copy(
            diagramCode = part.diagramCode, diagramNodes = part.diagramNodes, diagramEdges = part.diagramEdges, diagramType = "mermaid"
        )
        StudySection.QUIZ -> full.copy(questions = part.questions)
        StudySection.FLASHCARDS -> full.copy(flashcards = part.flashcards)
    }

    /** First balanced {...} object in [raw], ignoring braces inside strings. Null when absent or unbalanced. */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun parseNodes(obj: JsonObject): List<DiagramNode> {
        val seen = HashSet<String>()
        return obj.array("diagram_nodes", "diagramNodes", "nodes").mapIndexedNotNull { index, el ->
            val n = el as? JsonObject ?: return@mapIndexedNotNull null
            val id = n.str("id").ifBlank { ('A' + index).toString() }
            val label = n.str("label")
            val labelAr = n.str("labelAr", "label_ar")
            if ((label.isBlank() && labelAr.isBlank()) || !seen.add(id)) null
            else DiagramNode(id, label, labelAr, MermaidBuilder.normalizeCategory(n.str("category")), n.str("details"))
        }
    }

    private fun parseTable(obj: JsonObject): ComparisonTable? {
        val t = obj["comparison_table"] as? JsonObject ?: obj["comparisonTable"] as? JsonObject ?: return null
        val columns = t.strings("columns")
        val rows = t.array("rows").mapNotNull { row ->
            val cells = (row as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
            cells?.takeIf { c -> c.any { it.isNotBlank() } }
        }
        return if (columns.size >= 2 && rows.isNotEmpty()) ComparisonTable(t.str("title").ifBlank { "جدول مقارنة" }, columns, rows) else null
    }

    private fun parseQuestions(obj: JsonObject, random: Random): List<QuizQuestion> =
        obj.array("questions").mapNotNull { el ->
            val q = el as? JsonObject ?: return@mapNotNull null
            val question = q.str("question")
            val options = q.strings("options")
            if (question.isBlank() || options.size < 2 || options.toSet().size != options.size) return@mapNotNull null
            val correct = resolveCorrectIndex(q, options) ?: return@mapNotNull null
            val order = options.indices.shuffled(random)
            QuizQuestion(
                question = question,
                options = order.map { options[it] },
                correctIndex = order.indexOf(correct),
                explanation = q.str("explanation")
            )
        }

    private fun resolveCorrectIndex(q: JsonObject, options: List<String>): Int? {
        val el = q["correct_index"] ?: q["correctIndex"] ?: q["correct_answer"] ?: q["answer"] ?: return null
        val primitive = el as? JsonPrimitive ?: return null
        primitive.intOrNull?.let { return if (it in options.indices) it else null }
        val text = primitive.contentOrNull?.trim() ?: return null
        if (text.length == 1 && text[0].uppercaseChar() in 'A'..'Z') {
            val idx = text[0].uppercaseChar() - 'A'
            if (idx in options.indices) return idx
        }
        val byText = options.indexOfFirst { it.equals(text, ignoreCase = true) }
        return if (byText >= 0) byText else null
    }

    private fun JsonObject.str(vararg keys: String): String {
        for (k in keys) {
            val v = (this[k] as? JsonPrimitive)?.contentOrNull
            if (!v.isNullOrBlank()) return v.trim()
        }
        return ""
    }

    private fun JsonObject.array(vararg keys: String): List<JsonElement> {
        for (k in keys) {
            val v = this[k] as? JsonArray
            if (v != null) return v
        }
        return emptyList()
    }

    private fun JsonObject.strings(vararg keys: String): List<String> =
        array(*keys).mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
}
