package com.ogautam.letters.data.repository

import com.ogautam.letters.data.dao.StoryDao
import com.ogautam.letters.data.dao.StorySummary
import com.ogautam.letters.data.entity.StoryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant

/** Stories are folders for scenes. Deleting one keeps its scenes; they stop being grouped. */
class StoryRepository(
    private val dao: StoryDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun observeSummaries(): Flow<List<StorySummary>> = dao.observeSummaries()

    fun observeById(id: String): Flow<StoryEntity?> = dao.observeById(id)

    suspend fun getById(id: String): StoryEntity? = dao.getById(id)

    suspend fun create(title: String): StoryEntity {
        val now = Instant.now(clock)
        val story = StoryEntity(title = title.trim(), createdAt = now, updatedAt = now)
        dao.insert(story)
        return story
    }

    suspend fun rename(id: String, title: String) {
        val story = dao.getById(id) ?: return
        dao.update(story.copy(title = title.trim(), updatedAt = Instant.now(clock)))
    }

    suspend fun delete(id: String) = dao.delete(id)
}
