package com.routinepulse.app.domain

import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.QuizDto
import java.time.LocalDate

/** Lightweight gamification (user-defined feature 4): milestone badges and a simple level. */
enum class Badge {
    FIRST_STEP, STREAK_3, STREAK_7, STREAK_14, STREAK_30,
    HABIT_COLLECTOR, PERFECT_DAY, NOTE_TAKER, QUIZ_STARTER, QUIZ_ACE;

    companion object {
        fun fromName(name: String): Badge? = entries.firstOrNull { it.name == name }
    }
}

data class ProgressStats(
    val habitCount: Int = 0,
    val totalCheckIns: Int = 0,
    val longestStreak: Int = 0,
    val perfectToday: Boolean = false,
    val noteCount: Int = 0,
    val quizAttempts: Int = 0,
    val hasPerfectQuiz: Boolean = false
)

object Achievements {
    const val CHECK_INS_PER_LEVEL = 10

    /** Which badges these numbers earn. Badges are never taken away once earned (see BadgeTracker). */
    fun earned(s: ProgressStats): Set<Badge> = buildSet {
        if (s.totalCheckIns >= 1) add(Badge.FIRST_STEP)
        if (s.longestStreak >= 3) add(Badge.STREAK_3)
        if (s.longestStreak >= 7) add(Badge.STREAK_7)
        if (s.longestStreak >= 14) add(Badge.STREAK_14)
        if (s.longestStreak >= 30) add(Badge.STREAK_30)
        if (s.habitCount >= 5) add(Badge.HABIT_COLLECTOR)
        if (s.perfectToday) add(Badge.PERFECT_DAY)
        if (s.noteCount >= 5) add(Badge.NOTE_TAKER)
        if (s.quizAttempts >= 1) add(Badge.QUIZ_STARTER)
        if (s.hasPerfectQuiz) add(Badge.QUIZ_ACE)
    }

    fun level(totalCheckIns: Int): Int = 1 + totalCheckIns / CHECK_INS_PER_LEVEL

    /** 0..1 progress towards the next level. */
    fun levelProgress(totalCheckIns: Int): Float = (totalCheckIns % CHECK_INS_PER_LEVEL).toFloat() / CHECK_INS_PER_LEVEL

    /**
     * Builds the numbers from the habit list. [noteCount] / [quizzes] are optional (quizzes need the network),
     * so badges that depend on them are simply not awarded until the data is available.
     */
    fun statsFrom(habits: List<HabitDto>, noteCount: Int, quizzes: List<QuizDto>?, today: LocalDate): ProgressStats {
        val due = HabitLogic.dueToday(habits)
        return ProgressStats(
            habitCount = habits.size,
            totalCheckIns = habits.sumOf { it.completionDates?.size ?: 0 },
            longestStreak = habits.maxOfOrNull {
                StreakCalculator.longest(it.frequency, (it.customDays ?: emptyList()).toSet(), (it.completionDates ?: emptyList()).toSet(), today)
            } ?: 0,
            perfectToday = due.size >= 2 && due.all { it.completedToday },
            noteCount = noteCount,
            quizAttempts = quizzes?.sumOf { it.attemptCount } ?: 0,
            hasPerfectQuiz = quizzes?.any { it.questionCount > 0 && it.lastScore == it.questionCount } ?: false
        )
    }
}
