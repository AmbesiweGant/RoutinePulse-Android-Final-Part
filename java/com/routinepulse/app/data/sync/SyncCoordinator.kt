package com.routinepulse.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.routinepulse.app.data.local.db.CompletionDao
import com.routinepulse.app.data.local.db.HabitDao
import com.routinepulse.app.data.local.db.NoteDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit

/**
 * Glue between the UI, the database and WorkManager:
 *  - request()  schedules a sync that runs as soon as the phone is online (offline changes wait in Room),
 *  - pending / syncing / completed  feed the Settings screen and let lists reload after a sync.
 */
class SyncCoordinator(
    private val context: Context,
    private val engine: SyncEngine,
    habits: HabitDao,
    completions: CompletionDao,
    notes: NoteDao
) : SyncStatus {

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override val pending: Flow<Int> =
        combine(habits.dirtyCount(), completions.unsyncedCount(), notes.dirtyCount()) { h, c, n -> h + c + n }
    override val syncing: StateFlow<Boolean> get() = engine.syncing
    override val completed: SharedFlow<Unit> get() = engine.completed

    override fun request() {
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, work)
        Log.d(TAG, "Sync requested (runs when online)")
    }

    /** Called once at app start: also syncs every ~15 minutes in the background while online. */
    fun schedulePeriodic() {
        val work = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(connected).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    private companion object {
        const val TAG = "SyncCoordinator"
        const val ONE_TIME = "routinepulse_sync_once"
        const val PERIODIC = "routinepulse_sync_periodic"
    }
}
