package com.ogautam.letters.ui.scenes.chat

import com.ogautam.letters.data.entity.SceneMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PlaybackTimelineTest {

    private fun message(text: String, outgoing: Boolean, index: Int) = SceneMessageEntity(
        id = "m$index",
        sceneId = "s",
        charId = if (outgoing) "you" else "them",
        charName = if (outgoing) "You" else "Them",
        charColor = 0,
        text = text,
        time = LocalTime.NOON,
        outgoing = outgoing,
        orderIndex = index,
    )

    /** "ab" — two characters, so typing runs 1000 + 2*30 = 1060ms. */
    private val twoChars = "ab"

    @Test
    fun `an outgoing message appears after the initial kick and holds for 600ms`() {
        val timeline = PlaybackTimeline(listOf(message("hi", outgoing = true, 0)), PlaySpeed.NORMAL)

        assertEquals(300L, timeline.steps.single().bubbleAtMs)
        assertNull(timeline.steps.single().typingFromMs)
        assertEquals(900L, timeline.totalMs)
    }

    @Test
    fun `an incoming message types for 1000ms plus 30ms a character`() {
        val timeline = PlaybackTimeline(listOf(message(twoChars, outgoing = false, 0)), PlaySpeed.NORMAL)

        val step = timeline.steps.single()
        assertEquals(300L, step.typingFromMs)
        assertEquals(300L + 1_060L, step.bubbleAtMs)
        assertEquals(300L + 1_060L + 400L, timeline.totalMs)
    }

    @Test
    fun `speed divides every duration except the initial kick`() {
        val messages = listOf(message(twoChars, outgoing = false, 0))
        val double = PlaybackTimeline(messages, PlaySpeed.DOUBLE)

        assertEquals(300L, double.steps.single().typingFromMs)
        assertEquals(300L + 530L, double.steps.single().bubbleAtMs)
        assertEquals(300L + 530L + 200L, double.totalMs)
    }

    @Test
    fun `state reports what is on screen at an instant`() {
        val timeline = PlaybackTimeline(
            listOf(
                message("hi", outgoing = true, 0),
                message(twoChars, outgoing = false, 1),
            ),
            PlaySpeed.NORMAL,
        )

        // Before anything: empty.
        assertEquals(0, timeline.stateAt(0).visibleCount)
        assertNull(timeline.stateAt(0).typingIndex)

        // The outgoing bubble is up, and the reply has not started typing.
        assertEquals(1, timeline.stateAt(350).visibleCount)
        assertNull(timeline.stateAt(350).typingIndex)

        // Typing, with the first bubble still the newest.
        val typing = timeline.stateAt(1_200)
        assertEquals(1, typing.visibleCount)
        assertEquals(1, typing.typingIndex)

        // Both up, typing done.
        val done = timeline.stateAt(timeline.totalMs)
        assertEquals(2, done.visibleCount)
        assertNull(done.typingIndex)
    }

    @Test
    fun `the newest bubble's age drives its pop-in`() {
        val timeline = PlaybackTimeline(listOf(message("hi", outgoing = true, 0)), PlaySpeed.NORMAL)

        assertEquals(0L, timeline.stateAt(300).msSinceLastBubble)
        assertEquals(120L, timeline.stateAt(420).msSinceLastBubble)
    }

    @Test
    fun `seeking to an index shows exactly that many messages and no typing`() {
        val messages = List(4) { message("m$it", outgoing = it % 2 == 0, it) }
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        for (index in 0..messages.size) {
            val state = timeline.stateAt(timeline.timeAtIndex(index))
            assertEquals("seek to $index", index, state.visibleCount)
            if (index < messages.size) assertNull("seek to $index", state.typingIndex)
        }
    }

    // ── the per-message controls ───────────────────────────────────────────

    @Test
    fun `a per-message delay is added before that message and no other`() {
        val messages = listOf(
            message("hi", outgoing = true, 0),
            message("again", outgoing = true, 1).copy(delayBeforeMs = 2_000L),
        )
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        assertEquals(300L, timeline.steps[0].bubbleAtMs)
        // 300 kick + 600 pause + 2000 wait
        assertEquals(2_900L, timeline.steps[1].bubbleAtMs)
    }

    @Test
    fun `a set typing duration overrides the one derived from length`() {
        val messages = listOf(message("a very long message indeed", outgoing = false, 0).copy(typingMs = 500L))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        assertEquals(300L, timeline.steps.single().typingFromMs)
        assertEquals(800L, timeline.steps.single().bubbleAtMs)
    }

    @Test
    fun `a typewriter message arrives at once and fills in over time`() {
        val messages = listOf(message("abcd", outgoing = true, 0).copy(revealPerCharMs = 50L))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
        val step = timeline.steps.single()

        assertEquals(300L, step.bubbleAtMs)
        assertEquals(500L, step.revealDoneAtMs)

        // The bubble is there from the start; only the letters take time.
        assertEquals(1, timeline.stateAt(300).visibleCount)
        assertEquals(0, timeline.stateAt(300).revealedChars)
        assertEquals(2, timeline.stateAt(400).revealedChars)
        // Once finished, nothing is being revealed any more.
        assertNull(timeline.stateAt(500).revealedChars)
    }

    @Test
    fun `a message without a typewriter is never partly revealed`() {
        val timeline = PlaybackTimeline(listOf(message("hi", outgoing = true, 0)), PlaySpeed.NORMAL)

        assertNull(timeline.stateAt(300).revealedChars)
        assertNull(timeline.stateAt(320).revealedChars)
    }

    // ── words that were never sent ─────────────────────────────────────────

    @Test
    fun `an unsent message never becomes a bubble`() {
        val messages = listOf(message("i miss you", outgoing = true, 0).copy(unsent = true))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        // Sampled across the whole playback, nothing is ever on screen.
        for (t in 0..timeline.totalMs step 50) {
            assertEquals("at ${t}ms", 0, timeline.stateAt(t).visibleCount)
        }
    }

    @Test
    fun `your own unsent words appear, hold, and are taken back`() {
        val text = "i miss you"
        val messages = listOf(message(text, outgoing = true, 0).copy(unsent = true))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
        val step = timeline.steps.single()

        // Nothing before it starts.
        assertNull(timeline.stateAt(step.composeFromMs!! - 10).composing)
        // Part-way through being written.
        val midType = step.composeFromMs!! + (step.eraseFromMs!! - step.composeFromMs!!) / 4
        val typing = timeline.stateAt(midType).composing!!
        assertTrue("was '$typing'", typing.isNotEmpty() && text.startsWith(typing))
        assertTrue(typing.length < text.length)
        // Written out in full, before it goes.
        assertEquals(text, timeline.stateAt(step.eraseFromMs!! - 10).composing)
        // Part-way through being erased.
        val midErase = (step.eraseFromMs!! + step.bubbleAtMs) / 2
        val erasing = timeline.stateAt(midErase).composing!!
        assertTrue("was '$erasing'", text.startsWith(erasing))
        assertTrue(erasing.length < text.length)
        // Gone, and nothing left behind.
        assertNull(timeline.stateAt(step.bubbleAtMs + 10).composing)
        assertEquals(0, timeline.stateAt(step.bubbleAtMs + 10).visibleCount)
    }

    /**
     * The bug this guards: the end of an unsent beat leaves nothing on the screen, so
     * scrubbing to it settled a raised keyboard over an empty input bar — the one beat in
     * the scene the viewer could never pause on and read.
     */
    @Test
    fun `pausing on an unsent beat shows the words it is about, held`() {
        val text = "i never stopped thinking about you"
        val messages = listOf(
            message("are you awake", outgoing = false, 0),
            message(text, outgoing = true, 1).copy(unsent = true),
            message("goodnight", outgoing = false, 2),
        )
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        val state = timeline.stateAt(timeline.timeAtIndex(2))

        assertEquals(text, state.composing)
        assertEquals(1f, state.keyboardFraction, 0.001f)
        // And the progress counter says the beat the viewer asked for, not the one before.
        assertEquals(2, state.playedCount)
        // The words were never sent, so they are still not a bubble.
        assertEquals(1, state.visibleCount)
    }

    @Test
    fun `the keyboard is up while your own unsent words are, and only then`() {
        val messages = listOf(message("i miss you", outgoing = true, 0).copy(unsent = true))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
        val step = timeline.steps.single()
        val slide = ChatTheme.KEYBOARD_SLIDE_MS

        // Down before it starts to rise, and in place by the time the first letter lands.
        assertEquals(0f, timeline.keyboardFractionAt(step.composeFromMs!! - slide - 1), 0.001f)
        assertEquals(1f, timeline.keyboardFractionAt(step.composeFromMs!!), 0.001f)
        // Part-way up in between.
        val rising = timeline.keyboardFractionAt(step.composeFromMs!! - slide / 2)
        assertTrue("was $rising", rising > 0f && rising < 1f)
        // Up for the whole of the writing, the holding and the taking back.
        for (t in step.composeFromMs!!..step.bubbleAtMs step 20) {
            assertEquals("at ${t}ms", 1f, timeline.keyboardFractionAt(t), 0.001f)
        }
        // Part-way down once the words are gone, and away by the end of the slide.
        val falling = timeline.keyboardFractionAt(step.bubbleAtMs + slide / 2)
        assertTrue("was $falling", falling > 0f && falling < 1f)
        assertEquals(0f, timeline.keyboardFractionAt(step.bubbleAtMs + slide), 0.001f)
    }

    @Test
    fun `a plain scene never raises the keyboard`() {
        val timeline = PlaybackTimeline(
            listOf(message("hi", outgoing = true, 0), message("hello", outgoing = false, 1)),
            PlaySpeed.NORMAL,
        )

        for (t in 0..timeline.totalMs step 25) {
            assertEquals("at ${t}ms", 0f, timeline.stateAt(t).keyboardFraction, 0.001f)
        }
    }

    /** You never see someone else's keyboard, any more than you see their sentence. */
    @Test
    fun `someone else's unsent message does not raise your keyboard`() {
        val messages = listOf(message("i almost said it", outgoing = false, 0).copy(unsent = true))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        for (t in 0..timeline.totalMs step 25) {
            assertEquals("at ${t}ms", 0f, timeline.keyboardFractionAt(t), 0.001f)
        }
    }

    @Test
    fun `backspace is what is held while the words are being taken back`() {
        val text = "i miss you"
        val messages = listOf(message(text, outgoing = true, 0).copy(unsent = true))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
        val step = timeline.steps.single()

        assertFalse(timeline.stateAt(step.eraseFromMs!! - 10).composeErasing)
        assertTrue(timeline.stateAt((step.eraseFromMs!! + step.bubbleAtMs) / 2).composeErasing)
        // Nothing is being held once the message is over.
        assertFalse(timeline.stateAt(step.bubbleAtMs + 10).composeErasing)
    }

    /**
     * You cannot see what someone else is typing. Their unsent words show as three dots
     * that start and stop, and the sentence behind them is never revealed.
     */
    @Test
    fun `someone else's unsent words are never shown, only their typing`() {
        val messages = listOf(message("i almost said it", outgoing = false, 0).copy(unsent = true))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
        val step = timeline.steps.single()

        val whileTyping = timeline.stateAt(step.composeFromMs!! + 200)
        assertEquals(0, whileTyping.typingIndex)
        assertNull("their words must never reach the input bar", whileTyping.composing)
        assertEquals(0, whileTyping.visibleCount)
    }

    /**
     * The bug this guards: a playback state counts only real bubbles, so if the renderer
     * still measured the unsent ones, every later bubble shifted up by one and the last was
     * never drawn at all.
     */
    @Test
    fun `visible count matches the number of bubbles that exist`() {
        val messages = listOf(
            message("hi", outgoing = false, 0),
            message("hello", outgoing = true, 1),
            message("everything i wanted to say", outgoing = true, 2).copy(unsent = true),
            message("goodnight", outgoing = true, 3),
        )
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        val real = messages.count { !it.unsent }
        assertEquals(real, timeline.stateAt(timeline.totalMs).visibleCount)
        // And it never exceeds that at any point along the way.
        for (t in 0..timeline.totalMs step 25) {
            assertTrue("at ${t}ms", timeline.stateAt(t).visibleCount <= real)
        }
    }

    @Test
    fun `an unsent message still lets the next one play`() {
        val messages = listOf(
            message("everything i wanted to say", outgoing = true, 0).copy(unsent = true),
            message("k", outgoing = true, 1),
        )
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        assertEquals(1, timeline.stateAt(timeline.totalMs).visibleCount)
        assertEquals("k", messages[timeline.steps[1].index].text)
    }

    @Test
    fun `an empty scene has nothing but the initial kick`() {
        val timeline = PlaybackTimeline(emptyList(), PlaySpeed.NORMAL)

        assertEquals(0, timeline.messageCount)
        assertEquals(300L, timeline.totalMs)
        assertEquals(0, timeline.stateAt(5_000).visibleCount)
    }
}
