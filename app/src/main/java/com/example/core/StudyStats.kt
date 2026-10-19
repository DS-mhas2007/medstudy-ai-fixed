package com.example.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Aggregated numbers shown on the home dashboard. */
data class StudyStats(
    val streakDays: Int = 0,
    val reviewsToday: Int = 0,
    val minutesThisWeek: Int = 0,
    val totalCards: Int = 0,
    val matureCards: Int = 0,
    val dueToday: Int = 0
) {
    /** Share of cards that reached a mature interval, 0..1. */
    val retentionProgress: Float
        get() = if (totalCards == 0) 0f else matureCards.toFloat() / totalCards
}

object DateUtils {
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun startOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Days since 1970-01-01 in the given time zone (DST safe). */
    fun epochDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val offset = zone.getOffset(millis).toLong()
        return Math.floorDiv(millis + offset, DAY_MILLIS)
    }

    /** ISO date (yyyy-MM-dd) for [millis], plus [plusDays]. */
    fun isoDate(millis: Long, plusDays: Int = 0, zone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = millis
        cal.add(Calendar.DAY_OF_YEAR, plusDays)
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Days between two ISO dates (to - from). Returns null when either is not a valid ISO date. */
    fun daysBetweenIso(fromIso: String, toIso: String): Int? {
        val a = parseIso(fromIso) ?: return null
        val b = parseIso(toIso) ?: return null
        return Math.floorDiv(b - a, DAY_MILLIS).toInt()
    }

    fun isIsoDate(value: String): Boolean = parseIso(value) != null

    private fun parseIso(value: String): Long? {
        val parts = value.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (parts[0].length != 4 || m !in 1..12 || d !in 1..31) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.isLenient = false
        return try {
            cal.set(y, m - 1, d)
            cal.timeInMillis
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

object StreakCalculator {
    /**
     * Number of consecutive days with activity ending today. If there is no activity yet today the
     * streak is still alive as long as yesterday had some (the day is not over yet).
     */
    fun currentStreak(
        activityMillis: Collection<Long>,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (activityMillis.isEmpty()) return 0
        val days = activityMillis.map { DateUtils.epochDay(it, zone) }.toHashSet()
        val today = DateUtils.epochDay(nowMillis, zone)
        var cursor = if (today in days) today else today - 1
        var streak = 0
        while (cursor in days) {
            streak++
            cursor--
        }
        return streak
    }
}
