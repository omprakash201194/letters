package com.ogautam.letters.ui.scenes.chat

import com.ogautam.letters.data.entity.SceneMessageEntity
import kotlin.math.max

/** The four speeds from the web app. Every duration below is divided by this. */
enum class PlaySpeed(val factor: Float, val label: String) {
    HALF(0.5f, "0.5×"),
    NORMAL(1f, "1×"),
    ONE_AND_HALF(1.5f, "1.5×"),
    DOUBLE(2f, "2×");

    companion object {
        val DEFAULT = NORMAL
    }
}

/**
 * When one message happens, and what it does while it happens.
 *
 * A plain message types (as an indicator, if incoming) and then appears. A message with a
 * typewriter reveal appears at its final size and fills in. An unsent one never appears at
 * all: it is typed into the input bar, held, and taken back.
 */
data class TimelineStep(
    val index: Int,
    /** When the typing indicator starts, for incoming messages. */
    val typingFromMs: Long?,
    /** When the bubble appears. For an unsent message, when its erasure completes. */
    val bubbleAtMs: Long,
    /** When the bubble's text is fully revealed; equal to [bubbleAtMs] without a typewriter. */
    val revealDoneAtMs: Long,
    /** For an unsent message: when its words start appearing in the input bar. */
    val composeFromMs: Long? = null,
    /** For an unsent message: when they start being erased. */
    val eraseFromMs: Long? = null,
    val unsent: Boolean = false,
)

/** What the renderer needs to draw a single instant. */
data class PlaybackState(
    /** Bubbles on screen. Unsent messages never count towards this. */
    val visibleCount: Int,
    /**
     * How many messages have played, unsent ones included. What the progress bar counts —
     * a scene is not two thirds finished because one of its beats left nothing behind.
     */
    val playedCount: Int = visibleCount,
    /** Index of the message whose sender is typing, or null. */
    val typingIndex: Int?,
    /** Age of the newest visible bubble, which drives its pop-in. */
    val msSinceLastBubble: Long,
    /**
     * How much of the newest bubble's text has been typed, or null when it is all there.
     * Only the newest bubble can be mid-reveal.
     */
    val revealedChars: Int? = null,
    /**
     * What is sitting in the input bar right now — your own words, being written or taken
     * back. Never another character's: a chat does not show you what they are typing.
     */
    val composing: String? = null,
    /**
     * Whether those words are being taken back rather than written — which key is held
     * down, in other words.
     */
    val composeErasing: Boolean = false,
    /**
     * How far the on-screen keyboard has slid up, 0 to 1. It is up only while your own
     * unsent words are on the screen, because it is the reason they are there.
     */
    val keyboardFraction: Float = 0f,
)

/**
 * The whole playback as a function of time, rather than the web app's chain of
 * `setTimeout`s. The durations are identical; expressing them as absolute timestamps is
 * what lets the same playback be scrubbed, and — the reason it matters — sampled at fixed
 * frame intervals by the MP4 encoder. A chain of timeouts can only ever run forwards, in
 * real time, once.
 */
class PlaybackTimeline(
    private val messages: List<SceneMessageEntity>,
    private val speed: PlaySpeed,
) {

    val steps: List<TimelineStep>
    val totalMs: Long

    init {
        var t = INITIAL_KICK_MS
        val built = ArrayList<TimelineStep>(messages.size)

        messages.forEachIndexed { index, message ->
            t += message.delayBeforeMs?.let(::scaled) ?: 0L

            if (message.unsent) {
                built += unsentStep(index, message, from = t)
                t = built.last().bubbleAtMs + scaled(UNSENT_PAUSE_MS)
            } else if (message.outgoing) {
                val revealDone = t + revealDurationMs(message)
                built += TimelineStep(
                    index = index,
                    typingFromMs = null,
                    bubbleAtMs = t,
                    revealDoneAtMs = revealDone,
                )
                t = revealDone + scaled(OUTGOING_PAUSE_MS)
            } else {
                val typingFrom = t
                val bubbleAt = t + typingDurationMs(message)
                val revealDone = bubbleAt + revealDurationMs(message)
                built += TimelineStep(
                    index = index,
                    typingFromMs = typingFrom,
                    bubbleAtMs = bubbleAt,
                    revealDoneAtMs = revealDone,
                )
                t = revealDone + scaled(INCOMING_PAUSE_MS)
            }
        }

        steps = built
        totalMs = t
    }

    val messageCount: Int get() = steps.size

    /**
     * The instant at which exactly [index] messages have played — used by the progress bar,
     * which jumps to a message boundary rather than an arbitrary time.
     */
    fun timeAtIndex(index: Int): Long = when {
        index <= 0 -> 0L
        index >= steps.size -> totalMs
        else -> steps[index - 1].revealDoneAtMs
    }

    fun stateAt(timeMs: Long): PlaybackState {
        var visible = 0
        var played = 0
        var lastBubbleAt = 0L
        var revealedChars: Int? = null
        var typingIndex: Int? = null
        var composing: String? = null
        var erasing = false

        for (step in steps) {
            val message = messages[step.index]

            if (step.unsent) {
                // An unsent message is over once its words are gone; it leaves nothing behind.
                if (step.bubbleAtMs <= timeMs) {
                    played++
                    continue
                }
                composing = composingTextAt(step, message, timeMs)
                if (composing != null && !message.outgoing) composing = null
                erasing = composing != null && step.eraseFromMs != null && timeMs >= step.eraseFromMs
                if (step.typingFromMs != null && step.typingFromMs <= timeMs) {
                    typingIndex = step.index
                }
                break
            }

            if (step.bubbleAtMs <= timeMs) {
                visible++
                played++
                lastBubbleAt = step.bubbleAtMs
                revealedChars = revealedCharsAt(step, message, timeMs)
            } else {
                // reason: steps are in time order, so the first one still to come is the
                // only one that can be typing — nothing later can have started yet
                if (step.typingFromMs != null && step.typingFromMs <= timeMs) {
                    typingIndex = step.index
                }
                break
            }
        }

        return PlaybackState(
            visibleCount = visible,
            playedCount = played,
            typingIndex = typingIndex,
            msSinceLastBubble = if (visible == 0) 0L else timeMs - lastBubbleAt,
            revealedChars = revealedChars,
            composing = composing,
            composeErasing = erasing,
            keyboardFraction = keyboardFractionAt(timeMs),
        )
    }

    /**
     * How far the keyboard has slid up at this instant.
     *
     * It rises so as to be in place just before the first letter appears and falls once the
     * last one is gone: the keyboard is why the words are there, so it arrives with them and
     * leaves with them. Only your own unsent words raise it — you never see someone else's
     * keyboard, any more than you see their sentence.
     *
     * Computed over the whole step list rather than inside [stateAt]'s loop, which walks
     * past a finished unsent message: the keyboard is still on its way down then.
     */
    fun keyboardFractionAt(timeMs: Long): Float {
        val slide = scaled(ChatTheme.KEYBOARD_SLIDE_MS).coerceAtLeast(1L)
        var fraction = 0f
        for (step in steps) {
            if (!step.unsent || !messages[step.index].outgoing) continue
            val composeFrom = step.composeFromMs ?: continue
            val upFrom = composeFrom - slide
            val downTo = step.bubbleAtMs + slide
            if (timeMs < upFrom || timeMs >= downTo) continue
            val here = when {
                timeMs < composeFrom -> (timeMs - upFrom).toFloat() / slide
                timeMs < step.bubbleAtMs -> 1f
                else -> 1f - (timeMs - step.bubbleAtMs).toFloat() / slide
            }
            // reason: two unsent messages close together keep it up rather than flickering
            // it down and straight back.
            fraction = max(fraction, here)
        }
        return fraction.coerceIn(0f, 1f)
    }

    // ── phases ─────────────────────────────────────────────────────────────

    private fun unsentStep(
        index: Int,
        message: SceneMessageEntity,
        from: Long,
    ): TimelineStep {
        val typeDuration = revealDurationMs(message, fallbackPerChar = UNSENT_TYPE_PER_CHAR_MS)
        val eraseFrom = from + typeDuration + scaled(UNSENT_HOLD_MS)
        val goneAt = eraseFrom + eraseDurationMs(message)
        return TimelineStep(
            index = index,
            // The other side sees only three dots, and only while the words are being written.
            typingFromMs = if (message.outgoing) null else from,
            bubbleAtMs = goneAt,
            revealDoneAtMs = goneAt,
            composeFromMs = from,
            eraseFromMs = eraseFrom,
            unsent = true,
        )
    }

    /** How much of an unsent message is in the input bar at this instant. */
    private fun composingTextAt(
        step: TimelineStep,
        message: SceneMessageEntity,
        timeMs: Long,
    ): String? {
        val from = step.composeFromMs ?: return null
        val eraseFrom = step.eraseFromMs ?: return null
        if (timeMs < from) return null

        val typeDuration = revealDurationMs(message, fallbackPerChar = UNSENT_TYPE_PER_CHAR_MS)
        return when {
            timeMs < from + typeDuration -> {
                val fraction = (timeMs - from).toFloat() / typeDuration.coerceAtLeast(1)
                message.text.take((message.text.length * fraction).toInt())
            }

            timeMs < eraseFrom -> message.text

            else -> {
                val erased = eraseDurationMs(message).coerceAtLeast(1)
                val fraction = (timeMs - eraseFrom).toFloat() / erased
                message.text.take((message.text.length * (1f - fraction)).toInt().coerceAtLeast(0))
            }
        }
    }

    /** How much of a bubble's text has been typed, or null once it is all there. */
    private fun revealedCharsAt(
        step: TimelineStep,
        message: SceneMessageEntity,
        timeMs: Long,
    ): Int? {
        if (message.revealPerCharMs == null) return null
        if (timeMs >= step.revealDoneAtMs) return null
        val duration = (step.revealDoneAtMs - step.bubbleAtMs).coerceAtLeast(1)
        val fraction = (timeMs - step.bubbleAtMs).toFloat() / duration
        return (message.text.length * fraction).toInt().coerceIn(0, message.text.length)
    }

    private fun typingDurationMs(message: SceneMessageEntity): Long =
        message.typingMs?.let(::scaled)
            ?: scaled(TYPING_BASE_MS + message.text.length * TYPING_PER_CHAR_MS)

    private fun revealDurationMs(
        message: SceneMessageEntity,
        fallbackPerChar: Long? = null,
    ): Long {
        val perChar = message.revealPerCharMs ?: fallbackPerChar ?: return 0L
        return scaled(message.text.length * perChar)
    }

    private fun eraseDurationMs(message: SceneMessageEntity): Long {
        val perChar = message.revealPerCharMs ?: UNSENT_TYPE_PER_CHAR_MS
        // Taking words back is quicker than writing them, as holding backspace is.
        return scaled(message.text.length * perChar / ERASE_SPEEDUP)
    }

    private fun scaled(ms: Long): Long = (ms / speed.factor).toLong()

    companion object {
        /** Deliberately not divided by speed, matching the web app. */
        const val INITIAL_KICK_MS = 300L
        const val OUTGOING_PAUSE_MS = 600L
        const val INCOMING_PAUSE_MS = 400L
        const val TYPING_BASE_MS = 1_000L
        const val TYPING_PER_CHAR_MS = 30L

        /** Defaults for words that were typed and taken back. */
        const val UNSENT_TYPE_PER_CHAR_MS = 45L
        const val UNSENT_HOLD_MS = 1_200L
        const val UNSENT_PAUSE_MS = 700L
        const val ERASE_SPEEDUP = 3L
    }
}
