package com.ogautam.letters.data.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * A scene with its characters and messages.
 *
 * Room's `@Relation` makes no ordering guarantee, so the raw lists arrive in whatever
 * order SQLite returns them. Always read through [sorted] — message order *is* the
 * scene, and an out-of-order list is a silent playback bug rather than a crash.
 */
data class SceneWithContent(
    @Embedded val scene: SceneEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = SceneCastEntity::class,
            parentColumn = "sceneId",
            entityColumn = "characterId",
        ),
    )
    val characters: List<CharacterEntity>,

    @Relation(parentColumn = "id", entityColumn = "sceneId")
    val cast: List<SceneCastEntity>,

    @Relation(parentColumn = "id", entityColumn = "sceneId")
    val messages: List<SceneMessageEntity>,
) {
    /** Cast order lives on the join row, not the character, so sorting goes through it. */
    fun sorted(): SceneWithContent {
        val order = cast.associate { it.characterId to it.orderIndex }
        return copy(
            characters = characters.sortedBy { order[it.id] ?: Int.MAX_VALUE },
            cast = cast.sortedBy(SceneCastEntity::orderIndex),
            messages = messages.sortedBy(SceneMessageEntity::orderIndex),
        )
    }

    /** The character speaking as "you" in this scene, by the role the cast assigns. */
    val outgoingCharacterId: String?
        get() = cast.firstOrNull { it.outgoing }?.characterId
            ?: cast.minByOrNull(SceneCastEntity::orderIndex)?.characterId

    /**
     * The character speaking as "you" — outgoing, green bubbles, right-aligned. In v1 this
     * was always the first character; it is now a role the cast assigns, so the same person
     * can be the narrator of one scene and a participant in another.
     */
    val outgoingCharacter: CharacterEntity?
        get() = outgoingCharacterId?.let { id -> characters.firstOrNull { it.id == id } }
}
