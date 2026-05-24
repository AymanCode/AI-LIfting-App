package com.ayman.ecolift.update

import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeSeedInstrumentedTest {

    @Test
    fun seedWorkoutDataInInstalledAppBeforeUpdate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("Target app must have a launch activity", launchIntent)

        context.startActivity(
            requireNotNull(launchIntent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )

        val dbFile = waitForDatabase()
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            seedSentinelWorkout(db)
        }
    }

    private fun waitForDatabase(): java.io.File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        repeat(60) {
            if (dbFile.exists() && dbFile.hasTable("exercise")) return dbFile
            SystemClock.sleep(500)
        }
        throw AssertionError("Expected $DATABASE_NAME with exercise table after launching ${context.packageName}")
    }

    private fun seedSentinelWorkout(db: SQLiteDatabase) {
        assertTrue("exercise table should exist before seeding", tableExists(db, "exercise"))
        val userVersion = db.longFor("PRAGMA user_version")
        db.beginTransaction()
        try {
            insertExercise(db)
            if (tableExists(db, "workout_day")) {
                insertModernWorkout(db, userVersion)
            } else {
                insertLegacyWorkout(db)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertExercise(db: SQLiteDatabase) {
        val values = ContentValues().apply {
            put("id", SENTINEL_EXERCISE_ID)
            if (columnExists(db, "exercise", "name")) put("name", SENTINEL_EXERCISE_NAME)
            if (columnExists(db, "exercise", "canonicalName")) put("canonicalName", SENTINEL_EXERCISE_NAME)
            if (columnExists(db, "exercise", "aliases")) put("aliases", "")
            if (columnExists(db, "exercise", "muscleGroups")) put("muscleGroups", "CHEST")
            if (columnExists(db, "exercise", "isBodyweight")) put("isBodyweight", 0)
            if (columnExists(db, "exercise", "createdAt")) put("createdAt", SENTINEL_CREATED_AT)
        }
        db.insertWithOnConflict("exercise", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertModernWorkout(db: SQLiteDatabase, userVersion: Long) {
        val dayValues = ContentValues().apply {
            put("date", SENTINEL_DATE)
            if (columnExists(db, "workout_day", "cycleSlotType")) putNull("cycleSlotType")
            if (columnExists(db, "workout_day", "cycleSlotOccurrence")) putNull("cycleSlotOccurrence")
            if (columnExists(db, "workout_day", "alternativeForDate")) putNull("alternativeForDate")
            if (columnExists(db, "workout_day", "cycleSlotId")) putNull("cycleSlotId")
        }
        db.insertWithOnConflict("workout_day", null, dayValues, SQLiteDatabase.CONFLICT_REPLACE)

        val storedWeight = if (userVersion >= TENTHS_WEIGHT_SCHEMA_VERSION) SENTINEL_WEIGHT_TENTHS else SENTINEL_WEIGHT_LBS
        val setValues = ContentValues().apply {
            put("id", SENTINEL_SET_ID)
            put("exerciseId", SENTINEL_EXERCISE_ID)
            put("date", SENTINEL_DATE)
            put("setNumber", 1)
            if (columnExists(db, "workout_set", "weightLbs")) put("weightLbs", storedWeight)
            if (columnExists(db, "workout_set", "reps")) put("reps", SENTINEL_REPS)
            if (columnExists(db, "workout_set", "isBodyweight")) put("isBodyweight", 0)
            if (columnExists(db, "workout_set", "completed")) put("completed", 1)
            if (columnExists(db, "workout_set", "restTimeSeconds")) put("restTimeSeconds", SENTINEL_REST_SECONDS)
        }
        db.insertWithOnConflict("workout_set", null, setValues, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertLegacyWorkout(db: SQLiteDatabase) {
        assertTrue("legacy workout table should exist before legacy seed", tableExists(db, "workout"))
        val workoutValues = ContentValues().apply {
            put("id", SENTINEL_WORKOUT_ID)
            if (columnExists(db, "workout", "dateEpochDay")) put("dateEpochDay", SENTINEL_EPOCH_DAY)
            if (columnExists(db, "workout", "startedAt")) put("startedAt", SENTINEL_CREATED_AT)
            if (columnExists(db, "workout", "endedAt")) put("endedAt", SENTINEL_CREATED_AT + 1L)
        }
        db.insertWithOnConflict("workout", null, workoutValues, SQLiteDatabase.CONFLICT_REPLACE)

        val setValues = ContentValues().apply {
            put("id", SENTINEL_SET_ID)
            put("exerciseId", SENTINEL_EXERCISE_ID)
            if (columnExists(db, "workout_set", "workoutId")) put("workoutId", SENTINEL_WORKOUT_ID)
            if (columnExists(db, "workout_set", "date")) put("date", SENTINEL_DATE)
            if (columnExists(db, "workout_set", "setNumber")) put("setNumber", 1)
            if (columnExists(db, "workout_set", "setOrder")) put("setOrder", 1)
            if (columnExists(db, "workout_set", "weightLbs")) put("weightLbs", SENTINEL_WEIGHT_LBS)
            if (columnExists(db, "workout_set", "weightLb")) put("weightLb", SENTINEL_WEIGHT_LBS.toDouble())
            if (columnExists(db, "workout_set", "reps")) put("reps", SENTINEL_REPS)
            if (columnExists(db, "workout_set", "isBodyweight")) put("isBodyweight", 0)
            if (columnExists(db, "workout_set", "completed")) put("completed", 1)
            if (columnExists(db, "workout_set", "restTimeSeconds")) put("restTimeSeconds", SENTINEL_REST_SECONDS)
            if (columnExists(db, "workout_set", "loggedAt")) put("loggedAt", SENTINEL_CREATED_AT)
        }
        db.insertWithOnConflict("workout_set", null, setValues, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { cursor ->
            cursor.moveToFirst()
        }

    private fun java.io.File.hasTable(tableName: String): Boolean =
        try {
            SQLiteDatabase.openDatabase(absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                tableExists(db, tableName)
            }
        } catch (_: SQLiteException) {
            false
        }

    private fun columnExists(db: SQLiteDatabase, tableName: String, columnName: String): Boolean =
        db.rawQuery("PRAGMA table_info(`$tableName`)", emptyArray()).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == columnName }
        }

    private fun SQLiteDatabase.longFor(sql: String): Long =
        rawQuery(sql, emptyArray()).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
