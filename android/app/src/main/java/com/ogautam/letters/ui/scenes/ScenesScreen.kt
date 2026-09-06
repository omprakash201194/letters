package com.ogautam.letters.ui.scenes

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
import com.ogautam.letters.data.dao.SceneSummary
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.ConfirmDialog
import com.ogautam.letters.ui.common.HeaderButton
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.common.ScreenTitle
import com.ogautam.letters.ui.common.formatShort
import com.ogautam.letters.ui.theme.LettersPalette
import java.time.ZoneId

@Composable
fun ScenesScreen(
    onBack: () -> Unit,
    onOpenScene: (String) -> Unit,
    onNewScene: () -> Unit,
    viewModel: SceneListViewModel = viewModel(factory = SceneListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<SceneSummary?>(null) }
    var renaming by remember { mutableStateOf<SceneSummary?>(null) }

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
                ScreenTitle("Chat Scenes")
            }
            HeaderButton("＋ New", onNewScene)
        }

        if (!state.loading && state.scenes.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🎬", fontSize = 48.sp)
                Text("No scenes yet", fontSize = 18.sp, color = LettersPalette.GreenInk)
                Text(
                    "Build a conversation message by message, then watch it play.",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    color = LettersPalette.GreenMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.scenes, key = SceneSummary::id) { scene ->
                    SceneRow(
                        scene = scene,
                        onClick = { onOpenScene(scene.id) },
                        onRename = { renaming = scene },
                        onDelete = { pendingDelete = scene },
                    )
                }
            }
        }
    }

    pendingDelete?.let { scene ->
        ConfirmDialog(
            title = "Delete this scene?",
            body = "\"${scene.name}\" and all of its messages. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.delete(scene.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }

    renaming?.let { scene ->
        RenameDialog(
            initial = scene.name,
            onConfirm = { viewModel.rename(scene.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun SceneRow(
    scene: SceneSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(LettersPalette.Teal),
            contentAlignment = Alignment.Center,
        ) {
            Text("💬", fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(scene.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LettersPalette.Title)
            Spacer(Modifier.height(2.dp))
            Text(
                "${scene.characterCount} characters · ${scene.messageCount} messages · " +
                    formatShort(scene.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                fontSize = 12.sp,
                color = LettersPalette.Meta,
            )
        }
        Text(
            "✎",
            modifier = Modifier.clickable(onClick = onRename).padding(8.dp),
            fontSize = 16.sp,
            color = LettersPalette.Meta,
        )
        Text(
            "✕",
            modifier = Modifier.clickable(onClick = onDelete).padding(8.dp),
            fontSize = 16.sp,
            color = LettersPalette.Chevron,
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename scene", color = LettersPalette.BrownDeep) },
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
            ) { Text("Rename", color = LettersPalette.Teal) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", color = LettersPalette.Muted)
            }
        },
        containerColor = Color.White,
    )
}
