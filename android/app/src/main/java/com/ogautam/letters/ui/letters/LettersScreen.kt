package com.ogautam.letters.ui.letters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ogautam.letters.data.entity.Mood
import com.ogautam.letters.data.repository.LetterSummary
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.ConfirmDialog
import com.ogautam.letters.ui.common.HeaderButton
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.common.ScreenTitle
import com.ogautam.letters.ui.common.formatShort
import com.ogautam.letters.ui.theme.LettersPalette
import com.ogautam.letters.ui.theme.LoraFamily
import java.time.ZoneId

@Composable
fun LettersScreen(
    onBack: () -> Unit,
    onOpenLetter: (String) -> Unit,
    onNewLetter: () -> Unit,
    viewModel: LettersViewModel = viewModel(factory = LettersViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<LetterSummary?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LettersPalette.Ground),
    ) {
        ScreenHeader(background = LettersPalette.Brown) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(Color.White, onBack)
                Spacer(Modifier.width(6.dp))
                ScreenTitle("Unsent Letters")
            }
            HeaderButton("✍️ Write", onNewLetter, background = Color.White.copy(alpha = 0.2f))
        }

        when {
            state.loading -> Unit

            state.letters.isEmpty() -> EmptyState()

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.letters, key = LetterSummary::id) { letter ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 520.dp)) {
                            LetterCard(
                                letter = letter,
                                onClick = { onOpenLetter(letter.id) },
                                onDelete = { pendingDelete = letter },
                            )
                        }
                    }
                }
            }
        }

        if (!state.loading && state.letters.isNotEmpty()) {
            Text(
                "— ${state.penName}",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                fontFamily = LoraFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
                color = LettersPalette.Faint,
                textAlign = TextAlign.Center,
            )
        }
    }

    pendingDelete?.let { letter ->
        ConfirmDialog(
            title = "Delete this letter?",
            body = "Dear ${letter.recipient} — this cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.delete(letter.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✉️", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("No letters yet", fontFamily = LoraFamily, fontSize = 18.sp, color = LettersPalette.Muted)
        Spacer(Modifier.height(8.dp))
        Text("Write the things you never said", fontSize = 14.sp, color = LettersPalette.Faint)
    }
}

@Composable
private fun LetterCard(letter: LetterSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .background(LettersPalette.Paper, RoundedCornerShape(8.dp))
                .border(1.dp, LettersPalette.PaperBorder, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Mood.fromSlug(letter.mood)?.emoji ?: "✉️", fontSize = 20.sp)
                Text(
                    letter.letterDate?.let(::formatShort)
                        ?: formatShort(letter.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                    // reason: clears the delete affordance pinned to the card's top-right
                    modifier = Modifier.padding(end = 24.dp),
                    fontSize = 12.sp,
                    color = LettersPalette.ListDate,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Dear ${letter.recipient},",
                fontFamily = LoraFamily,
                fontSize = 13.sp,
                color = LettersPalette.Muted,
            )
            Spacer(Modifier.height(4.dp))
            if (letter.subject != null) {
                Text(
                    letter.subject,
                    fontFamily = LoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = LettersPalette.BrownDeep,
                )
            } else {
                Text(
                    "(no subject)",
                    fontFamily = LoraFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = LettersPalette.Muted,
                )
            }
            Spacer(Modifier.height(4.dp))
            when {
                letter.isSealed -> Text(
                    "🔒 Opens ${letter.sealedUntil?.let(::formatShort).orEmpty()}",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = LettersPalette.Faint,
                )

                letter.contentPreview != null -> Text(
                    letter.contentPreview,
                    fontFamily = LoraFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = LettersPalette.Preview,
                )
            }
            if (letter.mood != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    letter.mood.replaceFirstChar(Char::uppercase),
                    fontSize = 11.sp,
                    color = LettersPalette.Faint,
                )
            }
        }

        Text(
            "✕",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable(onClick = onDelete)
                .padding(top = 10.dp, end = 10.dp, start = 8.dp, bottom = 8.dp),
            fontSize = 16.sp,
            color = LettersPalette.Chevron,
        )
    }
}
