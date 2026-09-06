package com.ogautam.letters.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ogautam.letters.LettersApplication
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ogautam.letters.ui.home.HomeScreen
import com.ogautam.letters.ui.letters.LetterEditorScreen
import com.ogautam.letters.ui.letters.LettersScreen
import com.ogautam.letters.ui.characters.CharacterLibraryScreen
import com.ogautam.letters.ui.scenes.ScenesScreen
import com.ogautam.letters.ui.scenes.editor.NewSceneScreen
import com.ogautam.letters.ui.stories.StoriesScreen
import com.ogautam.letters.ui.scenes.editor.SceneEditorScreen

object Routes {
    const val HOME = "home"
    const val LETTERS = "letters"
    const val SCENES = "scenes"
    const val CHARACTERS = "characters"
    const val STORY = "story/{storyId}"
    const val NEW_SCENE_WIZARD = "scene/new/wizard?storyId={storyId}"

    fun story(id: String) = "story/$id"

    fun newSceneWizard(storyId: String?) =
        "scene/new/wizard?storyId=${storyId.orEmpty()}"
    const val NEW_SCENE = "scene/new?storyId={storyId}&cast={cast}&title={title}"

    fun newScene(storyId: String?, castIds: List<String>, title: String) =
        "scene/new?storyId=${storyId.orEmpty()}" +
            "&cast=${castIds.joinToString(",")}" +
            "&title=${android.net.Uri.encode(title)}"
    const val EDIT_SCENE = "scene/edit/{sceneId}"

    fun editScene(id: String) = "scene/edit/$id"
    const val NEW_LETTER = "letter/new"
    const val EDIT_LETTER = "letter/edit/{letterId}"

    fun editLetter(id: String) = "letter/edit/$id"
}

@Composable
fun LettersNavHost(navController: NavHostController = rememberNavController()) {
    val avatars = (LocalContext.current.applicationContext as LettersApplication).avatars

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenLetters = { navController.navigate(Routes.LETTERS) },
                onOpenScenes = { navController.navigate(Routes.SCENES) },
                onOpenLetter = { navController.navigate(Routes.editLetter(it)) },
                onNewLetter = { navController.navigate(Routes.NEW_LETTER) },
                onOpenScene = { navController.navigate(Routes.editScene(it)) },
            )
        }

        composable(Routes.LETTERS) {
            LettersScreen(
                onBack = navController::popBackStack,
                onOpenLetter = { navController.navigate(Routes.editLetter(it)) },
                onNewLetter = { navController.navigate(Routes.NEW_LETTER) },
            )
        }

        composable(Routes.SCENES) {
            StoriesScreen(
                onBack = navController::popBackStack,
                onOpenStory = { navController.navigate(Routes.story(it)) },
                onOpenScene = { navController.navigate(Routes.editScene(it)) },
                onNewScene = { navController.navigate(Routes.newSceneWizard(it)) },
                onOpenCharacters = { navController.navigate(Routes.CHARACTERS) },
            )
        }

        composable(Routes.CHARACTERS) {
            CharacterLibraryScreen(onBack = navController::popBackStack)
        }

        composable(Routes.STORY) { entry ->
            val storyId = entry.arguments?.getString("storyId")
            ScenesScreen(
                onBack = navController::popBackStack,
                onOpenScene = { navController.navigate(Routes.editScene(it)) },
                onNewScene = { navController.navigate(Routes.newSceneWizard(storyId)) },
                title = "Story",
                storyId = storyId,
            )
        }

        composable(Routes.NEW_SCENE_WIZARD) { entry ->
            val storyId = entry.arguments?.getString("storyId")?.ifBlank { null }
            NewSceneScreen(
                storyId = storyId,
                onBack = navController::popBackStack,
                onStart = { castIds, title ->
                    navController.navigate(Routes.newScene(storyId, castIds, title)) {
                        popUpTo(Routes.NEW_SCENE_WIZARD) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.NEW_SCENE) { entry ->
            SceneEditorScreen(
                sceneId = null,
                onBack = { navController.popBackStack() },
                avatarFor = avatars::load,
                onPickCast = { navController.navigate(Routes.CHARACTERS) },
                storyId = entry.arguments?.getString("storyId")?.ifBlank { null },
                castIds = entry.arguments?.getString("cast")
                    ?.split(",")
                    ?.filter(String::isNotBlank)
                    .orEmpty(),
                initialName = entry.arguments?.getString("title"),
            )
        }

        composable(Routes.EDIT_SCENE) { entry ->
            SceneEditorScreen(
                sceneId = entry.arguments?.getString("sceneId"),
                onBack = { navController.popBackStack() },
                avatarFor = avatars::load,
                onPickCast = { navController.navigate(Routes.CHARACTERS) },
            )
        }

        composable(Routes.NEW_LETTER) {
            LetterEditorScreen(letterId = null, onBack = { navController.popBackStack() })
        }

        composable(Routes.EDIT_LETTER) { entry ->
            LetterEditorScreen(
                letterId = entry.arguments?.getString("letterId"),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
