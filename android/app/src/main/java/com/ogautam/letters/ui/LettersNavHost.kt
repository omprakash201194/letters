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
import com.ogautam.letters.ui.scenes.ScenesScreen
import com.ogautam.letters.ui.scenes.editor.SceneEditorScreen

object Routes {
    const val HOME = "home"
    const val LETTERS = "letters"
    const val SCENES = "scenes"
    const val NEW_SCENE = "scene/new"
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
            ScenesScreen(
                onBack = navController::popBackStack,
                onOpenScene = { navController.navigate(Routes.editScene(it)) },
                onNewScene = { navController.navigate(Routes.NEW_SCENE) },
            )
        }

        composable(Routes.NEW_SCENE) {
            SceneEditorScreen(
                sceneId = null,
                onBack = { navController.popBackStack() },
                avatarFor = avatars::load,
            )
        }

        composable(Routes.EDIT_SCENE) { entry ->
            SceneEditorScreen(
                sceneId = entry.arguments?.getString("sceneId"),
                onBack = { navController.popBackStack() },
                avatarFor = avatars::load,
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
