package com.dutu007.favorapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TogetherDaysTest {
    @Test
    fun startingDayCountsAsDayOne() {
        assertEquals(1L, togetherDays("2026-10-17", LocalDate.of(2026, 10, 17)))
        assertEquals(2L, togetherDays("2026-10-16", LocalDate.of(2026, 10, 17)))
    }

    @Test
    fun countsCalendarDaysAcrossLeapDaysAndYearBoundaries() {
        assertEquals(3L, togetherDays("2024-02-28", LocalDate.of(2024, 3, 1)))
        assertEquals(2L, togetherDays("2025-12-31", LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun daylightSavingChangesDoNotLoseACalendarDay() {
        val zone = ZoneId.of("America/Los_Angeles")
        val today = ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, zone).toLocalDate()
        assertEquals(2L, togetherDays("2026-03-08", today))
    }

    @Test
    fun hidesMissingInvalidOrFutureDates() {
        val today = LocalDate.of(2026, 10, 17)
        for (date in listOf(null, "", "2026-02-29", "2026-1-01", "0000-01-01", "-0001-01-01", "2026-10-18")) {
            assertNull("Unexpected day count for $date", togetherDays(date, today))
        }
    }

    @Test
    fun crossingMidnightIncrementsTheCount() {
        assertEquals(365L, togetherDays("2025-10-18", LocalDate.of(2026, 10, 17)))
        assertEquals(366L, togetherDays("2025-10-18", LocalDate.of(2026, 10, 18)))
    }
}
