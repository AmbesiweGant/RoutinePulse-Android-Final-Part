package com.routinepulse.app

import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.QuizDto
import com.routinepulse.app.domain.Achievements
import com.routinepulse.app.domain.Badge
import com.routinepulse.app.domain.BoardLogic
import com.routinepulse.app.domain.ProgressStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BoardAndAchievementsTest {
    private val wednesday = LocalDate.of(2024, 1, 10)

    private fun habit(id: String, done: List<String>, today: Boolean = false, frequency: String = "daily", created: String? = null) =
        HabitDto(id = id, title = id, frequency = frequency, completionDates = done, completedToday = today, createdAt = created)

    // ---------------- streak board ----------------

    @Test fun weeks_are_monday_aligned_and_future_days_are_null() {
        val weeks = BoardLogic.weeks(wednesday, 2)
        assertEquals(2, weeks.size)
        assertEquals(LocalDate.of(2024, 1, 1), weeks[0][0])   // previous Monday
        assertEquals(LocalDate.of(2024, 1, 7), weeks[0][6])   // previous Sunday
        assertEquals(LocalDate.of(2024, 1, 8), weeks[1][0])   // this Monday
        assertEquals(wednesday, weeks[1][2])
        assertNull(weeks[1][3])                               // Thursday has not happened yet
        assertNull(weeks[1][6])
    }

    @Test fun ratio_is_share_of_scheduled_habits_done() {
        val habits = listOf(habit("a", listOf("2024-01-09")), habit("b", emptyList()))
        assertEquals(0.5f, BoardLogic.ratio(habits, LocalDate.of(2024, 1, 9))!!, 0.0001f)
    }

    @Test fun ratio_is_null_when_nothing_is_scheduled() {
        val weekdaysOnly = listOf(habit("a", emptyList(), frequency = "weekdays"))
        assertNull(BoardLogic.ratio(weekdaysOnly, LocalDate.of(2024, 1, 6))) // a Saturday
    }

    @Test fun habits_do_not_count_against_days_before_they_existed() {
        val h = habit("a", emptyList(), created = "2024-01-09T08:00:00Z")
        assertNull(BoardLogic.ratio(listOf(h), LocalDate.of(2024, 1, 1)))
    }

    @Test fun level_buckets() {
        assertEquals(-1, BoardLogic.level(null))
        assertEquals(0, BoardLogic.level(0f))
        assertEquals(1, BoardLogic.level(0.25f))
        assertEquals(2, BoardLogic.level(0.75f))
        assertEquals(3, BoardLogic.level(1f))
    }

    @Test fun completionRate_over_a_window() {
        // one daily habit, done on 2 of the last 4 days (10th, 9th done; 8th, 7th missed)
        val h = habit("a", listOf("2024-01-10", "2024-01-09"))
        assertEquals(50, BoardLogic.completionRate(listOf(h), wednesday, 4))
        assertEquals(0, BoardLogic.completionRate(emptyList(), wednesday, 30))
    }

    // ---------------- badges + levels ----------------

    @Test fun no_activity_earns_nothing() {
        assertTrue(Achievements.earned(ProgressStats()).isEmpty())
    }

    @Test fun streak_badges_unlock_at_their_thresholds() {
        assertEquals(setOf(Badge.FIRST_STEP, Badge.STREAK_3), Achievements.earned(ProgressStats(totalCheckIns = 3, longestStreak = 3)))
        val seven = Achievements.earned(ProgressStats(totalCheckIns = 7, longestStreak = 7))
        assertTrue(Badge.STREAK_7 in seven)
        assertFalse(Badge.STREAK_14 in seven)
        assertTrue(Badge.STREAK_30 in Achievements.earned(ProgressStats(totalCheckIns = 30, longestStreak = 30)))
    }

    @Test fun collector_notes_and_quiz_badges() {
        val stats = ProgressStats(habitCount = 5, noteCount = 5, quizAttempts = 1, hasPerfectQuiz = true, perfectToday = true)
        val earned = Achievements.earned(stats)
        assertTrue(earned.containsAll(listOf(Badge.HABIT_COLLECTOR, Badge.NOTE_TAKER, Badge.QUIZ_STARTER, Badge.QUIZ_ACE, Badge.PERFECT_DAY)))
    }

    @Test fun level_rises_every_ten_check_ins() {
        assertEquals(1, Achievements.level(0))
        assertEquals(1, Achievements.level(9))
        assertEquals(2, Achievements.level(10))
        assertEquals(0.5f, Achievements.levelProgress(15), 0.0001f)
    }

    @Test fun statsFrom_builds_the_numbers_from_habits_and_quizzes() {
        val a = habit("a", listOf("2024-01-08", "2024-01-09", "2024-01-10"), today = true)
        val b = habit("b", listOf("2024-01-10"), today = true)
        val quizzes = listOf(QuizDto(id = "q", questionCount = 5, lastScore = 5, attemptCount = 2))
        val stats = Achievements.statsFrom(listOf(a, b), noteCount = 3, quizzes = quizzes, today = wednesday)
        assertEquals(2, stats.habitCount)
        assertEquals(4, stats.totalCheckIns)
        assertEquals(3, stats.longestStreak)
        assertTrue(stats.perfectToday)          // both habits are due and done
        assertEquals(3, stats.noteCount)
        assertEquals(2, stats.quizAttempts)
        assertTrue(stats.hasPerfectQuiz)
    }

    @Test fun perfectDay_needs_at_least_two_habits() {
        val only = habit("a", listOf("2024-01-10"), today = true)
        assertFalse(Achievements.statsFrom(listOf(only), 0, null, wednesday).perfectToday)
    }

    @Test fun offline_quizzes_do_not_award_quiz_badges() {
        val stats = Achievements.statsFrom(emptyList(), 0, null, wednesday)
        assertEquals(0, stats.quizAttempts)
        assertFalse(stats.hasPerfectQuiz)
    }
}
