package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.ui.viewmodel.OrchestratorViewModel
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.DataGeneration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RestoreGenerationTest {
    @Test
    fun `retained pending delete cannot delete a restored row with the same id`() = runVmTest {
        val bench = WorkoutSet(id = 10, exerciseId = 1, date = "2026-05-10", setNumber = 1, weightLbs = 135, reps = 8)
        val squat = bench.copy(exerciseId = 2, weightLbs = 225, reps = 5)
        val storage = AgentMutationRepos(listOf(Exercise(1, "Bench Press"), Exercise(2, "Squat")), listOf(bench))
        val generation = DataGeneration()
        val repos = object : AppRepos by storage {
            override val dataGeneration = generation
        }
        val vm = OrchestratorViewModel(repos, FakeReadyEngine())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.updateInput("delete bench press set from 2026-05-10")
        vm.sendMessage()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingAction)

        generation.replace { storage.currentSets.value = listOf(squat) }
        vm.confirmPending()
        advanceUntilIdle()

        assertEquals(listOf(squat), storage.currentSets.value)
        assertNull(vm.uiState.value.pendingAction)
    }

    @Test
    fun `stale service rejects delete and undo while a fresh request works`() = runTest {
        val bench = WorkoutSet(id = 10, exerciseId = 1, date = "2026-05-10", setNumber = 1, weightLbs = 135, reps = 8)
        val squat = bench.copy(exerciseId = 2, weightLbs = 225, reps = 5)
        val storage = AgentMutationRepos(listOf(Exercise(1, "Bench Press"), Exercise(2, "Squat")), listOf(bench))
        val generation = DataGeneration()
        val repos = object : AppRepos by storage { override val dataGeneration = generation }
        val audits = InMemoryAuditStore()
        val old = PatchService(repos, PatchValidator(), audits)
        val edited = old.applyPatches("edit", listOf(DbPatch.EditSet(10, reps = 9)), false) as PatchResult.Applied
        generation.replace { storage.currentSets.value = listOf(squat) }

        assertTrue(old.applyPatches("stale-delete", listOf(DbPatch.DeleteSet(10)), true) is PatchResult.Rejected)
        assertTrue(old.undo(edited.auditId) is PatchResult.Rejected)
        assertEquals(listOf(squat), storage.currentSets.value)

        val fresh = PatchService(repos, PatchValidator(), audits)
        assertTrue(fresh.undo(edited.auditId) is PatchResult.Failed)
        val applied = fresh.applyPatches("fresh", listOf(DbPatch.EditSet(10, reps = 6)), false) as PatchResult.Applied
        assertEquals(listOf(squat.copy(reps = 6)), storage.currentSets.value)
        assertTrue(fresh.undo(applied.auditId) is PatchResult.Applied)
        assertEquals(listOf(squat), storage.currentSets.value)
        assertTrue(fresh.undo(applied.auditId) is PatchResult.Failed)
        assertEquals(listOf(squat), storage.currentSets.value)
    }

    @Test
    fun `action waiting behind replacement is rejected at the mutation boundary`() = runTest {
        val bench = WorkoutSet(id = 10, exerciseId = 1, date = "2026-05-10", setNumber = 1, weightLbs = 135, reps = 8)
        val squat = bench.copy(exerciseId = 2)
        val storage = AgentMutationRepos(listOf(Exercise(1, "Bench Press"), Exercise(2, "Squat")), listOf(bench))
        val generation = DataGeneration()
        val repos = object : AppRepos by storage { override val dataGeneration = generation }
        val service = PatchService(repos, PatchValidator(), InMemoryAuditStore())
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val replacement = async {
            generation.replace {
                entered.complete(Unit)
                finish.await()
                storage.currentSets.value = listOf(squat)
            }
        }
        entered.await()
        val action = async(start = CoroutineStart.UNDISPATCHED) { service.applyPatches("waiting", listOf(DbPatch.DeleteSet(10)), true) }
        finish.complete(Unit)
        replacement.await()
        assertTrue(action.await() is PatchResult.Rejected)
        assertEquals(listOf(squat), storage.currentSets.value)
    }

    @Test
    fun `undo expires after fifty actions without replaying a serialized inverse`() = runTest {
        val original = WorkoutSet(id = 10, exerciseId = 1, date = "2026-05-10", setNumber = 1, weightLbs = 135, reps = 8)
        val storage = AgentMutationRepos(listOf(Exercise(1, "Bench Press")), listOf(original))
        val service = PatchService(storage, PatchValidator(), InMemoryAuditStore())
        val actions = (1..51).map { n ->
            service.applyPatches("edit-$n", listOf(DbPatch.EditSet(10, reps = n)), false) as PatchResult.Applied
        }
        assertTrue(service.undo(actions.first().auditId) is PatchResult.Failed)
        assertEquals(listOf(original.copy(reps = 51)), storage.currentSets.value)
        assertTrue(service.undo(actions.last().auditId) is PatchResult.Applied)
        assertEquals(listOf(original.copy(reps = 50)), storage.currentSets.value)
    }

    @Test
    fun `failed import preserves generation but a reload failure still invalidates it`() = runTest {
        val generation = DataGeneration()
        runCatching { generation.replace { error("invalid backup") } }
        assertEquals(0L, generation.changes.value)
        assertEquals("current", generation.withCurrent(0) { "current" })
        runCatching { generation.replace(reload = { error("reload failed") }) { "imported" } }
        assertEquals(1L, generation.changes.value)
        assertNull(generation.withCurrent(0) { "stale" })
        assertEquals("current", generation.withCurrent(1) { "current" })
    }
}
