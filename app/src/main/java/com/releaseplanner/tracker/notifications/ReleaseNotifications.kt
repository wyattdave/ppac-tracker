package com.releaseplanner.tracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.releaseplanner.tracker.R
import com.releaseplanner.tracker.data.SyncResult

object ReleaseNotifications {
    private const val channelId = "release_updates"
    private const val notificationId = 4201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            channelId,
            "Release updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "New and changed Microsoft release planner updates"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showSyncNotification(context: Context, result: SyncResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val text = buildString {
            if (result.newCount > 0) append("${result.newCount} new")
            if (result.newCount > 0 && result.changedCount > 0) append(", ")
            if (result.changedCount > 0) append("${result.changedCount} changed")
            append(" release updates")
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Release planner updated")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
