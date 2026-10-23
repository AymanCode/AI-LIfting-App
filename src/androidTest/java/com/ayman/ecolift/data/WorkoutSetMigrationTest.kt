package com.ayman.ecolift.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * Exercises MIGRATION_18_19 against the shape of data that exists in the wild:
 * set numbers with gaps and repeats, produced by the numbering bug this
 * migration cleans up after.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutSetMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration18To19_renumbersCollidingSetsAndKeepsEveryRow() {
        helper.createDatabase(DB_NAME, 18).use { db ->
            db.execSQL(
                "INSERT INTO exercise (id, name, muscleGroups, isBodyweight, createdAt) " +
                    "VALUES (1, 'Bench Press', 'chest', 0, 0)"
            )
            // Taken from a real export: a gap at 1, and two distinct completed
            // sets both numbered 3.
            insertSet(db, id = 206, setNumber = 2, reps = 10)
            insertSet(db, id = 194, setNumber = 3, reps = 12)
            insertSet(db, id = 207, setNumber = 3, reps = 10)
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 19, true, Migrations.MIGRATION_18_19)

        db.query("SELECT id, setNumber, reps FROM workout_set ORDER BY setNumber").use { cursor ->
            assertEquals("no row may be dropped", 3, cursor.count)

            cursor.moveToNext()
            assertEquals(206L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))

            // Ties break on id, so the older row keeps the lower number and both
            // sets survive with their own reps.
            cursor.moveToNext()
            assertEquals(194L, cursor.getLong(0))
            assertEquals(2, cursor.getInt(1))
            assertEquals(12, cursor.getInt(2))

            cursor.moveToNext()
            assertEquals(207L, cursor.getLong(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(10, cursor.getInt(2))
        }
    }

    @Test
    fun migration18To19_leavesAlreadyContiguousNumberingAlone() {
        helper.createDatabase(DB_NAME, 18).use { db ->
            db.execSQL(
                "INSERT INTO exercise (id, name, muscleGroups, isBodyweight, createdAt) " +
                    "VALUES (1, 'Bench Press', 'chest', 0, 0)"
            )
            insertSet(db, id = 1, setNumber = 1, reps = 8)
            insertSet(db, id = 2, setNumber = 2, reps = 8)
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 19, true, Migrations.MIGRATION_18_19)

        db.query("SELECT id, setNumber FROM workout_set ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToNext()
            assertEquals(1, cursor.getInt(1))
            cursor.moveToNext()
            assertEquals(2, cursor.getInt(1))
        }
    }

    @Test
    fun migration19To20_keepsSetValuesAndBackfillsOneLegacyCardPerExercise() {
        helper.createDatabase(DB_NAME_20, 19).use { db ->
            db.execSQL(
                "INSERT INTO exercise (id, name, muscleGroups, isBodyweight, createdAt) " +
                    "VALUES (1, 'Bench Press', 'chest', 0, 10)",
            )
            db.execSQL(
                "INSERT INTO exercise (id, name, muscleGroups, isBodyweight, createdAt) " +
                    "VALUES (2, 'Row', 'back', 0, 11)",
            )
            db.execSQL(
                "INSERT INTO workout_set " +
                    "(id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed, restTimeSeconds) " +
                    "VALUES (10, 1, '2026-05-17', 1, 500, 8, 0, 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO workout_set " +
                    "(id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed, restTimeSeconds) " +
                    "VALUES (11, 1, '2026-05-17', 2, 450, 6, 0, 0, 30)",
            )
            db.execSQL(
                "INSERT INTO workout_set " +
                    "(id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed, restTimeSeconds) " +
                    "VALUES (12, 2, '2026-05-17', 1, 300, 10, 0, 1, NULL)",
            )
            db.execSQL("INSERT INTO user_settings (id, user_bodyweight_lbs) VALUES (1, 180)")
        }

        val db = helper.runMigrationsAndValidate(DB_NAME_20, 20, true, Migrations.MIGRATION_19_20)

        assertNotNull(
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_workout_set_exerciseId_date_setNumber'",
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null },
        )
        db.query(
            "SELECT id, weightLbs, reps, syncId, rowRevision, cardId, isWarmup " +
                "FROM workout_set ORDER BY id",
        ).use { cursor ->
            assertEquals(3, cursor.count)
            cursor.moveToNext()
            assertEquals(10L, cursor.getLong(0))
            assertEquals(500, cursor.getInt(1))
            assertEquals(8, cursor.getInt(2))
            assertEquals("", cursor.getString(3))
            assertEquals(0, cursor.getInt(4))
            assertEquals("legacy-2026-05-17-1", cursor.getString(5))
            assertEquals(0, cursor.getInt(6))
            cursor.moveToNext()
            assertEquals(11L, cursor.getLong(0))
            assertEquals(450, cursor.getInt(1))
            assertEquals("legacy-2026-05-17-1", cursor.getString(5))
        }
        db.query(
            "SELECT id, exerciseId, position, displayMode, origin FROM workout_exercise_card ORDER BY position",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToNext()
            assertEquals("legacy-2026-05-17-1", cursor.getString(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals("EXPANDED", cursor.getString(3))
            assertEquals("LEGACY", cursor.getString(4))
            cursor.moveToNext()
            assertEquals("legacy-2026-05-17-2", cursor.getString(0))
            assertEquals(2L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("COMPACT", cursor.getString(3))
        }
        assertEquals("UTC", db.query("SELECT time_zone FROM user_settings WHERE id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            cursor.getString(0)
        })
        db.close()
    }

    @Test
    fun migration19To20_writesDeviceZoneOnlyForUntouchedSettings() {
        helper.createDatabase(DB_NAME_20_ZONE, 19).use { db ->
            db.execSQL("INSERT INTO user_settings (id) VALUES (1)")
        }

        val db = helper.runMigrationsAndValidate(DB_NAME_20_ZONE, 20, true, Migrations.MIGRATION_19_20)
        val expected = ZoneId.systemDefault().id
        val stored = db.query("SELECT time_zone FROM user_settings WHERE id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            cursor.getString(0)
        }
        assertFalse(stored.isNullOrBlank())
        assertEquals(expected, stored)
        db.close()
    }

    private fun insertSet(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        setNumber: Int,
        reps: Int,
    ) {
        db.execSQL(
            "INSERT INTO workout_set " +
                "(id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed, restTimeSeconds) " +
                "VALUES ($id, 1, '2026-05-17', $setNumber, 500, $reps, 0, 1, NULL)"
        )
    }

    private companion object {
        const val DB_NAME = "migration-test"
        const val DB_NAME_20 = "migration-19-20"
        const val DB_NAME_20_ZONE = "migration-19-20-zone"
    }
}
