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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Lossless JSON form of a [MedicalAiResult] for the response cache. [decode] returns null for anything it cannot
 * read (corrupt row, older format), and the caller then simply asks the model again.
 */
object AiResultJson {
    const val FORMAT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun encode(r: MedicalAiResult): String = buildJsonObject {
        put("v", FORMAT_VERSION)
        put("title", r.title)
        put("topic", r.topic)
        put("mode", r.mode)
        put("originalText", r.originalText)
        put("translation", r.translation)
        put("simpleExplanation", r.simpleExplanation)
        put("detailedExplanation", r.detailedExplanation)
        put("keyTerms", buildJsonArray {
            r.keyTerms.forEach { t ->
                add(buildJsonObject { put("term", t.term); put("ar", t.arabicEquivalent); put("def", t.definition) })
            }
        })
        put("mechanismSteps", strings(r.mechanismSteps))
        put("keyPoints", strings(r.keyPoints))
        put("misconceptions", strings(r.misconceptions))
        r.comparisonTable?.let { table ->
            put("table", buildJsonObject {
                put("title", table.title)
                put("columns", strings(table.columns))
                put("rows", buildJsonArray { table.rows.forEach { add(strings(it)) } })
            })
        }
        put("diagramType", r.diagramType)
        put("diagramCode", r.diagramCode)
        put("nodes", buildJsonArray {
            r.diagramNodes.forEach { n ->
                add(buildJsonObject {
                    put("id", n.id); put("label", n.label); put("labelAr", n.labelAr)
                    put("category", n.category); put("details", n.details)
                })
            }
        })
        put("edges", buildJsonArray {
            r.diagramEdges.forEach { e ->
                add(buildJsonObject { put("from", e.from); put("to", e.to); put("label", e.label) })
            }
        })
        put("questions", buildJsonArray {
            r.questions.forEach { q ->
                add(buildJsonObject {
                    put("q", q.question); put("options", strings(q.options))
                    put("correct", q.correctIndex); put("explanation", q.explanation)
                })
            }
        })
        put("flashcards", buildJsonArray {
            r.flashcards.forEach { f ->
                add(buildJsonObject { put("front", f.front); put("back", f.back); put("explanation", f.explanation) })
            }
        })
        put("translationNotes", r.translationNotes)
        put("alternativeTranslation", r.alternativeTranslation)
        put("offline", r.isOfflineGenerated)
    }.toString()

    fun decode(text: String): MedicalAiResult? = try {
        val o = json.parseToJsonElement(text) as? JsonObject
        if (o == null || (o["v"] as? JsonPrimitive)?.intOrNull != FORMAT_VERSION) null else fromObject(o)
    } catch (e: Exception) {
        null
    }

    private fun fromObject(o: JsonObject): MedicalAiResult = MedicalAiResult(
        title = o.s("title"),
        topic = o.s("topic"),
        mode = o.s("mode"),
        originalText = o.s("originalText"),
        translation = o.s("translation"),
        simpleExplanation = o.s("simpleExplanation"),
        detailedExplanation = o.s("detailedExplanation"),
        keyTerms = o.objs("keyTerms").map { MedicalTerm(it.s("term"), it.s("ar"), it.s("def")) },
        mechanismSteps = o.strs("mechanismSteps"),
        keyPoints = o.strs("keyPoints"),
        misconceptions = o.strs("misconceptions"),
        comparisonTable = (o["table"] as? JsonObject)?.let { t ->
            ComparisonTable(
                t.s("title"),
                t.strs("columns"),
                (t["rows"] as? JsonArray).orEmpty().map { row -> (row as? JsonArray).orEmpty().map { it.str() } }
            )
        },
        diagramType = o.s("diagramType").ifBlank { "mermaid" },
        diagramCode = o.s("diagramCode"),
        diagramNodes = o.objs("nodes").map { DiagramNode(it.s("id"), it.s("label"), it.s("labelAr"), it.s("category"), it.s("details")) },
        diagramEdges = o.objs("edges").map { DiagramEdge(it.s("from"), it.s("to"), it.s("label")) },
        questions = o.objs("questions").map {
            QuizQuestion(it.s("q"), it.strs("options"), (it["correct"] as? JsonPrimitive)?.intOrNull ?: 0, it.s("explanation"))
        },
        flashcards = o.objs("flashcards").map { FlashcardItem(it.s("front"), it.s("back"), it.s("explanation")) },
        translationNotes = o.s("translationNotes"),
        alternativeTranslation = o.s("alternativeTranslation"),
        isOfflineGenerated = (o["offline"] as? JsonPrimitive)?.booleanOrNull ?: false
    )

    private fun strings(list: List<String>): JsonArray = buildJsonArray { list.forEach { add(JsonPrimitive(it)) } }
    private fun JsonElement.str(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.s(key: String): String = this[key]?.str().orEmpty()
    private fun JsonObject.strs(key: String): List<String> = (this[key] as? JsonArray).orEmpty().map { it.str() }
    private fun JsonObject.objs(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
