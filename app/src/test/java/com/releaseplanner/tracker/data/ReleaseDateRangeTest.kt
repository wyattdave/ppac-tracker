package com.releaseplanner.tracker.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDateRangeTest {
    @Test
    fun `ISO month covers every day in that month`() {
        val range = requireNotNull(parseReleaseDateRange("2026-08"))

        assertEquals(LocalDate.of(2026, 8, 1), range.start)
        assertEquals(LocalDate.of(2026, 8, 31), range.endInclusive)
        assertTrue(range.isMonthOnly)
        assertTrue(LocalDate.of(2026, 8, 17) in range)
        assertTrue(range.overlaps(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 5)))
        assertFalse(range.overlaps(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7)))
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
        val range = requireNotNull(parseReleaseDateRange("Aug 2026"))

        assertEquals(LocalDate.of(2026, 8, 1), range.start)
        assertEquals(LocalDate.of(2026, 8, 31), range.endInclusive)
        assertTrue(range.isMonthOnly)
    }
}
