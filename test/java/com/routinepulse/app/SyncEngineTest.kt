package com.routinepulse.app

import com.routinepulse.app.data.local.db.HabitEntity
import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.HabitRequest
import com.routinepulse.app.data.model.NoteDto
import com.routinepulse.app.data.model.NoteRequest
import com.routinepulse.app.data.repository.OfflineHabitRepository
import com.routinepulse.app.data.repository.OfflineNoteRepository
import com.routinepulse.app.data.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Exercises the whole offline-then-sync story with a pretend server that can be switched off. */
class SyncEngineTest {
    private val day = "2024-01-08"
    private lateinit var habits: FakeHabitDao
    private lateinit var completions: FakeCompletionDao
    private lateinit var notes: FakeNoteDao
    private lateinit var server: FakeServer
    private lateinit var engine: SyncEngine
    private lateinit var habitRepo: OfflineHabitRepository
    private lateinit var noteRepo: OfflineNoteRepository
    private var syncedAt = 0L
    private var counter = 0

    private val request = HabitRequest("Study Biology", "daily", emptyList(), "18:00", "high")

    @Before fun setUp() {
        habits = FakeHabitDao(); completions = FakeCompletionDao(); notes = FakeNoteDao(); server = FakeServer()
        counter = 0; syncedAt = 0L
        engine = SyncEngine(
            api = server, habits = habits, completions = completions, notes = notes,
            today = { LocalDate.parse(day) }, now = { 2_000L }, ids = { "local${++counter}" },
            onSynced = { syncedAt = it }
        )
        val trigger = FakeSyncTrigger()
        habitRepo = OfflineHabitRepository(habits, completions, trigger, now = { 1_000L }, ids = { "h${++counter}" })
        noteRepo = OfflineNoteRepository(notes, trigger, now = { 1_000L }, ids = { "n${++counter}" })
    }

    private fun <T> com.routinepulse.app.data.remote.ApiResult<T>.data(): T = (this as com.routinepulse.app.data.remote.ApiResult.Success).data

    @Test fun offline_changes_reach_the_server_once_the_connection_returns() = runBlocking {
        server.online = false
        val id = habitRepo.create(request, day).data().id
        habitRepo.setCompleted(id, day, true)
        noteRepo.create(NoteRequest("Biology", "Cells", "Mitochondria...", listOf("exam")))

        // 1) while offline the sync fails but NOTHING is lost
        assertFalse(engine.syncNow())
        assertTrue(habits.rows.getValue(id).dirty)
        assertFalse(completions.rows.getValue(id to day).synced)
        assertEquals(0L, syncedAt)

        // 2) the connection comes back
        server.online = true
        assertTrue(engine.syncNow())

        val habit = habits.rows.getValue(id)
        assertNotNull(habit.serverId)
        assertFalse(habit.dirty)
        assertTrue(completions.rows.getValue(id to day).synced)
        assertTrue(notes.rows.values.all { !it.dirty && it.serverId != null })
        assertEquals(1, server.habits.size)
        assertEquals(listOf(day), server.habits[0].completionDates)
        assertEquals(1, server.notes.size)
        assertEquals(2_000L, syncedAt) // "last synced" timestamp recorded
    }

    @Test fun a_failed_sync_keeps_every_change_for_the_next_attempt() = runBlocking {
        server.online = false
        habitRepo.create(request, day)
        assertFalse(engine.syncNow())
        assertFalse(engine.syncNow()) // trying again while still offline is harmless
        assertEquals(1, habits.rows.size)
        assertTrue(habits.rows.values.single().dirty)
    }

    @Test fun habits_from_another_device_are_pulled_in_with_their_ticks() = runBlocking {
        server.habits += HabitDto(
            id = "r1", title = "Read 20 min", frequency = "daily", priority = "low",
            createdAt = "2024-01-01T00:00:00Z", updatedAt = "2024-01-05T00:00:00Z",
            completionDates = listOf("2024-01-07", "2024-01-08")
        )
        assertTrue(engine.syncNow())
        val local = habits.byServerId("r1")
        assertNotNull(local)
        assertEquals("Read 20 min", local!!.title)
        assertFalse(local.dirty)
        assertTrue(completions.rows.getValue(local.localId to "2024-01-08").synced)
        assertEquals(2, completions.forHabit(local.localId).size)
    }

    @Test fun when_the_server_copy_is_newer_it_wins_the_conflict() = runBlocking {
        val id = habitRepo.create(request, day).data().id
        assertTrue(engine.syncNow()) // now it exists on the server
        habitRepo.update(id, request.copy(title = "My offline edit"), day)

        server.conflictOnUpdate = true // someone else edited it later; the server answers conflict=true
        assertTrue(engine.syncNow())

        assertEquals("Server title", habits.rows.getValue(id).title) // last write wins: the server's version
        assertFalse(habits.rows.getValue(id).dirty)
    }

    @Test fun a_habit_deleted_elsewhere_disappears_locally_after_a_pull() = runBlocking {
        val id = habitRepo.create(request, day).data().id
        assertTrue(engine.syncNow())
        server.habits.clear() // deleted from another device

        assertTrue(engine.syncNow())
        assertNull(habits.rows[id])
        assertTrue(completions.rows.isEmpty())
    }

    @Test fun deleting_a_synced_habit_calls_the_server_then_forgets_it() = runBlocking {
        val id = habitRepo.create(request, day).data().id
        assertTrue(engine.syncNow())
        val serverId = habits.rows.getValue(id).serverId!!

        habitRepo.delete(id)
        assertTrue(engine.syncNow())

        assertTrue(server.calls.contains("deleteHabit:$serverId"))
        assertNull(habits.rows[id])
        assertTrue(server.habits.isEmpty())
    }

    @Test fun unticking_offline_is_sent_as_an_undo() = runBlocking {
        val id = habitRepo.create(request, day).data().id
        habitRepo.setCompleted(id, day, true)
        assertTrue(engine.syncNow())
        val serverId = habits.rows.getValue(id).serverId!!

        habitRepo.setCompleted(id, day, false)
        assertTrue(engine.syncNow())

        assertTrue(server.calls.contains("uncomplete:$serverId:$day"))
        assertTrue(completions.rows.isEmpty())
        assertEquals(emptyList<String>(), server.habits[0].completionDates)
    }

    @Test fun notes_synced_from_the_server_do_not_duplicate_on_repeat_syncs() = runBlocking {
        server.notes += NoteDto(id = "n9", subject = "History", title = "Cold War", content = "...", tags = listOf("exam"), updatedAt = "2024-01-02T00:00:00Z")
        assertTrue(engine.syncNow())
        assertTrue(engine.syncNow())
        assertEquals(1, notes.rows.size)
        assertEquals("Cold War", notes.rows.values.single().title)
    }

    @Test fun a_note_deleted_offline_is_deleted_on_the_server() = runBlocking {
        val id = noteRepo.create(NoteRequest("Maths", "Calculus", "Derivatives", emptyList())).data().id
        assertTrue(engine.syncNow())
        noteRepo.delete(id)
        assertTrue(engine.syncNow())
        assertTrue(server.notes.isEmpty())
        assertNull(notes.rows[id])
    }

    @Test fun an_unsynced_local_edit_is_not_overwritten_by_an_older_server_copy() = runBlocking {
        // Local copy is dirty (edited at 9_000) and the server copy is older (2024-01-01) -> keep local.
        habits.upsert(
            HabitEntity(
                localId = "L1", serverId = "r1", title = "Local newer title", frequency = "daily", customDays = "",
                reminderTime = null, priority = "medium", createdAt = 0L, updatedAt = Long.MAX_VALUE - 1, dirty = true
            )
        )
        server.habits += HabitDto(id = "r1", title = "Old server title", updatedAt = "2024-01-01T00:00:00Z", createdAt = "2024-01-01T00:00:00Z")
        server.conflictOnUpdate = false
        assertTrue(engine.syncNow())
        // The push step sends the edit first, so the server now holds the local title as well.
        assertEquals("Local newer title", habits.rows.getValue("L1").title)
        assertEquals("Local newer title", server.habits.single().title)
    }
}
