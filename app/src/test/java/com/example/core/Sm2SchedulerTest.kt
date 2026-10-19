package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class Sm2SchedulerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 15, 14, 30, 0) }.timeInMillis
    private val fresh = SrsState()

    private fun step(state: SrsState, vararg grades: ReviewGrade): SrsState =
        grades.fold(state) { s, g -> Sm2Scheduler.next(s, g, now, utc) }

    @Test fun goodProgression_isOneThreeThenEaseMultiplied() {
        val s1 = step(fresh, ReviewGrade.GOOD)
        assertEquals(1, s1.intervalDays)
        val s2 = step(s1, ReviewGrade.GOOD)
        assertEquals(3, s2.intervalDays)
        val s3 = step(s2, ReviewGrade.GOOD)
        assertEquals(8, s3.intervalDays) // 3 * 2.5 = 7.5 -> 8
        assertEquals(3, s3.repetitions)
    }

    @Test fun again_resetsAndReturnsWithinMinutes() {
        val mature = SrsState(intervalDays = 30, repetitions = 5, easeFactor = 2.5f)
        val s = step(mature, ReviewGrade.AGAIN)
        assertEquals(0, s.repetitions)
        assertEquals(1, s.lapses)
        assertEquals(2.3f, s.easeFactor, 0.001f)
        assertEquals(now + Sm2Scheduler.RELEARN_DELAY_MILLIS, s.dueAtMillis)
    }

    @Test fun hard_isAPassWithSlowGrowthAndLowerEase() {
        val s = step(SrsState(intervalDays = 10, repetitions = 3, easeFactor = 2.5f), ReviewGrade.HARD)
        assertEquals(12, s.intervalDays)
        assertEquals(4, s.repetitions)
        assertEquals(2.35f, s.easeFactor, 0.001f)
    }

    @Test fun easy_growsFastAndRaisesEase() {
        val s = step(SrsState(intervalDays = 10, repetitions = 3, easeFactor = 2.5f), ReviewGrade.EASY)
        assertEquals(33, s.intervalDays) // 10 * 2.5 * 1.3
        assertEquals(2.65f, s.easeFactor, 0.001f)
    }

    @Test fun easeStaysWithinBounds() {
        var s = SrsState(intervalDays = 5, repetitions = 3, easeFactor = 1.35f)
        repeat(10) { s = step(s, ReviewGrade.AGAIN) }
        assertEquals(Sm2Scheduler.MIN_EASE, s.easeFactor, 0.0001f)
        var e = SrsState(intervalDays = 5, repetitions = 3, easeFactor = 2.95f)
        repeat(10) { e = step(e, ReviewGrade.EASY) }
        assertEquals(Sm2Scheduler.MAX_EASE, e.easeFactor, 0.0001f)
    }

    @Test fun intervalAlwaysGrowsOnPass() {
        var s = SrsState(intervalDays = 1, repetitions = 2, easeFactor = 1.3f)
        repeat(6) {
            val n = step(s, ReviewGrade.GOOD)
            assertTrue(n.intervalDays > s.intervalDays)
            s = n
        }
    }

    @Test fun dueDateIsStartOfLocalDay() {
        val s = step(fresh, ReviewGrade.GOOD, ReviewGrade.GOOD) // 3 days
        val expected = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 18, 0, 0, 0) }.timeInMillis
        assertEquals(expected, s.dueAtMillis)
    }

    @Test fun startOfTomorrow_isMidnight() {
        val expected = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 16, 0, 0, 0) }.timeInMillis
        assertEquals(expected, Sm2Scheduler.startOfTomorrow(now, utc))
    }

    @Test fun previewMatchesActualScheduling() {
        val state = SrsState(intervalDays = 4, repetitions = 2, easeFactor = 2.0f)
        for (g in ReviewGrade.entries) {
            val expected = if (g == ReviewGrade.AGAIN) 0 else step(state, g).intervalDays
            assertEquals(expected, Sm2Scheduler.previewDays(state, g))
        }
    }

    @Test fun maturityAndNewness() {
        assertTrue(Sm2Scheduler.isMature(21))
        assertFalse(Sm2Scheduler.isMature(20))
        assertTrue(Sm2Scheduler.isNew(fresh, 0L))
        assertFalse(Sm2Scheduler.isNew(fresh, 5L))
    }

    @Test fun gradeFromValueIsTotal() {
        assertEquals(ReviewGrade.AGAIN, ReviewGrade.fromValue(-4))
        assertEquals(ReviewGrade.HARD, ReviewGrade.fromValue(1))
        assertEquals(ReviewGrade.EASY, ReviewGrade.fromValue(99))
    }
}
