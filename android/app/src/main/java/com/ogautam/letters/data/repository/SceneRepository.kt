package com.ogautam.letters.data.repository

import com.ogautam.letters.data.dao.SceneDao
import com.ogautam.letters.data.dao.SceneSummary
import com.ogautam.letters.data.entity.SceneCharacterEntity
import com.ogautam.letters.data.entity.SceneEntity
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.data.entity.SceneWithContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

/**
 * Owns scene persistence. Two things happen here rather than in the DAO: `orderIndex` is
 * assigned from list position on every save, so callers never maintain it by hand, and
 * loaded scenes are sorted before they escape the data layer.
 */
class SceneRepository(
    private val dao: SceneDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun observeSummaries(): Flow<List<SceneSummary>> = dao.observeSummaries()

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun search(query: String): Flow<List<SceneSummary>> = dao.search(query.trim())

    fun observeWithContent(id: String): Flow<SceneWithContent?> =
        dao.observeWithContent(id).map { it?.sorted() }

    suspend fun getWithContent(id: String): SceneWithContent? =
        dao.getWithContent(id)?.sorted()

    suspend fun create(
        scene: SceneEntity,
        characters: List<SceneCharacterEntity>,
        messages: List<SceneMessageEntity>,
    ): SceneEntity {
        val now = Instant.now(clock)
        val stored = scene.copy(createdAt = now, updatedAt = now)
        dao.insertWithContent(
            scene = stored,
            characters = reindexCharacters(stored.id, characters),
            messages = reindexMessages(stored.id, messages),
        )
        return stored
    }

    /** Full replace — the previous characters and messages are discarded, not merged. */
    suspend fun save(
        scene: SceneEntity,
        characters: List<SceneCharacterEntity>,
        messages: List<SceneMessageEntity>,
    ): SceneEntity {
        val stored = scene.copy(updatedAt = Instant.now(clock))
        dao.replaceContent(
            scene = stored,
            characters = reindexCharacters(stored.id, characters),
            messages = reindexMessages(stored.id, messages),
        )
        return stored
    }

    suspend fun rename(id: String, name: String) {
        val scene = dao.getWithContent(id)?.scene ?: return
        dao.updateScene(scene.copy(name = name, updatedAt = Instant.now(clock)))
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    private fun reindexCharacters(sceneId: String, characters: List<SceneCharacterEntity>) =
        characters.mapIndexed { index, c -> c.copy(sceneId = sceneId, orderIndex = index) }

    private fun reindexMessages(sceneId: String, messages: List<SceneMessageEntity>) =
        messages.mapIndexed { index, m -> m.copy(sceneId = sceneId, orderIndex = index) }
}
