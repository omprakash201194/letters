package com.ogautam.letters.ui.letters

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ogautam.letters.data.entity.Mood
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.ConfirmDialog
import com.ogautam.letters.ui.common.DatePickerField
import com.ogautam.letters.ui.common.HeaderButton
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.common.ScreenTitle
import com.ogautam.letters.ui.common.countWords
import com.ogautam.letters.ui.common.formatLong
import com.ogautam.letters.ui.theme.LettersPalette
import com.ogautam.letters.ui.theme.LoraFamily

/** The ruled-line pitch. The body's line height must equal it or the text drifts off the lines. */
private val LINE_PITCH = 28.dp

/** The red margin rule sits 72dp from the paper's left edge, as in the web app. */
private val MARGIN_RULE_X = 72.dp

@Composable
fun LetterEditorScreen(
    letterId: String?,
    onBack: () -> Unit,
    viewModel: LetterEditorViewModel = viewModel(factory = LetterEditorViewModel.factory(letterId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(state.missing) { if (state.missing) onBack() }

    val leave = { if (state.isDirty) confirmLeave = true else onBack() }
    BackHandler(enabled = true) { leave() }

    if (state.loading) return

    Column(
        Modifier
            .fillMaxSize()
            .background(LettersPalette.Ground),
    ) {
        ScreenHeader(background = LettersPalette.Brown) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(Color.White, leave)
                Spacer(Modifier.width(4.dp))
                ScreenTitle(if (state.isNew) "New Letter" else "Edit Letter", size = 15)
                if (state.isDirty) {
                    Spacer(Modifier.width(6.dp))
                    Text("unsaved", fontSize = 11.sp, color = LettersPalette.OnHeaderDim)
                }
            }
            HeaderButton(
                label = when {
                    state.saving -> "…"
                    state.justSaved -> "✓ Saved"
                    else -> "💾 Save"
                },
                onClick = viewModel::save,
                enabled = !state.saving,
                background = if (state.justSaved) LettersPalette.Saved else LettersPalette.HeaderBtn,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 32.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 560.dp),
            ) {
                Column {
                    if (state.sealed) {
                        SealedPaper(
                            recipient = state.recipient,
                            opensOn = state.sealedUntil?.let(::formatLong).orEmpty(),
                        )
                    } else {
                        Paper(state = state, viewModel = viewModel)
                    }
                    Spacer(Modifier.height(16.dp))
                    TimeCapsuleCard(state = state, viewModel = viewModel)
                }
            }
        }
    }

    state.error?.let { message ->
        ConfirmDialog(
            title = message,
            confirmLabel = "OK",
            onConfirm = viewModel::dismissError,
            onDismiss = viewModel::dismissError,
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave without saving?",
            body = "This letter has changes that have not been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { confirmLeave = false; onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun Paper(state: LetterEditorUiState, viewModel: LetterEditorViewModel) {
    val words = countWords(state.content)
    PaperSheet(minHeight = 500.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatePickerField(
                date = state.letterDate,
                onDateChange = viewModel::onDateChange,
                textStyle = TextStyle(
                    fontFamily = LoraFamily,
                    fontSize = 13.sp,
                    color = LettersPalette.Muted,
                ),
            )
            MoodRow(selected = state.mood, onToggle = viewModel::onMoodToggle)
        }
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text("Dear", fontFamily = LoraFamily, fontSize = 17.sp, color = LettersPalette.BrownDeep)
            Spacer(Modifier.width(6.dp))
            PaperField(
                value = state.recipient,
                onValueChange = viewModel::onRecipientChange,
                placeholder = "…",
                underline = LettersPalette.DashStrong,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontFamily = LoraFamily,
                    fontSize = 17.sp,
                    color = LettersPalette.BrownDeep,
                ),
            )
            Text(",", fontFamily = LoraFamily, fontSize = 17.sp, color = LettersPalette.BrownDeep)
        }
        Spacer(Modifier.height(8.dp))

        PaperField(
            value = state.subject,
            onValueChange = viewModel::onSubjectChange,
            placeholder = "Subject (optional)",
            underline = LettersPalette.DashLight,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontFamily = LoraFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = LettersPalette.Subject,
            ),
        )
        Spacer(Modifier.height(20.dp))

        RuledBody(value = state.content, onValueChange = viewModel::onContentChange)

        Spacer(Modifier.height(8.dp))
        Text(
            "$words ${if (words == 1) "word" else "words"}",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            color = LettersPalette.Whisper,
            textAlign = TextAlign.End,
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "— ${state.penName}",
            modifier = Modifier.fillMaxWidth(),
            fontFamily = LoraFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            color = LettersPalette.Muted,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            formatLong(state.letterDate),
            modifier = Modifier.fillMaxWidth(),
            fontFamily = LoraFamily,
            fontSize = 12.sp,
            color = LettersPalette.Faint,
            textAlign = TextAlign.End,
        )
    }
}

/** The sheet itself: ground, border, and the red margin rule running its full height. */
@Composable
private fun PaperSheet(
    minHeight: androidx.compose.ui.unit.Dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .background(LettersPalette.Paper, RoundedCornerShape(4.dp))
            .border(1.dp, LettersPalette.PaperBorder, RoundedCornerShape(4.dp))
            .drawBehind {
                val x = MARGIN_RULE_X.toPx()
                drawLine(
                    color = LettersPalette.MarginRule,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(start = 36.dp, end = 36.dp, top = 32.dp, bottom = 40.dp),
        content = content,
    )
}

@Composable
private fun SealedPaper(recipient: String, opensOn: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 400.dp)
            .background(LettersPalette.Paper, RoundedCornerShape(4.dp))
            .border(1.dp, LettersPalette.PaperBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 36.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("🔒", fontSize = 56.sp)
        Text(
            "Time Capsule",
            fontFamily = LoraFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = LettersPalette.BrownDeep,
        )
        Text(
            "Dear $recipient,",
            fontFamily = LoraFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            color = LettersPalette.Muted,
        )
        Text("This letter is sealed until", fontSize = 13.sp, color = LettersPalette.Faint)
        Text(
            opensOn,
            fontFamily = LoraFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = LettersPalette.Brown,
        )
        Text("Come back then to read it.", fontSize = 12.sp, color = LettersPalette.SealedFaint)
    }
}

/**
 * The body. The lines are drawn behind the text at the same 28dp pitch as its line height,
 * and the text box is told to centre each line within its box the way CSS line-height does —
 * Compose's default distributes the extra leading differently and the text sits high.
 */
@Composable
private fun RuledBody(value: String, onValueChange: (String) -> Unit) {
    val style = TextStyle(
        fontFamily = LoraFamily,
        fontSize = 15.5.sp,
        lineHeight = 28.sp,
        color = LettersPalette.Ink,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(LettersPalette.Brown),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = LINE_PITCH * 14)
            .drawBehind {
                val pitch = LINE_PITCH.toPx()
                var y = pitch
                while (y <= size.height) {
                    drawLine(
                        color = LettersPalette.PaperRule,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    y += pitch
                }
            },
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    "Write what you never said…",
                    style = style.copy(color = LettersPalette.Faint),
                )
            }
            inner()
        },
    )
}

/** A single-line field on a dashed rule, as the paper's inputs are drawn. */
@Composable
private fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    underline: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(LettersPalette.Brown),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = LettersPalette.Faint))
                }
                inner()
            },
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(underline))
    }
}

/**
 * All eight moods, always visible. With none chosen every emoji sits at half opacity; once
 * one is chosen it goes full and the rest fade back, so the choice reads at a glance.
 */
@Composable
private fun MoodRow(selected: String?, onToggle: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Mood.entries.forEach { mood ->
            val isSelected = selected == mood.slug
            val alpha = when {
                selected == null -> 0.5f
                isSelected -> 1f
                else -> 0.25f
            }
            Text(
                mood.emoji,
                fontSize = if (isSelected) 22.sp else 18.sp,
                modifier = Modifier
                    .alpha(alpha)
                    .clickable { onToggle(mood.slug) }
                    .padding(2.dp),
            )
        }
    }
}

@Composable
private fun TimeCapsuleCard(state: LetterEditorUiState, viewModel: LetterEditorViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, LettersPalette.CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "🔒 Time capsule",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = LettersPalette.BrownDeep,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Seal this letter until a future date",
                    fontSize = 12.sp,
                    color = LettersPalette.Hint,
                )
            }
            SealToggle(on = state.sealToggle, onClick = viewModel::onSealToggle)
        }

        if (state.sealToggle) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opens on", fontSize = 13.sp, color = LettersPalette.SealLabel)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .background(LettersPalette.SealField, RoundedCornerShape(6.dp))
                        .border(1.dp, LettersPalette.SealBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    DatePickerField(
                        date = state.sealedUntil ?: state.today,
                        onDateChange = viewModel::onSealDateChange,
                        minDate = state.today,
                        textStyle = TextStyle(
                            fontFamily = LoraFamily,
                            fontSize = 13.sp,
                            color = LettersPalette.BrownDeep,
                        ),
                    )
                }
                if (state.sealed) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "· currently sealed",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = LettersPalette.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SealToggle(on: Boolean, onClick: () -> Unit) {
    val knobOffset by animateDpAsState(if (on) 23.dp else 3.dp, label = "sealKnob")
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) LettersPalette.Brown else LettersPalette.ToggleOff)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
