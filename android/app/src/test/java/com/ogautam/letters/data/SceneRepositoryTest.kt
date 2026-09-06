package com.ogautam.letters.data

import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.entity.SceneCharacterEntity
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

    private fun character(name: String, index: Int) = SceneCharacterEntity(
        id = "c$index",
        sceneId = "",                       // assigned by the repository
        name = name,
        color = CharacterPalette.colorForIndex(index),
        orderIndex = -1,                    // assigned by the repository
    )

    private fun message(id: String, from: SceneCharacterEntity, text: String, outgoing: Boolean) =
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

        repo.create(
            scene = scene,
            characters = listOf(you, priya),
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
    fun `first character is the outgoing one`() = runTest {
        val repo = repo()
        val loaded = repo.getWithContent(seedScene(repo))!!

        assertEquals("You", loaded.outgoingCharacter!!.name)
        assertEquals(0, loaded.outgoingCharacter!!.orderIndex)
        assertTrue(loaded.messages.first { it.charName == "You" }.outgoing)
        assertFalse(loaded.messages.first { it.charName == "Priya" }.outgoing)
    }

    @Test
    fun `repository assigns orderIndex from list position`() = runTest {
        val repo = repo()
        // Every incoming row claims orderIndex -1; position in the list is what counts.
        val loaded = repo.getWithContent(seedScene(repo))!!

        assertEquals(listOf(0, 1), loaded.characters.map { it.orderIndex })
        assertEquals(listOf(0, 1, 2, 3), loaded.messages.map { it.orderIndex })
    }

    @Test
    fun `messages keep the sender snapshot after the character is renamed`() = runTest {
        val repo = repo()
        val id = seedScene(repo)
        val loaded = repo.getWithContent(id)!!

        // Rename Priya but leave the messages untouched, as the composer does.
        val renamed = loaded.characters.map { c ->
            if (c.name == "Priya") c.copy(name = "P.") else c
        }
        repo.save(loaded.scene, renamed, loaded.messages)

        val after = repo.getWithContent(id)!!
        assertEquals(listOf("You", "P."), after.characters.map { it.name })
        // The message still renders under the name it was sent with.
        assertEquals("Priya", after.messages.first { it.charId == "c1" }.charName)
    }

    @Test
    fun `save replaces content rather than merging it`() = runTest {
        val repo = repo()
        val id = seedScene(repo)
        val loaded = repo.getWithContent(id)!!

        repo.save(
            scene = loaded.scene,
            characters = loaded.characters,
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
    fun `deleting a scene cascades to its characters and messages`() = runTest {
        val repo = repo()
        val id = seedScene(repo)

        repo.delete(id)

        assertNull(repo.getWithContent(id))
        assertEquals(0, repo.observeCount().first())
        // Orphans would still satisfy the query above, so check the child tables directly.
        assertEquals(0, countRows("scene_characters"))
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
