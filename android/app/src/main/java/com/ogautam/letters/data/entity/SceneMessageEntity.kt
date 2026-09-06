package com.ogautam.letters.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalTime
import java.util.UUID

@Entity(
    tableName = "scene_messages",
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
data class SceneMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sceneId: String,
    val charId: String,
    // reason: sender name/color/avatar are snapshotted onto the message so playback stays
    // correct after the character is renamed, recolored or deleted. Do not normalise these
    // away by joining back to scene_characters.
    val charName: String,
    val charColor: Int,
    val charAvatarPath: String? = null,
    val text: String,
    /**
     * Stored as a time, not a pre-rendered string. The web app persisted the output of
     * toLocaleTimeString(), so a scene composed on a 24-hour device rendered "14:32" forever.
     */
    val time: LocalTime,
    val outgoing: Boolean,
    val orderIndex: Int,
)
