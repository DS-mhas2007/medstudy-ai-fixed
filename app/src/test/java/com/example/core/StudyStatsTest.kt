package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StudyStatsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(day: Int, hour: Int = 12) =
        Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, day, hour, 0, 0) }.timeInMillis

    @Test fun streakCountsConsecutiveDaysEndingToday() {
        val acts = listOf(at(15, 9), at(15, 20), at(14), at(13), at(11))
        assertEquals(3, StreakCalculator.currentStreak(acts, at(15, 22), utc))
    }

    @Test fun streakSurvivesUntilEndOfTodayIfYesterdayStudied() {
        assertEquals(2, StreakCalculator.currentStreak(listOf(at(14), at(13)), at(15, 8), utc))
    }

    @Test fun streakBreaksAfterAMissedDay() {
        assertEquals(0, StreakCalculator.currentStreak(listOf(at(12), at(11)), at(15), utc))
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), at(15), utc))
    }

    @Test fun isoDateAndDiff() {
        assertEquals("2026-10-15", DateUtils.isoDate(at(15), 0, utc))
        assertEquals("2026-10-16", DateUtils.isoDate(at(15), 1, utc))
        assertEquals("2026-11-01", DateUtils.isoDate(at(31), 1, utc))
        assertEquals(3, DateUtils.daysBetweenIso("2026-10-15", "2026-10-18"))
        assertEquals(-1, DateUtils.daysBetweenIso("2026-10-15", "2026-10-14"))
        assertNull(DateUtils.daysBetweenIso("اليوم", "2026-10-14"))
        assertNull(DateUtils.daysBetweenIso("2026-02-30", "2026-03-01"))
    }

    @Test fun isIsoDate() {
        assertTrue(DateUtils.isIsoDate("2026-10-15"))
        assertTrue(!DateUtils.isIsoDate("اليوم"))
        assertTrue(!DateUtils.isIsoDate("2026-13-01"))
    }

    @Test fun retentionProgress() {
        assertEquals(0f, StudyStats().retentionProgress, 0f)
        assertEquals(0.25f, StudyStats(totalCards = 8, matureCards = 2).retentionProgress, 0.0001f)
    }
}
