package com.ogautam.letters.ui.home

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ogautam.letters.data.dao.SceneSummary
import com.ogautam.letters.data.entity.Mood
import com.ogautam.letters.data.repository.LetterSummary
import com.ogautam.letters.ui.common.PenNameDialog
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.theme.LettersPalette
import com.ogautam.letters.ui.theme.LoraFamily

@Composable
fun HomeScreen(
    onOpenLetters: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenLetter: (String) -> Unit,
    onNewLetter: () -> Unit,
    onOpenScene: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var penNameDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LettersPalette.HomeGround),
    ) {
        ScreenHeader(background = Color.White) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✉️", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Letters",
                    fontFamily = LoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = LettersPalette.Title,
                )
            }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { menuOpen = true }.padding(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(LettersPalette.Brown),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.penName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("≡", fontSize = 13.sp, color = LettersPalette.Subject)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Color.White,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "✏️  Pen name · ${state.penName}",
                                fontSize = 14.sp,
                                color = LettersPalette.BrownDeep,
                            )
                        },
                        onClick = { menuOpen = false; penNameDialog = true },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LettersPalette.HomeBorder))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 32.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 520.dp)) {
                Column {
                    SearchField(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                    )
                    Spacer(Modifier.height(20.dp))

                    if (state.isSearching) {
                        SearchResults(
                            query = state.query,
                            scenes = state.sceneResults,
                            letters = state.letterResults,
                            hasResults = state.hasResults,
                            onOpenScene = onOpenScene,
                            onOpenLetter = onOpenLetter,
                        )
                    } else {
                        PromptCard(prompt = state.prompt, onWrite = onNewLetter)
                        Spacer(Modifier.height(20.dp))
                        FeatureTile(
                            gradient = listOf(
                                LettersPalette.TileGreenA,
                                LettersPalette.TileGreenB,
                            ),
                            border = LettersPalette.TileGreenEdge,
                            iconBackground = LettersPalette.Teal,
                            icon = "💬",
                            title = "Chat Scenes",
                            titleColor = LettersPalette.GreenInk,
                            subtitle = if (state.loading) "…" else plural(state.sceneCount, "scene"),
                            subtitleColor = LettersPalette.GreenMuted,
                            chevronColor = LettersPalette.ChevronGreen,
                            onClick = onOpenScenes,
                        )
                        Spacer(Modifier.height(12.dp))
                        FeatureTile(
                            gradient = listOf(
                                LettersPalette.TileBrownA,
                                LettersPalette.TileBrownB,
                            ),
                            border = LettersPalette.TileBrownEdge,
                            iconBackground = LettersPalette.Brown,
                            icon = "✉️",
                            title = "Unsent Letters",
                            titleColor = LettersPalette.BrownDeep,
                            subtitle = when {
                                state.loading -> "…"
                                state.sealedCount > 0 ->
                                    "${plural(state.letterCount, "letter")} · ${state.sealedCount} sealed 🔒"
                                else -> plural(state.letterCount, "letter")
                            },
                            subtitleColor = LettersPalette.TileLetters,
                            chevronColor = LettersPalette.ChevronBrown,
                            onClick = onOpenLetters,
                        )
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "Your words, your way",
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = LoraFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                            color = LettersPalette.Whisper,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (penNameDialog) {
        PenNameDialog(
            current = state.penName,
            onConfirm = { viewModel.setPenName(it); penNameDialog = false },
            onDismiss = { penNameDialog = false },
        )
    }
}

private fun plural(count: Int, noun: String) =
    "$count $noun${if (count == 1) "" else "s"}"

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, LettersPalette.SearchBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔍", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search scenes & letters…", fontSize = 14.sp, color = LettersPalette.Hint)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = LettersPalette.Ink),
                cursorBrush = SolidColor(LettersPalette.Brown),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Text(
                "✕",
                fontSize = 16.sp,
                color = LettersPalette.Hint,
                modifier = Modifier.clickable { onQueryChange("") }.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    scenes: List<SceneSummary>,
    letters: List<LetterSummary>,
    hasResults: Boolean,
    onOpenScene: (String) -> Unit,
    onOpenLetter: (String) -> Unit,
) {
    if (!hasResults) {
        Text(
            "No results for \"$query\"",
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            fontSize = 14.sp,
            color = LettersPalette.Faint,
            textAlign = TextAlign.Center,
        )
        return
    }
    if (scenes.isNotEmpty()) {
        SectionLabel("Scenes")
        scenes.forEach { scene ->
            ResultRow(
                icon = "💬",
                title = scene.name,
                subtitle = "${plural(scene.characterCount, "character")} · " +
                    plural(scene.messageCount, "message"),
                onClick = { onOpenScene(scene.id) },
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
    if (letters.isNotEmpty()) {
        SectionLabel("Letters")
        letters.forEach { letter ->
            ResultRow(
                icon = Mood.fromSlug(letter.mood)?.emoji ?: if (letter.isSealed) "🔒" else "✉️",
                title = "Dear ${letter.recipient}",
                subtitle = letter.subject ?: "(no subject)",
                onClick = { onOpenLetter(letter.id) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = LettersPalette.Meta,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ResultRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, LettersPalette.CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LettersPalette.Title)
            Text(subtitle, fontSize = 12.sp, color = LettersPalette.Meta)
        }
        Text("›", fontSize = 18.sp, color = LettersPalette.Chevron)
    }
}

@Composable
private fun PromptCard(prompt: String, onWrite: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(LettersPalette.PromptA, LettersPalette.PromptB)),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            "✍️ TODAY'S PROMPT",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "\"$prompt\"",
            fontFamily = LoraFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable(onClick = onWrite)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text("Write a letter →", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FeatureTile(
    gradient: List<Color>,
    border: Color,
    iconBackground: Color,
    icon: String,
    title: String,
    titleColor: Color,
    subtitle: String,
    subtitleColor: Color,
    chevronColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(gradient), RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(iconBackground, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = titleColor)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 13.sp, color = subtitleColor)
        }
        Text("›", fontSize = 20.sp, color = chevronColor)
    }
}
