package com.ogautam.letters.ui.scenes.chat

import android.graphics.Color

/**
 * The WhatsApp visual spec from CLAUDE.md, in one place. Everything is in dp/sp; the
 * renderer multiplies by a density it is given rather than reading one from a Context,
 * so the same numbers produce the same picture on screen and in an exported frame.
 *
 * These values are a specification, not preferences. Changing one changes what the scene
 * looks like, and the whole point of this module is that it looks like a screenshot.
 */
object ChatTheme {

    // Surface
    const val BACKGROUND = 0xFFECE5DD.toInt()
    const val TILE_STROKE = 0xFFD4C9BD.toInt()
    const val TILE_SIZE_DP = 80f
    const val TILE_DIAGONAL_WIDTH_DP = 0.5f
    const val TILE_CROSS_WIDTH_DP = 0.3f

    // Header
    const val HEADER = 0xFF075E54.toInt()
    const val HEADER_HEIGHT_DP = 56f

    // Bubbles
    const val OUTGOING = 0xFFDCF8C6.toInt()
    const val INCOMING = Color.WHITE
    const val BUBBLE_TEXT = 0xFF111111.toInt()
    const val BUBBLE_MAX_WIDTH_DP = 280f
    const val BUBBLE_RADIUS_DP = 8f
    const val BUBBLE_TEXT_SIZE_SP = 14.5f
    const val BUBBLE_LINE_HEIGHT_RATIO = 1.4f

    /** CSS `padding: 6px 8px 6px 9px` — the left inset is a pixel wider than the right. */
    const val BUBBLE_PAD_TOP_DP = 6f
    const val BUBBLE_PAD_RIGHT_DP = 8f
    const val BUBBLE_PAD_BOTTOM_DP = 6f
    const val BUBBLE_PAD_LEFT_DP = 9f

    // Shadow: 0 1px 2px rgba(0,0,0,.13)
    const val SHADOW_COLOR = 0x21000000
    const val SHADOW_DY_DP = 1f
    const val SHADOW_RADIUS_DP = 2f

    // Meta row inside the bubble
    const val TIME_COLOR = 0xFF667781.toInt()
    const val TIME_SIZE_SP = 11f
    const val TICK_COLOR = 0xFF53BDEB.toInt()
    const val TICK_SIZE_SP = 13f
    const val TICKS = "✓✓"
    const val META_GAP_DP = 3f
    const val META_MARGIN_TOP_DP = 2f

    // Sender label
    const val SENDER_SIZE_SP = 12f
    const val SENDER_MARGIN_BOTTOM_DP = 2f
    const val SENDER_PADDING_LEFT_DP = 2f

    // Row geometry
    const val AVATAR_CHAT_DP = 28f
    const val AVATAR_GAP_DP = 6f
    const val ROW_PAD_HORIZONTAL_DP = 12f
    const val ROW_PAD_VERTICAL_DP = 2f
    const val LIST_PAD_VERTICAL_DP = 8f

    /** Initials are drawn at 38% of the avatar's diameter, as the web did. */
    const val AVATAR_INITIALS_RATIO = 0.38f

    // Typing indicator
    const val TYPING_DOT = 0xFF90A4AE.toInt()
    const val TYPING_DOT_DIAMETER_DP = 8f
    const val TYPING_DOT_GAP_DP = 4f
    const val TYPING_PAD_HORIZONTAL_DP = 14f
    const val TYPING_PAD_VERTICAL_DP = 10f
    const val TYPING_MARGIN_BOTTOM_DP = 8f

    /** One bounce per second, each dot a fifth of a second behind the one before it. */
    const val TYPING_BOUNCE_PERIOD_MS = 1_000L
    const val TYPING_BOUNCE_STAGGER_MS = 200L
    const val TYPING_BOUNCE_RISE_DP = 5f

    // Bubble pop-in: scale(0.7) → scale(1) over 250ms, cubic-bezier(0.34, 1.56, 0.64, 1)
    const val POP_DURATION_MS = 250L
    const val POP_FROM_SCALE = 0.7f

    /**
     * The input bar. It is drawn on every frame, not only while something is being typed
     * into it — a bar that appeared for one moment and left would read as a mistake.
     */
    const val INPUT_BAR_HEIGHT_DP = 56f
    const val INPUT_BAR_GROUND = 0xFFF0F0F0.toInt()
    const val INPUT_FIELD = Color.WHITE
    const val INPUT_FIELD_RADIUS_DP = 22f
    const val INPUT_PLACEHOLDER = 0xFF9AA0A6.toInt()
    const val INPUT_TEXT_SIZE_SP = 14.5f
    const val INPUT_PAD_HORIZONTAL_DP = 12f
    const val INPUT_PAD_VERTICAL_DP = 6f
    const val INPUT_FIELD_PAD_DP = 14f
    const val SEND_BUTTON_DIAMETER_DP = 44f
    const val SEND_BUTTON = HEADER

    /** The dashed edge on a message that was written and never sent. */
    const val GHOST_OUTLINE = 0xFF9AA0A6.toInt()

    /** The caret that blinks while words are being written or taken back. */
    const val CARET_BLINK_MS = 1_000L
    const val CARET_WIDTH_DP = 1.5f
}
