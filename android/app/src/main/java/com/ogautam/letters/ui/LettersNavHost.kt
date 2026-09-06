package com.ogautam.letters.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ogautam.letters.ui.home.HomeScreen
import com.ogautam.letters.ui.letters.LetterEditorScreen
import com.ogautam.letters.ui.letters.LettersScreen
import com.ogautam.letters.ui.scenes.ScenesScreen

object Routes {
    const val HOME = "home"
    const val LETTERS = "letters"
    const val SCENES = "scenes"
    const val NEW_LETTER = "letter/new"
    const val EDIT_LETTER = "letter/edit/{letterId}"

    fun editLetter(id: String) = "letter/edit/$id"
}

@Composable
fun LettersNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenLetters = { navController.navigate(Routes.LETTERS) },
                onOpenScenes = { navController.navigate(Routes.SCENES) },
                onOpenLetter = { navController.navigate(Routes.editLetter(it)) },
                onNewLetter = { navController.navigate(Routes.NEW_LETTER) },
                onOpenScene = { navController.navigate(Routes.SCENES) },
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
            ScenesScreen(onBack = navController::popBackStack)
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
