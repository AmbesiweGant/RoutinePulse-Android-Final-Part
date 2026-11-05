package com.routinepulse.app.data.sync

import android.util.Log
import com.routinepulse.app.data.local.db.CompletionDao
import com.routinepulse.app.data.local.db.CompletionEntity
import com.routinepulse.app.data.local.db.HabitDao
import com.routinepulse.app.data.local.db.HabitEntity
import com.routinepulse.app.data.local.db.NoteDao
import com.routinepulse.app.data.local.db.NoteEntity
import com.routinepulse.app.data.model.CompleteRequest
import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.NoteDto
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.remote.ApiService
import com.routinepulse.app.data.remote.ErrorKind
import com.routinepulse.app.data.remote.safeApiCall
import com.routinepulse.app.data.repository.Mappers
import com.routinepulse.app.domain.SyncMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

/**
 * Offline sync (REQ-08). Strategy:
 *  1. PUSH everything created / edited / deleted / ticked while offline (oldest rule: the phone is the source
 *     of truth for its own unsynced changes).
 *  2. PULL the server's data and merge it in. If the same record was changed on both sides, the one with the
 *     LATEST timestamp wins ("last write wins") - the server decides for pushed edits via clientUpdatedAt.
 *
 * A failure that is probably temporary (no network, server error, expired login) aborts the run so
 * WorkManager can retry later. A permanent rejection of one record (e.g. validation) drops only that change.
 */
class SyncEngine(
    private val api: ApiService,
    private val habits: HabitDao,
    private val completions: CompletionDao,
    private val notes: NoteDao,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val onSynced: suspend (Long) -> Unit = {}
) : SyncRunner {

    private val mutex = Mutex()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _completed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completed: SharedFlow<Unit> = _completed.asSharedFlow()

    private fun ApiError.isTemporary() =
        kind == ErrorKind.NETWORK || kind == ErrorKind.SERVER || kind == ErrorKind.UNAUTHORIZED || kind == ErrorKind.UNKNOWN

    override suspend fun syncNow(): Boolean = mutex.withLock {
        _syncing.value = true
        try {
            var ok = true
            // Changes made while a pass is running are picked up by a follow-up pass (max 3 passes).
            for (pass in 1..3) {
                ok = runOnce()
                if (!ok || !hasPending()) break
            }
            if (ok) onSynced(now())
            _completed.tryEmit(Unit)
            Log.i(TAG, "Sync finished: success=$ok")
            ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sync crashed", e)
            false
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun hasPending(): Boolean = habits.dirty().isNotEmpty() || completions.unsynced().isNotEmpty() || notes.dirty().isNotEmpty()

    private suspend fun runOnce(): Boolean =
        pushHabits() && pushCompletions() && pushNotes() && pullHabits() && pullNotes()

    // ---------------------------------------------------------------- PUSH

    private suspend fun pushHabits(): Boolean {
        val day = today().toString()
        for (h in habits.dirty()) {
            val serverId = h.serverId
            if (h.deleted) {
                if (serverId == null) { removeHabit(h.localId); continue } // never reached the server
                when (val r = safeApiCall { api.deleteHabit(serverId) }) {
                    is ApiResult.Success -> removeHabit(h.localId)
                    is ApiResult.Failure -> if (r.error.isTemporary()) return false else removeHabit(h.localId)
                }
                continue
            }

            val result = if (serverId == null) {
                safeApiCall { api.createHabit(Mappers.toRequest(h, includeClientTimestamp = false), day) }
            } else {
                safeApiCall { api.updateHabit(serverId, Mappers.toRequest(h, includeClientTimestamp = true), day) }
            }
            when (result) {
                is ApiResult.Success -> {
                    val current = habits.byLocalId(h.localId) ?: continue
                    if (current.updatedAt != h.updatedAt) {
                        // The user edited it again while we were sending: keep their newer edit, just remember the id.
                        habits.upsert(current.copy(serverId = result.data.id))
                    } else {
                        habits.upsert(Mappers.applyRemote(current, result.data, now())) // also handles conflict=true
                    }
                }
                is ApiResult.Failure -> when {
                    result.error.isTemporary() -> return false
                    result.error.kind == ErrorKind.NOT_FOUND && serverId != null -> removeHabit(h.localId) // deleted elsewhere
                    else -> { Log.w(TAG, "Server rejected habit ${h.localId}: ${result.error}"); habits.upsert(h.copy(dirty = false)) }
                }
            }
        }
        return true
    }

    private suspend fun pushCompletions(): Boolean {
        for (c in completions.unsynced()) {
            val habit = habits.byLocalId(c.habitLocalId)
            if (habit == null || habit.deleted) { completions.delete(c.habitLocalId, c.date); continue }
            val serverId = habit.serverId ?: continue // the habit itself has not reached the server yet; try next time

            val result = safeApiCall {
                if (c.completed) api.completeHabit(serverId, CompleteRequest(c.date)) else api.uncompleteHabit(serverId, c.date)
            }
            when (result) {
                is ApiResult.Success ->
                    if (c.completed) completions.markSynced(c.habitLocalId, c.date, c.updatedAt)
                    else completions.deleteIfUnchanged(c.habitLocalId, c.date, c.updatedAt)
                is ApiResult.Failure ->
                    if (result.error.isTemporary()) return false else completions.delete(c.habitLocalId, c.date)
            }
        }
        return true
    }

    private suspend fun pushNotes(): Boolean {
        for (n in notes.dirty()) {
            val serverId = n.serverId
            if (n.deleted) {
                if (serverId == null) { notes.delete(n.localId); continue }
                when (val r = safeApiCall { api.deleteNote(serverId) }) {
                    is ApiResult.Success -> notes.delete(n.localId)
                    is ApiResult.Failure -> if (r.error.isTemporary()) return false else notes.delete(n.localId)
                }
                continue
            }

            val result = if (serverId == null) {
                safeApiCall { api.createNote(Mappers.toRequest(n, includeClientTimestamp = false)) }
            } else {
                safeApiCall { api.updateNote(serverId, Mappers.toRequest(n, includeClientTimestamp = true)) }
            }
            when (result) {
                is ApiResult.Success -> {
                    val current = notes.byLocalId(n.localId) ?: continue
                    if (current.updatedAt != n.updatedAt) notes.upsert(current.copy(serverId = result.data.id))
                    else notes.upsert(Mappers.applyRemote(current, result.data, now()))
                }
                is ApiResult.Failure -> when {
                    result.error.isTemporary() -> return false
                    result.error.kind == ErrorKind.NOT_FOUND && serverId != null -> notes.delete(n.localId)
                    else -> { Log.w(TAG, "Server rejected note ${n.localId}: ${result.error}"); notes.upsert(n.copy(dirty = false)) }
                }
            }
        }
        return true
    }

    // ---------------------------------------------------------------- PULL

    private suspend fun pullHabits(): Boolean {
        val day = today()
        val remote: List<HabitDto> = when (val r = safeApiCall { api.getHabits(day.toString()) }) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> return false
        }
        val remoteIds = remote.map { it.id }.toSet()

        for (rh in remote) {
            val local = habits.byServerId(rh.id)
            val remoteUpdated = SyncMerge.parseIso(rh.updatedAt)
            val entity = when {
                local == null -> HabitEntity(
                    localId = ids(), serverId = rh.id, title = rh.title, frequency = rh.frequency,
                    customDays = SyncMerge.daysToCsv(rh.customDays ?: emptyList()), reminderTime = rh.reminderTime,
                    priority = rh.priority,
                    createdAt = SyncMerge.parseIso(rh.createdAt).takeIf { it > 0 } ?: now(),
                    updatedAt = remoteUpdated.takeIf { it > 0 } ?: now()
                )
                // An unsynced local edit survives unless the server copy is actually newer (last write wins).
                local.dirty && !SyncMerge.serverWins(local.updatedAt, remoteUpdated) -> local
                else -> Mappers.applyRemote(local, rh, now())
            }
            habits.upsert(entity)
            mergeCompletions(entity.localId, rh.completionDates.orEmpty())
        }

        // Habits deleted from another device disappear here too (only if we have nothing unsynced for them).
        for (l in habits.all()) {
            if (l.serverId != null && l.serverId !in remoteIds && !l.dirty) removeHabit(l.localId)
        }
        return true
    }

    private suspend fun mergeCompletions(habitLocalId: String, remoteDates: List<String>) {
        val remoteSet = remoteDates.toSet()
        val local = completions.forHabit(habitLocalId).associateBy { it.date }
        for (date in remoteSet) {
            val l = local[date]
            if (l == null || l.synced) completions.upsert(CompletionEntity(habitLocalId, date, true, now(), true))
        }
        // Ticks that were removed on another device. Unsynced local rows are never touched here.
        for ((date, l) in local) {
            if (l.synced && l.completed && date !in remoteSet) completions.delete(habitLocalId, date)
        }
    }

    private suspend fun pullNotes(): Boolean {
        val remote: List<NoteDto> = when (val r = safeApiCall { api.getNotes() }) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> return false
        }
        val remoteIds = remote.map { it.id }.toSet()

        for (rn in remote) {
            val local = notes.byServerId(rn.id)
            val remoteUpdated = SyncMerge.parseIso(rn.updatedAt)
            val entity = when {
                local == null -> NoteEntity(
                    localId = ids(), serverId = rn.id, subject = rn.subject, title = rn.title, content = rn.content,
                    tags = (rn.tags ?: emptyList()).joinToString(","),
                    createdAt = remoteUpdated.takeIf { it > 0 } ?: now(),
                    updatedAt = remoteUpdated.takeIf { it > 0 } ?: now()
                )
                local.dirty && !SyncMerge.serverWins(local.updatedAt, remoteUpdated) -> local
                else -> Mappers.applyRemote(local, rn, now())
            }
            notes.upsert(entity)
        }
        for (l in notes.all()) {
            if (l.serverId != null && l.serverId !in remoteIds && !l.dirty) notes.delete(l.localId)
        }
        return true
    }

    private suspend fun removeHabit(localId: String) {
        completions.deleteForHabit(localId)
        habits.delete(localId)
    }

    private companion object {
        const val TAG = "SyncEngine"
    }
}
