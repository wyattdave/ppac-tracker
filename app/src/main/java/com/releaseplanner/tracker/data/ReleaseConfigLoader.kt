package com.releaseplanner.tracker.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object ReleaseConfigLoader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun loadSources(context: Context): List<ReleaseSource> {
        return runCatching {
            decodeAsset<List<ReleaseSourceConfig>>(context, "api.json")
                .mapNotNull { it.toReleaseSource() }
                .ifEmpty { DefaultReleaseSources.all }
        }.getOrDefault(DefaultReleaseSources.all)
    }

    fun loadRewards(context: Context): List<RewardDefinition> {
        return runCatching {
            decodeAsset<List<RewardDefinition>>(context, "rewards.json")
                .distinctBy { listOf(it.type.trim(), it.name.trim(), it.description.trim()).joinToString("|") }
        }.getOrDefault(emptyList())
    }

    private inline fun <reified T> decodeAsset(context: Context, fileName: String): T {
        val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}

@Serializable
data class RewardDefinition(
    @SerialName("type") val type: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String = "",
)

@Serializable
private data class ReleaseSourceConfig(
    @SerialName("Product") val product: String = "",
    @SerialName("Version") val version: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("Url") val url: String = "",
    @SerialName("url") val legacyUrl: String = "",
) {
    fun toReleaseSource(): ReleaseSource? {
        val configuredUrl = url.ifBlank { legacyUrl }
        if (product.isBlank() || configuredUrl.isBlank()) return null
        return ReleaseSource(
            product = product.toDisplayProductName(),
            version = version,
            type = type,
            url = configuredUrl,
        )
    }
}

private fun String.toDisplayProductName(): String {
    return when (trim()) {
        "PowerApps" -> "Power Apps"
        "PowerAutomate" -> "Power Automate"
        "CopilotStudio" -> "Copilot Studio"
        "AiBuilder" -> "AI Builder"
        "PowerPages" -> "Power Pages"
        else -> trim().ifBlank { this }
    }
}