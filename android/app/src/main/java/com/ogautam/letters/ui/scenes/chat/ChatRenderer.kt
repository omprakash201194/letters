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
) {

    private val metrics = ChatMetrics(density)
    private val layout = ChatLayout(widthPx, density, metrics)

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
    private val rect = RectF()
    private val radii = FloatArray(8)

    fun setMessages(value: List<SceneMessageEntity>) {
        if (value == messages) return
        messages = value
        measured = layout.measure(value)
    }

    /** How tall the transcript is at this instant, typing indicator included. */
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
        drawBackground(canvas, viewportHeight)

        canvas.save()
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
            drawBubbleRow(canvas, bubble, popFraction)
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

    private fun drawBubbleRow(canvas: Canvas, bubble: BubbleLayout, popFraction: Float) {
        val message = bubble.message

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
        val alpha = (255 * min(1f, popFraction / OPACITY_FRACTION)).toInt().coerceIn(0, 255)

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

        drawBubbleContent(canvas, bubble, alpha)
        canvas.restore()
    }

    private fun drawBubbleContent(canvas: Canvas, bubble: BubbleLayout, alpha: Int) {
        canvas.save()
        canvas.translate(
            bubble.bubbleLeft + metrics.bubblePadLeft,
            bubble.bubbleTop + metrics.bubblePadTop,
        )
        layout.textPaint.alpha = alpha
        bubble.textLayout.draw(canvas)
        layout.textPaint.alpha = 255
        canvas.restore()

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

    private fun scaleAlpha(color: Int, alpha: Int): Int {
        val scaled = (android.graphics.Color.alpha(color) * alpha / 255).coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaled shl 24)
    }

    companion object {
        /** The bubble reaches full opacity in the first third of its pop. */
        private const val OPACITY_FRACTION = 0.33f
        private const val RISE_END = 0.3f
        private const val FALL_END = 0.6f
    }
}
