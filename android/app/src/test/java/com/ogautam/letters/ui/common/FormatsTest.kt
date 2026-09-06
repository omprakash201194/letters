package com.ogautam.letters.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatsTest {

    @Test
    fun `dates use the fixed English month tables, not the device locale`() {
        val date = LocalDate.of(2026, 9, 6)
        assertEquals("Sep 6, 2026", formatShort(date))
        assertEquals("September 6, 2026", formatLong(date))
    }

    @Test
    fun `day and month are not zero padded`() {
        assertEquals("Jan 1, 2026", formatShort(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `word count ignores surrounding and repeated whitespace`() {
        assertEquals(0, countWords(""))
        assertEquals(0, countWords("   \n  "))
        assertEquals(1, countWords("  hello  "))
        assertEquals(3, countWords("I  understand\n now"))
    }
}
