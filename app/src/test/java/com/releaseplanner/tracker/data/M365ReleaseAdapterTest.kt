package com.releaseplanner.tracker.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M365ReleaseAdapterTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val source = ReleaseSource(
        product = "Copilot Studio",
        version = "M365",
        type = "PowerPlatform",
        url = "https://example.test/m365?top=500",
        schema = ReleaseSourceSchema.M365,
    )

    @Test
    fun `maps M365 fields into the existing release schema`() {
        val response = json.decodeFromString<M365PlannerResponse>(
            """
            {
              "@odata.count": 1,
              "value": [{
                "id": 567894,
                "title": "Microsoft Copilot Studio: Share autonomous agents",
                "description": "A description that has no legacy equivalent.",
                "cloudInstances": ["Worldwide (Standard Multi-Tenant)"],
                "platforms": ["Web"],
                "releaseRings": ["General Availability", "Preview"],
                "moreInfoUrls": ["https://contoso.test/info", "https://learn.microsoft.com/example"],
                "products": ["Microsoft Copilot Studio"],
                "generalAvailabilityDate": "2027-01",
                "previewAvailabilityDate": "2026-08",
                "created": "2026-07-20T22:37:15.9046041Z",
                "status": "Launched",
                "modified": "2026-07-21T10:00:00Z",
                "locale": null
              }]
            }
            """.trimIndent(),
        )

        val item = response.value.single().toReleaseItemDto(source)

        assertEquals("m365:copilotstudio:567894", item.releasePlanId)
        assertEquals("567894", item.productId)
        assertEquals("Share autonomous agents", item.featureName)
        assertEquals("2026-08", item.earlyAccessDate)
        assertEquals("2026-08", item.publicPreviewDate)
        assertEquals("2027-01", item.gaDate)
        assertEquals("2026-07-21", item.gitCommitDate)
        assertEquals("Shipped", item.gaStatus)
        assertEquals("M365 Planner", item.featureCategory)
        assertEquals("https://learn.microsoft.com/example", item.docsUrl)
        assertEquals("", item.businessValue)
        assertEquals("", item.featureDetails)
        assertEquals(listOf("Worldwide (Standard Multi-Tenant)"), item.geos)
        assertTrue(item.aiContribution.toBoolean())
    }

    @Test
    fun `uses an existing legacy id after a title match`() {
        val legacyEntity = ReleaseItemDto(
            releasePlanId = "legacy-release-id",
            featureName = "Share autonomous agents",
        ).toEntity(source.copy(schema = ReleaseSourceSchema.ReleasePlanner), existing = null, now = 1)
        val m365Item = M365PlannerItemDto(
            id = 567894,
            title = "Microsoft Copilot Studio: Share autonomous agents",
        ).toReleaseItemDto(source)

        val migratedEntity = m365Item.toEntity(source, existing = legacyEntity, now = 2)

        assertEquals("legacy-release-id", migratedEntity.id)
        assertEquals("m365:copilotstudio:567894", migratedEntity.releasePlanId)
        assertEquals("567894", migratedEntity.productId)
        assertEquals(
            legacyEntity.featureName.normalizedReleaseTitle(),
            migratedEntity.featureName.normalizedReleaseTitle(),
        )
    }

    @Test
    fun `description-only edits are retained in change detection`() {
        val first = M365PlannerItemDto(
            id = 567894,
            title = "Microsoft Copilot Studio: Share autonomous agents",
            description = "First description",
            modified = "2026-07-21T10:00:00Z",
        ).toReleaseItemDto(source)
        val changed = M365PlannerItemDto(
            id = 567894,
            title = "Microsoft Copilot Studio: Share autonomous agents",
            description = "Changed description",
            modified = "2026-07-21T10:00:00Z",
        ).toReleaseItemDto(source)

        val firstEntity = first.toEntity(source, existing = null, now = 1)
        val changedEntity = changed.toEntity(source, existing = firstEntity, now = 2)

        assertTrue(changedEntity.isChanged)
        assertTrue(firstEntity.contentHash != changedEntity.contentHash)
        assertEquals("", changedEntity.businessValue)
        assertEquals("", changedEntity.featureDetails)
    }

    @Test
    fun `the same roadmap item has a distinct id in each configured source`() {
        val item = M365PlannerItemDto(
            id = 566999,
            title = "A feature associated with multiple products",
        )

        val copilotStudioId = item.toReleaseItemDto(source).releasePlanId
        val m365CopilotId = item.toReleaseItemDto(
            source.copy(product = "Microsoft Copilot (Microsoft 365)"),
        ).releasePlanId

        assertEquals("m365:copilotstudio:566999", copilotStudioId)
        assertEquals("m365:microsoftcopilotmicrosoft365:566999", m365CopilotId)
        assertTrue(copilotStudioId != m365CopilotId)
    }
}
