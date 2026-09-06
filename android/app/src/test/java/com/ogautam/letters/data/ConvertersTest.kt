package com.ogautam.letters.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `local date round trips`() {
        val date = LocalDate.of(2026, 9, 6)
        assertEquals("2026-09-06", converters.localDateToString(date))
        assertEquals(date, converters.stringToLocalDate("2026-09-06"))
    }

    @Test
    fun `local time round trips and keeps minutes`() {
        val time = LocalTime.of(14, 32)
        assertEquals("14:32", converters.localTimeToString(time))
        assertEquals(time, converters.stringToLocalTime("14:32"))
    }

    @Test
    fun `instant round trips`() {
        val instant = Instant.parse("2026-09-06T10:15:30Z")
        assertEquals(instant, converters.longToInstant(converters.instantToLong(instant)))
    }

    @Test
    fun `nulls pass through`() {
        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
        assertNull(converters.longToInstant(null))
    }

    @Test
    fun `iso date strings sort chronologically`() {
        // reason: sealedUntil is range-queried as a string, so lexical order must match date order
        val dates = listOf("2026-12-01", "2026-01-15", "2027-03-09").sorted()
        assertEquals(listOf("2026-01-15", "2026-12-01", "2027-03-09"), dates)
    }
}
