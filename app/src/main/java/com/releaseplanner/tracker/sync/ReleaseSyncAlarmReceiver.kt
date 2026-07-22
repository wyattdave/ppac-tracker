package com.releaseplanner.tracker.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.releaseplanner.tracker.data.ReleaseConfigLoader
import com.releaseplanner.tracker.data.ReleasePreferences
import com.releaseplanner.tracker.data.ReleaseRepository
import com.releaseplanner.tracker.data.local.ReleasePlannerDatabase
import com.releaseplanner.tracker.notifications.ReleaseNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReleaseSyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReleaseSyncScheduler.ACTION_DAILY_NOTIFICATION) return

        val appContext = context.applicationContext
        val sources = ReleaseConfigLoader.loadSources(appContext)
        val preferences = ReleasePreferences(appContext, sources)

        val settings = preferences.notificationSettings.value
        ReleaseSyncScheduler.scheduleNext(appContext, settings)
        if (!settings.enabled) return

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val repository = ReleaseRepository(
                    database = ReleasePlannerDatabase.get(appContext),
                    sources = sources,
                    preferences = preferences,
                )
                ReleaseNotifications.showSyncNotification(appContext, repository.summaryMetrics())
                ReleaseSyncScheduler.enqueueSync(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
