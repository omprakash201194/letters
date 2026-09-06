package com.ogautam.letters.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Which library characters appear in a scene, and which of them is speaking as "you".
 *
 * Replaces the old per-scene character rows. Deleting a character from the library removes
 * them from casts, but the scenes themselves stay intact and still play correctly — their
 * messages carry their own snapshot of who said them.
 */
@Entity(
    tableName = "scene_cast",
    primaryKeys = ["sceneId", "characterId"],
    foreignKeys = [
        ForeignKey(
            entity = SceneEntity::class,
            parentColumns = ["id"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sceneId"), Index("characterId")],
)
data class SceneCastEntity(
    val sceneId: String,
    val characterId: String,
    val orderIndex: Int,
    /** Exactly one member of a cast should be outgoing — the person holding the phone. */
    val outgoing: Boolean = false,
)
