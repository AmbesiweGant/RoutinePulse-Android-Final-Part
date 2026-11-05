package com.routinepulse.app.domain

import java.time.Instant

/** Small pure helpers for the offline-sync conflict rule: LAST WRITE WINS, decided by timestamp. */
object SyncMerge {
    /** True when the server copy was changed after the phone's copy, so the server copy must win. */
    fun serverWins(localUpdatedAt: Long, serverUpdatedAt: Long): Boolean = serverUpdatedAt > localUpdatedAt

    /** "2026-10-30T12:00:00.000Z" -> epoch milliseconds. Returns 0 for null / invalid text. */
    fun parseIso(text: String?): Long =
        if (text.isNullOrBlank()) 0L else runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)

    fun daysToCsv(days: Collection<Int>): String = days.sorted().joinToString(",")

    fun csvToDays(csv: String?): Set<Int> =
        csv.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
}
