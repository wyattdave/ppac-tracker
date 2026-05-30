package com.releaseplanner.tracker.data

import com.releaseplanner.tracker.data.local.ReleaseUpdateEntity
import com.releaseplanner.tracker.data.local.UserTrackingEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ReleaseSummaryMetrics(
    val totalOpen: Int = 0,
    val totalLastSevenDays: Int = 0,
    val totalGaThisWeek: Int = 0,
)

fun calculateReleaseSummaryMetrics(
    updates: List<ReleaseUpdateEntity>,
    trackingByReleaseId: Map<String, UserTrackingEntity> = emptyMap(),
    today: LocalDate = LocalDate.now(),
    includeHidden: Boolean = false,
): ReleaseSummaryMetrics {
    val visibleUpdates = if (includeHidden) updates else updates.filter { trackingByReleaseId[it.id]?.isHidden != true }
    val sevenDaysAgo = today.minusDays(7)
    val sevenDaysAhead = today.plusDays(7)

    return ReleaseSummaryMetrics(
        totalOpen = visibleUpdates.count { update ->
            val tracking = trackingByReleaseId[update.id]
            tracking?.isComplete != true && tracking?.isSkipped != true
        },
        totalLastSevenDays = visibleUpdates.count { update ->
            update.lastUpdateDate()?.isWithin(sevenDaysAgo, today) == true
        },
        totalGaThisWeek = visibleUpdates.count { update ->
            update.generalAvailabilityDate()?.isWithin(today, sevenDaysAhead) == true
        },
    )
}

private val rawDateFormats = listOf(
    DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.US),
)
private val monthYearFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

private fun ReleaseUpdateEntity.lastUpdateDate(): LocalDate? = parseReleaseDate(gitCommitDate)

private fun ReleaseUpdateEntity.generalAvailabilityDate(): LocalDate? {
    return parseReleaseDate(gaDateValue) ?: parseReleaseDate(gaDate)
}

private fun LocalDate.isWithin(start: LocalDate, end: LocalDate): Boolean {
    return !isBefore(start) && !isAfter(end)
}

private fun parseReleaseDate(value: String): LocalDate? {
    val meaningful = value.trim().takeIf { it.isNotEmpty() && it != "-" } ?: return null
    rawDateFormats.forEach { formatter ->
        try {
            return LocalDate.parse(meaningful, formatter)
        } catch (_: DateTimeParseException) {
        }
    }
    try {
        return YearMonth.parse(meaningful, monthYearFormat).atDay(1)
    } catch (_: DateTimeParseException) {
    }
    return null
}