package com.ogautam.letters.ui.scenes

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.entity.CharacterPalette

/** A character's face, or their initials on their colour when they have no photo. */
@Composable
fun AvatarBadge(
    character: CharacterEntity,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = character.avatarPath?.let { path ->
        remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    Box(
        modifier.size(size).clip(CircleShape).background(Color(character.color)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = character.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                CharacterPalette.initials(character.name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}
