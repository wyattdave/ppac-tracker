package com.releaseplanner.tracker.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.releaseplanner.tracker.data.NotificationSettings
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReleaseSyncScheduler {
    const val ACTION_DAILY_NOTIFICATION = "com.releaseplanner.tracker.action.DAILY_NOTIFICATION"

    private const val legacyWorkName = "release-planner-sync"
    private const val syncWorkName = "release-planner-background-sync"
    private const val alarmRequestCode = 4201

    fun schedule(context: Context, settings: NotificationSettings) {
        WorkManager.getInstance(context).cancelUniqueWork(legacyWorkName)
        scheduleAlarm(context, settings)
    }

    fun reschedule(context: Context, settings: NotificationSettings) {
        cancelAlarm(context)
        scheduleAlarm(context, settings)
    }

    fun scheduleNext(context: Context, settings: NotificationSettings) {
        scheduleAlarm(context, settings)
    }

    fun enqueueSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReleaseSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            syncWorkName,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        cancelAlarm(context)
        WorkManager.getInstance(context).cancelUniqueWork(legacyWorkName)
        WorkManager.getInstance(context).cancelUniqueWork(syncWorkName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun scheduleAlarm(context: Context, settings: NotificationSettings) {
        if (!settings.enabled) {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAtMillis = nextTriggerAt(settings).toInstant().toEpochMilli()
        val pendingIntent = alarmPendingIntent(context)

        if (canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun nextTriggerAt(
        settings: NotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): ZonedDateTime {
        var nextRun = now.toLocalDate()
            .atTime(settings.hour, settings.minute)
            .atZone(now.zone)
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }
        return nextRun
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReleaseSyncAlarmReceiver::class.java)
            .setAction(ACTION_DAILY_NOTIFICATION)
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
