package com.releaseplanner.tracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.releaseplanner.tracker.data.ReleaseConfigLoader
import com.releaseplanner.tracker.data.ReleasePreferences
import com.releaseplanner.tracker.data.ReleaseRepository
import com.releaseplanner.tracker.data.local.ReleasePlannerDatabase
import com.releaseplanner.tracker.notifications.ReleaseNotifications
import java.io.IOException

class ReleaseSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val database = ReleasePlannerDatabase.get(applicationContext)
        val sources = ReleaseConfigLoader.loadSources(applicationContext)
        val preferences = ReleasePreferences(applicationContext, sources)
        if (!preferences.notificationSettings.value.enabled) return Result.success()

        val repository = ReleaseRepository(database, sources = sources, preferences = preferences)
        val result = try {
            repository.refresh()
        } catch (exception: IOException) {
            return Result.retry()
        }

        if (result.sourceCount > 0 && result.failedSources.size == result.sourceCount) {
            return Result.retry()
        }

        ReleaseNotifications.showSyncNotification(applicationContext, repository.summaryMetrics())
        ReleaseSyncScheduler.scheduleNext(applicationContext, preferences.notificationSettings.value)
        return Result.success()
    }
}
