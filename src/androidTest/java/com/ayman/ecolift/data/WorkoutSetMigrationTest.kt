package com.ayman.ecolift.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    }
}
