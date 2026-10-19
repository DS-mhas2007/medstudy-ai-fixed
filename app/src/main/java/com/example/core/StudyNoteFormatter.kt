package com.example.core

import com.example.data.remote.MedicalAiResult

/** A clean Markdown study note (for sharing to a notes app, email or print) built from a result. */
object StudyNoteFormatter {
    fun toMarkdown(r: MedicalAiResult, includeAnswers: Boolean = true): String {
        val out = StringBuilder()
        out.append("# ").append(r.title.ifBlank { r.topic.ifBlank { "ملاحظة دراسية" } }).append("\n\n")
        if (r.isOfflineGenerated) out.append("> محتوى جاهز من التطبيق وليس ناتجاً عن الذكاء الاصطناعي.\n\n")

        if (r.translation.isNotBlank()) section(out, "الترجمة", r.translation)
        if (r.simpleExplanation.isNotBlank()) section(out, "الخلاصة", r.simpleExplanation)
        if (r.detailedExplanation.isNotBlank()) section(out, "الشرح", r.detailedExplanation)

        if (r.keyTerms.isNotEmpty()) {
            out.append("## المصطلحات\n\n| المصطلح | بالعربية | التعريف |\n|---|---|---|\n")
            r.keyTerms.forEach { t -> out.append("| ${cell(t.term)} | ${cell(t.arabicEquivalent)} | ${cell(t.definition)} |\n") }
            out.append('\n')
        }
        if (r.mechanismSteps.isNotEmpty()) {
            out.append("## الآلية\n\n")
            r.mechanismSteps.forEachIndexed { i, s -> out.append("${i + 1}. $s\n") }
            out.append('\n')
        }
        if (r.keyPoints.isNotEmpty()) bullets(out, "نقاط مهمة للامتحان", r.keyPoints)
        if (r.misconceptions.isNotEmpty()) bullets(out, "أخطاء شائعة", r.misconceptions)

        r.comparisonTable?.let { t ->
            out.append("## ").append(t.title.ifBlank { "جدول مقارنة" }).append("\n\n")
            out.append("| ").append(t.columns.joinToString(" | ") { cell(it) }).append(" |\n")
            out.append("|").append(t.columns.joinToString("|") { "---" }).append("|\n")
            t.rows.forEach { row ->
                val padded = row + List((t.columns.size - row.size).coerceAtLeast(0)) { "" }
                out.append("| ").append(padded.take(t.columns.size).joinToString(" | ") { cell(it) }).append(" |\n")
            }
            out.append('\n')
        }
        if (r.questions.isNotEmpty()) {
            out.append("## أسئلة للمراجعة\n\n")
            r.questions.forEachIndexed { i, q ->
                out.append("${i + 1}. ${q.question}\n")
                q.options.forEachIndexed { j, o -> out.append("   - ${'A' + j}) $o\n") }
                if (includeAnswers) {
                    out.append("   - الإجابة: ${'A' + q.correctIndex}")
                    if (q.explanation.isNotBlank()) out.append(" — ${q.explanation}")
                    out.append('\n')
                }
            }
            out.append('\n')
        }
        if (r.flashcards.isNotEmpty()) {
            out.append("## بطاقات الحفظ\n\n")
            r.flashcards.forEach { f -> out.append("- **${f.front}** ← ${f.back}\n") }
            out.append('\n')
        }
        out.append("---\n_أُعدّ بواسطة MedStudy AI للمراجعة الدراسية فقط، وراجع مرجعك المعتمد._\n")
        return out.toString().trimEnd() + "\n"
    }

    private fun section(out: StringBuilder, heading: String, body: String) {
        out.append("## ").append(heading).append("\n\n").append(body.trim()).append("\n\n")
    }

    private fun bullets(out: StringBuilder, heading: String, items: List<String>) {
        out.append("## ").append(heading).append("\n\n")
        items.forEach { out.append("- ").append(it).append('\n') }
        out.append('\n')
    }

    private fun cell(text: String): String = text.replace("|", "\\|").replace("\n", " ").trim()
}
