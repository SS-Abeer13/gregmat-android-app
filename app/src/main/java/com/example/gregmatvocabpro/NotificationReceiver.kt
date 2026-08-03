package com.example.gregmatvocabpro

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Fires one study reminder, then re-arms itself for the next occurrence.
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val kind = intent?.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: "dailyWords"
        val title = intent?.getStringExtra(ReminderScheduler.EXTRA_TITLE)
            ?: "GRE VocabPro Reminder"
        val body = intent?.getStringExtra(ReminderScheduler.EXTRA_BODY)
            ?: "Time to study your GRE words."

        ReminderScheduler.ensureChannel(context)

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Deep-link so tapping the reminder opens MainActivity
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_reminder_kind", kind)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (kind.hashCode() and 0x7FFFFFFF) % 1000,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = R.mipmap.ic_launcher

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify((kind.hashCode() and 0x7FFFFFFF) % 1000, notification)

        // Reschedule alarm for next occurrence
        ReminderScheduler.rescheduleAfterFiring(context, kind)
    }
}
