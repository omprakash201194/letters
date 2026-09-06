package com.ogautam.letters.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.ogautam.letters.ui.theme.LettersPalette
import com.ogautam.letters.ui.theme.LoraFamily

/**
 * The name signed at the bottom of every letter. Clearing it falls back to the default
 * rather than signing a letter with an empty line.
 */
@Composable
fun PenNameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pen name", fontFamily = LoraFamily, color = LettersPalette.BrownDeep) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text("Signed at the end of every letter") },
                )
                Text(
                    "— ${value.trim().ifBlank { "You" }}",
                    fontFamily = LoraFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = LettersPalette.Muted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("Save", color = LettersPalette.Brown)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = LettersPalette.Muted) }
        },
        containerColor = LettersPalette.Paper,
    )
}
