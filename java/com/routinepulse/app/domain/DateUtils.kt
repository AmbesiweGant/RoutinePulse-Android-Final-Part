package com.routinepulse.app.domain

import java.time.LocalDate
import java.util.Locale

enum class Greeting { MORNING, AFTERNOON, EVENING }

object DateUtils {
    /** The last [n] days ending on [today], oldest first (used by the streak board). */
    fun lastNDays(today: LocalDate, n: Int): List<LocalDate> =
        (n - 1 downTo 0).map { today.minusDays(it.toLong()) }

    fun greetingFor(hour: Int): Greeting = when {
        hour < 12 -> Greeting.MORNING
        hour < 18 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }

    /** "07:30" -> 450 minutes after midnight, or null when the text is not a valid HH:mm time. */
    fun parseTimeToMinutes(time: String?): Int? {
        if (time == null) return null
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(time.trim()) ?: return null
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        return if (hours in 0..23 && minutes in 0..59) hours * 60 + minutes else null
    }

    fun formatTime(hour: Int, minute: Int): String = String.format(Locale.US, "%02d:%02d", hour, minute)
}
