package com.releaseplanner.tracker.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.releaseplanner.tracker.data.NotificationSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReleaseSyncScheduler {
    private const val workName = "release-planner-sync"

    fun schedule(context: Context, settings: NotificationSettings) {
        enqueue(context, settings, ExistingWorkPolicy.KEEP)
    }

    fun reschedule(context: Context, settings: NotificationSettings) {
        enqueue(context, settings, ExistingWorkPolicy.REPLACE)
    }

    fun scheduleNext(context: Context, settings: NotificationSettings) {
        enqueue(context, settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(
        context: Context,
        settings: NotificationSettings,
        existingWorkPolicy: ExistingWorkPolicy,
    ) {
        if (!settings.enabled) {
            cancel(context)
            return
        }

        val request = OneTimeWorkRequestBuilder<ReleaseSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(initialDelayMinutes(settings), TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
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
