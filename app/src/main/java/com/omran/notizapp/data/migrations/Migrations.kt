package com.omran.notizapp.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `notes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`categoryId`)
                        REFERENCES `categories`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_notes_categoryId`
                ON `notes` (`categoryId`)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `categories` " +
                    "ADD COLUMN `deletedAt` INTEGER"
            )
            db.execSQL(
                "ALTER TABLE `notes` " +
                    "ADD COLUMN `deletedAt` INTEGER"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audio_attachments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    noteId INTEGER NOT NULL,
                    fileName TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL,
                    byteCount INTEGER NOT NULL,
                    FOREIGN KEY(noteId)
                        REFERENCES notes(id)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_audio_attachments_noteId
                ON audio_attachments(noteId)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                index_audio_attachments_fileName
                ON audio_attachments(fileName)
                """.trimIndent()
            )
        }
    }

    // Ausschließlich neue Tabelle + Indizes: vorhandene Daten bleiben bestehen.
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `image_attachments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `noteId` INTEGER NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `byteCount` INTEGER NOT NULL,
                    FOREIGN KEY(`noteId`)
                        REFERENCES `notes`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_image_attachments_noteId`
                ON `image_attachments` (`noteId`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_image_attachments_fileName`
                ON `image_attachments` (`fileName`)
                """.trimIndent()
            )
        }
    }
}
