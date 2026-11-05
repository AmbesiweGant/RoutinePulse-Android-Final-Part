package com.routinepulse.app

import com.routinepulse.app.data.local.db.CompletionDao
import com.routinepulse.app.data.local.db.CompletionEntity
import com.routinepulse.app.data.local.db.HabitDao
import com.routinepulse.app.data.local.db.HabitEntity
import com.routinepulse.app.data.local.db.NoteDao
import com.routinepulse.app.data.local.db.NoteEntity
import com.routinepulse.app.data.model.*
import com.routinepulse.app.data.remote.ApiService
import com.routinepulse.app.data.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.IOException

// ---------- In-memory stand-ins for the Room DAOs (so offline logic can be tested on the JVM) ----------

class FakeHabitDao : HabitDao {
    val rows = LinkedHashMap<String, HabitEntity>()
    override suspend fun active() = rows.values.filter { !it.deleted }.sortedBy { it.createdAt }
    override suspend fun all() = rows.values.toList()
    override suspend fun byLocalId(id: String) = rows[id]
    override suspend fun byServerId(id: String) = rows.values.firstOrNull { it.serverId == id }
    override suspend fun dirty() = rows.values.filter { it.dirty }
    override suspend fun upsert(habit: HabitEntity) { rows[habit.localId] = habit }
    override suspend fun delete(id: String) { rows.remove(id) }
    override fun dirtyCount(): Flow<Int> = flowOf(rows.values.count { it.dirty })
}

class FakeCompletionDao : CompletionDao {
    val rows = LinkedHashMap<Pair<String, String>, CompletionEntity>()
    override suspend fun allCompleted() = rows.values.filter { it.completed }
    override suspend fun forHabit(habitId: String) = rows.values.filter { it.habitLocalId == habitId }
    override suspend fun unsynced() = rows.values.filter { !it.synced }
    override suspend fun upsert(completion: CompletionEntity) { rows[completion.habitLocalId to completion.date] = completion }
    override suspend fun delete(habitId: String, date: String) { rows.remove(habitId to date) }
    override suspend fun deleteForHabit(habitId: String) { rows.keys.removeAll { it.first == habitId } }
    override suspend fun markSynced(habitId: String, date: String, updatedAt: Long) {
        val r = rows[habitId to date] ?: return
        if (r.updatedAt == updatedAt) rows[habitId to date] = r.copy(synced = true)
    }
    override suspend fun deleteIfUnchanged(habitId: String, date: String, updatedAt: Long) {
        val r = rows[habitId to date] ?: return
        if (r.updatedAt == updatedAt) rows.remove(habitId to date)
    }
    override fun unsyncedCount(): Flow<Int> = flowOf(rows.values.count { !it.synced })
}

class FakeNoteDao : NoteDao {
    val rows = LinkedHashMap<String, NoteEntity>()
    override suspend fun active() = rows.values.filter { !it.deleted }.sortedByDescending { it.updatedAt }
    override suspend fun all() = rows.values.toList()
    override suspend fun byLocalId(id: String) = rows[id]
    override suspend fun byServerId(id: String) = rows.values.firstOrNull { it.serverId == id }
    override suspend fun dirty() = rows.values.filter { it.dirty }
    override suspend fun upsert(note: NoteEntity) { rows[note.localId] = note }
    override suspend fun delete(id: String) { rows.remove(id) }
    override fun dirtyCount(): Flow<Int> = flowOf(rows.values.count { it.dirty })
}

class FakeSyncTrigger : SyncTrigger {
    var requests = 0
    override fun request() { requests++ }
}

/**
 * A tiny pretend server. [online] = false makes every call fail like a dead connection, which is exactly
 * what the phone sees when it has no signal.
 */
class FakeServer : ApiService {
    var online = true
    val habits = mutableListOf<HabitDto>()
    val notes = mutableListOf<NoteDto>()
    val calls = mutableListOf<String>()
    var conflictOnUpdate = false
    var nextId = 1
    var stamp = "2024-01-08T10:00:00.000Z"

    private fun requireOnline() { if (!online) throw IOException("no connection") }

    override suspend fun getHabits(date: String): List<HabitDto> { requireOnline(); calls += "getHabits"; return habits.toList() }

    override suspend fun deleteAccount() { requireOnline(); calls += "deleteAccount" }

    override suspend fun createHabit(body: HabitRequest, date: String): HabitDto {
        requireOnline(); calls += "createHabit"
        val h = HabitDto(
            id = "srv${nextId++}", title = body.title, frequency = body.frequency, customDays = body.customDays,
            reminderTime = body.reminderTime, priority = body.priority, createdAt = stamp, updatedAt = stamp,
            completionDates = emptyList()
        )
        habits += h
        return h
    }

    override suspend fun updateHabit(id: String, body: HabitRequest, date: String): HabitDto {
        requireOnline(); calls += "updateHabit"
        val i = habits.indexOfFirst { it.id == id }
        if (conflictOnUpdate) {
            // server copy was newer: it keeps ITS values and tells the app so (last-write-wins)
            val won = habits[i].copy(title = "Server title", updatedAt = "2024-02-01T00:00:00.000Z", conflict = true)
            habits[i] = won.copy(conflict = false)
            return won
        }
        val updated = habits[i].copy(
            title = body.title, frequency = body.frequency, customDays = body.customDays,
            reminderTime = body.reminderTime, priority = body.priority, updatedAt = stamp
        )
        habits[i] = updated
        return updated
    }

    override suspend fun deleteHabit(id: String) { requireOnline(); calls += "deleteHabit:$id"; habits.removeAll { it.id == id } }

    override suspend fun completeHabit(id: String, body: CompleteRequest): CompleteResponse {
        requireOnline(); calls += "complete:$id:${body.date}"
        val i = habits.indexOfFirst { it.id == id }
        habits[i] = habits[i].copy(completionDates = ((habits[i].completionDates ?: emptyList()) + body.date).distinct())
        return CompleteResponse(habit = habits[i], streakCount = 1, syncStatus = true)
    }

    override suspend fun uncompleteHabit(id: String, date: String): CompleteResponse {
        requireOnline(); calls += "uncomplete:$id:$date"
        val i = habits.indexOfFirst { it.id == id }
        habits[i] = habits[i].copy(completionDates = (habits[i].completionDates ?: emptyList()) - date)
        return CompleteResponse(habit = habits[i], streakCount = 0, syncStatus = true)
    }

    override suspend fun getNotes(): List<NoteDto> { requireOnline(); calls += "getNotes"; return notes.toList() }

    override suspend fun createNote(body: NoteRequest): NoteDto {
        requireOnline(); calls += "createNote"
        val n = NoteDto(id = "note${nextId++}", subject = body.subject, title = body.title, content = body.content, tags = body.tags, updatedAt = stamp)
        notes += n
        return n
    }

    override suspend fun updateNote(id: String, body: NoteRequest): NoteDto {
        requireOnline(); calls += "updateNote"
        val i = notes.indexOfFirst { it.id == id }
        notes[i] = notes[i].copy(subject = body.subject, title = body.title, content = body.content, tags = body.tags, updatedAt = stamp)
        return notes[i]
    }

    override suspend fun deleteNote(id: String) { requireOnline(); calls += "deleteNote:$id"; notes.removeAll { it.id == id } }

    // Not needed by the sync tests
    override suspend fun health(): HealthDto = throw UnsupportedOperationException()
    override suspend fun register(body: RegisterRequest): AuthResponse = throw UnsupportedOperationException()
    override suspend fun login(body: LoginRequest): AuthResponse = throw UnsupportedOperationException()
    override suspend fun loginWithGoogle(body: GoogleLoginRequest): AuthResponse = throw UnsupportedOperationException()
    override suspend fun registerDevice(body: DeviceRequest) {}
    override suspend fun unregisterDevice(body: DeviceUnregisterRequest) {}
    override suspend fun testPush(): TestPushResponse = throw UnsupportedOperationException()
    override suspend fun getHabit(id: String, date: String): HabitDto = throw UnsupportedOperationException()
    override suspend fun getNote(id: String): NoteDto = throw UnsupportedOperationException()
    override suspend fun generateQuiz(id: String): QuizDto = throw UnsupportedOperationException()
    override suspend fun getQuizzes(): List<QuizDto> = throw UnsupportedOperationException()
    override suspend fun getQuiz(id: String): QuizDto = throw UnsupportedOperationException()
    override suspend fun submitQuiz(id: String, body: SubmitRequest): SubmitResponse = throw UnsupportedOperationException()
}
