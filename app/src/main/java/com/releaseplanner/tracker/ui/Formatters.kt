package com.releaseplanner.tracker.ui

import android.text.Html
import com.releaseplanner.tracker.data.local.ReleaseUpdateEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val displayFormat = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US)
private val rawDateFormats = listOf(
    DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.US),
)
private val monthYearFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

fun String.htmlToText(): String {
    if (isBlank()) return ""
    return Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}

fun ReleaseUpdateEntity.primaryDateLabel(): String {
    return gaDateValue.toDisplayDate()
        ?: gaDate.toDisplayDate()
        ?: ppDateValue.toDisplayDate()
        ?: publicPreviewDate.toDisplayDate()
        ?: "Date not announced"
}

fun ReleaseUpdateEntity.earlyAccessDateLabel(): String {
    return eaDateValue.toDisplayDate() ?: earlyAccessDate.toDisplayDate() ?: "-"
}

fun ReleaseUpdateEntity.publicPreviewDateLabel(): String {
    return ppDateValue.toDisplayDate() ?: publicPreviewDate.toDisplayDate() ?: "-"
}

fun ReleaseUpdateEntity.gaDateLabel(): String {
    return gaDateValue.toDisplayDate() ?: gaDate.toDisplayDate() ?: "-"
}

fun ReleaseUpdateEntity.releaseDate(): LocalDate? {
    return parseReleaseDate(gaDateValue)
        ?: parseReleaseDate(gaDate)
        ?: parseReleaseDate(ppDateValue)
        ?: parseReleaseDate(publicPreviewDate)
}

fun ReleaseUpdateEntity.timelineDate(sort: TimelineSortOption): LocalDate? {
    return when (sort) {
        TimelineSortOption.LastUpdate -> parseReleaseDate(gitCommitDate)
        TimelineSortOption.EarlyAccess -> parseReleaseDate(eaDateValue) ?: parseReleaseDate(earlyAccessDate)
        TimelineSortOption.PublicPreview -> parseReleaseDate(ppDateValue) ?: parseReleaseDate(publicPreviewDate)
        TimelineSortOption.GeneralAvailability -> parseReleaseDate(gaDateValue) ?: parseReleaseDate(gaDate)
    }
}

fun ReleaseUpdateEntity.timelineDateLabel(sort: TimelineSortOption): String {
    val dateLabel = timelineDate(sort)?.toDisplayDate()
    return when (sort) {
        TimelineSortOption.LastUpdate -> "Last update: ${dateLabel ?: "Not announced"}"
        TimelineSortOption.EarlyAccess -> "Early access: ${dateLabel ?: "Not announced"}"
        TimelineSortOption.PublicPreview -> "Public preview: ${dateLabel ?: "Not announced"}"
        TimelineSortOption.GeneralAvailability -> "GA: ${dateLabel ?: "Not announced"}"
    }
}

fun ReleaseUpdateEntity.isScheduledForToday(): Boolean {
    return releaseDate() == LocalDate.now()
}

fun ReleaseUpdateEntity.timelineMonthLabel(): String {
    val date = releaseDate()
    return date?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)) ?: "Unscheduled"
}

fun ReleaseUpdateEntity.timelineMonthLabel(sort: TimelineSortOption): String {
    val date = timelineDate(sort)
    return date?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)) ?: "Unscheduled"
}

fun LocalDate.toDisplayDate(): String = format(displayFormat)

fun ReleaseUpdateEntity.statusLabel(): String {
    return when {
        gaStatus.takeMeaningful() != null && !gaStatus.equals("N/A", ignoreCase = true) -> "GA $gaStatus"
        ppStatus.takeMeaningful() != null && !ppStatus.equals("N/A", ignoreCase = true) -> "Preview $ppStatus"
        eaStatus.takeMeaningful() != null && !eaStatus.equals("N/A", ignoreCase = true) -> "EA $eaStatus"
        else -> "Planned"
    }
}

fun Long.asSyncLabel(): String {
    val dateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val formatter = DateTimeFormatter.ofPattern("MMM d h:mm a", Locale.US)
    return "Synced ${dateTime.format(formatter)}"
}

private fun String.takeMeaningful(): String? {
    val trimmed = trim()
    return trimmed.takeIf { it.isNotEmpty() && it != "-" }
}

private fun String.toDisplayDate(): String? = parseReleaseDate(this)?.format(displayFormat)

private fun parseReleaseDate(value: String): LocalDate? {
    val meaningful = value.takeMeaningful() ?: return null
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
