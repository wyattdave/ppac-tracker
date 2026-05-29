package com.releaseplanner.tracker.ui

import com.releaseplanner.tracker.data.ReleaseApiDiagnostic
import com.releaseplanner.tracker.data.ReleaseSourceSetting
import com.releaseplanner.tracker.data.ReleaseThemeMode
import com.releaseplanner.tracker.data.local.ChangeEventEntity
import com.releaseplanner.tracker.data.local.ReleaseUpdateEntity
import com.releaseplanner.tracker.data.local.UserTrackingEntity
import java.time.LocalDate

import com.releaseplanner.tracker.R

enum class AppScreen(val title: String, val iconResId: Int) {
    Today("Today", R.drawable.ic_today),
    Updates("Updates", R.drawable.ic_updates),
    Timeline("Timeline", R.drawable.ic_timeline),
    Tracked("Tracked", R.drawable.ic_tracked),
    Settings("Settings", R.drawable.ic_settings)
}

enum class TimelineSortOption(val label: String) {
    LastUpdate("Last Update"),
    EarlyAccess("Early Access"),
    PublicPreview("Public Preview"),
    GeneralAvailability("GA"),
}

data class ReleaseUpdateUi(
    val update: ReleaseUpdateEntity,
    val tracking: UserTrackingEntity,
) {
    val isComplete: Boolean get() = tracking.isComplete
    val isSkipped: Boolean get() = tracking.isSkipped
    val isSaved: Boolean get() = tracking.isSaved
    val isHidden: Boolean get() = tracking.isHidden
}

data class ReleaseTrackerUiState(
    val screen: AppScreen = AppScreen.Today,
    val updates: List<ReleaseUpdateUi> = emptyList(),
    val filteredUpdates: List<ReleaseUpdateUi> = emptyList(),
    val timelineUpdates: List<ReleaseUpdateUi> = emptyList(),
    val products: List<String> = emptyList(),
    val selectedProduct: String = "All",
    val searchQuery: String = "",
    val statusFilter: String = "Open",
    val timelineSort: TimelineSortOption = TimelineSortOption.LastUpdate,
    val timelineDateFilter: LocalDate? = null,
    val selectedUpdate: ReleaseUpdateUi? = null,
    val recentEvents: List<ChangeEventEntity> = emptyList(),
    val sourceSettings: List<ReleaseSourceSetting> = emptyList(),
    val themeMode: ReleaseThemeMode = ReleaseThemeMode.Light,
    val rewardProgress: List<RewardProgressUi> = emptyList(),
    val rewardBadges: List<RewardBadgeUi> = emptyList(),
    val isRefreshing: Boolean = false,
    val isDebugLoading: Boolean = false,
    val errorMessage: String? = null,
    val apiDiagnostic: ReleaseApiDiagnostic? = null,
    val lastSyncLabel: String = "Not synced yet",
) {
    val visibleUpdates: List<ReleaseUpdateUi> = updates.filterNot { it.isHidden }
    val todayUpdates: List<ReleaseUpdateUi> = visibleUpdates.filter { it.update.isScheduledForToday() }
    val todayCount: Int = todayUpdates.size
    val todayCompleteCount: Int = todayUpdates.count { it.isComplete }
    val todayOpenCount: Int = todayUpdates.count { !it.isComplete && !it.isSkipped }
    val newCount: Int = visibleUpdates.count { it.update.isNew }
    val changedCount: Int = visibleUpdates.count { it.update.isChanged }
    val completeCount: Int = visibleUpdates.count { it.isComplete }
    val skippedCount: Int = visibleUpdates.count { it.isSkipped }
    val savedCount: Int = visibleUpdates.count { it.isSaved }
    val incompleteCount: Int = visibleUpdates.count { !it.isComplete && !it.isSkipped }
}

data class ReleaseTrackerControls(
    val selectedProduct: String,
    val searchQuery: String,
    val statusFilter: String,
    val timelineSort: TimelineSortOption,
    val timelineDateFilter: LocalDate?,
    val screen: AppScreen,
    val selectedUpdateId: String?,
)

data class ReleaseTrackerMeta(
    val isRefreshing: Boolean,
    val isDebugLoading: Boolean,
    val errorMessage: String?,
    val apiDiagnostic: ReleaseApiDiagnostic?,
)

data class RewardProgressUi(
    val category: String,
    val rewardName: String,
    val description: String,
    val current: Int,
    val target: Int,
    val unit: String,
) {
    val fraction: Float = if (target <= 0) 0f else (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val progressLabel: String = "${current.coerceAtMost(target)} / $target $unit"
}

data class RewardBadgeUi(
    val category: String,
    val name: String,
    val description: String,
    val assetName: String,
    val current: Int,
    val target: Int,
    val unit: String,
    val isComplete: Boolean,
) {
    val progressLabel: String = "${current.coerceAtMost(target)} / $target $unit"
}
