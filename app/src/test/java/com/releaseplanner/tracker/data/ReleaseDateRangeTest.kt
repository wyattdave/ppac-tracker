package com.releaseplanner.tracker.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDateRangeTest {
    @Test
    fun `month synced during that month uses the sync day only`() {
        val range = requireNotNull(
            parseReleaseDateRange("2026-08", monthOnlyAnchor = LocalDate.of(2026, 8, 17)),
        )

        assertEquals(LocalDate.of(2026, 8, 17), range.start)
        assertEquals(LocalDate.of(2026, 8, 17), range.endInclusive)
        assertTrue(range.isMonthOnly)
        assertTrue(LocalDate.of(2026, 8, 17) in range)
        assertFalse(LocalDate.of(2026, 8, 18) in range)
        assertFalse(range.overlaps(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 5)))
        assertFalse(range.overlaps(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7)))
    }

    @Test
    fun `month synced after that month uses its last day`() {
        val range = requireNotNull(
            parseReleaseDateRange("2026-06", monthOnlyAnchor = LocalDate.of(2026, 7, 4)),
        )

        assertEquals(LocalDate.of(2026, 6, 30), range.start)
        assertEquals(range.start, range.endInclusive)
    }

    @Test
    fun `month synced before that month uses its first day`() {
        val range = requireNotNull(
            parseReleaseDateRange("2026-08", monthOnlyAnchor = LocalDate.of(2026, 7, 28)),
        )

        assertEquals(LocalDate.of(2026, 8, 1), range.start)
        assertEquals(range.start, range.endInclusive)
    }

    @Test
    fun `legacy and ISO exact dates remain one day`() {
        val legacy = requireNotNull(parseReleaseDateRange("08/17/2026"))
        val iso = requireNotNull(parseReleaseDateRange("2026-08-17"))

        assertEquals(legacy, iso)
        assertFalse(legacy.isMonthOnly)
        assertEquals(legacy.start, legacy.endInclusive)
    }

    @Test
    fun `named month format remains supported`() {
        val range = requireNotNull(
            parseReleaseDateRange("Aug 2026", monthOnlyAnchor = LocalDate.of(2026, 8, 12)),
        )

        assertEquals(LocalDate.of(2026, 8, 12), range.start)
        assertEquals(range.start, range.endInclusive)
        assertTrue(range.isMonthOnly)
    }
}
