package com.ogautam.letters.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ogautam.letters.data.migration.MIGRATION_1_2
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v1 → v2 migration, run against a real v1 database and then opened by the real Room
 * builder — which validates that what the migration produced actually matches the entities.
 *
 * This is the test that matters most in the project: there are scenes on devices that exist
 * nowhere else, and a schema change is the one way to lose them irrecoverably. It builds the
 * old database by hand rather than through `MigrationTestHelper`, which keeps a
 * serialization dependency off the test classpath and exercises the same path a real
 * upgrade takes.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @Before
    fun clean() = context.deleteDatabase(dbName).let { }

    @After
    fun tidy() = context.deleteDatabase(dbName).let { }

    /** The v1 schema, exactly as `schemas/1.json` describes it. */
    private fun createV1(seed: SupportSQLiteDatabase.() -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `letters` (`id` TEXT NOT NULL, " +
                                "`recipient` TEXT NOT NULL, `subject` TEXT, `content` TEXT, " +
                                "`mood` TEXT, `letterDate` TEXT, `sealedUntil` TEXT, " +
                                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `scenes` (`id` TEXT NOT NULL, " +
                                "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `scene_characters` (`id` TEXT NOT NULL, " +
                                "`sceneId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                                "`color` INTEGER NOT NULL, `avatarPath` TEXT, " +
                                "`orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                                "FOREIGN KEY(`sceneId`) REFERENCES `scenes`(`id`) " +
                                "ON UPDATE NO ACTION ON DELETE CASCADE )",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_scene_characters_sceneId` " +
                                "ON `scene_characters` (`sceneId`)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `scene_messages` (`id` TEXT NOT NULL, " +
                                "`sceneId` TEXT NOT NULL, `charId` TEXT NOT NULL, " +
                                "`charName` TEXT NOT NULL, `charColor` INTEGER NOT NULL, " +
                                "`charAvatarPath` TEXT, `text` TEXT NOT NULL, " +
                                "`time` TEXT NOT NULL, `outgoing` INTEGER NOT NULL, " +
                                "`orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                                "FOREIGN KEY(`sceneId`) REFERENCES `scenes`(`id`) " +
                                "ON UPDATE NO ACTION ON DELETE CASCADE )",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_scene_messages_sceneId` " +
                                "ON `scene_messages` (`sceneId`)",
                        )
                        // Room stamps its own identity; without it the upgrade path is not taken.
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_master_table " +
                                "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
                        )
                        db.execSQL(
                            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                                "VALUES(42, '$V1_IDENTITY_HASH')",
                        )
                        db.version = 1
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { it.seed() }
        helper.close()
    }

    /**
     * Opens through the real builder, so Room runs the migration and then validates that
     * what it produced matches the entities. RoomDatabase is not Closeable, hence the
     * explicit finally rather than `use`.
     */
    private inline fun <T> withV2(block: (LettersDatabase) -> T): T {
        val db = Room.databaseBuilder(context, LettersDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .build()
        return try {
            block(db)
        } finally {
            db.close()
        }
    }

    /**
     * Two scenes sharing the same two people, as a v1 database held them: the same person
     * entered separately into each scene, with no notion of a library.
     */
    private fun seedTwoScenes() = createV1 {
        execSQL(
            "INSERT INTO scenes VALUES ('s1','Tuesday',1000,2000),('s2','Wednesday',1000,2000)",
        )
        execSQL(
            """
            INSERT INTO scene_characters VALUES
              ('c1','s1','You',$YOU,NULL,0),
              ('c2','s1','Meera',$MEERA,NULL,1),
              ('c3','s2','You',$YOU,NULL,0),
              ('c4','s2','Meera',$MEERA,NULL,1)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO scene_messages VALUES
              ('m1','s1','c1','You',$YOU,NULL,'are you awake','21:14',1,0),
              ('m2','s1','c2','Meera',$MEERA,NULL,'barely','21:15',0,1),
              ('m3','s2','c3','You',$YOU,NULL,'again?','09:02',1,0)
            """.trimIndent(),
        )
    }

    @Test
    fun theSamePersonInTwoScenesBecomesOneLibraryEntry() = runBlocking {
        seedTwoScenes()
        withV2 { db ->
            val names = db.characterDao().observeAllOnce().map { it.name }.sorted()
            assertEquals(listOf("Meera", "You"), names)
        }
    }

    @Test
    fun everySceneKeepsItsCastAndItsMessages() = runBlocking {
        seedTwoScenes()
        withV2 { db ->
            val tuesday = db.sceneDao().getWithContent("s1")!!
            assertEquals("Tuesday", tuesday.scene.name)
            assertEquals(listOf("You", "Meera"), tuesday.characters.map { it.name })
            assertEquals(listOf("are you awake", "barely"), tuesday.messages.map { it.text })

            val wednesday = db.sceneDao().getWithContent("s2")!!
            assertEquals(listOf("You", "Meera"), wednesday.characters.map { it.name })
            assertEquals(listOf("again?"), wednesday.messages.map { it.text })
        }
    }

    @Test
    fun theOutgoingRoleFollowsTheOldFirstPosition() = runBlocking {
        seedTwoScenes()
        withV2 { db ->
            val tuesday = db.sceneDao().getWithContent("s1")!!
            assertEquals("You", tuesday.outgoingCharacter!!.name)
        }
    }

    @Test
    fun whoeverSatFirstBecomesTheSelfCharacter() = runBlocking {
        seedTwoScenes()
        withV2 { db ->
            assertEquals("You", db.characterDao().getSelf()!!.name)
        }
    }

    /** Nothing about a written message may change. It is the record of what was said. */
    @Test
    fun messagesSurviveUntouchedAndDefaultTheNewColumns() = runBlocking {
        seedTwoScenes()
        withV2 { db ->
            val message = db.sceneDao().getWithContent("s1")!!.messages.first()
            assertEquals("are you awake", message.text)
            assertEquals("You", message.charName)
            assertTrue(message.outgoing)
            assertEquals(false, message.unsent)
            assertNull(message.revealPerCharMs)
            assertNull(message.typingMs)
            assertNull(message.delayBeforeMs)
        }
    }

    @Test
    fun scenesBelongToNoStoryUntilFiled() = runBlocking {
        seedTwoScenes()
        withV2 { db ->
            assertNull(db.sceneDao().getWithContent("s1")!!.scene.storyId)
            assertEquals(0, db.storyDao().observeSummariesOnce().size)
        }
    }

    @Test
    fun lettersAreUntouched() = runBlocking {
        createV1 {
            execSQL(
                "INSERT INTO letters VALUES ('l1','Dad','Subject','Body','grateful'," +
                    "'2026-05-22',NULL,1000,2000)",
            )
        }
        withV2 { db ->
            val letter = db.letterDao().getById("l1")!!
            assertEquals("Dad", letter.recipient)
            assertEquals("Body", letter.content)
        }
    }

    /** The common case for anyone who never built a scene. */
    @Test
    fun anEmptyDatabaseMigratesCleanly() = runBlocking {
        createV1 { }
        withV2 { db ->
            assertEquals(0, db.characterDao().observeAllOnce().size)
            assertEquals(0, db.sceneDao().observeCountOnce())
        }
    }

    private companion object {
        const val YOU = -1499549 // 0xFFE91E63
        const val MEERA = -6543440 // 0xFF9C27B0

        /** From `schemas/1.json` — Room refuses to upgrade a database it does not recognise. */
        const val V1_IDENTITY_HASH = "fa0f3d7a994f104beb2cf560520dca5a"
    }
}
