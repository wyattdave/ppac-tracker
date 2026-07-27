package com.releaseplanner.tracker.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ReleaseDateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val isMonthOnly: Boolean,
) {
    operator fun contains(date: LocalDate): Boolean {
        return !date.isBefore(start) && !date.isAfter(endInclusive)
    }

    fun overlaps(otherStart: LocalDate, otherEndInclusive: LocalDate): Boolean {
        return !endInclusive.isBefore(otherStart) && !start.isAfter(otherEndInclusive)
    }
}

private val exactDateFormats = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.US),
)
private val namedMonthYearFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

fun parseReleaseDateRange(value: String): ReleaseDateRange? {
    val meaningful = value.trim().takeIf { it.isNotEmpty() && it != "-" } ?: return null

    exactDateFormats.forEach { formatter ->
        try {
            val date = LocalDate.parse(meaningful, formatter)
            return ReleaseDateRange(date, date, isMonthOnly = false)
        } catch (_: DateTimeParseException) {
        }
    }

    val month = try {
        YearMonth.parse(meaningful)
    } catch (_: DateTimeParseException) {
        try {
            YearMonth.parse(meaningful, namedMonthYearFormat)
        } catch (_: DateTimeParseException) {
            return null
        }
    }
    return ReleaseDateRange(
        start = month.atDay(1),
        endInclusive = month.atEndOfMonth(),
        isMonthOnly = true,
    )
}
