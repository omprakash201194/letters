package com.ogautam.letters.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * `storyId` carries no foreign key on purpose. Adding one to an existing table means
 * rebuilding it, and SQLite rewrites the references of every table pointing at it while you
 * do — on a device holding scenes someone cares about, that is a large risk to take for a
 * constraint. Deleting a story clears the column in the repository instead, which is the
 * same outcome as ON DELETE SET NULL and cannot corrupt anything on the way.
 */
@Entity(
    tableName = "scenes",
    indices = [Index("storyId")],
)
data class SceneEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val storyId: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
