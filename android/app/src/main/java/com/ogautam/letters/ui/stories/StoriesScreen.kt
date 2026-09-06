package com.ogautam.letters.ui.stories

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
import com.ogautam.letters.data.dao.StorySummary
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.ConfirmDialog
import com.ogautam.letters.ui.common.HeaderButton
import com.ogautam.letters.ui.common.NameDialog
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.common.ScreenTitle
import com.ogautam.letters.ui.common.formatShort
import com.ogautam.letters.ui.theme.LettersPalette
import java.time.ZoneId

/**
 * Stories are folders. Scenes that belong to none of them sit underneath, so nothing is
 * ever hidden behind a decision about where to file it.
 */
@Composable
fun StoriesScreen(
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenScene: (String) -> Unit,
    onNewScene: (String?) -> Unit,
    onOpenCharacters: () -> Unit,
    viewModel: StoryListViewModel = viewModel(factory = StoryListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<StorySummary?>(null) }
    var deleting by remember { mutableStateOf<StorySummary?>(null) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderButton("👥", onClick = onOpenCharacters)
                HeaderButton("＋ Scene", onClick = { onNewScene(null) })
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Stories")
                    Text(
                        "＋ new story",
                        modifier = Modifier.clickable { creating = true }.padding(4.dp),
                        fontSize = 12.sp,
                        color = LettersPalette.Teal,
                    )
                }
            }

            if (state.stories.isEmpty()) {
                item {
                    Text(
                        "A story is a folder for scenes that belong together.",
                        fontSize = 13.sp,
                        color = LettersPalette.Meta,
                    )
                }
            }

            items(state.stories, key = StorySummary::id) { story ->
                StoryRow(
                    story = story,
                    onClick = { onOpenStory(story.id) },
                    onRename = { renaming = story },
                    onDelete = { deleting = story },
                )
            }

            if (state.looseScenes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Not in a story")
                }
                items(state.looseScenes, key = SceneSummary::id) { scene ->
                    LooseSceneRow(scene = scene, onClick = { onOpenScene(scene.id) })
                }
            }

            if (!state.loading && state.stories.isEmpty() && state.looseScenes.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Name this story",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { title -> viewModel.create(title) { creating = false }; creating = false },
            onDismiss = { creating = false },
        )
    }

    renaming?.let { story ->
        NameDialog(
            title = "Rename story",
            initial = story.title,
            confirmLabel = "Rename",
            onConfirm = { viewModel.rename(story.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { story ->
        ConfirmDialog(
            title = "Delete \"${story.title}\"?",
            body = "Its ${story.sceneCount} " +
                "${if (story.sceneCount == 1) "scene stays" else "scenes stay"} — they move " +
                "back out of the folder rather than being deleted.",
            confirmLabel = "Delete story",
            destructive = true,
            onConfirm = { viewModel.delete(story.id); deleting = null },
            onDismiss = { deleting = null },
        )
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
    )
}

@Composable
private fun StoryRow(
    story: StorySummary,
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
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(LettersPalette.TileGreenB),
            contentAlignment = Alignment.Center,
        ) {
            Text("📁", fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(story.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LettersPalette.Title)
            Text(
                "${story.sceneCount} ${if (story.sceneCount == 1) "scene" else "scenes"} · " +
                    formatShort(story.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate()),
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
private fun LooseSceneRow(scene: SceneSummary, onClick: () -> Unit) {
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
            Text(
                "${scene.characterCount} characters · ${scene.messageCount} messages",
                fontSize = 12.sp,
                color = LettersPalette.Meta,
            )
        }
        Text("›", fontSize = 18.sp, color = LettersPalette.Chevron)
    }
}
