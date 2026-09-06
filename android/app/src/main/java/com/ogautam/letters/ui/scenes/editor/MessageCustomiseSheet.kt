package com.ogautam.letters.ui.scenes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.ui.scenes.chat.PlaybackTimeline
import com.ogautam.letters.ui.theme.LettersPalette
import kotlin.math.roundToLong

/** What the sheet hands back when it closes. */
data class MessageCustomisation(
    val revealPerCharMs: Long?,
    val typingMs: Long?,
    val delayBeforeMs: Long?,
    val unsent: Boolean,
)

/**
 * How one message plays: whether it types itself out, how long the other person appears to
 * be writing, how long the pause before it is, and whether it is ever sent at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCustomiseSheet(
    message: SceneMessageEntity,
    onApply: (MessageCustomisation) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var typewriter by remember { mutableStateOf(message.revealPerCharMs != null) }
    var revealSpeed by remember {
        mutableFloatStateOf((message.revealPerCharMs ?: DEFAULT_REVEAL).toFloat())
    }
    var unsent by remember { mutableStateOf(message.unsent) }
    var customTyping by remember { mutableStateOf(message.typingMs != null) }
    var typingMs by remember {
        mutableFloatStateOf((message.typingMs ?: defaultTypingFor(message)).toFloat())
    }
    var delayMs by remember { mutableFloatStateOf((message.delayBeforeMs ?: 0L).toFloat()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                message.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = LettersPalette.Title,
                maxLines = 2,
            )
            Text(
                "${message.charName} · ${if (message.outgoing) "outgoing" else "incoming"}",
                fontSize = 12.sp,
                color = LettersPalette.Meta,
            )
            Spacer(Modifier.height(20.dp))

            SheetToggle(
                title = "Typed out letter by letter",
                subtitle = "The bubble arrives at its full size and the words fill it in.",
                checked = typewriter,
                onCheckedChange = { typewriter = it },
            )
            if (typewriter) {
                SheetSlider(
                    label = "Typing speed",
                    value = revealSpeed,
                    range = 15f..120f,
                    format = { "${it.roundToLong()} ms a letter" },
                    onValueChange = { revealSpeed = it },
                )
            }

            Spacer(Modifier.height(12.dp))
            SheetToggle(
                title = "Typed, then taken back",
                subtitle = if (message.outgoing) {
                    "Never sent. Your words appear in the input bar and are erased."
                } else {
                    "Never sent. Only their typing indicator shows — never the words."
                },
                checked = unsent,
                onCheckedChange = { unsent = it },
            )

            if (!message.outgoing && !unsent) {
                Spacer(Modifier.height(12.dp))
                SheetToggle(
                    title = "Set how long they type",
                    subtitle = "Otherwise it follows the length of the message.",
                    checked = customTyping,
                    onCheckedChange = { customTyping = it },
                )
                if (customTyping) {
                    SheetSlider(
                        label = "Typing for",
                        value = typingMs,
                        range = 300f..8_000f,
                        format = { "${(it / 100).roundToLong() / 10f} s" },
                        onValueChange = { typingMs = it },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SheetSlider(
                label = "Wait before this message",
                value = delayMs,
                range = 0f..6_000f,
                format = {
                    if (it < 50f) "no extra wait" else "${(it / 100).roundToLong() / 10f} s"
                },
                onValueChange = { delayMs = it },
            )

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetButton(
                    label = "Delete",
                    background = Color(0x14C0392B),
                    content = LettersPalette.Danger,
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
                SheetButton(
                    label = "Done",
                    background = LettersPalette.Teal,
                    content = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onApply(
                            MessageCustomisation(
                                revealPerCharMs = revealSpeed.roundToLong().takeIf { typewriter },
                                typingMs = typingMs.roundToLong()
                                    .takeIf { customTyping && !message.outgoing && !unsent },
                                delayBeforeMs = delayMs.roundToLong().takeIf { it >= 50L },
                                unsent = unsent,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = LettersPalette.Title)
            Text(subtitle, fontSize = 12.sp, color = LettersPalette.Meta, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) LettersPalette.Teal else LettersPalette.ToggleOff),
        ) {
            Box(
                Modifier
                    .offset(x = if (checked) 23.dp else 3.dp, y = 3.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun SheetSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = LettersPalette.Subject)
            Text(
                format(value),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = LettersPalette.Meta,
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun SheetButton(
    label: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontWeight = FontWeight.SemiBold)
    }
}

private const val DEFAULT_REVEAL = 45L

private fun defaultTypingFor(message: SceneMessageEntity): Long =
    PlaybackTimeline.TYPING_BASE_MS + message.text.length * PlaybackTimeline.TYPING_PER_CHAR_MS
