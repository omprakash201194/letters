package com.ogautam.letters.ui.scenes

import android.app.Application
import com.ogautam.letters.audio.SceneTones
import com.ogautam.letters.ui.scenes.chat.ChatTheme
import com.ogautam.letters.ui.scenes.chat.PlaySpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScenePlayerViewModelTest {

    /** Records what playback asked to be played, in order. */
    private class RecordingTones : SceneTones {
        val played = mutableListOf<String>()
        override fun playSend() { played += "send" }
        override fun playReceive() { played += "receive" }
        override fun release() = Unit
    }

    private val tones = RecordingTones()

    private fun viewModel() =
        ScenePlayerViewModel(SampleScene.NAME, SampleScene.messages, tones)

    @Test
    fun `a scene opens paused at the beginning`() {
        val state = viewModel().state.value

        assertFalse(state.isPlaying)
        assertEquals(0, state.playback.visibleCount)
        assertEquals(SampleScene.messages.size, state.messageCount)
    }

    /**
     * A seek lands exactly on a bubble's timestamp — the first instant of its pop-in. With
     * playback paused the pop would never run, so the message would sit invisible.
     */
    @Test
    fun `seeking shows the message settled, not at the start of its pop`() {
        val vm = viewModel()
        vm.seekToIndex(5)

        val state = vm.state.value
        assertEquals(5, state.playback.visibleCount)
        assertNull(state.playback.typingIndex)
        assertEquals(ChatTheme.POP_DURATION_MS, state.playback.msSinceLastBubble)
        assertFalse(state.isPlaying)
    }

    @Test
    fun `seeking past either end is clamped`() {
        val vm = viewModel()

        vm.seekToIndex(-3)
        assertEquals(0, vm.state.value.playback.visibleCount)

        vm.seekToIndex(999)
        assertEquals(SampleScene.messages.size, vm.state.value.playback.visibleCount)
    }

    @Test
    fun `changing speed keeps the viewer on the same message`() {
        val vm = viewModel()
        vm.seekToIndex(4)

        vm.setSpeed(PlaySpeed.DOUBLE)

        assertEquals(PlaySpeed.DOUBLE, vm.state.value.speed)
        assertEquals(4, vm.state.value.playback.visibleCount)
    }

    @Test
    fun `advancing does nothing while paused`() {
        val vm = viewModel()
        vm.advance(5_000)

        assertEquals(0, vm.state.value.playback.visibleCount)
        assertEquals(0L, vm.state.value.timeMs)
    }

    @Test
    fun `playing types before the first message appears, then stops at the end`() {
        val vm = viewModel()
        vm.playPause()
        assertTrue(vm.state.value.isPlaying)

        // The sample opens with an incoming message, so the first thing on screen is its
        // typing indicator — not a bubble.
        vm.advance(1_000)
        assertEquals(0, vm.state.value.playback.visibleCount)
        assertEquals(0, vm.state.value.playback.typingIndex)

        vm.advance(600_000)
        assertFalse("playback should stop at the end", vm.state.value.isPlaying)
        assertEquals(SampleScene.messages.size, vm.state.value.playback.visibleCount)
    }

    @Test
    fun `each message plays a tone matching its direction, in order`() {
        val vm = viewModel()
        vm.playPause()
        vm.advance(600_000)

        val expected = SampleScene.messages.map { if (it.outgoing) "send" else "receive" }
        assertEquals(expected, tones.played)
    }

    @Test
    fun `scrubbing is silent`() {
        val vm = viewModel()
        vm.seekToIndex(SampleScene.messages.size)

        assertEquals(SampleScene.messages.size, vm.state.value.playback.visibleCount)
        assertTrue("seeking should not play anything", tones.played.isEmpty())
    }

    @Test
    fun `pressing play at the end restarts from the beginning`() {
        val vm = viewModel()
        vm.playPause()
        vm.advance(600_000)
        assertEquals(SampleScene.messages.size, vm.state.value.playback.visibleCount)

        vm.playPause()

        assertTrue(vm.state.value.isPlaying)
        assertEquals(0L, vm.state.value.timeMs)
        assertEquals(0, vm.state.value.playback.visibleCount)
    }
}
