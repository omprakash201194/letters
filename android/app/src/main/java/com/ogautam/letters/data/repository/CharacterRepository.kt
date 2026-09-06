package com.ogautam.letters.data.repository

import com.ogautam.letters.data.dao.CharacterDao
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.entity.CharacterPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

/**
 * The character library. Characters outlive the scenes they appear in, and editing one here
 * never changes a scene already built — a message carries its own snapshot of who said it,
 * and that snapshot is what plays back.
 */
class CharacterRepository(
    private val dao: CharacterDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun observeAll(): Flow<List<CharacterEntity>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun getById(id: String): CharacterEntity? = dao.getById(id)

    suspend fun getAllById(ids: List<String>): List<CharacterEntity> =
        if (ids.isEmpty()) emptyList() else dao.getAllById(ids)

    suspend fun getSelf(): CharacterEntity? = dao.getSelf()

    /**
     * An existing character with this name, ignoring case and surrounding space.
     *
     * Used where typing a name is a way of *reaching* someone rather than inventing them —
     * the new-scene wizard. The library itself does not dedupe: two people really can be
     * called Meera, and that is the screen where you would say so.
     */
    suspend fun findByName(name: String): CharacterEntity? =
        name.trim().takeIf(String::isNotEmpty)?.let { dao.findByName(it) }

    /** The people already appearing in a story, so a new scene can offer them first. */
    suspend fun forStory(storyId: String?): List<CharacterEntity> =
        storyId?.let { dao.getForStory(it) }.orEmpty()

    suspend fun sceneCountFor(id: String): Int = dao.sceneCountFor(id)

    suspend fun create(
        name: String,
        avatarPath: String? = null,
        isSelf: Boolean = false,
        color: Int? = null,
    ): CharacterEntity {
        val now = Instant.now(clock)
        val character = CharacterEntity(
            name = name.trim(),
            color = color ?: nextColor(),
            avatarPath = avatarPath,
            isSelf = isSelf,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(character)
        if (isSelf) dao.clearSelfExcept(character.id)
        return character
    }

    suspend fun update(character: CharacterEntity): CharacterEntity {
        val stored = character.copy(updatedAt = Instant.now(clock))
        dao.update(stored)
        if (stored.isSelf) dao.clearSelfExcept(stored.id)
        return stored
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    /**
     * The least-used palette colour, so a new character is unlikely to clash with the people
     * they will share a scene with. Colour belongs to the character, not to a position.
     */
    private suspend fun nextColor(): Int {
        val taken = dao.observeAll().first().groupingBy(CharacterEntity::color).eachCount()
        return CharacterPalette.COLORS.minByOrNull { taken[it] ?: 0 } ?: CharacterPalette.COLORS.first()
    }
}
