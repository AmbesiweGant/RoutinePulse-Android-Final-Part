package com.routinepulse.app.data.repository

import com.routinepulse.app.data.local.db.HabitEntity
import com.routinepulse.app.data.local.db.NoteEntity
import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.HabitRequest
import com.routinepulse.app.data.model.NoteDto
import com.routinepulse.app.data.model.NoteRequest
import com.routinepulse.app.domain.DateUtils
import com.routinepulse.app.domain.StreakCalculator
import com.routinepulse.app.domain.SyncMerge
import java.time.Instant
import java.time.LocalDate

/** Converts between database rows (Room), UI models and API request bodies. Pure functions - unit tested. */
object Mappers {

    fun toDto(entity: HabitEntity, completedDates: Set<String>, today: LocalDate): HabitDto {
        val days = SyncMerge.csvToDays(entity.customDays)
        val recentWindow = DateUtils.lastNDays(today, 14).map { it.toString() }.toSet()
        return HabitDto(
            id = entity.localId,
            title = entity.title,
            frequency = entity.frequency,
            customDays = days.sorted(),
            reminderTime = entity.reminderTime,
            priority = entity.priority,
            streakCount = StreakCalculator.current(entity.frequency, days, completedDates, today),
            completedToday = today.toString() in completedDates,
            scheduledToday = StreakCalculator.isScheduled(entity.frequency, days, today),
            recentCompletions = completedDates.filter { it in recentWindow }.sorted(),
            completionDates = completedDates.sorted(),
            createdAt = Instant.ofEpochMilli(entity.createdAt).toString(),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt).toString()
        )
    }

    fun toRequest(entity: HabitEntity, includeClientTimestamp: Boolean): HabitRequest = HabitRequest(
        title = entity.title,
        frequency = entity.frequency,
        customDays = SyncMerge.csvToDays(entity.customDays).sorted(),
        reminderTime = entity.reminderTime,
        priority = entity.priority,
        clientUpdatedAt = if (includeClientTimestamp) entity.updatedAt else null
    )

    fun toDto(entity: NoteEntity): NoteDto = NoteDto(
        id = entity.localId,
        subject = entity.subject,
        title = entity.title,
        content = entity.content,
        tags = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        updatedAt = Instant.ofEpochMilli(entity.updatedAt).toString()
    )

    fun toRequest(entity: NoteEntity, includeClientTimestamp: Boolean): NoteRequest = NoteRequest(
        subject = entity.subject,
        title = entity.title,
        content = entity.content,
        tags = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        clientUpdatedAt = if (includeClientTimestamp) entity.updatedAt else null
    )

    /** Overwrites a local habit with the server's version (used when the server copy wins). */
    fun applyRemote(local: HabitEntity, remote: HabitDto, now: Long): HabitEntity = local.copy(
        serverId = remote.id,
        title = remote.title,
        frequency = remote.frequency,
        customDays = SyncMerge.daysToCsv(remote.customDays ?: emptyList()),
        reminderTime = remote.reminderTime,
        priority = remote.priority,
        updatedAt = SyncMerge.parseIso(remote.updatedAt).takeIf { it > 0 } ?: now,
        dirty = false
    )

    fun applyRemote(local: NoteEntity, remote: NoteDto, now: Long): NoteEntity = local.copy(
        serverId = remote.id,
        subject = remote.subject,
        title = remote.title,
        content = remote.content,
        tags = (remote.tags ?: emptyList()).joinToString(","),
        updatedAt = SyncMerge.parseIso(remote.updatedAt).takeIf { it > 0 } ?: now,
        dirty = false
    )
}
