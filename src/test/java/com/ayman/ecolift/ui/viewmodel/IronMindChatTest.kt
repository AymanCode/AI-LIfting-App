package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.agent.AgentMutationRepos
import com.ayman.ecolift.agent.AgentTurn
import com.ayman.ecolift.agent.FakeReadyEngine
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.test.runVmTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
    fun undoRemovesTheChatSetWithoutOfferingAnotherUndo() = runVmTest {
        val data = AgentMutationRepos(listOf(Exercise(1, "Bench Press", "Chest")))
        val vm = OrchestratorViewModel(data, FakeReadyEngine())
        val events = mutableListOf<AgentTurn.Applied>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.undoEvent.collect { events += it } }

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
