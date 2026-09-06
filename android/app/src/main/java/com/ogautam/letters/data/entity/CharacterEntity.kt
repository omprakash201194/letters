package com.ogautam.letters.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A character in the library, reusable across scenes.
 *
 * Colour lives here rather than being derived from a position, so the same person is the
 * same colour in every scene they appear in. Being the outgoing participant is *not* a
 * property of a character — that is a role a scene assigns, held on [SceneCastEntity].
 * [isSelf] only marks who the picker should offer first.
 *
 * Editing a character here never rewrites scenes already built: a message carries its own
 * snapshot of the sender's name, colour and avatar, and that snapshot is what plays back.
 */
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int,
    val avatarPath: String? = null,
    /** Marks the character who is usually "you". At most one is expected, none is fine. */
    val isSelf: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
