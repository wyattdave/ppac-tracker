package com.releaseplanner.tracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSyncTest {
    private val sources = DefaultReleaseSources.all.take(6)

    @Test
    fun `fetches concurrently with at most four requests and preserves source order`() = runBlocking {
        var active = 0
        var peak = 0
        val results = fetchReleaseSources(sources, {
            active += 1
            peak = maxOf(peak, active)
            delay(20)
            active -= 1
            listOf(ReleaseItemDto(featureName = it.product))
        })

        assertEquals(4, peak)
        assertEquals(sources, results.map { it.first })
        assertTrue(results.all { it.second.isSuccess })
    }

    @Test
    fun `a failed or stalled feed does not discard successful feeds`() = runBlocking {
        val results = fetchReleaseSources(sources.take(3), { source ->
            when (source) {
                sources[0] -> error("Feed unavailable")
                sources[1] -> awaitCancellation()
                else -> listOf(ReleaseItemDto(featureName = source.product))
            }
        }, timeoutMillis = 50)

        assertTrue(results[0].second.isFailure)
        assertTrue(results[1].second.exceptionOrNull()?.message.orEmpty().contains("timed out"))
        assertTrue(results[2].second.isSuccess)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is propagated rather than reported as a failed feed`() {
        runBlocking {
            fetchReleaseSources(sources, { throw CancellationException("Cancelled") })
        }
    }
}