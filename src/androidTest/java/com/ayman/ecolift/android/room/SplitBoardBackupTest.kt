package com.ayman.ecolift.android.room

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.android.platform.RestoreSource
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.DataBackupManager
import com.ayman.ecolift.data.UserDataBackup
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.effectiveSplitMembers
import com.ayman.ecolift.ui.MainActivity
import com.ayman.ecolift.web.AppRepos
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SplitBoardBackupTest {
    @Test
    fun export_replaces_a_longer_existing_backup_without_leaving_old_bytes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val file = File(context.cacheDir, "sp-overwrite.json").apply { writeText("old backup bytes".repeat(10_000)) }
        val result = DataBackupManager.exportToUri(context, db, Uri.fromFile(file))
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<UserDataBackup>(file.readText())
        assertEquals(result.createdAtEpochMs, decoded.metadata.createdAtEpochMs)
        assertEquals(db.exerciseDao().getAll(), decoded.exercises)
        assertEquals(db.splitMemberChoiceDao().getAll(), decoded.splitMemberChoices)
    }

    @Test
    fun owner_restore_replaces_newer_cached_boards_with_exported_order_options_and_empty_board() = runBlocking {
        withOwner { context, db, scenario, owner, repos ->
            val ids = listOf("SP backup Gamma", "SP backup Alpha", "SP backup Beta").map { repos.exercises.getOrCreate(it).id }
            val slot = repos.workout.addCycleSlot("SP backup manual")
            val emptySlot = repos.workout.addCycleSlot("SP backup empty")
            val blankSlot = repos.workout.addCycleSlot("SP backup saved empty")
            repos.workout.saveSplitMembers(slot, EffectiveSplitMembers.of(ids.take(2), ids.drop(2)))
            repos.workout.saveSplitMembers(emptySlot, EffectiveSplitMembers.of(emptyList(), ids))
            repos.workout.saveSplitMembers(blankSlot, EffectiveSplitMembers.of(emptyList(), emptyList()))
            val expected = db.splitMemberChoiceDao().getAll()
            val backup = File(context.cacheDir, "sp-boards.json")
            val exported = DataBackupManager.exportToUri(context, db, Uri.fromFile(backup))
            val encoded = Json.parseToJsonElement(backup.readText()) as JsonObject
            assertTrue("Export must include the saved boards", encoded.containsKey("splitMemberChoices"))
            val withoutBoards = File(context.cacheDir, "sp-no-boards.json")
            withoutBoards.writeText(JsonObject(encoded - "splitMemberChoices").toString())
            val oldExportCount = DataBackupManager.importFromUri(context, db, Uri.fromFile(withoutBoards)).entryCount
            assertEquals(expected.size, exported.entryCount - oldExportCount)
            repos.loadAll()
            repos.workout.saveSplitMembers(slot, EffectiveSplitMembers.of(ids.reversed(), emptyList()))
            repos.workout.saveSplitMembers(slot, EffectiveSplitMembers.of(ids.drop(1), ids.take(1)))
            repos.workout.saveSplitMembers(emptySlot, EffectiveSplitMembers.of(ids, emptyList()))
            scenario.recreate()
            assertSame(repos, graph(scenario))

            restore(scenario, owner, backup)

            assertEquals(expected, db.splitMemberChoiceDao().getAll())
            val choices = repos.workout.splitMemberChoices.value as SplitChoicesState.Ready
            assertEquals(ids.take(2), choices.choices.getValue(slot).board.inSplit)
            assertEquals(ids.drop(2), choices.choices.getValue(slot).board.otherOptions)
            assertEquals(emptyList<Long>(), choices.choices.getValue(emptySlot).board.inSplit)
            assertEquals(ids, choices.choices.getValue(emptySlot).board.otherOptions)
            assertEquals(emptyList<Long>(), choices.choices.getValue(blankSlot).board.inSplit)
            assertEquals(emptyList<Long>(), choices.choices.getValue(blankSlot).board.otherOptions)
            assertSame(repos, graph(scenario))
        }
    }

    @Test
    fun older_backup_without_board_field_clears_cached_choices_and_keeps_anchor_fallback() = runBlocking {
        withOwner { context, db, scenario, owner, repos ->
            val first = repos.exercises.getOrCreate("SP legacy first").id
            val second = repos.exercises.getOrCreate("SP legacy second").id
            val slot = repos.workout.addCycleSlot("SP legacy manual")
            repos.workout.appendSplitExercise(slot, first)
            repos.workout.appendSplitExercise(slot, second)
            val backup = File(context.cacheDir, "sp-legacy.json")
            DataBackupManager.exportToUri(context, db, Uri.fromFile(backup))
            val encoded = Json.parseToJsonElement(backup.readText()) as JsonObject
            backup.writeText(JsonObject(encoded - "splitMemberChoices").toString())
            repos.workout.saveSplitMembers(slot, EffectiveSplitMembers.of(listOf(second), listOf(first)))

            restore(scenario, owner, backup)

            assertEquals(emptyList<RoomSplitMemberChoice>(), db.splitMemberChoiceDao().getAll())
            val choices = repos.workout.splitMemberChoices.value as SplitChoicesState.Ready
            assertEquals(emptyMap<Long, Any>(), choices.choices)
            val shown = effectiveSplitMembers(slot, null, db.splitExerciseDao().getAll().map { row ->
                com.ayman.ecolift.data.SplitExercise(row.id, row.splitId, row.exerciseId, row.orderIndex, row.userAdded)
            }, choices.choices[slot], { it })
            assertEquals(listOf(first, second), shown.inSplit)
            assertSame(repos, graph(scenario))
        }
    }

    private suspend fun withOwner(block: suspend (Context, AppDatabase, ActivityScenario<MainActivity>, RepositoryOwner, AppRepos) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val repos = graph(scenario)
            lateinit var owner: RepositoryOwner
            scenario.onActivity { owner = it.repositoryOwner }
            val original = File(context.cacheDir, "sp-original.json")
            DataBackupManager.exportToUri(context, db, Uri.fromFile(original))
            var primaryFailure: Throwable? = null
            try {
                block(context, db, scenario, owner, repos)
            } catch (failure: Throwable) {
                primaryFailure = failure
                throw failure
            } finally {
                try {
                    restore(scenario, owner, original)
                } catch (cleanupFailure: Throwable) {
                    if (primaryFailure == null) throw cleanupFailure
                    primaryFailure.addSuppressed(cleanupFailure)
                }
            }
        }
    }

    private suspend fun restore(scenario: ActivityScenario<MainActivity>, owner: RepositoryOwner, backup: File) {
        scenario.onActivity {
            owner.backups.requestRestore(RestoreSource.Document(Uri.fromFile(backup).toString()))
            owner.confirmRestore()
        }
        try {
            withTimeout(15_000) {
                while (owner.backups.state.value.busy || owner.backups.state.value.message == null) delay(25)
            }
        } catch (failure: TimeoutCancellationException) {
            val atDeadline = owner.backups.state.value
            val diagnostics = withTimeoutOrNull(5_000) {
                owner.backups.state.first { !it.busy }
                val context = ApplicationProvider.getApplicationContext<Context>()
                val db = AppDatabase.getInstance(context)
                val expected = Json { ignoreUnknownKeys = true }.decodeFromString<UserDataBackup>(backup.readText())
                val boards = db.splitMemberChoiceDao().getAll()
                val anchors = db.splitExerciseDao().getAll()
                val sets = db.workoutSetDao().getAll()
                val cached = graph(scenario).workout.splitMemberChoices.value as? SplitChoicesState.Ready
                val expectedChoices = expected.splitMemberChoices.map { it.toChoice() }.associateBy { it.splitId }
                "afterReads=${owner.backups.state.value}, boardRowsMatch=${boards == expected.splitMemberChoices}, " +
                    "cachedBoardsMatch=${cached?.confirmed == expectedChoices}, " +
                    "anchorRowsMatch=${anchors == expected.splitExercises}, workoutRowsMatch=${sets == expected.workoutSets}"
            }
            throw AssertionError(
                "Restore ${backup.name} timed out: atDeadline=$atDeadline, diagnostics=$diagnostics",
                failure,
            )
        }
        assertTrue("Restore ${backup.name}: ${owner.backups.state.value.message}", owner.backups.state.value.message!!.startsWith("Imported "))
    }

    private suspend fun graph(scenario: ActivityScenario<MainActivity>): AppRepos {
        lateinit var loaded: Deferred<LoadedRepositories>
        scenario.onActivity { loaded = it.repositoryOwner.initialize(false) }
        return withTimeout(30_000) { loaded.await().repos }
    }
}
