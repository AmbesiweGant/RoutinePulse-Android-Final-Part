package com.routinepulse.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deleted = 0 ORDER BY createdAt ASC")
    suspend fun active(): List<HabitEntity>

    @Query("SELECT * FROM habits")
    suspend fun all(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE localId = :id LIMIT 1")
    suspend fun byLocalId(id: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE serverId = :id LIMIT 1")
    suspend fun byServerId(id: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE dirty = 1")
    suspend fun dirty(): List<HabitEntity>

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE localId = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM habits WHERE dirty = 1")
    fun dirtyCount(): Flow<Int>
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions WHERE completed = 1")
    suspend fun allCompleted(): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE habitLocalId = :habitId")
    suspend fun forHabit(habitId: String): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE synced = 0")
    suspend fun unsynced(): List<CompletionEntity>

    @Upsert
    suspend fun upsert(completion: CompletionEntity)

    @Query("DELETE FROM completions WHERE habitLocalId = :habitId AND date = :date")
    suspend fun delete(habitId: String, date: String)

    @Query("DELETE FROM completions WHERE habitLocalId = :habitId")
    suspend fun deleteForHabit(habitId: String)

    /** Marks a row as sent - but only if the user has not changed it again while the request was in flight. */
    @Query("UPDATE completions SET synced = 1 WHERE habitLocalId = :habitId AND date = :date AND updatedAt = :updatedAt")
    suspend fun markSynced(habitId: String, date: String, updatedAt: Long)

    @Query("DELETE FROM completions WHERE habitLocalId = :habitId AND date = :date AND updatedAt = :updatedAt")
    suspend fun deleteIfUnchanged(habitId: String, date: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM completions WHERE synced = 0")
    fun unsyncedCount(): Flow<Int>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC")
    suspend fun active(): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE localId = :id LIMIT 1")
    suspend fun byLocalId(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE serverId = :id LIMIT 1")
    suspend fun byServerId(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE dirty = 1")
    suspend fun dirty(): List<NoteEntity>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE localId = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM notes WHERE dirty = 1")
    fun dirtyCount(): Flow<Int>
}
