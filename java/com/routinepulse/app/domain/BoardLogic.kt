package com.routinepulse.app.domain

import com.routinepulse.app.data.model.HabitDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Logic behind the GitHub-style streak board (user-defined feature 5). */
object BoardLogic {
    /**
     * Calendar grid: [weekCount] columns (oldest first), each with 7 cells Monday..Sunday.
     * Days after [today] are null (they have not happened yet).
     */
    fun weeks(today: LocalDate, weekCount: Int): List<List<LocalDate?>> {
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val firstMonday = thisMonday.minusWeeks((weekCount - 1).toLong())
        return (0 until weekCount).map { w ->
            (0 until 7).map { d ->
                val date = firstMonday.plusWeeks(w.toLong()).plusDays(d.toLong())
                if (date.isAfter(today)) null else date
            }
        }
    }

    private fun createdOn(habit: HabitDto): LocalDate? =
        habit.createdAt?.let {
            runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        }

    /**
     * Share of the habits scheduled on [date] that were completed (0..1), or null on a day when nothing was scheduled.
     * A habit does not count against days before it was created.
     */
    fun ratio(habits: List<HabitDto>, date: LocalDate): Float? {
        var scheduled = 0
        var done = 0
        for (h in habits) {
            val created = createdOn(h)
            if (created != null && date.isBefore(created)) continue
            if (!StreakCalculator.isScheduled(h.frequency, (h.customDays ?: emptyList()).toSet(), date)) continue
            scheduled++
            if (h.completionDates?.contains(date.toString()) == true) done++
        }
        return if (scheduled == 0) null else done.toFloat() / scheduled
    }

    /** -1 = rest day (nothing scheduled), 0 = nothing done, 1..3 = increasing consistency. */
    fun level(ratio: Float?): Int = when {
        ratio == null -> -1
        ratio <= 0f -> 0
        ratio < 0.5f -> 1
        ratio < 1f -> 2
        else -> 3
    }

    /** Percentage (0..100) of scheduled habit-days completed over the last [days] days, including today. */
    fun completionRate(habits: List<HabitDto>, today: LocalDate, days: Int): Int {
        var scheduled = 0
        var done = 0
        for (offset in 0 until days) {
            val date = today.minusDays(offset.toLong())
            for (h in habits) {
                val created = createdOn(h)
                if (created != null && date.isBefore(created)) continue
                if (!StreakCalculator.isScheduled(h.frequency, (h.customDays ?: emptyList()).toSet(), date)) continue
                scheduled++
                if (h.completionDates?.contains(date.toString()) == true) done++
            }
        }
        return if (scheduled == 0) 0 else (done * 100) / scheduled
    }
}
