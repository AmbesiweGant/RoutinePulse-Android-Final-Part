package com.routinepulse.app.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.routinepulse.app.RoutinePulseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Receives push messages from Firebase - even when the app is closed - and turns them into notifications. */
class RoutinePulseMessagingService : FirebaseMessagingService() {

    private fun container() = (application as RoutinePulseApp).container

    override fun onNewToken(token: String) {
        Log.i(TAG, "New FCM token")
        container().pushManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Push received: type=${message.data["type"]}")
        val settings = container().settingsStore
        // Respect the in-app switch and never show one person's alerts to a logged-out phone.
        val enabled = runBlocking { settings.notificationsFlow.first() }
        val loggedIn = runBlocking { !settings.tokenFlow.first().isNullOrBlank() }
        if (!enabled || !loggedIn) return
        NotificationHelper.show(applicationContext, message.data)
    }

    private companion object {
        const val TAG = "PushService"
    }
}
