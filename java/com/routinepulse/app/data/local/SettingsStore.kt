package com.routinepulse.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val wire: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromWire(v: String?): ThemeMode = entries.firstOrNull { it.wire == v } ?: SYSTEM
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "routinepulse_settings")

/**
 * Persists user settings (theme, notifications), the login session and a few bookkeeping values using
 * Jetpack DataStore. Only the JWT token is stored - the password is never kept on the device.
 * (The language is stored by AppCompat itself - see push/LocaleHelper.kt.)
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_tag")
        val THEME = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val TOKEN = stringPreferencesKey("auth_token")
        val NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")
        val SSO_PROVIDER = stringPreferencesKey("sso_provider")
        val LAST_USER_EMAIL = stringPreferencesKey("last_user_email")
        val LAST_SYNCED = longPreferencesKey("last_synced_at")
        val PUSH_TOKEN = stringPreferencesKey("registered_push_token")
        val PUSH_PERMISSION_ASKED = booleanPreferencesKey("push_permission_asked")
        val EARNED_BADGES = stringSetPreferencesKey("earned_badges")
    }

    private val prefs: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val languageFlow: Flow<String> = prefs.map { it[Keys.LANGUAGE] ?: "en" }
    val themeFlow: Flow<ThemeMode> = prefs.map { ThemeMode.fromWire(it[Keys.THEME]) }
    val notificationsFlow: Flow<Boolean> = prefs.map { it[Keys.NOTIFICATIONS] ?: true }
    val tokenFlow: Flow<String?> = prefs.map { it[Keys.TOKEN] }
    val displayNameFlow: Flow<String> = prefs.map { it[Keys.NAME] ?: "" }
    val emailFlow: Flow<String> = prefs.map { it[Keys.EMAIL] ?: "" }
    /** "local" (email + password) or "google" - how the current session was started. */
    val ssoProviderFlow: Flow<String> = prefs.map { it[Keys.SSO_PROVIDER] ?: "local" }
    /** Survives logout / session expiry so we can tell when a DIFFERENT person logs in on this phone. */
    val lastUserEmailFlow: Flow<String> = prefs.map { it[Keys.LAST_USER_EMAIL] ?: "" }
    /** Epoch milliseconds of the last fully successful sync (0 = never). */
    val lastSyncedFlow: Flow<Long> = prefs.map { it[Keys.LAST_SYNCED] ?: 0L }
    val registeredPushTokenFlow: Flow<String?> = prefs.map { it[Keys.PUSH_TOKEN] }
    val pushPermissionAskedFlow: Flow<Boolean> = prefs.map { it[Keys.PUSH_PERMISSION_ASKED] ?: false }
    val earnedBadgesFlow: Flow<Set<String>> = prefs.map { it[Keys.EARNED_BADGES] ?: emptySet() }

    suspend fun setLanguage(tag: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = tag }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.wire }
    }

    suspend fun setNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun saveSession(token: String, displayName: String, email: String, ssoProvider: String = "local") {
        context.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.NAME] = displayName
            it[Keys.EMAIL] = email
            it[Keys.SSO_PROVIDER] = ssoProvider
            it[Keys.LAST_USER_EMAIL] = email.lowercase()
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.TOKEN)
            it.remove(Keys.NAME)
            it.remove(Keys.EMAIL)
            it.remove(Keys.SSO_PROVIDER)
            it.remove(Keys.LAST_SYNCED)
            it.remove(Keys.PUSH_TOKEN)
        }
    }

    suspend fun setLastSynced(epochMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNCED] = epochMillis }
    }

    suspend fun setRegisteredPushToken(token: String?) {
        context.dataStore.edit { if (token == null) it.remove(Keys.PUSH_TOKEN) else it[Keys.PUSH_TOKEN] = token }
    }

    suspend fun setPushPermissionAsked(asked: Boolean) {
        context.dataStore.edit { it[Keys.PUSH_PERMISSION_ASKED] = asked }
    }

    suspend fun setEarnedBadges(names: Set<String>) {
        context.dataStore.edit { it[Keys.EARNED_BADGES] = names }
    }
}
