package com.example.gregmatvocabpro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms do not survive a reboot or an app update, so every reminder silently
 * stopped working until the app was reopened. This puts them back from the
 * settings persisted by ReminderScheduler.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ReminderScheduler.rescheduleFromStorage(context)
                // The icon-refresh alarm is lost on reboot too.
                IconManager.scheduleDailyCheck(context)
                IconManager.refresh(context)
            }
        }
    }
}
