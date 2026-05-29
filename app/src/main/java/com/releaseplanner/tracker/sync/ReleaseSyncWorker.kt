package com.releaseplanner.tracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.releaseplanner.tracker.data.ReleaseConfigLoader
import com.releaseplanner.tracker.data.ReleasePreferences
import com.releaseplanner.tracker.data.ReleaseRepository
import com.releaseplanner.tracker.data.local.ReleasePlannerDatabase
import com.releaseplanner.tracker.notifications.ReleaseNotifications

class ReleaseSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val database = ReleasePlannerDatabase.get(applicationContext)
        val sources = ReleaseConfigLoader.loadSources(applicationContext)
        val preferences = ReleasePreferences(applicationContext, sources)
        val repository = ReleaseRepository(database, sources = sources, preferences = preferences)
        val result = repository.refresh()

        if (result.sourceCount > 0 && result.failedSources.size == result.sourceCount) {
            return Result.retry()
        }

        if (result.hasChanges) {
            ReleaseNotifications.showSyncNotification(applicationContext, result)
        }
        return Result.success()
    }
}
