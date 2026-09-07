package com.ogautam.letters.ui.scenes.chat

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.entity.SceneMessageEntity
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the chat surface onto a plain [Canvas].
 *
 * A plain Canvas rather than Compose layout, and a plain Canvas rather than a Composable,
 * because the MP4 encoder in the last phase draws into a Bitmap with no composition
 * running at all. One renderer, driven by a [PlaybackState] and an elapsed time, is what
 * makes the exported file identical to what played on screen.
 */
class ChatRenderer(
    private val widthPx: Float,
    density: Float,
    /** Resolves a message's avatar image; returns null to fall back to initials. */
    private val avatarFor: (SceneMessageEntity) -> Bitmap? = { null },
    /**
     * Whether to draw the input bar. On during playback and in the export, where unsent
     * words are typed into it; off in the composer, which has a real one of its own and
     * would otherwise show two.
     */
    private val showInputBar: Boolean = true,
    /**
     * Whether messages that were never sent are drawn, faintly, where they sit in the
     * transcript. On in the composer, so they can be found and edited; off in playback and
     * in the export, where they are not part of the conversation at all.
     */
    private val showUnsentGhosts: Boolean = false,
) {

    private val metrics = ChatMetrics(density)
    private val layout = ChatLayout(widthPx, density, metrics)
    private val keyboard = KeyboardLayout(widthPx, metrics)

    /** Every message, including unsent ones — a typing indicator may belong to one. */
    private var messages: List<SceneMessageEntity> = emptyList()
    private var measured: ChatLayoutResult = ChatLayoutResult(emptyList(), 0f)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ChatTheme.TILE_STROKE
    }
    private val avatarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.WHITE
        textSize = metrics.avatarSize * ChatTheme.AVATAR_INITIALS_RATIO
    }

    private val bubblePath = Path()
    private val revealPath = Path()
    private val inputPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ChatTheme.GHOST_OUTLINE
    }
    private val ghostDash = android.graphics.DashPathEffect(
        floatArrayOf(metrics.caretWidth * 4f, metrics.caretWidth * 3f),
        0f,
    )
    private val inputTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = metrics.inputTextSize
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = metrics.keyTextSize
        color = ChatTheme.KEY_TEXT
    }
    private val rect = RectF()
    private val radii = FloatArray(8)

    fun setMessages(value: List<SceneMessageEntity>) {
        if (value == messages) return
        messages = value
        // reason: a playback state counts only the bubbles that exist, so the laid-out list
        // has to match it. Measuring unsent messages there would shift every later bubble
        // by one and hide the last of them.
        measured = layout.measure(
            if (showUnsentGhosts) value else value.filterNot(SceneMessageEntity::unsent),
        )
    }

    /** How tall the transcript is at this instant, typing indicator included. */
    /**
     * The transcript's own height: the surface, less the input bar sitting under it and less
     * however much of the keyboard is up. The transcript really does give up that room — a
     * chat scrolls its last messages out of the way when the keyboard opens, and this scene
     * should too.
     */
    fun transcriptHeight(viewportHeight: Float, keyboardFraction: Float = 0f): Float {
        if (!showInputBar) return viewportHeight.coerceAtLeast(0f)
        val below = metrics.inputBarHeight + keyboard.height * keyboardFraction.coerceIn(0f, 1f)
        return (viewportHeight - below).coerceAtLeast(0f)
    }

    fun contentHeight(state: PlaybackState): Float {
        val bubbles = measured.bubbles
        val visible = state.visibleCount.coerceIn(0, bubbles.size)
        var height = if (visible == 0) {
            metrics.listPadVertical
        } else {
            val last = bubbles[visible - 1]
            last.rowTop + last.rowHeight
        }
        if (state.typingIndex != null) height += layout.typingRowHeight()
        return height + metrics.listPadVertical
    }

    /**
     * The index of the message drawn at this point, or null. The whole row is a target, not
     * just the bubble — a one-word bubble is a small thing to hit.
     */
    fun hitTest(x: Float, y: Float, scrollY: Float, visibleCount: Int): Int? {
        val contentY = y + scrollY
        return measured.bubbles
            .take(visibleCount.coerceIn(0, measured.bubbles.size))
            .indexOfFirst { contentY >= it.rowTop && contentY < it.rowTop + it.rowHeight }
            .takeIf { it >= 0 }
    }

    fun draw(
        canvas: Canvas,
        state: PlaybackState,
        elapsedMs: Long,
        scrollY: Float,
        viewportHeight: Float,
    ) {
        val transcriptHeight = transcriptHeight(viewportHeight, state.keyboardFraction)
        drawBackground(canvas, transcriptHeight)

        canvas.save()
        canvas.clipRect(0f, 0f, widthPx, transcriptHeight)
        canvas.translate(0f, -scrollY)

        val bubbles = measured.bubbles
        val visible = state.visibleCount.coerceIn(0, bubbles.size)
        for (index in 0 until visible) {
            val bubble = bubbles[index]
            val isNewest = index == visible - 1
            val popFraction = if (isNewest) {
                (state.msSinceLastBubble.toFloat() / ChatTheme.POP_DURATION_MS).coerceIn(0f, 1f)
            } else {
                1f
            }
            drawBubbleRow(
                canvas = canvas,
                bubble = bubble,
                popFraction = popFraction,
                revealedChars = if (isNewest) state.revealedChars else null,
            )
        }

        state.typingIndex?.let { typingIndex ->
            messages.getOrNull(typingIndex)?.let { message ->
                val top = if (visible == 0) {
                    metrics.listPadVertical
                } else {
                    bubbles[visible - 1].let { it.rowTop + it.rowHeight }
                }
                drawTypingIndicator(canvas, message, top, elapsedMs)
            }
        }

        canvas.restore()
        if (showInputBar) {
            drawInputBar(canvas, state.composing, transcriptHeight, elapsedMs)
            if (state.keyboardFraction > 0f) {
                drawKeyboard(
                    canvas = canvas,
                    top = transcriptHeight + metrics.inputBarHeight,
                    viewportHeight = viewportHeight,
                    state = state,
                )
            }
        }
    }

    // ── surface ────────────────────────────────────────────────────────────

    /**
     * The tile is drawn in screen space rather than with the content, matching a CSS
     * background on a scrolling element: the pattern stays put while the messages move.
     *
     * The surface is filled with an explicit rect rather than `drawColor`, and its extent
     * comes from the caller rather than the Canvas: a Compose draw scope is not clipped to
     * its node, so both would otherwise paint the whole window.
     */
    private fun drawBackground(canvas: Canvas, height: Float) {
        fillPaint.color = ChatTheme.BACKGROUND
        canvas.drawRect(0f, 0f, widthPx, height, fillPaint)

        val tile = metrics.tileSize
        var originY = 0f
        while (originY < height) {
            var originX = 0f
            while (originX < widthPx) {
                tilePaint.strokeWidth = metrics.tileDiagonalWidth
                canvas.drawLine(originX, originY, originX + tile, originY + tile, tilePaint)
                canvas.drawLine(originX + tile, originY, originX, originY + tile, tilePaint)

                tilePaint.strokeWidth = metrics.tileCrossWidth
                val midX = originX + tile / 2f
                val midY = originY + tile / 2f
                canvas.drawLine(midX, originY, midX, originY + tile, tilePaint)
                canvas.drawLine(originX, midY, originX + tile, midY, tilePaint)

                originX += tile
            }
            originY += tile
        }
    }

    // ── one message ────────────────────────────────────────────────────────

    private fun drawBubbleRow(
        canvas: Canvas,
        bubble: BubbleLayout,
        popFraction: Float,
        revealedChars: Int?,
    ) {
        val message = bubble.message
        // A ghost of something never sent: faint, and outlined rather than filled.
        val ghost = message.unsent

        // The avatar and the sender label are not animated — only the bubble pops.
        if (!message.outgoing && bubble.showSender) {
            drawAvatar(
                canvas = canvas,
                message = message,
                left = metrics.rowPadHorizontal,
                top = bubble.bubbleBottom - metrics.avatarSize,
            )
            layout.senderPaint.color = message.charColor
            canvas.drawText(
                message.charName,
                bubble.bubbleLeft + metrics.senderPaddingLeft,
                bubble.bubbleTop - metrics.senderMarginBottom -
                    layout.senderPaint.fontMetrics.descent,
                layout.senderPaint,
            )
        }

        canvas.save()
        if (popFraction < 1f) {
            val scale = ChatTheme.POP_FROM_SCALE +
                (1f - ChatTheme.POP_FROM_SCALE) * CubicBezierEasing.BUBBLE_POP(popFraction)
            canvas.scale(scale, scale, bubble.centerX, bubble.centerY)
        }
        var alpha = (255 * min(1f, popFraction / OPACITY_FRACTION)).toInt().coerceIn(0, 255)
        if (ghost) alpha = (alpha * GHOST_ALPHA).toInt()

        rect.set(bubble.bubbleLeft, bubble.bubbleTop, bubble.bubbleRight, bubble.bubbleBottom)
        setBubbleRadii(message.outgoing)
        bubblePath.reset()
        bubblePath.addRoundRect(rect, radii, Path.Direction.CW)

        fillPaint.color = if (message.outgoing) ChatTheme.OUTGOING else ChatTheme.INCOMING
        fillPaint.alpha = alpha
        // reason: a shadow layer is not scaled by the paint's alpha, so a bubble still
        // fading in would cast a full-strength shadow under nothing
        fillPaint.setShadowLayer(
            metrics.shadowRadius,
            0f,
            metrics.shadowDy,
            scaleAlpha(ChatTheme.SHADOW_COLOR, alpha),
        )
        canvas.drawPath(bubblePath, fillPaint)
        fillPaint.clearShadowLayer()
        fillPaint.alpha = 255

        if (ghost) drawGhostOutline(canvas)

        drawBubbleContent(canvas, bubble, alpha, revealedChars)
        canvas.restore()
    }

    private fun drawBubbleContent(
        canvas: Canvas,
        bubble: BubbleLayout,
        alpha: Int,
        revealedChars: Int?,
    ) {
        canvas.save()
        canvas.translate(
            bubble.bubbleLeft + metrics.bubblePadLeft,
            bubble.bubbleTop + metrics.bubblePadTop,
        )
        layout.textPaint.alpha = alpha
        if (revealedChars == null) {
            bubble.textLayout.draw(canvas)
        } else {
            // The bubble is already at its final size; only the letters arrive over time.
            val regions = layout.revealRegions(bubble, revealedChars)
            if (regions.isNotEmpty()) {
                canvas.save()
                revealPath.reset()
                regions.forEach { revealPath.addRect(it, Path.Direction.CW) }
                canvas.clipPath(revealPath)
                bubble.textLayout.draw(canvas)
                canvas.restore()
            }
        }
        layout.textPaint.alpha = 255
        canvas.restore()

        // The meta row waits until the words have finished arriving.
        if (revealedChars != null) return

        // The meta row hugs the bubble's right edge, under the text.
        val metaBaseline = bubble.bubbleBottom - metrics.bubblePadBottom -
            layout.timePaint.fontMetrics.descent
        var right = bubble.bubbleRight - metrics.bubblePadRight

        if (bubble.message.outgoing) {
            layout.tickPaint.alpha = alpha
            val tickWidth = layout.tickPaint.measureText(ChatTheme.TICKS)
            canvas.drawText(ChatTheme.TICKS, right - tickWidth, metaBaseline, layout.tickPaint)
            layout.tickPaint.alpha = 255
            right -= tickWidth + metrics.metaGap
        }

        layout.timePaint.alpha = alpha
        canvas.drawText(
            bubble.timeText,
            right - bubble.timeWidth,
            metaBaseline,
            layout.timePaint,
        )
        layout.timePaint.alpha = 255
    }

    private fun setBubbleRadii(outgoing: Boolean) {
        val r = metrics.bubbleRadius
        // Outgoing squares its bottom-right; incoming squares its bottom-left.
        radii[0] = r; radii[1] = r     // top-left
        radii[2] = r; radii[3] = r     // top-right
        radii[4] = if (outgoing) 0f else r; radii[5] = if (outgoing) 0f else r  // bottom-right
        radii[6] = if (outgoing) r else 0f; radii[7] = if (outgoing) r else 0f  // bottom-left
    }

    private fun drawAvatar(
        canvas: Canvas,
        message: SceneMessageEntity,
        left: Float,
        top: Float,
    ) {
        val size = metrics.avatarSize
        val radius = size / 2f
        val centerX = left + radius
        val centerY = top + radius

        val bitmap = avatarFor(message)
        if (bitmap != null) {
            val scale = size / min(bitmap.width, bitmap.height).toFloat()
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    left - (bitmap.width * scale - size) / 2f,
                    top - (bitmap.height * scale - size) / 2f,
                )
            }
            fillPaint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                .apply { setLocalMatrix(matrix) }
            canvas.drawCircle(centerX, centerY, radius, fillPaint)
            fillPaint.shader = null
            return
        }

        fillPaint.color = message.charColor
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        val fm = avatarTextPaint.fontMetrics
        canvas.drawText(
            CharacterPalette.initials(message.charName),
            centerX,
            centerY - (fm.ascent + fm.descent) / 2f,
            avatarTextPaint,
        )
    }

    // ── input bar ──────────────────────────────────────────────────────────

    /**
     * The input bar, drawn on every frame whether or not anything is being written into it.
     * It is where your own unsent words appear: typed out, held, and taken back, so the
     * video shows what you almost said. Nobody else's words ever appear here — a chat shows
     * you three dots and never the sentence behind them.
     */
    private fun drawInputBar(
        canvas: Canvas,
        composing: String?,
        top: Float,
        elapsedMs: Long,
    ) {
        val bottom = top + metrics.inputBarHeight
        inputPaint.color = ChatTheme.INPUT_BAR_GROUND
        canvas.drawRect(0f, top, widthPx, bottom, inputPaint)

        val sendRadius = metrics.sendButtonDiameter / 2f
        val sendCenterX = widthPx - metrics.inputPadHorizontal - sendRadius
        val fieldRight = sendCenterX - sendRadius - metrics.inputPadHorizontal

        rect.set(
            metrics.inputPadHorizontal,
            top + metrics.inputPadVertical,
            fieldRight,
            bottom - metrics.inputPadVertical,
        )
        inputPaint.color = ChatTheme.INPUT_FIELD
        inputPaint.setShadowLayer(metrics.shadowRadius, 0f, metrics.shadowDy, ChatTheme.SHADOW_COLOR)
        canvas.drawRoundRect(rect, metrics.inputFieldRadius, metrics.inputFieldRadius, inputPaint)
        inputPaint.clearShadowLayer()

        val hasText = !composing.isNullOrEmpty()
        inputTextPaint.color =
            if (hasText) ChatTheme.BUBBLE_TEXT else ChatTheme.INPUT_PLACEHOLDER
        val text = if (hasText) composing!! else PLACEHOLDER
        val textLeft = rect.left + metrics.inputFieldPad
        val baseline = rect.centerY() -
            (inputTextPaint.fontMetrics.ascent + inputTextPaint.fontMetrics.descent) / 2f

        // A long draft runs off the end of the field, as it would while you were writing it.
        val available = rect.right - metrics.inputFieldPad - textLeft
        val shown = trimToWidth(text, available)
        canvas.drawText(shown, textLeft, baseline, inputTextPaint)

        if (hasText) {
            val caretX = textLeft + inputTextPaint.measureText(shown)
            if (Math.floorMod(elapsedMs, ChatTheme.CARET_BLINK_MS) < ChatTheme.CARET_BLINK_MS / 2) {
                inputPaint.color = ChatTheme.HEADER
                canvas.drawRect(
                    caretX,
                    baseline + inputTextPaint.fontMetrics.ascent,
                    caretX + metrics.caretWidth,
                    baseline + inputTextPaint.fontMetrics.descent,
                    inputPaint,
                )
            }
        }

        inputPaint.color = ChatTheme.SEND_BUTTON
        canvas.drawCircle(sendCenterX, (top + bottom) / 2f, sendRadius, inputPaint)
        drawSendGlyph(canvas, sendCenterX, (top + bottom) / 2f, sendRadius)
    }

    /** Keeps the tail of the text visible, the way a real field scrolls as you write. */
    private fun trimToWidth(text: String, available: Float): String {
        if (available <= 0f) return ""
        if (inputTextPaint.measureText(text) <= available) return text
        var start = 0
        while (start < text.length &&
            inputTextPaint.measureText(text, start, text.length) > available
        ) {
            start++
        }
        return text.substring(start)
    }

    /** A paper-plane triangle, rather than shipping an icon font for one glyph. */
    private fun drawSendGlyph(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val arm = radius * 0.42f
        bubblePath.reset()
        bubblePath.moveTo(centerX - arm, centerY - arm)
        bubblePath.lineTo(centerX + arm, centerY)
        bubblePath.lineTo(centerX - arm, centerY + arm)
        bubblePath.lineTo(centerX - arm * 0.45f, centerY)
        bubblePath.close()
        inputPaint.color = android.graphics.Color.WHITE
        canvas.drawPath(bubblePath, inputPaint)
    }

    // ── keyboard ───────────────────────────────────────────────────────────

    /**
     * The keyboard, drawn at full height from wherever its top has slid to, so the part of
     * it that has not arrived yet is simply below the screen.
     */
    private fun drawKeyboard(
        canvas: Canvas,
        top: Float,
        viewportHeight: Float,
        state: PlaybackState,
    ) {
        canvas.save()
        // reason: a Compose draw scope is not clipped to its node, so without this the rows
        // still below the bottom of the screen paint over whatever sits under the canvas
        canvas.clipRect(0f, top, widthPx, viewportHeight)
        canvas.translate(0f, top)

        keyPaint.color = ChatTheme.KEYBOARD_GROUND
        canvas.drawRect(0f, 0f, widthPx, keyboard.height, keyPaint)

        val pressed = pressedKey(state)
        for (key in keyboard.keys) {
            rect.set(key.left, key.top, key.right, key.bottom)
            keyPaint.color = when {
                key === pressed -> ChatTheme.KEY_FACE_PRESSED
                key.kind == KeyKind.RETURN -> ChatTheme.KEY_RETURN
                key.kind == KeyKind.SHIFT ||
                    key.kind == KeyKind.BACKSPACE ||
                    key.kind == KeyKind.SYMBOLS -> ChatTheme.KEY_FACE_MUTED
                else -> ChatTheme.KEY_FACE
            }
            canvas.drawRoundRect(rect, metrics.keyRadius, metrics.keyRadius, keyPaint)
            drawKeyFace(canvas, key)
        }

        canvas.restore()
    }

    /**
     * The key under the finger: the letter that has just appeared, or backspace while the
     * words are going. Nothing while the bar is empty — a key held down with nothing
     * happening reads as a frozen frame rather than as typing.
     */
    private fun pressedKey(state: PlaybackState): KeyLayout? {
        if (state.composeErasing) return keyboard.backspace
        val last = state.composing?.lastOrNull() ?: return null
        return keyboard.keyFor(last)
    }

    private fun drawKeyFace(canvas: Canvas, key: KeyLayout) {
        when (key.kind) {
            KeyKind.SPACE -> Unit
            KeyKind.SHIFT -> drawShiftGlyph(canvas, key)
            KeyKind.BACKSPACE -> drawBackspaceGlyph(canvas, key)
            KeyKind.RETURN -> drawReturnGlyph(canvas, key)
            else -> {
                keyTextPaint.textSize =
                    if (key.kind == KeyKind.SYMBOLS) metrics.keyTextSize * 0.72f
                    else metrics.keyTextSize
                val fm = keyTextPaint.fontMetrics
                canvas.drawText(
                    key.label,
                    key.centerX,
                    key.centerY - (fm.ascent + fm.descent) / 2f,
                    keyTextPaint,
                )
            }
        }
    }

    // The three glyphs are drawn rather than typed. ⇧ ⌫ ⏎ are not in every system font, and
    // a missing-glyph box in an exported video is not something the viewer can be told about.

    private fun drawShiftGlyph(canvas: Canvas, key: KeyLayout) {
        val arm = key.height * GLYPH_ARM
        bubblePath.reset()
        bubblePath.moveTo(key.centerX, key.centerY - arm * 1.3f)
        bubblePath.lineTo(key.centerX + arm, key.centerY)
        bubblePath.lineTo(key.centerX + arm * 0.42f, key.centerY)
        bubblePath.lineTo(key.centerX + arm * 0.42f, key.centerY + arm)
        bubblePath.lineTo(key.centerX - arm * 0.42f, key.centerY + arm)
        bubblePath.lineTo(key.centerX - arm * 0.42f, key.centerY)
        bubblePath.lineTo(key.centerX - arm, key.centerY)
        bubblePath.close()
        keyPaint.color = ChatTheme.KEY_GLYPH
        canvas.drawPath(bubblePath, keyPaint)
    }

    private fun drawBackspaceGlyph(canvas: Canvas, key: KeyLayout) {
        val arm = key.height * GLYPH_ARM
        bubblePath.reset()
        bubblePath.moveTo(key.centerX - arm * 1.5f, key.centerY)
        bubblePath.lineTo(key.centerX - arm * 0.4f, key.centerY - arm * 0.85f)
        bubblePath.lineTo(key.centerX + arm * 1.4f, key.centerY - arm * 0.85f)
        bubblePath.lineTo(key.centerX + arm * 1.4f, key.centerY + arm * 0.85f)
        bubblePath.lineTo(key.centerX - arm * 0.4f, key.centerY + arm * 0.85f)
        bubblePath.close()
        keyPaint.color = ChatTheme.KEY_GLYPH
        canvas.drawPath(bubblePath, keyPaint)
    }

    private fun drawReturnGlyph(canvas: Canvas, key: KeyLayout) {
        val arm = key.height * GLYPH_ARM
        keyPaint.color = android.graphics.Color.WHITE
        keyPaint.style = Paint.Style.STROKE
        keyPaint.strokeWidth = metrics.caretWidth * 1.4f
        canvas.drawLine(key.centerX + arm, key.centerY - arm, key.centerX + arm, key.centerY, keyPaint)
        canvas.drawLine(key.centerX + arm, key.centerY, key.centerX - arm * 0.5f, key.centerY, keyPaint)
        keyPaint.style = Paint.Style.FILL
        bubblePath.reset()
        bubblePath.moveTo(key.centerX - arm, key.centerY)
        bubblePath.lineTo(key.centerX - arm * 0.35f, key.centerY - arm * 0.55f)
        bubblePath.lineTo(key.centerX - arm * 0.35f, key.centerY + arm * 0.55f)
        bubblePath.close()
        canvas.drawPath(bubblePath, keyPaint)
    }

    // ── typing ─────────────────────────────────────────────────────────────

    private fun drawTypingIndicator(
        canvas: Canvas,
        message: SceneMessageEntity,
        top: Float,
        elapsedMs: Long,
    ) {
        val bubbleLeft = metrics.rowPadHorizontal + metrics.avatarSize + metrics.avatarGap
        val bubbleTop = top
        val bubbleBottom = bubbleTop + metrics.typingBubbleHeight

        drawAvatar(canvas, message, metrics.rowPadHorizontal, bubbleBottom - metrics.avatarSize)

        rect.set(
            bubbleLeft,
            bubbleTop,
            bubbleLeft + metrics.typingBubbleWidth,
            bubbleBottom,
        )
        setBubbleRadii(outgoing = false)
        bubblePath.reset()
        bubblePath.addRoundRect(rect, radii, Path.Direction.CW)
        fillPaint.color = ChatTheme.INCOMING
        fillPaint.setShadowLayer(metrics.shadowRadius, 0f, metrics.shadowDy, ChatTheme.SHADOW_COLOR)
        canvas.drawPath(bubblePath, fillPaint)
        fillPaint.clearShadowLayer()

        fillPaint.color = ChatTheme.TYPING_DOT
        val radius = metrics.typingDot / 2f
        val centerY = bubbleTop + metrics.typingPadVertical + radius
        repeat(3) { i ->
            val centerX = bubbleLeft + metrics.typingPadHorizontal + radius +
                i * (metrics.typingDot + metrics.typingDotGap)
            canvas.drawCircle(centerX, centerY - dotRise(elapsedMs, i), radius, fillPaint)
        }
    }

    /**
     * The dot bounce: up over the first 30% of a one-second loop, back down by 60%, still
     * for the rest, each dot a fifth of a second behind the last.
     */
    private fun dotRise(elapsedMs: Long, dotIndex: Int): Float {
        val period = ChatTheme.TYPING_BOUNCE_PERIOD_MS
        val shifted = elapsedMs - dotIndex * ChatTheme.TYPING_BOUNCE_STAGGER_MS
        val phase = Math.floorMod(shifted, period) / period.toFloat()
        val eased = when {
            phase <= RISE_END -> CubicBezierEasing.EASE_IN_OUT(phase / RISE_END)
            phase <= FALL_END -> 1f - CubicBezierEasing.EASE_IN_OUT((phase - RISE_END) / (FALL_END - RISE_END))
            else -> 0f
        }
        return max(0f, eased) * metrics.typingBounceRise
    }

    /** A dashed edge, so a message that was never sent cannot be mistaken for one that was. */
    private fun drawGhostOutline(canvas: Canvas) {
        ghostPaint.strokeWidth = metrics.caretWidth
        ghostPaint.pathEffect = ghostDash
        canvas.drawPath(bubblePath, ghostPaint)
    }

    private fun scaleAlpha(color: Int, alpha: Int): Int {
        val scaled = (android.graphics.Color.alpha(color) * alpha / 255).coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaled shl 24)
    }

    companion object {
        private const val PLACEHOLDER = "Message"

        /** How faint a never-sent message is drawn while composing. */
        private const val GHOST_ALPHA = 0.4f

        /** The bubble reaches full opacity in the first third of its pop. */
        private const val OPACITY_FRACTION = 0.33f
        private const val RISE_END = 0.3f
        private const val FALL_END = 0.6f

        /** Half-size of the drawn key glyphs, as a fraction of a key's height. */
        private const val GLYPH_ARM = 0.17f
    }
}
