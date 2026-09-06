package com.ogautam.letters.data

import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.entity.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterPaletteTest {

    @Test
    fun `palette matches the web app order`() {
        assertEquals(8, CharacterPalette.COLORS.size)
        assertEquals(0xFFE91E63.toInt(), CharacterPalette.colorForIndex(0))
        assertEquals(0xFF607D8B.toInt(), CharacterPalette.colorForIndex(7))
    }

    @Test
    fun `palette cycles every eight characters`() {
        assertEquals(CharacterPalette.colorForIndex(0), CharacterPalette.colorForIndex(8))
        assertEquals(CharacterPalette.colorForIndex(3), CharacterPalette.colorForIndex(11))
    }

    @Test
    fun `initials take up to two uppercase letters`() {
        assertEquals("P", CharacterPalette.initials("priya"))
        assertEquals("PS", CharacterPalette.initials("priya sharma"))
        assertEquals("PS", CharacterPalette.initials("  Priya   Sharma  Nair "))
    }

    @Test
    fun `mood slugs resolve and unknown slugs do not throw`() {
        assertEquals(Mood.NOSTALGIC, Mood.fromSlug("nostalgic"))
        assertEquals("🕊️", Mood.LONELY.emoji)
        assertEquals(8, Mood.entries.size)
        assertNull(Mood.fromSlug("elated"))
        assertNull(Mood.fromSlug(null))
    }
}
