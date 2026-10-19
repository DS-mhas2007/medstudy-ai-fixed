package com.example.core

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS (Free Spaced Repetition Scheduler v4) implementation for medical study.
 *
 * Implements the DSR (Difficulty, Stability, Retrievability) memory model:
 * - Retrievability R(t, S) = (1 + factor * (t / S))^(-1)
 *   represents the estimated probability of recall after t days.
 * - Stability S represents the storage strength of the memory in days (interval for 90% retention).
 * - Difficulty D (1.0 to 10.0) represents how hard the knowledge item is to remember.
 *
 * Fully standalone Kotlin object with zero Android dependencies, 100% testable.
 */
object FsrsScheduler {
    const val REQUESTED_RETENTION = 0.90f // 90% retention target
    const val DECAY_FACTOR = -0.5f
    const val FACTOR = 19.0f / 81.0f // Standard FSRS-4 factor for (1 + factor * t / S)^-w

    // Initial stability values for each first review grade (in days)
    private val INITIAL_STABILITY = floatArrayOf(0.4f, 1.2f, 3.2f, 8.5f)

    // Initial difficulty default: 5.0 on a 1.0..10.0 scale
    const val DEFAULT_DIFFICULTY = 5.0f

    /**
     * Calculates the estimated retrievability (probability of recall) given
     * elapsed days since last review and current stability.
     */
    fun calculateRetrievability(elapsedDays: Float, stability: Float): Float {
        if (stability <= 0.05f) return 0.1f
        if (elapsedDays <= 0f) return 1.0f
        return (1.0f + FACTOR * (elapsedDays / stability)).pow(-1.0f).coerceIn(0.01f, 1.0f)
    }

    /**
     * Calculates the optimal review interval (in days) to achieve the target retention.
     */
    fun calculateNextInterval(stability: Float, targetRetention: Float = REQUESTED_RETENTION): Int {
        if (stability <= 0.2f) return 1
        // Interval = (S / FACTOR) * (targetRetention^(-1) - 1)
        val interval = (stability / FACTOR) * ((1.0f / targetRetention) - 1.0f)
        return max(1, interval.roundToInt())
    }

    /**
     * Computes the next state of a flashcard using FSRS principles.
     */
    fun next(
        currentStability: Float,
        currentDifficulty: Float,
        currentRepetitions: Int,
        grade: ReviewGrade,
        elapsedDays: Float,
        nowMillis: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault()
    ): FsrsCardState {
        val gradeIndex = grade.value.coerceIn(0, 3)

        val nextDifficulty: Float
        val nextStability: Float
        val nextState: String
        val lapses: Int

        if (currentRepetitions == 0 || currentStability <= 0f) {
            // Initial learning step
            nextStability = INITIAL_STABILITY[gradeIndex]
            nextDifficulty = (DEFAULT_DIFFICULTY - (grade.value - 2) * 0.7f).coerceIn(1.0f, 10.0f)
            nextState = if (grade == ReviewGrade.AGAIN) "LEARNING" else "REVIEW"
            lapses = if (grade == ReviewGrade.AGAIN) 1 else 0
        } else {
            val r = calculateRetrievability(elapsedDays, currentStability)

            // Update difficulty: mean reversion toward 5.0 + grade delta
            val dDelta = -(grade.value - 2) * 0.6f
            nextDifficulty = (currentDifficulty + dDelta * 0.8f).coerceIn(1.0f, 10.0f)

            if (grade == ReviewGrade.AGAIN) {
                // Lapse: severe stability drop, enter relearning
                nextStability = max(0.3f, currentStability * 0.25f)
                nextState = "RELEARNING"
                lapses = 1
            } else {
                // Successful recall
                val gradeBonus = when (grade) {
                    ReviewGrade.HARD -> 0.75f
                    ReviewGrade.GOOD -> 1.0f
                    ReviewGrade.EASY -> 1.35f
                    ReviewGrade.AGAIN -> 0.2f
                }
                // Stability growth depends on retrievability: harder recalls (low R) produce higher stability growth!
                val growthFactor = 1.0f + (11.0f - nextDifficulty) * 0.22f * (2.0f - r) * gradeBonus
                nextStability = max(currentStability + 0.5f, currentStability * growthFactor)
                nextState = "REVIEW"
                lapses = 0
            }
        }

        val nextIntervalDays = if (grade == ReviewGrade.AGAIN) 0 else calculateNextInterval(nextStability)
        val dueAtMillis = if (grade == ReviewGrade.AGAIN) {
            // Re-learn in 10 minutes
            nowMillis + 10 * 60 * 1000L
        } else {
            // Align to midnight of local day after nextIntervalDays
            alignToMidnight(nowMillis, nextIntervalDays, zone)
        }

        return FsrsCardState(
            stability = nextStability,
            difficulty = nextDifficulty,
            repetitions = currentRepetitions + 1,
            lapses = lapses,
            intervalDays = nextIntervalDays,
            dueAtMillis = dueAtMillis,
            state = nextState,
            retrievability = calculateRetrievability(0f, nextStability)
        )
    }

    /**
     * Preview predicted intervals for the 4 answer buttons.
     */
    fun previewIntervals(
        stability: Float,
        difficulty: Float,
        repetitions: Int,
        elapsedDays: Float
    ): Map<ReviewGrade, String> {
        return ReviewGrade.entries.associateWith { grade ->
            if (repetitions == 0) {
                when (grade) {
                    ReviewGrade.AGAIN -> "< 10 د"
                    ReviewGrade.HARD -> "يوم 1"
                    ReviewGrade.GOOD -> "3 أيام"
                    ReviewGrade.EASY -> "8 أيام"
                }
            } else {
                when (grade) {
                    ReviewGrade.AGAIN -> "< 10 د"
                    else -> {
                        val predicted = next(stability, difficulty, repetitions, grade, elapsedDays)
                        formatDays(predicted.intervalDays)
                    }
                }
            }
        }
    }

    private fun formatDays(days: Int): String {
        return when {
            days <= 1 -> "يوم 1"
            days == 2 -> "يومان"
            days in 3..10 -> "$days أيام"
            days in 11..30 -> "$days يوماً"
            days in 31..60 -> "شهر"
            days > 60 -> "${days / 30} أشهر"
            else -> "$days يوماً"
        }
    }

    private fun alignToMidnight(nowMillis: Long, addDays: Int, zone: TimeZone): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, addDays)
        }
        return cal.timeInMillis
    }
}

/** Complete FSRS state output for a card review */
data class FsrsCardState(
    val stability: Float,
    val difficulty: Float,
    val repetitions: Int,
    val lapses: Int,
    val intervalDays: Int,
    val dueAtMillis: Long,
    val state: String,
    val retrievability: Float
)
