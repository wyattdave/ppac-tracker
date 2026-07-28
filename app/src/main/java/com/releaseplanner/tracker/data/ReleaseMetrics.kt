package com.releaseplanner.tracker.data

import com.releaseplanner.tracker.data.local.ReleaseUpdateEntity
import com.releaseplanner.tracker.data.local.UserTrackingEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
            update.lastUpdateDate()?.overlaps(sevenDaysAgo, today) == true
        },
        totalGaThisWeek = visibleUpdates.count { update ->
            update.generalAvailabilityDate()?.overlaps(today, sevenDaysAhead) == true
        },
    )
}

private fun ReleaseUpdateEntity.lastUpdateDate(): ReleaseDateRange? = parseReleaseDateRange(gitCommitDate)

private fun ReleaseUpdateEntity.generalAvailabilityDate(): ReleaseDateRange? {
    val firstSeenDate = Instant.ofEpochMilli(firstSeenAt).atZone(ZoneId.systemDefault()).toLocalDate()
    return parseReleaseDateRange(gaDateValue, firstSeenDate)
        ?: parseReleaseDateRange(gaDate, firstSeenDate)
}
