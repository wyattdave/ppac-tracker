package com.releaseplanner.tracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class M365PlannerResponse(
    @SerialName("@odata.count") val count: Long = 0,
    @SerialName("value") val value: List<M365PlannerItemDto> = emptyList(),
)

@Serializable
data class M365PlannerItemDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("cloudInstances") val cloudInstances: List<String>? = null,
    @SerialName("platforms") val platforms: List<String>? = null,
    @SerialName("releaseRings") val releaseRings: List<String>? = null,
    @SerialName("moreInfoUrls") val moreInfoUrls: List<String>? = null,
    @SerialName("products") val products: List<String>? = null,
    @SerialName("generalAvailabilityDate") val generalAvailabilityDate: String? = null,
    @SerialName("previewAvailabilityDate") val previewAvailabilityDate: String? = null,
    @SerialName("created") val created: String = "",
    @SerialName("status") val status: String? = null,
    @SerialName("modified") val modified: String = "",
)

fun M365PlannerItemDto.toReleaseItemDto(source: ReleaseSource): ReleaseItemDto {
    val sourceKey = source.product.normalizedType().ifBlank { "source" }
    val sourceId = "m365:$sourceKey:$id"
    val sourceTitle = title.orEmpty()
    val featureTitle = sourceTitle.removeCopilotStudioPrefix().ifBlank { sourceTitle }
    val documentationUrl = bestDocumentationUrl()
    val normalizedStatus = status.orEmpty().toReleasePlannerStatus()

    return ReleaseItemDto(
        snapshotId = sourceId,
        releasePlanId = sourceId,
        productId = id.toString(),
        featureName = featureTitle,
        productArea = platforms.orEmpty().joinToString(),
        enabledFor = "",
        earlyAccessDate = previewAvailabilityDate.orEmpty(),
        publicPreviewDate = previewAvailabilityDate.orEmpty(),
        gaDate = generalAvailabilityDate.orEmpty(),
        product = source.product,
        gitCommitDate = modified.toIsoDate(),
        firstGitHubPushDate = created.toIsoDate(),
        docsUrl = documentationUrl,
        eaStatus = normalizedStatus,
        gaStatus = normalizedStatus,
        ppStatus = normalizedStatus,
        featureType = releaseRings.orEmpty().joinToString(),
        featureCategory = "M365 Planner",
        businessValue = "",
        featureDetails = description.orEmpty(),
        aiContribution = (
            source.product.contains("copilot", ignoreCase = true) ||
                products.orEmpty().any { it.contains("copilot", ignoreCase = true) }
        ).toString(),
        docsName = featureTitle.takeIf { documentationUrl.isNotBlank() }.orEmpty(),
        createdOn = created.toIsoDate(),
        geos = cloudInstances.orEmpty(),
        sourceFingerprint = listOf(
            description.orEmpty(),
            cloudInstances.orEmpty().joinToString(),
            platforms.orEmpty().joinToString(),
            releaseRings.orEmpty().joinToString(),
            moreInfoUrls.orEmpty().joinToString(),
            products.orEmpty().joinToString(),
            generalAvailabilityDate.orEmpty(),
            previewAvailabilityDate.orEmpty(),
            created,
            status.orEmpty(),
            modified,
        ).joinToString("\u001f"),
    )
}

private fun M365PlannerItemDto.bestDocumentationUrl(): String {
    val candidates = moreInfoUrls.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
    return candidates.firstOrNull { it.contains("learn.microsoft.com", ignoreCase = true) }
        ?: candidates.firstOrNull { it.startsWith("https://", ignoreCase = true) }
        ?: candidates.firstOrNull { it.startsWith("http://", ignoreCase = true) }
        ?: candidates.firstOrNull().orEmpty()
}

private fun String.removeCopilotStudioPrefix(): String {
    return replace(
        Regex("^\\s*Microsoft\\s+Copilot\\s+Studio\\s*:\\s*", RegexOption.IGNORE_CASE),
        "",
    ).trim()
}

private fun String.toReleasePlannerStatus(): String {
    val trimmed = trim()
    return when {
        trimmed.equals("Launched", ignoreCase = true) -> "Shipped"
        trimmed.equals("Shipped", ignoreCase = true) -> "Shipped"
        else -> trimmed
    }
}

private fun String.toIsoDate(): String {
    val trimmed = trim()
    return trimmed.takeIf { it.length >= 10 && it[4] == '-' && it[7] == '-' }
        ?.take(10)
        ?: trimmed
}
