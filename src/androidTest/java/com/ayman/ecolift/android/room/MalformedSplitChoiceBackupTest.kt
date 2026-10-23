package com.ayman.ecolift.android.room

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.DataBackupManager
import com.ayman.ecolift.data.IsolatedBackupContext
import com.ayman.ecolift.data.Migrations
import com.ayman.ecolift.data.room.CycleSlot
import com.ayman.ecolift.data.room.Exercise
import com.ayman.ecolift.data.room.WorkoutSet
import com.ayman.ecolift.data.splits.SplitChoicesState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MalformedSplitChoiceBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val names = listOf(IMPORT_DB, PLANT_DB)

    @After
    fun tearDown() {
        names.forEach(context::deleteDatabase)
    }

    @Test
    fun importRejectsABadNestedChoiceAndLeavesTheDatabase() = runBlocking {
        context.deleteDatabase(IMPORT_DB)
        val db = open(IMPORT_DB)
        try {
            db.exerciseDao().insert(Exercise(id = 1, name = "Bench", muscleGroups = "CHEST", createdAt = 1))
            db.workoutSetDao().insert(
                WorkoutSet(id = 10, exerciseId = 1, date = "2026-08-14", setNumber = 1, weightLbs = 1350, reps = 8),
            )
            db.cycleSlotDao().upsert(CycleSlot(id = 7, name = "Push", orderIndex = 0))
            db.splitMemberChoiceDao().upsert(RoomSplitMemberChoice(splitId = 7, revision = 1, inSplitJson = "[1]", otherOptionsJson = "[]"))
            val exercises = db.exerciseDao().getAll()
            val sets = db.workoutSetDao().getAll()
            val choices = db.splitMemberChoiceDao().getAll()
            val backups = IsolatedBackupContext.open(context)
            try {
                val backupNames = DataBackupManager.listAutomaticBackups(backups).map { it.fileName }

                val exported = File(context.cacheDir, "f4-choice-export.json")
                DataBackupManager.exportToUri(backups, db, Uri.fromFile(exported))
                val bad = File(context.cacheDir, "f4-choice-bad.json")
                bad.writeText(backupWithExactBadChoice(exported.readText()))
                assertTrue(bad.readText().contains(BAD_CHOICE))

                val failure = runCatching {
                    DataBackupManager.importFromUri(backups, db, Uri.fromFile(bad))
                }.exceptionOrNull()
                assertEquals("JsonDecodingException", failure?.javaClass?.simpleName)
                assertEquals(exercises, db.exerciseDao().getAll())
                assertEquals(sets, db.workoutSetDao().getAll())
                assertEquals(choices, db.splitMemberChoiceDao().getAll())
                assertEquals(backupNames, DataBackupManager.listAutomaticBackups(backups).map { it.fileName })
            } finally {
                backups.deleteIsolatedFiles()
            }

            val crashes = mutableListOf<Throwable>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error -> crashes.add(error) })
            val repos = RoomRepos(db, scope)
            try {
                repos.loadAll()
                delay(200)
                val state = repos.workout.splitMemberChoices.value
                assertTrue(state is SplitChoicesState.Ready)
                assertEquals(listOf(7L), (state as SplitChoicesState.Ready).choices.keys.sorted())
                assertTrue(crashes.isEmpty())
            } finally {
                repos.close()
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun loadAllSkipsAPlantedBadChoice() = runBlocking {
        context.deleteDatabase(PLANT_DB)
        val db = open(PLANT_DB)
        try {
            db.exerciseDao().insert(Exercise(id = 1, name = "Bench", muscleGroups = "CHEST", createdAt = 1))
            db.workoutSetDao().insert(
                WorkoutSet(id = 10, exerciseId = 1, date = "2026-08-14", setNumber = 1, weightLbs = 1350, reps = 8),
            )
            db.cycleSlotDao().upsert(CycleSlot(id = 7, name = "Push", orderIndex = 0))
            db.cycleSlotDao().upsert(CycleSlot(id = 8, name = "Pull", orderIndex = 1))
            db.splitMemberChoiceDao().upsert(RoomSplitMemberChoice(7, 1, "[1]", "[]"))
            db.splitMemberChoiceDao().upsert(RoomSplitMemberChoice(8, 1, "[1]", "[2]"))
            db.openHelper.writableDatabase.execSQL(
                "UPDATE split_member_choice SET inSplitJson = 'not-json' WHERE splitId = 7",
            )
            val crashes = mutableListOf<Throwable>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error -> crashes.add(error) })
            val repos = RoomRepos(db, scope)
            try {
                repos.loadAll()
                delay(200)
                val state = repos.workout.splitMemberChoices.value
                assertTrue(state is SplitChoicesState.Ready)
                val ready = state as SplitChoicesState.Ready
                assertFalse(ready.choices.containsKey(7L))
                assertEquals(listOf(2L), ready.choices.getValue(8L).board.otherOptions)
                assertEquals(1350, db.workoutSetDao().getAll().single().weightLbs)
                assertTrue(crashes.isEmpty())
            } finally {
                repos.close()
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        } finally {
            db.close()
        }
    }

    private fun open(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .build()

    private fun backupWithExactBadChoice(exported: String): String {
        val root = Json.parseToJsonElement(exported).jsonObject
        val bad = Json.parseToJsonElement("[$BAD_CHOICE]")
        return JsonObject((root - "splitMemberChoices") + ("splitMemberChoices" to bad)).toString()
    }

    private companion object {
        const val IMPORT_DB = "f4-malformed-choice-import.db"
        const val PLANT_DB = "f4-malformed-choice-plant.db"
        const val BAD_CHOICE = """{"splitId":7,"revision":1,"inSplitJson":"not-json","otherOptionsJson":"[]"}"""
    }
}
