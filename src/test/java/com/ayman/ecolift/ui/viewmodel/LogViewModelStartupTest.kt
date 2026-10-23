package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.installLogSearch
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals

class LogViewModelStartupTest {
    @Test
    fun first_frame_contains_the_loaded_workout_before_subscription() = runVmTest {
        val date = WorkoutDates.today()
        val data = ViewModelMutationTestRepos(
            listOf(WorkoutSet(101, 10, date, 1, 1350, 8, completed = true)),
        )
        val vm = LogViewModel(data.repos, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate)

        assertEquals(listOf("Bench Press"), vm.uiState.value.exercises.map { it.name })
        assertEquals(1350, vm.uiState.value.exercises.single().sets.single().weightLbs)
        assertEquals(true, vm.uiState.value.exercises.single().sets.single().completed)
        assertEquals(listOf(101L), vm.uiState.first().exercises.flatMap { it.sets }.map { it.id })
    }

    @Test
    fun first_frame_uses_the_requested_day_and_preserves_a_truly_empty_day() = runVmTest {
        val data = ViewModelMutationTestRepos(
            listOf(WorkoutSet(101, 10, "2026-08-14", 1, 1350, 8)),
        )
        try {
            installLogSearch("?date=2026-08-14")
            val loaded = LogViewModel(data.repos, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate)
            assertEquals("2026-08-14", loaded.uiState.value.currentDate)
            assertEquals(listOf(101L), loaded.uiState.value.exercises.flatMap { it.sets }.map { it.id })

            installLogSearch("?date=2026-08-15")
            val empty = LogViewModel(data.repos, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate)
            assertEquals("2026-08-15", empty.uiState.value.currentDate)
            assertEquals(emptyList(), empty.uiState.value.exercises)
        } finally {
            installLogSearch("")
        }
    }
}
