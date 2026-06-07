package com.releaseplanner.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate

class ReleasePreferences(
    context: Context,
    private val sources: List<ReleaseSource>,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val sourceSettingsState = MutableStateFlow(readSourceSettings())
    private val rewardPerformanceState = MutableStateFlow(readRewardPerformance(syncLongest = true))
    private val themeModeState = MutableStateFlow(readThemeMode())
    private val notificationSettingsState = MutableStateFlow(readNotificationSettings())

    val sourceSettings: StateFlow<List<ReleaseSourceSetting>> = sourceSettingsState
    val rewardPerformance: StateFlow<RewardPerformance> = rewardPerformanceState
    val themeMode: StateFlow<ReleaseThemeMode> = themeModeState
    val notificationSettings: StateFlow<NotificationSettings> = notificationSettingsState

    fun enabledSources(): List<ReleaseSource> {
        return sources.filter { isProductEnabled(it) }
    }

    fun isProductEnabled(source: ReleaseSource): Boolean {
        return prefs.getBoolean(productEnabledKey(source.product), source.isPowerPlatform)
    }

    fun setProductEnabled(product: String, enabled: Boolean) {
        prefs.edit().putBoolean(productEnabledKey(product), enabled).apply()
        sourceSettingsState.value = readSourceSettings()
    }

    fun setThemeMode(themeMode: ReleaseThemeMode) {
        prefs.edit().putString(THEME_MODE_KEY, themeMode.name).apply()
        themeModeState.value = themeMode
    }

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(NOTIFICATIONS_ENABLED_KEY, enabled).apply()
        notificationSettingsState.value = readNotificationSettings()
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(NOTIFICATION_HOUR_KEY, hour.coerceIn(0, 23))
            .putInt(NOTIFICATION_MINUTE_KEY, minute.coerceIn(0, 59))
            .apply()
        notificationSettingsState.value = readNotificationSettings()
    }

    fun recordTaskRead(date: LocalDate = LocalDate.now()) {
        updateDateSet(READ_TASK_DATES_KEY, date)
        refreshRewardPerformance(date)
    }

    fun recordTaskCompleted(date: LocalDate = LocalDate.now()) {
        updateDateSet(READ_TASK_DATES_KEY, date)
        updateDateSet(COMPLETE_TASK_DATES_KEY, date)
        refreshRewardPerformance(date)
    }

    fun recordApiFailure(date: LocalDate = LocalDate.now()) {
        updateDateSet(API_FAILURE_DATES_KEY, date)
        refreshRewardPerformance(date)
    }

    fun refreshRewardPerformance(date: LocalDate = LocalDate.now()) {
        rewardPerformanceState.value = readRewardPerformance(syncLongest = true, date = date)
    }

    fun setDebugRewardState(
        readStreakDays: Int,
        readStreakStartDate: LocalDate,
        completeStreakWeeks: Int,
        completeStreakStartDate: LocalDate,
        completedTaskCount: Int,
    ) {
        prefs.edit()
            .putInt(DEBUG_READ_STREAK_DAYS_KEY, readStreakDays.coerceAtLeast(0))
            .putString(DEBUG_READ_STREAK_DATE_KEY, readStreakStartDate.toString())
            .putInt(DEBUG_COMPLETE_STREAK_WEEKS_KEY, completeStreakWeeks.coerceAtLeast(0))
            .putString(DEBUG_COMPLETE_STREAK_DATE_KEY, completeStreakStartDate.toString())
            .putInt(DEBUG_COMPLETED_TASK_COUNT_KEY, completedTaskCount.coerceAtLeast(0))
            .apply()
        rewardPerformanceState.value = readRewardPerformance(syncLongest = true)
    }

    fun toggleManualBadge(name: String, isCurrentlyComplete: Boolean) {
        val completed = prefs.getStringSet(MANUAL_COMPLETED_BADGES_KEY, emptySet()).orEmpty().toMutableSet()
        val open = prefs.getStringSet(MANUAL_OPEN_BADGES_KEY, emptySet()).orEmpty().toMutableSet()

        if (isCurrentlyComplete) {
            completed -= name
            open += name
        } else {
            open -= name
            completed += name
        }

        prefs.edit()
            .putStringSet(MANUAL_COMPLETED_BADGES_KEY, completed)
            .putStringSet(MANUAL_OPEN_BADGES_KEY, open)
            .apply()
        rewardPerformanceState.value = readRewardPerformance()
    }

    fun markRewardBadgesAnnounced(names: Set<String>) {
        if (names.isEmpty()) return

        val announced = prefs.getStringSet(ANNOUNCED_REWARD_BADGES_KEY, emptySet()).orEmpty().toMutableSet()
        if (announced.addAll(names)) {
            prefs.edit()
                .putStringSet(ANNOUNCED_REWARD_BADGES_KEY, announced)
                .apply()
            rewardPerformanceState.value = readRewardPerformance()
        }
    }

    private fun readSourceSettings(): List<ReleaseSourceSetting> {
        return sources.map { source ->
            ReleaseSourceSetting(
                product = source.product,
                type = source.type,
                version = source.version,
                enabled = isProductEnabled(source),
                defaultEnabled = source.isPowerPlatform,
            )
        }
    }

    private fun readRewardPerformance(syncLongest: Boolean = false, date: LocalDate = LocalDate.now()): RewardPerformance {
        val readDates = readDateSet(READ_TASK_DATES_KEY)
        val completeDates = readDateSet(COMPLETE_TASK_DATES_KEY)
        val apiFailureDates = readDateSet(API_FAILURE_DATES_KEY)
        val baseReadStreakDays = consecutiveDaysThroughCurrentOrPreviousDay(date, readDates, apiFailureDates)
        val baseCompleteStreakWeeks = consecutiveWeeksEndingThisOrLastWeek(date, completeDates, apiFailureDates)
        val baseReadStreak = RewardStreak(
            count = baseReadStreakDays,
            startDate = date.minusDays((baseReadStreakDays - 1).toLong()).takeIf { baseReadStreakDays > 0 },
        )
        val baseCompleteStreak = RewardStreak(
            count = baseCompleteStreakWeeks,
            startDate = date.weekStart().minusWeeks((baseCompleteStreakWeeks - 1).toLong()).takeIf { baseCompleteStreakWeeks > 0 },
        )
        val readStreak = maxStreak(debugReadStreak(date, readDates, apiFailureDates), baseReadStreak)
        val completeStreak = maxStreak(debugCompleteStreak(date, completeDates, apiFailureDates), baseCompleteStreak)
        val storedLongestReadStreakDays = prefs.getInt(LONGEST_READ_STREAK_DAYS_KEY, 0)
        val storedLongestCompleteStreakWeeks = prefs.getInt(LONGEST_COMPLETE_STREAK_WEEKS_KEY, 0)
        val storedLongestReadStartDate = readStoredDate(LONGEST_READ_STREAK_START_DATE_KEY)
        val storedLongestCompleteStartDate = readStoredDate(LONGEST_COMPLETE_STREAK_START_DATE_KEY)
        val longestReadStreak = if (readStreak.count >= storedLongestReadStreakDays) readStreak else RewardStreak(storedLongestReadStreakDays, storedLongestReadStartDate)
        val longestCompleteStreak = if (completeStreak.count >= storedLongestCompleteStreakWeeks) completeStreak else RewardStreak(storedLongestCompleteStreakWeeks, storedLongestCompleteStartDate)

        if (syncLongest) {
            prefs.edit().apply {
                putInt(LONGEST_READ_STREAK_DAYS_KEY, longestReadStreak.count)
                putInt(LONGEST_COMPLETE_STREAK_WEEKS_KEY, longestCompleteStreak.count)
                longestReadStreak.startDate?.let { putString(LONGEST_READ_STREAK_START_DATE_KEY, it.toString()) }
                longestCompleteStreak.startDate?.let { putString(LONGEST_COMPLETE_STREAK_START_DATE_KEY, it.toString()) }
            }.apply()
        }

        return RewardPerformance(
            readStreakDays = readStreak.count,
            completeStreakWeeks = completeStreak.count,
            longestReadStreakDays = longestReadStreak.count,
            longestCompleteStreakWeeks = longestCompleteStreak.count,
            readStreakStartDate = readStreak.startDate,
            completeStreakStartDate = completeStreak.startDate,
            longestReadStreakStartDate = longestReadStreak.startDate,
            longestCompleteStreakStartDate = longestCompleteStreak.startDate,
            debugReadStreakStartDate = readStoredDate(DEBUG_READ_STREAK_DATE_KEY),
            debugCompleteStreakStartDate = readStoredDate(DEBUG_COMPLETE_STREAK_DATE_KEY),
            completedTaskOverride = prefs.getOptionalInt(DEBUG_COMPLETED_TASK_COUNT_KEY),
            manuallyCompletedBadges = prefs.getStringSet(MANUAL_COMPLETED_BADGES_KEY, emptySet()).orEmpty(),
            manuallyOpenBadges = prefs.getStringSet(MANUAL_OPEN_BADGES_KEY, emptySet()).orEmpty(),
            announcedRewardBadges = prefs.getStringSet(ANNOUNCED_REWARD_BADGES_KEY, emptySet()).orEmpty(),
        )
    }

    private fun android.content.SharedPreferences.getOptionalInt(key: String): Int? {
        return if (contains(key)) getInt(key, 0) else null
    }

    private fun readThemeMode(): ReleaseThemeMode {
        val stored = prefs.getString(THEME_MODE_KEY, null)
        return ReleaseThemeMode.entries.firstOrNull { it.name == stored } ?: ReleaseThemeMode.Light
    }

    private fun readNotificationSettings(): NotificationSettings {
        return NotificationSettings(
            enabled = prefs.getBoolean(NOTIFICATIONS_ENABLED_KEY, true),
            hour = prefs.getInt(NOTIFICATION_HOUR_KEY, NotificationSettings.DEFAULT_HOUR).coerceIn(0, 23),
            minute = prefs.getInt(NOTIFICATION_MINUTE_KEY, NotificationSettings.DEFAULT_MINUTE).coerceIn(0, 59),
        )
    }

    private fun updateDateSet(key: String, date: LocalDate) {
        val values = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        values += date.toString()
        prefs.edit().putStringSet(key, values).apply()
    }

    private fun readDateSet(key: String): Set<LocalDate> {
        return prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()
        }.toSet()
    }

    private fun readStoredDate(key: String): LocalDate? {
        return prefs.getString(key, null)?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()
        }
    }

    private fun consecutiveDaysThroughCurrentOrPreviousDay(
        date: LocalDate,
        readDates: Set<LocalDate>,
        apiFailureDates: Set<LocalDate>,
    ): Int {
        return consecutiveDaysEndingOn(date, readDates, apiFailureDates)
    }

    private fun consecutiveDaysEndingOn(
        date: LocalDate,
        readDates: Set<LocalDate>,
        apiFailureDates: Set<LocalDate>,
    ): Int {
        var count = 0
        var cursor = date
        while (cursor.isSuccessfulReadDay(readDates, apiFailureDates)) {
            count += 1
            cursor = cursor.minusDays(1)
        }
        return count
    }

    private fun maxStreak(first: RewardStreak?, second: RewardStreak): RewardStreak {
        return if ((first?.count ?: 0) > second.count) first!! else second
    }

    private fun debugReadStreak(
        date: LocalDate,
        readDates: Set<LocalDate>,
        apiFailureDates: Set<LocalDate>,
    ): RewardStreak? {
        val debugCount = prefs.getOptionalInt(DEBUG_READ_STREAK_DAYS_KEY) ?: return null
        val startDate = readStoredDate(DEBUG_READ_STREAK_DATE_KEY) ?: date.minusDays((debugCount - 1).toLong())
        val endDate = startDate.plusDays((debugCount.coerceAtLeast(1) - 1).toLong())
        if (!date.isAfter(endDate)) return RewardStreak(debugCount, startDate.takeIf { debugCount > 0 })

        var count = debugCount
        var cursor = endDate.plusDays(1)
        while (!cursor.isAfter(date)) {
            if (cursor.isSuccessfulReadDay(readDates, apiFailureDates)) {
                count += 1
            } else {
                return null
            }
            cursor = cursor.plusDays(1)
        }
        return RewardStreak(count, startDate.takeIf { count > 0 })
    }

    private fun consecutiveWeeksEndingThisOrLastWeek(
        date: LocalDate,
        completeDates: Set<LocalDate>,
        apiFailureDates: Set<LocalDate>,
    ): Int {
        val completedWeeks = (completeDates + apiFailureDates).map { it.weekStart() }.toSet()
        var cursor = date.weekStart()
        if (!completedWeeks.contains(cursor)) return 0

        var count = 0
        while (completedWeeks.contains(cursor)) {
            count += 1
            cursor = cursor.minusWeeks(1)
        }
        return count
    }

    private fun debugCompleteStreak(
        date: LocalDate,
        completeDates: Set<LocalDate>,
        apiFailureDates: Set<LocalDate>,
    ): RewardStreak? {
        val debugCount = prefs.getOptionalInt(DEBUG_COMPLETE_STREAK_WEEKS_KEY) ?: return null
        val startDate = readStoredDate(DEBUG_COMPLETE_STREAK_DATE_KEY) ?: date.weekStart().minusWeeks((debugCount - 1).toLong())
        val startWeek = startDate.weekStart()
        val debugWeek = startWeek.plusWeeks((debugCount.coerceAtLeast(1) - 1).toLong())
        val currentWeek = date.weekStart()
        if (!currentWeek.isAfter(debugWeek)) return RewardStreak(debugCount, startDate.takeIf { debugCount > 0 })

        val completedWeeks = (completeDates + apiFailureDates).map { it.weekStart() }.toSet()
        var count = debugCount
        var cursor = debugWeek.plusWeeks(1)
        while (!cursor.isAfter(currentWeek)) {
            if (completedWeeks.contains(cursor)) {
                count += 1
            } else {
                return null
            }
            cursor = cursor.plusWeeks(1)
        }
        return RewardStreak(count, startDate.takeIf { count > 0 })
    }

    private fun LocalDate.weekStart(): LocalDate {
        return minusDays((dayOfWeek.value - 1).toLong())
    }

    private fun LocalDate.isSuccessfulReadDay(readDates: Set<LocalDate>, apiFailureDates: Set<LocalDate>): Boolean {
        return readDates.contains(this) || apiFailureDates.contains(this) || isWeekend()
    }

    private fun LocalDate.isWeekend(): Boolean {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
    }

    private fun productEnabledKey(product: String): String {
        val safeProduct = product.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return "product_enabled_$safeProduct"
    }

    private companion object {
        const val PREFS_NAME = "release_tracker_preferences"
        const val READ_TASK_DATES_KEY = "read_task_dates"
        const val COMPLETE_TASK_DATES_KEY = "complete_task_dates"
        const val API_FAILURE_DATES_KEY = "api_failure_dates"
        const val THEME_MODE_KEY = "theme_mode"
        const val NOTIFICATIONS_ENABLED_KEY = "notifications_enabled"
        const val NOTIFICATION_HOUR_KEY = "notification_hour"
        const val NOTIFICATION_MINUTE_KEY = "notification_minute"
        const val LONGEST_READ_STREAK_DAYS_KEY = "longest_read_streak_days"
        const val LONGEST_READ_STREAK_START_DATE_KEY = "longest_read_streak_start_date"
        const val LONGEST_COMPLETE_STREAK_WEEKS_KEY = "longest_complete_streak_weeks"
        const val LONGEST_COMPLETE_STREAK_START_DATE_KEY = "longest_complete_streak_start_date"
        const val DEBUG_READ_STREAK_DAYS_KEY = "debug_read_streak_days"
        const val DEBUG_READ_STREAK_DATE_KEY = "debug_read_streak_date"
        const val DEBUG_COMPLETE_STREAK_WEEKS_KEY = "debug_complete_streak_weeks"
        const val DEBUG_COMPLETE_STREAK_DATE_KEY = "debug_complete_streak_date"
        const val DEBUG_COMPLETED_TASK_COUNT_KEY = "debug_completed_task_count"
        const val MANUAL_COMPLETED_BADGES_KEY = "manual_completed_badges"
        const val MANUAL_OPEN_BADGES_KEY = "manual_open_badges"
        const val ANNOUNCED_REWARD_BADGES_KEY = "announced_reward_badges"
    }
}

private data class RewardStreak(
    val count: Int,
    val startDate: LocalDate?,
)

enum class ReleaseThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
}

data class ReleaseSourceSetting(
    val product: String,
    val type: String,
    val version: String,
    val enabled: Boolean,
    val defaultEnabled: Boolean,
)

data class RewardPerformance(
    val readStreakDays: Int,
    val completeStreakWeeks: Int,
    val longestReadStreakDays: Int,
    val longestCompleteStreakWeeks: Int,
    val readStreakStartDate: LocalDate?,
    val completeStreakStartDate: LocalDate?,
    val longestReadStreakStartDate: LocalDate?,
    val longestCompleteStreakStartDate: LocalDate?,
    val debugReadStreakStartDate: LocalDate?,
    val debugCompleteStreakStartDate: LocalDate?,
    val completedTaskOverride: Int?,
    val manuallyCompletedBadges: Set<String>,
    val manuallyOpenBadges: Set<String>,
    val announcedRewardBadges: Set<String>,
)

data class NotificationSettings(
    val enabled: Boolean = true,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
) {
    val timeLabel: String = "%02d:%02d".format(hour, minute)

    companion object {
        const val DEFAULT_HOUR = 8
        const val DEFAULT_MINUTE = 0
    }
}