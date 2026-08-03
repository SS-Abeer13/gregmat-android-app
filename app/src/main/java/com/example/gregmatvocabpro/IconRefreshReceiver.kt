package com.example.gregmatvocabpro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-evaluates the launcher icon once a day while the app is closed.
 *
 * Without this the mascot could only ever change at launch — which is precisely
 * the moment it resets to happy, so it would never look sad.
 */
class IconRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        IconManager.refresh(context)
    }
}
