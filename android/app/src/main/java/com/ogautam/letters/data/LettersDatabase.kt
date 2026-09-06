package com.ogautam.letters.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ogautam.letters.data.dao.LetterDao
import com.ogautam.letters.data.dao.SceneDao
import com.ogautam.letters.data.entity.LetterEntity
import com.ogautam.letters.data.entity.SceneCharacterEntity
import com.ogautam.letters.data.entity.SceneEntity
import com.ogautam.letters.data.entity.SceneMessageEntity

@Database(
    entities = [
        LetterEntity::class,
        SceneEntity::class,
        SceneCharacterEntity::class,
        SceneMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LettersDatabase : RoomDatabase() {

    abstract fun letterDao(): LetterDao
    abstract fun sceneDao(): SceneDao

    companion object {
        private const val NAME = "letters.db"

        @Volatile
        private var instance: LettersDatabase? = null

        fun get(context: Context): LettersDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): LettersDatabase =
            Room.databaseBuilder(context, LettersDatabase::class.java, NAME).build()
    }
}
