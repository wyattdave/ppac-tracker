package com.releaseplanner.tracker.data

data class ReleaseSource(
    val product: String,
    val version: String,
    val type: String,
    val url: String,
) {
    val isPowerPlatform: Boolean
        get() = type.normalizedType() == "powerplatform"
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
            url = "https://releaseplans.microsoft.com/en-US/releaseplanner-json/?productId=1019ec3d-1dc5-e911-a969-000d3a4f36ce&langCode=en-us",
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
    )
}

fun String.normalizedType(): String = lowercase().filter { it.isLetterOrDigit() }
