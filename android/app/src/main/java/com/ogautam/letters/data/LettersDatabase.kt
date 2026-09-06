package com.ogautam.letters.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ogautam.letters.data.dao.CharacterDao
import com.ogautam.letters.data.dao.LetterDao
import com.ogautam.letters.data.dao.SceneDao
import com.ogautam.letters.data.dao.StoryDao
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.entity.LetterEntity
import com.ogautam.letters.data.entity.SceneCastEntity
import com.ogautam.letters.data.entity.SceneEntity
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.data.entity.StoryEntity
import com.ogautam.letters.data.migration.MIGRATION_1_2

@Database(
    entities = [
        LetterEntity::class,
        StoryEntity::class,
        SceneEntity::class,
        CharacterEntity::class,
        SceneCastEntity::class,
        SceneMessageEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LettersDatabase : RoomDatabase() {

    abstract fun letterDao(): LetterDao
    abstract fun sceneDao(): SceneDao
    abstract fun characterDao(): CharacterDao
    abstract fun storyDao(): StoryDao

    companion object {
        private const val NAME = "letters.db"

        @Volatile
        private var instance: LettersDatabase? = null

        fun get(context: Context): LettersDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): LettersDatabase =
            Room.databaseBuilder(context, LettersDatabase::class.java, NAME)
                // reason: never destructive. There are scenes on people's devices that
                // exist nowhere else — losing them to a schema change is not recoverable.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
