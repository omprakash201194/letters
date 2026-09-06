package com.ogautam.letters.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "scene_characters",
    foreignKeys = [
        ForeignKey(
            entity = SceneEntity::class,
            parentColumns = ["id"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sceneId")],
)
data class SceneCharacterEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sceneId: String,
    val name: String,
    /** Assigned by position from [CharacterPalette], cycling every 8 characters. */
    val color: Int,
    /**
     * Path to a file under filesDir/avatars, not a base64 data URL — the web app inlined
     * the whole image into every row that referenced it.
     */
    val avatarPath: String? = null,
    val orderIndex: Int,
)
