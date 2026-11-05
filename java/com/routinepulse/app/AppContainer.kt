package com.routinepulse.app

import android.content.Context
import com.routinepulse.app.data.local.SettingsStore
import com.routinepulse.app.data.local.db.RoutinePulseDb
import com.routinepulse.app.data.remote.ApiClient
import com.routinepulse.app.data.repository.*
import com.routinepulse.app.data.sync.SyncCoordinator
import com.routinepulse.app.data.sync.SyncEngine
import com.routinepulse.app.push.PushManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Simple manual dependency container (keeps the project free of DI-framework setup). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(appContext)
    private val api = ApiClient.create(settingsStore)
    private val database = RoutinePulseDb.create(appContext)

    // Offline sync (Room + WorkManager)
    val syncEngine = SyncEngine(
        api = api,
        habits = database.habitDao(),
        completions = database.completionDao(),
        notes = database.noteDao(),
        onSynced = { settingsStore.setLastSynced(it) }
    )
    val syncCoordinator = SyncCoordinator(
        appContext, syncEngine, database.habitDao(), database.completionDao(), database.noteDao()
    )

    // Push notifications (Firebase Cloud Messaging)
    val pushManager = PushManager(appContext, api, settingsStore)

    private val localCleaner = object : LocalDataCleaner {
        override suspend fun clearAll() {
            withContext(Dispatchers.IO) { database.clearAllTables() }
        }
    }

    val authRepository: AuthRepository = RemoteAuthRepository(api, settingsStore, localCleaner, pushManager, syncCoordinator)
    val cloudRepository: CloudRepository = RemoteCloudRepository(api)
    val habitRepository: HabitRepository =
        OfflineHabitRepository(database.habitDao(), database.completionDao(), syncCoordinator)
    val noteRepository: NoteRepository = OfflineNoteRepository(database.noteDao(), syncCoordinator)
    val quizRepository: QuizRepository = RemoteQuizRepository(api, database.noteDao(), syncEngine)
    val badgeTracker = BadgeTracker(settingsStore)
}
