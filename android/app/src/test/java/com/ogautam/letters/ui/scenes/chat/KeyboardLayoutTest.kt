package com.ogautam.letters.ui.scenes.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard's geometry, which is arithmetic and so needs no device. What is worth
 * pinning is that every row fills the same width — a row a few pixels short of the one
 * above it is the sort of thing that only shows up in an exported video.
 */
class KeyboardLayoutTest {

    private val metrics = ChatMetrics(density = 2f)
    private val width = 720f
    private val layout = KeyboardLayout(width, metrics)

    private fun rows() = layout.keys.groupBy { it.top }.toSortedMap().values.toList()

    @Test
    fun `there are four rows`() {
        assertEquals(4, rows().size)
    }

    @Test
    fun `every full row spans exactly the width of the letters above it`() {
        val rows = rows()
        val left = rows.first().first().left
        val right = rows.first().last().right

        // Every row but the home one, which is deliberately inset.
        listOf(rows[0], rows[2], rows[3]).forEach { row ->
            assertEquals("row starting '${row.first().label}'", left, row.first().left, 0.5f)
            assertEquals("row ending '${row.last().label}'", right, row.last().right, 0.5f)
        }
    }

    @Test
    fun `the home row is inset by the same amount at both ends`() {
        val rows = rows()
        val home = rows[1]
        val leftInset = home.first().left - rows.first().first().left
        val rightInset = rows.first().last().right - home.last().right

        assertTrue("was $leftInset", leftInset > 0f)
        assertEquals(leftInset, rightInset, 0.5f)
    }

    @Test
    fun `keys in a row do not overlap and stay inside the keyboard`() {
        rows().forEach { row ->
            row.zipWithNext { a, b -> assertTrue("${a.label} into ${b.label}", a.right <= b.left) }
        }
        layout.keys.forEach { key ->
            assertTrue(key.left >= 0f && key.right <= width)
            assertTrue(key.bottom <= layout.height)
        }
    }

    @Test
    fun `the home row is inset by half a key`() {
        val (top, home) = rows()
        val unit = top.first().width
        assertEquals(top.first().left + (unit + metrics.keyGap) / 2f, home.first().left, 0.5f)
    }

    @Test
    fun `a letter finds its own key, whatever its case`() {
        assertEquals("q", layout.keyFor('q').label)
        assertSame(layout.keyFor('q'), layout.keyFor('Q'))
        assertEquals(",", layout.keyFor(',').label)
    }

    @Test
    fun `a space finds the space bar and anything unknown finds the symbol key`() {
        assertEquals(KeyKind.SPACE, layout.keyFor(' ').kind)
        assertEquals(KeyKind.SPACE, layout.keyFor('\n').kind)
        assertEquals(KeyKind.SYMBOLS, layout.keyFor('7').kind)
        assertEquals(KeyKind.SYMBOLS, layout.keyFor('!').kind)
    }
}
