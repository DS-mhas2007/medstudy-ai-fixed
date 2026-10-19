package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiExporterTest {
    @Test fun headerAndRows() {
        val out = AnkiExporter.toAnkiText(listOf(ExportCard("Threshold", "-55 mV", "Opens Na+ channels", listOf("Physiology", "Action Potential"))))
        val lines = out.trimEnd().split("\n")
        assertEquals("#separator:tab", lines[0])
        assertEquals("#html:true", lines[1])
        assertEquals("#tags column:3", lines[2])
        val cols = lines[3].split("\t")
        assertEquals(3, cols.size)
        assertEquals("\"Threshold\"", cols[0])
        assertEquals("\"-55 mV<br><br><i>Opens Na+ channels</i>\"", cols[1])
        assertEquals("\"Physiology Action_Potential\"", cols[2])
    }

    @Test fun escapesQuotesTabsNewlinesAndHtml() {
        val out = AnkiExporter.toAnkiText(listOf(ExportCard("Say \"ah\"\tnow", "a < b\nc & d")))
        val row = out.trimEnd().split("\n").last()
        val cols = row.split("\t")
        assertEquals(3, cols.size)
        assertEquals("\"Say \"\"ah\"\" now\"", cols[0])
        assertEquals("\"a &lt; b<br>c &amp; d\"", cols[1])
    }

    @Test fun skipsIncompleteCards() {
        val cards = listOf(ExportCard("", "x"), ExportCard("y", " "), ExportCard("ok", "ok"))
        assertEquals(1, AnkiExporter.exportableCount(cards))
        assertFalse(AnkiExporter.toAnkiText(cards.take(2)).contains("\"x\""))
        assertTrue(AnkiExporter.toAnkiText(cards).contains("\"ok\""))
    }
}
