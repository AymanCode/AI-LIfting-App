package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.agent.AgentMutationRepos
import com.ayman.ecolift.agent.AgentTurn
import com.ayman.ecolift.agent.FakeReadyEngine
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.test.runVmTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class IronMindChatTest {
    @Test
    fun pendingRenameCanBeCancelledThenApplied() = runVmTest {
        val data = AgentMutationRepos(listOf(Exercise(1, "Bench Press", "Chest")))
        val vm = OrchestratorViewModel(data, FakeReadyEngine())
        vm.updateInput("rename Bench Press to T3 Bench")
        vm.sendMessage()
        val pending = withTimeout(2_000) { vm.uiState.first { it.pendingAction != null } }
        assertEquals("Apply", assertNotNull(pending.pendingAction).confirmLabel)
        assertEquals("Bench Press", data.currentExercises.value.single().name)

        vm.dismissPending()
        withTimeout(2_000) { vm.uiState.first { it.pendingAction == null } }
        assertEquals("No changes made.", vm.sessionMessages.value.last().text)
        assertEquals("Bench Press", data.currentExercises.value.single().name)

        vm.updateInput("rename Bench Press to T3 Bench")
        vm.sendMessage()
        withTimeout(2_000) { vm.uiState.first { it.pendingAction != null } }
        vm.confirmPending()
        assertEquals("T3 Bench", data.currentExercises.value.single().name)
    }

    @Test
    fun undoRemainsAvailableToANewCollectorAndEndsWhenUsed() = runVmTest {
        val data = AgentMutationRepos(listOf(Exercise(1, "Bench Press", "Chest")))
        val vm = OrchestratorViewModel(data, FakeReadyEngine())
        vm.updateInput("bench press 135x5")
        vm.sendMessage()
        val original = assertNotNull(vm.undoOffer.value)
        val returningTab = withTimeout(2_000) { vm.undoOffer.first { it != null } }
        assertEquals(original, returningTab)
        assertEquals(listOf(1350 to 5), data.currentSets.value.map { it.weightLbs to it.reps })

        vm.undo(original.auditId)

        assertEquals(emptyList(), data.currentSets.value)
        assertNull(vm.undoOffer.value)
        assertEquals("Undone.", vm.sessionMessages.value.last().text)
    }

    @Test
    fun newerChangeReplacesUndoAndRestoreEndsTheOffer() = runVmTest {
        val data = AgentMutationRepos(listOf(Exercise(1, "Bench Press", "Chest")))
        val generation = com.ayman.ecolift.web.DataGeneration()
        val repos = object : com.ayman.ecolift.web.AppRepos by data {
            override val dataGeneration = generation
        }
        val vm = OrchestratorViewModel(repos, FakeReadyEngine())
        vm.updateInput("bench press 135x5")
        vm.sendMessage()
        val first = assertNotNull(vm.undoOffer.value)
        vm.updateInput("bench press 145x6")
        vm.sendMessage()
        val latest = assertNotNull(vm.undoOffer.value)
        kotlin.test.assertNotEquals(first.auditId, latest.auditId)
        vm.undo(first.auditId)
        assertEquals(listOf(1350 to 5, 1450 to 6), data.currentSets.value.map { it.weightLbs to it.reps })
        assertEquals(latest, vm.undoOffer.value)

        generation.replace { }
        vm.undo(latest.auditId)

        assertNull(vm.undoOffer.value)
        assertEquals(listOf(1350 to 5, 1450 to 6), data.currentSets.value.map { it.weightLbs to it.reps })
    }

    @Test
    fun undoRemovesTheChatSetWithoutOfferingAnotherUndo() = runVmTest {
        val data = AgentMutationRepos(listOf(Exercise(1, "Bench Press", "Chest")))
        val vm = OrchestratorViewModel(data, FakeReadyEngine())
        val events = mutableListOf<AgentTurn.Applied>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.undoOffer.filterNotNull().collect { events += it } }

        vm.updateInput("bench press 135x5")
        vm.sendMessage()
        assertEquals(listOf(1350 to 5), data.currentSets.value.map { it.weightLbs to it.reps })
        assertEquals(1, events.size)
        vm.undo(events.single().auditId)

        assertEquals(emptyList(), data.currentSets.value)
        assertEquals("Undone.", vm.sessionMessages.value.last().text)
        assertEquals(1, events.size)
    }
}
