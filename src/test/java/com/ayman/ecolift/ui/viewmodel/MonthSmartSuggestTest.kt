package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.splits.LearnedFixtures
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.LearnedSplitsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthSmartSuggestTest {
    @Test
    fun offMonthRollupDropsHiddenEngineSlotWhileOnKeepsIt() = runVmTest {
        val engineSlot = LearnedFixtures.slot(42, name = "", learned = true)
        val userSlot = LearnedFixtures.slot(7, name = "Push", learned = false)
        val today = LocalDate.parse(WorkoutDates.today())
        // Last month: with no sessions this month the sheet opens there, whatever today's date is.
        val lastMonth = today.minus(DatePeriod(months = 1))
        val month = lastMonth.monthNumber.toString().padStart(2, '0')
        val userDate = "${lastMonth.year}-$month-10"
        val engineDate = "${lastMonth.year}-$month-11"
        val seed = DemoSeedData(
            anchorDate = today,
            exercises = listOf(Exercise(id = 11L, name = "Bench Press", muscleGroups = "Chest")),
            sets = listOf(LearnedFixtures.set(userDate, 11L), LearnedFixtures.set(engineDate, 11L)),
            settings = UserSettings(),
            cycle = Cycle(numTypes = 1, isActive = false),
            slots = listOf(engineSlot, userSlot),
            splitExercises = emptyList(),
            workoutDays = listOf(
                WorkoutDay(date = userDate, cycleSlotId = userSlot.id),
                WorkoutDay(date = engineDate, cycleSlotId = engineSlot.id),
            ),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = emptyMap(),
        )
        val base = DemoRepos(seed)

        val offRuns = monthRunsFor(base, hidden = setOf(engineSlot.id))
        val onRuns = monthRunsFor(base, hidden = emptySet())

        assertEquals(listOf(userSlot.id), offRuns, "the hidden engine slot must not reach the rollup")
        assertTrue(onRuns.contains(engineSlot.id), "an empty hidden set keeps the engine slot")
    }

    private suspend fun monthRunsFor(base: AppRepos, hidden: Set<Long>): List<Long> {
        val repos = object : AppRepos by base {
            override val learnedSplits: LearnedSplitsSource = object : LearnedSplitsSource {
                override val model: StateFlow<LearnedModel> =
                    MutableStateFlow(LearnedModel.EMPTY.copy(smartSuggest = hidden.isEmpty(), hiddenSlotIds = hidden))
            }
        }
        val viewModel = MonthViewModel(repos)
        val state = withTimeout(2_000) { viewModel.uiState.first { it.rollup != null } }
        return state.rollup!!.slotRuns.map { it.slotId }
    }
}
