package com.ogautam.letters.ui.scenes.chat

/**
 * A CSS `cubic-bezier(x1, y1, x2, y2)` timing function. The animations in the visual spec
 * are written as CSS curves — including one that overshoots — and approximating them with
 * whatever easing is nearest to hand would change how the scene moves.
 */
class CubicBezierEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float,
) {

    operator fun invoke(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        return valueY(solveT(fraction))
    }

    private fun valueX(t: Float): Float = bezier(t, x1, x2)

    private fun valueY(t: Float): Float = bezier(t, y1, y2)

    private fun bezier(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
    }

    /** Newton's method, falling back to bisection when the derivative is unhelpful. */
    private fun solveT(x: Float): Float {
        var t = x
        repeat(NEWTON_ITERATIONS) {
            val error = valueX(t) - x
            if (kotlin.math.abs(error) < EPSILON) return t
            val slope = derivativeX(t)
            if (kotlin.math.abs(slope) < EPSILON) return@repeat
            t -= error / slope
        }

        var low = 0f
        var high = 1f
        t = x
        repeat(BISECTION_ITERATIONS) {
            val error = valueX(t) - x
            if (kotlin.math.abs(error) < EPSILON) return t
            if (error > 0f) high = t else low = t
            t = (low + high) / 2f
        }
        return t
    }

    private fun derivativeX(t: Float): Float {
        val u = 1f - t
        return 3f * u * u * x1 + 6f * u * t * (x2 - x1) + 3f * t * t * (1f - x2)
    }

    companion object {
        private const val NEWTON_ITERATIONS = 8
        private const val BISECTION_ITERATIONS = 20
        private const val EPSILON = 1e-5f

        /** The bubble's pop-in, which overshoots past 1 before settling. */
        val BUBBLE_POP = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

        /** CSS `ease-in-out`, which the typing dots bounce on. */
        val EASE_IN_OUT = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    }
}
