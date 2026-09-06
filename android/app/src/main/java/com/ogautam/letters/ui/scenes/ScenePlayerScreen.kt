package com.ogautam.letters.ui.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.scenes.chat.ChatRenderer
import com.ogautam.letters.ui.scenes.chat.ChatTheme
import com.ogautam.letters.ui.scenes.chat.PlaySpeed
import com.ogautam.letters.ui.theme.LettersPalette
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Plays a scene back. The chat surface is a single [Canvas] driven by [ChatRenderer] —
 * everything above the player controls is drawn, not composed.
 */
@Composable
fun ScenePlayerScreen(
    onBack: () -> Unit,
    viewModel: ScenePlayerViewModel = viewModel(factory = ScenePlayerViewModel.sampleFactory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            viewModel.advance((now - previous) / 1_000_000L)
            previous = now
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(ChatTheme.BACKGROUND))
            .navigationBarsPadding(),
    ) {
        ScreenHeader(background = LettersPalette.Teal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(Color.White, onBack)
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(state.sceneName, 15, FontWeight.SemiBold, Color.White)
                    Text(
                        "${state.messageCount} messages",
                        11,
                        FontWeight.Normal,
                        Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        ChatSurface(state = state, modifier = Modifier.weight(1f))

        PlayerControls(
            state = state,
            onSeekToIndex = viewModel::seekToIndex,
            onSpeed = viewModel::setSpeed,
            onPlayPause = viewModel::playPause,
        )
    }
}

@Composable
private fun ChatSurface(state: ScenePlayerUiState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    // One renderer per size: building it re-measures every bubble's text, which must not
    // happen per frame.
    val renderer = remember(viewport.width, density) {
        if (viewport.width == 0) null else ChatRenderer(viewport.width.toFloat(), density)
    }
    renderer?.setMessages(state.messages)

    // The transcript is pinned to the bottom, as the web app's scroll-to-bottom-on-update
    // left it. Deriving the offset rather than storing it keeps the draw pass read-only.
    val scrollY = renderer?.let {
        max(0f, it.contentHeight(state.playback) - viewport.height)
    } ?: 0f

    Canvas(
        modifier
            .fillMaxSize()
            // reason: a Compose draw scope is not clipped to its node, and this one fills
            // its whole surface — without this it paints over the header and the controls
            .clipToBounds()
            .onSizeChanged { viewport = it },
    ) {
        renderer?.draw(
            canvas = drawContext.canvas.nativeCanvas,
            state = state.playback,
            elapsedMs = state.timeMs,
            scrollY = scrollY,
            viewportHeight = size.height,
        )
    }
}

@Composable
private fun PlayerControls(
    state: ScenePlayerUiState,
    onSeekToIndex: (Int) -> Unit,
    onSpeed: (PlaySpeed) -> Unit,
    onPlayPause: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var trackWidth by remember { mutableFloatStateOf(1f) }
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE0E0E0))
                    .pointerInput(state.messageCount) {
                        trackWidth = size.width.toFloat()
                        detectTapGestures { offset ->
                            val ratio = (offset.x / trackWidth).coerceIn(0f, 1f)
                            onSeekToIndex((ratio * state.messageCount).roundToInt())
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress)
                        .fillMaxSize()
                        .background(LettersPalette.Teal),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${state.playback.visibleCount} / ${state.messageCount}",
                12,
                FontWeight.Normal,
                Color(0xFF888888),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PlaySpeed.entries.forEach { speed ->
                    val selected = speed == state.speed
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) LettersPalette.Teal else Color(0xFFF0F0F0))
                            .clickable { onSpeed(speed) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            speed.label,
                            12,
                            FontWeight.Normal,
                            if (selected) Color.White else Color(0xFF555555),
                        )
                    }
                }
            }

            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LettersPalette.Teal)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (state.isPlaying) "⏸" else "▶", 18, FontWeight.Normal, Color.White)
            }
        }
    }
}

@Composable
private fun Text(text: String, size: Int, weight: FontWeight, color: Color) {
    androidx.compose.material3.Text(
        text = text,
        fontSize = size.sp,
        fontWeight = weight,
        color = color,
    )
}
