package com.routinepulse.app.ui.screens

import android.Manifest
import android.text.format.DateUtils as AndroidDateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routinepulse.app.BuildConfig
import com.routinepulse.app.R
import com.routinepulse.app.data.local.ThemeMode
import com.routinepulse.app.push.LocaleHelper
import com.routinepulse.app.push.NotificationHelper
import com.routinepulse.app.ui.components.*
import com.routinepulse.app.ui.theme.PriorityHigh
import com.routinepulse.app.ui.viewmodel.CloudStatus
import com.routinepulse.app.ui.viewmodel.SettingsViewModel
import com.routinepulse.app.ui.viewmodel.TestPushState

/** Mockup 6: language, theme, notifications, cloud sync, account / SSO and log out (REQ-03, REQ-08, REQ-10). */
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var currentLanguage by remember(s.language) { mutableStateOf(if (s.language in LocaleHelper.supported) s.language else LocaleHelper.currentLanguage()) }

    // Android 13+ asks the user for permission before an app may show notifications.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotifications(granted)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(stringResource(R.string.settings_title))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- Language (English / isiZulu / Afrikaans) ----
            SettingsCard {
                SectionLabel(stringResource(R.string.settings_language))
                Text(
                    stringResource(R.string.settings_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChoiceRow(
                    options = listOf(
                        "en" to stringResource(R.string.lang_en),
                        "zu" to stringResource(R.string.lang_zu),
                        "af" to stringResource(R.string.lang_af),
                        "xh" to stringResource(R.string.lang_xh)
                    ),
                    selected = currentLanguage,
                    onSelect = { tag ->
                        currentLanguage = tag
                        vm.setLanguage(tag)
                    }
                )
            }

            // ---- Theme ----
            SettingsCard {
                SectionLabel(stringResource(R.string.settings_theme))
                ChoiceRow(
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark)
                    ),
                    selected = s.theme,
                    onSelect = vm::setTheme
                )
            }

            // ---- Push notifications ----
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel(stringResource(R.string.settings_notifications))
                        Text(
                            stringResource(R.string.settings_notifications_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = s.notifications,
                        onCheckedChange = { wanted ->
                            if (wanted && !NotificationHelper.hasPermission(context)) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setNotifications(wanted)
                            }
                        }
                    )
                }
                if (s.notifications && !NotificationHelper.hasPermission(context)) {
                    Text(
                        stringResource(R.string.settings_notifications_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = PriorityHigh
                    )
                }
                SecondaryButton(
                    text = stringResource(if (s.testPush == TestPushState.SENDING) R.string.settings_test_sending else R.string.settings_test_push),
                    onClick = vm::sendTestPush,
                    enabled = s.testPush != TestPushState.SENDING
                )
                val testMessage = when (s.testPush) {
                    TestPushState.IDLE, TestPushState.SENDING -> null
                    TestPushState.SENT -> R.string.test_push_sent
                    TestPushState.NO_DEVICE -> R.string.test_push_no_device
                    TestPushState.NOT_CONFIGURED -> R.string.test_push_not_configured
                    TestPushState.OFFLINE -> R.string.error_network
                    TestPushState.FAILED -> R.string.test_push_failed
                }
                if (testMessage != null) {
                    Text(
                        stringResource(testMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.testPush == TestPushState.SENT) MaterialTheme.colorScheme.primary else PriorityHigh
                    )
                }
            }

            // ---- Offline sync ----
            SettingsCard {
                SectionLabel(stringResource(R.string.settings_sync))
                val status = when {
                    s.syncing -> stringResource(R.string.sync_syncing)
                    s.pending > 0 -> stringResource(R.string.sync_pending, s.pending)
                    s.lastSyncedAt > 0L -> stringResource(
                        R.string.sync_last,
                        AndroidDateUtils.getRelativeTimeSpanString(
                            s.lastSyncedAt, System.currentTimeMillis(), AndroidDateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    )
                    else -> stringResource(R.string.sync_never)
                }
                Text(status, fontWeight = FontWeight.SemiBold, color = if (s.pending > 0) PriorityHigh else MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.settings_sync_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = vm::syncNow, enabled = !s.syncing) { Text(stringResource(R.string.sync_now)) }
            }

            // ---- Cloud status ----
            SettingsCard {
                SectionLabel(stringResource(R.string.settings_cloud))
                val (text, color) = when (s.cloud) {
                    CloudStatus.IDLE, CloudStatus.CHECKING -> stringResource(R.string.cloud_checking) to MaterialTheme.colorScheme.onSurfaceVariant
                    CloudStatus.ONLINE -> stringResource(R.string.cloud_online) to MaterialTheme.colorScheme.primary
                    CloudStatus.DB_DOWN -> stringResource(R.string.cloud_db_down) to PriorityHigh
                    CloudStatus.OFFLINE -> stringResource(R.string.cloud_offline) to PriorityHigh
                }
                Text(text, color = color, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = vm::checkCloud) { Text(stringResource(R.string.cloud_recheck)) }
            }

            // ---- Account & SSO ----
            SettingsCard {
                SectionLabel(stringResource(R.string.settings_account))
                Text(s.displayName.ifBlank { "-" })
                Text(s.email.ifBlank { "-" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(if (s.ssoProvider == "google") R.string.account_google else R.string.account_password),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))
            SecondaryButton(text = stringResource(R.string.settings_logout), onClick = { confirmLogout = true })
            s.deleteError?.let { ErrorBanner(errorMessage(it)) }
            SecondaryButton(
                text = stringResource(if (s.deleting) R.string.delete_account_working else R.string.settings_delete_account),
                onClick = { confirmDelete = true },
                enabled = !s.deleting
            )
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteAccount() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = {
                Text(
                    if (s.pending > 0) stringResource(R.string.settings_logout_pending, s.pending)
                    else stringResource(R.string.settings_logout_message)
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text(stringResource(R.string.settings_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
