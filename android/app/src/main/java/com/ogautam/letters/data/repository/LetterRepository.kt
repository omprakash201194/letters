package com.ogautam.letters.data.repository

import com.ogautam.letters.data.dao.LetterDao
import com.ogautam.letters.data.entity.LetterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Owns letter persistence and the two rules that must not drift from the web app:
 * the 120-character list preview, and the fact that every write touches `updatedAt`.
 */
class LetterRepository(
    private val dao: LetterDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    fun observeAll(): Flow<List<LetterSummary>> =
        dao.observeAll().map { letters -> letters.map(::toSummary) }

    fun observeById(id: String): Flow<LetterEntity?> = dao.observeById(id)

    suspend fun getById(id: String): LetterEntity? = dao.getById(id)

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeSealedCount(): Flow<Int> = dao.observeSealedCount(today().toString())

    fun search(query: String): Flow<List<LetterSummary>> =
        dao.search(query.trim()).map { letters -> letters.map(::toSummary) }

    /** Returns the stored row so callers can pick up the generated id and timestamps. */
    suspend fun create(letter: LetterEntity): LetterEntity {
        val now = Instant.now(clock)
        val stored = letter.copy(createdAt = now, updatedAt = now)
        dao.insert(stored)
        return stored
    }

    suspend fun update(letter: LetterEntity): LetterEntity {
        val stored = letter.copy(updatedAt = Instant.now(clock))
        dao.update(stored)
        return stored
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    fun today(): LocalDate = LocalDate.now(clock)

    private fun toSummary(letter: LetterEntity): LetterSummary {
        val today = today()
        return LetterSummary(
            id = letter.id,
            recipient = letter.recipient,
            subject = letter.subject,
            // reason: a sealed letter must not leak its body through the list preview
            contentPreview = if (letter.isSealedOn(today)) null else preview(letter.content),
            mood = letter.mood,
            letterDate = letter.letterDate,
            sealedUntil = letter.sealedUntil,
            isSealed = letter.isSealedOn(today),
            createdAt = letter.createdAt,
            updatedAt = letter.updatedAt,
        )
    }

    companion object {
        const val PREVIEW_LENGTH = 120

        /**
         * Whitespace is stripped before truncating, matching the web backend. Computed in
         * Kotlin rather than SQL because SQLite's trim() only handles spaces, and getting
         * the strip-then-truncate order wrong changes the result.
         */
        fun preview(content: String?): String? {
            if (content.isNullOrBlank()) return null
            val stripped = content.trim()
            return if (stripped.length <= PREVIEW_LENGTH) stripped
            else stripped.take(PREVIEW_LENGTH) + "…"
        }
    }
}
