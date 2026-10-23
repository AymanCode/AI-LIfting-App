package com.ayman.ecolift.android.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.agent.patches.AtomicPatchRepository
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPatchServiceTest {
    @Test
    fun delete_undo_restores_card_after_another_lift_reuses_its_position() = withFixture { repos, _, service ->
        repos.sets.insert(WorkoutSet(exerciseId = 1, date = DATE, setNumber = 1, weightLbs = 1350, reps = 8))
        val deleted = repos.sets.getAllSets().single()
        val originalCard = repos.sets.cards.value.single()
        val applied = service.applyPatches("delete", listOf(DbPatch.DeleteSet(deleted.id)), true) as PatchResult.Applied
        repos.sets.insert(WorkoutSet(exerciseId = 2, date = DATE, setNumber = 1, weightLbs = 2250, reps = 5))
        val unrelated = repos.sets.getAllSets().single()
        assertEquals(originalCard.position, repos.sets.cards.value.single().position)
        val undo = service.undo(applied.auditId)
        assertTrue(undo.toString(), undo is PatchResult.Applied)
        assertEquals(listOf(deleted, unrelated), repos.sets.getAllSets().sortedBy { it.exerciseId })
        assertEquals(listOf(1L, 2L), repos.sets.cards.value.sortedBy { it.position }.map { it.exerciseId })
        assertEquals(listOf(0, 1), repos.sets.cards.value.sortedBy { it.position }.map { it.position })
        repos.sets.refresh()
        assertEquals(listOf(deleted, unrelated), repos.sets.getAllSets().sortedBy { it.exerciseId })
    }

    @Test
    fun rename_publishes_after_commit_and_a_failed_batch_keeps_the_old_name() = withFixture { repos, audit, service ->
        val failed = service.applyPatches(
            "rename-fail",
            listOf(DbPatch.RenameExercise(1, "Flat Bench"), DbPatch.EditSet(999, reps = 5)),
            true,
        )
        assertTrue(failed.toString(), failed is PatchResult.Failed)
        assertEquals("Bench Press", repos.exercises.exercises.value.first { it.id == 1L }.name)
        assertEquals("Bench Press", repos.exercises.getById(1)?.name)
        assertEquals(0, audit.all().size)
        val applied = service.applyPatches("rename", listOf(DbPatch.RenameExercise(1, "Flat Bench")), true)
        assertTrue(applied.toString(), applied is PatchResult.Applied)
        assertEquals("Flat Bench", repos.exercises.exercises.value.first { it.id == 1L }.name)
        assertEquals(1, audit.all().size)
    }

    @Test
    fun cancellation_after_the_first_insert_discards_rows_cards_and_audit() = withFixture { repos, audit, _ ->
        val interrupted = object : AppRepos by repos {
            override val sets = object : SetRepository by repos.sets,
                AtomicPatchRepository by (repos.sets as AtomicPatchRepository) {
                override suspend fun insert(set: WorkoutSet): LocalCommit<WorkoutSet> {
                    val result = repos.sets.insert(set)
                    assertEquals(0, repos.sets.sets.value.size)
                    assertEquals(0, repos.sets.cards.value.size)
                    assertEquals(0, audit.all().size)
                    throw CancellationException("cancel after first insert: $result")
                }
            }
        }
        var cancelled = false
        try {
            PatchService(interrupted, PatchValidator(), audit).applyPatches(
                "cancel",
                listOf(DbPatch.LogSet(1, DATE, 1, 1350, 8), DbPatch.LogSet(1, DATE, 2, 1350, 6)),
                false,
            )
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(0, repos.sets.getAllSets().size)
        assertEquals(0, repos.sets.cards.value.size)
        assertEquals(0, audit.all().size)
        repos.sets.refresh()
        assertEquals(0, repos.sets.getAllSets().size)
        val applied = PatchService(repos, PatchValidator(), audit).applyPatches(
            "after-cancel",
            listOf(DbPatch.LogSet(1, DATE, 1, 1350, 8)),
            false,
        )
        assertTrue(applied.toString(), applied is PatchResult.Applied)
        assertEquals(1350, repos.sets.getAllSets().single().weightLbs)
        assertEquals(1, audit.all().size)
    }

    @Test
    fun newer_group_edits_prevent_destructive_undo() = withFixture { repos, _, service ->
        repos.sets.insert(WorkoutSet(exerciseId = 1, date = DATE, setNumber = 1, weightLbs = 1350, reps = 8))
        repos.sets.insert(WorkoutSet(exerciseId = 1, date = DATE, setNumber = 2, weightLbs = 1450, reps = 6))
        val deleted = repos.sets.getAllSets().first { it.setNumber == 1 }
        val applied = service.applyPatches("delete", listOf(DbPatch.DeleteSet(deleted.id)), true) as PatchResult.Applied
        val current = repos.sets.getAllSets().single()
        repos.sets.update(current.copy(reps = 10))
        val changed = repos.sets.getAllSets()
        val undo = service.undo(applied.auditId)
        assertTrue(undo.toString(), undo is PatchResult.Failed)
        assertEquals(changed, repos.sets.getAllSets())
        assertEquals(10, repos.sets.getAllSets().single().reps)
    }

    @Test
    fun repeated_edits_undo_in_reverse_order() = withFixture { repos, _, service ->
        repos.sets.insert(WorkoutSet(exerciseId = 1, date = DATE, setNumber = 1, weightLbs = 1350, reps = 8))
        val row = repos.sets.getAllSets().single()
        val result = service.applyPatches(
            "edits",
            listOf(DbPatch.EditSet(row.id, reps = 6), DbPatch.EditSet(row.id, reps = 4)),
            false,
        ) as PatchResult.Applied
        assertEquals(4, repos.sets.getAllSets().single().reps)
        assertTrue(service.undo(result.auditId) is PatchResult.Applied)
        assertEquals(8, repos.sets.getAllSets().single().reps)
    }

    @Test
    fun second_patch_constraint_failure_rolls_back_the_whole_batch() = withFixture { repos, audit, service ->
        repos.sets.insert(WorkoutSet(exerciseId = 2, date = DATE, setNumber = 1, completed = false))
        val before = repos.sets.getAllSets()
        val cards = repos.sets.cards.value
        val result = service.applyPatches(
            "collision",
            listOf(
                DbPatch.LogSet(1, DATE, 1, 1350, 8),
                DbPatch.LogSet(2, DATE, 1, 2250, 5),
            ),
            false,
        )
        assertTrue(result.toString(), result is PatchResult.Failed)
        assertEquals(before, repos.sets.getAllSets())
        assertEquals(cards, repos.sets.cards.value)
        assertEquals(0, audit.all().size)
        repos.sets.refresh()
        assertEquals(before, repos.sets.getAllSets())
    }

    @Test
    fun undo_first_of_two_sets_restores_the_group() = assertDeleteUndo(count = 2, deletedNumber = 1)

    @Test
    fun undo_middle_of_three_sets_restores_the_group() = assertDeleteUndo(count = 3, deletedNumber = 2)

    @Test
    fun undo_final_set_restores_the_original_card() = assertDeleteUndo(count = 1, deletedNumber = 1)

    @Test
    fun delete_then_edit_undo_restores_the_group_and_can_redo() = withFixture { repos, _, service ->
        repos.sets.insert(WorkoutSet(exerciseId = 1, date = DATE, setNumber = 1, weightLbs = 1350, reps = 8))
        repos.sets.insert(WorkoutSet(exerciseId = 1, date = DATE, setNumber = 2, weightLbs = 1450, reps = 6))
        val before = repos.sets.getAllSets().sortedBy { it.setNumber }
        val applied = service.applyPatches(
            "delete-edit",
            listOf(DbPatch.DeleteSet(before[0].id), DbPatch.EditSet(before[1].id, reps = 10)),
            true,
        ) as PatchResult.Applied
        val changed = repos.sets.getAllSets()
        val undo = service.undo(applied.auditId) as PatchResult.Applied
        assertEquals(before, repos.sets.getAllSets().sortedBy { it.setNumber })
        assertTrue(service.undo(undo.auditId) is PatchResult.Applied)
        assertEquals(changed.map { it.reps }, repos.sets.getAllSets().map { it.reps })
        assertEquals(changed.map { it.setNumber }, repos.sets.getAllSets().map { it.setNumber })
    }

    private fun assertDeleteUndo(count: Int, deletedNumber: Int) = withFixture { repos, _, service ->
        repeat(count) { index ->
            repos.sets.insert(
                WorkoutSet(
                    exerciseId = 1,
                    date = DATE,
                    setNumber = index + 1,
                    weightLbs = 1350 + index * 50,
                    reps = 8 - index,
                    completed = true,
                    restTimeSeconds = index * 30,
                    isWarmup = index == 0,
                ),
            )
        }
        val before = repos.sets.getAllSets().sortedBy { it.setNumber }
        val cards = repos.sets.cards.value
        val result = service.applyPatches(
            "delete",
            listOf(DbPatch.DeleteSet(before.first { it.setNumber == deletedNumber }.id)),
            true,
        ) as PatchResult.Applied
        assertEquals(count - 1, repos.sets.getAllSets().size)
        val undo = service.undo(result.auditId)
        assertTrue(undo.toString(), undo is PatchResult.Applied)
        assertEquals(before, repos.sets.getAllSets().sortedBy { it.setNumber })
        assertEquals(cards, repos.sets.cards.value)
        repos.sets.refresh()
        assertEquals(before, repos.sets.getAllSets().sortedBy { it.setNumber })
    }

    private fun withFixture(block: suspend (RoomRepos, InMemoryAuditStore, PatchService) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repos = RoomRepos(db, scope)
        try {
            repos.loadAll()
            RoomStore(db).write {
                upsertExercise(Exercise(id = 1, name = "Bench Press"))
                upsertExercise(Exercise(id = 2, name = "Back Squat"))
            }
            repos.exercises.refresh()
            val audit = InMemoryAuditStore()
            block(repos, audit, PatchService(repos, PatchValidator(), audit))
        } finally {
            repos.close()
            scope.cancel()
            db.close()
        }
    }

    private companion object {
        const val DATE = "2026-10-23"
    }
}
