package com.routinepulse.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.routinepulse.app.RoutinePulseApp
import kotlinx.coroutines.flow.first

/**
 * Background job run by WorkManager. WorkManager only starts it when the phone has internet (see
 * WorkSyncScheduler), survives app restarts and reboots, and retries with back-off if we ask it to.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RoutinePulseApp).container
        if (container.settingsStore.tokenFlow.first().isNullOrBlank()) {
            Log.d("SyncWorker", "Not logged in - nothing to sync")
            return Result.success()
        }
        return if (container.syncEngine.syncNow()) Result.success() else Result.retry()
    }
}
