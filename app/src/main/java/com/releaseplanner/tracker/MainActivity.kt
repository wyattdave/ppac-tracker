package com.releaseplanner.tracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.releaseplanner.tracker.ui.ReleaseTrackerApp
import com.releaseplanner.tracker.ui.ReleaseTrackerViewModel
import com.releaseplanner.tracker.ui.theme.ReleasePlannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent {
            ReleasePlannerRoot()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
    }
}

@Composable
private fun ReleasePlannerRoot() {
    val viewModel: ReleaseTrackerViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReleasePlannerTheme(themeMode = state.themeMode) {
        ReleaseTrackerApp(viewModel = viewModel, state = state)
    }
}
