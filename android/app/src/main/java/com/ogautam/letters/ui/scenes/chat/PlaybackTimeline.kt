package com.ogautam.letters.ui.scenes.chat

import com.ogautam.letters.data.entity.SceneMessageEntity

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

/** When one message appears, and — if incoming — when its typing indicator starts. */
data class TimelineStep(
    val index: Int,
    val typingFromMs: Long?,
    val bubbleAtMs: Long,
)

/** What the renderer needs to draw a single instant. */
data class PlaybackState(
    val visibleCount: Int,
    /** Index of the message whose sender is typing, or null. */
    val typingIndex: Int?,
    /** Age of the newest visible bubble, which drives its pop-in. */
    val msSinceLastBubble: Long,
)

/**
 * The whole playback as a function of time, rather than the web app's chain of
 * `setTimeout`s. The durations are identical; expressing them as absolute timestamps is
 * what lets the same playback be scrubbed, and — the reason it matters — sampled at fixed
 * frame intervals by the MP4 encoder. A chain of timeouts can only ever run forwards, in
 * real time, once.
 */
class PlaybackTimeline(
    messages: List<SceneMessageEntity>,
    private val speed: PlaySpeed,
) {

    val steps: List<TimelineStep>
    val totalMs: Long

    init {
        var t = INITIAL_KICK_MS
        val built = ArrayList<TimelineStep>(messages.size)
        messages.forEachIndexed { index, message ->
            if (message.outgoing) {
                built += TimelineStep(index, typingFromMs = null, bubbleAtMs = t)
                t += scaled(OUTGOING_PAUSE_MS)
            } else {
                val typingFrom = t
                val bubbleAt = t + typingDurationMs(message.text)
                built += TimelineStep(index, typingFromMs = typingFrom, bubbleAtMs = bubbleAt)
                t = bubbleAt + scaled(INCOMING_PAUSE_MS)
            }
        }
        steps = built
        totalMs = t
    }

    val messageCount: Int get() = steps.size

    /**
     * The instant at which exactly [index] messages are on screen — used by the progress
     * bar, which jumps to a message boundary rather than an arbitrary time.
     */
    fun timeAtIndex(index: Int): Long = when {
        index <= 0 -> 0L
        index >= steps.size -> totalMs
        else -> steps[index - 1].bubbleAtMs
    }

    fun stateAt(timeMs: Long): PlaybackState {
        var visible = 0
        var lastBubbleAt = 0L
        var typingIndex: Int? = null

        for (step in steps) {
            if (step.bubbleAtMs <= timeMs) {
                visible++
                lastBubbleAt = step.bubbleAtMs
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
            typingIndex = typingIndex,
            msSinceLastBubble = if (visible == 0) 0L else timeMs - lastBubbleAt,
        )
    }

    private fun typingDurationMs(text: String): Long =
        scaled(TYPING_BASE_MS + text.length * TYPING_PER_CHAR_MS)

    private fun scaled(ms: Long): Long = (ms / speed.factor).toLong()

    companion object {
        /** Deliberately not divided by speed, matching the web app. */
        const val INITIAL_KICK_MS = 300L
        const val OUTGOING_PAUSE_MS = 600L
        const val INCOMING_PAUSE_MS = 400L
        const val TYPING_BASE_MS = 1_000L
        const val TYPING_PER_CHAR_MS = 30L
    }
}
