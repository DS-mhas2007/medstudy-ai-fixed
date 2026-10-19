package com.example.core

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Review quality, mirroring the four Anki answer buttons. */
enum class ReviewGrade(val value: Int) {
    AGAIN(0),
    HARD(1),
    GOOD(2),
    EASY(3);

    companion object {
        fun fromValue(value: Int): ReviewGrade = entries.firstOrNull { it.value == value } ?: when {
            value < 0 -> AGAIN
            else -> EASY
        }
    }
}

/** Immutable scheduling state of one flashcard. */
data class SrsState(
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val easeFactor: Float = Sm2Scheduler.DEFAULT_EASE,
    val lapses: Int = 0,
    val dueAtMillis: Long = 0L
)

/**
 * SuperMemo-2 style scheduler with the behaviour students expect from Anki:
 *  - "Again" resets the card and brings it back within minutes (re-learning),
 *  - "Hard" passes the card but grows the interval slowly and lowers ease,
 *  - "Good" follows the classic 1 -> 3 -> interval * ease progression,
 *  - "Easy" jumps ahead and raises ease.
 * Due dates of reviewed cards are aligned to the start of the local day so a card
 * scheduled for "3 days" becomes available at midnight, not at the exact minute you reviewed it.
 *
 * Pure Kotlin: no Android dependencies, fully unit tested.
 */
object Sm2Scheduler {
    const val DEFAULT_EASE = 2.5f
    const val MIN_EASE = 1.3f
    const val MAX_EASE = 3.0f
    const val MATURE_INTERVAL_DAYS = 21
    const val RELEARN_DELAY_MILLIS = 10 * 60 * 1000L
    private const val MAX_INTERVAL_DAYS = 3650

    fun next(
        current: SrsState,
        grade: ReviewGrade,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): SrsState {
        val newInterval = nextIntervalDays(current, grade)
        return when (grade) {
            ReviewGrade.AGAIN -> SrsState(
                intervalDays = 1,
                repetitions = 0,
                easeFactor = max(MIN_EASE, current.easeFactor - 0.20f),
                lapses = current.lapses + 1,
                dueAtMillis = nowMillis + RELEARN_DELAY_MILLIS
            )
            ReviewGrade.HARD -> SrsState(
                intervalDays = newInterval,
                repetitions = current.repetitions + 1,
                easeFactor = max(MIN_EASE, current.easeFactor - 0.15f),
                lapses = current.lapses,
                dueAtMillis = startOfDayPlusDays(nowMillis, newInterval, zone)
            )
            ReviewGrade.GOOD -> SrsState(
                intervalDays = newInterval,
                repetitions = current.repetitions + 1,
                easeFactor = current.easeFactor,
                lapses = current.lapses,
                dueAtMillis = startOfDayPlusDays(nowMillis, newInterval, zone)
            )
            ReviewGrade.EASY -> SrsState(
                intervalDays = newInterval,
                repetitions = current.repetitions + 1,
                easeFactor = min(MAX_EASE, current.easeFactor + 0.15f),
                lapses = current.lapses,
                dueAtMillis = startOfDayPlusDays(nowMillis, newInterval, zone)
            )
        }
    }

    /**
     * Interval (in days) the card would get for [grade]. 0 means "a few minutes" (re-learning).
     * Used by the UI to preview the outcome on each answer button.
     */
    fun previewDays(current: SrsState, grade: ReviewGrade): Int =
        if (grade == ReviewGrade.AGAIN) 0 else nextIntervalDays(current, grade)

    fun isMature(intervalDays: Int): Boolean = intervalDays >= MATURE_INTERVAL_DAYS

    fun isNew(state: SrsState, lastReviewedAtMillis: Long): Boolean =
        state.repetitions == 0 && lastReviewedAtMillis == 0L

    private fun nextIntervalDays(current: SrsState, grade: ReviewGrade): Int {
        val reps = current.repetitions
        val ease = current.easeFactor
        val last = max(1, current.intervalDays)
        val raw = when (grade) {
            ReviewGrade.AGAIN -> 1
            ReviewGrade.HARD -> when (reps) {
                0 -> 1
                1 -> 2
                else -> max(last + 1, (last * 1.2f).roundToInt())
            }
            ReviewGrade.GOOD -> when (reps) {
                0 -> 1
                1 -> 3
                else -> max(last + 1, (last * ease).roundToInt())
            }
            ReviewGrade.EASY -> when (reps) {
                0 -> 4
                1 -> 7
                else -> max(last + 2, (last * ease * 1.3f).roundToInt())
            }
        }
        return raw.coerceIn(1, MAX_INTERVAL_DAYS)
    }

    fun startOfDayPlusDays(
        nowMillis: Long,
        days: Int,
        zone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = nowMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.timeInMillis
    }

    /** First millisecond of tomorrow: every card due before this instant is "due today". */
    fun startOfTomorrow(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        startOfDayPlusDays(nowMillis, 1, zone)
}
