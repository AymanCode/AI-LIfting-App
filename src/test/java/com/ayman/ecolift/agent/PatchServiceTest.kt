package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PatchServiceTest {

    private val bench = Exercise(id = 10L, name = "Bench Press")
    private val squat = Exercise(id = 11L, name = "Squat")
    private val logged = WorkoutSet(
        id = 1L,
        exerciseId = 10L,
        date = "2026-04-16",
        setNumber = 1,
        weightLbs = 135,
        reps = 8,
        completed = true,
    )
    private val earlier = WorkoutSet(
        id = 2L,
        exerciseId = 10L,
        date = "2026-04-15",
        setNumber = 1,
        weightLbs = 100,
        reps = 5,
        completed = true,
    )

    private lateinit var repos: AgentMutationRepos
    private lateinit var auditStore: InMemoryAuditStore
    private lateinit var service: PatchService

    @Before
    fun setUp() {
        repos = AgentMutationRepos(listOf(bench, squat), listOf(logged, earlier))
        auditStore = InMemoryAuditStore()
        service = PatchService(repos, PatchValidator(), auditStore)
    }

    @Test
    fun `invalid patch rejected and stores nothing`() = runTest {
        val before = repos.currentSets.value
        val result = service.applyPatches(
            "req-1",
            listOf(DbPatch.LogSet(exerciseId = -1, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8)),
            false,
        )
        assertTrue(result is PatchResult.Rejected)
        assertEquals(before, repos.currentSets.value)
        assertTrue(auditStore.all().isEmpty())
    }

    @Test
    fun `empty patch list succeeds with zero count`() = runTest {
        val result = service.applyPatches("req-empty", emptyList(), false)
        assertTrue(result is PatchResult.Applied)
        assertEquals(0, (result as PatchResult.Applied).patchCount)
        assertEquals(1, auditStore.all().size)
        assertEquals(2, repos.currentSets.value.size)
    }

    @Test
    fun `DeleteSet without confirmation rejected and keeps the row`() = runTest {
        val result = service.applyPatches("req-2", listOf(DbPatch.DeleteSet(setId = 1L)), userConfirmed = false)
        assertTrue(result is PatchResult.Rejected)
        assertEquals(logged, repos.currentSets.value.first { it.id == 1L })
        assertTrue(auditStore.all().isEmpty())
    }

    @Test
    fun `RenameExercise without confirmation rejected and keeps the name`() = runTest {
        val result = service.applyPatches(
            "req-3",
            listOf(DbPatch.RenameExercise(exerciseId = 10L, newName = "Flat Bench")),
            userConfirmed = false,
        )
        assertTrue(result is PatchResult.Rejected)
        assertEquals("Bench Press", repos.currentExercises.value.first { it.id == 10L }.name)
        assertTrue(auditStore.all().isEmpty())
    }

    @Test
    fun `LogSet stores the set and its inverse deletes that id`() = runTest {
        val result = service.applyPatches(
            "req-4",
            listOf(DbPatch.LogSet(exerciseId = 10L, date = "2026-04-20", setNumber = 1, weightLbs = 135, reps = 8)),
            false,
        )
        assertTrue(result is PatchResult.Applied)
        val inserted = repos.currentSets.value.single { it.date == "2026-04-20" }
        assertEquals(10L, inserted.exerciseId)
        assertEquals(135, inserted.weightLbs)
        assertEquals(8, inserted.reps)
        assertTrue(inserted.completed)
        val inverse = inverseOf((result as PatchResult.Applied).auditId)
        val delete = inverse.single() as DbPatch.DeleteSet
        assertEquals(inserted.id, delete.setId)
    }

    @Test
    fun `EditSet changes the row and undo restores it`() = runTest {
        val result = service.applyPatches(
            "req-6",
            listOf(DbPatch.EditSet(setId = 1L, weightLbs = 225, reps = 3)),
            false,
        )
        assertTrue(result is PatchResult.Applied)
        assertEquals(225, repos.currentSets.value.first { it.id == 1L }.weightLbs)
        assertEquals(3, repos.currentSets.value.first { it.id == 1L }.reps)
        val inverse = inverseOf((result as PatchResult.Applied).auditId).single() as DbPatch.EditSet
        assertEquals(135, inverse.weightLbs)
        assertEquals(8, inverse.reps)

        val undone = service.undo(result.auditId)
        assertTrue(undone is PatchResult.Applied)
        assertEquals(135, repos.currentSets.value.first { it.id == 1L }.weightLbs)
        assertEquals(8, repos.currentSets.value.first { it.id == 1L }.reps)
    }

    @Test
    fun `undoing an undo reapplies the edit once`() = runTest {
        val result = service.applyPatches("req-redo", listOf(DbPatch.EditSet(setId = 1L, reps = 3)), false) as PatchResult.Applied
        val undone = service.undo(result.auditId) as PatchResult.Applied
        assertEquals(8, repos.currentSets.value.first { it.id == 1L }.reps)

        assertTrue(service.undo(undone.auditId) is PatchResult.Applied)
        assertEquals(3, repos.currentSets.value.first { it.id == 1L }.reps)
        assertTrue(service.undo(undone.auditId) is PatchResult.Failed)
    }

    @Test
    fun `DeleteSet removes the row and undo logs it back`() = runTest {
        val result = service.applyPatches("req-7", listOf(DbPatch.DeleteSet(setId = 1L)), userConfirmed = true)
        assertTrue(result is PatchResult.Applied)
        assertNull(repos.currentSets.value.firstOrNull { it.id == 1L })
        val inverse = inverseOf((result as PatchResult.Applied).auditId).single() as DbPatch.LogSet
        assertEquals(135, inverse.weightLbs)
        assertEquals(8, inverse.reps)
        assertEquals("2026-04-16", inverse.date)

        val undone = service.undo(result.auditId)
        assertTrue(undone is PatchResult.Applied)
        val restored = repos.currentSets.value.single { it.date == "2026-04-16" }
        assertEquals(10L, restored.exerciseId)
        assertEquals(135, restored.weightLbs)
        assertEquals(8, restored.reps)
    }

    @Test
    fun `MoveWorkoutDay moves the sets and undo moves them back`() = runTest {
        val result = service.applyPatches(
            "req-8",
            listOf(DbPatch.MoveWorkoutDay(currentDate = "2026-04-15", newDate = "2026-04-17")),
            false,
        )
        assertTrue(result is PatchResult.Applied)
        assertEquals(listOf("2026-04-15" to "2026-04-17"), repos.movedDays.toList())
        assertEquals("2026-04-17", repos.currentSets.value.first { it.id == 2L }.date)
        assertEquals("2026-04-16", repos.currentSets.value.first { it.id == 1L }.date)
        val inverse = inverseOf((result as PatchResult.Applied).auditId).single() as DbPatch.MoveWorkoutDay
        assertEquals("2026-04-17", inverse.currentDate)
        assertEquals("2026-04-15", inverse.newDate)

        val undone = service.undo(result.auditId)
        assertTrue(undone is PatchResult.Applied)
        assertEquals("2026-04-15", repos.currentSets.value.first { it.id == 2L }.date)
        assertEquals(
            listOf("2026-04-15" to "2026-04-17", "2026-04-17" to "2026-04-15"),
            repos.movedDays.toList(),
        )
    }

    @Test
    fun `RenameExercise changes the name and undo restores it`() = runTest {
        val result = service.applyPatches(
            "req-10",
            listOf(DbPatch.RenameExercise(exerciseId = 10L, newName = "Flat Bench")),
            userConfirmed = true,
        )
        assertTrue(result is PatchResult.Applied)
        assertEquals("Flat Bench", repos.currentExercises.value.first { it.id == 10L }.name)
        val inverse = inverseOf((result as PatchResult.Applied).auditId).single() as DbPatch.RenameExercise
        assertEquals("Bench Press", inverse.newName)

        val undone = service.undo(result.auditId)
        assertTrue(undone is PatchResult.Applied)
        assertEquals("Bench Press", repos.currentExercises.value.first { it.id == 10L }.name)
    }

    @Test
    fun `RenameExercise reports a name collision and keeps the old name`() = runTest {
        val result = service.applyPatches(
            "req-collision",
            listOf(DbPatch.RenameExercise(exerciseId = 10L, newName = "Squat")),
            userConfirmed = true,
        )
        assertTrue(result is PatchResult.Failed)
        assertTrue((result as PatchResult.Failed).error.contains("Squat"))
        assertEquals("Bench Press", repos.currentExercises.value.first { it.id == 10L }.name)
        assertTrue(auditStore.all().isEmpty())
    }

    @Test
    fun `audit row keeps the request id and the forward patch`() = runTest {
        val result = service.applyPatches(
            "req-11",
            listOf(DbPatch.LogSet(exerciseId = 10L, date = "2026-04-21", setNumber = 1, weightLbs = 135, reps = 8)),
            false,
        )
        val applied = result as PatchResult.Applied
        val audit = auditStore.getById(applied.auditId)!!
        assertEquals("req-11", audit.requestId)
        assertFalse(audit.isUndo)
        val forward = Json.decodeFromString<List<DbPatch>>(audit.serializedPatches)
        assertTrue(forward.single() is DbPatch.LogSet)
    }

    @Test
    fun `undo returns Failed when audit not found`() = runTest {
        val before = repos.currentSets.value
        val result = service.undo(999L)
        assertTrue(result is PatchResult.Failed)
        assertEquals(before, repos.currentSets.value)
    }

    @Test
    fun `EditSet of a missing row returns Failed`() = runTest {
        val before = repos.currentSets.value
        val result = service.applyPatches("req-13", listOf(DbPatch.EditSet(setId = 999L, reps = 5)), false)
        assertTrue(result is PatchResult.Failed)
        assertEquals(before, repos.currentSets.value)
        assertTrue(auditStore.all().isEmpty())
    }

    private fun inverseOf(auditId: Long): List<DbPatch> {
        val audit = auditStore.getById(auditId)!!
        return Json.decodeFromString<List<DbPatch>>(audit.serializedInverse)
    }
}
