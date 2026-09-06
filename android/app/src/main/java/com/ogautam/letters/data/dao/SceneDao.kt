package com.ogautam.letters.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ogautam.letters.data.entity.SceneCastEntity
import com.ogautam.letters.data.entity.SceneEntity
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.data.entity.SceneWithContent
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneDao {

    @Query(
        """
        SELECT s.id, s.name, s.createdAt, s.updatedAt,
               (SELECT COUNT(*) FROM scene_cast       c WHERE c.sceneId = s.id) AS characterCount,
               (SELECT COUNT(*) FROM scene_messages   m WHERE m.sceneId = s.id) AS messageCount
        FROM scenes s
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<SceneSummary>>

    @Query(
        """
        SELECT s.id, s.name, s.createdAt, s.updatedAt,
               (SELECT COUNT(*) FROM scene_cast       c WHERE c.sceneId = s.id) AS characterCount,
               (SELECT COUNT(*) FROM scene_messages   m WHERE m.sceneId = s.id) AS messageCount
        FROM scenes s
        WHERE (:storyId IS NULL AND s.storyId IS NULL) OR s.storyId = :storyId
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeSummariesForStory(storyId: String?): Flow<List<SceneSummary>>

    @Query(
        """
        SELECT s.id, s.name, s.createdAt, s.updatedAt,
               (SELECT COUNT(*) FROM scene_cast       c WHERE c.sceneId = s.id) AS characterCount,
               (SELECT COUNT(*) FROM scene_messages   m WHERE m.sceneId = s.id) AS messageCount
        FROM scenes s
        WHERE s.name LIKE '%' || :query || '%'
        ORDER BY s.updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<SceneSummary>>

    @Query("SELECT COUNT(*) FROM scenes")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scenes")
    suspend fun observeCountOnce(): Int

    @Query("UPDATE scenes SET storyId = :storyId, updatedAt = :updatedAt WHERE id = :sceneId")
    suspend fun setStory(sceneId: String, storyId: String?, updatedAt: java.time.Instant)

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
    suspend fun insertCast(cast: List<SceneCastEntity>)

    @Insert
    suspend fun insertMessages(messages: List<SceneMessageEntity>)

    @Query("DELETE FROM scene_cast WHERE sceneId = :sceneId")
    suspend fun deleteCastFor(sceneId: String)

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
        cast: List<SceneCastEntity>,
        messages: List<SceneMessageEntity>,
    ) {
        updateScene(scene)
        deleteCastFor(scene.id)
        deleteMessagesFor(scene.id)
        insertCast(cast)
        insertMessages(messages)
    }

    @Transaction
    suspend fun insertWithContent(
        scene: SceneEntity,
        cast: List<SceneCastEntity>,
        messages: List<SceneMessageEntity>,
    ) {
        insertScene(scene)
        insertCast(cast)
        insertMessages(messages)
    }
}
