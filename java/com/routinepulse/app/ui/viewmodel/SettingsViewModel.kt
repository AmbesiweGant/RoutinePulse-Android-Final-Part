package com.routinepulse.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinepulse.app.data.local.SettingsStore
import com.routinepulse.app.data.local.ThemeMode
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.remote.ErrorKind
import com.routinepulse.app.data.repository.AuthRepository
import com.routinepulse.app.data.repository.CloudRepository
import com.routinepulse.app.data.sync.SyncStatus
import com.routinepulse.app.push.LocaleHelper
import com.routinepulse.app.push.PushManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CloudStatus { IDLE, CHECKING, ONLINE, DB_DOWN, OFFLINE }
enum class TestPushState { IDLE, SENDING, SENT, NO_DEVICE, NOT_CONFIGURED, OFFLINE, FAILED }

data class SettingsUiState(
    val language: String = LocaleHelper.currentLanguage(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notifications: Boolean = true,
    val displayName: String = "",
    val email: String = "",
    val ssoProvider: String = "local",
    val cloud: CloudStatus = CloudStatus.IDLE,
    val pending: Int = 0,
    val syncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val testPush: TestPushState = TestPushState.IDLE,
    val deleting: Boolean = false,
    val deleteError: ApiError? = null
)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val auth: AuthRepository,
    private val cloud: CloudRepository,
    private val sync: SyncStatus,
    private val push: PushManager
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Mirror each stored / live value into the UI state so changes show up instantly.
        viewModelScope.launch { settings.languageFlow.collect { v -> _state.update { it.copy(language = v) } } }
        viewModelScope.launch { settings.themeFlow.collect { v -> _state.update { it.copy(theme = v) } } }
        viewModelScope.launch { settings.notificationsFlow.collect { v -> _state.update { it.copy(notifications = v) } } }
        viewModelScope.launch { settings.displayNameFlow.collect { v -> _state.update { it.copy(displayName = v) } } }
        viewModelScope.launch { settings.emailFlow.collect { v -> _state.update { it.copy(email = v) } } }
        viewModelScope.launch { settings.ssoProviderFlow.collect { v -> _state.update { it.copy(ssoProvider = v) } } }
        viewModelScope.launch { settings.lastSyncedFlow.collect { v -> _state.update { it.copy(lastSyncedAt = v) } } }
        viewModelScope.launch { sync.pending.collect { v -> _state.update { it.copy(pending = v) } } }
        viewModelScope.launch { sync.syncing.collect { v -> _state.update { it.copy(syncing = v) } } }
        checkCloud()
    }

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            settings.setLanguage(tag)
            LocaleHelper.setLanguage(tag)
            _state.update { it.copy(language = tag) }
        }
    }

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { settings.setTheme(mode) } }

    /** Saves the switch, then tells the server so it stops (or starts) sending push messages to this phone. */
    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settings.setNotifications(enabled)
            push.register()
        }
    }

    fun logout() { viewModelScope.launch { auth.logout() } }

    /** On success the session is cleared, so the navigation layer returns to the login screen by itself. */
    fun deleteAccount() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            when (val r = auth.deleteAccount()) {
                is ApiResult.Success -> _state.update { it.copy(deleting = false) }
                is ApiResult.Failure -> _state.update { it.copy(deleting = false, deleteError = r.error) }
            }
        }
    }
    fun syncNow() = sync.request()

    /** Pings GET /health so the user (and the demo video) can see the hosted API + database status. */
    fun checkCloud() {
        _state.update { it.copy(cloud = CloudStatus.CHECKING) }
        viewModelScope.launch {
            val status = when (val r = cloud.ping()) {
                is ApiResult.Success -> if (r.data.db == "connected") CloudStatus.ONLINE else CloudStatus.DB_DOWN
                is ApiResult.Failure -> CloudStatus.OFFLINE
            }
            _state.update { it.copy(cloud = status) }
        }
    }

    /** Asks the server to push a test notification to this phone (great for the demo video). */
    fun sendTestPush() {
        if (_state.value.testPush == TestPushState.SENDING) return
        _state.update { it.copy(testPush = TestPushState.SENDING) }
        viewModelScope.launch {
            push.register() // make sure the server knows this phone's current token first
            val outcome = when (val r = push.sendTest()) {
                is ApiResult.Success -> if (r.data.sent > 0) TestPushState.SENT else TestPushState.FAILED
                is ApiResult.Failure -> when (r.error.kind) {
                    ErrorKind.NETWORK -> TestPushState.OFFLINE
                    ErrorKind.CONFLICT -> TestPushState.NO_DEVICE
                    ErrorKind.SERVER -> TestPushState.NOT_CONFIGURED
                    else -> TestPushState.FAILED
                }
            }
            _state.update { it.copy(testPush = outcome) }
        }
    }
}
