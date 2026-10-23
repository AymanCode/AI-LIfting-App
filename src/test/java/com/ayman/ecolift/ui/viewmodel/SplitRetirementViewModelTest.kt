package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.WorkoutRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SplitRetirementViewModelTest {
    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun split_dial_contains_only_active_slots() = runVmTest {
        CurrentAppMode.mode = AppMode.Demo
        val seed = DemoData.generate(LocalDate(2026, 7, 5))
        val retiredId = seed.slots.first().id
        val repos = DemoRepos(
            seed.copy(
                slots = seed.slots.map { slot ->
                    if (slot.id == retiredId) slot.copy(archivedAt = 1234L) else slot
                },
            ),
        )
        val viewModel = SplitViewModel(repos)

        val state = withTimeout(2_000) {
            viewModel.uiState.first { it.cycle.order.isNotEmpty() }
        }
        val dialIds = state.cycle.order.mapNotNull { (it as? CycleEntry.SplitDay)?.splitId }

        assertEquals(seed.slots.size - 1, dialIds.size)
        assertFalse(retiredId in dialIds)
        assertFalse(state.cards.any { it.slotId == retiredId })
    }

    @Test
    fun a_failed_retire_or_rename_says_so() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 7, 5)))
        val failing = object : WorkoutRepository by base.workout {
            override suspend fun suppressCycleSlot(slotId: Long) = error("network down")
            override suspend fun renameCycleSlot(id: Long, name: String) = error("network down")
        }
        val viewModel = SplitViewModel(object : AppRepos by base { override val workout: WorkoutRepository = failing })
        val slotId = base.workout.getCycleSlots().first().id

        val retire = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { SyncErrors.events.first() } }
        viewModel.retireSplit(slotId)
        assertEquals("Couldn't retire the split. Try again.", retire.await().message)

        val rename = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { SyncErrors.events.first() } }
        viewModel.renameSplit(slotId, "Legs")
        assertEquals("Couldn't rename the split.", rename.await().message)
    }

    @Test
    fun a_failed_add_says_so() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 7, 5)))
        val failing = object : WorkoutRepository by base.workout {
            override suspend fun addCycleSlot(name: String): Long = error("network down")
        }
        val viewModel = SplitViewModel(object : AppRepos by base { override val workout: WorkoutRepository = failing })
        val add = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { SyncErrors.events.first() } }
        viewModel.addSplit("Push")
        assertEquals("Couldn't add the split.", add.await().message)
    }
}
