package com.releaseplanner.tracker

import android.app.Application
import com.releaseplanner.tracker.data.ReleaseConfigLoader
import com.releaseplanner.tracker.data.ReleasePreferences
import com.releaseplanner.tracker.notifications.ReleaseNotifications
import com.releaseplanner.tracker.sync.ReleaseSyncScheduler

class ReleasePlannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReleaseNotifications.ensureChannel(this)
        val sources = ReleaseConfigLoader.loadSources(this)
        val preferences = ReleasePreferences(this, sources)
        ReleaseSyncScheduler.schedule(this, preferences.notificationSettings.value)
    }
}
