package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {
    @Test fun parsesSrtWithBomAndCrlf() {
        val srt = "﻿1\r\n00:00:01,000 --> 00:00:04,500\r\nWelcome to the lecture.\r\n\r\n2\r\n00:00:05,000 --> 00:00:08,000\r\n<i>Action</i> potential\r\nbegins here.\r\n"
        val cues = SubtitleParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(4500L, cues[0].endMs)
        assertEquals("Action potential begins here.", cues[1].text)
    }

    @Test fun parsesVttWithHeaderNotesAndShortTimestamps() {
        val vtt = "WEBVTT\n\nNOTE a comment\n\nintro\n00:01.250 --> 00:03.000 align:start\nHello {\\an8}world\n\n01:02:03.004 --> 01:02:05.000\nLate cue\n"
        val cues = SubtitleParser.parse(vtt)
        assertEquals(2, cues.size)
        assertEquals(1250L, cues[0].startMs)
        assertEquals("Hello world", cues[0].text)
        assertEquals(3723004L, cues[1].startMs)
    }

    @Test fun skipsGarbageAndEmptyCues() {
        assertTrue(SubtitleParser.parse("not a subtitle file").isEmpty())
        assertTrue(SubtitleParser.parse("1\n00:00:01,000 --> 00:00:02,000\n\n").isEmpty())
    }

    @Test fun groupsCuesIntoSegments() {
        val cues = (0 until 10).map { SubtitleCue(it * 5000L, it * 5000L + 4000, "line$it") }
        val groups = SubtitleParser.group(cues, 20_000L)
        assertEquals(3, groups.size)
        assertEquals("line0 line1 line2 line3", groups[0].text)
        assertEquals(20L, groups[1].startSeconds)
        assertEquals("00:40", groups[2].timeLabel)
    }

    @Test fun formatsTime() {
        assertEquals("00:09", SubtitleParser.formatTime(9_000))
        assertEquals("1:02:03", SubtitleParser.formatTime(3_723_000))
        assertEquals("00:00", SubtitleParser.formatTime(-5))
    }
}
