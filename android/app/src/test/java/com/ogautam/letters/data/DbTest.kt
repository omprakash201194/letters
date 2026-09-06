package com.ogautam.letters.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Base for data-layer tests. Uses an in-memory database and a fixed clock so timestamps
 * are assertable. Runs the stock Application rather than LettersApplication so tests
 * don't build the real on-disk database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
abstract class DbTest {

    protected lateinit var db: LettersDatabase

    protected val fixedInstant: Instant = Instant.parse("2026-09-06T10:15:30Z")
    protected val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LettersDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        db.close()
    }
}
