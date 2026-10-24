package com.ayman.ecolift.android.room

import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.BackupMetadata
import com.ayman.ecolift.data.DataBackupManager
import com.ayman.ecolift.data.IsolatedBackupContext
import com.ayman.ecolift.data.Migrations
import com.ayman.ecolift.data.UserDataBackup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.ayman.ecolift.data.room.CycleSlot as RoomCycleSlot
import com.ayman.ecolift.data.room.Exercise as RoomExercise
import com.ayman.ecolift.data.room.SplitExercise as RoomSplitExercise

@RunWith(AndroidJUnit4::class)
class LegacySplitUpgradeTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun upgrade_from_schema_19_keeps_four_lifts_and_stamps_the_evidence_window() {
        val name = "legacy-split-upgrade"
        val started = System.currentTimeMillis()
        helper.createDatabase(name, 19).use { db ->
            repeat(4) { index ->
                val id = index + 1
                db.execSQL(
                    "INSERT INTO exercise (id, name, muscleGroups, isBodyweight, createdAt) " +
                        "VALUES ($id, 'Lift $id', 'chest', 0, $id)",
                )
            }
            db.execSQL("INSERT INTO cycle_slot (id, name, orderIndex) VALUES (7, 'Push', 0)")
            repeat(4) { index ->
                val id = index + 1
                db.execSQL(
                    "INSERT INTO split_exercise (id, splitId, exerciseId, orderIndex) " +
                        "VALUES ($id, 7, $id, $index)",
                )
            }
        }

        helper.runMigrationsAndValidate(
            name,
            21,
            true,
            Migrations.MIGRATION_19_20,
            Migrations.MIGRATION_20_21,
        ).use { db ->
            db.query(
                "SELECT exerciseId FROM split_exercise WHERE splitId = 7 ORDER BY orderIndex",
            ).use { cursor ->
                assertEquals(4, cursor.count)
                val ids = mutableListOf<Long>()
                while (cursor.moveToNext()) ids += cursor.getLong(0)
                assertEquals(listOf(1L, 2L, 3L, 4L), ids)
            }
            db.query("SELECT createdAt, anchorUpdatedAt, learned FROM cycle_slot WHERE id = 7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
                assertFalse(cursor.isNull(1))
                assertTrue(cursor.getLong(1) >= started)
                assertEquals(0, cursor.getInt(2))
            }
        }
    }

    @Test
    fun restore_of_a_pre_learned_backup_stamps_only_slots_that_have_no_evidence_window() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val backups = IsolatedBackupContext.open(context)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val started = System.currentTimeMillis()
        try {
            val backup = UserDataBackup(
                metadata = BackupMetadata(createdAtEpochMs = 1L, source = "schema-19", appDbVersion = 19),
                cycle = null,
                exercises = (1L..4L).map { RoomExercise(id = it, name = "Lift $it", createdAt = it) },
                workoutDays = emptyList(),
                workoutSets = emptyList(),
                pendingReviews = emptyList(),
                cycleSlots = listOf(
                    RoomCycleSlot(id = 7, name = "Push", createdAt = 0, anchorUpdatedAt = null),
                    RoomCycleSlot(id = 8, name = "New", orderIndex = 1, createdAt = 5_000, anchorUpdatedAt = null),
                    RoomCycleSlot(id = 9, name = "Edited", orderIndex = 2, createdAt = 0, anchorUpdatedAt = 99),
                ),
                splitExercises = (1L..4L).map { RoomSplitExercise(id = it, splitId = 7, exerciseId = it, orderIndex = (it - 1).toInt()) },
                tempSessionSwaps = emptyList(),
            )
            val file = File(context.cacheDir, "legacy-split-restore.json")
            val encoded = Json { prettyPrint = true; ignoreUnknownKeys = true }.encodeToString(backup)
            val push = Json.parseToJsonElement(encoded)
                .jsonObject.getValue("cycleSlots").jsonArray
                .map { it.jsonObject }
                .single { it.getValue("id").jsonPrimitive.long == 7L }
            assertFalse(push.containsKey("anchorUpdatedAt"))
            assertFalse(push.containsKey("createdAt"))
            file.writeText(encoded)
            DataBackupManager.importFromUri(backups, db, Uri.fromFile(file))

            val slots = db.cycleSlotDao().getAll().associateBy { it.id }
            assertEquals(0L, slots.getValue(7).createdAt)
            val stamped = slots.getValue(7).anchorUpdatedAt
            assertTrue(stamped != null && stamped >= started)
            val members = db.splitExerciseDao().getAll()
                .filter { it.splitId == 7L }
                .sortedBy { it.orderIndex }
                .map { it.exerciseId }
            assertEquals(listOf(1L, 2L, 3L, 4L), members)
            assertEquals(5_000L, slots.getValue(8).createdAt)
            assertNull(slots.getValue(8).anchorUpdatedAt)
            assertEquals(99L, slots.getValue(9).anchorUpdatedAt)
        } finally {
            db.close()
            backups.deleteIsolatedFiles()
        }
    }
}
