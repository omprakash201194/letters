package com.ogautam.letters.data

import com.ogautam.letters.data.entity.LetterEntity
import com.ogautam.letters.data.entity.Mood
import com.ogautam.letters.data.repository.LetterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LetterRepositoryTest : DbTest() {

    private fun repo() = LetterRepository(db.letterDao(), clock)

    @Test
    fun `letter round trips through the database`() = runTest {
        val repo = repo()
        val stored = repo.create(
            LetterEntity(
                recipient = "Dad",
                subject = "The thing I never said",
                content = "I understand now why you worked those hours.",
                mood = Mood.GRATEFUL.slug,
                letterDate = LocalDate.of(2026, 9, 6),
            )
        )

        val loaded = repo.getById(stored.id)
        assertNotNull(loaded)
        assertEquals("Dad", loaded!!.recipient)
        assertEquals("The thing I never said", loaded.subject)
        assertEquals("I understand now why you worked those hours.", loaded.content)
        assertEquals(Mood.GRATEFUL, Mood.fromSlug(loaded.mood))
        assertEquals(LocalDate.of(2026, 9, 6), loaded.letterDate)
        assertEquals(fixedInstant, loaded.createdAt)
        assertEquals(fixedInstant, loaded.updatedAt)
    }

    @Test
    fun `recipient is the only required field`() = runTest {
        val repo = repo()
        val stored = repo.create(LetterEntity(recipient = "Someone"))
        val loaded = repo.getById(stored.id)!!

        assertNull(loaded.subject)
        assertNull(loaded.content)
        assertNull(loaded.mood)
        assertNull(loaded.sealedUntil)
    }

    @Test
    fun `list is ordered by updatedAt descending`() = runTest {
        val dao = db.letterDao()
        // Written directly so each row gets a distinct updatedAt without moving the clock.
        dao.insert(LetterEntity(id = "a", recipient = "A", updatedAt = fixedInstant.minusSeconds(60)))
        dao.insert(LetterEntity(id = "b", recipient = "B", updatedAt = fixedInstant))
        dao.insert(LetterEntity(id = "c", recipient = "C", updatedAt = fixedInstant.minusSeconds(30)))

        val ids = repo().observeAll().first().map { it.id }
        assertEquals(listOf("b", "c", "a"), ids)
    }

    @Test
    fun `preview truncates at 120 characters after stripping`() {
        val long = "  " + "x".repeat(200) + "  "
        val preview = LetterRepository.preview(long)!!

        assertEquals(121, preview.length)          // 120 chars + the ellipsis
        assertTrue(preview.endsWith("…"))
        assertEquals("x".repeat(120), preview.dropLast(1))
    }

    @Test
    fun `preview leaves short content intact and drops blank content`() {
        assertEquals("Short one.", LetterRepository.preview("  Short one.  "))
        assertNull(LetterRepository.preview(null))
        assertNull(LetterRepository.preview("   "))
    }

    @Test
    fun `sealed letter withholds its preview`() = runTest {
        val repo = repo()
        val today = repo.today()

        repo.create(
            LetterEntity(
                id = "sealed",
                recipient = "Future me",
                content = "Open this in a year.",
                sealedUntil = today.plusYears(1),
            )
        )
        repo.create(
            LetterEntity(
                id = "open",
                recipient = "Past me",
                content = "Readable now.",
                sealedUntil = today.minusDays(1),
            )
        )

        val summaries = repo.observeAll().first().associateBy { it.id }

        assertTrue(summaries.getValue("sealed").isSealed)
        assertNull(summaries.getValue("sealed").contentPreview)

        assertFalse(summaries.getValue("open").isSealed)
        assertEquals("Readable now.", summaries.getValue("open").contentPreview)
    }

    @Test
    fun `sealed count only counts future dates`() = runTest {
        val repo = repo()
        val today = repo.today()

        repo.create(LetterEntity(recipient = "A", sealedUntil = today.plusDays(1)))
        repo.create(LetterEntity(recipient = "B", sealedUntil = today))          // opens today
        repo.create(LetterEntity(recipient = "C", sealedUntil = today.minusDays(1)))
        repo.create(LetterEntity(recipient = "D"))

        assertEquals(4, repo.observeCount().first())
        assertEquals(1, repo.observeSealedCount().first())
    }

    @Test
    fun `search matches recipient and subject but not body`() = runTest {
        val repo = repo()
        repo.create(LetterEntity(recipient = "Priya", subject = "Thanks"))
        repo.create(LetterEntity(recipient = "Sam", subject = "About Priya"))
        repo.create(LetterEntity(recipient = "Alex", subject = "Nothing", content = "Priya was there too"))

        val hits = repo.search("priya").first().map { it.recipient }.toSet()
        assertEquals(setOf("Priya", "Sam"), hits)
    }

    @Test
    fun `update touches updatedAt and delete removes the row`() = runTest {
        val dao = db.letterDao()
        val repo = repo()

        dao.insert(
            LetterEntity(
                id = "l1",
                recipient = "Old",
                createdAt = fixedInstant.minusSeconds(3600),
                updatedAt = fixedInstant.minusSeconds(3600),
            )
        )

        val updated = repo.update(repo.getById("l1")!!.copy(recipient = "New"))
        assertEquals("New", updated.recipient)
        assertEquals(fixedInstant, updated.updatedAt)
        assertEquals(fixedInstant.minusSeconds(3600), updated.createdAt)

        repo.delete("l1")
        assertNull(repo.getById("l1"))
    }
}
