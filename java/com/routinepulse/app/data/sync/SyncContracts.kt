package com.routinepulse.app.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Something that can ask for a background sync (the repositories call this after every local change). */
interface SyncTrigger {
    fun request()
}

/** Runs one sync immediately and reports whether it fully succeeded. */
interface SyncRunner {
    suspend fun syncNow(): Boolean
}

/** What the UI needs to show: pending changes, "syncing..." and a signal to reload after a sync finished. */
interface SyncStatus : SyncTrigger {
    val pending: Flow<Int>
    val syncing: StateFlow<Boolean>
    val completed: SharedFlow<Unit>
}
