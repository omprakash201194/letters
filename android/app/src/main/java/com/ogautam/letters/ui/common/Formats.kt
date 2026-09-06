package com.ogautam.letters.ui.common

import java.time.LocalDate

private val SHORT_MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val LONG_MONTHS = arrayOf(
    "January", "February", "March", "April", "May", "June", "July",
    "August", "September", "October", "November", "December",
)

/**
 * Dates are formatted with a fixed English month table rather than the device locale,
 * on purpose: the web app hardcoded these tables, and a letter that reads
 * "May 22, 2026" on one device and "22.05.2026" on another is not the same object.
 */
fun formatShort(date: LocalDate): String =
    "${SHORT_MONTHS[date.monthValue - 1]} ${date.dayOfMonth}, ${date.year}"

fun formatLong(date: LocalDate): String =
    "${LONG_MONTHS[date.monthValue - 1]} ${date.dayOfMonth}, ${date.year}"

/** Blank text is zero words; everything else splits on runs of whitespace. */
fun countWords(text: String): Int {
    val trimmed = text.trim()
    return if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size
}
