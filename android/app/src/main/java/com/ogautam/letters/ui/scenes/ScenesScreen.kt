package com.ogautam.letters.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogautam.letters.ui.common.BackChevron
import com.ogautam.letters.ui.common.ScreenHeader
import com.ogautam.letters.ui.common.ScreenTitle
import com.ogautam.letters.ui.theme.LettersPalette

/**
 * Placeholder. The scenes data layer is already in place; the Canvas chat renderer it needs
 * is the next phase, and a half-built editor here would be worse than an honest gap.
 */
@Composable
fun ScenesScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LettersPalette.HomeGround),
    ) {
        ScreenHeader(background = LettersPalette.Teal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(Color.White, onBack)
                Spacer(Modifier.width(6.dp))
                ScreenTitle("Chat Scenes")
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("💬", fontSize = 48.sp)
            Text(
                "Not here yet",
                fontSize = 18.sp,
                color = LettersPalette.GreenInk,
            )
            Text(
                "Scene building arrives with the chat renderer.",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                color = LettersPalette.GreenMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
