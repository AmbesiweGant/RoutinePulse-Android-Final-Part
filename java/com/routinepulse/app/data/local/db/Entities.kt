package com.routinepulse.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local (Room) copy of a habit. [localId] is created on the phone and never changes, so the UI keeps working
 * while offline. [serverId] is filled in once the habit has been created on the server.
 *  - dirty   = changed on the phone and not yet sent to the API
 *  - deleted = removed by the user; kept until the server has been told (then really deleted)
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val localId: String,
    val serverId: String?,
    val title: String,
    val frequency: String,
    val customDays: String, // "1,3,5" (ISO weekdays)
    val reminderTime: String?,
    val priority: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = false
)

/** One row per habit per day. completed=false is a pending "undo" that still has to reach the server. */
@Entity(tableName = "completions", primaryKeys = ["habitLocalId", "date"])
data class CompletionEntity(
    val habitLocalId: String,
    val date: String, // yyyy-MM-dd (the user's local date)
    val completed: Boolean,
    val updatedAt: Long,
    val synced: Boolean
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val localId: String,
    val serverId: String?,
    val subject: String,
    val title: String,
    val content: String,
    val tags: String, // comma separated
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = false
)
