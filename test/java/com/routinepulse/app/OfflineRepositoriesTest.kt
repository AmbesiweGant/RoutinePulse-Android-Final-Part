package com.routinepulse.app

import com.routinepulse.app.data.model.HabitRequest
import com.routinepulse.app.data.model.NoteRequest
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.remote.ErrorKind
import com.routinepulse.app.data.repository.OfflineHabitRepository
import com.routinepulse.app.data.repository.OfflineNoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** These run with NO network at all - exactly like the app with the phone in airplane mode. */
class OfflineRepositoriesTest {
    private val today = "2024-01-08"
    private lateinit var habits: FakeHabitDao
    private lateinit var completions: FakeCompletionDao
    private lateinit var notes: FakeNoteDao
    private lateinit var sync: FakeSyncTrigger
    private var clock = 1_000L
    private var counter = 0

    private lateinit var habitRepo: OfflineHabitRepository
    private lateinit var noteRepo: OfflineNoteRepository

    private val request = HabitRequest("Drink water", "daily", emptyList(), "07:30", "medium")

    @Before fun setUp() {
        habits = FakeHabitDao(); completions = FakeCompletionDao(); notes = FakeNoteDao(); sync = FakeSyncTrigger()
        clock = 1_000L; counter = 0
        habitRepo = OfflineHabitRepository(habits, completions, sync, now = { clock }, ids = { "id${++counter}" })
        noteRepo = OfflineNoteRepository(notes, sync, now = { clock }, ids = { "note${++counter}" })
    }

    private fun <T> ApiResult<T>.data(): T = (this as ApiResult.Success).data

    // ---------------- habits ----------------

    @Test fun a_habit_created_offline_is_saved_locally_and_marked_for_sync() = runBlocking {
        val created = habitRepo.create(request, today).data()
        assertEquals("Drink water", created.title)
        val row = habits.rows.getValue(created.id)
        assertNull(row.serverId)       // the server has not heard of it yet
        assertTrue(row.dirty)
        assertEquals(1, sync.requests) // a sync was requested (it will run when online)
        assertEquals(1, habitRepo.list(today).data().size)
    }

    @Test fun ticking_a_habit_offline_updates_the_streak_immediately() = runBlocking {
        val id = habitRepo.create(request, today).data().id
        habitRepo.setCompleted(id, "2024-01-06", true)
        habitRepo.setCompleted(id, "2024-01-07", true)
        val dto = habitRepo.setCompleted(id, "2024-01-08", true).data()
        assertTrue(dto.completedToday)
        assertEquals(3, dto.streakCount)
        assertFalse(completions.rows.getValue(id to "2024-01-08").synced) // waiting to reach the server
    }

    @Test fun unticking_removes_todays_check() = runBlocking {
        val id = habitRepo.create(request, today).data().id
        habitRepo.setCompleted(id, today, true)
        val dto = habitRepo.setCompleted(id, today, false).data()
        assertFalse(dto.completedToday)
        assertEquals(0, dto.streakCount)
    }

    @Test fun editing_a_habit_marks_it_dirty_with_a_new_timestamp() = runBlocking {
        val id = habitRepo.create(request, today).data().id
        habits.upsert(habits.rows.getValue(id).copy(dirty = false, serverId = "srv1")) // pretend it was synced
        clock = 5_000L
        habitRepo.update(id, request.copy(title = "Drink more water"), today)
        val row = habits.rows.getValue(id)
        assertEquals("Drink more water", row.title)
        assertTrue(row.dirty)
        assertEquals(5_000L, row.updatedAt)
        assertEquals("srv1", row.serverId) // keeps its server id
    }

    @Test fun deleting_a_never_synced_habit_just_forgets_it() = runBlocking {
        val id = habitRepo.create(request, today).data().id
        habitRepo.setCompleted(id, today, true)
        habitRepo.delete(id)
        assertTrue(habits.rows.isEmpty())
        assertTrue(completions.rows.isEmpty())
    }

    @Test fun deleting_a_synced_habit_hides_it_and_queues_the_deletion() = runBlocking {
        val id = habitRepo.create(request, today).data().id
        habits.upsert(habits.rows.getValue(id).copy(dirty = false, serverId = "srv1"))
        habitRepo.delete(id)
        assertTrue(habitRepo.list(today).data().isEmpty())   // gone from the UI
        val row = habits.rows.getValue(id)                   // but remembered until the server is told
        assertTrue(row.deleted)
        assertTrue(row.dirty)
    }

    @Test fun unknown_habit_is_not_found() = runBlocking {
        val result = habitRepo.get("nope", today)
        assertTrue(result is ApiResult.Failure)
        assertEquals(ErrorKind.NOT_FOUND, (result as ApiResult.Failure).error.kind)
    }

    // ---------------- notes ----------------

    @Test fun a_note_written_offline_is_available_immediately() = runBlocking {
        val created = noteRepo.create(NoteRequest("Biology", "Cells", "Mitochondria...", listOf("exam-prep"))).data()
        assertEquals(listOf(created.id), noteRepo.list().data().map { it.id })
        assertEquals(listOf("exam-prep"), noteRepo.get(created.id).data().tags)
        assertTrue(notes.rows.getValue(created.id).dirty)
        assertEquals(1, sync.requests)
    }

    @Test fun editing_and_deleting_notes() = runBlocking {
        val id = noteRepo.create(NoteRequest("Maths", "Calculus", "Derivatives", emptyList())).data().id
        notes.upsert(notes.rows.getValue(id).copy(dirty = false, serverId = "srvN"))

        clock = 9_000L
        noteRepo.update(id, NoteRequest("Maths", "Calculus II", "Integrals", listOf("tag")))
        assertEquals("Calculus II", notes.rows.getValue(id).title)
        assertTrue(notes.rows.getValue(id).dirty)

        noteRepo.delete(id)
        assertTrue(noteRepo.list().data().isEmpty())
        assertNotNull(notes.rows[id])            // remembered so the server can be told
        assertTrue(notes.rows.getValue(id).deleted)
    }
}
