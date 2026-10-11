package com.releaseplanner.tracker.data

import com.releaseplanner.tracker.data.local.UserTrackingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseMetricsTest {
    private val source = DefaultReleaseSources.all.first()
    private val releases = listOf(
        ReleaseItemDto(snapshotId = "ga", gaStatus = "shipped", gaDate = "2099-01-01"),
        ReleaseItemDto(snapshotId = "preview", ppStatus = "Shipped", gaStatus = "Planned"),
        ReleaseItemDto(snapshotId = "early", eaStatus = "Shipped"),
        ReleaseItemDto(snapshotId = "planned", gaStatus = "Planned", gaDate = "2000-01-01", publicPreviewDate = "2000-01-01"),
        ReleaseItemDto(snapshotId = "hidden", gaStatus = "Shipped"),
        ReleaseItemDto(snapshotId = "disabled", product = "Excel", gaStatus = "Shipped"),
        ReleaseItemDto(snapshotId = "skipped", gaStatus = "Shipped"),
        ReleaseItemDto(snapshotId = "completed", gaStatus = "Shipped"),
    ).map { it.toEntity(source, existing = null, now = 1) }
    private val tracking = mapOf(
        "hidden" to UserTrackingEntity(releaseId = "hidden", isHidden = true, isSaved = true, notes = "Keep this note"),
        "skipped" to UserTrackingEntity(releaseId = "skipped", isSkipped = true),
        "completed" to UserTrackingEntity(releaseId = "completed", isComplete = true),
    )

    @Test
    fun `GA completion considers all cached shipped tasks regardless of visibility`() {
        val eligible = releasedTasksToComplete(releases, tracking)

        assertEquals(listOf("ga", "hidden", "disabled", "skipped"), eligible.map { it.id })
        assertEquals("Keep this note", tracking.getValue("hidden").notes)
        assertEquals(true, tracking.getValue("skipped").isSkipped)
    }

    @Test
    fun `public preview completion includes GA but excludes early access and planned tasks`() {
        val eligible = releasedTasksToComplete(releases, tracking, includePublicPreview = true)

        assertEquals(listOf("ga", "preview", "hidden", "disabled", "skipped"), eligible.map { it.id })
    }
}