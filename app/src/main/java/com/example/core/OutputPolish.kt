package com.example.core

import com.example.data.model.ComparisonTable
import com.example.data.model.DiagramNode
import com.example.data.model.FlashcardItem
import com.example.data.model.MedicalTerm
import com.example.data.model.QuizQuestion
import com.example.data.remote.MedicalAiResult

/**
 * Last step before a result is shown or stored: makes model output uniform and removes the usual defects
 * (markdown symbols inside plain text, numbered/bulleted list items, "A)" prefixes on quiz options, duplicates,
 * Latin punctuation inside Arabic sentences, runaway lists). It never adds facts and never changes numbers.
 */
object OutputPolish {
    const val MAX_TERMS = 15
    const val MAX_STEPS = 12
    const val MAX_POINTS = 10
    const val MAX_MISCONCEPTIONS = 6
    const val MAX_CARDS = 8
    const val MAX_NODE_LABEL = 48

    private val emphasis = Regex("""(\*\*|__|~~|`+)""")
    private val headingMark = Regex("""(?m)^\s{0,3}#{1,6}\s+""")
    private val listPrefix = Regex("""^\s*(?:[-*•▪●]|\(?\d{1,2}[.)\-:]|[Ss]tep\s*\d{1,2}\s*[:.\-]|الخطوة\s*\d{1,2}\s*[:.\-])\s+""")
    private val optionPrefix = Regex("""^\s*\(?[A-Da-d][.):]\s+""")
    private val arabicLetter = Regex("""\p{InArabic}""")

    fun cleanText(text: String): String {
        var t = text.replace(headingMark, "").replace(emphasis, "").replace("\r\n", "\n")
        t = t.replace(Regex("""[ \t]{2,}"""), " ").replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n{3,}"""), "\n\n")
        return arabicPunctuation(t.trim())
    }

    /** "," ";" "?" directly after an Arabic letter become "،" "؛" "؟". Digits like 1,000 are left alone. */
    internal fun arabicPunctuation(text: String): String {
        if (!arabicLetter.containsMatchIn(text)) return text
        val out = StringBuilder(text.length)
        for ((i, c) in text.withIndex()) {
            val prevArabic = i > 0 && arabicLetter.matches(text[i - 1].toString())
            out.append(
                when {
                    prevArabic && c == ',' -> '،'
                    prevArabic && c == ';' -> '؛'
                    prevArabic && c == '?' -> '؟'
                    else -> c
                }
            )
        }
        return out.toString()
    }

    private fun item(text: String): String = cleanText(text).replace(listPrefix, "").replace(Regex("""\s*\n\s*"""), " ").trim()

    private fun items(list: List<String>, max: Int): List<String> {
        val seen = HashSet<String>()
        return list.map(::item).filter { it.isNotBlank() && seen.add(it.lowercase()) }.take(max)
    }

    fun polish(r: MedicalAiResult): MedicalAiResult {
        val termSeen = HashSet<String>()
        val terms = r.keyTerms.mapNotNull { t ->
            val term = item(t.term)
            if (term.isBlank() || !termSeen.add(term.lowercase())) return@mapNotNull null
            val ar = item(t.arabicEquivalent).let { if (it.equals(term, ignoreCase = true)) "" else it }
            MedicalTerm(term, ar, cleanText(t.definition))
        }.take(MAX_TERMS)

        val cardSeen = HashSet<String>()
        val cards = r.flashcards.mapNotNull { f ->
            val front = item(f.front)
            val back = cleanText(f.back)
            if (front.isBlank() || back.isBlank() || front.equals(back, ignoreCase = true) || !cardSeen.add(front.lowercase())) null
            else FlashcardItem(front, back, cleanText(f.explanation))
        }.take(MAX_CARDS)

        val questions = r.questions.mapNotNull { q ->
            val cleaned = q.options.map { cleanText(it) }
            // "A) ..." labels are stripped only when every option carries one (the UI adds its own letters).
            val options = if (cleaned.isNotEmpty() && cleaned.all { optionPrefix.containsMatchIn(it) }) {
                cleaned.map { it.replace(optionPrefix, "").trim() }
            } else cleaned
            val question = cleanText(q.question)
            if (question.isBlank() || options.any { it.isBlank() } || options.map { it.lowercase() }.toSet().size != options.size) null
            else if (q.correctIndex !in options.indices) null
            else QuizQuestion(question, options, q.correctIndex, cleanText(q.explanation))
        }

        val nodes = r.diagramNodes.map { n ->
            DiagramNode(n.id, shorten(item(n.label)), shorten(item(n.labelAr)), n.category, cleanText(n.details))
        }

        val table = r.comparisonTable?.let { t ->
            val columns = t.columns.map(::item)
            val rows = t.rows.map { row -> row.map(::cleanText) }.filter { row -> row.any { it.isNotBlank() } }
            if (columns.size >= 2 && rows.isNotEmpty()) ComparisonTable(item(t.title), columns, rows) else null
        }

        val diagramCode = if (nodes.size >= 2) {
            MermaidBuilder.fromNodes(nodes, r.diagramEdges).ifBlank { r.diagramCode }
        } else r.diagramCode

        return r.copy(
            title = item(r.title),
            topic = item(r.topic),
            translation = cleanText(r.translation),
            simpleExplanation = cleanText(r.simpleExplanation),
            detailedExplanation = cleanText(r.detailedExplanation),
            keyTerms = terms,
            mechanismSteps = items(r.mechanismSteps, MAX_STEPS),
            keyPoints = items(r.keyPoints, MAX_POINTS),
            misconceptions = items(r.misconceptions, MAX_MISCONCEPTIONS),
            comparisonTable = table,
            diagramCode = diagramCode,
            diagramNodes = nodes,
            questions = questions,
            flashcards = cards,
            translationNotes = cleanText(r.translationNotes),
            alternativeTranslation = cleanText(r.alternativeTranslation)
        )
    }

    private fun shorten(label: String): String =
        if (label.length <= MAX_NODE_LABEL) label else label.take(MAX_NODE_LABEL - 1).trimEnd() + "…"
}
