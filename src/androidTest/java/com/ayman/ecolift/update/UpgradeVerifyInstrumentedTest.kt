package com.ayman.ecolift.update

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayman.ecolift.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeVerifyInstrumentedTest {

    @Test
    fun updatedAppLaunchesAndMigratesSeededWorkoutData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("Updated app must have a launch activity", launchIntent)

        context.startActivity(
            requireNotNull(launchIntent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        SystemClock.sleep(1_000)

        AppDatabase.getInstance(context).openHelper.writableDatabase.query("SELECT 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }

        val dbFile = context.getDatabasePath(DATABASE_NAME)
        assertTrue("Updated app database should exist", dbFile.exists())
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(CURRENT_ROOM_SCHEMA_VERSION, db.longFor("PRAGMA user_version"))
            assertEquals(1, db.longFor("SELECT COUNT(*) FROM exercise WHERE name = ?", SENTINEL_EXERCISE_NAME))
            assertEquals(
                SENTINEL_WEIGHT_TENTHS.toLong(),
                db.longFor(
                    """
                    SELECT workout_set.weightLbs
                    FROM workout_set
                    JOIN exercise ON exercise.id = workout_set.exerciseId
                    WHERE exercise.name = ?
                    """.trimIndent(),
                    SENTINEL_EXERCISE_NAME,
                )
            )
            assertEquals(
                SENTINEL_REPS.toLong(),
                db.longFor(
                    """
                    SELECT workout_set.reps
                    FROM workout_set
                    JOIN exercise ON exercise.id = workout_set.exerciseId
                    WHERE exercise.name = ?
                    """.trimIndent(),
                    SENTINEL_EXERCISE_NAME,
                )
            )
            assertEquals(
                SENTINEL_REST_SECONDS.toLong(),
                db.longFor(
                    """
                    SELECT workout_set.restTimeSeconds
                    FROM workout_set
                    JOIN exercise ON exercise.id = workout_set.exerciseId
                    WHERE exercise.name = ?
                    """.trimIndent(),
                    SENTINEL_EXERCISE_NAME,
                )
            )
        }
    }

    private fun SQLiteDatabase.longFor(sql: String, vararg args: String): Long =
        rawQuery(sql, args).use { cursor ->
            assertTrue("Expected at least one row for query: $sql", cursor.moveToFirst())
            cursor.getLong(0)
        }
}
