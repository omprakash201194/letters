package com.ogautam.letters.ui.scenes

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.ui.common.HeaderButton
import com.ogautam.letters.ui.common.ScreenHeader
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Bitmap
import com.ogautam.letters.ui.scenes.chat.ChatCanvas
import com.ogautam.letters.ui.scenes.chat.ChatTheme
import com.ogautam.letters.ui.scenes.chat.PlaySpeed
import com.ogautam.letters.ui.theme.LettersPalette
import kotlin.math.roundToInt

/**
 * Plays a scene back. The chat surface is a single [Canvas] driven by [ChatRenderer] —
 * everything above the player controls is drawn, not composed.
 */
@Composable
fun ScenePlayerScreen(
    onBack: () -> Unit,
    sceneName: String,
    messages: List<SceneMessageEntity>,
    avatarFor: (String?) -> Bitmap? = { null },
    viewModel: ScenePlayerViewModel = viewModel(
        // reason: keyed so switching scenes builds a player for the new one rather than
        // reusing the old one's timeline
        key = "player:$sceneName:${messages.size}",
        factory = ScenePlayerViewModel.factory(sceneName, messages),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The user names the file and says where it goes; we write into what they picked.
    val pickDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        if (uri != null) viewModel.export(uri, displayNameOf(context, uri))
    }

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
                    Text(
                        state.sceneName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        "${state.messageCount} messages",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
            HeaderButton(
                label = if (state.export is ExportState.Running) "…" else "⬇ MP4",
                onClick = { pickDestination.launch(viewModel.suggestedFileName()) },
                enabled = state.export !is ExportState.Running && state.messages.isNotEmpty(),
            )
        }

        ChatCanvas(
            messages = state.messages,
            playback = state.playback,
            elapsedMs = state.timeMs,
            modifier = Modifier.weight(1f),
            avatarFor = avatarFor,
        )

        PlayerControls(
            state = state,
            onSeekToIndex = viewModel::seekToIndex,
            onSpeed = viewModel::setSpeed,
            onPlayPause = viewModel::playPause,
        )
    }

    ExportDialogs(
        state = state,
        onDismiss = viewModel::dismissExport,
        onShare = { uri -> shareVideo(context, uri) },
    )
}

/** The picked document's own name, so the dialog says what the user called it. */
private fun displayNameOf(context: Context, uri: Uri): String =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment
        ?: "the scene"

/** The Uri already came from a document provider, so it can be shared as it is. */
private fun shareVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share scene"))
    }
}

@Composable
private fun ExportDialogs(
    state: ScenePlayerUiState,
    onDismiss: () -> Unit,
    onShare: (Uri) -> Unit,
) {
    when (val export = state.export) {
        is ExportState.Idle -> Unit

        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting…", color = LettersPalette.GreenInk) },
            text = {
                Column {
                    Text(
                        "Drawing every frame of the scene. This takes a moment.",
                        fontSize = 14.sp,
                        color = Color(0xFF555555),
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { export.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = LettersPalette.Teal,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${(export.fraction * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        color = Color(0xFF888888),
                    )
                }
            },
            confirmButton = {},
            containerColor = Color.White,
        )

        is ExportState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Scene exported", color = LettersPalette.GreenInk) },
            text = {
                Text(
                    "Saved as ${export.name}.",
                    fontSize = 14.sp,
                    color = Color(0xFF555555),
                )
            },
            confirmButton = {
                TextButton(onClick = { onShare(export.destination) }) {
                    Text("Share", color = LettersPalette.Teal)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Done", color = LettersPalette.Muted) }
            },
            containerColor = Color.White,
        )

        is ExportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Export failed", color = LettersPalette.Danger) },
            text = { Text(export.message, fontSize = 14.sp, color = Color(0xFF555555)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK", color = LettersPalette.Teal) }
            },
            containerColor = Color.White,
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
                "${state.playback.playedCount} / ${state.messageCount}",
                fontSize = 12.sp,
                color = Color(0xFF888888),
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
                            fontSize = 12.sp,
                            color = if (selected) Color.White else Color(0xFF555555),
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
                Text(
                    if (state.isPlaying) "⏸" else "▶",
                    fontSize = 18.sp,
                    color = Color.White,
                )
            }
        }
    }
}

