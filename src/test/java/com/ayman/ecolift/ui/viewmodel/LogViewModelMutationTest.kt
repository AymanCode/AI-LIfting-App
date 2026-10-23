package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.CurrentAppMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogViewModelMutationTest {
    @BeforeTest
    fun setUp() { CurrentAppMode.mode = AppMode.Authenticated }

    @AfterTest
    fun tearDown() { CurrentAppMode.mode = AppMode.Authenticated }

    @Test
    fun toggleWarmup_updates_only_the_requested_set_and_dispatches_each_flip() = runVmTest {
        val h = LogMutationHarness()
        h.showDate()

        h.vm.toggleWarmup(101L)
        h.awaitSet { it.isWarmup }

        assertEquals(h.initial[0].copy(isWarmup = true), h.data.currentSets.value.first())
        assertEquals(h.initial[1], h.data.currentSets.value.last())
        assertEquals(listOf(h.initial[0].copy(isWarmup = true)), h.data.updates)

        h.vm.toggleWarmup(101L)
        h.awaitSet { !it.isWarmup }
        assertEquals(h.initial, h.data.currentSets.value)
        assertEquals(listOf(true, false), h.data.updates.map { it.isWarmup })
    }

    @Test
    fun toggleBodyweight_updates_only_the_requested_set_and_flips_back() = runVmTest {
        val h = LogMutationHarness()
        h.showDate()

        h.vm.toggleBodyweight(101L)
        h.awaitSet { it.isBodyweight }

        assertTrue(h.data.currentSets.value.first().isBodyweight)
        assertFalse(h.data.currentSets.value.last().isBodyweight)
        assertEquals(h.initial[1], h.data.currentSets.value.last())
        assertEquals(listOf(101L), h.data.updates.map { it.id })

        h.vm.toggleBodyweight(101L)
        h.awaitSet { !it.isBodyweight }
        assertEquals(listOf(true, false), h.data.updates.map { it.isBodyweight })
    }

    @Test
    fun updateExerciseName_renames_through_the_exercise_repository() = runVmTest {
        val h = LogMutationHarness()
        h.showDate()

        h.vm.updateExerciseName(10L, "Incline Bench Press")
        withTimeout(TIMEOUT) {
            h.data.currentExercises.first { rows -> rows.any { it.id == 10L && it.name == "Incline Bench Press" } }
        }

        assertEquals(listOf(10L to "Incline Bench Press"), h.data.renames)
        assertEquals("Row", h.data.exercises.getById(20L)?.name)
        assertEquals(h.initial, h.data.currentSets.value)
        assertNull(h.vm.uiState.value.uiMessage)
    }

    @Test
    fun updateExerciseName_reports_collision_without_renaming_either_exercise() = runVmTest {
        // Offline uses the existing collision message instead of offering an online merge.
        val h = LogMutationHarness()
        h.showDate()
        val before = h.data.currentExercises.value

        h.vm.updateExerciseName(10L, "Row")
        val state = withTimeout(TIMEOUT) {
            h.vm.uiState.first { it.uiMessage == "That exercise name is already in use." }
        }

        assertEquals("That exercise name is already in use.", state.uiMessage)
        assertEquals(listOf(10L to "Row"), h.data.renames)
        assertEquals(before, h.data.currentExercises.value)
        assertEquals(h.initial, h.data.currentSets.value)
    }

    private class LogMutationHarness {
        val initial = listOf(
            WorkoutSet(101L, 10L, DATE, 1, 100, 8, syncId = "mutation-set-1"),
            WorkoutSet(102L, 10L, DATE, 2, 100, 8, syncId = "mutation-set-2"),
        )
        val data = ViewModelMutationTestRepos(initial)
        val vm = LogViewModel(data.repos, isOnline = { false })

        suspend fun showDate() {
            vm.selectDate(DATE)
            // viewModelScope uses the real dispatcher on wasmJs; await observable state.
            withTimeout(TIMEOUT) {
                vm.uiState.first { it.currentDate == DATE && it.exercises.sumOf { ex -> ex.sets.size } == 2 }
            }
        }

        suspend fun awaitSet(predicate: (WorkoutSet) -> Boolean) {
            withTimeout(TIMEOUT) {
                data.currentSets.first { rows -> rows.any { it.id == 101L && predicate(it) } }
            }
        }
    }

    private companion object {
        const val DATE = "2026-08-14"
        const val TIMEOUT = 2_000L
    }
}
