package com.releaseplanner.tracker.data

data class ReleaseSource(
    val product: String,
    val version: String,
    val type: String,
    val url: String,
    val schema: ReleaseSourceSchema = ReleaseSourceSchema.ReleasePlanner,
) {
    val isPowerPlatform: Boolean
        get() = type.normalizedType() == "powerplatform"

    val defaultEnabled: Boolean
        get() = schema == ReleaseSourceSchema.M365
}

enum class ReleaseSourceSchema {
    ReleasePlanner,
    M365,
    ;

    companion object {
        fun fromConfig(value: String): ReleaseSourceSchema {
            return when (value.normalizedType()) {
                "", "releaseplanner" -> ReleasePlanner
                "m365", "m365planner", "microsoft365" -> M365
                else -> error("Unsupported release source schema: $value")
            }
        }
    }
}

object DefaultReleaseSources {
    val all = listOf(
        ReleaseSource(
            product = "Power Apps",
            version = "2026-06",
            type = "PowerPlatform",
            url = "https://releaseplans.microsoft.com/en-US/releaseplanner-json/?productId=e72f17ac-715d-e911-a968-000d3a4e32b5&langCode=en-us",
        ),
        ReleaseSource(
            product = "Power Automate",
            version = "2026-06",
            type = "PowerPlatform",
            url = "https://releaseplans.microsoft.com/en-US/releaseplanner-json/?productId=e92f17ac-715d-e911-a968-000d3a4e32b5&langCode=en-us",
        ),
        ReleaseSource(
            product = "Copilot Studio",
            version = "2026-06",
            type = "PowerPlatform",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=false&top=500&skip=0&filter=products/any(f:f%20eq%20%27Microsoft%20Copilot%20Studio%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "Microsoft 365 admin center",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27Microsoft%20365%20admin%20center%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "PowerAutomate(M365)",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27Power%20Automate%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "PowerApps(M365)",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27Power%20Apps%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "Dataverse(M365)",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27Microsoft%20Dataverse%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "Platform(M365)",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27Microsoft%20Power%20Platform%20governance%20and%20administration%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "SharePoint",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27SharePoint%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "Microsoft Agent 365",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27Microsoft%20Agent%20365%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "Microsoft Copilot (Microsoft 365)",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=50&skip=0&filter=products/any(f:f%20eq%20%27Microsoft%20Copilot%20(Microsoft%20365)%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "Microsoft Purview",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27Microsoft%20Purview%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "Excel",
            version = "M365",
            type = "M365",
            url = "https://www.microsoft.com/releasecommunications/api/v2/m365?\$count=true&includeFacets=true&top=500&skip=0&filter=products/any(f:f%20eq%20%27Excel%27)&orderby=generalAvailabilityDate%20desc",
            schema = ReleaseSourceSchema.M365,
        ),
        ReleaseSource(
            product = "AI Builder",
            version = "2026-06",
            type = "PowerPlatform",
            url = "https://releaseplans.microsoft.com/en-US/releaseplanner-json/?productId=eb2f17ac-715d-e911-a968-000d3a4e32b5&langCode=en-us",
        ),
        ReleaseSource(
            product = "Dataverse",
            version = "2026-06",
            type = "PowerPlatform",
            url = "https://releaseplans.microsoft.com/en-US/releaseplanner-json/?productId=a0e02858-50a4-ea11-a812-000d3a8faea9&langCode=en-us",
        ),
        ReleaseSource(
            product = "Platform",
            version = "2026-06",
            type = "PowerPlatform",
            url = "https://releaseplans.microsoft.com/en-US/releaseplanner-json/?productId=dbedfa94-1517-ea11-a811-000d3a8f010c&langCode=en-us",
        ),
        ReleaseSource(
            product = "Power Pages",
            version = "2026-06",
            type = "PowerPlatform",
            url = "https://releaseplans.microsoft.com/en-US/releaseplanner-json/?productId=1197f7de-0a44-ec11-8c62-00224829b77f&langCode=en-us",
        ),
    ).sortedByDescending { it.defaultEnabled }
}

fun String.normalizedType(): String = lowercase().filter { it.isLetterOrDigit() }

fun String.releaseProductLabel(): String = when (this) {
    "Platform" -> "Power Platform"
    "Platform(M365)" -> "Power Platform (M365)"
    else -> this
}

internal fun diagnosticSource(
    sources: List<ReleaseSource>,
    enabledSources: List<ReleaseSource>,
): ReleaseSource = enabledSources.firstOrNull { it.schema == ReleaseSourceSchema.M365 }
    ?: sources.firstOrNull { it.schema == ReleaseSourceSchema.M365 }
    ?: error("No Microsoft 365 API source is configured")
