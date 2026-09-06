package com.ogautam.letters.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

class DailyPromptTest {

    @Test
    fun `there are twenty prompts`() {
        assertEquals(20, DailyPrompt.PROMPTS.size)
    }

    @Test
    fun `the prompt is stable across a UTC day and changes at midnight`() {
        val justAfterMidnight = Instant.parse("2026-09-06T00:00:01Z")
        val justBeforeMidnight = Instant.parse("2026-09-06T23:59:59Z")
        val nextDay = Instant.parse("2026-09-07T00:00:01Z")

        assertEquals(
            DailyPrompt.forInstant(justAfterMidnight),
            DailyPrompt.forInstant(justBeforeMidnight),
        )
        assertNotEquals(
            DailyPrompt.forInstant(justBeforeMidnight),
            DailyPrompt.forInstant(nextDay),
        )
    }

    @Test
    fun `the cycle repeats every twenty days`() {
        val day = Instant.parse("2026-09-06T12:00:00Z")
        assertEquals(
            DailyPrompt.forInstant(day),
            DailyPrompt.forInstant(day.plusSeconds(20 * 86_400L)),
        )
    }

    @Test
    fun `the index matches whole days since the epoch`() {
        // 2026-09-06 is day 20702 since the epoch; 20702 % 20 == 2
        val prompt = DailyPrompt.forInstant(Instant.parse("2026-09-06T10:15:30Z"))
        assertEquals(DailyPrompt.PROMPTS[20702 % 20], prompt)
    }
}
