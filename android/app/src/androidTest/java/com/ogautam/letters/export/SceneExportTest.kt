package com.ogautam.letters.export

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.ui.scenes.chat.ChatTheme
import com.ogautam.letters.ui.scenes.chat.PlaySpeed
import com.ogautam.letters.ui.scenes.chat.PlaybackTimeline
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalTime
import kotlin.math.abs

/**
 * Exports a scene on a real device and decodes the result back.
 *
 * This is the only test that can prove the export works: everything before it — the
 * timeline, the layout, the colour conversion — is checked in isolation on the JVM, but
 * whether those pieces produce a playable file with the right picture in it needs a codec.
 */
@RunWith(AndroidJUnit4::class)
class SceneExportTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val produced = mutableListOf<File>()

    @After
    fun cleanUp() {
        produced.forEach { it.delete() }
    }

    private fun message(text: String, outgoing: Boolean, index: Int) = SceneMessageEntity(
        id = "m$index",
        sceneId = "s",
        charId = if (outgoing) "you" else "meera",
        charName = if (outgoing) "You" else "Meera",
        charColor = CharacterPalette.colorForIndex(if (outgoing) 0 else 1),
        text = text,
        time = LocalTime.of(21, 14),
        outgoing = outgoing,
        orderIndex = index,
    )

    private val messages = listOf(
        message("are you awake", outgoing = false, 0),
        message("unfortunately yes", outgoing = true, 1),
    )

    private fun export(speed: PlaySpeed = PlaySpeed.DOUBLE): File =
        SceneExporter(context).export("Export test", messages, speed).also { produced += it }

    private fun Bitmap.colorAt(xFraction: Float, yFraction: Float): Int =
        getPixel(
            (width * xFraction).toInt().coerceIn(0, width - 1),
            (height * yFraction).toInt().coerceIn(0, height - 1),
        )

    /** Video colour goes through a YUV round trip, so exact equality is the wrong test. */
    private fun assertLooksLike(expected: Int, actual: Int, tolerance: Int = 12, what: String) {
        val message = "$what: expected ~#${Integer.toHexString(expected)}, " +
            "was #${Integer.toHexString(actual)}"
        assertTrue(message, abs(Color.red(expected) - Color.red(actual)) <= tolerance)
        assertTrue(message, abs(Color.green(expected) - Color.green(actual)) <= tolerance)
        assertTrue(message, abs(Color.blue(expected) - Color.blue(actual)) <= tolerance)
    }

    @Test
    fun exportsAPlayableFileOfTheRightShape() {
        val file = export()

        assertTrue("the file should exist", file.exists())
        assertTrue("the file should not be empty", file.length() > 0)

        val retriever = MediaMetadataRetriever()
        retriever.use {
            it.setDataSource(file.absolutePath)
            assertEquals(
                VideoSpec.WIDTH.toString(),
                it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
            )
            assertEquals(
                VideoSpec.HEIGHT.toString(),
                it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
            )
            assertEquals(
                "the file should carry an audio track",
                "yes",
                it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO),
            )

            val durationMs = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            val expected = SceneExporter.durationMs(messages, PlaySpeed.DOUBLE)
            assertTrue(
                "expected about ${expected}ms, file says ${durationMs}ms",
                abs(durationMs - expected) < 200,
            )
        }
    }

    /**
     * The frames have to contain the scene, not just be the right size — this is what
     * catches a broken colour conversion or a renderer fed the wrong time.
     */
    @Test
    fun theFramesContainTheSceneAtTheRightTimes() {
        val file = export()
        val timeline = PlaybackTimeline(messages, PlaySpeed.DOUBLE)

        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)

            fun frameAt(timeMs: Long): Bitmap = requireNotNull(
                retriever.getFrameAtTime(
                    timeMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                ),
            ) { "no frame at ${timeMs}ms" }

            // Before anything is said, the surface is the empty chat background.
            val opening = frameAt(50)
            assertLooksLike(
                expected = ChatTheme.BACKGROUND,
                actual = opening.colorAt(0.5f, 0.5f),
                what = "the background before the first message",
            )

            // After the outgoing message, its green bubble is on the right of the screen.
            val outgoingStep = timeline.steps.last()
            val settled = frameAt(outgoingStep.bubbleAtMs + 400)
            val bubbleRow = bubbleRowFraction(settled)
            assertNotNull("no green bubble was found in the frame", bubbleRow)
            assertLooksLike(
                expected = ChatTheme.OUTGOING,
                actual = settled.colorAt(0.85f, bubbleRow!!),
                what = "the outgoing bubble",
            )
        }
    }

    /** Scans the right-hand edge for the row holding the outgoing bubble. */
    private fun bubbleRowFraction(frame: Bitmap): Float? {
        val x = (frame.width * 0.85f).toInt()
        for (y in 0 until frame.height step 4) {
            val pixel = frame.getPixel(x, y)
            if (abs(Color.red(pixel) - Color.red(ChatTheme.OUTGOING)) <= 12 &&
                abs(Color.green(pixel) - Color.green(ChatTheme.OUTGOING)) <= 12 &&
                abs(Color.blue(pixel) - Color.blue(ChatTheme.OUTGOING)) <= 12
            ) {
                return y.toFloat() / frame.height
            }
        }
        return null
    }

    @Test
    fun aSceneWithNoMessagesIsRefusedRatherThanWrittenEmpty() {
        val error = runCatching {
            SceneExporter(context).export("Empty", emptyList())
        }.exceptionOrNull()

        assertTrue("expected a refusal, got $error", error is IllegalArgumentException)
    }
}
