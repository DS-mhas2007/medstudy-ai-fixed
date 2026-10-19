package com.example.core

import com.example.data.model.DiagramEdge
import com.example.data.model.DiagramNode

/**
 * Generates Mermaid flowchart code from structured nodes/edges ourselves instead of trusting
 * model-written Mermaid (which often has syntax errors, especially with Arabic text and parentheses).
 * Categories get the app palette through classDef so the diagram is colour coded.
 */
object MermaidBuilder {
    val CATEGORIES = setOf("definition", "mechanism", "anatomy", "exam", "warning", "outcome")

    fun fromNodes(nodes: List<DiagramNode>, edges: List<DiagramEdge>, arabicLabels: Boolean = false): String {
        if (nodes.size < 2) return ""
        val ids = nodes.map { it.id }.toSet()
        val sb = StringBuilder("graph TD\n")
        for (node in nodes) {
            val label = (if (arabicLabels && node.labelAr.isNotBlank()) node.labelAr else node.label)
                .ifBlank { node.labelAr }
                .ifBlank { node.id }
            sb.append("  ").append(safeId(node.id)).append("[\"").append(escape(label)).append("\"]\n")
        }
        for (edge in edges) {
            if (edge.from !in ids || edge.to !in ids) continue
            val a = safeId(edge.from)
            val b = safeId(edge.to)
            if (edge.label.isBlank()) sb.append("  $a --> $b\n")
            else sb.append("  $a -->|\"").append(escape(edge.label)).append("\"| $b\n")
        }
        sb.append("  classDef definition fill:#34234C,stroke:#D6B2FC,color:#EEF2F7,stroke-width:2px\n")
        sb.append("  classDef mechanism fill:#0E3830,stroke:#60F5D2,color:#EEF2F7,stroke-width:2px\n")
        sb.append("  classDef anatomy fill:#0C3852,stroke:#38BDF8,color:#EEF2F7,stroke-width:2px\n")
        sb.append("  classDef exam fill:#3D2E0B,stroke:#FBBF24,color:#EEF2F7,stroke-width:2px\n")
        sb.append("  classDef warning fill:#451919,stroke:#F87171,color:#EEF2F7,stroke-width:2px\n")
        sb.append("  classDef outcome fill:#0E3A26,stroke:#34D399,color:#EEF2F7,stroke-width:2px\n")
        for (category in CATEGORIES) {
            val members = nodes.filter { normalizeCategory(it.category) == category }.map { safeId(it.id) }
            if (members.isNotEmpty()) sb.append("  class ").append(members.joinToString(",")).append(' ').append(category).append('\n')
        }
        return sb.toString().trimEnd()
    }

    /** Keeps only Mermaid code that starts with a known diagram keyword; strips fences. */
    fun sanitizeModelCode(code: String): String {
        val cleaned = code.replace("`", "").trim()
        val first = cleaned.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.lowercase() ?: return ""
        val ok = listOf("graph ", "flowchart ", "sequencediagram", "statediagram", "classdiagram", "mindmap")
        return if (ok.any { first.startsWith(it) }) cleaned else ""
    }

    fun normalizeCategory(raw: String): String {
        val c = raw.trim().lowercase()
        return if (c in CATEGORIES) c else "mechanism"
    }

    private fun safeId(id: String): String {
        val cleaned = id.filter { it.isLetterOrDigit() || it == '_' }
        return if (cleaned.isEmpty() || cleaned[0].isDigit()) "n$cleaned" else cleaned
    }

    private fun escape(text: String): String = text
        .replace("\"", "#quot;")
        .replace("\n", " ")
        .replace("\r", " ")
        .trim()
}
