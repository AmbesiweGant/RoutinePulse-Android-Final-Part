package com.routinepulse.app

import com.routinepulse.app.data.local.db.HabitEntity
import com.routinepulse.app.data.local.db.NoteEntity
import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.NoteDto
import com.routinepulse.app.data.repository.Mappers
import com.routinepulse.app.domain.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MappersAndMergeTest {
    private val habit = HabitEntity(
        localId = "l1", serverId = null, title = "Read", frequency = "custom", customDays = "1,3",
        reminderTime = "07:30", priority = "high", createdAt = 0L, updatedAt = 1000L, dirty = true
    )

    // ---------------- SyncMerge ----------------

    @Test fun server_wins_only_when_strictly_newer() {
        assertTrue(SyncMerge.serverWins(localUpdatedAt = 100, serverUpdatedAt = 200))
        assertFalse(SyncMerge.serverWins(localUpdatedAt = 200, serverUpdatedAt = 200))
        assertFalse(SyncMerge.serverWins(localUpdatedAt = 300, serverUpdatedAt = 200))
    }

    @Test fun parseIso_handles_valid_and_invalid_text() {
        assertEquals(Instant.parse("2024-01-08T10:00:00.000Z").toEpochMilli(), SyncMerge.parseIso("2024-01-08T10:00:00.000Z"))
        assertEquals(0L, SyncMerge.parseIso(null))
        assertEquals(0L, SyncMerge.parseIso("yesterday"))
    }

    @Test fun days_csv_round_trip() {
        assertEquals("1,3,5", SyncMerge.daysToCsv(listOf(5, 1, 3)))
        assertEquals(setOf(1, 3, 5), SyncMerge.csvToDays("1, 3,5"))
        assertEquals(emptySet<Int>(), SyncMerge.csvToDays(""))
        assertEquals(emptySet<Int>(), SyncMerge.csvToDays(null))
    }

    // ---------------- Mappers ----------------

    @Test fun habit_entity_becomes_a_dto_with_streak_and_flags() {
        val daily = habit.copy(frequency = "daily", customDays = "")
        val dto = Mappers.toDto(daily, setOf("2024-01-08", "2024-01-07"), LocalDate.of(2024, 1, 8))
        assertEquals("l1", dto.id)
        assertEquals(2, dto.streakCount)
        assertTrue(dto.completedToday)
        assertTrue(dto.scheduledToday)
        assertEquals(listOf("2024-01-07", "2024-01-08"), dto.completionDates)
        assertEquals(listOf("2024-01-07", "2024-01-08"), dto.recentCompletions)
        assertEquals("1970-01-01T00:00:00Z", dto.createdAt)
    }

    @Test fun custom_days_decide_scheduledToday() {
        val monday = Mappers.toDto(habit, emptySet(), LocalDate.of(2024, 1, 8))   // Mon: chosen
        val tuesday = Mappers.toDto(habit, emptySet(), LocalDate.of(2024, 1, 9))  // Tue: not chosen
        assertTrue(monday.scheduledToday)
        assertFalse(tuesday.scheduledToday)
        assertEquals(listOf(1, 3), monday.customDays)
    }

    @Test fun request_only_carries_the_client_timestamp_for_updates() {
        assertNull(Mappers.toRequest(habit, includeClientTimestamp = false).clientUpdatedAt)
        assertEquals(1000L, Mappers.toRequest(habit, includeClientTimestamp = true).clientUpdatedAt)
        assertEquals(listOf(1, 3), Mappers.toRequest(habit, false).customDays)
    }

    @Test fun applyRemote_takes_the_servers_values_and_clears_dirty() {
        val remote = HabitDto(
            id = "s9", title = "New title", frequency = "weekdays", customDays = listOf(2, 4),
            reminderTime = null, priority = "low", updatedAt = "2024-01-08T10:00:00Z"
        )
        val merged = Mappers.applyRemote(habit, remote, now = 5L)
        assertEquals("s9", merged.serverId)
        assertEquals("New title", merged.title)
        assertEquals("2,4", merged.customDays)
        assertNull(merged.reminderTime)
        assertFalse(merged.dirty)
        assertEquals(Instant.parse("2024-01-08T10:00:00Z").toEpochMilli(), merged.updatedAt)
        assertEquals("l1", merged.localId) // the local id never changes
    }

    @Test fun applyRemote_falls_back_to_now_when_the_server_gave_no_timestamp() {
        val merged = Mappers.applyRemote(habit, HabitDto(id = "s1", title = "T"), now = 777L)
        assertEquals(777L, merged.updatedAt)
    }

    @Test fun note_mappers_split_and_join_tags() {
        val note = NoteEntity(
            localId = "n1", serverId = null, subject = "Biology", title = "Cells", content = "text",
            tags = "exam-prep,cells", createdAt = 1L, updatedAt = 2L, dirty = true
        )
        assertEquals(listOf("exam-prep", "cells"), Mappers.toDto(note).tags)
        val merged = Mappers.applyRemote(note, NoteDto(id = "srv", subject = "S", title = "T", content = "C", tags = listOf("a", "b")), now = 9L)
        assertEquals("a,b", merged.tags)
        assertEquals("srv", merged.serverId)
        assertFalse(merged.dirty)
    }
}
