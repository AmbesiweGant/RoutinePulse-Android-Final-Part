package com.routinepulse.app

import android.app.Application
import android.util.Log
import com.routinepulse.app.push.NotificationHelper

class RoutinePulseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        container.syncCoordinator.schedulePeriodic() // background sync every ~15 min while online
        Log.i("RoutinePulseApp", "Application started")
    }
}
