package com.ogautam.letters.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ogautam.letters.R

/**
 * Lora stands in for the web app's Georgia, which Android does not ship.
 * Both files are variable fonts with a weight axis, so each face is the same
 * resource at a different variation setting — `FontVariation` needs API 26,
 * which is our minSdk.
 */
@OptIn(ExperimentalTextApi::class)
private fun lora(resId: Int, weight: FontWeight, style: FontStyle) = Font(
    resId = resId,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val LoraFamily = FontFamily(
    lora(R.font.lora, FontWeight.Normal, FontStyle.Normal),
    lora(R.font.lora, FontWeight.SemiBold, FontStyle.Normal),
    lora(R.font.lora, FontWeight.Bold, FontStyle.Normal),
    lora(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
    lora(R.font.lora_italic, FontWeight.SemiBold, FontStyle.Italic),
)
