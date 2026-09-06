package com.ogautam.letters.ui.characters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.ConfirmDialog
import com.ogautam.letters.ui.common.HeaderButton
import com.ogautam.letters.ui.common.NameDialog
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.common.ScreenTitle
import com.ogautam.letters.ui.scenes.AvatarBadge
import com.ogautam.letters.ui.theme.LettersPalette

/**
 * Everyone you have written, kept between scenes. Editing someone here changes how they
 * appear in scenes from now on; scenes already written keep the person as they were.
 */
@Composable
fun CharacterLibraryScreen(
    onBack: () -> Unit,
    viewModel: CharacterLibraryViewModel = viewModel(factory = CharacterLibraryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deletePrompt by viewModel.deletePrompt.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CharacterEntity?>(null) }
    var avatarTarget by remember { mutableStateOf<CharacterEntity?>(null) }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = avatarTarget
        if (uri != null && target != null) viewModel.setAvatar(target, uri)
        avatarTarget = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LettersPalette.HomeGround)
            .navigationBarsPadding(),
    ) {
        ScreenHeader(background = LettersPalette.Teal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(Color.White, onBack)
                Spacer(Modifier.width(6.dp))
                ScreenTitle("Characters")
            }
            HeaderButton("＋ New", onClick = { adding = true })
        }

        if (!state.loading && state.characters.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("👥", fontSize = 48.sp)
                Text("Nobody yet", fontSize = 18.sp, color = LettersPalette.GreenInk)
                Text(
                    "Add the people your scenes are between. They stay here, so you never " +
                        "have to type them twice.",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    color = LettersPalette.GreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.characters, key = CharacterEntity::id) { character ->
                    CharacterCard(
                        character = character,
                        onRename = { editing = character },
                        onPickAvatar = {
                            avatarTarget = character
                            pickAvatar.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onColor = { viewModel.setColor(character, it) },
                        onMakeSelf = { viewModel.setSelf(character) },
                        onDelete = { viewModel.askToDelete(character) },
                    )
                }
            }
        }
    }

    if (adding) {
        NameDialog(
            title = "Who is this?",
            initial = "",
            onConfirm = { viewModel.create(it, isSelf = state.characters.isEmpty()); adding = false },
            onDismiss = { adding = false },
        )
    }

    editing?.let { character ->
        NameDialog(
            title = "Rename",
            initial = character.name,
            onConfirm = { viewModel.rename(character, it); editing = null },
            onDismiss = { editing = null },
        )
    }

    deletePrompt?.let { prompt ->
        ConfirmDialog(
            title = "Remove ${prompt.character.name}?",
            body = if (prompt.sceneCount == 0) {
                "They are not in any scene."
            } else {
                "They appear in ${prompt.sceneCount} " +
                    "${if (prompt.sceneCount == 1) "scene" else "scenes"}. Those scenes keep " +
                    "everything they said — they just cannot say anything new."
            },
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun CharacterCard(
    character: CharacterEntity,
    onRename: () -> Unit,
    onPickAvatar: () -> Unit,
    onColor: (Int) -> Unit,
    onMakeSelf: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBadge(
                character = character,
                size = 44.dp,
                modifier = Modifier.clickable(onClick = onPickAvatar),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onRename)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(character.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (character.isSelf) {
                        Spacer(Modifier.width(6.dp))
                        Text("you", fontSize = 11.sp, color = LettersPalette.Teal)
                    }
                }
                Text(
                    if (character.isSelf) "Tap the name to rename, the face for a photo"
                    else "Tap to rename · hold a colour to make them you",
                    fontSize = 12.sp,
                    color = LettersPalette.Meta,
                )
            }
            Text(
                "✕",
                modifier = Modifier.clickable(onClick = onDelete).padding(8.dp),
                fontSize = 16.sp,
                color = LettersPalette.Chevron,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CharacterPalette.COLORS.forEach { color ->
                Box(
                    Modifier
                        .size(if (color == character.color) 26.dp else 22.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .clickable { onColor(color) },
                )
            }
            if (!character.isSelf) {
                Spacer(Modifier.weight(1f))
                Text(
                    "make me",
                    modifier = Modifier.clickable(onClick = onMakeSelf),
                    fontSize = 12.sp,
                    color = LettersPalette.Teal,
                )
            }
        }
    }
}
