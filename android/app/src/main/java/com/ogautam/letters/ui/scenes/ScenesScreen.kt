package com.ogautam.letters.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
 * Placeholder for the scene list. The data layer and the chat renderer both exist; what is
 * missing is the editor that composes a scene, so the only thing to open here is the sample
 * the renderer was built against.
 */
@Composable
fun ScenesScreen(onBack: () -> Unit, onOpenSample: () -> Unit) {
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
                "Composing your own scene arrives with the editor.",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                color = LettersPalette.GreenMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .background(LettersPalette.Teal, RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenSample)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text("▶  Play the sample scene", fontSize = 14.sp, color = Color.White)
            }
        }
    }
}
