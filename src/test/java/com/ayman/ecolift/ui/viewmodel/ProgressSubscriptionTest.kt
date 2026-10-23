package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseProgressSummary
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.Diagnostics
import com.ayman.ecolift.web.demo.DemoGenAiEngine
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.SetRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressSubscriptionTest {
    @Test
    fun subscriptions_build_real_values_and_stop_immediately_without_a_grace_period() = runVmTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        Diagnostics.reset()
        try {
            val fixture = Fixture()
            val model = fixture.model()
            store.put("progress", model)
            model.setVisible(true)
            repeat(10) {
                fixture.changeUncheckedReps(it)
                runCurrent()
            }
            assertEquals(emptyMap(), Diagnostics.counterSnapshot())
            assertEquals(ProgressProjection.Pending, model.uiState.value)

            val emissions = mutableListOf<ProgressProjection>()
            var collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                model.uiState.collect { emissions += it }
            }
            runCurrent()
            assertEquals(ProgressProjection.Pending, emissions.first())
            val first = assertIs<ProgressProjection.Ready>(model.uiState.value).state
            val bench = first.exercises.single { it.exerciseId == 10L }
            assertEquals("+5 lb", bench.rangeStatLabel)
            assertEquals(1, bench.rangeStatSign)
            assertEquals("at 8 reps", bench.rangeStatContext)
            assertEquals("+3.7%", bench.overview.deltaLabel)
            assertEquals("past month", bench.monthDeltaSubLabel)
            assertEquals(2, bench.sessions)
            assertEquals("140", bench.lastSessionSummary.substringBefore(" "))
            model.selectExercise(10L)
            runCurrent()
            val detail = assertIs<ProgressProjection.Ready>(model.uiState.value).state
            assertEquals(listOf("2026-09-14", "2026-10-14"), detail.chartPoints.map { it.date })
            assertEquals(listOf(1350, 1400), detail.chartPoints.map { it.maxWeight })
            assertEquals(1, Diagnostics.counterSnapshot()["progress.list"])

            collector.cancel()
            runCurrent()
            Diagnostics.reset()
            listOf(0L, 100L, 5_100L).forEachIndexed { index, elapsed ->
                advanceTimeBy(elapsed)
                fixture.changeUncheckedReps(20 + index)
                runCurrent()
                assertEquals(emptyMap(), Diagnostics.counterSnapshot())
            }
            fixture.currentSets.value = fixture.currentSets.value.map {
                if (it.id == 102L) it.copy(weightLbs = 1450) else it
            }
            runCurrent()
            assertEquals(detail, assertIs<ProgressProjection.Ready>(model.uiState.value).state)
            collector = backgroundScope.launch { model.uiState.collect() }
            runCurrent()
            val reopened = assertIs<ProgressProjection.Ready>(model.uiState.value).state
            assertEquals("+10 lb", reopened.exercises.single { it.exerciseId == 10L }.rangeStatLabel)
            assertEquals("+7.4%", reopened.exercises.single { it.exerciseId == 10L }.overview.deltaLabel)
            assertEquals(listOf(1350, 1450), reopened.chartPoints.map { it.maxWeight })
            collector.cancel()
        } finally {
            store.clear()
            runCurrent()
            Diagnostics.reset()
        }
    }

    @Test
    fun hidden_destination_keeps_its_last_frame_and_filters_without_computing() = runVmTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        Diagnostics.reset()
        try {
            val fixture = Fixture()
            val model = fixture.model()
            store.put("progress", model)
            val collector = backgroundScope.launch { model.uiState.collect() }
            model.setVisible(true)
            model.setSearchQuery("Bench")
            model.setTimeframe(TimeframeFilter.THREE_MONTHS)
            runCurrent()
            val lastReady = model.uiState.value
            model.setVisible(false)
            runCurrent()
            Diagnostics.reset()
            repeat(10) {
                fixture.changeUncheckedReps(it)
                runCurrent()
            }
            assertEquals(emptyMap(), Diagnostics.counterSnapshot())
            assertEquals(lastReady, model.uiState.value)
            fixture.currentSets.value = fixture.currentSets.value.map {
                if (it.id == 102L) it.copy(weightLbs = 1450) else it
            }
            model.setVisible(true)
            runCurrent()
            val resumed = assertIs<ProgressProjection.Ready>(model.uiState.value).state
            assertEquals("Bench", resumed.searchQuery)
            assertEquals(TimeframeFilter.THREE_MONTHS, resumed.timeframe)
            assertEquals(listOf("Bench Press"), resumed.visibleExercises.map { it.name })
            assertEquals("+10 lb", resumed.visibleExercises.single().rangeStatLabel)
            collector.cancel()
        } finally {
            store.clear()
            runCurrent()
            Diagnostics.reset()
        }
    }

    @Test
    fun a_report_reads_only_its_lift_and_never_builds_overview_projections() = runVmTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        Diagnostics.reset()
        try {
            val fixture = Fixture()
            val model = fixture.model(ProgressScope.Report(10L))
            store.put("report", model)
            model.setVisible(true)
            val collector = backgroundScope.launch { model.uiState.collect() }
            runCurrent()
            val report = assertIs<ProgressProjection.Ready>(model.uiState.value).state
            assertEquals("Bench Press", report.selectedExerciseName)
            assertEquals(listOf("2026-09-14", "2026-10-14"), report.chartPoints.map { it.date })
            assertEquals(listOf(1350, 1400), report.chartPoints.map { it.maxWeight })
            assertEquals(listOf(10L, 10L), fixture.observedExerciseIds)
            assertEquals(mapOf("progress.detail" to 1), Diagnostics.counterSnapshot())
            collector.cancel()
        } finally {
            store.clear()
            runCurrent()
            Diagnostics.reset()
        }
    }

    private class Fixture {
        private val exercises = listOf(
            Exercise(10L, "Bench Press", "CHEST"),
            Exercise(20L, "Row", "BACK"),
            Exercise(30L, "Pull-up", "BACK", isBodyweight = true),
        )
        val currentSets = MutableStateFlow(listOf(
            WorkoutSet(101L, 10L, "2026-09-14", 1, 1350, 8, completed = true),
            WorkoutSet(102L, 10L, "2026-10-14", 1, 1400, 8, completed = true),
            WorkoutSet(103L, 10L, "2026-10-14", 2, 9990, 20, completed = false),
            WorkoutSet(104L, 10L, "2026-10-14", 3, 9990, 20, completed = true, isWarmup = true),
            WorkoutSet(201L, 20L, "2026-10-14", 1, 1000, 10, completed = true),
            WorkoutSet(301L, 30L, "2026-09-14", 1, 0, 6, completed = true, isBodyweight = true),
            WorkoutSet(302L, 30L, "2026-10-14", 1, 0, 8, completed = true, isBodyweight = true),
        ))
        private val base = DemoRepos(DemoSeedData(
            anchorDate = LocalDate(2026, 10, 14), exercises = exercises, sets = currentSets.value,
            settings = UserSettings(), cycle = Cycle(numTypes = 1, isActive = false),
            slots = emptyList(), splitExercises = emptyList(),
            workoutDays = listOf(WorkoutDay(date = "2026-09-14"), WorkoutDay(date = "2026-10-14")),
            archives = emptyList(), cardio = emptyList(), pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(currentSets.value),
        ))
        val observedExerciseIds = mutableListOf<Long>()
        private val sets = object : SetRepository by base.sets {
            override val sets = currentSets
            override fun observeExerciseProgressSummaries() = currentSets.map { rows ->
                rows.filter { it.completed && !it.isWarmup }.groupBy { it.exerciseId }.map { (id, history) ->
                    val exercise = exercises.single { it.id == id }
                    ExerciseProgressSummary(id, exercise.name, exercise.isBodyweight,
                        history.map { it.date }.distinct().size, history.maxOf { it.date })
                }
            }
            override suspend fun getSetsSince(exerciseId: Long, sinceDate: String) =
                currentSets.value.filter { it.exerciseId == exerciseId && it.date >= sinceDate && it.completed && !it.isWarmup }
            override fun observeSetsSince(exerciseId: Long, sinceDate: String) = flow {
                observedExerciseIds += exerciseId
                currentSets.collect { rows ->
                    emit(rows.filter { it.exerciseId == exerciseId && it.date >= sinceDate && it.completed && !it.isWarmup })
                }
            }
        }
        private val repos = object : AppRepos by base { override val sets = this@Fixture.sets }
        fun model(scope: ProgressScope = ProgressScope.Overview) =
            ProgressViewModel(
                repos,
                DemoGenAiEngine(),
                scope = scope,
                today = { LocalDate(2026, 10, 14) },
                derivationDispatcher = Dispatchers.Main,
            )
        fun changeUncheckedReps(reps: Int) {
            currentSets.value = currentSets.value.map { if (it.id == 103L) it.copy(reps = reps) else it }
        }
    }
}
