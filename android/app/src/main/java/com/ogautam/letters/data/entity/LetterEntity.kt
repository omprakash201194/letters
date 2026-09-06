package com.ogautam.letters.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "letters")
data class LetterEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val recipient: String,
    val subject: String? = null,
    val content: String? = null,
    /** Mood slug, e.g. "grateful". See [com.ogautam.letters.data.entity.Mood]. */
    val mood: String? = null,
    val letterDate: LocalDate? = null,
    /** Null means not sealed. A date in the future means the body is withheld by the UI. */
    val sealedUntil: LocalDate? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    /**
     * Sealing is a UI convention, not encryption — the row always holds the full body.
     * Never describe it to the user as protection.
     */
    fun isSealedOn(today: LocalDate): Boolean =
        sealedUntil?.isAfter(today) == true
}
