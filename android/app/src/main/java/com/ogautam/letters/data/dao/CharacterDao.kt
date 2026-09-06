package com.ogautam.letters.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ogautam.letters.data.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    @Query("SELECT * FROM characters ORDER BY isSelf DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters ORDER BY isSelf DESC, name COLLATE NOCASE ASC")
    suspend fun observeAllOnce(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getById(id: String): CharacterEntity?

    @Query("SELECT * FROM characters WHERE id IN (:ids)")
    suspend fun getAllById(ids: List<String>): List<CharacterEntity>

    /** Case- and whitespace-insensitive, which is how a person types the same name twice. */
    @Query("SELECT * FROM characters WHERE TRIM(name) = TRIM(:name) COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CharacterEntity?

    @Query("SELECT * FROM characters WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): CharacterEntity?

    /** The characters appearing in a story's scenes, most-used first. */
    @Query(
        """
        SELECT c.* FROM characters c
        JOIN scene_cast sc ON sc.characterId = c.id
        JOIN scenes s ON s.id = sc.sceneId
        WHERE s.storyId = :storyId
        GROUP BY c.id
        ORDER BY COUNT(*) DESC, c.name COLLATE NOCASE ASC
        """
    )
    suspend fun getForStory(storyId: String): List<CharacterEntity>

    @Query("SELECT COUNT(*) FROM characters")
    fun observeCount(): Flow<Int>

    /** How many scenes a character appears in, for warning before a delete. */
    @Query("SELECT COUNT(DISTINCT sceneId) FROM scene_cast WHERE characterId = :id")
    suspend fun sceneCountFor(id: String): Int

    @Insert
    suspend fun insert(character: CharacterEntity)

    @Insert
    suspend fun insertAll(characters: List<CharacterEntity>)

    @Update
    suspend fun update(character: CharacterEntity)

    @Query("UPDATE characters SET isSelf = 0 WHERE id != :id")
    suspend fun clearSelfExcept(id: String)

    @Delete
    suspend fun delete(character: CharacterEntity)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteById(id: String)
}
