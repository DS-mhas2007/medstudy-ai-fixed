package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FsrsSchedulerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = Calendar.getInstance(utc).apply {
        clear()
        set(2026, Calendar.OCTOBER, 18, 10, 0, 0)
    }.timeInMillis

    @Test
    fun calculateRetrievability_atZeroDays_isOne() {
        val r = FsrsScheduler.calculateRetrievability(0f, 5.0f)
        assertEquals(1.0f, r, 0.001f)
    }

    @Test
    fun calculateRetrievability_dropsAsTimePasses() {
        val r1 = FsrsScheduler.calculateRetrievability(1f, 5.0f)
        val r5 = FsrsScheduler.calculateRetrievability(5f, 5.0f)
        val r20 = FsrsScheduler.calculateRetrievability(20f, 5.0f)

        assertTrue(r1 > r5)
        assertTrue(r5 > r20)
        assertTrue(r20 > 0f)
    }

    @Test
    fun initialLearning_setsAppropriateState() {
        val againState = FsrsScheduler.next(
            currentStability = 0f,
            currentDifficulty = 5.0f,
            currentRepetitions = 0,
            grade = ReviewGrade.AGAIN,
            elapsedDays = 0f,
            nowMillis = now,
            zone = utc
        )
        assertEquals("LEARNING", againState.state)
        assertEquals(1, againState.lapses)
        assertTrue(againState.stability < 1.0f)

        val goodState = FsrsScheduler.next(
            currentStability = 0f,
            currentDifficulty = 5.0f,
            currentRepetitions = 0,
            grade = ReviewGrade.GOOD,
            elapsedDays = 0f,
            nowMillis = now,
            zone = utc
        )
        assertEquals("REVIEW", goodState.state)
        assertEquals(0, goodState.lapses)
        assertTrue(goodState.stability > againState.stability)
    }

    @Test
    fun reviewPass_increasesStability() {
        val initialStability = 3.0f
        val nextState = FsrsScheduler.next(
            currentStability = initialStability,
            currentDifficulty = 5.0f,
            currentRepetitions = 2,
            grade = ReviewGrade.GOOD,
            elapsedDays = 3.0f,
            nowMillis = now,
            zone = utc
        )

        assertTrue(nextState.stability > initialStability)
        assertTrue(nextState.intervalDays >= 1)
        assertEquals(3, nextState.repetitions)
    }

    @Test
    fun easyGrade_yieldsLongerIntervalThanGood() {
        val goodState = FsrsScheduler.next(
            currentStability = 4.0f,
            currentDifficulty = 5.0f,
            currentRepetitions = 3,
            grade = ReviewGrade.GOOD,
            elapsedDays = 4.0f,
            nowMillis = now,
            zone = utc
        )
        val easyState = FsrsScheduler.next(
            currentStability = 4.0f,
            currentDifficulty = 5.0f,
            currentRepetitions = 3,
            grade = ReviewGrade.EASY,
            elapsedDays = 4.0f,
            nowMillis = now,
            zone = utc
        )

        assertTrue(easyState.intervalDays >= goodState.intervalDays)
        assertTrue(easyState.stability >= goodState.stability)
    }

    @Test
    fun againGrade_triggersLapseAndRelearning() {
        val lapsedState = FsrsScheduler.next(
            currentStability = 15.0f,
            currentDifficulty = 4.0f,
            currentRepetitions = 6,
            grade = ReviewGrade.AGAIN,
            elapsedDays = 15.0f,
            nowMillis = now,
            zone = utc
        )

        assertEquals("RELEARNING", lapsedState.state)
        assertEquals(1, lapsedState.lapses)
        assertTrue(lapsedState.stability < 15.0f)
        assertEquals(0, lapsedState.intervalDays)
        assertEquals(now + 10 * 60 * 1000L, lapsedState.dueAtMillis)
    }
}
