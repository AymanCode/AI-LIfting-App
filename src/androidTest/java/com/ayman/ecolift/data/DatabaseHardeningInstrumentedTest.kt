package com.ayman.ecolift.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        context.deleteDatabase(LEGACY_SOURCE_DB)
        context.deleteDatabase(BACKUP_SOURCE_DB)
        context.deleteDatabase(BACKUP_TARGET_DB)
        context.deleteDatabase(ARCHIVE_SOURCE_DB)
        context.deleteDatabase(ARCHIVE_OVERLAP_DB)
        context.deleteDatabase(COMPLETED_ONLY_DB)
        context.deleteDatabase(PREPACKAGED_ASSET_DB)
        context.deleteDatabase(REPOSITORY_MUTATION_DB)
        context.deleteDatabase(FLUID_SET_LIFECYCLE_DB)
    }

    @Test
    fun migrationsFromExportedSchemasToCurrentPreserveWorkoutRowsAndCreateCurrentTables() {
        listOf(8, 9, 10, 11).forEach { startVersion ->
            val dbName = "migration-$startVersion.db"
            migrationHelper.createDatabase(dbName, startVersion).apply {
                seedCoreWorkoutRows()
                close()
            }

            val migrated = migrationHelper.runMigrationsAndValidate(
                dbName,
                APP_DATABASE_VERSION,
                true,
                *Migrations.ALL_MIGRATIONS,
            )

            assertEquals(1, migrated.longFor("SELECT COUNT(*) FROM workout_set"))
            assertEquals(1350, migrated.longFor("SELECT weightLbs FROM workout_set WHERE id = 1"))
            migrated.assertCurrentRoomSchema()
            migrated.close()
        }
    }

    @Test
    fun migration12ToCurrentPreservesSplitAssignmentsAndScalesWeights() {
        val dbName = "migration-12.db"
        migrationHelper.createDatabase(dbName, 12).apply {
            seedCoreWorkoutRows(weightLbs = 225)
            execSQL("INSERT INTO cycle_slot (id, name, orderIndex) VALUES (10, 'Push', 0)")
            execSQL("INSERT INTO split_exercise (id, splitId, exerciseId, orderIndex) VALUES (20, 10, 1, 0)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            dbName,
            APP_DATABASE_VERSION,
            true,
            *Migrations.ALL_MIGRATIONS,
        )

        assertEquals(2250, migrated.longFor("SELECT weightLbs FROM workout_set WHERE id = 1"))
        assertEquals(1, migrated.longFor("SELECT COUNT(*) FROM split_exercise WHERE splitId = 10 AND exerciseId = 1"))
        migrated.assertCurrentRoomSchema()
        migrated.close()
    }

    @Test
    fun migration13To14AddsCycleDateColumnsAndArchivedCycleTable() {
        val dbName = "migration-13.db"
        migrationHelper.createDatabase(dbName, 13).apply {
            seedCoreWorkoutRows(weightLbs = 2250)
            execSQL("INSERT INTO cycle (id, numTypes, isActive, nextSessionType) VALUES (1, 2, 1, 0)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            dbName,
            14,
            true,
            *Migrations.ALL_MIGRATIONS,
        )

        assertEquals(2250, migrated.longFor("SELECT weightLbs FROM workout_set WHERE id = 1"))
        assertNotNull(migrated.stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'archived_cycle'"))
        assertTrue(migrated.query("SELECT startDate, name FROM cycle WHERE id = 1").use { cursor ->
            cursor.moveToFirst() && cursor.isNull(0) && cursor.isNull(1)
        })
        migrated.close()
    }

    @Test
    fun migration14ToCurrentAddsUserSettingsTableAndCurrentColumns() {
        val dbName = "migration-14.db"
        migrationHelper.createDatabase(dbName, 14).apply {
            seedCoreWorkoutRows(weightLbs = 2250)
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            dbName,
            APP_DATABASE_VERSION,
            true,
            *Migrations.ALL_MIGRATIONS,
        )

        assertEquals(2250, migrated.longFor("SELECT weightLbs FROM workout_set WHERE id = 1"))
        assertNotNull(migrated.stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'user_settings'"))
        assertTrue(migrated.columnExists("user_settings", "glass_palette_choice"))
        migrated.execSQL("INSERT INTO user_settings (id, user_bodyweight_lbs) VALUES (1, 185)")
        assertEquals(185, migrated.longFor("SELECT user_bodyweight_lbs FROM user_settings WHERE id = 1"))
        migrated.assertCurrentRoomSchema()
        migrated.close()
    }

    @Test
    fun migration15To16AddsGlassPaletteChoiceWithoutDroppingSettings() {
        val dbName = "migration-15.db"
        migrationHelper.createDatabase(dbName, 15).apply {
            seedCoreWorkoutRows(weightLbs = 2250)
            execSQL("INSERT INTO user_settings (id, user_bodyweight_lbs) VALUES (1, 185)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            dbName,
            APP_DATABASE_VERSION,
            true,
            *Migrations.ALL_MIGRATIONS,
        )

        assertTrue(migrated.columnExists("user_settings", "glass_palette_choice"))
        assertEquals(185, migrated.longFor("SELECT user_bodyweight_lbs FROM user_settings WHERE id = 1"))
        assertTrue(migrated.query("SELECT glass_palette_choice FROM user_settings WHERE id = 1").use { cursor ->
            cursor.moveToFirst() && cursor.isNull(0)
        })
        migrated.assertCurrentRoomSchema()
        migrated.close()
    }

    @Test
    fun legacyVersion1WorkoutRowsMigrateToCurrentWithoutDroppingData() = runTest {
        context.deleteDatabase(LEGACY_SOURCE_DB)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(LEGACY_SOURCE_DB), null).use { db ->
            db.execSQL(
                """
                CREATE TABLE exercise (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    canonicalName TEXT NOT NULL,
                    aliases TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE workout (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dateEpochDay INTEGER NOT NULL,
                    startedAt INTEGER NOT NULL,
                    endedAt INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE workout_set (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    workoutId INTEGER NOT NULL,
                    exerciseId INTEGER NOT NULL,
                    setOrder INTEGER NOT NULL,
                    weightLb REAL NOT NULL,
                    reps INTEGER NOT NULL,
                    loggedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO exercise (id, canonicalName, aliases, createdAt) VALUES (1, 'Bench Press', '', 1)")
            db.execSQL("INSERT INTO workout (id, dateEpochDay, startedAt, endedAt) VALUES (1, 20589, 1, 2)")
            db.execSQL("INSERT INTO workout_set (id, workoutId, exerciseId, setOrder, weightLb, reps, loggedAt) VALUES (1, 1, 1, 1, 135.0, 8, 2)")
            db.execSQL("PRAGMA user_version = 1")
        }

        val migrated = createDb(LEGACY_SOURCE_DB)

        assertEquals("Bench Press", migrated.exerciseDao().getById(1L)?.name)
        val migratedSet = migrated.workoutSetDao().getById(1L)
        assertNotNull(migratedSet)
        assertEquals("2026-05-16", migratedSet?.date)
        assertEquals(1350, migratedSet?.weightLbs)
        assertEquals(8, migratedSet?.reps)
        assertTrue(migratedSet?.completed == true)
        val writableDb = migrated.openHelper.writableDatabase
        assertEquals(APP_DATABASE_VERSION.toLong(), writableDb.longFor("PRAGMA user_version"))
        assertTrue(writableDb.columnExists("user_settings", "glass_palette_choice"))
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
        val exportedSnapshot = Json { ignoreUnknownKeys = true }
            .decodeFromString<UserDataBackup>(backupFile.readText())

        assertEquals(APP_DATABASE_VERSION, exportedSnapshot.metadata.appDbVersion)
        assertTrue(exportResult.entryCount >= 10)
        assertEquals(exportResult.entryCount, importResult.entryCount)
        assertEquals(source.exerciseDao().getAll().size, target.exerciseDao().getAll().size)
        assertEquals(source.workoutSetDao().getAll().size, target.workoutSetDao().getAll().size)
        assertEquals(source.splitExerciseDao().getAll().size, target.splitExerciseDao().getAll().size)
        assertEquals(source.auditDao().getAll().size, target.auditDao().getAll().size)
        assertEquals(source.agentTurnLogDao().getAll().size, target.agentTurnLogDao().getAll().size)
        assertEquals("Bench Press", target.exerciseDao().getById(1L)?.name)
        assertTrue(DataBackupManager.listAutomaticBackups(context).size > backupsBefore)
        assertEquals(source.archivedCycleDao().getAll().size, target.archivedCycleDao().getAll().size)
        assertEquals("Spring Block", target.archivedCycleDao().getById(700L)?.name)
        assertEquals(185, target.userSettingsDao().get()?.userBodyweightLbs)
    }

    @Test
    fun prepackagedAssetMigratesToCurrentRoomSchema() = runTest {
        val db = createAssetDb(PREPACKAGED_ASSET_DB)
        val writableDb = db.openHelper.writableDatabase

        assertEquals(APP_DATABASE_VERSION.toLong(), writableDb.longFor("PRAGMA user_version"))
        assertEquals("ok", writableDb.stringFor("PRAGMA integrity_check"))
        assertFalse(writableDb.query("PRAGMA foreign_key_check").use { cursor -> cursor.moveToFirst() })
        assertNotNull(writableDb.stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'archived_cycle'"))
        assertNotNull(writableDb.stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'user_settings'"))
        assertTrue(writableDb.columnExists("user_settings", "glass_palette_choice"))
    }

    @Test
    fun repositorySplitAndCycleMutationsStayConsistent() = runTest {
        val db = createDb(REPOSITORY_MUTATION_DB)
        val repository = WorkoutRepository(db)
        db.exerciseDao().insertAll(
            listOf(
                Exercise(id = 1L, name = "Bench Press", muscleGroups = "CHEST"),
                Exercise(id = 2L, name = "Incline Press", muscleGroups = "CHEST"),
            )
        )

        val pushId = repository.addCycleSlot("Push")
        val pullId = repository.addCycleSlot("Pull")
        assertEquals(2, db.cycleDao().getCycle()?.numTypes)
        assertEquals(listOf(pushId, pullId), db.cycleSlotDao().getAll().map { it.id })

        db.workoutSetDao().insertAll(
            listOf(
                WorkoutSet(id = 10L, exerciseId = 2L, date = "2026-05-20", setNumber = 1, weightLbs = 650, reps = 10, completed = false),
                WorkoutSet(id = 11L, exerciseId = 1L, date = "2026-05-20", setNumber = 2, weightLbs = 1850, reps = 5, completed = true),
                WorkoutSet(id = 12L, exerciseId = 2L, date = "2026-05-20", setNumber = 3, weightLbs = 700, reps = 10, completed = true),
            )
        )

        repository.saveSplitFromDate(pushId, "2026-05-20")
        assertEquals(listOf(2L, 1L), db.splitExerciseDao().getForSplit(pushId).map { it.exerciseId })

        val assignedDay = repository.assignCycleSlot("2026-05-21", pushId)
        assertEquals(pushId, assignedDay.cycleSlotId)
        assertEquals(1, assignedDay.cycleSlotOccurrence)
        assertEquals(0, assignedDay.cycleSlotType)
        assertEquals(pushId, db.workoutDayDao().getByDate("2026-05-21")?.cycleSlotId)

        repository.deleteCycleSlot(pullId)
        assertEquals(1, db.cycleDao().getCycle()?.numTypes)
        assertEquals(listOf(pushId), db.cycleSlotDao().getAll().map { it.id })
    }

    @Test
    fun fluidSetLifecycleKeepsDraftRowsButProgressFollowsCompletedFlag() = runTest {
        val db = createDb(FLUID_SET_LIFECYCLE_DB)
        val sets = SetRepository(db)
        val workouts = WorkoutRepository(db)
        db.exerciseDao().insertAll(listOf(Exercise(id = 1L, name = "Bench Press", muscleGroups = "CHEST")))
        db.cycleDao().upsert(Cycle(id = 1, numTypes = 1, isActive = true, startDate = "2026-05-01"))

        val firstDraft = sets.addSet("2026-05-15", 1L)
        assertFalse(firstDraft.completed)
        assertEquals(1, firstDraft.setNumber)
        assertEquals(listOf(firstDraft.id), sets.getSetsForDate("2026-05-15").map { it.id })
        assertTrue(sets.getVolumesSince("2026-05-01").isEmpty())
        assertTrue(sets.observeExerciseProgressSummaries().first().isEmpty())
        assertEquals(0, workouts.activeCycleProgress().totalSets)

        val firstChecked = firstDraft.copy(weightLbs = 1850, reps = 5, completed = true)
        sets.updateSet(firstChecked)
        assertTrue(sets.getById(firstDraft.id)?.completed == true)
        assertEquals(listOf(ExerciseVolume(1L, 925L)), sets.getVolumesSince("2026-05-01"))
        assertEquals(1, workouts.activeCycleProgress().totalSets)

        sets.updateSet(firstChecked.copy(completed = false))
        assertFalse(sets.getById(firstDraft.id)?.completed ?: true)
        assertTrue(sets.getVolumesSince("2026-05-01").isEmpty())
        assertEquals(listOf(firstDraft.id), sets.getSetsForDate("2026-05-15").map { it.id })
        assertEquals(0, workouts.activeCycleProgress().totalSets)

        val secondDraft = sets.addSet("2026-05-15", 1L)
        assertFalse(secondDraft.completed)
        assertEquals(2, secondDraft.setNumber)
        assertEquals(1850, secondDraft.weightLbs)
        assertEquals(5, secondDraft.reps)

        val secondChecked = secondDraft.copy(weightLbs = 1950, reps = 4, completed = true)
        sets.updateSet(secondChecked)
        assertEquals(listOf(false, true), sets.getSetsForDate("2026-05-15").map { it.completed })
        assertEquals(listOf(ExerciseVolume(1L, 780L)), sets.getVolumesSince("2026-05-01"))
        assertEquals(1, workouts.activeCycleProgress().totalSets)

        sets.deleteSet(secondDraft.id)
        assertNull(sets.getById(secondDraft.id))
        assertTrue(sets.getVolumesSince("2026-05-01").isEmpty())
        assertEquals(listOf(firstDraft.id), sets.getSetsForDate("2026-05-15").map { it.id })

        sets.deleteSet(firstDraft.id)
        assertTrue(sets.getSetsForDate("2026-05-15").isEmpty())
    }

    @Test
    fun completedOnlyProgressQueriesIgnoreUncheckedWorkingSets() = runTest {
        val db = createDb(COMPLETED_ONLY_DB)
        db.exerciseDao().insertAll(
            listOf(
                Exercise(id = 1L, name = "Bench Press", muscleGroups = "CHEST"),
                Exercise(id = 2L, name = "Back Squat", muscleGroups = "LEGS"),
            )
        )
        db.workoutSetDao().insertAll(
            listOf(
                WorkoutSet(id = 1L, exerciseId = 1L, date = "2026-05-10", setNumber = 1, weightLbs = 1850, reps = 5, completed = true),
                WorkoutSet(id = 2L, exerciseId = 1L, date = "2026-05-10", setNumber = 2, weightLbs = 2250, reps = 5, completed = false),
                WorkoutSet(id = 3L, exerciseId = 1L, date = "2026-05-11", setNumber = 1, weightLbs = 1950, reps = 5, completed = true),
                WorkoutSet(id = 4L, exerciseId = 2L, date = "2026-05-12", setNumber = 1, weightLbs = 3150, reps = 5, completed = false),
            )
        )

        val dao = db.workoutSetDao()

        assertEquals(
            listOf(ExerciseProgressSummary(1L, "Bench Press", false, sessionCount = 2, lastSessionDate = "2026-05-11")),
            dao.observeExerciseProgressSummaries().first(),
        )
        assertEquals(
            listOf(DateVolume("2026-05-11", 975L), DateVolume("2026-05-10", 925L)),
            dao.getVolumeHistory(1L, limit = 10),
        )
        assertEquals(listOf(ExerciseVolume(1L, 1900L)), dao.getVolumesSince("2026-05-01"))
        assertEquals(listOf(3L, 1L), dao.getRecentHistoryForExercise(1L, "2026-05-20").map { it.id })
        assertEquals(1950, dao.getMaxWeightBeforeDate(1L, "2026-05-20"))
        assertEquals(listOf(1L, 3L), dao.getSetsSince(1L, "2026-05-01").map { it.id })
        assertEquals(listOf(1L, 3L), dao.observeSetsSince(1L, "2026-05-01").first().map { it.id })
        assertTrue(dao.getSetsSince(2L, "2026-05-01").isEmpty())
        assertEquals(listOf(ExerciseMaxWeight(1L, 1950)), dao.getAllTimeMaxWeights())
        assertEquals(listOf(ExerciseMaxWeight(1L, 1950)), dao.getMaxWeightsForExercises(listOf(1L, 2L)))
    }

    @Test
    fun archiveCurrentCyclePersistsSnapshotAndAdvancesCycleStart() = runTest {
        val db = createDb(ARCHIVE_SOURCE_DB)
        db.exerciseDao().insertAll(listOf(Exercise(id = 1L, name = "Bench Press", muscleGroups = "CHEST")))
        db.cycleDao().upsert(Cycle(id = 1, numTypes = 1, isActive = true, nextSessionType = 0, startDate = "2026-05-01"))
        db.cycleSlotDao().insertAll(listOf(CycleSlot(id = 10L, name = "Push", orderIndex = 0)))
        db.splitExerciseDao().insertAll(listOf(SplitExercise(id = 20L, splitId = 10L, exerciseId = 1L, orderIndex = 0)))
        db.workoutDayDao().insertAll(listOf(WorkoutDay(date = "2026-05-16", cycleSlotId = 10L)))
        db.workoutSetDao().insertAll(
            listOf(
                WorkoutSet(id = 1L, exerciseId = 1L, date = "2026-05-16", setNumber = 1, weightLbs = 1850, reps = 5, completed = true),
                WorkoutSet(id = 2L, exerciseId = 1L, date = "2026-05-16", setNumber = 2, weightLbs = 1850, reps = 5, completed = false),
            )
        )

        val archiveId = WorkoutRepository(db).archiveCurrentCycle("Spring Block", "2026-05-01", "2026-05-31")
        val archive = db.archivedCycleDao().getById(archiveId)
        assertNotNull(archive)
        val snapshot = Json { ignoreUnknownKeys = true }
            .decodeFromString<CycleSnapshot>(archive!!.snapshotJson)

        assertEquals("Spring Block", archive.name)
        assertEquals(925L, archive.totalVolumeLbs)
        assertEquals(1, archive.totalSessions)
        assertEquals(1, archive.splitCount)
        assertEquals(1, snapshot.totals.totalSets)
        val currentCycle = db.cycleDao().getCycle()
        assertEquals("2026-06-01", currentCycle?.startDate)
        assertFalse(currentCycle?.isActive ?: true)
        assertEquals(0, currentCycle?.numTypes)
        assertNull(currentCycle?.nextSessionType)
        assertTrue(db.cycleSlotDao().getAll().isEmpty())
        assertTrue(db.splitExerciseDao().getAll().isEmpty())
    }

    @Test
    fun archivedCycleOverlapQueryIsInclusive() = runTest {
        val db = createDb(ARCHIVE_OVERLAP_DB)
        db.archivedCycleDao().insert(
            ArchivedCycle(
                name = "Block",
                startDate = "2026-05-01",
                endDate = "2026-05-31",
                splitCount = 1,
                totalVolumeLbs = 100L,
                totalSessions = 1,
                archivedAt = 1L,
                schemaVersion = CYCLE_SNAPSHOT_SCHEMA_VERSION,
                snapshotJson = "{}",
            )
        )

        val dao = db.archivedCycleDao()
        assertEquals(1, dao.countOverlapping("2026-04-01", "2026-05-01"))
        assertEquals(1, dao.countOverlapping("2026-05-31", "2026-06-10"))
        assertEquals(0, dao.countOverlapping("2026-06-01", "2026-06-10"))
    }

    private fun createDb(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .build()
            .also(openDbs::add)

    private fun createAssetDb(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .createFromAsset("database/ecolift.db")
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
        db.archivedCycleDao().insertAll(
            listOf(
                ArchivedCycle(
                    id = 700L,
                    name = "Spring Block",
                    startDate = "2026-04-01",
                    endDate = "2026-04-30",
                    splitCount = 1,
                    totalVolumeLbs = 925L,
                    totalSessions = 1,
                    archivedAt = 3L,
                    schemaVersion = CYCLE_SNAPSHOT_SCHEMA_VERSION,
                    snapshotJson = "{}",
                )
            )
        )
        db.userSettingsDao().upsert(UserSettings(userBodyweightLbs = 185))
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

    private fun SupportSQLiteDatabase.columnExists(tableName: String, columnName: String): Boolean =
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == columnName }
        }

    private fun SupportSQLiteDatabase.assertCurrentRoomSchema() {
        assertEquals(APP_DATABASE_VERSION.toLong(), longFor("PRAGMA user_version"))
        assertNotNull(stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'audit_log'"))
        assertNotNull(stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'agent_turn_log'"))
        assertNotNull(stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'archived_cycle'"))
        assertNotNull(stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'user_settings'"))
        assertNotNull(stringFor("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_split_exercise_splitId_exerciseId'"))
        assertTrue(columnExists("user_settings", "glass_palette_choice"))
    }

    private companion object {
        const val LEGACY_SOURCE_DB = "legacy-source.db"
        const val BACKUP_SOURCE_DB = "backup-source.db"
        const val BACKUP_TARGET_DB = "backup-target.db"
        const val ARCHIVE_SOURCE_DB = "archive-source.db"
        const val ARCHIVE_OVERLAP_DB = "archive-overlap.db"
        const val COMPLETED_ONLY_DB = "completed-only.db"
        const val PREPACKAGED_ASSET_DB = "prepackaged-asset.db"
        const val REPOSITORY_MUTATION_DB = "repository-mutation.db"
        const val FLUID_SET_LIFECYCLE_DB = "fluid-set-lifecycle.db"
    }
}
