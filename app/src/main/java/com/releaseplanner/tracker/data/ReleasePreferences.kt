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
    private val rewardPerformanceState = MutableStateFlow(readRewardPerformance())
    private val themeModeState = MutableStateFlow(readThemeMode())

    val sourceSettings: StateFlow<List<ReleaseSourceSetting>> = sourceSettingsState
    val rewardPerformance: StateFlow<RewardPerformance> = rewardPerformanceState
    val themeMode: StateFlow<ReleaseThemeMode> = themeModeState

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

    fun recordTaskRead(date: LocalDate = LocalDate.now()) {
        updateDateSet(READ_TASK_DATES_KEY, date)
        rewardPerformanceState.value = readRewardPerformance()
    }

    fun recordTaskCompleted(date: LocalDate = LocalDate.now()) {
        updateDateSet(COMPLETE_TASK_DATES_KEY, date)
        rewardPerformanceState.value = readRewardPerformance()
    }

    fun recordApiFailure(date: LocalDate = LocalDate.now()) {
        updateDateSet(API_FAILURE_DATES_KEY, date)
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

    private fun readRewardPerformance(): RewardPerformance {
        val readDates = readDateSet(READ_TASK_DATES_KEY)
        val completeDates = readDateSet(COMPLETE_TASK_DATES_KEY)
        val apiFailureDates = readDateSet(API_FAILURE_DATES_KEY)
        return RewardPerformance(
            readStreakDays = consecutiveDaysEndingToday(readDates, apiFailureDates),
            completeStreakWeeks = consecutiveWeeksEndingThisOrLastWeek(completeDates, apiFailureDates),
        )
    }

    private fun readThemeMode(): ReleaseThemeMode {
        val stored = prefs.getString(THEME_MODE_KEY, null)
        return ReleaseThemeMode.entries.firstOrNull { it.name == stored } ?: ReleaseThemeMode.Light
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
)