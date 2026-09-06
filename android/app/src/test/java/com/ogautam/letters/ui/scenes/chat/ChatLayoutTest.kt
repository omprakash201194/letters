package com.ogautam.letters.ui.scenes.chat

import android.app.Application
import com.ogautam.letters.data.entity.SceneMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChatLayoutTest {

    private val density = 2f
    private val widthPx = 1080f
    private val metrics = ChatMetrics(density)

    private fun message(
        charId: String,
        text: String = "hello",
        outgoing: Boolean = false,
        index: Int = 0,
    ) = SceneMessageEntity(
        id = "m$index-$charId",
        sceneId = "s",
        charId = charId,
        charName = charId.replaceFirstChar(Char::uppercase),
        charColor = 0,
        text = text,
        time = LocalTime.of(21, 14),
        outgoing = outgoing,
        orderIndex = index,
    )

    private fun layout() = ChatLayout(widthPx, density, metrics)

    @Test
    fun `the sender label shows once per run, not on every message`() {
        val messages = listOf(
            message("meera", index = 0),
            message("meera", index = 1),
            message("you", outgoing = true, index = 2),
            message("meera", index = 3),
            message("arjun", index = 4),
        )

        val shown = messages.indices.map { ChatLayout.showSenderLabel(messages, it) }
        assertEquals(listOf(true, false, false, true, true), shown)
    }

    @Test
    fun `outgoing messages never show a sender label`() {
        val messages = listOf(message("you", outgoing = true, index = 0))
        assertFalse(ChatLayout.showSenderLabel(messages, 0))
    }

    /**
     * Robolectric stubs glyph measurement, so a long string does not actually wrap here and
     * the line count means nothing. What is still worth asserting is the invariant that no
     * bubble can exceed the cap. That the text really wraps is checked on a device.
     */
    @Test
    fun `no bubble exceeds the 280dp cap`() {
        val long = "wait why is everyone awake, it is a tuesday, and some of us " +
            "have a standup at nine that we have been pretending is optional"
        val result = layout().measure(listOf(message("arjun", text = long)))

        val bubble = result.bubbles.single()
        assertTrue(
            "bubble was ${bubble.bubbleWidth}px, cap is ${metrics.bubbleMaxWidth}px",
            bubble.bubbleWidth <= metrics.bubbleMaxWidth + 0.5f,
        )
    }

    @Test
    fun `a short message keeps a narrow bubble`() {
        val result = layout().measure(listOf(message("meera", text = "no")))
        assertTrue(result.bubbles.single().bubbleWidth < metrics.bubbleMaxWidth / 2f)
    }

    @Test
    fun `the avatar column stays reserved so a run stays aligned`() {
        val messages = listOf(
            message("meera", index = 0),
            message("meera", index = 1),
        )
        val result = layout().measure(messages)

        val (first, second) = result.bubbles
        assertEquals(first.bubbleLeft, second.bubbleLeft, 0.01f)
        assertEquals(
            metrics.rowPadHorizontal + metrics.avatarSize + metrics.avatarGap,
            first.bubbleLeft,
            0.01f,
        )
    }

    @Test
    fun `outgoing bubbles hang off the right edge`() {
        val result = layout().measure(listOf(message("you", outgoing = true)))
        val bubble = result.bubbles.single()

        assertEquals(widthPx - metrics.rowPadHorizontal, bubble.bubbleRight, 0.01f)
    }

    @Test
    fun `a labelled message is taller than the same message without a label`() {
        val labelled = layout().measure(
            listOf(message("meera", index = 0), message("arjun", index = 1)),
        )
        val unlabelled = layout().measure(
            listOf(message("meera", index = 0), message("meera", index = 1)),
        )

        assertTrue(labelled.bubbles[1].rowHeight > unlabelled.bubbles[1].rowHeight)
    }

    @Test
    fun `rows stack without gaps or overlap`() {
        val messages = List(5) { message(if (it % 2 == 0) "meera" else "you", outgoing = it % 2 == 1, index = it) }
        val result = layout().measure(messages)

        var expectedTop = metrics.listPadVertical
        result.bubbles.forEach { bubble ->
            assertEquals(expectedTop, bubble.rowTop, 0.01f)
            expectedTop += bubble.rowHeight
        }
        assertEquals(expectedTop + metrics.listPadVertical, result.contentHeight, 0.01f)
    }

    @Test
    fun `an empty scene is just the list padding`() {
        val result = layout().measure(emptyList())
        assertEquals(metrics.listPadVertical * 2, result.contentHeight, 0.01f)
    }
}
