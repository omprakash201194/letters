package com.ogautam.letters.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colors are lifted verbatim from the web app so the two stay recognisably the same
 * product. Letters is warm brown-on-paper; Scenes is WhatsApp green.
 */
object LettersPalette {
    // Letters — paper and ink
    val Brown       = Color(0xFF5C4A3A)   // headers, primary action
    val BrownDeep   = Color(0xFF3D2B1F)   // "Dear" line, strong body text
    val Ink         = Color(0xFF2D1F0F)   // letter body
    val Subject     = Color(0xFF6B5040)
    val Muted       = Color(0xFF9A8060)   // sign-off, secondary text
    val Faint       = Color(0xFFB8A080)   // hints, sealed captions
    val Whisper     = Color(0xFFC0B090)   // word count, tagline

    val Paper       = Color(0xFFFFFEF9)
    val PaperBorder = Color(0xFFE2D5C0)
    val PaperRule   = Color(0xFFE8DDC8)   // the ruled lines
    val MarginRule  = Color(0x40DC503C)   // rgba(220,80,60,.25)
    val Ground      = Color(0xFFF5F0E8)   // screen behind the paper

    // Home shell
    val HomeGround  = Color(0xFFF7F5F2)
    val HomeBorder  = Color(0xFFE8E0D8)
    val CardBorder  = Color(0xFFE8E0D0)
    val TileBrownA  = Color(0xFFFDF8F0)
    val TileBrownB  = Color(0xFFF8F0E0)
    val TileBrownEdge = Color(0xFFE2D0B0)
    val PromptA     = Color(0xFF5C4A3A)
    val PromptB     = Color(0xFF7A6250)

    // Scenes — WhatsApp
    val Teal        = Color(0xFF075E54)
    val TileGreenA  = Color(0xFFF0FAF0)
    val TileGreenB  = Color(0xFFE8F5E8)
    val TileGreenEdge = Color(0xFFC8E6C8)
    val GreenInk    = Color(0xFF1A3D2B)
    val GreenMuted  = Color(0xFF5A8A70)

    val Danger      = Color(0xFFC0392B)

    // Shared chrome — lists, search, menus
    val Title       = Color(0xFF2D2010)   // app title, tile headings
    val Meta        = Color(0xFFA09080)   // tile subtitles, section labels
    val ListDate    = Color(0xFFA09070)
    val Preview     = Color(0xFF8A7060)   // list content preview
    val Hint        = Color(0xFFB0A090)   // search icon, placeholder
    val SearchBorder = Color(0xFFE0D8CC)
    val Divider     = Color(0xFFF0E8D8)
    val Chevron     = Color(0xFFCCCCCC)
    val ChevronGreen = Color(0xFFA0C8B0)
    val ChevronBrown = Color(0xFFC8A880)
    val TileLetters = Color(0xFF9A7A5A)   // letters tile subtitle
    val SealedFaint = Color(0xFFC8B898)   // "come back then"

    // Editor
    val DashStrong  = Color(0xFFC8B090)   // recipient underline
    val DashLight   = Color(0xFFE0CDB0)   // subject underline
    val ToggleOff   = Color(0xFFD0C8BC)
    val SealField   = Color(0xFFFDF8F0)
    val SealBorder  = Color(0xFFD0C0A0)
    val SealLabel   = Color(0xFF7A6250)
    val Saved       = Color(0x4D64C864)   // rgba(100,200,100,.3)
    val OnHeaderDim = Color(0x80FFFFFF)
    val HeaderBtn   = Color(0x33FFFFFF)
}
