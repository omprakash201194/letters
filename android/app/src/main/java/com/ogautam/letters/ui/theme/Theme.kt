package com.ogautam.letters.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Single light theme, deliberately. The letter is a sheet of paper and the chat is a
 * WhatsApp screenshot — both are lit surfaces, and a dark variant would have to
 * reinvent rather than invert them. Revisit as its own design pass, not as a toggle.
 */
private val LightScheme = lightColorScheme(
    primary = LettersPalette.Brown,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = LettersPalette.Teal,
    background = LettersPalette.HomeGround,
    onBackground = LettersPalette.BrownDeep,
    surface = LettersPalette.Paper,
    onSurface = LettersPalette.Ink,
    error = LettersPalette.Danger,
)

@Composable
fun LettersTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = LightScheme, content = content)
}
