package com.releaseplanner.tracker.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

class ReleasePlannerClient(
    private val httpClient: HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                },
            )
        }
    },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun fetch(source: ReleaseSource): List<ReleaseItemDto> {
        return fetchResponse(source).results
    }

    suspend fun debug(source: ReleaseSource): ReleaseApiDiagnostic {
        val response = httpClient.get(source.url)
        val rawBody = response.bodyAsText()
        val normalizedBody = rawBody.stripJsonPrefix()
        return runCatching { json.decodeFromString<ReleasePlannerResponse>(normalizedBody) }
            .fold(
                onSuccess = { parsed ->
                    ReleaseApiDiagnostic(
                        sourceProduct = source.product,
                        url = source.url,
                        statusCode = response.status.value,
                        contentType = response.headers[HttpHeaders.ContentType].orEmpty(),
                        bodyLength = rawBody.length,
                        parsedCount = parsed.results.size,
                        firstFeature = parsed.results.firstOrNull()?.featureName.orEmpty(),
                        responsePreview = normalizedBody.take(1_500),
                    )
                },
                onFailure = { error ->
                    ReleaseApiDiagnostic(
                        sourceProduct = source.product,
                        url = source.url,
                        statusCode = response.status.value,
                        contentType = response.headers[HttpHeaders.ContentType].orEmpty(),
                        bodyLength = rawBody.length,
                        parsedCount = 0,
                        firstFeature = "",
                        responsePreview = normalizedBody.take(1_500),
                        errorMessage = error.message ?: error::class.simpleName.orEmpty(),
                    )
                },
            )
    }

    private suspend fun fetchResponse(source: ReleaseSource): ReleasePlannerResponse {
        val rawBody = httpClient.get(source.url).bodyAsText().stripJsonPrefix()
        return json.decodeFromString<ReleasePlannerResponse>(rawBody)
    }
}

data class ReleaseApiDiagnostic(
    val sourceProduct: String,
    val url: String,
    val statusCode: Int,
    val contentType: String,
    val bodyLength: Int,
    val parsedCount: Int,
    val firstFeature: String,
    val responsePreview: String,
    val errorMessage: String? = null,
)

private fun String.stripJsonPrefix(): String {
    return trimStart('\uFEFF', '\n', '\r', '\t', ' ')
}
