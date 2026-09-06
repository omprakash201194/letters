package com.ogautam.letters.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ogautam.letters.data.entity.LetterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LetterDao {

    @Query("SELECT * FROM letters ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters WHERE id = :id")
    fun observeById(id: String): Flow<LetterEntity?>

    @Query("SELECT * FROM letters WHERE id = :id")
    suspend fun getById(id: String): LetterEntity?

    @Query("SELECT COUNT(*) FROM letters")
    fun observeCount(): Flow<Int>

    /** Sealed letters, counted for the home tile. */
    @Query("SELECT COUNT(*) FROM letters WHERE sealedUntil IS NOT NULL AND sealedUntil > :today")
    fun observeSealedCount(today: String): Flow<Int>

    /**
     * Matches recipient and subject, as the web app does — not the body.
     * Whether bodies should be searchable is still an open question; if it is answered
     * yes, this becomes an FTS4 table rather than a widening of this LIKE.
     */
    @Query(
        """
        SELECT * FROM letters
        WHERE recipient LIKE '%' || :query || '%'
           OR subject   LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<LetterEntity>>

    @Insert
    suspend fun insert(letter: LetterEntity)

    @Update
    suspend fun update(letter: LetterEntity)

    @Delete
    suspend fun delete(letter: LetterEntity)

    @Query("DELETE FROM letters WHERE id = :id")
    suspend fun deleteById(id: String)
}
