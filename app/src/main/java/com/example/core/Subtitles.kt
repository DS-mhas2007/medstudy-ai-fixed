package com.example.core

/** One timed line of a subtitle file. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/** Several cues merged into a block that is comfortable to read, translate or explain. */
data class TranscriptSegment(val startMs: Long, val text: String) {
    val startSeconds: Long get() = startMs / 1000
    val timeLabel: String get() = SubtitleParser.formatTime(startMs)
}

/**
 * Parser for the two subtitle formats lecture platforms export: SubRip (.srt) and WebVTT (.vtt).
 * Tolerant by design: unknown blocks are skipped instead of failing the whole import.
 */
object SubtitleParser {
    private val timing = Regex(
        """(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})\s*-->\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})"""
    )
    private val tags = Regex("""<[^>]*>|\{\\[^}]*\}""")
    private val spaces = Regex("""[ \t]+""")

    fun parse(raw: String): List<SubtitleCue> {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val cues = ArrayList<SubtitleCue>()
        for (block in text.split(Regex("\n{2,}"))) {
            val lines = block.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val timingIndex = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (timingIndex < 0) continue // WEBVTT header, NOTE, STYLE blocks, ...
            val match = timing.find(lines[timingIndex]) ?: continue
            val g = match.groupValues
            val start = toMillis(g[1], g[2], g[3], g[4])
            val end = toMillis(g[5], g[6], g[7], g[8])
            val body = lines.drop(timingIndex + 1)
                .joinToString(" ") { it.replace(tags, "").replace(spaces, " ").trim() }
                .trim()
            if (body.isNotEmpty() && end >= start) cues.add(SubtitleCue(start, end, body))
        }
        return cues.sortedBy { it.startMs }
    }

    /** Merge consecutive cues into segments of roughly [targetMs] so the list stays short. */
    fun group(cues: List<SubtitleCue>, targetMs: Long = 20_000L): List<TranscriptSegment> {
        if (cues.isEmpty()) return emptyList()
        val result = ArrayList<TranscriptSegment>()
        var start = cues.first().startMs
        val buffer = StringBuilder()
        for (cue in cues) {
            if (buffer.isNotEmpty() && cue.startMs - start >= targetMs) {
                result.add(TranscriptSegment(start, buffer.toString().trim()))
                buffer.clear()
                start = cue.startMs
            }
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(cue.text)
        }
        if (buffer.isNotEmpty()) result.add(TranscriptSegment(start, buffer.toString().trim()))
        return result
    }

    fun formatTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun toMillis(h: String, m: String, s: String, frac: String): Long {
        val hours = h.toLongOrNull() ?: 0L
        val millis = frac.padEnd(3, '0').take(3).toLong()
        return ((hours * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + millis
    }
}
