package com.routinepulse.app.domain

import java.time.LocalDate

/**
 * Streak maths that runs ON THE PHONE so streaks work offline. It mirrors backend/src/services/streak.js
 * so the app and the server always agree.
 */
object StreakCalculator {
    private const val MAX_DAYS = 3660

    /** ISO weekday: Monday = 1 ... Sunday = 7 */
    fun isScheduled(frequency: String, customDays: Set<Int>, date: LocalDate): Boolean {
        val weekday = date.dayOfWeek.value
        return when (frequency) {
            "weekdays" -> weekday in 1..5
            "custom" -> weekday in customDays
            else -> true
        }
    }

    /**
     * Consecutive scheduled days completed, walking back from [today].
     * Rest days are skipped; a missed scheduled day in the past ends the streak; today not being done YET
     * does not break it.
     */
    fun current(frequency: String, customDays: Set<Int>, completed: Set<String>, today: LocalDate): Int {
        var streak = 0
        var cursor = today
        for (i in 0 until MAX_DAYS) {
            if (isScheduled(frequency, customDays, cursor)) {
                if (cursor.toString() in completed) streak++ else if (cursor != today) return streak
            }
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** The best streak this habit has EVER had (used for the "7-day streak" style badges). */
    fun longest(frequency: String, customDays: Set<Int>, completed: Set<String>, today: LocalDate): Int {
        if (completed.isEmpty()) return 0
        val earliest = completed.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull() ?: return 0
        var cursor = maxOf(earliest, today.minusDays(MAX_DAYS.toLong()))
        var best = 0
        var run = 0
        while (!cursor.isAfter(today)) {
            if (isScheduled(frequency, customDays, cursor)) {
                if (cursor.toString() in completed) {
                    run++
                    if (run > best) best = run
                } else if (cursor != today) {
                    run = 0
                }
            }
            cursor = cursor.plusDays(1)
        }
        return best
    }
}
