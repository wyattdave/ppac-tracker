package com.releaseplanner.tracker.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

class ReleasePlannerClient(
    private val httpClient: HttpClient = HttpClient(Android) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
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
        val rawBody = httpClient.get(source.url).bodyAsText().stripJsonPrefix()
        return decodeItems(source, rawBody)
    }

    suspend fun debug(source: ReleaseSource): ReleaseApiDiagnostic {
        val response = httpClient.get(source.url) {
            expectSuccess = false
        }
        val rawBody = response.bodyAsText()
        val normalizedBody = rawBody.stripJsonPrefix()
        return runCatching { decodeItems(source, normalizedBody) }
            .fold(
                onSuccess = { items ->
                    ReleaseApiDiagnostic(
                        sourceProduct = source.product,
                        url = source.url,
                        statusCode = response.status.value,
                        contentType = response.headers[HttpHeaders.ContentType].orEmpty(),
                        bodyLength = rawBody.length,
                        parsedCount = items.size,
                        firstFeature = items.firstOrNull()?.featureName.orEmpty(),
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

    private fun decodeItems(source: ReleaseSource, rawBody: String): List<ReleaseItemDto> {
        return when (source.schema) {
            ReleaseSourceSchema.ReleasePlanner ->
                json.decodeFromString<ReleasePlannerResponse>(rawBody).results

            ReleaseSourceSchema.M365 ->
                json.decodeFromString<M365PlannerResponse>(rawBody).value.map { it.toReleaseItemDto(source) }
        }
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
