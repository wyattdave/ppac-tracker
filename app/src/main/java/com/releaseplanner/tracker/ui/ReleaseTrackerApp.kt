package com.releaseplanner.tracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.releaseplanner.tracker.R
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
    "Complete",
    "Skipped",
    "Saved",
    "In Early Access",
    "In Public Preview",
    "In GA",
    "AI",
    "Shipped",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseTrackerApp(viewModel: ReleaseTrackerViewModel, state: ReleaseTrackerUiState) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PPAC Tracker", style = MaterialTheme.typography.titleMedium)
                        Text(state.lastSyncLabel, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(36.dp),
                        shape = CircleShape,
                        color = Color(0xFF0F4FD6),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground_wireframe),
                            contentDescription = "PPAC Tracker",
                            modifier = Modifier.padding(5.dp),
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
                        onClick = { viewModel.selectScreen(screen) },
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
                when (state.screen) {
                    AppScreen.Today -> TodayScreen(state, viewModel)
                    AppScreen.Updates -> UpdatesScreen(state, viewModel)
                    AppScreen.Timeline -> TimelineScreen(state, viewModel)
                    AppScreen.Tracked -> TrackedScreen(state, viewModel)
                    AppScreen.Settings -> SettingsScreen(state, viewModel)
                }
            }
        }
    }

    state.selectedUpdate?.let { item ->
        UpdateDetailDialog(
            item = item,
            onDismiss = { viewModel.selectUpdate(null) },
            onToggleComplete = { viewModel.toggleComplete(item.update.id) },
            onToggleSkipped = { viewModel.toggleSkipped(item.update.id) },
            onToggleSaved = { viewModel.toggleSaved(item.update.id) },
            onHide = {
                viewModel.toggleHidden(item.update.id)
                viewModel.selectUpdate(null)
            },
            onShare = { shareUpdate(context, item) },
            onOpenDocs = {
                item.update.bestDocsUrl().takeIf { it.isNotBlank() }?.let(uriHandler::openUri)
            },
        )
    }
}

@Composable
private fun TodayScreen(state: ReleaseTrackerUiState, viewModel: ReleaseTrackerViewModel) {
    var showAllBadges by remember { mutableStateOf(false) }
    val today = state.todayUpdates
        .sortedWith(compareBy<ReleaseUpdateUi> { it.isComplete || it.isSkipped }.thenBy { it.update.featureName.lowercase() })

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Today", state.todayCount.toString(), Modifier.weight(1f))
                MetricCard("Done", state.todayCompleteCount.toString(), Modifier.weight(1f))
                MetricCard("Open", state.todayOpenCount.toString(), Modifier.weight(1f))
            }
        }
        if (state.rewardProgress.isNotEmpty()) {
            item { RewardProgressSection(state.rewardProgress) }
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
            item { CompletedBadgesSection(state.rewardBadges, onSeeAll = { showAllBadges = true }) }
        }
    }

    if (showAllBadges) {
        RewardBadgesDialog(
            badges = state.rewardBadges,
            onDismiss = { showAllBadges = false },
        )
    }
}

@Composable
private fun UpdatesScreen(state: ReleaseTrackerUiState, viewModel: ReleaseTrackerViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search updates") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProductFilterRow(state.products, state.selectedProduct, viewModel::selectProduct)
            Spacer(modifier = Modifier.height(8.dp))
            StatusFilterRow(state.statusFilter, viewModel::setStatusFilter)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.filteredUpdates.isEmpty()) {
                item { EmptyState("No updates match these filters.") }
            } else {
                items(state.filteredUpdates, key = { it.update.id }) { item ->
                    ReleaseUpdateRow(item = item, onClick = { viewModel.selectUpdate(item.update.id) })
                }
            }
        }
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
private fun SettingsScreen(state: ReleaseTrackerUiState, viewModel: ReleaseTrackerViewModel) {
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
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                Text("The app checks for updates in the background and uses local Android notifications. Product settings and streak progress are included in Android backup and device transfer.")
            }
        }
        OutlinedButton(onClick = viewModel::clearBadges) {
            Text("Clear new and changed badges")
        }
    }
}

@Composable
private fun CompletedBadgesSection(badges: List<RewardBadgeUi>, onSeeAll: () -> Unit) {
    val completedBadges = badges.filter { it.isComplete }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Badges")
        if (completedBadges.isEmpty()) {
            EmptyState("No badges earned yet.")
        } else {
            CompletedBadgeCarousel(completedBadges)
        }
        Button(onClick = onSeeAll, modifier = Modifier.fillMaxWidth()) {
            Text("See all")
        }
    }
}

@Composable
private fun CompletedBadgeCarousel(badges: List<RewardBadgeUi>) {
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
                    modifier = Modifier.width(badgeWidth),
                )
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: RewardBadgeUi, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.aspectRatio(1f)) {
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
private fun RewardProgressSection(progress: List<RewardProgressUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Rewards")
        RewardProgressCard(progress)
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
private fun RewardBadgesDialog(badges: List<RewardBadgeUi>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Badges", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(badges, key = { it.name }) { badge ->
                        BadgeListRow(badge)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun BadgeListRow(badge: RewardBadgeUi) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                if (item.update.isNew) StatusPill("New")
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
private fun UpdateDetailDialog(
    item: ReleaseUpdateUi,
    onDismiss: () -> Unit,
    onToggleComplete: () -> Unit,
    onToggleSkipped: () -> Unit,
    onToggleSaved: () -> Unit,
    onHide: () -> Unit,
    onShare: () -> Unit,
    onOpenDocs: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(item.update.product, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(item.update.featureName, style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(item.update.statusLabel())
                    if (item.update.aiContribution) StatusPill("AI")
                    if (item.isComplete) StatusPill("Complete")
                    if (item.isSkipped) StatusPill("Skipped")
                }
                DetailLine("Area", item.update.productArea)
                DetailLine("Date", item.update.primaryDateLabel())
                DetailLine("Enabled for", item.update.enabledFor)
                DetailLine("Wave", item.update.gaReleaseWaveName.ifBlank { item.update.releaseWaveName })
                SectionTitle("Business value")
                Text(item.update.businessValue.htmlToText().ifBlank { "No summary provided." })
                SectionTitle("Details")
                Text(item.update.featureDetails.htmlToText().ifBlank { "No details provided." })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onToggleComplete, modifier = Modifier.weight(1f)) {
                        Text(if (item.isComplete) "Mark open" else "Complete")
                    }
                    OutlinedButton(onClick = onToggleSkipped, modifier = Modifier.weight(1f)) {
                        Text(if (item.isSkipped) "Mark open" else "Skip")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onToggleSaved, modifier = Modifier.weight(1f)) {
                        Text(if (item.isSaved) "Unsave" else "Save")
                    }
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onOpenDocs, modifier = Modifier.weight(1f), enabled = item.update.bestDocsUrl().isNotBlank()) { Text("Docs") }
                }
                TextButton(onClick = onHide) { Text("Hide from lists") }
                TextButton(onClick = onDismiss) { Text("Close") }
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
        TimelineAnchor("This last week", now.minusDays(7), TimelineAnchorKind.RelativeDate),
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
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
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
    val text = buildString {
        appendLine("${update.product} release update:")
        appendLine(update.featureName)
        appendLine()
        appendLine("Status: ${update.statusLabel()}")
        appendLine("Date: ${update.primaryDateLabel()}")
        update.bestDocsUrl().takeIf { it.isNotBlank() }?.let { appendLine("Docs: $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share release update"))
}

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
