package com.routinepulse.app

import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.NoteDto
import com.routinepulse.app.domain.DateUtils
import com.routinepulse.app.domain.Greeting
import com.routinepulse.app.domain.HabitLogic
import com.routinepulse.app.domain.NoteLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DomainLogicTest {

    private fun habit(
        id: String,
        done: Boolean = false,
        streak: Int = 0,
        reminder: String? = null,
        scheduled: Boolean = true
    ) = HabitDto(id = id, title = "Habit $id", streakCount = streak, completedToday = done, reminderTime = reminder, scheduledToday = scheduled)

    // ---- HabitLogic ----

    @Test fun completedCount_and_progress_ignore_habits_not_scheduled_today() {
        val habits = listOf(habit("1", done = true), habit("2"), habit("3", scheduled = false))
        assertEquals(1, HabitLogic.completedCount(habits))
        assertEquals(0.5f, HabitLogic.progress(habits), 0.0001f)
    }

    @Test fun progress_is_zero_for_empty_list() {
        assertEquals(0f, HabitLogic.progress(emptyList()), 0.0001f)
    }

    @Test fun bestStreak_is_the_maximum() {
        assertEquals(7, HabitLogic.bestStreak(listOf(habit("1", streak = 3), habit("2", streak = 7))))
        assertEquals(0, HabitLogic.bestStreak(emptyList()))
    }

    @Test fun nextReminder_picks_earliest_upcoming_unfinished_habit() {
        val habits = listOf(
            habit("early", reminder = "06:00"),             // already passed at 10:00
            habit("later", reminder = "18:00"),
            habit("soon", reminder = "11:30"),
            habit("done", done = true, reminder = "10:30"), // finished, so ignored
            habit("none", reminder = null)
        )
        assertEquals("soon", HabitLogic.nextReminder(habits, nowMinutes = 10 * 60)?.id)
    }

    @Test fun nextReminder_is_null_when_nothing_is_left() {
        assertNull(HabitLogic.nextReminder(listOf(habit("a", reminder = "06:00")), nowMinutes = 12 * 60))
    }

    // ---- DateUtils ----

    @Test fun lastNDays_returns_oldest_first_ending_today() {
        val days = DateUtils.lastNDays(LocalDate.of(2024, 3, 1), 3)
        assertEquals(listOf(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 1)), days)
    }

    @Test fun parseTimeToMinutes_valid_and_invalid() {
        assertEquals(450, DateUtils.parseTimeToMinutes("07:30"))
        assertEquals(0, DateUtils.parseTimeToMinutes("00:00"))
        assertEquals(1439, DateUtils.parseTimeToMinutes("23:59"))
        assertNull(DateUtils.parseTimeToMinutes("24:00"))
        assertNull(DateUtils.parseTimeToMinutes("7pm"))
        assertNull(DateUtils.parseTimeToMinutes(null))
    }

    @Test fun formatTime_pads_with_zeros() {
        assertEquals("07:05", DateUtils.formatTime(7, 5))
    }

    @Test fun greeting_changes_through_the_day() {
        assertEquals(Greeting.MORNING, DateUtils.greetingFor(8))
        assertEquals(Greeting.AFTERNOON, DateUtils.greetingFor(12))
        assertEquals(Greeting.EVENING, DateUtils.greetingFor(19))
    }

    // ---- NoteLogic ----

    @Test fun parseTags_lowercases_trims_and_removes_duplicates() {
        assertEquals(listOf("exam-prep", "biology"), NoteLogic.parseTags(" Exam-Prep, biology ,, BIOLOGY"))
        assertEquals(emptyList<String>(), NoteLogic.parseTags("  ,  "))
    }

    @Test fun parseTags_limits_to_ten() {
        assertEquals(10, NoteLogic.parseTags((1..15).joinToString(",") { "tag$it" }).size)
    }

    @Test fun filter_searches_subject_title_content_and_tags_case_insensitively() {
        val notes = listOf(
            NoteDto(id = "1", subject = "Biology", title = "Cell Structure", content = "Mitochondria", tags = listOf("exam-prep")),
            NoteDto(id = "2", subject = "Maths", title = "Calculus", content = "Derivatives", tags = null)
        )
        assertEquals(2, NoteLogic.filter(notes, "  ").size)
        assertEquals(listOf("1"), NoteLogic.filter(notes, "BIOLOGY").map { it.id })
        assertEquals(listOf("1"), NoteLogic.filter(notes, "mitochondria").map { it.id })
        assertEquals(listOf("1"), NoteLogic.filter(notes, "exam").map { it.id })
        assertEquals(listOf("2"), NoteLogic.filter(notes, "calc").map { it.id })
        assertEquals(0, NoteLogic.filter(notes, "history").size)
    }

    @Test fun percent_handles_zero_total() {
        assertEquals(60, NoteLogic.percent(3, 5))
        assertEquals(0, NoteLogic.percent(0, 0))
    }
}
