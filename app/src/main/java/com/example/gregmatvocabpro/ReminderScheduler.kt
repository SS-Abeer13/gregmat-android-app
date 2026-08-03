package com.example.gregmatvocabpro

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONArray
import java.util.Calendar

/**
 * One reminder the user has configured.
 *
 * [days] is a list of Calendar.DAY_OF_WEEK values (1 = Sunday .. 7 = Saturday).
 * An empty list means every day.
 */
data class Reminder(
    val kind: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val days: List<Int>,
    val title: String,
    val body: String
) {
    fun toJson(): String = org.json.JSONObject().apply {
        put("kind", kind)
        put("enabled", enabled)
        put("hour", hour)
        put("minute", minute)
        put("days", JSONArray(days))
        put("title", title)
        put("body", body)
    }.toString()
}

/**
 * Schedules study reminders using exact system alarm clock mechanisms.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    private const val PREFS = "gregmat_reminders"
    private const val KEY_REMINDERS = "reminders_json"

    const val CHANNEL_ID = "gregmat_study_reminders"
    const val EXTRA_KIND = "reminder_kind"
    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_BODY = "reminder_body"

    /** Stable request code per reminder kind so alarms replace rather than stack. */
    private fun requestCode(kind: String): Int = 2000 + (kind.hashCode() and 0x7FFFFFFF) % 1000

    /* ----------------------------------------------------------- persistence */

    fun save(context: Context, reminders: List<Reminder>) {
        val arr = JSONArray()
        reminders.forEach { arr.put(org.json.JSONObject(it.toJson())) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REMINDERS, arr.toString())
            .apply()
    }

    fun load(context: Context): List<Reminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REMINDERS, null) ?: return emptyList()
        return parse(raw)
    }

    /** Parses the JSON array the WebView sends (and the copy we persist). */
    fun parse(json: String): List<Reminder> {
        val out = mutableListOf<Reminder>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)

                // Accept either {hour,minute} (persisted) or {time:"HH:MM"} (web).
                var hour = o.optInt("hour", -1)
                var minute = o.optInt("minute", -1)
                if (hour < 0 || minute < 0) {
                    val time = o.optString("time", "09:00")
                    val parts = time.split(":")
                    hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
                    minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                }

                val daysArr = o.optJSONArray("days")
                val days = mutableListOf<Int>()
                if (daysArr != null) {
                    for (d in 0 until daysArr.length()) {
                        // Web sends 0=Sunday..6=Saturday; Calendar wants 1..7.
                        days.add(daysArr.getInt(d) + 1)
                    }
                }

                out.add(
                    Reminder(
                        kind = o.optString("kind", "dailyWords"),
                        enabled = o.optBoolean("enabled", false),
                        hour = hour.coerceIn(0, 23),
                        minute = minute.coerceIn(0, 59),
                        days = days,
                        title = o.optString("title", "GRE VocabPro"),
                        body = o.optString("body", "Time to study.")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse reminders: ${e.message}")
        }
        return out
    }

    /* ------------------------------------------------------------ scheduling */

    /** Cancel everything, then schedule each enabled reminder. */
    fun applyAll(context: Context, reminders: List<Reminder>) {
        ensureChannel(context)
        reminders.forEach { cancel(context, it.kind) }
        reminders.filter { it.enabled }.forEach { schedule(context, it) }
    }

    /** Re-apply whatever is currently persisted (used on boot and app start). */
    fun rescheduleFromStorage(context: Context) {
        val saved = load(context)
        if (saved.isNotEmpty()) applyAll(context, saved)
    }

    fun cancel(context: Context, kind: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntentFor(context, kind, null))
    }

    fun schedule(context: Context, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextOccurrence(reminder)
        val pi = pendingIntentFor(context, reminder.kind, reminder)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val showPi = PendingIntent.getActivity(
                    context,
                    requestCode(reminder.kind) + 10000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, showPi)
                am.setAlarmClock(alarmClockInfo, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.i(TAG, "Scheduled alarm for ${reminder.kind} at $triggerAt")
        } catch (e: Exception) {
            Log.w(TAG, "setAlarmClock failed for ${reminder.kind}: ${e.message}")
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not schedule ${reminder.kind}: ${e2.message}")
            }
        }
    }

    private fun pendingIntentFor(
        context: Context,
        kind: String,
        reminder: Reminder?
    ): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.example.gregmatvocabpro.REMIND.$kind"
            putExtra(EXTRA_KIND, kind)
            reminder?.let {
                putExtra(EXTRA_TITLE, it.title)
                putExtra(EXTRA_BODY, it.body)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Next future time matching the reminder's time and day filter. */
    fun nextOccurrence(reminder: Reminder, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)

        if (reminder.days.isNotEmpty()) {
            var guard = 0
            while (!reminder.days.contains(cal.get(Calendar.DAY_OF_WEEK)) && guard < 8) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                guard++
            }
        }
        return cal.timeInMillis
    }

    /** Called by the receiver after firing, so the alarm repeats daily. */
    fun rescheduleAfterFiring(context: Context, kind: String) {
        val reminder = load(context).firstOrNull { it.kind == kind && it.enabled } ?: return
        schedule(context, reminder)
    }

    /**
     * Name/description come from string resources (overridden per flavor at
     * app/src/bigbook/res/values/strings.xml) rather than being hardcoded
     * here, so each installed app's notification settings entry describes
     * its own actual purpose instead of both apps sharing identical
     * "Study Reminders" wording.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}
