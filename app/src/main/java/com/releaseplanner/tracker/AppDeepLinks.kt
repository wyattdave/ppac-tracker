package com.releaseplanner.tracker

import android.net.Uri

object AppDeepLinks {
    const val SCHEME = "ppactracker"
    const val UPDATES_HOST = "updates"

    private const val TASK_PATH = "task"

    fun taskUri(taskId: String): String {
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(UPDATES_HOST)
            .appendPath(TASK_PATH)
            .appendPath(taskId)
            .build()
            .toString()
    }

    fun taskIdFrom(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != SCHEME || uri.host != UPDATES_HOST) return null
        val pathSegments = uri.pathSegments
        if (pathSegments.firstOrNull() != TASK_PATH) return null
        return pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }
    }
}