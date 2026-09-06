package com.ogautam.letters.data.repository

import java.time.Instant
import java.time.LocalDate

/** What the letters list and search results need — never the full body. */
data class LetterSummary(
    val id: String,
    val recipient: String,
    val subject: String?,
    val contentPreview: String?,
    val mood: String?,
    val letterDate: LocalDate?,
    val sealedUntil: LocalDate?,
    val isSealed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
