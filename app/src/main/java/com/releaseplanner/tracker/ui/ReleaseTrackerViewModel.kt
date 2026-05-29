package com.releaseplanner.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.releaseplanner.tracker.data.ReleaseConfigLoader
import com.releaseplanner.tracker.data.ReleasePreferences
import com.releaseplanner.tracker.data.ReleaseRepository
import com.releaseplanner.tracker.data.ReleaseThemeMode
import com.releaseplanner.tracker.data.RewardDefinition
import com.releaseplanner.tracker.data.RewardPerformance
import com.releaseplanner.tracker.data.local.ChangeEventEntity
import com.releaseplanner.tracker.data.local.ReleasePlannerDatabase
import com.releaseplanner.tracker.data.local.ReleaseUpdateEntity
import com.releaseplanner.tracker.data.local.UserTrackingEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReleaseTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val sources = ReleaseConfigLoader.loadSources(application)
    private val rewards = ReleaseConfigLoader.loadRewards(application)
    private val preferences = ReleasePreferences(application, sources)
    private val repository = ReleaseRepository(
        database = ReleasePlannerDatabase.get(application),
        sources = sources,
        preferences = preferences,
    )
    private val selectedProduct = MutableStateFlow("All")
    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow("Open")
    private val timelineSort = MutableStateFlow(TimelineSortOption.LastUpdate)
    private val timelineDateFilter = MutableStateFlow<LocalDate?>(null)
    private val currentScreen = MutableStateFlow(AppScreen.Today)
    private val selectedUpdateId = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val isDebugLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val apiDiagnostic = MutableStateFlow<com.releaseplanner.tracker.data.ReleaseApiDiagnostic?>(null)

    private val filterControls = combine(
        selectedProduct,
        searchQuery,
        statusFilter,
    ) { product, query, status ->
        Triple(product, query, status)
    }

    private val timelineControls = combine(
        timelineSort,
        timelineDateFilter,
    ) { sort, dateFilter ->
        sort to dateFilter
    }

    private val navigationControls = combine(
        currentScreen,
        selectedUpdateId,
    ) { screen, selectedId ->
        screen to selectedId
    }

    private val controls = combine(
        filterControls,
        timelineControls,
        navigationControls,
    ) { filters, timeline, navigation ->
        ReleaseTrackerControls(
            selectedProduct = filters.first,
            searchQuery = filters.second,
            statusFilter = filters.third,
            timelineSort = timeline.first,
            timelineDateFilter = timeline.second,
            screen = navigation.first,
            selectedUpdateId = navigation.second,
        )
    }

    private val meta = combine(
        isRefreshing,
        isDebugLoading,
        errorMessage,
        apiDiagnostic,
    ) { refreshing, debugLoading, error, diagnostic ->
        ReleaseTrackerMeta(refreshing, debugLoading, error, diagnostic)
    }

    private val dataState = combine(
        repository.updates,
        repository.tracking,
        repository.recentEvents,
    ) { updates, tracking, events ->
        ReleaseTrackerData(updates, tracking, events)
    }

    private val preferenceState = combine(
        preferences.sourceSettings,
        preferences.rewardPerformance,
        preferences.themeMode,
    ) { sourceSettings, rewardPerformance, themeMode ->
        ReleaseTrackerPreferenceState(sourceSettings, rewardPerformance, themeMode)
    }

    val uiState = combine(
        dataState,
        controls,
        meta,
        preferenceState,
    ) { data, controls, meta, preferenceState ->
        val enabledSources = preferenceState.sourceSettings.filter { it.enabled }.map { it.product }.toSet()
        val trackingByRelease = data.tracking.associateBy { it.releaseId }
        val items = data.updates.map { update ->
            ReleaseUpdateUi(
                update = update,
                tracking = trackingByRelease[update.id] ?: UserTrackingEntity(releaseId = update.id),
            )
        }.filter { it.update.sourceProduct in enabledSources }
        val products = items.map { it.update.product }.filter { it.isNotBlank() }.distinct().sorted()
        val visible = items.filterNot { it.isHidden }
        val filtered = visible
            .filter { controls.selectedProduct == "All" || it.update.product == controls.selectedProduct }
            .filter { it.matchesSearch(controls.searchQuery) }
            .filter { it.matchesStatus(controls.statusFilter) }
            .sortedWith(defaultUpdateComparator())
        val timeline = visible
            .filter { controls.selectedProduct == "All" || it.update.product == controls.selectedProduct }
            .filter { item ->
                controls.timelineDateFilter?.let { filterDate ->
                    item.update.timelineDate(controls.timelineSort)?.let { !it.isBefore(filterDate) } == true
                } ?: true
            }
            .sortedWith(timelineComparator(controls.timelineSort))
        val selected = items.firstOrNull { it.update.id == controls.selectedUpdateId }
        val lastSeenAt = data.updates.maxOfOrNull { it.lastSeenAt }

        ReleaseTrackerUiState(
            screen = controls.screen,
            updates = items,
            filteredUpdates = filtered,
            timelineUpdates = timeline,
            products = products,
            selectedProduct = controls.selectedProduct,
            searchQuery = controls.searchQuery,
            statusFilter = controls.statusFilter,
            timelineSort = controls.timelineSort,
            timelineDateFilter = controls.timelineDateFilter,
            selectedUpdate = selected,
            recentEvents = data.events,
            sourceSettings = preferenceState.sourceSettings,
            themeMode = preferenceState.themeMode,
            rewardProgress = rewards.toRewardProgress(preferenceState.rewardPerformance, items.count { it.isComplete }),
            rewardBadges = rewards.toRewardBadges(preferenceState.rewardPerformance, items.count { it.isComplete }),
            isRefreshing = meta.isRefreshing,
            isDebugLoading = meta.isDebugLoading,
            errorMessage = meta.errorMessage,
            apiDiagnostic = meta.apiDiagnostic,
            lastSyncLabel = lastSeenAt?.asSyncLabel() ?: "Not synced yet",
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReleaseTrackerUiState(),
    )

    init {
        viewModelScope.launch {
            if (repository.isEmpty()) refresh()
        }
    }

    fun selectScreen(screen: AppScreen) {
        currentScreen.value = screen
    }

    fun selectProduct(product: String) {
        selectedProduct.value = product
    }

    fun setProductEnabled(product: String, enabled: Boolean) {
        preferences.setProductEnabled(product, enabled)
        if (!enabled && selectedProduct.value == product) {
            selectedProduct.value = "All"
        }
    }

    fun setThemeMode(themeMode: ReleaseThemeMode) {
        preferences.setThemeMode(themeMode)
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setStatusFilter(status: String) {
        statusFilter.value = status
    }

    fun setTimelineSort(sort: TimelineSortOption) {
        timelineSort.value = sort
    }

    fun setTimelineDateFilter(date: LocalDate?) {
        timelineDateFilter.value = date
    }

    fun selectUpdate(id: String?) {
        if (id != null) {
            preferences.recordTaskRead()
        }
        selectedUpdateId.value = id
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            errorMessage.value = null
            runCatching { repository.refresh() }
                .onSuccess { result ->
                    when {
                        result.total == 0 && result.failureMessages.isNotEmpty() -> {
                            errorMessage.value = "Sync failed: ${result.failureMessages.joinToString()}"
                        }
                        result.failedSources.isNotEmpty() -> {
                            errorMessage.value = "Partial sync: failed ${result.failedSources.joinToString()}"
                        }
                    }
                }
                .onFailure {
                    preferences.recordApiFailure()
                    errorMessage.value = it.message ?: "Refresh failed"
                }
            isRefreshing.value = false
        }
    }

    fun runApiDiagnostic() {
        viewModelScope.launch {
            isDebugLoading.value = true
            errorMessage.value = null
            runCatching { repository.debugFirstSource() }
                .onSuccess { apiDiagnostic.value = it }
                .onFailure { errorMessage.value = "API diagnostic failed: ${it.message ?: it::class.simpleName.orEmpty()}" }
            isDebugLoading.value = false
        }
    }

    fun clearBadges() {
        viewModelScope.launch { repository.clearSyncBadges() }
    }

    fun toggleComplete(id: String) {
        viewModelScope.launch {
            val isCompleting = uiState.value.updates.firstOrNull { it.update.id == id }?.isComplete == false
            repository.updateTracking(id) { tracking ->
                if (tracking.isComplete) {
                    tracking.copy(isComplete = false)
                } else {
                    tracking.copy(isComplete = true, isSkipped = false)
                }
            }
            if (isCompleting) {
                preferences.recordTaskCompleted()
            }
        }
    }

    fun toggleSkipped(id: String) {
        viewModelScope.launch {
            repository.updateTracking(id) { tracking ->
                if (tracking.isSkipped) {
                    tracking.copy(isSkipped = false)
                } else {
                    tracking.copy(isSkipped = true, isComplete = false)
                }
            }
        }
    }

    fun toggleSaved(id: String) {
        viewModelScope.launch {
            repository.updateTracking(id) { it.copy(isSaved = !it.isSaved) }
        }
    }

    fun toggleHidden(id: String) {
        viewModelScope.launch {
            repository.updateTracking(id) { it.copy(isHidden = !it.isHidden) }
        }
    }

    private fun ReleaseUpdateUi.matchesSearch(query: String): Boolean {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return true
        return listOf(
            update.featureName,
            update.product,
            update.productArea,
            update.featureType,
            update.featureCategory,
            update.docsName,
        ).any { it.lowercase().contains(normalized) }
    }

    private fun ReleaseUpdateUi.matchesStatus(status: String): Boolean {
        return when (status) {
            "All" -> true
            "Open" -> !isComplete && !isSkipped
            "New" -> update.isNew
            "Changed" -> update.isChanged
            "Saved" -> isSaved
            "Incomplete" -> !isComplete && !isSkipped
            "Complete" -> isComplete
            "Skipped" -> isSkipped
            "In Early Access" -> update.isCurrentlyInEarlyAccess()
            "In Public Preview" -> update.isCurrentlyInPublicPreview()
            "In GA" -> update.isCurrentlyInGa()
            "AI" -> update.aiContribution
            "Shipped" -> update.gaStatus.equals("Shipped", ignoreCase = true)
            else -> true
        }
    }

    private fun defaultUpdateComparator(): Comparator<ReleaseUpdateUi> {
        return compareBy<ReleaseUpdateUi> { it.isComplete || it.isSkipped }
            .thenByDescending { it.update.timelineDate(TimelineSortOption.LastUpdate) ?: LocalDate.MIN }
            .thenBy { it.update.featureName.lowercase() }
    }

    private fun timelineComparator(sort: TimelineSortOption): Comparator<ReleaseUpdateUi> {
        return compareByDescending<ReleaseUpdateUi> { it.update.timelineDate(sort) ?: LocalDate.MIN }
            .thenBy { it.update.featureName.lowercase() }
    }

    private fun ReleaseUpdateEntity.isCurrentlyInEarlyAccess(): Boolean {
        return eaStatus.isShippedStage() && !ppStatus.isShippedStage() && !gaStatus.isShippedStage()
    }

    private fun ReleaseUpdateEntity.isCurrentlyInPublicPreview(): Boolean {
        return ppStatus.isShippedStage() && !gaStatus.isShippedStage()
    }

    private fun ReleaseUpdateEntity.isCurrentlyInGa(): Boolean {
        return gaStatus.isShippedStage()
    }

    private fun String.isShippedStage(): Boolean = equals("Shipped", ignoreCase = true)

    private fun List<RewardDefinition>.toRewardProgress(
        performance: RewardPerformance,
        completedTaskCount: Int,
    ): List<RewardProgressUi> {
        val targets = mapNotNull { it.toRewardTarget() }
            .distinctBy { listOf(it.kind, it.name, it.target).joinToString("|") }

        return RewardKind.entries.mapNotNull { kind ->
            val current = when (kind) {
                RewardKind.ReadStreak -> performance.readStreakDays
                RewardKind.CompleteWeekStreak -> performance.completeStreakWeeks
                RewardKind.CompleteTasks -> completedTaskCount
            }
            val target = targets
                .filter { it.kind == kind }
                .sortedBy { it.target }
                .firstOrNull { current < it.target }
                ?: targets.filter { it.kind == kind }.maxByOrNull { it.target }

            target?.let {
                RewardProgressUi(
                    category = kind.label,
                    rewardName = it.name,
                    description = it.description,
                    current = current,
                    target = it.target,
                    unit = kind.unit,
                )
            }
        }
    }

    private fun List<RewardDefinition>.toRewardBadges(
        performance: RewardPerformance,
        completedTaskCount: Int,
    ): List<RewardBadgeUi> {
        val targets = mapNotNull { it.toRewardTarget() }
            .distinctBy { listOf(it.kind, it.name, it.target).joinToString("|") }

        return targets.map { target ->
            val current = when (target.kind) {
                RewardKind.ReadStreak -> performance.readStreakDays
                RewardKind.CompleteWeekStreak -> performance.completeStreakWeeks
                RewardKind.CompleteTasks -> completedTaskCount
            }
            RewardBadgeUi(
                category = target.kind.label,
                name = target.name,
                description = target.description,
                assetName = "badges/${target.name.toBadgeAssetSegment()}.png",
                current = current,
                target = target.target,
                unit = target.kind.unit,
                isComplete = current >= target.target,
            )
        }
    }

    private fun String.toBadgeAssetSegment(): String {
        return trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun RewardDefinition.toRewardTarget(): RewardTarget? {
        val normalizedType = type.trim().lowercase()
        val numbers = Regex("\\d+").findAll(description).map { it.value.toInt() }.toList()
        return when {
            normalizedType.contains("read task") && description.contains("consecutive days", ignoreCase = true) -> {
                RewardTarget(RewardKind.ReadStreak, name, description, numbers.lastOrNull() ?: return null)
            }
            normalizedType.contains("complete task streak") -> {
                RewardTarget(RewardKind.CompleteWeekStreak, name, description, numbers.lastOrNull() ?: return null)
            }
            normalizedType.contains("complete task") -> {
                RewardTarget(RewardKind.CompleteTasks, name, description, numbers.firstOrNull() ?: return null)
            }
            else -> null
        }
    }

    private data class RewardTarget(
        val kind: RewardKind,
        val name: String,
        val description: String,
        val target: Int,
    )

    private enum class RewardKind(val label: String, val unit: String) {
        ReadStreak("Read streak", "days"),
        CompleteWeekStreak("Weekly complete streak", "weeks"),
        CompleteTasks("Completed tasks", "tasks"),
    }

    private data class ReleaseTrackerData(
        val updates: List<ReleaseUpdateEntity>,
        val tracking: List<UserTrackingEntity>,
        val events: List<ChangeEventEntity>,
    )

    private data class ReleaseTrackerPreferenceState(
        val sourceSettings: List<com.releaseplanner.tracker.data.ReleaseSourceSetting>,
        val rewardPerformance: RewardPerformance,
        val themeMode: ReleaseThemeMode,
    )
}
