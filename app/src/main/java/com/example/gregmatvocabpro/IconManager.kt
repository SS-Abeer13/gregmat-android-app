package com.example.gregmatvocabpro

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Swaps the launcher icon based on how long it has been since the app was
 * opened or based on daily completion state sent from the frontend.
 */
object IconManager {

    private const val TAG = "IconManager"
    private const val PREFS = "gregmat_icon"
    private const val KEY_LAST_OPENED = "last_opened_at"
    private const val KEY_CURRENT_ALIAS = "current_alias"
    private const val KEY_PENDING_ALIAS = "pending_alias"

    enum class IconState(val aliasSuffix: String, val minDaysAway: Int) {
        HAPPY("MainActivityHappy", 0),
        NEUTRAL("MainActivityNeutral", 1),
        SAD("MainActivitySad", 2),
        CRYING("MainActivityCrying", 4);

        fun getAlias(context: Context): String = "${context.packageName}.$aliasSuffix"

        companion object {
            fun forDaysAway(days: Long): IconState =
                entries.lastOrNull { days >= it.minDaysAway } ?: HAPPY

            fun fromKey(key: String): IconState {
                return when (key.lowercase().trim()) {
                    "celebration", "happy", "graduation" -> HAPPY
                    "concerned", "neutral" -> NEUTRAL
                    "sad" -> SAD
                    "very-sad", "verysad", "crying", "anxious" -> CRYING
                    else -> HAPPY
                }
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun onAppOpened(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_OPENED, System.currentTimeMillis()).apply()
    }

    fun setAppIcon(context: Context, key: String) {
        // IMPORTANT: only *queue* the desired icon here. Toggling the launcher
        // activity-alias while the app is in the foreground tears down the
        // running task (even with DONT_KILL_APP), which is what made the app
        // "auto close" on every mood change. The actual switch is deferred to
        // applyPendingIconSwitch(), called from MainActivity.onStop() — i.e.
        // when the user has already left/closed the app.
        val state = IconState.fromKey(key)
        prefs(context).edit().putString(KEY_PENDING_ALIAS, state.getAlias(context)).apply()
        Log.i(TAG, "Queued dynamic icon ${state.name} for key '$key' (applies on app close)")
    }

    /**
     * Applies whatever icon was last queued by [setAppIcon]. Call this only
     * when the app is leaving the foreground (onStop), so the alias swap can't
     * kill a visible activity.
     */
    fun applyPendingIconSwitch(context: Context) {
        val pending = prefs(context).getString(KEY_PENDING_ALIAS, null) ?: return
        val current = prefs(context).getString(KEY_CURRENT_ALIAS, null)
        if (pending == current) return
        val state = IconState.entries.firstOrNull { it.getAlias(context) == pending } ?: return
        apply(context, state)
    }

    /** Recompute from the stored timestamp. Safe to call as often as you like. */
    fun refresh(context: Context) {
        val last = prefs(context).getLong(KEY_LAST_OPENED, 0L)
        if (last == 0L) {
            apply(context, IconState.HAPPY)
            return
        }
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last)
        val state = IconState.forDaysAway(days)
        apply(context, state)
    }

    fun daysSinceOpened(context: Context): Long {
        val last = prefs(context).getLong(KEY_LAST_OPENED, 0L)
        if (last == 0L) return 0L
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last)
    }

    /**
     * Daily alarm re-evaluates mascot icon while app is closed.
     */
    fun scheduleDailyCheck(context: Context) {
        // No-op: halted per user request
    }

    /** Enable the alias for [state] safely without destroying foreground activities. */
    fun apply(context: Context, state: IconState) {
        val targetAlias = state.getAlias(context)
        val stored = prefs(context).getString(KEY_CURRENT_ALIAS, null)
        if (stored == targetAlias) return

        val pm = context.packageManager
        try {
            pm.setComponentEnabledSetting(
                ComponentName(context, targetAlias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            IconState.entries
                .filter { it != state }
                .forEach { other ->
                    pm.setComponentEnabledSetting(
                        ComponentName(context, other.getAlias(context)),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }

            prefs(context).edit()
                .putString(KEY_CURRENT_ALIAS, targetAlias)
                .putString(KEY_PENDING_ALIAS, targetAlias)
                .apply()
            Log.i(TAG, "Launcher icon -> ${state.name} (${targetAlias})")
        } catch (e: Exception) {
            Log.w(TAG, "Could not change launcher icon: ${e.message}")
        }
    }
}
