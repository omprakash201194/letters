package com.ogautam.letters.export

import com.ogautam.letters.audio.ToneSynth
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.ui.scenes.chat.PlaySpeed
import com.ogautam.letters.ui.scenes.chat.PlaybackTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.math.abs

class SceneAudioTrackTest {

    private fun message(text: String, outgoing: Boolean, index: Int) = SceneMessageEntity(
        id = "m$index",
        sceneId = "s",
        charId = if (outgoing) "you" else "them",
        charName = "n",
        charColor = 0,
        text = text,
        time = LocalTime.NOON,
        outgoing = outgoing,
        orderIndex = index,
    )

    private fun ShortArray.isSilentAround(sample: Int, window: Int = 200): Boolean =
        (maxOf(0, sample - window) until minOf(size, sample + window))
            .all { this[it].toInt() == 0 }

    @Test
    fun `the track is exactly as long as the video`() {
        val timeline = PlaybackTimeline(listOf(message("hi", true, 0)), PlaySpeed.NORMAL)
        val totalMs = 5_000L

        val pcm = SceneAudioTrack.build(timeline, { true }, totalMs)

        assertEquals(ToneSynth.SAMPLE_RATE * 5, pcm.size)
    }

    /** Sound and picture come from the same timeline, so a tone starts where a bubble does. */
    @Test
    fun `each tone starts at the instant its bubble appears`() {
        val messages = listOf(
            message("hi", outgoing = true, 0),
            message("hello", outgoing = false, 1),
        )
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
        val pcm = SceneAudioTrack.build(
            timeline,
            { index -> messages[index].outgoing },
            timeline.totalMs + 1_000,
        )

        timeline.steps.forEach { step ->
            val start = SceneAudioTrack.samplesFor(step.bubbleAtMs)
            val justBefore = start - 1
            assertTrue(
                "should be silent before message ${step.index}",
                pcm.isSilentAround(justBefore - 300, window = 100),
            )
            val opening = (start until start + 500).maxOf { abs(pcm[it].toInt()) }
            assertTrue("message ${step.index} should sound at $start", opening > 100)
        }
    }

    @Test
    fun `silence between messages stays silent`() {
        val messages = listOf(message("hi", outgoing = true, 0))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
        val pcm = SceneAudioTrack.build(timeline, { true }, timeline.totalMs + 2_000)

        // The send tone is 120ms; well after it, nothing should be playing.
        val wellAfter = SceneAudioTrack.samplesFor(timeline.steps.single().bubbleAtMs + 500)
        assertTrue(pcm.isSilentAround(wellAfter))
    }

    @Test
    fun `a tone that would run past the end is truncated, not dropped`() {
        val messages = listOf(message("hi", outgoing = true, 0))
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
        // Ends 20ms after the bubble — the 120ms tone cannot fit.
        val totalMs = timeline.steps.single().bubbleAtMs + 20

        val pcm = SceneAudioTrack.build(timeline, { true }, totalMs)

        val start = SceneAudioTrack.samplesFor(timeline.steps.single().bubbleAtMs)
        assertTrue("the tone should still begin", (start until pcm.size).any { pcm[it].toInt() != 0 })
    }

    @Test
    fun `nothing clips when two messages land close together`() {
        val messages = List(6) { message("x", outgoing = it % 2 == 0, it) }
        val timeline = PlaybackTimeline(messages, PlaySpeed.DOUBLE)

        val pcm = SceneAudioTrack.build(
            timeline,
            { index -> messages[index].outgoing },
            timeline.totalMs + 500,
        )

        assertTrue(pcm.all { abs(it.toInt()) < Short.MAX_VALUE.toInt() })
    }
}
