package com.ogautam.letters.ui.scenes.chat

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ogautam.letters.data.entity.SceneMessageEntity
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One measured row, positioned relative to the top of the message list. */
data class BubbleLayout(
    val message: SceneMessageEntity,
    val showSender: Boolean,
    /** Left edge of the bubble itself, in px. */
    val bubbleLeft: Float,
    val bubbleTop: Float,
    val bubbleWidth: Float,
    val bubbleHeight: Float,
    val textLayout: StaticLayout,
    val timeText: String,
    val timeWidth: Float,
    /** Top of the whole row, including the sender label when there is one. */
    val rowTop: Float,
    val rowHeight: Float,
) {
    val bubbleRight: Float get() = bubbleLeft + bubbleWidth
    val bubbleBottom: Float get() = bubbleTop + bubbleHeight
    val centerX: Float get() = bubbleLeft + bubbleWidth / 2f
    val centerY: Float get() = bubbleTop + bubbleHeight / 2f
}

/** The measured transcript: every row placed, and the total height it occupies. */
data class ChatLayoutResult(
    val bubbles: List<BubbleLayout>,
    val contentHeight: Float,
)

/**
 * Measures the transcript. Kept apart from drawing because measurement is the part with
 * rules in it — the sender-label suppression, the reserved avatar column, the max bubble
 * width — and those are worth testing without a Canvas.
 */
class ChatLayout(
    private val widthPx: Float,
    private val density: Float,
    /**
     * sp is scaled by the same density as dp, on purpose: an exported video must not
     * change size because the phone's font-size setting changed.
     */
    private val metrics: ChatMetrics = ChatMetrics(density),
) {

    val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = metrics.bubbleTextSize
        color = ChatTheme.BUBBLE_TEXT
    }

    val timePaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = metrics.timeSize
        color = ChatTheme.TIME_COLOR
    }

    val tickPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = metrics.tickSize
        color = ChatTheme.TICK_COLOR
    }

    val senderPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = metrics.senderSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val tickWidth: Float = tickPaint.measureText(ChatTheme.TICKS)

    fun measure(messages: List<SceneMessageEntity>): ChatLayoutResult {
        var y = metrics.listPadVertical
        val laid = ArrayList<BubbleLayout>(messages.size)

        messages.forEachIndexed { index, message ->
            val showSender = showSenderLabel(messages, index)
            val rowTop = y

            val senderHeight = if (showSender && !message.outgoing) {
                metrics.senderLineHeight + metrics.senderMarginBottom
            } else {
                0f
            }

            val timeText = formatTime(message)
            val timeWidth = timePaint.measureText(timeText)
            val metaWidth = timeWidth +
                if (message.outgoing) metrics.metaGap + tickWidth else 0f

            val maxTextWidth = metrics.bubbleMaxWidth - metrics.bubblePadLeft - metrics.bubblePadRight
            val textLayout = buildTextLayout(message.text, maxTextWidth.toInt().coerceAtLeast(1))
            val widestLine = (0 until textLayout.lineCount)
                .maxOfOrNull(textLayout::getLineWidth) ?: 0f

            val contentWidth = maxOf(widestLine, metaWidth)
            val bubbleWidth = contentWidth + metrics.bubblePadLeft + metrics.bubblePadRight
            val bubbleHeight = metrics.bubblePadTop + textLayout.height +
                metrics.metaMarginTop + metrics.metaLineHeight + metrics.bubblePadBottom

            val bubbleLeft = if (message.outgoing) {
                widthPx - metrics.rowPadHorizontal - bubbleWidth
            } else {
                // reason: the avatar column stays reserved even when no avatar is drawn, so
                // consecutive messages from one person stay aligned with the first
                metrics.rowPadHorizontal + metrics.avatarSize + metrics.avatarGap
            }

            val bubbleTop = rowTop + metrics.rowPadVertical + senderHeight
            val rowHeight = metrics.rowPadVertical + senderHeight + bubbleHeight +
                metrics.rowPadVertical

            laid += BubbleLayout(
                message = message,
                showSender = showSender,
                bubbleLeft = bubbleLeft,
                bubbleTop = bubbleTop,
                bubbleWidth = bubbleWidth,
                bubbleHeight = bubbleHeight,
                textLayout = textLayout,
                timeText = timeText,
                timeWidth = timeWidth,
                rowTop = rowTop,
                rowHeight = rowHeight,
            )
            y += rowHeight
        }

        return ChatLayoutResult(laid, y + metrics.listPadVertical)
    }

    /** The height a typing indicator adds under the transcript. */
    fun typingRowHeight(): Float =
        metrics.typingBubbleHeight + metrics.typingMarginBottom

    private fun buildTextLayout(text: String, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            // reason: CSS line-height 1.4 is a multiple of the font size, not extra leading
            .setLineSpacing(0f, ChatTheme.BUBBLE_LINE_HEIGHT_RATIO)
            .setIncludePad(false)
            .build()

    private fun formatTime(message: SceneMessageEntity): String =
        TIME_FORMAT.format(message.time)

    companion object {
        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US)

        /**
         * Show the sender's name — and their avatar — only on the first message of a run.
         * One rule drives both, and outgoing messages never show either.
         */
        fun showSenderLabel(messages: List<SceneMessageEntity>, index: Int): Boolean {
            val message = messages[index]
            if (message.outgoing) return false
            if (index == 0) return true
            return messages[index - 1].charId != message.charId
        }
    }
}

/** Every dimension in the spec, converted to px once. */
class ChatMetrics(val density: Float) {
    private fun dp(value: Float) = value * density

    val bubbleTextSize = dp(ChatTheme.BUBBLE_TEXT_SIZE_SP)
    val timeSize = dp(ChatTheme.TIME_SIZE_SP)
    val tickSize = dp(ChatTheme.TICK_SIZE_SP)
    val senderSize = dp(ChatTheme.SENDER_SIZE_SP)

    val bubbleMaxWidth = dp(ChatTheme.BUBBLE_MAX_WIDTH_DP)
    val bubbleRadius = dp(ChatTheme.BUBBLE_RADIUS_DP)
    val bubblePadTop = dp(ChatTheme.BUBBLE_PAD_TOP_DP)
    val bubblePadRight = dp(ChatTheme.BUBBLE_PAD_RIGHT_DP)
    val bubblePadBottom = dp(ChatTheme.BUBBLE_PAD_BOTTOM_DP)
    val bubblePadLeft = dp(ChatTheme.BUBBLE_PAD_LEFT_DP)

    val metaGap = dp(ChatTheme.META_GAP_DP)
    val metaMarginTop = dp(ChatTheme.META_MARGIN_TOP_DP)
    val metaLineHeight = dp(ChatTheme.TICK_SIZE_SP)

    val senderLineHeight = dp(ChatTheme.SENDER_SIZE_SP * 1.3f)
    val senderMarginBottom = dp(ChatTheme.SENDER_MARGIN_BOTTOM_DP)
    val senderPaddingLeft = dp(ChatTheme.SENDER_PADDING_LEFT_DP)

    val avatarSize = dp(ChatTheme.AVATAR_CHAT_DP)
    val avatarGap = dp(ChatTheme.AVATAR_GAP_DP)
    val rowPadHorizontal = dp(ChatTheme.ROW_PAD_HORIZONTAL_DP)
    val rowPadVertical = dp(ChatTheme.ROW_PAD_VERTICAL_DP)
    val listPadVertical = dp(ChatTheme.LIST_PAD_VERTICAL_DP)

    val shadowDy = dp(ChatTheme.SHADOW_DY_DP)
    val shadowRadius = dp(ChatTheme.SHADOW_RADIUS_DP)

    val tileSize = dp(ChatTheme.TILE_SIZE_DP)
    val tileDiagonalWidth = dp(ChatTheme.TILE_DIAGONAL_WIDTH_DP)
    val tileCrossWidth = dp(ChatTheme.TILE_CROSS_WIDTH_DP)

    val typingDot = dp(ChatTheme.TYPING_DOT_DIAMETER_DP)
    val typingDotGap = dp(ChatTheme.TYPING_DOT_GAP_DP)
    val typingPadHorizontal = dp(ChatTheme.TYPING_PAD_HORIZONTAL_DP)
    val typingPadVertical = dp(ChatTheme.TYPING_PAD_VERTICAL_DP)
    val typingMarginBottom = dp(ChatTheme.TYPING_MARGIN_BOTTOM_DP)
    val typingBounceRise = dp(ChatTheme.TYPING_BOUNCE_RISE_DP)
    val typingBubbleWidth = typingPadHorizontal * 2 + typingDot * 3 + typingDotGap * 2
    val typingBubbleHeight = typingPadVertical * 2 + typingDot
}
