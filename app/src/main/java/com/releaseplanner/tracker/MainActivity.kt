package com.releaseplanner.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.releaseplanner.tracker.ui.ReleaseTrackerApp
import com.releaseplanner.tracker.ui.ReleaseTrackerViewModel
import com.releaseplanner.tracker.ui.theme.ReleasePlannerTheme
import com.releaseplanner.tracker.data.ReleaseConfigLoader
import com.releaseplanner.tracker.data.ReleasePreferences
import com.releaseplanner.tracker.sync.ReleaseSyncScheduler

class MainActivity : ComponentActivity() {
    private val requestedTaskId = mutableStateOf<String?>(null)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) requestExactAlarmAccessOnce()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermissions()
        requestedTaskId.value = intent.taskIdFromDeepLink()
        setContent {
            ReleasePlannerRoot(
                taskId = requestedTaskId.value,
                onTaskLinkHandled = { requestedTaskId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTaskId.value = intent.taskIdFromDeepLink()
    }

    private fun ensureNotificationPermissions() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestExactAlarmAccessOnce()
        }
    }

    private fun requestExactAlarmAccessOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ReleaseSyncScheduler.canScheduleExactAlarms(this)) return

        val sources = ReleaseConfigLoader.loadSources(this)
        val preferences = ReleasePreferences(this, sources)
        if (!preferences.notificationSettings.value.enabled || preferences.hasRequestedExactAlarmAccess()) return

        preferences.markExactAlarmAccessRequested()
        Toast.makeText(
            this,
            "Allow alarms and reminders so notifications arrive at the selected time.",
            Toast.LENGTH_LONG,
        ).show()
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}

@Composable
private fun ReleasePlannerRoot(taskId: String?, onTaskLinkHandled: () -> Unit) {
    val viewModel: ReleaseTrackerViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(taskId) {
        taskId?.let {
            viewModel.openTaskLink(it)
            onTaskLinkHandled()
        }
    }

    ReleasePlannerTheme(themeMode = state.themeMode) {
        ReleaseTrackerApp(viewModel = viewModel, state = state)
    }
}

private fun Intent.taskIdFromDeepLink(): String? = AppDeepLinks.taskIdFrom(data)
