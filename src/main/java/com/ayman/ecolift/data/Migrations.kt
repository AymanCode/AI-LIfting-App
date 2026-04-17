package com.ayman.ecolift.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add restTimeSeconds to workout_set
            db.execSQL("ALTER TABLE workout_set ADD COLUMN restTimeSeconds INTEGER DEFAULT NULL")
            // Add alternativeForDate to workout_day
            db.execSQL("ALTER TABLE workout_day ADD COLUMN alternativeForDate TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercise ADD COLUMN muscleGroups TEXT NOT NULL DEFAULT 'CHEST · TRICEPS'")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cycle_slot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            db.execSQL("ALTER TABLE workout_day ADD COLUMN cycleSlotId INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Version 7 did not introduce a schema delta.
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Version 8 kept the same schema while moving the DB version forward.
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `temp_session_swap` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `weekStartDate` TEXT NOT NULL,
                    `sourceSlotType` INTEGER NOT NULL,
                    `sourceExerciseId` INTEGER NOT NULL,
                    `targetSlotType` INTEGER NOT NULL,
                    `targetExerciseId` INTEGER NOT NULL,
                    `resolved` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_temp_session_swap_weekStartDate` ON `temp_session_swap` (`weekStartDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_temp_session_swap_sourceSlotType` ON `temp_session_swap` (`sourceSlotType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_temp_session_swap_targetSlotType` ON `temp_session_swap` (`targetSlotType`)")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `audit_log` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `requestId` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `serializedPatches` TEXT NOT NULL,
                    `serializedInverse` TEXT NOT NULL,
                    `userConfirmed` INTEGER NOT NULL,
                    `isUndo` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `agent_turn_log` (
                    `id`           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp`    INTEGER NOT NULL,
                    `userText`     TEXT NOT NULL,
                    `turnKind`     TEXT NOT NULL,
                    `latencyMs`    INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    `auditId`      INTEGER
                )
                """.trimIndent()
            )
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
    )
}
