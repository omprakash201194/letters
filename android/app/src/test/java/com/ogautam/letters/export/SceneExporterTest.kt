package com.ogautam.letters.export

import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.ui.scenes.chat.PlaySpeed
import com.ogautam.letters.ui.scenes.chat.PlaybackTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SceneExporterTest {

    private fun message(index: Int) = SceneMessageEntity(
        id = "m$index",
        sceneId = "s",
        charId = "c",
        charName = "n",
        charColor = 0,
        text = "hello",
        time = LocalTime.NOON,
        outgoing = index % 2 == 0,
        orderIndex = index,
    )

    @Test
    fun `the file name is a slug of the scene name`() {
        assertEquals("the-group-chat", SceneExporter.slug("The Group Chat"))
        assertEquals("tuesday-night", SceneExporter.slug("  Tuesday, night!  "))
        assertEquals("scene", SceneExporter.slug("   "))
        assertEquals("scene", SceneExporter.slug("!!!"))
        assertTrue(SceneExporter.slug("x".repeat(200)).length <= 40)
    }

    @Test
    fun `the video runs the whole scene plus a tail`() {
        val messages = List(4, ::message)
        val timeline = PlaybackTimeline(messages, PlaySpeed.NORMAL)

        assertEquals(
            timeline.totalMs + VideoSpec.TAIL_MS,
            SceneExporter.durationMs(messages, PlaySpeed.NORMAL),
        )
    }

    @Test
    fun `speed shortens the video`() {
        val messages = List(4, ::message)

        assertTrue(
            SceneExporter.durationMs(messages, PlaySpeed.DOUBLE) <
                SceneExporter.durationMs(messages, PlaySpeed.NORMAL),
        )
    }

    @Test
    fun `frame count follows the duration at the export frame rate`() {
        val messages = List(3, ::message)
        val durationMs = SceneExporter.durationMs(messages, PlaySpeed.NORMAL)

        val frames = SceneExporter.estimatedFrames(messages, PlaySpeed.NORMAL)

        assertEquals((durationMs * VideoSpec.FPS / 1_000.0).toInt() + 1, frames)
    }
}
