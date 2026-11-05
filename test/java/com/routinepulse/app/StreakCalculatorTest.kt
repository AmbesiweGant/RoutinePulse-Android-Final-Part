package com.routinepulse.app

import com.routinepulse.app.domain.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Mirrors backend/test/streak.test.js - the phone and the server must agree on streaks. 2024-01-08 is a Monday. */
class StreakCalculatorTest {
    private val monday = LocalDate.of(2024, 1, 8)

    @Test fun daily_streak_counts_consecutive_days_ending_today() {
        assertEquals(3, StreakCalculator.current("daily", emptySet(), setOf("2024-01-08", "2024-01-07", "2024-01-06"), monday))
    }

    @Test fun streak_is_still_alive_when_today_is_not_done_yet() {
        assertEquals(2, StreakCalculator.current("daily", emptySet(), setOf("2024-01-07", "2024-01-06"), monday))
    }

    @Test fun a_missed_day_breaks_the_streak() {
        assertEquals(1, StreakCalculator.current("daily", emptySet(), setOf("2024-01-08", "2024-01-06", "2024-01-05"), monday))
    }

    @Test fun no_completions_means_zero() {
        assertEquals(0, StreakCalculator.current("daily", emptySet(), emptySet(), monday))
    }

    @Test fun weekday_habit_skips_the_weekend_without_breaking() {
        // Mon 8 done, Sat/Sun skipped, Fri 5 + Thu 4 done, Wed 3 missed
        assertEquals(3, StreakCalculator.current("weekdays", emptySet(), setOf("2024-01-08", "2024-01-05", "2024-01-04"), monday))
    }

    @Test fun custom_habit_only_counts_chosen_days() {
        val days = setOf(1, 3) // Mon, Wed
        assertTrue(StreakCalculator.isScheduled("custom", days, monday))
        assertFalse(StreakCalculator.isScheduled("custom", days, LocalDate.of(2024, 1, 9)))
        assertEquals(2, StreakCalculator.current("custom", days, setOf("2024-01-08", "2024-01-03"), monday))
    }

    @Test fun longest_finds_the_best_run_even_after_it_was_broken() {
        // 4-day run (1-4 Jan), a gap on the 5th, then a 2-day run
        val done = setOf("2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04", "2024-01-06", "2024-01-07")
        assertEquals(4, StreakCalculator.longest("daily", emptySet(), done, monday))
        // Today (Mon 8th) is not done yet, so the streak is still alive: Sun 7th and Sat 6th count, Fri 5th was missed.
        assertEquals(2, StreakCalculator.current("daily", emptySet(), done, monday))
    }

    @Test fun longest_is_zero_without_completions() {
        assertEquals(0, StreakCalculator.longest("daily", emptySet(), emptySet(), monday))
    }
}
