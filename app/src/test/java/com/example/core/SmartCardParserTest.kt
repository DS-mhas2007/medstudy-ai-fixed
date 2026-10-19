package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCardParserTest {

    @Test
    fun parseCloze_separatesTextAndBlanks() {
        val raw = "The {{c1::mitral valve}} prevents backflow into the left atrium."
        val segments = SmartCardParser.parseCloze(raw)

        assertEquals(3, segments.size)
        assertEquals("The ", segments[0].text)
        assertFalse(segments[0].isCloze)

        assertEquals("mitral valve", segments[1].text)
        assertTrue(segments[1].isCloze)
        assertEquals("mitral valve", segments[1].answer)

        assertEquals(" prevents backflow into the left atrium.", segments[2].text)
        assertFalse(segments[2].isCloze)
    }

    @Test
    fun formatQuestionSide_replacesWithBracketedHintOrEllipsis() {
        val rawNoHint = "The pacemaker of the heart is the {{c1::SA node}}."
        val q1 = SmartCardParser.formatQuestionSide(rawNoHint)
        assertEquals("The pacemaker of the heart is the [...].", q1)

        val rawWithHint = "The cell responsible for bone resorption is the {{c1::osteoclast::cell type}}."
        val q2 = SmartCardParser.formatQuestionSide(rawWithHint)
        assertEquals("The cell responsible for bone resorption is the [cell type].", q2)
    }

    @Test
    fun formatAnswerSide_revealsHiddenText() {
        val raw = "Normal intracranial pressure is between {{c1::7 to 15 mmHg::range}}."
        val answer = SmartCardParser.formatAnswerSide(raw)
        assertEquals("Normal intracranial pressure is between 7 to 15 mmHg.", answer)
    }

    @Test
    fun isClozeCard_detectsSyntaxCorrectly() {
        assertTrue(SmartCardParser.isClozeCard("Normal body temperature is {{c1::37°C}}."))
        assertFalse(SmartCardParser.isClozeCard("Normal body temperature is 37°C."))
    }

    @Test
    fun cleanOcrText_fixesHyphenationAndWhitespace() {
        val dirty = "The cardio-\nvascular system consists   of the heart and vessels.\n\n\nNext section."
        val clean = SmartCardParser.cleanOcrText(dirty)
        assertEquals("The cardiovascular system consists of the heart and vessels.\n\nNext section.", clean)
    }

    @Test
    fun wrapWordAsCloze_wrapsTargetProperly() {
        val input = "Insulin is secreted by beta cells."
        val wrapped = SmartCardParser.wrapWordAsCloze(input, "Insulin")
        assertEquals("{{c1::Insulin}} is secreted by beta cells.", wrapped)
    }
}
