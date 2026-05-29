package com.releaseplanner.tracker

import android.app.Application
import com.releaseplanner.tracker.notifications.ReleaseNotifications
import com.releaseplanner.tracker.sync.ReleaseSyncScheduler

class ReleasePlannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReleaseNotifications.ensureChannel(this)
        ReleaseSyncScheduler.schedule(this)
    }
}
