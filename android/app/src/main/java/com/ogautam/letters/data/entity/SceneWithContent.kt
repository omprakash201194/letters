package com.ogautam.letters.data.entity

import androidx.room.Embedded
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

    @Relation(parentColumn = "id", entityColumn = "sceneId")
    val characters: List<SceneCharacterEntity>,

    @Relation(parentColumn = "id", entityColumn = "sceneId")
    val messages: List<SceneMessageEntity>,
) {
    fun sorted(): SceneWithContent = copy(
        characters = characters.sortedBy(SceneCharacterEntity::orderIndex),
        messages = messages.sortedBy(SceneMessageEntity::orderIndex),
    )

    /**
     * The first character is always "You" — outgoing, green bubbles, right-aligned.
     * This invariant defines the scene model; everything downstream depends on it.
     */
    val outgoingCharacter: SceneCharacterEntity?
        get() = characters.minByOrNull(SceneCharacterEntity::orderIndex)
}
