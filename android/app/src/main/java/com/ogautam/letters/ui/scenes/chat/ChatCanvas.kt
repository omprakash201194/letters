package com.ogautam.letters.ui.scenes.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.ogautam.letters.data.entity.SceneMessageEntity
import kotlin.math.max

/**
 * The chat surface, shared by the composer and the player. Both show the same transcript
 * drawn by the same [ChatRenderer]; they differ only in what drives it — a playback clock
 * in one, the message list in the other.
 */
@Composable
fun ChatCanvas(
    messages: List<SceneMessageEntity>,
    playback: PlaybackState,
    elapsedMs: Long,
    modifier: Modifier = Modifier,
    avatarFor: (String?) -> Bitmap? = { null },
    onMessageTap: ((Int) -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    // Rebuilding the renderer re-measures every bubble's text, so it is keyed on the only
    // things that change its measurements.
    val renderer = remember(viewport.width, density) {
        if (viewport.width == 0) null else {
            ChatRenderer(viewport.width.toFloat(), density) { avatarFor(it.charAvatarPath) }
        }
    }
    renderer?.setMessages(messages)

    val contentHeight = renderer?.contentHeight(playback) ?: 0f
    val maxScroll = max(0f, contentHeight - viewport.height)

    var scrollY by remember { mutableFloatStateOf(0f) }

    // Pin to the bottom whenever the transcript grows, which is what the web app's
    // scroll-to-bottom-on-update amounted to. Scrolling back up in between still works.
    LaunchedEffect(contentHeight, viewport) { scrollY = maxScroll }

    val scroll = rememberScrollableState { delta ->
        val next = (scrollY - delta).coerceIn(0f, maxScroll)
        val consumed = scrollY - next
        scrollY = next
        -consumed
    }

    Canvas(
        modifier
            .fillMaxSize()
            // reason: a Compose draw scope is not clipped to its node, and the renderer
            // fills its surface — without this it paints over everything around it
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .scrollable(scroll, Orientation.Vertical)
            .then(
                if (onMessageTap == null) Modifier else Modifier.pointerInput(messages) {
                    detectTapGestures { offset ->
                        renderer
                            ?.hitTest(offset.x, offset.y, scrollY, playback.visibleCount)
                            ?.let(onMessageTap)
                    }
                },
            ),
    ) {
        renderer?.draw(
            canvas = drawContext.canvas.nativeCanvas,
            state = playback,
            elapsedMs = elapsedMs,
            scrollY = scrollY.coerceIn(0f, maxScroll),
            viewportHeight = size.height,
        )
    }
}

/** Everything visible and settled — what the composer shows. */
fun staticPlayback(messageCount: Int) =
    PlaybackState(
        visibleCount = messageCount,
        typingIndex = null,
        msSinceLastBubble = ChatTheme.POP_DURATION_MS,
    )
