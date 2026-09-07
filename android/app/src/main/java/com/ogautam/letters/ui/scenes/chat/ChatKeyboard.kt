package com.ogautam.letters.ui.scenes.chat

/** What a key does, which is what decides how it is drawn. */
enum class KeyKind { LETTER, PUNCTUATION, SPACE, SHIFT, BACKSPACE, SYMBOLS, RETURN }

/** One key, placed relative to the top-left of the keyboard. */
data class KeyLayout(
    val label: String,
    val kind: KeyKind,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Where every key sits, and which key a character presses.
 *
 * Kept apart from drawing for the same reason [ChatLayout] is: the part with rules in it —
 * rows that fill the width exactly whatever the width is, a character finding its key — is
 * worth testing without a Canvas. Plain floats rather than `RectF` so those tests need no
 * Android runtime at all.
 */
class KeyboardLayout(
    private val widthPx: Float,
    private val metrics: ChatMetrics,
) {

    val keys: List<KeyLayout>

    /** The keyboard's full height — what the transcript gives up while it is up. */
    val height: Float

    private val byChar: Map<Char, KeyLayout>

    init {
        val gap = metrics.keyGap
        val pad = metrics.keyboardPadHorizontal
        // Ten keys and nine gaps across the top row set the unit every other row is built
        // from, so a row is never a few pixels short of the one above it.
        val unit = (widthPx - pad * 2 - gap * 9) / 10f
        val wide = unit * 1.5f + gap / 2f

        val built = ArrayList<KeyLayout>(40)
        var top = metrics.keyboardPadTop

        top = row(built, top, pad, TOP_ROW.map { it.toString() to KeyKind.LETTER }, List(10) { unit })
        // The home row is inset by half a key, as every phone keyboard insets it.
        top = row(
            built,
            top,
            pad + (unit + gap) / 2f,
            HOME_ROW.map { it.toString() to KeyKind.LETTER },
            List(9) { unit },
        )
        top = row(
            built,
            top,
            pad,
            listOf("" to KeyKind.SHIFT) +
                BOTTOM_ROW.map { it.toString() to KeyKind.LETTER } +
                listOf("" to KeyKind.BACKSPACE),
            listOf(wide) + List(7) { unit } + listOf(wide),
        )
        top = row(
            built,
            top,
            pad,
            listOf(
                "?123" to KeyKind.SYMBOLS,
                "," to KeyKind.PUNCTUATION,
                "" to KeyKind.SPACE,
                "." to KeyKind.PUNCTUATION,
                "" to KeyKind.RETURN,
            ),
            listOf(unit * 1.5f, unit, unit * 5f + gap * 5f, unit, unit * 1.5f),
        )

        keys = built
        height = top - gap + metrics.keyboardPadBottom

        byChar = keys
            .filter { it.kind == KeyKind.LETTER || it.kind == KeyKind.PUNCTUATION }
            .associateBy { it.label.first() }
    }

    val backspace: KeyLayout = keys.first { it.kind == KeyKind.BACKSPACE }
    private val space: KeyLayout = keys.first { it.kind == KeyKind.SPACE }
    private val symbols: KeyLayout = keys.first { it.kind == KeyKind.SYMBOLS }

    /**
     * The key this character was typed on. A capital finds its own letter — the shift that
     * would really have preceded it is a keystroke the timeline does not model. Anything
     * this layout has no key for is a trip to the symbol page, which is where it would be.
     */
    fun keyFor(char: Char): KeyLayout = when {
        char.isWhitespace() -> space
        else -> byChar[char.lowercaseChar()] ?: symbols
    }

    /** Places one row and returns the top of the next. */
    private fun row(
        into: MutableList<KeyLayout>,
        top: Float,
        startLeft: Float,
        keys: List<Pair<String, KeyKind>>,
        widths: List<Float>,
    ): Float {
        var left = startLeft
        keys.forEachIndexed { index, (label, kind) ->
            val width = widths[index]
            into += KeyLayout(label, kind, left, top, left + width, top + metrics.keyHeight)
            left += width + metrics.keyGap
        }
        return top + metrics.keyHeight + metrics.keyGap
    }

    private companion object {
        const val TOP_ROW = "qwertyuiop"
        const val HOME_ROW = "asdfghjkl"
        const val BOTTOM_ROW = "zxcvbnm"
    }
}
