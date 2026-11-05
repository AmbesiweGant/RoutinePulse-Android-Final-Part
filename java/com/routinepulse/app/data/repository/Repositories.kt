package com.routinepulse.app.data.repository

import android.util.Log
import com.routinepulse.app.data.local.SettingsStore
import com.routinepulse.app.data.local.db.CompletionDao
import com.routinepulse.app.data.local.db.CompletionEntity
import com.routinepulse.app.data.local.db.HabitDao
import com.routinepulse.app.data.local.db.HabitEntity
import com.routinepulse.app.data.local.db.NoteDao
import com.routinepulse.app.data.local.db.NoteEntity
import com.routinepulse.app.data.model.*
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.remote.ApiService
import com.routinepulse.app.data.remote.ErrorKind
import com.routinepulse.app.data.remote.safeApiCall
import com.routinepulse.app.data.sync.SyncRunner
import com.routinepulse.app.data.sync.SyncTrigger
import com.routinepulse.app.domain.SyncMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

// Interfaces let ViewModels be unit-tested with simple fakes (no network or database needed).

interface AuthRepository {
    suspend fun login(email: String, password: String): ApiResult<Unit>
    suspend fun register(email: String, password: String, displayName: String): ApiResult<Unit>
    /** Single sign-on: [idToken] is the Google ID token; the server verifies it before issuing our own session. */
    suspend fun googleLogin(idToken: String): ApiResult<Unit>
    suspend fun logout()
    /** Permanently deletes the account and all its data on the server, then wipes this phone. */
    suspend fun deleteAccount(): ApiResult<Unit>
}

interface CloudRepository {
    suspend fun ping(): ApiResult<HealthDto>
}

interface HabitRepository {
    suspend fun list(date: String): ApiResult<List<HabitDto>>
    suspend fun get(id: String, date: String): ApiResult<HabitDto>
    suspend fun create(request: HabitRequest, date: String): ApiResult<HabitDto>
    suspend fun update(id: String, request: HabitRequest, date: String): ApiResult<HabitDto>
    suspend fun delete(id: String): ApiResult<Unit>
    suspend fun setCompleted(id: String, date: String, completed: Boolean): ApiResult<HabitDto>
}

interface NoteRepository {
    suspend fun list(): ApiResult<List<NoteDto>>
    suspend fun get(id: String): ApiResult<NoteDto>
    suspend fun create(request: NoteRequest): ApiResult<NoteDto>
    suspend fun update(id: String, request: NoteRequest): ApiResult<NoteDto>
    suspend fun delete(id: String): ApiResult<Unit>
}

interface QuizRepository {
    suspend fun generate(noteId: String): ApiResult<QuizDto>
    suspend fun list(): ApiResult<List<QuizDto>>
    suspend fun get(id: String): ApiResult<QuizDto>
    suspend fun submit(id: String, answers: List<Int>): ApiResult<SubmitResponse>
}

/** Wipes the offline database (on log out, or when a different person logs in on this phone). */
interface LocalDataCleaner {
    suspend fun clearAll()
}

/** Registers / unregisters this phone for push notifications. */
interface PushGateway {
    suspend fun register(): Boolean
    suspend fun unregister()
}

private const val TAG = "Repository"
private val NOT_FOUND = ApiResult.Failure(ApiError(ErrorKind.NOT_FOUND))

// ====================================================================== auth / cloud

class RemoteAuthRepository(
    private val api: ApiService,
    private val settings: SettingsStore,
    private val local: LocalDataCleaner,
    private val push: PushGateway,
    private val sync: SyncTrigger
) : AuthRepository {

    override suspend fun login(email: String, password: String): ApiResult<Unit> {
        Log.d(TAG, "Login attempt")
        return finish(safeApiCall { api.login(LoginRequest(email.trim(), password)) }, email.trim())
    }

    override suspend fun register(email: String, password: String, displayName: String): ApiResult<Unit> {
        Log.d(TAG, "Register attempt")
        return finish(
            safeApiCall { api.register(RegisterRequest(email.trim(), password, displayName.trim())) },
            email.trim()
        )
    }

    override suspend fun googleLogin(idToken: String): ApiResult<Unit> {
        Log.d(TAG, "Google sign-in attempt")
        return finish(safeApiCall { api.loginWithGoogle(GoogleLoginRequest(idToken)) }, "")
    }

    override suspend fun logout() {
        Log.i(TAG, "Logging out")
        runCatching { push.unregister() } // needs the session token, so do it BEFORE clearing the session
        local.clearAll()
        settings.clearSession()
    }

    override suspend fun deleteAccount(): ApiResult<Unit> {
        Log.w(TAG, "Deleting account")
        val result = safeApiCall { api.deleteAccount() }
        if (result is ApiResult.Success) {
            local.clearAll()
            settings.setRegisteredPushToken(null)
            settings.clearSession() // the server already removed this phone's push registration with the account
        }
        return result
    }

    private suspend fun finish(result: ApiResult<AuthResponse>, fallbackEmail: String): ApiResult<Unit> = when (result) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            persist(result.data, fallbackEmail)
            ApiResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not store the session", e)
            ApiResult.Failure(ApiError(ErrorKind.UNKNOWN))
        }
    }

    private suspend fun persist(response: AuthResponse, fallbackEmail: String) {
        val token = response.authToken ?: throw IllegalStateException("Server did not return a token")
        val email = response.profile?.email ?: fallbackEmail
        // A different person on the same phone must never see the previous person's offline data.
        val previous = settings.lastUserEmailFlow.first()
        if (previous.isNotBlank() && !previous.equals(email, ignoreCase = true)) local.clearAll()

        settings.saveSession(
            token = token,
            displayName = response.profile?.displayName.orEmpty(),
            email = email,
            ssoProvider = response.profile?.ssoProvider ?: "local"
        )
        runCatching { push.register() } // best effort: login must not fail because push is unavailable
        sync.request()
    }
}

class RemoteCloudRepository(private val api: ApiService) : CloudRepository {
    override suspend fun ping(): ApiResult<HealthDto> = safeApiCall { api.health() }
}

// ====================================================================== habits (offline first)

/**
 * Reads and writes the LOCAL Room database, so it works with no internet. Every write marks the record as
 * "dirty" and asks the sync trigger to push it to the API as soon as the phone is online.
 */
class OfflineHabitRepository(
    private val habits: HabitDao,
    private val completions: CompletionDao,
    private val sync: SyncTrigger,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val ids: () -> String = { UUID.randomUUID().toString() }
) : HabitRepository {

    private suspend fun dto(entity: HabitEntity, today: LocalDate): HabitDto {
        val done = completions.forHabit(entity.localId).filter { it.completed }.map { it.date }.toSet()
        return Mappers.toDto(entity, done, today)
    }

    override suspend fun list(date: String): ApiResult<List<HabitDto>> {
        val today = LocalDate.parse(date)
        val doneByHabit = completions.allCompleted().groupBy({ it.habitLocalId }, { it.date })
        val result = habits.active().map { Mappers.toDto(it, (doneByHabit[it.localId] ?: emptyList()).toSet(), today) }
        return ApiResult.Success(result)
    }

    override suspend fun get(id: String, date: String): ApiResult<HabitDto> {
        val entity = habits.byLocalId(id)?.takeIf { !it.deleted } ?: return NOT_FOUND
        return ApiResult.Success(dto(entity, LocalDate.parse(date)))
    }

    override suspend fun create(request: HabitRequest, date: String): ApiResult<HabitDto> {
        val t = now()
        val entity = HabitEntity(
            localId = ids(), serverId = null, title = request.title, frequency = request.frequency,
            customDays = SyncMerge.daysToCsv(request.customDays), reminderTime = request.reminderTime,
            priority = request.priority, createdAt = t, updatedAt = t, dirty = true
        )
        habits.upsert(entity)
        sync.request()
        return ApiResult.Success(dto(entity, LocalDate.parse(date)))
    }

    override suspend fun update(id: String, request: HabitRequest, date: String): ApiResult<HabitDto> {
        val existing = habits.byLocalId(id)?.takeIf { !it.deleted } ?: return NOT_FOUND
        val updated = existing.copy(
            title = request.title, frequency = request.frequency,
            customDays = SyncMerge.daysToCsv(request.customDays), reminderTime = request.reminderTime,
            priority = request.priority, updatedAt = now(), dirty = true
        )
        habits.upsert(updated)
        sync.request()
        return ApiResult.Success(dto(updated, LocalDate.parse(date)))
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        val existing = habits.byLocalId(id) ?: return ApiResult.Success(Unit)
        if (existing.serverId == null) {
            // The server never heard of it, so just forget it.
            completions.deleteForHabit(id)
            habits.delete(id)
        } else {
            habits.upsert(existing.copy(deleted = true, dirty = true, updatedAt = now()))
        }
        sync.request()
        return ApiResult.Success(Unit)
    }

    override suspend fun setCompleted(id: String, date: String, completed: Boolean): ApiResult<HabitDto> {
        val existing = habits.byLocalId(id)?.takeIf { !it.deleted } ?: return NOT_FOUND
        completions.upsert(CompletionEntity(id, date, completed, now(), synced = false))
        sync.request()
        return ApiResult.Success(dto(existing, LocalDate.parse(date)))
    }
}

// ====================================================================== notes (offline first)

class OfflineNoteRepository(
    private val notes: NoteDao,
    private val sync: SyncTrigger,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val ids: () -> String = { UUID.randomUUID().toString() }
) : NoteRepository {

    override suspend fun list(): ApiResult<List<NoteDto>> =
        ApiResult.Success(notes.active().map { Mappers.toDto(it) })

    override suspend fun get(id: String): ApiResult<NoteDto> {
        val entity = notes.byLocalId(id)?.takeIf { !it.deleted } ?: return NOT_FOUND
        return ApiResult.Success(Mappers.toDto(entity))
    }

    override suspend fun create(request: NoteRequest): ApiResult<NoteDto> {
        val t = now()
        val entity = NoteEntity(
            localId = ids(), serverId = null, subject = request.subject, title = request.title,
            content = request.content, tags = request.tags.joinToString(","),
            createdAt = t, updatedAt = t, dirty = true
        )
        notes.upsert(entity)
        sync.request()
        return ApiResult.Success(Mappers.toDto(entity))
    }

    override suspend fun update(id: String, request: NoteRequest): ApiResult<NoteDto> {
        val existing = notes.byLocalId(id)?.takeIf { !it.deleted } ?: return NOT_FOUND
        val updated = existing.copy(
            subject = request.subject, title = request.title, content = request.content,
            tags = request.tags.joinToString(","), updatedAt = now(), dirty = true
        )
        notes.upsert(updated)
        sync.request()
        return ApiResult.Success(Mappers.toDto(updated))
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        val existing = notes.byLocalId(id) ?: return ApiResult.Success(Unit)
        if (existing.serverId == null) notes.delete(id)
        else notes.upsert(existing.copy(deleted = true, dirty = true, updatedAt = now()))
        sync.request()
        return ApiResult.Success(Unit)
    }
}

// ====================================================================== quizzes (online: needs the AI / server)

class RemoteQuizRepository(
    private val api: ApiService,
    private val notes: NoteDao,
    private val runner: SyncRunner
) : QuizRepository {

    /**
     * Quizzes are generated on the server from the SAVED note, so the note must have reached the server first.
     * If it can't (offline), the user gets the normal "can't reach the server" message.
     */
    override suspend fun generate(noteId: String): ApiResult<QuizDto> {
        var note = notes.byLocalId(noteId) ?: return NOT_FOUND
        if (note.serverId == null || note.dirty) {
            runner.syncNow()
            note = notes.byLocalId(noteId) ?: return NOT_FOUND
        }
        val serverId = note.serverId
        if (serverId == null || note.dirty) {
            Log.w(TAG, "Quiz generation needs the note to be synced first (offline?)")
            return ApiResult.Failure(ApiError(ErrorKind.NETWORK))
        }
        return safeApiCall { api.generateQuiz(serverId) }
    }

    override suspend fun list() = safeApiCall { api.getQuizzes() }
    override suspend fun get(id: String) = safeApiCall { api.getQuiz(id) }
    override suspend fun submit(id: String, answers: List<Int>) = safeApiCall { api.submitQuiz(id, SubmitRequest(answers)) }
}
