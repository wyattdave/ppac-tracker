package com.releaseplanner.tracker.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.releaseplanner.tracker.data.ReleaseConfigLoader
import com.releaseplanner.tracker.data.ReleasePreferences

class ReleaseSyncRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in rescheduleActions) return

        val appContext = context.applicationContext
        val sources = ReleaseConfigLoader.loadSources(appContext)
        val preferences = ReleasePreferences(appContext, sources)
        ReleaseSyncScheduler.schedule(appContext, preferences.notificationSettings.value)
    }

    private companion object {
        val rescheduleActions = setOf(
            EXACT_ALARM_PERMISSION_CHANGED_ACTION,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        const val EXACT_ALARM_PERMISSION_CHANGED_ACTION =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
