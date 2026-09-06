package com.ogautam.letters.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ogautam.letters.data.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

/** A story and how much is in it, for the list. */
data class StorySummary(
    val id: String,
    val title: String,
    val sceneCount: Int,
    val updatedAt: java.time.Instant,
)

@Dao
interface StoryDao {

    @Query(
        """
        SELECT s.id, s.title, s.updatedAt,
               (SELECT COUNT(*) FROM scenes sc WHERE sc.storyId = s.id) AS sceneCount
        FROM stories s
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<StorySummary>>

    @Query("SELECT * FROM stories ORDER BY updatedAt DESC")
    suspend fun observeSummariesOnce(): List<StoryEntity>

    @Query("SELECT * FROM stories WHERE id = :id")
    suspend fun getById(id: String): StoryEntity?

    @Query("SELECT * FROM stories WHERE id = :id")
    fun observeById(id: String): Flow<StoryEntity?>

    @Insert
    suspend fun insert(story: StoryEntity)

    @Update
    suspend fun update(story: StoryEntity)

    /**
     * Deleting a story keeps its scenes; they simply stop being grouped. Done here rather
     * than by a foreign key so the scenes table never had to be rebuilt.
     */
    @Query("UPDATE scenes SET storyId = NULL WHERE storyId = :id")
    suspend fun detachScenes(id: String)

    @Query("DELETE FROM stories WHERE id = :id")
    suspend fun deleteRow(id: String)

    @androidx.room.Transaction
    suspend fun delete(id: String) {
        detachScenes(id)
        deleteRow(id)
    }
}
