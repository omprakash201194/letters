package com.ogautam.letters.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A folder of scenes. Deliberately not ordered — a story remembers a cast, not a sequence,
 * so a new scene inside one can offer the people already in it. Ordering can be added later
 * without disturbing anything here.
 */
@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
