package com.ayman.ecolift.android.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Migrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitMemberChoiceMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migration_keeps_anchors_and_workouts_without_freezing_automatic_boards() {
        val name = "split-choice-migration"
        helper.createDatabase(name, 20).use { db ->
            db.execSQL("INSERT INTO exercise (id, name, muscleGroups, isBodyweight, createdAt) VALUES (1, 'Squat', 'legs', 0, 0)")
            db.execSQL("INSERT INTO cycle_slot (id, name, learned) VALUES (7, 'Lower', 1)")
            db.execSQL("INSERT INTO split_exercise (id, splitId, exerciseId, orderIndex, userAdded) VALUES (8, 7, 1, 0, 0)")
            db.execSQL(
                "INSERT INTO workout_set (id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed) " +
                    "VALUES (9, 1, '2026-10-20', 1, 1350, 8, 0, 1)",
            )
        }
        helper.runMigrationsAndValidate(name, 21, true, Migrations.MIGRATION_20_21).use { db ->
            db.query("SELECT id, exerciseId FROM split_exercise WHERE splitId = 7").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(8L, cursor.getLong(0))
                assertEquals(1L, cursor.getLong(1))
            }
            db.query("SELECT weightLbs, reps, completed FROM workout_set WHERE id = 9").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(1350, cursor.getInt(0))
                assertEquals(8, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM split_member_choice").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.execSQL("INSERT INTO split_member_choice VALUES (7, 1, '[]', '[1]')")
            db.query("SELECT inSplitJson, otherOptionsJson FROM split_member_choice WHERE splitId = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals("[]", cursor.getString(0))
                assertEquals("[1]", cursor.getString(1))
            }
        }
    }
}
