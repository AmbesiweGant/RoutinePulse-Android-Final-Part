package com.routinepulse.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.routinepulse.app.data.local.SettingsStore
import com.routinepulse.app.data.model.DeviceRequest
import com.routinepulse.app.data.model.DeviceUnregisterRequest
import com.routinepulse.app.data.model.TestPushResponse
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.remote.ApiService
import com.routinepulse.app.data.remote.safeApiCall
import com.routinepulse.app.data.repository.PushGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.TimeZone

/**
 * Connects this phone to Firebase Cloud Messaging and tells our API which device token + time zone to use
 * (the server needs the time zone to fire reminders at the right local time).
 * If google-services.json was not added, Firebase is not configured and every method quietly does nothing.
 */
class PushManager(
    private val context: Context,
    private val api: ApiService,
    private val settings: SettingsStore
) : PushGateway {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    override suspend fun register(): Boolean {
        if (!isAvailable) {
            Log.i(TAG, "Firebase is not configured (no google-services.json) - push disabled")
            return false
        }
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get an FCM token: ${e.message}")
            return false
        }
        return upload(token)
    }

    private suspend fun upload(token: String): Boolean {
        if (settings.tokenFlow.first().isNullOrBlank()) return false // not logged in yet; we re-register after login
        val enabled = settings.notificationsFlow.first()
        val result = safeApiCall { api.registerDevice(DeviceRequest(token, TimeZone.getDefault().id, enabled)) }
        return if (result is ApiResult.Success) {
            settings.setRegisteredPushToken(token)
            Log.i(TAG, "Device registered for push (enabled=$enabled)")
            true
        } else {
            Log.w(TAG, "Device registration failed: $result")
            false
        }
    }

    /** Called by the messaging service when Firebase rotates the token. */
    fun onNewToken(token: String) {
        scope.launch { upload(token) }
    }

    override suspend fun unregister() {
        val token = settings.registeredPushTokenFlow.first() ?: return
        withTimeoutOrNull(5_000) { safeApiCall { api.unregisterDevice(DeviceUnregisterRequest(token)) } }
        settings.setRegisteredPushToken(null)
    }

    /** Asks the server to send a test notification to this user's devices (Settings > "Send test notification"). */
    suspend fun sendTest(): ApiResult<TestPushResponse> = safeApiCall { api.testPush() }

    private companion object {
        const val TAG = "PushManager"
    }
}
