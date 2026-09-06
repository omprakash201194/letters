package com.ogautam.letters.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogautam.letters.ui.theme.LettersPalette
import com.ogautam.letters.ui.theme.LoraFamily

/** The 56dp bar every screen wears. Ground and ink change; the geometry does not. */
@Composable
fun ScreenHeader(
    background: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) { content() }
}

/** The back chevron used on both letter screens. */
@Composable
fun BackChevron(tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text("‹", fontSize = 24.sp, color = tint, fontWeight = FontWeight.Normal)
    }
}

/** A translucent pill button on a colored header. */
@Composable
fun HeaderButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = LettersPalette.HeaderBtn,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ScreenTitle(text: String, color: Color = Color.White, size: Int = 17) {
    Text(
        text,
        fontFamily = LoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
        color = color,
    )
}

/** A statement the user can only acknowledge — no second button, nothing to cancel. */
@Composable
fun MessageDialog(title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = LoraFamily, color = LettersPalette.BrownDeep) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = LettersPalette.Brown) }
        },
        containerColor = LettersPalette.Paper,
    )
}

/** The header background reaches under the status bar; only its content is inset. */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = LoraFamily, color = LettersPalette.BrownDeep) },
        text = body?.let { { Text(it, color = LettersPalette.Subject) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) LettersPalette.Danger else LettersPalette.Brown,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = LettersPalette.Muted) }
        },
        containerColor = LettersPalette.Paper,
    )
}
