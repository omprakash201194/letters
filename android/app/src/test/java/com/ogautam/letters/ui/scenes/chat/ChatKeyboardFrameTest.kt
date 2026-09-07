package com.ogautam.letters.ui.scenes.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import com.ogautam.letters.data.entity.SceneMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/**
 * What is actually in the frame while the keyboard is up.
 *
 * The geometry is the part that can go wrong quietly: the keyboard takes its room from the
 * transcript rather than being laid over it, so a mistake here does not throw — it hides
 * the last bubble behind the keys, in the exported video, where nobody can scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatKeyboardFrameTest {

    private val width = 720
    private val height = 1280
    private val density = 2f
    private val metrics = ChatMetrics(density)
    private val keyboardHeight = KeyboardLayout(width.toFloat(), metrics).height

    private fun message(text: String, outgoing: Boolean, index: Int) = SceneMessageEntity(
        id = "m$index",
        sceneId = "s",
        charId = if (outgoing) "you" else "them",
        charName = if (outgoing) "You" else "Them",
        charColor = 0xFF9C27B0.toInt(),
        text = text,
        time = LocalTime.NOON,
        outgoing = outgoing,
        orderIndex = index,
    )

    private val messages = listOf(
        message("are you awake", outgoing = false, 0),
        message("i never stopped thinking about you", outgoing = true, 1).copy(unsent = true),
    )

    private val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)
    private val unsent = timeline.steps.last()

    private fun frameAt(timeMs: Long): Bitmap {
        val renderer = ChatRenderer(widthPx = width.toFloat(), density = density)
        renderer.setMessages(messages)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val state = timeline.stateAt(timeMs)
        val transcriptHeight = renderer.transcriptHeight(height.toFloat(), state.keyboardFraction)
        renderer.draw(
            canvas = Canvas(bitmap),
            state = state,
            elapsedMs = timeMs,
            scrollY = maxOf(0f, renderer.contentHeight(state) - transcriptHeight),
            viewportHeight = height.toFloat(),
        )
        return bitmap
    }

    /** The strip of ground below the bottom row of keys, which nothing else ever paints. */
    private fun bottomLeftPixel(bitmap: Bitmap) = bitmap.getPixel(1, height - 2)

    @Test
    fun `the keyboard is in the frame while your unsent words are being written`() {
        val frame = frameAt(unsent.composeFromMs!! + 100)

        assertEquals(ChatTheme.KEYBOARD_GROUND, bottomLeftPixel(frame))
    }

    @Test
    fun `the input bar is at the bottom of the frame when the keyboard is not up`() {
        assertEquals(ChatTheme.INPUT_BAR_GROUND, bottomLeftPixel(frameAt(0)))
        assertEquals(
            ChatTheme.INPUT_BAR_GROUND,
            bottomLeftPixel(frameAt(unsent.bubbleAtMs + ChatTheme.KEYBOARD_SLIDE_MS + 50)),
        )
    }

    /**
     * The keyboard takes its room from the transcript. If it were laid over the transcript
     * instead, the words in the input bar would still be readable and the bubble above them
     * would not — which is the failure this pins.
     */
    @Test
    fun `the transcript gives up exactly the room the keyboard takes`() {
        val renderer = ChatRenderer(widthPx = width.toFloat(), density = density)

        val closed = renderer.transcriptHeight(height.toFloat(), 0f)
        val open = renderer.transcriptHeight(height.toFloat(), 1f)

        assertEquals(height - metrics.inputBarHeight, closed, 0.5f)
        assertEquals(closed - keyboardHeight, open, 0.5f)
        // Half way up, half the room.
        assertEquals(
            closed - keyboardHeight / 2f,
            renderer.transcriptHeight(height.toFloat(), 0.5f),
            0.5f,
        )
    }

    @Test
    fun `the keyboard leaves the transcript alone in the composer, which has a real one`() {
        val renderer = ChatRenderer(
            widthPx = width.toFloat(),
            density = density,
            showInputBar = false,
            showUnsentGhosts = true,
        )

        assertEquals(height.toFloat(), renderer.transcriptHeight(height.toFloat(), 1f), 0.5f)
    }

    @Test
    fun `it slides rather than appearing`() {
        val midSlide = unsent.composeFromMs!! - ChatTheme.KEYBOARD_SLIDE_MS / 2
        val rising = timeline.keyboardFractionAt(midSlide)

        assertTrue("was $rising", rising > 0f && rising < 1f)
        // Part way up, the keyboard already reaches the bottom of the frame — it comes up
        // from under the screen rather than fading in where it will end up.
        assertEquals(ChatTheme.KEYBOARD_GROUND, bottomLeftPixel(frameAt(midSlide)))
        // And the input bar has not finished rising with it.
        val renderer = ChatRenderer(widthPx = width.toFloat(), density = density)
        val partial = renderer.transcriptHeight(height.toFloat(), rising)
        assertTrue(
            "input bar between its two positions",
            partial > renderer.transcriptHeight(height.toFloat(), 1f) &&
                partial < renderer.transcriptHeight(height.toFloat(), 0f),
        )
    }
}
