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
        rewardPerformanceState.value = readRewardPerformance(syncLongest = true)
    }

    fun recordTaskCompleted(date: LocalDate = LocalDate.now()) {
        updateDateSet(COMPLETE_TASK_DATES_KEY, date)
        rewardPerformanceState.value = readRewardPerformance(syncLongest = true)
    }

    fun recordApiFailure(date: LocalDate = LocalDate.now()) {
        updateDateSet(API_FAILURE_DATES_KEY, date)
        rewardPerformanceState.value = readRewardPerformance(syncLongest = true)
    }

    fun setDebugRewardState(readStreakDays: Int, completeStreakWeeks: Int, completedTaskCount: Int) {
        prefs.edit()
            .putInt(DEBUG_READ_STREAK_DAYS_KEY, readStreakDays.coerceAtLeast(0))
            .putInt(DEBUG_COMPLETE_STREAK_WEEKS_KEY, completeStreakWeeks.coerceAtLeast(0))
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

    private fun readRewardPerformance(syncLongest: Boolean = false): RewardPerformance {
        val readDates = readDateSet(READ_TASK_DATES_KEY)
        val completeDates = readDateSet(COMPLETE_TASK_DATES_KEY)
        val apiFailureDates = readDateSet(API_FAILURE_DATES_KEY)
        val baseReadStreakDays = consecutiveDaysEndingToday(readDates, apiFailureDates)
        val baseCompleteStreakWeeks = consecutiveWeeksEndingThisOrLastWeek(completeDates, apiFailureDates)
        val readStreakDays = prefs.getOptionalInt(DEBUG_READ_STREAK_DAYS_KEY) ?: baseReadStreakDays
        val completeStreakWeeks = prefs.getOptionalInt(DEBUG_COMPLETE_STREAK_WEEKS_KEY) ?: baseCompleteStreakWeeks
        val longestReadStreakDays = maxOf(prefs.getInt(LONGEST_READ_STREAK_DAYS_KEY, 0), readStreakDays)
        val longestCompleteStreakWeeks = maxOf(prefs.getInt(LONGEST_COMPLETE_STREAK_WEEKS_KEY, 0), completeStreakWeeks)

        if (syncLongest) {
            prefs.edit()
                .putInt(LONGEST_READ_STREAK_DAYS_KEY, longestReadStreakDays)
                .putInt(LONGEST_COMPLETE_STREAK_WEEKS_KEY, longestCompleteStreakWeeks)
                .apply()
        }

        return RewardPerformance(
            readStreakDays = readStreakDays,
            completeStreakWeeks = completeStreakWeeks,
            longestReadStreakDays = longestReadStreakDays,
            longestCompleteStreakWeeks = longestCompleteStreakWeeks,
            completedTaskOverride = prefs.getOptionalInt(DEBUG_COMPLETED_TASK_COUNT_KEY),
            manuallyCompletedBadges = prefs.getStringSet(MANUAL_COMPLETED_BADGES_KEY, emptySet()).orEmpty(),
            manuallyOpenBadges = prefs.getStringSet(MANUAL_OPEN_BADGES_KEY, emptySet()).orEmpty(),
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

    private fun consecutiveDaysEndingToday(readDates: Set<LocalDate>, apiFailureDates: Set<LocalDate>): Int {
        var count = 0
        var cursor = LocalDate.now()
        while (cursor.isSuccessfulReadDay(readDates, apiFailureDates)) {
            count += 1
            cursor = cursor.minusDays(1)
        }
        return count
    }

    private fun consecutiveWeeksEndingThisOrLastWeek(completeDates: Set<LocalDate>, apiFailureDates: Set<LocalDate>): Int {
        val completedWeeks = (completeDates + apiFailureDates).map { it.weekStart() }.toSet()
        var cursor = LocalDate.now().weekStart()
        if (!completedWeeks.contains(cursor)) {
            cursor = cursor.minusWeeks(1)
        }

        var count = 0
        while (completedWeeks.contains(cursor)) {
            count += 1
            cursor = cursor.minusWeeks(1)
        }
        return count
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
        const val LONGEST_COMPLETE_STREAK_WEEKS_KEY = "longest_complete_streak_weeks"
        const val DEBUG_READ_STREAK_DAYS_KEY = "debug_read_streak_days"
        const val DEBUG_COMPLETE_STREAK_WEEKS_KEY = "debug_complete_streak_weeks"
        const val DEBUG_COMPLETED_TASK_COUNT_KEY = "debug_completed_task_count"
        const val MANUAL_COMPLETED_BADGES_KEY = "manual_completed_badges"
        const val MANUAL_OPEN_BADGES_KEY = "manual_open_badges"
    }
}

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
    val completedTaskOverride: Int?,
    val manuallyCompletedBadges: Set<String>,
    val manuallyOpenBadges: Set<String>,
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