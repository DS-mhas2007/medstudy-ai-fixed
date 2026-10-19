package com.example.core

/**
 * Arranges the student's text before it is sent: the same facts in fewer tokens and a clean cut when the text is
 * too long. Nothing is rewritten or summarised, so the meaning cannot change.
 *
 *  1. PDF noise out: page-number lines, running headers/footers, hyphenated line breaks.
 *  2. Typographic bullets become "- ", spaces and blank lines are collapsed.
 *  3. A paragraph that repeats earlier text (copied twice, slide notes) is kept once.
 *  4. Over the limit, the cut is made at a paragraph or sentence end instead of in the middle of a word.
 */
object ContextBuilder {
    data class Prepared(val text: String, val truncated: Boolean, val originalChars: Int, val keptChars: Int)

    private val pageNumberLine = Regex("""(?i)^\s*(page|p\.|صفحة|ص)?\s*\d{1,4}\s*(/\s*\d{1,4}|of\s+\d{1,4})?\s*$""")
    private val hyphenBreak = Regex("""(\p{L})-\n(\p{Ll})""")
    private val bulletStart = Regex("""(?m)^[ \t]*[•▪●◦‣∙·–—][ \t]+""")
    private const val MIN_DEDUPE_CHARS = 40

    /** Removes page numbers, repeated headers/footers (12-79 chars, 3+ times) and joins hyphenated words. */
    fun compactNoise(text: String): String {
        val lines = text.replace("\r\n", "\n").split('\n')
        val counts = HashMap<String, Int>()
        lines.forEach { l -> val t = l.trim(); if (t.length in 12..79) counts[t] = (counts[t] ?: 0) + 1 }
        val kept = lines.filter { l ->
            val t = l.trim()
            !(t.isNotEmpty() && (pageNumberLine.matches(t) || (counts[t] ?: 0) >= 3))
        }
        return kept.joinToString("\n").replace(hyphenBreak, "$1$2")
    }

    fun prepare(raw: String, maxChars: Int): Prepared {
        val tidy = compactNoise(raw)
            .replace("\u0000", "")
            .replace(bulletStart, "- ")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        val deduped = dedupeParagraphs(tidy)
        return if (deduped.length <= maxChars) {
            Prepared(deduped, false, raw.length, deduped.length)
        } else {
            val cut = smartCut(deduped, maxChars)
            Prepared(cut, true, raw.length, cut.length)
        }
    }

    internal fun dedupeParagraphs(text: String): String {
        val seen = HashSet<String>()
        return text.split("\n\n").filter { p ->
            val key = p.trim().lowercase().replace(Regex("""\s+"""), " ")
            if (key.length < MIN_DEDUPE_CHARS) true else seen.add(key)
        }.joinToString("\n\n")
    }

    internal fun smartCut(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val window = text.substring(0, maxChars)
        val floor = (maxChars * 0.6).toInt()
        val paragraph = window.lastIndexOf("\n\n")
        if (paragraph >= floor) return window.substring(0, paragraph).trimEnd()
        val sentenceEnd = Regex("""[.!?؟؛。]\s""").findAll(window).lastOrNull { it.range.first >= floor }
        if (sentenceEnd != null) return window.substring(0, sentenceEnd.range.first + 1).trimEnd()
        val newline = window.lastIndexOf('\n')
        if (newline >= floor) return window.substring(0, newline).trimEnd()
        val space = window.lastIndexOf(' ')
        if (space >= floor) return window.substring(0, space).trimEnd()
        return window
    }
}
