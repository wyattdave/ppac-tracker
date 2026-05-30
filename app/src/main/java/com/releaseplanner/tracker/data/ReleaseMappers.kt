package com.releaseplanner.tracker.data

import com.releaseplanner.tracker.data.local.ReleaseUpdateEntity
import java.security.MessageDigest

fun ReleaseItemDto.stableId(source: ReleaseSource): String {
    return releasePlanId.ifBlank { snapshotId }.ifBlank { "${source.product}:$featureName".sha256() }
}

fun ReleaseItemDto.toEntity(
    source: ReleaseSource,
    existing: ReleaseUpdateEntity?,
    now: Long,
): ReleaseUpdateEntity {
    val hash = contentHash(source)
    val contentChanged = existing != null && existing.contentHash != hash
    return ReleaseUpdateEntity(
        id = stableId(source),
        snapshotId = snapshotId,
        releasePlanId = releasePlanId,
        productId = productId,
        product = product.ifBlank { source.product },
        sourceProduct = source.product,
        sourceUrl = source.url,
        featureName = featureName.ifBlank { "Untitled update" },
        productArea = productArea,
        enabledFor = enabledFor,
        earlyAccessDate = earlyAccessDate,
        publicPreviewDate = publicPreviewDate,
        gaDate = gaDate,
        releaseWaveName = releaseWaveName,
        gaReleaseWaveName = gaReleaseWaveName,
        gitCommitDate = gitCommitDate,
        firstGitHubPushDate = firstGitHubPushDate,
        docsUrl = docsUrl,
        docUrl = docUrl,
        docsName = docsName,
        gaDateValue = gaDateValue,
        ppDateValue = ppDateValue,
        eaDateValue = eaDateValue,
        gaStatus = gaStatus,
        ppStatus = ppStatus,
        eaStatus = eaStatus,
        featureType = featureType,
        featureCategory = featureCategory,
        businessValue = businessValue,
        featureDetails = featureDetails,
        aiContribution = aiContribution.equals("true", ignoreCase = true),
        geos = geos,
        createdOn = createdOn,
        firstSeenAt = existing?.firstSeenAt ?: now,
        lastSeenAt = now,
        changedAt = if (contentChanged) now else existing?.changedAt,
        contentHash = hash,
        isNew = isNewFromReleasePlannerDates(),
        isChanged = contentChanged || (existing?.isChanged ?: false),
    )
}

private fun ReleaseItemDto.isNewFromReleasePlannerDates(): Boolean {
    return firstGitHubPushDate.isBlank() || gitCommitDate.trim().equals(firstGitHubPushDate.trim(), ignoreCase = true)
}

private fun ReleaseItemDto.contentHash(source: ReleaseSource): String {
    return listOf(
        source.product,
        snapshotId,
        releasePlanId,
        featureName,
        productArea,
        enabledFor,
        earlyAccessDate,
        publicPreviewDate,
        gaDate,
        gaStatus,
        ppStatus,
        eaStatus,
        gitCommitDate,
        firstGitHubPushDate,
        docsUrl,
        docUrl,
        businessValue,
        featureDetails,
    ).joinToString("|").sha256()
}

private fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
