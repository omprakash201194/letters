package com.ogautam.letters.ui.scenes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.HeaderButton
import com.ogautam.letters.ui.common.NameDialog
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.common.ScreenTitle
import com.ogautam.letters.ui.scenes.AvatarBadge
import com.ogautam.letters.ui.theme.LettersPalette

/**
 * Who the scene is between, and what it is called. Nothing is written until the editor
 * saves, so backing out of here leaves nothing behind.
 */
@Composable
fun NewSceneScreen(
    storyId: String?,
    onBack: () -> Unit,
    onStart: (castIds: List<String>, title: String) -> Unit,
    viewModel: NewSceneViewModel = viewModel(
        key = "new-scene:${storyId ?: "loose"}",
        factory = NewSceneViewModel.factory(storyId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LettersPalette.HomeGround)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ScreenHeader(background = LettersPalette.Teal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(Color.White, onBack)
                Spacer(Modifier.width(6.dp))
                Column {
                    ScreenTitle("New scene", size = 16)
                    state.storyTitle?.let {
                        Text("in $it", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
            HeaderButton(
                label = "Start ▶",
                onClick = { onStart(state.selectedIds, state.title) },
                enabled = state.canStart,
                background = if (state.canStart) LettersPalette.HeaderBtn
                else Color.White.copy(alpha = 0.12f),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("WHAT IS IT CALLED", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LettersPalette.Meta, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (state.title.isEmpty()) {
                        Text("Untitled scene", fontSize = 15.sp, color = LettersPalette.Hint)
                    }
                    BasicTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        textStyle = TextStyle(fontSize = 15.sp, color = LettersPalette.Title),
                        cursorBrush = SolidColor(LettersPalette.Teal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("WHO IS IN IT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LettersPalette.Meta, letterSpacing = 1.sp)
                    Text(
                        "＋ someone new",
                        modifier = Modifier.clickable { adding = true }.padding(4.dp),
                        fontSize = 12.sp,
                        color = LettersPalette.Teal,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    state.notice ?: "The first person you pick speaks as you — green, on the right.",
                    fontSize = 12.sp,
                    color = if (state.notice != null) LettersPalette.Teal else LettersPalette.Meta,
                )
                Spacer(Modifier.height(8.dp))
            }

            items(state.everyone, key = CharacterEntity::id) { character ->
                val position = state.selectedIds.indexOf(character.id)
                CastPickRow(
                    character = character,
                    position = position,
                    suggested = character.id in state.suggestedIds,
                    onToggle = { viewModel.toggle(character.id) },
                )
            }

            if (!state.loading && state.everyone.isEmpty()) {
                item {
                    Text(
                        "Nobody in the library yet. Add the people this scene is between.",
                        fontSize = 13.sp,
                        color = LettersPalette.Meta,
                    )
                }
            }
        }
    }

    if (adding) {
        NameDialog(
            title = "Who is this?",
            initial = "",
            onConfirm = { viewModel.addCharacter(it); adding = false },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun CastPickRow(
    character: CharacterEntity,
    position: Int,
    suggested: Boolean,
    onToggle: () -> Unit,
) {
    val selected = position >= 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(character.color) else LettersPalette.CardBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBadge(character = character, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(character.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LettersPalette.Title)
            Text(
                when {
                    position == 0 -> "Speaking as you"
                    selected -> "In this scene"
                    suggested -> "Already in this story"
                    else -> "Tap to add"
                },
                fontSize = 12.sp,
                color = if (position == 0) LettersPalette.Teal else LettersPalette.Meta,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(character.color))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("${position + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
