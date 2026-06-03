package com.releaseplanner.tracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.releaseplanner.tracker.AppDeepLinks
import com.releaseplanner.tracker.R
import com.releaseplanner.tracker.data.NotificationSettings
import com.releaseplanner.tracker.data.ReleaseSourceSetting
import com.releaseplanner.tracker.data.ReleaseThemeMode
import com.releaseplanner.tracker.data.local.ReleaseUpdateEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val statusFilters = listOf(
    "Open",
    "All",
    "New",
    "Changed",
    "Skipped",
    "Saved",
    "AI",
    "Hidden",
)

private val releaseStageFilters = listOf(
    "All Releases",
    "All Not Released",
    "In Early Access",
    "In Public Preview",
    "In GA",
    "GA this Week",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseTrackerApp(viewModel: ReleaseTrackerViewModel, state: ReleaseTrackerUiState) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val selectedUpdate = state.selectedUpdate
    var aboutTapCount by remember { mutableStateOf(0) }
    var rewardDebugEnabled by remember { mutableStateOf(false) }
    var showAllBadges by remember { mutableStateOf(false) }
    var achievedBadge by remember { mutableStateOf<RewardBadgeUi?>(null) }
    var knownCompletedBadgeNames by remember { mutableStateOf<Set<String>?>(null) }
    var updatesFilterPanelVisible by remember { mutableStateOf(false) }
    var updatesResetToken by remember { mutableStateOf(0) }

    LaunchedEffect(state.rewardBadges, state.announcedRewardBadgeNames) {
        val completedNames = state.rewardBadges.filter { it.isComplete }.map { it.name }.toSet()
        val previousNames = knownCompletedBadgeNames
        if (previousNames == null) {
            if (state.rewardBadges.isNotEmpty()) {
                knownCompletedBadgeNames = completedNames
                viewModel.markRewardBadgesAnnounced(completedNames)
            }
        } else {
            val newNames = completedNames - previousNames
            val unannouncedNewNames = newNames - state.announcedRewardBadgeNames
            if (unannouncedNewNames.isNotEmpty()) {
                achievedBadge = state.rewardBadges.firstOrNull { it.name in unannouncedNewNames }
                viewModel.markRewardBadgesAnnounced(unannouncedNewNames)
            }
            knownCompletedBadgeNames = completedNames
        }
    }

    BackHandler(enabled = achievedBadge != null) {
        achievedBadge = null
    }

    BackHandler(enabled = achievedBadge == null && (selectedUpdate != null || showAllBadges)) {
        if (selectedUpdate != null) {
            viewModel.selectUpdate(null)
        } else {
            showAllBadges = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (achievedBadge != null) Modifier.blur(14.dp) else Modifier,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PPAC Tracker", style = MaterialTheme.typography.titleMedium)
                            Text(state.lastSyncLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        if (selectedUpdate != null || showAllBadges) {
                            IconButton(
                                onClick = {
                                    if (selectedUpdate != null) {
                                        viewModel.selectUpdate(null)
                                    } else {
                                        showAllBadges = false
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_back),
                                    contentDescription = "Back",
                                )
                            }
                        } else if (state.screen == AppScreen.Updates) {
                            IconButton(onClick = { updatesFilterPanelVisible = !updatesFilterPanelVisible }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_filter_list),
                                    contentDescription = if (updatesFilterPanelVisible) "Hide filters" else "Show filters",
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        Surface(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.icon_512),
                                contentDescription = "PPAC Tracker",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    AppScreen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = state.screen == screen,
                            onClick = {
                                showAllBadges = false
                                if (screen == AppScreen.Updates) {
                                    updatesFilterPanelVisible = true
                                    updatesResetToken += 1
                                }
                                viewModel.selectScreen(screen)
                            },
                            icon = { Icon(painterResource(id = screen.iconResId), contentDescription = screen.title) },
                            label = { Text(screen.title) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    state.errorMessage?.let { message ->
                        ErrorBanner(message = message)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (selectedUpdate != null) {
                            UpdateDetailSection(
                                item = selectedUpdate,
                                onStatusSelected = { status -> viewModel.setTaskStatus(selectedUpdate.update.id, status) },
                                onToggleSaved = { viewModel.toggleSaved(selectedUpdate.update.id) },
                                onHide = {
                                    viewModel.toggleHidden(selectedUpdate.update.id)
                                    viewModel.selectUpdate(null)
                                },
                                onShare = { shareUpdate(context, selectedUpdate) },
                                onEmail = { emailUpdate(context, selectedUpdate) },
                                onOpenDocs = {
                                    selectedUpdate.update.bestDocsUrl().takeIf { it.isNotBlank() }?.let(uriHandler::openUri)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                            )
                        } else if (showAllBadges) {
                            RewardBadgesSection(
                                badges = state.rewardBadges,
                                rewardDebugEnabled = rewardDebugEnabled,
                                onBadgeClick = viewModel::toggleBadgeReceived,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                            )
                        } else {
                            when (state.screen) {
                                AppScreen.Today -> TodayScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    rewardDebugEnabled = rewardDebugEnabled,
                                    onSeeAllBadges = { showAllBadges = true },
                                )
                                AppScreen.Updates -> UpdatesScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    filterPanelVisible = updatesFilterPanelVisible,
                                    resetToken = updatesResetToken,
                                )
                                AppScreen.Timeline -> TimelineScreen(state, viewModel)
                                AppScreen.Tracked -> TrackedScreen(state, viewModel)
                                AppScreen.Settings -> SettingsScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    rewardDebugEnabled = rewardDebugEnabled,
                                    onAboutTapped = {
                                        aboutTapCount += 1
                                        if (aboutTapCount >= 20) rewardDebugEnabled = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        achievedBadge?.let { badge ->
            BadgeAchievementOverlay(
                badge = badge,
                onDismiss = { achievedBadge = null },
            )
        }
    }
}

@Composable
private fun TodayScreen(
    state: ReleaseTrackerUiState,
    viewModel: ReleaseTrackerViewModel,
    rewardDebugEnabled: Boolean,
    onSeeAllBadges: () -> Unit,
) {
    val today = state.todayUpdates
        .sortedWith(compareBy<ReleaseUpdateUi> { it.isComplete || it.isSkipped }.thenBy { it.update.featureName.lowercase() })

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.rewardProgress.isNotEmpty()) {
            item { RewardProgressSection(state) }
        }
        item { SectionTitle("Today's updates") }
        if (today.isEmpty()) {
            item { EmptyState("No release updates are scheduled for today.") }
        } else {
            items(today, key = { it.update.id }) { item ->
                ReleaseUpdateRow(item = item, onClick = { viewModel.selectUpdate(item.update.id) })
            }
        }
        if (state.rewardBadges.isNotEmpty()) {
            item {
                CompletedBadgesSection(
                    badges = state.rewardBadges,
                    rewardDebugEnabled = rewardDebugEnabled,
                    onSeeAll = onSeeAllBadges,
                    onBadgeClick = viewModel::toggleBadgeReceived,
                )
            }
        }
    }
}

@Composable
private fun UpdatesScreen(
    state: ReleaseTrackerUiState,
    viewModel: ReleaseTrackerViewModel,
    filterPanelVisible: Boolean,
    resetToken: Int,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(resetToken) {
        listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        UpdatesOverviewAndFilters(
            state = state,
            viewModel = viewModel,
            filterPanelVisible = filterPanelVisible,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.filteredUpdates.isEmpty()) {
                item { EmptyState("No updates match these filters.") }
            } else {
                items(state.filteredUpdates, key = { it.update.id }) { item ->
                    SwipeableReleaseUpdateRow(
                        item = item,
                        onClick = { viewModel.selectUpdate(item.update.id) },
                        onComplete = { viewModel.setTaskStatus(item.update.id, TaskStatus.Complete) },
                        onSkip = { viewModel.setTaskStatus(item.update.id, TaskStatus.Skipped) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableReleaseUpdateRow(
    item: ReleaseUpdateUi,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onComplete()
                SwipeToDismissBoxValue.StartToEnd -> onSkip()
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState false
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeActionBackground(dismissState.targetValue) },
    ) {
        ReleaseUpdateRow(item = item, onClick = onClick)
    }
}

@Composable
private fun SwipeActionBackground(targetValue: SwipeToDismissBoxValue) {
    val isCompleteAction = targetValue == SwipeToDismissBoxValue.EndToStart
    val isSkipAction = targetValue == SwipeToDismissBoxValue.StartToEnd
    val containerColor = when {
        isCompleteAction -> MaterialTheme.colorScheme.primaryContainer
        isSkipAction -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = when {
        isCompleteAction -> MaterialTheme.colorScheme.onPrimaryContainer
        isSkipAction -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (isSkipAction) Alignment.CenterStart else Alignment.CenterEnd
    val label = if (isSkipAction) "Skipped" else "Complete"

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentAlignment = alignment,
        ) {
            Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun UpdatesOverviewAndFilters(
    state: ReleaseTrackerUiState,
    viewModel: ReleaseTrackerViewModel,
    filterPanelVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Total Open", state.summaryMetrics.totalOpen.toString(), Modifier.weight(1f))
            MetricCard("Total last 7 days", state.summaryMetrics.totalLastSevenDays.toString(), Modifier.weight(1f))
            MetricCard("Total GA this week", state.summaryMetrics.totalGaThisWeek.toString(), Modifier.weight(1f))
        }
        if (filterPanelVisible) {
            UpdatesFilterControls(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun UpdatesFilterControls(
    state: ReleaseTrackerUiState,
    viewModel: ReleaseTrackerViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search updates") },
        )
        ProductFilterRow(state.products, state.selectedProduct, viewModel::selectProduct)
        ReleaseStageFilterRow(state.releaseStageFilter, viewModel::setReleaseStageFilter)
        StatusFilterRow(state.statusFilter, viewModel::setStatusFilter)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineScreen(state: ReleaseTrackerUiState, viewModel: ReleaseTrackerViewModel) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    val grouped = state.timelineUpdates.groupBy { it.update.timelineMonthLabel(state.timelineSort) }
    val scrubTargets = remember(state.timelineUpdates, state.timelineSort) {
        buildTimelineScrubTargets(state.timelineUpdates, state.timelineSort)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProductFilterRow(state.products, state.selectedProduct, viewModel::selectProduct)
            TimelineSortRow(state.timelineSort, viewModel::setTimelineSort)
            TimelineDateFilterRow(
                date = state.timelineDateFilter,
                onPickDate = { showDatePicker = true },
                onClearDate = { viewModel.setTimelineDateFilter(null) },
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            TimelineScrubBar(
                targets = scrubTargets,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(104.dp),
                onSelected = { index -> scope.launch { listState.animateScrollToItem(index) } },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (month, itemsForMonth) ->
                    item(key = "section-$month") { SectionTitle(month) }
                    items(itemsForMonth, key = { it.update.id }) { item ->
                        TimelineRow(
                            item = item,
                            sort = state.timelineSort,
                            onClick = { viewModel.selectUpdate(item.update.id) },
                        )
                    }
                }
                if (grouped.isEmpty()) {
                    item { EmptyState("No timeline items match these filters.") }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.timelineDateFilter?.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTimelineDateFilter(datePickerState.selectedDateMillis?.toLocalDate())
                        showDatePicker = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TrackedScreen(state: ReleaseTrackerUiState, viewModel: ReleaseTrackerViewModel) {
    val tracked = state.visibleUpdates.filter { it.isSaved || it.isComplete || it.isSkipped }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Saved", state.savedCount.toString(), Modifier.weight(1f))
                MetricCard("Complete", state.completeCount.toString(), Modifier.weight(1f))
                MetricCard("Skipped", state.skippedCount.toString(), Modifier.weight(1f))
            }
        }
        if (tracked.isEmpty()) {
            item { EmptyState("Save or complete updates to build your local tracker.") }
        } else {
            items(tracked, key = { it.update.id }) { item ->
                ReleaseUpdateRow(item = item, onClick = { viewModel.selectUpdate(item.update.id) })
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: ReleaseTrackerUiState,
    viewModel: ReleaseTrackerViewModel,
    rewardDebugEnabled: Boolean,
    onAboutTapped: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data source", style = MaterialTheme.typography.titleMedium)
                Text("Microsoft public release planner API", style = MaterialTheme.typography.bodyMedium)
                Text("${state.visibleUpdates.size} cached updates", style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                    Text(if (state.isRefreshing) "Refreshing" else "Refresh now")
                }
            }
        }
        NotificationSettingsCard(
            settings = state.notificationSettings,
            onEnabledChange = viewModel::setNotificationEnabled,
            onTimeSelected = viewModel::setNotificationTime,
        )
        ProductSettingsCard(
            settings = state.sourceSettings,
            onToggle = viewModel::setProductEnabled,
        )
        AppearanceSettingsCard(
            themeMode = state.themeMode,
            onThemeModeSelected = viewModel::setThemeMode,
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("API diagnostic", style = MaterialTheme.typography.titleMedium)
                Text("Fetches the first public Microsoft source and shows the raw parse result.")
                Button(onClick = viewModel::runApiDiagnostic, enabled = !state.isDebugLoading) {
                    Text(if (state.isDebugLoading) "Checking" else "Run API diagnostic")
                }
                state.apiDiagnostic?.let { diagnostic ->
                    DetailLine("Source", diagnostic.sourceProduct)
                    DetailLine("HTTP status", diagnostic.statusCode.toString())
                    DetailLine("Content type", diagnostic.contentType.ifBlank { "Not provided" })
                    DetailLine("Body length", diagnostic.bodyLength.toString())
                    DetailLine("Parsed results", diagnostic.parsedCount.toString())
                    DetailLine("First feature", diagnostic.firstFeature)
                    diagnostic.errorMessage?.let { DetailLine("Parse error", it) }
                    Text("Response preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    ) {
                        Text(
                            text = diagnostic.responsePreview.ifBlank { "No response body." },
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        AboutCard(
            onTapped = onAboutTapped,
            onOpenWyattDave = { uriHandler.openUri("https://wyattdave.com") },
            onOpenPowerDevBox = { uriHandler.openUri("https://powerdevbox.com") },
            onOpenReleasePlans = { uriHandler.openUri("https://releaseplans.net/") },
        )
        if (rewardDebugEnabled) {
            RewardDebugCard(
                state = state,
                onApply = viewModel::setDebugRewardState,
            )
        }
    }
}

@Composable
private fun CompletedBadgesSection(
    badges: List<RewardBadgeUi>,
    rewardDebugEnabled: Boolean,
    onSeeAll: () -> Unit,
    onBadgeClick: (String, Boolean) -> Unit,
) {
    val completedBadges = badges.filter { it.isComplete }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Badges")
        if (completedBadges.isEmpty()) {
            EmptyState("No badges earned yet.")
        } else {
            CompletedBadgeCarousel(
                badges = completedBadges,
                rewardDebugEnabled = rewardDebugEnabled,
                onBadgeClick = onBadgeClick,
            )
        }
        Text(
            text = "See all",
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onSeeAll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CompletedBadgeCarousel(
    badges: List<RewardBadgeUi>,
    rewardDebugEnabled: Boolean,
    onBadgeClick: (String, Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val badgeWidth = (maxWidth - 20.dp) / 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            badges.forEach { badge ->
                BadgeTile(
                    badge = badge,
                    rewardDebugEnabled = rewardDebugEnabled,
                    onBadgeClick = onBadgeClick,
                    modifier = Modifier.width(badgeWidth),
                )
            }
        }
    }
}

@Composable
private fun BadgeTile(
    badge: RewardBadgeUi,
    rewardDebugEnabled: Boolean,
    onBadgeClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (rewardDebugEnabled) Modifier.clickable { onBadgeClick(badge.name, badge.isComplete) } else Modifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BadgeImage(
                badge = badge,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RewardProgressSection(state: ReleaseTrackerUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Rewards")
        RewardProgressCard(state.rewardProgress)
        LongestRewardStats(state.longestReadStreakDays, state.longestCompleteStreakWeeks)
    }
}

@Composable
private fun LongestRewardStats(longestReadStreakDays: Int, longestCompleteStreakWeeks: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard("Longest streak", "$longestReadStreakDays days", Modifier.weight(1f))
        MetricCard("Longest complete", "$longestCompleteStreakWeeks weeks", Modifier.weight(1f))
    }
}

@Composable
private fun RewardProgressCard(progress: List<RewardProgressUi>) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF0F766E),
        Color(0xFFB45309),
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(226.dp),
                contentAlignment = Alignment.Center,
            ) {
                val circleSize = minOf(maxWidth, 214.dp)
                progress.take(3).forEachIndexed { index, reward ->
                    CircularProgressIndicator(
                        progress = { reward.fraction },
                        modifier = Modifier.size(circleSize * (1f - index * 0.22f)),
                        color = colors[index % colors.size],
                        strokeWidth = (10 - index * 2).dp,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Streaks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = progress.firstOrNull()?.progressLabel ?: "No progress yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            progress.forEachIndexed { index, reward ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = CircleShape,
                        color = colors[index % colors.size],
                        content = {},
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(reward.category, style = MaterialTheme.typography.labelMedium)
                        Text(reward.rewardName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(reward.progressLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RewardBadgesSection(
    badges: List<RewardBadgeUi>,
    rewardDebugEnabled: Boolean,
    onBadgeClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Badges", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(badges, key = { it.name }) { badge ->
                    BadgeListRow(
                        badge = badge,
                        rewardDebugEnabled = rewardDebugEnabled,
                        onBadgeClick = onBadgeClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeListRow(
    badge: RewardBadgeUi,
    rewardDebugEnabled: Boolean,
    onBadgeClick: (String, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rewardDebugEnabled) Modifier.clickable { onBadgeClick(badge.name, badge.isComplete) } else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (badge.isComplete) 0.6f else 0.32f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (badge.isComplete) 1f else 0.38f)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BadgeImage(
                badge = badge,
                modifier = Modifier.size(58.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(badge.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AboutCard(
    onTapped: () -> Unit,
    onOpenWyattDave: () -> Unit,
    onOpenPowerDevBox: () -> Unit,
    onOpenReleasePlans: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTapped),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("Created by David Wyatt, Published by Power DevBox. For more information visit:")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinkListItem("https://wyattdave.com", onOpenWyattDave)
                LinkListItem("https://powerdevbox.com", onOpenPowerDevBox)
            }
            Text("For a more detailed view on your laptop we recommend checking out:")
            LinkListItem("https://releaseplans.net/", onOpenReleasePlans)
        }
    }
}

@Composable
private fun LinkListItem(text: String, onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text("-", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            modifier = Modifier.clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RewardDebugCard(
    state: ReleaseTrackerUiState,
    onApply: (Int, Int, Int) -> Unit,
) {
    var readStreakDays by remember { mutableStateOf(state.readStreakDays.toString()) }
    var completeStreakWeeks by remember { mutableStateOf(state.completeStreakWeeks.toString()) }
    var completedTaskCount by remember { mutableStateOf(state.rewardCompletedTaskCount.toString()) }

    LaunchedEffect(state.readStreakDays, state.completeStreakWeeks, state.rewardCompletedTaskCount) {
        readStreakDays = state.readStreakDays.toString()
        completeStreakWeeks = state.completeStreakWeeks.toString()
        completedTaskCount = state.rewardCompletedTaskCount.toString()
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Debug rewards", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = readStreakDays,
                onValueChange = { readStreakDays = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Days read streak") },
            )
            OutlinedTextField(
                value = completeStreakWeeks,
                onValueChange = { completeStreakWeeks = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Weeks complete streak") },
            )
            OutlinedTextField(
                value = completedTaskCount,
                onValueChange = { completedTaskCount = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Total complete") },
            )
            Button(
                onClick = {
                    onApply(
                        readStreakDays.toIntOrNull() ?: 0,
                        completeStreakWeeks.toIntOrNull() ?: 0,
                        completedTaskCount.toIntOrNull() ?: 0,
                    )
                },
            ) {
                Text("Apply debug values")
            }
            Text(
                text = "Badge tapping is enabled until the app restarts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BadgeImage(badge: RewardBadgeUi, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageBitmap = remember(badge.assetName) {
        runCatching {
            context.assets.open(badge.assetName).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }

    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = badge.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = badge.name.firstOrNull()?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun BadgeAchievementOverlay(
    badge: RewardBadgeUi,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BadgeImage(
                    badge = badge,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )
                Text(
                    text = "You achieved ${badge.name} for ${badge.description}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NotificationSettingsCard(
    settings: NotificationSettings,
    onEnabledChange: (Boolean) -> Unit,
    onTimeSelected: (Int, Int) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Daily summary", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (settings.enabled) "Runs at ${settings.timeLabel}" else "Turned off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.enabled, onCheckedChange = onEnabledChange)
            }
            if (settings.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NumberDropdown(
                        label = "Hour",
                        selected = settings.hour,
                        values = (0..23).toList(),
                        onSelected = { hour -> onTimeSelected(hour, settings.minute) },
                        modifier = Modifier.weight(1f),
                    )
                    NumberDropdown(
                        label = "Minute",
                        selected = settings.minute,
                        values = (0..59).toList(),
                        onSelected = { minute -> onTimeSelected(settings.hour, minute) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = "Product settings and streak progress are included in Android backup and device transfer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NumberDropdown(
    label: String,
    selected: Int,
    values: List<Int>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text(
                text = "$label: ${selected.toPaddedTimeSegment()}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.toPaddedTimeSegment()) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun Int.toPaddedTimeSegment(): String = toString().padStart(2, '0')

@Composable
private fun ProductSettingsCard(
    settings: List<ReleaseSourceSetting>,
    onToggle: (String, Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Products", style = MaterialTheme.typography.titleMedium)
            settings.forEach { setting ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(setting.product, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = setting.type.ifBlank { "No type" } + if (setting.defaultEnabled) " default on" else " default off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = setting.enabled,
                        onCheckedChange = { enabled -> onToggle(setting.product, enabled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsCard(
    themeMode: ReleaseThemeMode,
    onThemeModeSelected: (ReleaseThemeMode) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReleaseThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeModeSelected(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseUpdateRow(item: ReleaseUpdateUi, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.padding(top = 5.dp)) {
                    ProductDot(item.update.product)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.update.product,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.update.primaryDateLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            Text(
                text = item.update.featureName,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.update.productArea.ifBlank { item.update.featureType.ifBlank { item.update.statusLabel() } },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(item.update.statusLabel())
                if (item.update.isReleasePlannerNew()) StatusPill("New")
                if (item.update.isChanged) StatusPill("Changed")
                if (item.isComplete) StatusPill("Complete")
                if (item.isSkipped) StatusPill("Skipped")
                if (item.isSaved) StatusPill("Saved")
            }
        }
    }
}

@Composable
private fun TimelineRow(item: ReleaseUpdateUi, sort: TimelineSortOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ProductDot(item.update.product)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.update.timelineDateLabel(sort), style = MaterialTheme.typography.labelMedium)
            Text(item.update.featureName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.update.product, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun UpdateDetailSection(
    item: ReleaseUpdateUi,
    onStatusSelected: (TaskStatus) -> Unit,
    onToggleSaved: () -> Unit,
    onHide: () -> Unit,
    onShare: () -> Unit,
    onEmail: () -> Unit,
    onOpenDocs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.update.product, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(item.update.featureName, style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(item.update.statusLabel())
                if (item.update.aiContribution) StatusPill("AI")
                if (item.isComplete) StatusPill("Complete")
                if (item.isSkipped) StatusPill("Skipped")
            }
            DetailLine("Area", item.update.productArea)
            DetailLine("Date", item.update.primaryDateLabel())
            DetailLine("EarlyAccessDate", item.update.earlyAccessDateLabel())
            DetailLine("PublicPreviewDate", item.update.publicPreviewDateLabel())
            DetailLine("GADate", item.update.gaDateLabel())
            DetailLine("Enabled for", item.update.enabledFor)
            DetailLine("Wave", item.update.gaReleaseWaveName.ifBlank { item.update.releaseWaveName })
            SectionTitle("Business value")
            Text(item.update.businessValue.htmlToText().ifBlank { "No summary provided." })
            SectionTitle("Details")
            Text(item.update.featureDetails.htmlToText().ifBlank { "No details provided." })
            TaskStatusDropdown(selected = item.taskStatus, onSelected = onStatusSelected)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onToggleSaved, modifier = Modifier.weight(1f)) {
                    Text(if (item.isSaved) "Unsave" else "Save")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onOpenDocs, modifier = Modifier.weight(1f), enabled = item.update.bestDocsUrl().isNotBlank()) { Text("Docs") }
                OutlinedButton(onClick = onEmail, modifier = Modifier.weight(1f)) { Text("Email") }
            }
            TextButton(onClick = onHide) { Text("Hide from lists") }
        }
    }
}

@Composable
private fun TaskStatusDropdown(selected: TaskStatus, onSelected: (TaskStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Status: ${selected.label}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Text("Select")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            TaskStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.label) },
                    onClick = {
                        onSelected(status)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TimelineSortRow(selected: TimelineSortOption, onSelected: (TimelineSortOption) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineSortOption.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun TimelineDateFilterRow(date: LocalDate?, onPickDate: () -> Unit, onClearDate: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPickDate) {
            Text(date?.let { "From ${it.toDisplayDate()}" } ?: "Pick date")
        }
        if (date != null) {
            TextButton(onClick = onClearDate) { Text("Clear") }
        }
    }
}

@Composable
private fun TimelineScrubBar(
    targets: List<TimelineScrubTarget>,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            targets.forEach { target ->
                TextButton(
                    onClick = { onSelected(target.itemIndex) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(target.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

private fun buildTimelineScrubTargets(items: List<ReleaseUpdateUi>, sort: TimelineSortOption): List<TimelineScrubTarget> {
    if (items.isEmpty()) return emptyList()
    val now = LocalDate.now()
    val positions = buildTimelinePositions(items, sort)
    if (positions.itemPositions.isEmpty()) return emptyList()

    val relativeAnchors = listOf(
        TimelineAnchor("Last week", now.minusDays(7), TimelineAnchorKind.RelativeDate),
        TimelineAnchor("2 weeks ago", now.minusDays(14), TimelineAnchorKind.RelativeDate),
        TimelineAnchor("3 weeks ago", now.minusDays(21), TimelineAnchorKind.RelativeDate),
        TimelineAnchor("Month ago", now.minusDays(28), TimelineAnchorKind.RelativeDate),
    )
    val oldestAnchorDate = now.minusDays(364)
    val monthAnchors = generateSequence(now.minusDays(28).withDayOfMonth(1)) { it.minusMonths(1) }
        .takeWhile { !it.isBefore(oldestAnchorDate) }
        .map { date ->
            val month = date.month.getDisplayName(TextStyle.SHORT, Locale.US)
            val label = if (date.year == now.year) month else "$month ${date.year}"
            TimelineAnchor(label, date, TimelineAnchorKind.MonthStart)
        }
        .toList()

    val anchors = relativeAnchors + monthAnchors
    return anchors.mapNotNull { anchor ->
        val itemIndex = when (anchor.kind) {
            TimelineAnchorKind.RelativeDate -> positions.firstItemIndexOnOrBefore(anchor.date)
            TimelineAnchorKind.MonthStart -> positions.monthStartIndices[anchor.date]
                ?: positions.firstItemIndexOnOrBefore(anchor.date)
        } ?: return@mapNotNull null

        TimelineScrubTarget(anchor.label, itemIndex)
    }.distinctBy { it.itemIndex }
}

private fun buildTimelinePositions(items: List<ReleaseUpdateUi>, sort: TimelineSortOption): TimelinePositions {
    var index = 0
    val itemPositions = mutableListOf<TimelineItemPosition>()
    val monthStartIndices = mutableMapOf<LocalDate, Int>()

    items.groupBy { it.update.timelineMonthLabel(sort) }.forEach { (_, monthItems) ->
        monthItems.firstOrNull()?.update?.timelineDate(sort)?.withDayOfMonth(1)?.let { monthStart ->
            monthStartIndices[monthStart] = index
        }
        index += 1
        monthItems.forEach { item ->
            item.update.timelineDate(sort)?.let { date ->
                itemPositions += TimelineItemPosition(date = date, itemIndex = index)
            }
            index += 1
        }
    }

    return TimelinePositions(
        itemPositions = itemPositions.sortedByDescending { it.date },
        monthStartIndices = monthStartIndices,
    )
}

private fun TimelinePositions.firstItemIndexOnOrBefore(date: LocalDate): Int? {
    return itemPositions.firstOrNull { !it.date.isAfter(date) }?.itemIndex
        ?: itemPositions.lastOrNull()?.itemIndex
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private enum class TimelineAnchorKind { RelativeDate, MonthStart }

private data class TimelineAnchor(val label: String, val date: LocalDate, val kind: TimelineAnchorKind)

private data class TimelinePositions(
    val itemPositions: List<TimelineItemPosition>,
    val monthStartIndices: Map<LocalDate, Int>,
)

private data class TimelineItemPosition(val date: LocalDate, val itemIndex: Int)

private data class TimelineScrubTarget(val label: String, val itemIndex: Int)

@Composable
private fun ProductFilterRow(products: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("All").plus(products).forEach { product ->
            FilterChip(
                selected = selected == product,
                onClick = { onSelected(product) },
                label = { Text(product) },
            )
        }
    }
}

@Composable
private fun StatusFilterRow(selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        statusFilters.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelected(status) },
                label = { Text(status) },
            )
        }
    }
}

@Composable
private fun ReleaseStageFilterRow(selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        releaseStageFilters.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(filter) },
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.height(96.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    AssistChip(onClick = {}, label = { Text(text, maxLines = 1) })
}

@Composable
private fun ProductDot(product: String) {
    Surface(
        modifier = Modifier.size(10.dp),
        shape = CircleShape,
        color = productColor(product),
        border = BorderStroke(1.dp, Color.White),
        content = {},
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

private fun ReleaseUpdateEntity.bestDocsUrl(): String {
    if (docsUrl.isNotBlank()) return docsUrl
    if (docUrl.startsWith("http")) return docUrl
    if (docUrl.isNotBlank()) return "https://learn.microsoft.com$docUrl"
    return ""
}

private fun shareUpdate(context: Context, item: ReleaseUpdateUi) {
    val update = item.update
    val docsLink = update.bestDocsUrl().takeIf { it.isNotBlank() }
    val subject = "${update.product}: ${update.featureName}"
    val text = buildString {
        appendLine("${update.product} release update:")
        appendLine(update.featureName)
        appendLine()
        appendLine("Status: ${update.statusLabel()}")
        appendLine("Date: ${update.primaryDateLabel()}")
        docsLink?.let { appendLine("Docs: $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TITLE, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share release update"))
}

private fun emailUpdate(context: Context, item: ReleaseUpdateUi) {
    val update = item.update
    val appLink = AppDeepLinks.taskUri(update.id)
    val docsLink = update.bestDocsUrl().takeIf { it.isNotBlank() }
    val subject = "${update.product}: ${update.featureName}"
    val html = buildEmailHtml(update, appLink, docsLink)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
        putExtra(Intent.EXTRA_HTML_TEXT, html)
    }
    context.startActivity(Intent.createChooser(intent, "Email release update"))
}

private fun buildEmailHtml(update: ReleaseUpdateEntity, appLink: String, docsLink: String?): String {
    return buildString {
        append("<p><strong>")
        append("${update.product} release update:".htmlEscaped())
        append("</strong><br>")
        append(update.featureName.htmlEscaped())
        append("</p>")
        append("<p>Status: ")
        append(update.statusLabel().htmlEscaped())
        append("<br>Date: ")
        append(update.primaryDateLabel().htmlEscaped())
        append("</p>")
        append("<p><a href=\"")
        append(appLink.htmlEscaped())
        append("\">Open in PPAC Tracker</a></p>")
        docsLink?.let { link ->
            append("<p>Docs: <a href=\"")
            append(link.htmlEscaped())
            append("\">")
            append(link.htmlEscaped())
            append("</a></p>")
        }
    }
}

private fun String.htmlEscaped(): String = Html.escapeHtml(this)

private fun productColor(product: String): Color {
    return when {
        product.contains("Power Apps", ignoreCase = true) -> Color(0xFF742774)
        product.contains("Power Automate", ignoreCase = true) -> Color(0xFF0066FF)
        product.contains("Copilot", ignoreCase = true) -> Color(0xFF0F766E)
        product.contains("Dataverse", ignoreCase = true) -> Color(0xFF9333EA)
        product.contains("Power Pages", ignoreCase = true) -> Color(0xFFB45309)
        product.contains("AI", ignoreCase = true) -> Color(0xFFDC2626)
        else -> Color(0xFF475569)
    }
}
