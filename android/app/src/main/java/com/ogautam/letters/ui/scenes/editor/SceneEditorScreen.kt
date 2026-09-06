package com.ogautam.letters.ui.scenes.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.entity.SceneCharacterEntity
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.ConfirmDialog
import com.ogautam.letters.ui.common.HeaderButton
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.scenes.ScenePlayerScreen
import com.ogautam.letters.ui.scenes.chat.ChatCanvas
import com.ogautam.letters.ui.scenes.chat.ChatTheme
import com.ogautam.letters.ui.scenes.chat.staticPlayback
import com.ogautam.letters.ui.theme.LettersPalette

/**
 * One route, three steps: setup → composer → preview, as the web app had it. Keeping them
 * in one screen keeps the scene in one place — it is not saved between steps.
 */
@Composable
fun SceneEditorScreen(
    sceneId: String?,
    onBack: () -> Unit,
    avatarFor: (String?) -> Bitmap?,
    viewModel: SceneEditorViewModel = viewModel(factory = SceneEditorViewModel.factory(sceneId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(state.missing) { if (state.missing) onBack() }

    val leave = {
        when {
            state.step == EditorStep.PREVIEW -> viewModel.goTo(EditorStep.COMPOSER)
            state.step == EditorStep.COMPOSER -> viewModel.goTo(EditorStep.SETUP)
            state.isDirty -> confirmLeave = true
            else -> onBack()
        }
    }
    BackHandler(enabled = true) { leave() }

    if (state.loading) return

    when (state.step) {
        EditorStep.SETUP -> SetupStep(state, viewModel, leave)
        EditorStep.COMPOSER -> ComposerStep(state, viewModel, leave, avatarFor)
        EditorStep.PREVIEW -> ScenePlayerScreen(
            onBack = leave,
            avatarFor = avatarFor,
            sceneName = state.name,
            messages = state.messages,
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave without saving?",
            body = "This scene has changes that have not been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { confirmLeave = false; onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
}

// ── setup ──────────────────────────────────────────────────────────────────

@Composable
private fun SetupStep(
    state: SceneEditorUiState,
    viewModel: SceneEditorViewModel,
    onBack: () -> Unit,
) {
    var namingCharacter by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SceneCharacterEntity?>(null) }
    var avatarTarget by remember { mutableStateOf<String?>(null) }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val charId = avatarTarget
        if (uri != null && charId != null) viewModel.setAvatar(charId, uri)
        avatarTarget = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LettersPalette.HomeGround)
            .navigationBarsPadding(),
    ) {
        ScreenHeader(background = LettersPalette.Teal) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                BackChevron(Color.White, onBack)
                Spacer(Modifier.width(4.dp))
                SceneNameField(state.name, viewModel::onNameChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SaveButton(state, viewModel)
                HeaderButton(
                    label = "Next →",
                    onClick = { viewModel.goTo(EditorStep.COMPOSER) },
                    enabled = state.canCompose,
                    background = if (state.canCompose) LettersPalette.HeaderBtn
                    else Color.White.copy(alpha = 0.12f),
                )
            }
        }

        if (state.characters.isEmpty()) {
            Text(
                "Add at least one character. The first is \"You\" — outgoing, green bubbles, " +
                    "on the right.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
                fontSize = 14.sp,
                color = LettersPalette.Meta,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.characters, key = SceneCharacterEntity::id) { character ->
                CharacterRow(
                    character = character,
                    isOutgoing = character.id == state.outgoingCharId,
                    onPickAvatar = {
                        avatarTarget = character.id
                        pickAvatar.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRename = { renaming = character },
                    onRemove = { viewModel.removeCharacter(character.id) },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LettersPalette.Teal)
                    .clickable { namingCharacter = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("＋  Add character", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (namingCharacter) {
        NameDialog(
            title = if (state.characters.isEmpty()) "Who are you in this scene?" else "Character name",
            initial = if (state.characters.isEmpty()) "You" else "",
            onConfirm = { viewModel.addCharacter(it); namingCharacter = false },
            onDismiss = { namingCharacter = false },
        )
    }

    renaming?.let { character ->
        NameDialog(
            title = "Rename character",
            initial = character.name,
            onConfirm = { viewModel.renameCharacter(character.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun CharacterRow(
    character: SceneCharacterEntity,
    isOutgoing: Boolean,
    onPickAvatar: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable(onClick = onRename)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBadge(
            character = character,
            size = 44.dp,
            modifier = Modifier.clickable(onClick = onPickAvatar),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(character.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                if (isOutgoing) "You · outgoing · tap the avatar for a photo"
                else "Incoming · tap the avatar for a photo",
                fontSize = 12.sp,
                color = LettersPalette.Meta,
            )
        }
        Text(
            "✕",
            modifier = Modifier.clickable(onClick = onRemove).padding(8.dp),
            fontSize = 18.sp,
            color = LettersPalette.Chevron,
        )
    }
}

// ── composer ───────────────────────────────────────────────────────────────

@Composable
private fun ComposerStep(
    state: SceneEditorUiState,
    viewModel: SceneEditorViewModel,
    onBack: () -> Unit,
    avatarFor: (String?) -> Bitmap?,
) {
    var pendingDelete by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(ChatTheme.BACKGROUND))
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ScreenHeader(background = LettersPalette.Teal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(Color.White, onBack)
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(state.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        "${state.characters.size} characters · ${state.messages.size} messages",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SaveButton(state, viewModel)
                HeaderButton(
                    label = "Preview ▶",
                    onClick = { viewModel.goTo(EditorStep.PREVIEW) },
                    enabled = state.canPreview,
                    background = if (state.canPreview) LettersPalette.HeaderBtn
                    else Color.White.copy(alpha = 0.12f),
                )
            }
        }

        ChatCanvas(
            messages = state.messages,
            playback = staticPlayback(state.messages.size),
            elapsedMs = 0L,
            modifier = Modifier.weight(1f),
            avatarFor = avatarFor,
            onMessageTap = { pendingDelete = it },
        )

        Column(Modifier.fillMaxWidth().background(Color(0xFFF0F0F0))) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.characters.forEach { character ->
                    val selected = character.id == state.selectedCharId
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) Color(character.color) else Color(0xFFE0E0E0),
                            )
                            .clickable { viewModel.selectCharacter(character.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarBadge(character = character, size = 18.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            character.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) Color.White else Color(0xFF444444),
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (state.draft.isEmpty()) {
                        Text(
                            "Type a message…",
                            fontSize = 14.5.sp,
                            color = LettersPalette.Hint,
                        )
                    }
                    BasicTextField(
                        value = state.draft,
                        onValueChange = viewModel::onDraftChange,
                        // reason: the keyboard's action key sends, as Enter did on the web.
                        // Without this it inserts a newline into a message instead.
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                        textStyle = TextStyle(fontSize = 14.5.sp, color = Color(ChatTheme.BUBBLE_TEXT)),
                        cursorBrush = SolidColor(LettersPalette.Teal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                val canSend = state.draft.isNotBlank() && state.selectedCharId != null
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) LettersPalette.Teal else Color(0xFFCCCCCC))
                        .clickable(enabled = canSend) { viewModel.send() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("➤", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }

    pendingDelete?.let { index ->
        ConfirmDialog(
            title = "Delete this message?",
            body = state.messages.getOrNull(index)?.text,
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deleteMessage(index); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

// ── shared bits ────────────────────────────────────────────────────────────

@Composable
private fun SaveButton(state: SceneEditorUiState, viewModel: SceneEditorViewModel) {
    LaunchedEffect(state.justSaved) {
        if (state.justSaved) {
            kotlinx.coroutines.delay(2_000)
            viewModel.acknowledgeSaved()
        }
    }
    val enabled = !state.saving && state.canCompose
    HeaderButton(
        label = when {
            state.saving -> "…"
            state.justSaved -> "✓ Saved"
            else -> "💾 Save"
        },
        onClick = viewModel::save,
        enabled = enabled,
        background = when {
            state.justSaved -> LettersPalette.Saved
            enabled -> LettersPalette.HeaderBtn
            // reason: a button that cannot be pressed should not look like one that can
            else -> Color.White.copy(alpha = 0.12f)
        },
    )
}

@Composable
private fun SceneNameField(name: String, onNameChange: (String) -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            ),
            cursorBrush = SolidColor(Color.White),
        )
    }
}

@Composable
private fun AvatarBadge(
    character: SceneCharacterEntity,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = character.avatarPath?.let { path ->
        remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
    }
    Box(
        modifier.size(size).clip(CircleShape).background(Color(character.color)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = character.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                CharacterPalette.initials(character.name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = LettersPalette.BrownDeep) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) { Text("OK", color = LettersPalette.Teal) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", color = LettersPalette.Muted)
            }
        },
        containerColor = Color.White,
    )
}
