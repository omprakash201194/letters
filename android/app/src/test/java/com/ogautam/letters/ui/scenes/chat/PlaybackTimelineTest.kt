package com.ogautam.letters.ui.scenes.chat

import com.ogautam.letters.data.entity.SceneMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `an empty scene has nothing but the initial kick`() {
        val timeline = PlaybackTimeline(emptyList(), PlaySpeed.NORMAL)

        assertEquals(0, timeline.messageCount)
        assertEquals(300L, timeline.totalMs)
        assertEquals(0, timeline.stateAt(5_000).visibleCount)
    }
}
