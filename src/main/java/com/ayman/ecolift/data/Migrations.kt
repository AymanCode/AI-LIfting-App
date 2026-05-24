package com.ayman.ecolift.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateLegacyWorkoutSchemaToV3Shape(db)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateLegacyWorkoutSchemaToV3Shape(db)
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add restTimeSeconds to workout_set
            addColumnIfMissing(db, "workout_set", "restTimeSeconds", "INTEGER DEFAULT NULL")
            // Add alternativeForDate to workout_day
            addColumnIfMissing(db, "workout_day", "alternativeForDate", "TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "exercise", "muscleGroups", "TEXT NOT NULL DEFAULT 'CHEST · TRICEPS'")
            addColumnIfMissing(db, "cycle", "nextSessionType", "INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cycle_slot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            addColumnIfMissing(db, "workout_day", "cycleSlotId", "INTEGER DEFAULT NULL")
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

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cycle_slot ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            // Seed orderIndex = rowid ordering so existing slots keep their current display order
            db.execSQL("UPDATE cycle_slot SET orderIndex = id")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `split_exercise` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `splitId` INTEGER NOT NULL,
                    `exerciseId` INTEGER NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    FOREIGN KEY(`splitId`) REFERENCES `cycle_slot`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_exercise_splitId` ON `split_exercise` (`splitId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_exercise_exerciseId` ON `split_exercise` (`exerciseId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_split_exercise_splitId_exerciseId` ON `split_exercise` (`splitId`, `exerciseId`)")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            rebuildWorkoutSetForVersion13(db)
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
    )

    private fun migrateLegacyWorkoutSchemaToV3Shape(db: SupportSQLiteDatabase) {
        if (tableExists(db, "workout") && columnExists(db, "workout_set", "workoutId")) {
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL("ALTER TABLE exercise RENAME TO legacy_exercise")
            db.execSQL("ALTER TABLE workout RENAME TO legacy_workout")
            db.execSQL("ALTER TABLE workout_set RENAME TO legacy_workout_set")
            createVersion3Tables(db)
            db.execSQL(
                """
                INSERT INTO exercise (id, name, isBodyweight, createdAt)
                SELECT id, canonicalName, 0, createdAt
                FROM legacy_exercise
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO workout_day (date, cycleSlotType, cycleSlotOccurrence)
                SELECT date(dateEpochDay * 86400, 'unixepoch'), NULL, NULL
                FROM legacy_workout
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workout_set (id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed)
                SELECT
                    legacy_workout_set.id,
                    legacy_workout_set.exerciseId,
                    date(legacy_workout.dateEpochDay * 86400, 'unixepoch'),
                    legacy_workout_set.setOrder,
                    CAST(ROUND(legacy_workout_set.weightLb) AS INTEGER),
                    legacy_workout_set.reps,
                    0,
                    CASE WHEN legacy_workout.endedAt IS NULL THEN 0 ELSE 1 END
                FROM legacy_workout_set
                JOIN legacy_workout ON legacy_workout.id = legacy_workout_set.workoutId
                """.trimIndent()
            )
            db.execSQL("DROP TABLE legacy_workout_set")
            db.execSQL("DROP TABLE legacy_workout")
            db.execSQL("DROP TABLE legacy_exercise")
            db.execSQL("PRAGMA foreign_keys=ON")
        } else {
            createVersion3Tables(db)
        }
    }

    private fun createVersion3Tables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exercise` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `isBodyweight` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_name` ON `exercise` (`name`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_day` (
                `date` TEXT NOT NULL,
                `cycleSlotType` INTEGER,
                `cycleSlotOccurrence` INTEGER,
                PRIMARY KEY(`date`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_set` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `exerciseId` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `setNumber` INTEGER NOT NULL,
                `weightLbs` INTEGER,
                `reps` INTEGER,
                `isBodyweight` INTEGER NOT NULL DEFAULT 0,
                `completed` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_exerciseId` ON `workout_set` (`exerciseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_date` ON `workout_set` (`date`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cycle` (
                `id` INTEGER NOT NULL,
                `numTypes` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `nextSessionType` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_review` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `rawInput` TEXT NOT NULL,
                `dateLogged` TEXT NOT NULL,
                `resolved` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun rebuildWorkoutSetForVersion13(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.execSQL("ALTER TABLE workout_set RENAME TO workout_set_v12")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_set` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `exerciseId` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `setNumber` INTEGER NOT NULL,
                `weightLbs` INTEGER,
                `reps` INTEGER,
                `isBodyweight` INTEGER NOT NULL,
                `completed` INTEGER NOT NULL,
                `restTimeSeconds` INTEGER,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO workout_set (id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed, restTimeSeconds)
            SELECT
                id,
                exerciseId,
                date,
                setNumber,
                CASE WHEN weightLbs IS NULL THEN NULL ELSE weightLbs * 10 END,
                reps,
                isBodyweight,
                completed,
                restTimeSeconds
            FROM workout_set_v12
            """.trimIndent()
        )
        db.execSQL("DROP TABLE workout_set_v12")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_exerciseId` ON `workout_set` (`exerciseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_date` ON `workout_set` (`date`)")
        db.execSQL("PRAGMA foreign_keys=ON")
    }

    private fun addColumnIfMissing(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnDefinition: String,
    ) {
        if (!columnExists(db, tableName, columnName)) {
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDefinition")
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { cursor ->
            cursor.moveToFirst()
        }

    private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean =
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == columnName }
        }
}
