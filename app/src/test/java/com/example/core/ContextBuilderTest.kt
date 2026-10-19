package com.example.core

import org.junit.Assert.*
import org.junit.Test

class ContextBuilderTest {
    @Test fun repeatedParagraphIsKeptOnce() {
        val p = "The sodium-potassium pump moves three sodium ions out for two potassium ions in."
        val out = ContextBuilder.prepare("$p\n\nOther text.\n\n$p", 1000).text
        assertEquals(1, Regex("sodium-potassium pump").findAll(out).count())
        assertTrue(out.contains("Other text."))
    }

    @Test fun shortRepeatedParagraphsSurvive() {
        val out = ContextBuilder.prepare("Yes\n\nNo\n\nYes\n\nNo", 1000).text
        assertEquals(4, out.split("\n\n").size)
    }

    @Test fun typographicBulletsBecomeDashes() {
        val out = ContextBuilder.prepare("• first\n▪ second\n– third", 1000).text
        assertEquals("- first\n- second\n- third", out)
    }

    @Test fun longTextIsCutAtAParagraphEnd() {
        val para = "Alpha beta gamma. ".repeat(10).trim()
        val text = (1..10).joinToString("\n\n") { "P$it $para" }
        val prepared = ContextBuilder.prepare(text, 500)
        assertTrue(prepared.truncated)
        assertTrue(prepared.text.length <= 500)
        assertTrue(prepared.text.endsWith("gamma."))
        assertFalse(prepared.text.contains("\n\n\n"))
    }

    @Test fun cutPrefersSentenceThenWordBoundary() {
        val sentences = "One two three. Four five six. Seven eight nine. Ten eleven twelve."
        val cut = ContextBuilder.smartCut(sentences, 40)
        assertEquals("One two three. Four five six.", cut)
        val words = "alpha beta gamma delta epsilon"
        assertEquals("alpha beta gamma", ContextBuilder.smartCut(words, 18))
        assertEquals("abcdefghij", ContextBuilder.smartCut("abcdefghijklmnop", 10))
    }

    @Test fun shortTextIsNotTruncated() {
        val p = ContextBuilder.prepare("Hello world", 100)
        assertFalse(p.truncated)
        assertEquals("Hello world", p.text)
    }
}
