package com.routinepulse.app.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.routinepulse.app.MainActivity
import com.routinepulse.app.R

/** Builds and shows the notifications. Text is created HERE (not on the server) so it follows the app language. */
object NotificationHelper {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_ALERTS = "alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** True when the app may post notifications (Android 13+ needs the runtime permission). */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission() just below
    fun show(context: Context, data: Map<String, String>): Boolean {
        val type = data["type"] ?: return false
        val ctx = LocaleHelper.localized(context)
        val habitTitle = data["habitTitle"].orEmpty()
        val streak = data["streak"]?.toIntOrNull() ?: 0
        val hoursLeft = data["hoursLeft"]?.toIntOrNull() ?: 0

        val title: String
        val body: String
        val channel: String
        when (type) {
            "reminder" -> {
                title = ctx.getString(R.string.push_reminder_title)
                body = ctx.getString(R.string.push_reminder_body, habitTitle)
                channel = CHANNEL_REMINDERS
            }
            "streak_risk" -> {
                title = ctx.getString(R.string.push_streak_title)
                body = ctx.getString(R.string.push_streak_body, streak, habitTitle, hoursLeft)
                channel = CHANNEL_ALERTS
            }
            "test" -> {
                title = ctx.getString(R.string.push_test_title)
                body = ctx.getString(R.string.push_test_body)
                channel = CHANNEL_ALERTS
            }
            else -> return false
        }
        if (!hasPermission(context)) return false

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify((type + habitTitle).hashCode(), notification)
        return true
    }
}
