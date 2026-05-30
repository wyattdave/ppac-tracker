package com.releaseplanner.tracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleasePlannerResponse(
    @SerialName("results") val results: List<ReleaseItemDto> = emptyList(),
)

@Serializable
data class ReleaseItemDto(
    @SerialName("SnapshotId") val snapshotId: String = "",
    @SerialName("BusinessValue") val businessValue: String = "",
    @SerialName("FeatureDetails") val featureDetails: String = "",
    @SerialName("ProductId") val productId: String = "",
    @SerialName("FeatureName") val featureName: String = "",
    @SerialName("ProductArea") val productArea: String = "",
    @SerialName("ProductAssociatedFolder") val productAssociatedFolder: String = "",
    @SerialName("EnabledFor") val enabledFor: String = "",
    @SerialName("EarlyAccessDate") val earlyAccessDate: String = "",
    @SerialName("PublicPreviewDate") val publicPreviewDate: String = "",
    @SerialName("GADate") val gaDate: String = "",
    @SerialName("ReleaseWaveName") val releaseWaveName: String = "",
    @SerialName("GAReleaseWaveName") val gaReleaseWaveName: String = "",
    @SerialName("Product") val product: String = "",
    @SerialName("GitCommitDate") val gitCommitDate: String = "",
    @SerialName("FirstGitHubPushDate") val firstGitHubPushDate: String = "",
    @SerialName("DocsUrl") val docsUrl: String = "",
    @SerialName("GADateValue") val gaDateValue: String = "",
    @SerialName("EADateValue") val eaDateValue: String = "",
    @SerialName("PPDateValue") val ppDateValue: String = "",
    @SerialName("EAStatus") val eaStatus: String = "",
    @SerialName("GAStatus") val gaStatus: String = "",
    @SerialName("PPStatus") val ppStatus: String = "",
    @SerialName("FeatureType") val featureType: String = "",
    @SerialName("FeatureCategory") val featureCategory: String = "",
    @SerialName("ReleasePlanID") val releasePlanId: String = "",
    @SerialName("AiContribution") val aiContribution: String = "false",
    @SerialName("docsName") val docsName: String = "",
    @SerialName("docUrl") val docUrl: String = "",
    @SerialName("Createdon") val createdOn: String = "",
    @SerialName("geos") val geos: List<String> = emptyList(),
)
