package com.ayman.ecolift.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.agent.model.AuditEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseHardeningInstrumentedTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val openDbs = mutableListOf<AppDatabase>()

    @After
    fun tearDown() {
        openDbs.forEach(AppDatabase::close)
        context.deleteDatabase(BACKUP_SOURCE_DB)
        context.deleteDatabase(BACKUP_TARGET_DB)
    }

    @Test
    fun migrationsFromExportedSchemasTo13PreserveWorkoutRowsAndCreateAgentTables() {
        listOf(8, 9, 10, 11).forEach { startVersion ->
            val dbName = "migration-$startVersion.db"
            migrationHelper.createDatabase(dbName, startVersion).apply {
                seedCoreWorkoutRows()
                close()
            }

            val migrated = migrationHelper.runMigrationsAndValidate(
                dbName,
                13,
                true,
                *Migrations.ALL_MIGRATIONS,
            )

            assertEquals(1, migrated.longFor("SELECT COUNT(*) FROM workout_set"))
            assertEquals(1350, migrated.longFor("SELECT weightLbs FROM workout_set WHERE id = 1"))
            assertNotNull(migrated.stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'audit_log'"))
            assertNotNull(migrated.stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'agent_turn_log'"))
            assertNotNull(migrated.stringFor("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_split_exercise_splitId_exerciseId'"))
            migrated.close()
        }
    }

    @Test
    fun migration12To13PreservesSplitAssignmentsAndScalesWeights() {
        val dbName = "migration-12.db"
        migrationHelper.createDatabase(dbName, 12).apply {
            seedCoreWorkoutRows(weightLbs = 225)
            execSQL("INSERT INTO cycle_slot (id, name, orderIndex) VALUES (10, 'Push', 0)")
            execSQL("INSERT INTO split_exercise (id, splitId, exerciseId, orderIndex) VALUES (20, 10, 1, 0)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            dbName,
            13,
            true,
            *Migrations.ALL_MIGRATIONS,
        )

        assertEquals(2250, migrated.longFor("SELECT weightLbs FROM workout_set WHERE id = 1"))
        assertEquals(1, migrated.longFor("SELECT COUNT(*) FROM split_exercise WHERE splitId = 10 AND exerciseId = 1"))
        migrated.close()
    }

    @Test
    fun backupRoundTripPreservesWorkoutAndAgentAuditData() = runTest {
        val source = createDb(BACKUP_SOURCE_DB)
        val target = createDb(BACKUP_TARGET_DB)
        seedBackupSource(source)

        val backupsBefore = DataBackupManager.listAutomaticBackups(context).size
        val backupFile = File(context.cacheDir, "ecolift-roundtrip-test.json").apply { delete() }

        val exportResult = DataBackupManager.exportToUri(context, source, Uri.fromFile(backupFile))
        val importResult = DataBackupManager.importFromUri(context, target, Uri.fromFile(backupFile))

        assertTrue(exportResult.entryCount >= 9)
        assertEquals(exportResult.entryCount, importResult.entryCount)
        assertEquals(source.exerciseDao().getAll().size, target.exerciseDao().getAll().size)
        assertEquals(source.workoutSetDao().getAll().size, target.workoutSetDao().getAll().size)
        assertEquals(source.splitExerciseDao().getAll().size, target.splitExerciseDao().getAll().size)
        assertEquals(source.auditDao().getAll().size, target.auditDao().getAll().size)
        assertEquals(source.agentTurnLogDao().getAll().size, target.agentTurnLogDao().getAll().size)
        assertEquals("Bench Press", target.exerciseDao().getById(1L)?.name)
        assertTrue(DataBackupManager.listAutomaticBackups(context).size > backupsBefore)
    }

    private fun createDb(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .build()
            .also(openDbs::add)

    private suspend fun seedBackupSource(db: AppDatabase) {
        db.exerciseDao().insertAll(
            listOf(
                Exercise(id = 1L, name = "Bench Press", muscleGroups = "CHEST", createdAt = 1L),
                Exercise(id = 2L, name = "Back Squat", muscleGroups = "LEGS", createdAt = 2L),
            )
        )
        db.cycleDao().upsert(Cycle(id = 1, numTypes = 2, isActive = true, nextSessionType = 0))
        db.cycleSlotDao().insertAll(listOf(CycleSlot(id = 10L, name = "Push", orderIndex = 0)))
        db.workoutDayDao().insertAll(listOf(WorkoutDay(date = "2026-05-16", cycleSlotId = 10L)))
        db.workoutSetDao().insertAll(
            listOf(
                WorkoutSet(id = 100L, exerciseId = 1L, date = "2026-05-16", setNumber = 1, weightLbs = 1850, reps = 5, completed = true),
                WorkoutSet(id = 101L, exerciseId = 2L, date = "2026-05-16", setNumber = 1, weightLbs = 2250, reps = 5, completed = true),
            )
        )
        db.pendingReviewDao().insertAll(listOf(PendingReview(id = 200L, rawInput = "bench maybe 185x5", dateLogged = "2026-05-16")))
        db.splitExerciseDao().insertAll(listOf(SplitExercise(id = 300L, splitId = 10L, exerciseId = 1L, orderIndex = 0)))
        db.tempSessionSwapDao().insertAll(
            listOf(TempSessionSwap(id = 400L, weekStartDate = "2026-05-11", sourceSlotType = 0, sourceExerciseId = 1L, targetSlotType = 1, targetExerciseId = 2L, createdAt = 1L))
        )
        db.auditDao().insert(
            AuditEntity(
                id = 500L,
                requestId = "req-1",
                timestamp = 1L,
                serializedPatches = "[]",
                serializedInverse = "[]",
                userConfirmed = true,
            )
        )
        db.agentTurnLogDao().insert(
            AgentTurnLog(
                id = 600L,
                timestamp = 2L,
                userText = "delete my last bench set",
                turnKind = "NeedsConfirmation",
                latencyMs = 12L,
                auditId = 500L,
            )
        )
    }

    private fun SupportSQLiteDatabase.seedCoreWorkoutRows(weightLbs: Int = 135) {
        execSQL("INSERT INTO exercise (id, name, muscleGroups, isBodyweight, createdAt) VALUES (1, 'Bench Press', 'CHEST', 0, 1)")
        execSQL("INSERT INTO workout_day (date, cycleSlotType, cycleSlotOccurrence, alternativeForDate, cycleSlotId) VALUES ('2026-05-16', NULL, NULL, NULL, NULL)")
        execSQL("INSERT INTO workout_set (id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed, restTimeSeconds) VALUES (1, 1, '2026-05-16', 1, $weightLbs, 8, 0, 1, NULL)")
    }

    private fun SupportSQLiteDatabase.longFor(sql: String): Long =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.stringFor(sql: String): String? =
        query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private companion object {
        const val BACKUP_SOURCE_DB = "backup-source.db"
        const val BACKUP_TARGET_DB = "backup-target.db"
    }
}
