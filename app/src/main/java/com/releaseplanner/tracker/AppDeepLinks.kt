package com.releaseplanner.tracker

import android.net.Uri

object AppDeepLinks {
    const val SCHEME = "ppactracker"
    const val UPDATES_HOST = "updates"
    const val WEB_SCHEME = "https"
    const val WEB_HOST = "wyattdave.com"

    private const val WEB_ROOT_PATH = "ppac-tracker"
    private const val TASK_PATH = "task"

    fun taskUri(taskId: String): String {
        return Uri.Builder()
            .scheme(WEB_SCHEME)
            .authority(WEB_HOST)
            .appendPath(WEB_ROOT_PATH)
            .appendPath(TASK_PATH)
            .appendPath(taskId)
            .build()
            .toString()
    }

    fun appTaskUri(taskId: String): String {
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
        val pathSegments = uri.pathSegments
        return when {
            uri.scheme == SCHEME && uri.host == UPDATES_HOST -> {
                if (pathSegments.firstOrNull() != TASK_PATH) return null
                pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }
            }
            uri.scheme.equals(WEB_SCHEME, ignoreCase = true) && uri.host.equals(WEB_HOST, ignoreCase = true) -> {
                if (pathSegments.getOrNull(0) != WEB_ROOT_PATH || pathSegments.getOrNull(1) != TASK_PATH) return null
                pathSegments.getOrNull(2)?.takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }
}