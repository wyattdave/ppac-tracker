package com.releaseplanner.tracker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.releaseplanner.tracker.data.NotificationSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReleaseSyncScheduler {
    private const val workName = "release-planner-sync"

    fun schedule(context: Context, settings: NotificationSettings) {
        enqueue(context, settings, ExistingPeriodicWorkPolicy.KEEP)
    }

    fun reschedule(context: Context, settings: NotificationSettings) {
        enqueue(context, settings, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
    }

    private fun enqueue(
        context: Context,
        settings: NotificationSettings,
        existingWorkPolicy: ExistingPeriodicWorkPolicy,
    ) {
        if (!settings.enabled) {
            cancel(context)
            return
        }

        val request = PeriodicWorkRequestBuilder<ReleaseSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(initialDelayMinutes(settings), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            existingWorkPolicy,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }

    private fun initialDelayMinutes(settings: NotificationSettings): Long {
        val now = LocalDateTime.now()
        var nextRun = now.toLocalDate().atTime(LocalTime.of(settings.hour, settings.minute))
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }
        return Duration.between(now, nextRun).toMinutes().coerceAtLeast(1)
    }
}
