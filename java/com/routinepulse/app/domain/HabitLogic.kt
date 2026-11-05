package com.routinepulse.app.domain

import com.routinepulse.app.data.model.HabitDto

/** Pure functions that turn the habit list into the numbers shown on the dashboard. */
object HabitLogic {
    /** Habits that are scheduled for today (weekday/custom habits are hidden from the count on rest days). */
    fun dueToday(habits: List<HabitDto>): List<HabitDto> = habits.filter { it.scheduledToday }

    fun completedCount(habits: List<HabitDto>): Int = dueToday(habits).count { it.completedToday }

    fun progress(habits: List<HabitDto>): Float {
        val due = dueToday(habits)
        return if (due.isEmpty()) 0f else due.count { it.completedToday }.toFloat() / due.size
    }

    fun bestStreak(habits: List<HabitDto>): Int = habits.maxOfOrNull { it.streakCount } ?: 0

    /** The earliest reminder later today for a habit that is still not done, or null. */
    fun nextReminder(habits: List<HabitDto>, nowMinutes: Int): HabitDto? =
        dueToday(habits)
            .filter { !it.completedToday }
            .mapNotNull { h -> DateUtils.parseTimeToMinutes(h.reminderTime)?.let { m -> h to m } }
            .filter { it.second >= nowMinutes }
            .minByOrNull { it.second }
            ?.first
}
