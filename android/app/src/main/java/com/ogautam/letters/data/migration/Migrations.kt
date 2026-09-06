package com.ogautam.letters.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: characters become a reusable library, scenes gain stories, and messages gain the
 * per-message playback controls.
 *
 * This runs against scenes that already exist on the device, so it moves them rather than
 * asking for them again. Every v1 character is copied into the library, deduplicated by
 * name and colour — the same person added separately to three scenes becomes one library
 * entry appearing in three casts — and each scene's cast is rebuilt to point at it.
 *
 * Messages are not touched beyond their new columns. Their snapshot of the sender's name,
 * colour and avatar is exactly what makes this migration safe: whatever the library ends up
 * holding, every scene still plays back the way it was written.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `characters` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `color` INTEGER NOT NULL,
                `avatarPath` TEXT,
                `isSelf` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stories` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `scene_cast` (
                `sceneId` TEXT NOT NULL,
                `characterId` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `outgoing` INTEGER NOT NULL,
                PRIMARY KEY(`sceneId`, `characterId`),
                FOREIGN KEY(`sceneId`) REFERENCES `scenes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scene_cast_sceneId` ON `scene_cast` (`sceneId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scene_cast_characterId` ON `scene_cast` (`characterId`)")

        // One library entry per distinct person. The lowest existing id is reused as the
        // library id so the cast can be rebuilt by joining on it.
        db.execSQL(
            """
            INSERT INTO `characters` (`id`, `name`, `color`, `avatarPath`, `isSelf`, `createdAt`, `updatedAt`)
            SELECT MIN(`id`), `name`, `color`, MIN(`avatarPath`), 0, $NOW, $NOW
            FROM `scene_characters`
            GROUP BY `name`, `color`
            """.trimIndent(),
        )

        // reason: the first character of a scene was always the outgoing one in v1, so the
        // person most often in that seat is the closest thing to "you" this data has.
        db.execSQL(
            """
            UPDATE `characters` SET `isSelf` = 1 WHERE `id` = (
                SELECT c.`id` FROM `characters` c
                JOIN `scene_characters` sc ON sc.`name` = c.`name` AND sc.`color` = c.`color`
                WHERE sc.`orderIndex` = 0
                GROUP BY c.`id`
                ORDER BY COUNT(*) DESC
                LIMIT 1
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT OR IGNORE INTO `scene_cast` (`sceneId`, `characterId`, `orderIndex`, `outgoing`)
            SELECT sc.`sceneId`, c.`id`, sc.`orderIndex`, CASE WHEN sc.`orderIndex` = 0 THEN 1 ELSE 0 END
            FROM `scene_characters` sc
            JOIN `characters` c ON c.`name` = sc.`name` AND c.`color` = sc.`color`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `scene_characters`")

        // A plain column and an index: no table rebuild, so nothing referencing scenes
        // has its foreign keys rewritten underneath it.
        db.execSQL("ALTER TABLE `scenes` ADD COLUMN `storyId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scenes_storyId` ON `scenes` (`storyId`)")

        db.execSQL("ALTER TABLE `scene_messages` ADD COLUMN `unsent` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `scene_messages` ADD COLUMN `revealPerCharMs` INTEGER")
        db.execSQL("ALTER TABLE `scene_messages` ADD COLUMN `typingMs` INTEGER")
        db.execSQL("ALTER TABLE `scene_messages` ADD COLUMN `delayBeforeMs` INTEGER")
    }
}

/** Timestamps for rows this migration invents, rather than leaving them at the epoch. */
private const val NOW = "CAST(strftime('%s','now') AS INTEGER) * 1000"
