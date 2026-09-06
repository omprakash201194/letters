package com.ogautam.letters.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ogautam.letters.data.entity.SceneCharacterEntity
import com.ogautam.letters.data.entity.SceneEntity
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.data.entity.SceneWithContent
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneDao {

    @Query(
        """
        SELECT s.id, s.name, s.createdAt, s.updatedAt,
               (SELECT COUNT(*) FROM scene_characters c WHERE c.sceneId = s.id) AS characterCount,
               (SELECT COUNT(*) FROM scene_messages   m WHERE m.sceneId = s.id) AS messageCount
        FROM scenes s
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<SceneSummary>>

    @Query(
        """
        SELECT s.id, s.name, s.createdAt, s.updatedAt,
               (SELECT COUNT(*) FROM scene_characters c WHERE c.sceneId = s.id) AS characterCount,
               (SELECT COUNT(*) FROM scene_messages   m WHERE m.sceneId = s.id) AS messageCount
        FROM scenes s
        WHERE s.name LIKE '%' || :query || '%'
        ORDER BY s.updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<SceneSummary>>

    @Query("SELECT COUNT(*) FROM scenes")
    fun observeCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM scenes WHERE id = :id")
    fun observeWithContent(id: String): Flow<SceneWithContent?>

    @Transaction
    @Query("SELECT * FROM scenes WHERE id = :id")
    suspend fun getWithContent(id: String): SceneWithContent?

    @Insert
    suspend fun insertScene(scene: SceneEntity)

    @Update
    suspend fun updateScene(scene: SceneEntity)

    @Query("DELETE FROM scenes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert
    suspend fun insertCharacters(characters: List<SceneCharacterEntity>)

    @Insert
    suspend fun insertMessages(messages: List<SceneMessageEntity>)

    @Query("DELETE FROM scene_characters WHERE sceneId = :sceneId")
    suspend fun deleteCharactersFor(sceneId: String)

    @Query("DELETE FROM scene_messages WHERE sceneId = :sceneId")
    suspend fun deleteMessagesFor(sceneId: String)

    /**
     * Clear and re-insert rather than diffing. Scenes are small (< 100 messages) and the
     * web app's SceneService does the same — keeping the two implementations shaped alike
     * makes them easy to compare.
     */
    @Transaction
    suspend fun replaceContent(
        scene: SceneEntity,
        characters: List<SceneCharacterEntity>,
        messages: List<SceneMessageEntity>,
    ) {
        updateScene(scene)
        deleteCharactersFor(scene.id)
        deleteMessagesFor(scene.id)
        insertCharacters(characters)
        insertMessages(messages)
    }

    @Transaction
    suspend fun insertWithContent(
        scene: SceneEntity,
        characters: List<SceneCharacterEntity>,
        messages: List<SceneMessageEntity>,
    ) {
        insertScene(scene)
        insertCharacters(characters)
        insertMessages(messages)
    }
}
