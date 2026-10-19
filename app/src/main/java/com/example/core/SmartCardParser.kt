package com.example.core

/**
 * Parses and formats smart medical flashcards:
 * 1. Cloze Deletions (Anki style {{c1::answer}} or {{c1::answer::hint}})
 * 2. Anatomical diagram occlusion cards
 * 3. Text cleaning from camera/screenshot OCR
 */
object SmartCardParser {

    private val CLOZE_REGEX = Regex("""\{\{c\d+::(.*?)(?:::([^}]*))?\}\}""")

    /**
     * One chunk of text inside a cloze-enabled card.
     */
    data class ClozeSegment(
        val text: String,
        val isCloze: Boolean,
        val answer: String = "",
        val hint: String = ""
    )

    /**
     * Splits a text containing {{c1::answer}} into segments for display in study mode.
     */
    fun parseCloze(rawText: String): List<ClozeSegment> {
        val segments = mutableListOf<ClozeSegment>()
        var lastIndex = 0

        CLOZE_REGEX.findAll(rawText).forEach { match ->
            if (match.range.first > lastIndex) {
                segments.add(
                    ClozeSegment(
                        text = rawText.substring(lastIndex, match.range.first),
                        isCloze = false
                    )
                )
            }

            val answer = match.groupValues[1]
            val hint = if (match.groupValues.size > 2) match.groupValues[2] else ""
            segments.add(
                ClozeSegment(
                    text = answer,
                    isCloze = true,
                    answer = answer,
                    hint = hint.ifBlank { "..." }
                )
            )
            lastIndex = match.range.last + 1
        }

        if (lastIndex < rawText.length) {
            segments.add(
                ClozeSegment(
                    text = rawText.substring(lastIndex),
                    isCloze = false
                )
            )
        }

        return segments
    }

    /**
     * Renders text for the question side (hiding clozes as [...] or [hint]).
     */
    fun formatQuestionSide(rawText: String): String {
        return CLOZE_REGEX.replace(rawText) { match ->
            val hint = if (match.groupValues.size > 2 && match.groupValues[2].isNotBlank()) {
                match.groupValues[2]
            } else {
                "..."
            }
            "[$hint]"
        }
    }

    /**
     * Renders text for the answer side (revealing all clozes highlighted).
     */
    fun formatAnswerSide(rawText: String): String {
        return CLOZE_REGEX.replace(rawText) { match ->
            match.groupValues[1]
        }
    }

    /**
     * Detects if the card text contains cloze deletion syntax.
     */
    fun isClozeCard(text: String): Boolean = CLOZE_REGEX.containsMatchIn(text)

    /**
     * Helper to wrap a selected word or phrase inside text with {{c1::...}} syntax.
     */
    fun wrapWordAsCloze(fullText: String, targetWord: String): String {
        if (targetWord.isBlank()) return fullText
        return fullText.replaceFirst(targetWord, "{{c1::$targetWord}}", ignoreCase = true)
    }

    /**
     * Cleans raw OCR text from lecture slides or textbook photos:
     * - Fixes broken hyphenated words at line breaks
     * - Removes spurious scanner artifacts
     * - Normalizes spaces
     */
    fun cleanOcrText(rawOcr: String): String {
        return rawOcr
            .replace(Regex("""-\s*\n\s*"""), "") // merge hyphenated broken line
            .replace(Regex("""\n{3,}"""), "\n\n") // normalize excess blank lines
            .replace(Regex("""[ \t]+"""), " ") // normalize spacing
            .trim()
    }

    /**
     * Extracts potential medical key phrases suitable for cloze occlusion from scanned text.
     */
    fun suggestClozeKeywords(text: String): List<String> {
        val words = text.split(Regex("""[\s,.;:()\[\]{}]+"""))
            .filter { it.length >= 4 && !STOP_WORDS.contains(it.lowercase()) }
            .distinct()
        return words.take(6)
    }

    private val STOP_WORDS = setOf(
        "the", "and", "that", "this", "with", "from", "have", "were", "which", "there",
        "their", "about", "other", "into", "more", "also", "some", "time", "been", "when",
        "they", "will", "what", "than", "each", "them", "then", "many", "these", "such"
    )
}
