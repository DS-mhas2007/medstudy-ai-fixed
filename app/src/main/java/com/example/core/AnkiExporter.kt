package com.example.core

/** A flashcard in the minimal shape needed for export. */
data class ExportCard(
    val front: String,
    val back: String,
    val explanation: String = "",
    val tags: List<String> = emptyList()
)

/**
 * Builds a text file that Anki imports directly (File > Import): tab separated, HTML enabled,
 * every field quoted so quotes and line breaks inside medical text can never break the columns.
 */
object AnkiExporter {
    fun toAnkiText(cards: List<ExportCard>): String {
        val sb = StringBuilder()
        sb.append("#separator:tab\n")
        sb.append("#html:true\n")
        sb.append("#tags column:3\n")
        for (card in cards) {
            if (card.front.isBlank() || card.back.isBlank()) continue
            val back = buildString {
                append(html(card.back))
                if (card.explanation.isNotBlank()) {
                    append("<br><br><i>").append(html(card.explanation)).append("</i>")
                }
            }
            sb.append(quote(html(card.front))).append('\t')
                .append(quote(back)).append('\t')
                .append(quote(card.tags.map(::tag).filter { it.isNotEmpty() }.joinToString(" ")))
                .append('\n')
        }
        return sb.toString()
    }

    fun exportableCount(cards: List<ExportCard>): Int =
        cards.count { it.front.isNotBlank() && it.back.isNotBlank() }

    private fun html(text: String): String = text.trim()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\t", " ")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", "<br>")

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun tag(raw: String): String = raw.trim().replace(Regex("""\s+"""), "_").replace("\"", "")
}
