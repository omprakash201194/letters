package com.ogautam.letters.data.entity

/**
 * The eight character colors from the web app, in order. A character's color is
 * assigned by position: `COLORS[index % COLORS.size]`.
 */
object CharacterPalette {

    val COLORS: List<Int> = listOf(
        0xFFE91E63.toInt(),
        0xFF9C27B0.toInt(),
        0xFF3F51B5.toInt(),
        0xFF2196F3.toInt(),
        0xFF009688.toInt(),
        0xFFFF5722.toInt(),
        0xFF795548.toInt(),
        0xFF607D8B.toInt(),
    )

    fun colorForIndex(index: Int): Int = COLORS[index.mod(COLORS.size)]

    /** Up to two uppercase initials, used when a character has no avatar image. */
    fun initials(name: String): String =
        name.trim()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
}
