package com.ogautam.letters.data

import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.entity.SceneCastEntity
import com.ogautam.letters.data.entity.SceneEntity
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.data.repository.SceneRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SceneRepositoryTest : DbTest() {

    private fun repo() = SceneRepository(db.sceneDao(), clock)

    private fun character(name: String, index: Int) = CharacterEntity(
        id = "c$index",
        name = name,
        color = CharacterPalette.colorForIndex(index),
    )

    /** Cast rows carry the scene id and the ordering; the repository assigns both. */
    private fun castOf(vararg characters: CharacterEntity) = characters.map {
        SceneCastEntity(sceneId = "", characterId = it.id, orderIndex = -1)
    }

    private suspend fun seedLibrary(vararg characters: CharacterEntity) {
        db.characterDao().insertAll(characters.toList())
    }

    private fun message(id: String, from: CharacterEntity, text: String, outgoing: Boolean) =
        SceneMessageEntity(
            id = id,
            sceneId = "",
            charId = from.id,
            charName = from.name,
            charColor = from.color,
            text = text,
            time = LocalTime.of(14, 32),
            outgoing = outgoing,
            orderIndex = -1,
        )

    private suspend fun seedScene(repo: SceneRepository): String {
        val you = character("You", 0)
        val priya = character("Priya", 1)
        val scene = SceneEntity(id = "s1", name = "The argument")
        seedLibrary(you, priya)

        repo.create(
            scene = scene,
            cast = castOf(you, priya),
            messages = listOf(
                message("m1", you, "are you up?", outgoing = true),
                message("m2", priya, "barely", outgoing = false),
                message("m3", priya, "what's wrong", outgoing = false),
                message("m4", you, "nothing. everything.", outgoing = true),
            ),
        )
        return scene.id
    }

    @Test
    fun `scene round trips with characters and messages in order`() = runTest {
        val repo = repo()
        val id = seedScene(repo)

        val loaded = repo.getWithContent(id)!!
        assertEquals("The argument", loaded.scene.name)
        assertEquals(listOf("You", "Priya"), loaded.characters.map { it.name })
        assertEquals(
            listOf("are you up?", "barely", "what's wrong", "nothing. everything."),
            loaded.messages.map { it.text },
        )
        assertEquals(listOf(0, 1, 2, 3), loaded.messages.map { it.orderIndex })
        assertEquals(LocalTime.of(14, 32), loaded.messages.first().time)
    }

    @Test
    fun `the first of the cast speaks as you unless a role says otherwise`() = runTest {
        val repo = repo()
        val loaded = repo.getWithContent(seedScene(repo))!!

        assertEquals("You", loaded.outgoingCharacter!!.name)
        assertTrue(loaded.cast.first { it.characterId == "c0" }.outgoing)
        assertFalse(loaded.cast.first { it.characterId == "c1" }.outgoing)
        assertTrue(loaded.messages.first { it.charName == "You" }.outgoing)
        assertFalse(loaded.messages.first { it.charName == "Priya" }.outgoing)
    }

    @Test
    fun `the outgoing role can be given to someone other than the first`() = runTest {
        val repo = repo()
        val id = seedScene(repo)
        val loaded = repo.getWithContent(id)!!

        repo.save(
            scene = loaded.scene,
            cast = loaded.cast.map { it.copy(outgoing = it.characterId == "c1") },
            messages = loaded.messages,
        )

        assertEquals("Priya", repo.getWithContent(id)!!.outgoingCharacter!!.name)
    }

    @Test
    fun `repository assigns orderIndex from list position`() = runTest {
        val repo = repo()
        // Every incoming row claims orderIndex -1; position in the list is what counts.
        val loaded = repo.getWithContent(seedScene(repo))!!

        assertEquals(listOf(0, 1), loaded.cast.map { it.orderIndex })
        assertEquals(listOf(0, 1, 2, 3), loaded.messages.map { it.orderIndex })
    }

    /**
     * The point of the library: a character can be renamed once, everywhere, and every scene
     * already written still plays back exactly as it was written.
     */
    @Test
    fun `renaming a character in the library leaves written scenes alone`() = runTest {
        val repo = repo()
        val id = seedScene(repo)
        val priya = db.characterDao().getById("c1")!!
        db.characterDao().update(priya.copy(name = "P."))

        val after = repo.getWithContent(id)!!
        assertEquals(listOf("You", "P."), after.characters.map { it.name })
        // The message still renders under the name it was sent with.
        assertEquals("Priya", after.messages.first { it.charId == "c1" }.charName)
    }

    /** Removing someone from the library leaves the scenes they were in intact. */
    @Test
    fun `deleting a character keeps the scenes they appeared in`() = runTest {
        val repo = repo()
        val id = seedScene(repo)

        db.characterDao().deleteById("c1")

        val after = repo.getWithContent(id)!!
        assertEquals(listOf("You"), after.characters.map { it.name })
        assertEquals(4, after.messages.size)
        assertEquals("Priya", after.messages.first { it.charId == "c1" }.charName)
    }

    @Test
    fun `save replaces content rather than merging it`() = runTest {
        val repo = repo()
        val id = seedScene(repo)
        val loaded = repo.getWithContent(id)!!

        repo.save(
            scene = loaded.scene,
            cast = loaded.cast,
            messages = loaded.messages.take(2),
        )

        val after = repo.getWithContent(id)!!
        assertEquals(2, after.messages.size)
        assertEquals(listOf("are you up?", "barely"), after.messages.map { it.text })
    }

    @Test
    fun `summary reports character and message counts`() = runTest {
        val repo = repo()
        seedScene(repo)

        val summary = repo.observeSummaries().first().single()
        assertEquals("The argument", summary.name)
        assertEquals(2, summary.characterCount)
        assertEquals(4, summary.messageCount)
        assertEquals(fixedInstant, summary.updatedAt)
    }

    @Test
    fun `deleting a scene cascades to its cast and messages`() = runTest {
        val repo = repo()
        val id = seedScene(repo)

        repo.delete(id)

        assertNull(repo.getWithContent(id))
        assertEquals(0, repo.observeCount().first())
        // The characters themselves survive — they belong to the library, not the scene.
        assertEquals(2, countRows("characters"))
        // Orphans would still satisfy the query above, so check the child tables directly.
        assertEquals(0, countRows("scene_cast"))
        assertEquals(0, countRows("scene_messages"))
    }

    @Test
    fun `rename changes only the name and updatedAt`() = runTest {
        val repo = repo()
        val id = seedScene(repo)

        repo.rename(id, "The reconciliation")

        val after = repo.getWithContent(id)!!
        assertEquals("The reconciliation", after.scene.name)
        assertEquals(4, after.messages.size)
    }

    @Test
    fun `search matches scene names`() = runTest {
        val repo = repo()
        seedScene(repo)
        repo.create(SceneEntity(id = "s2", name = "Birthday plans"), emptyList(), emptyList())

        assertEquals(listOf("The argument"), repo.search("argu").first().map { it.name })
        assertEquals(2, repo.observeSummaries().first().size)
    }

    private fun countRows(table: String): Int =
        db.query("SELECT COUNT(*) FROM $table", emptyArray()).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
