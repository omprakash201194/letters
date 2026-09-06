package com.ogautam.letters.ui.scenes.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasingTest {

    @Test
    fun `every curve is pinned at both ends`() {
        listOf(CubicBezierEasing.BUBBLE_POP, CubicBezierEasing.EASE_IN_OUT).forEach { easing ->
            assertEquals(0f, easing(0f), 1e-4f)
            assertEquals(1f, easing(1f), 1e-4f)
            assertEquals(0f, easing(-1f), 1e-4f)
            assertEquals(1f, easing(2f), 1e-4f)
        }
    }

    @Test
    fun `the bubble pop overshoots past its target before settling`() {
        // cubic-bezier(0.34, 1.56, 0.64, 1) is what makes the bubble spring rather than slide.
        val peak = (0..100).maxOf { CubicBezierEasing.BUBBLE_POP(it / 100f) }
        assertTrue("peak was $peak", peak > 1f)
    }

    @Test
    fun `ease-in-out is symmetric about its midpoint`() {
        assertEquals(0.5f, CubicBezierEasing.EASE_IN_OUT(0.5f), 1e-3f)
        for (i in 1..9) {
            val f = i / 10f
            assertEquals(
                1f - CubicBezierEasing.EASE_IN_OUT(f),
                CubicBezierEasing.EASE_IN_OUT(1f - f),
                1e-3f,
            )
        }
    }
}
