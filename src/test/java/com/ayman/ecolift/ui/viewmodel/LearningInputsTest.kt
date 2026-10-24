package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.DayAttribution
import com.ayman.ecolift.data.splits.LearnedGroup
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.LearnedSplitsSource
import com.ayman.ecolift.web.supabase.SetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 2026-10-08 perf fix: the split-learning programs, the ticket ledger and the clipboard pages read
 * history before the shown day plus which shown-day rows exist and are checked, so a weight or
 * reps edit on the shown day must not rebuild them, and logging a set must.
 */
class LearningInputsTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    private fun bench(id: Long, date: String, setNumber: Int, weight: Int, completed: Boolean) = WorkoutSet(
        id = id, syncId = "s$id", exerciseId = BENCH_ID, date = date, setNumber = setNumber,
        weightLbs = weight, reps = 5, completed = completed,
    )

    private val rows = listOf(
        bench(1, LAST_TIME, 1, 1350, completed = true),
        bench(2, LAST_TIME, 2, 1350, completed = true),
        bench(3, TODAY, 1, 1350, completed = false),
        bench(4, TODAY, 2, 1350, completed = false),
    )

    @Test
    fun learning_key_ignores_shown_day_values_but_not_checks_or_history() {
        val base = learningSetsKey(TODAY, rows)
        val weightEdit = rows.map { if (it.id == 3L) it.copy(weightLbs = 1400, reps = 7) else it }
        assertEquals(base, learningSetsKey(TODAY, weightEdit))

        val logged = rows.map { if (it.id == 3L) it.copy(completed = true) else it }
        assertNotEquals(base, learningSetsKey(TODAY, logged))

        val historyEdit = rows.map { if (it.id == 1L) it.copy(weightLbs = 1400) else it }
        assertNotEquals(base, learningSetsKey(TODAY, historyEdit))
    }

    @Test
    fun clipboard_key_tracks_checked_lift_dates_only() {
        val base = clipboardLiftsKey(rows)
        assertEquals(base, clipboardLiftsKey(rows.map { it.copy(weightLbs = 900, reps = 12) }))
        assertNotEquals(base, clipboardLiftsKey(rows.map { if (it.id == 4L) it.copy(completed = true) else it }))
    }

    @Test
    fun prior_history_memo_rebuilds_only_when_prior_rows_or_context_change() {
        val memo = PriorHistoryMemo<String>()
        var builds = 0
        val model = Any()
        fun read(sets: List<WorkoutSet>, others: List<Any?> = listOf(model), tag: String = "g") =
            memo.get(TODAY, tag, sets, others) { prior -> builds++; "built from ${prior.size}" }

        assertEquals("built from 2", read(rows))
        read(rows)
        read(rows.map { if (it.id == 3L) it.copy(weightLbs = 1400, completed = true) else it })
        assertEquals(1, builds, "Shown-day edits, including a check, reuse the prior-history build")

        read(rows.map { if (it.id == 2L) it.copy(reps = 6) else it })
        assertEquals(2, builds)
        read(rows, others = listOf(Any()))
        assertEquals(3, builds, "A new model rebuilds")
        read(rows, others = listOf(model), tag = "other")
        assertEquals(4, builds)
    }

    /**
     * Starts on an empty shown day, so the empty-day ledger builds, and collects uiState with a
     * proposed group, so the shared alternatives pass builds too: each builder is counted.
     */
    @Test
    fun prior_day_edits_rebuild_and_shown_day_value_edits_do_not() = runVmTest {
        val history = rows.filter { it.date == LAST_TIME }
        val sets = MutableSetRepository(history)
        val viewModel = viewModelOver(sets, seedRows = history)
        viewModel.selectDate(TODAY)
        val programs = backgroundScope.launch { viewModel.clipboardPrograms.collect {} }
        val ledger = backgroundScope.launch { viewModel.emptyDayLedger.collect {} }
        val ui = backgroundScope.launch { viewModel.uiState.collect {} }
        settle()
        val programs0 = viewModel.learnedProgramBuilds
        val ledger0 = viewModel.ledgerBuilds
        val alternatives0 = viewModel.alternativesBuilds
        assertTrue(programs0 > 0 && ledger0 > 0 && alternatives0 > 0,
            "Every builder ran on subscription: programs=$programs0 ledger=$ledger0 alternatives=$alternatives0")

        sets.edit { if (it.id == 1L) it.copy(weightLbs = 1400) else it }
        settle()
        assertTrue(viewModel.learnedProgramBuilds > programs0, "A prior-day edit rebuilds the programs")
        assertTrue(viewModel.ledgerBuilds > ledger0, "A prior-day edit rebuilds the empty-day ledger")

        // The alternatives pass runs inside the uiState projection, which re-derives on shown-day,
        // catalogue and proposal changes (as before the perf fix); its memo then sees the edited
        // history and rebuilds.
        sets.add(bench(3, TODAY, 1, 1350, completed = false))
        settle()
        assertEquals(alternatives0 + 1, viewModel.alternativesBuilds,
            "The first pass after a prior-day edit rebuilds the alternatives")
        val alternativesAfterHistoryEdit = viewModel.alternativesBuilds
        val programs1 = viewModel.learnedProgramBuilds
        val ledger1 = viewModel.ledgerBuilds

        sets.edit { if (it.id == 3L) it.copy(weightLbs = 1400, reps = 7) else it }
        settle()
        assertEquals(programs1, viewModel.learnedProgramBuilds, "A shown-day weight/reps edit must not rebuild the programs")
        assertEquals(ledger1, viewModel.ledgerBuilds, "A shown-day weight/reps edit must not rebuild the ledger")
        assertEquals(alternativesAfterHistoryEdit, viewModel.alternativesBuilds,
            "A shown-day weight/reps edit must not rebuild the alternatives")

        sets.edit { if (it.id == 3L) it.copy(completed = true) else it }
        settle()
        assertEquals(programs1 + 1, viewModel.learnedProgramBuilds, "Logging a set rebuilds the programs")
        programs.cancel()
        ledger.cancel()
        ui.cancel()
    }

    /** Lets the view model's Main-dispatched collectors run (they are not on the test scheduler). */
    private suspend fun settle() = withContext(Dispatchers.Default) { delay(150) }

    private fun viewModelOver(sets: SetRepository, seedRows: List<WorkoutSet> = rows): LogViewModel {
        val seed = DemoSeedData(
            anchorDate = LocalDate.parse(TODAY),
            exercises = listOf(Exercise(id = BENCH_ID, name = "Bench Press", muscleGroups = "CHEST")),
            sets = seedRows,
            settings = UserSettings(),
            cycle = Cycle(numTypes = 1, isActive = false),
            slots = listOf(CycleSlot(id = 1L, name = "Push", orderIndex = 0)),
            splitExercises = emptyList(),
            workoutDays = listOf(WorkoutDay(date = TODAY)),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(seedRows),
        )
        val base = DemoRepos(seed)
        val repos: AppRepos = object : AppRepos by base {
            override val sets: SetRepository = sets
            // One visible group due today, so the Log proposes it and builds its alternatives.
            override val learnedSplits: LearnedSplitsSource = object : LearnedSplitsSource {
                override val model: StateFlow<LearnedModel> = MutableStateFlow(PUSH_MODEL)
            }
        }
        return LogViewModel(repos, isOnline = { false }, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate)
    }

    private inner class MutableSetRepository(initial: List<WorkoutSet>) : SetRepository by DemoRepos(
        DemoSeedData(
            anchorDate = LocalDate.parse(TODAY), exercises = emptyList(), sets = initial, settings = UserSettings(),
            cycle = Cycle(numTypes = 1, isActive = false), slots = emptyList(), splitExercises = emptyList(),
            workoutDays = emptyList(), archives = emptyList(), cardio = emptyList(), pendingReviews = emptyList(),
            usageCounts = emptyMap(),
        ),
    ).sets {
        private val mutableSets = MutableStateFlow(initial)
        override val sets: StateFlow<List<WorkoutSet>> = mutableSets.asStateFlow()
        override fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> = mutableSets.map { SetQueries.forDate(it, date) }
        override suspend fun getAllSets(): List<WorkoutSet> = mutableSets.value
        override suspend fun getSetsForDate(date: String): List<WorkoutSet> = SetQueries.forDate(mutableSets.value, date)
        override suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet> =
            SetQueries.completedHistoryBeforeDate(mutableSets.value, exerciseId, beforeDate)
        fun edit(transform: (WorkoutSet) -> WorkoutSet) { mutableSets.value = mutableSets.value.map(transform) }
        fun add(row: WorkoutSet) { mutableSets.value = mutableSets.value + row }
    }

    private companion object {
        const val BENCH_ID = 7L
        const val LAST_TIME = "2026-09-14"
        const val TODAY = "2026-09-21"

        val PUSH_GROUP = LearnedGroup("push", 1, "Push", 0, listOf(BENCH_ID), listOf(BENCH_ID), emptyList(),
            listOf(LAST_TIME), emptySet(), false, true, 0, false, false, false)
        val PUSH_MODEL = LearnedModel.EMPTY.copy(
            asOf = TODAY, memoryStart = "2026-09-01", groups = listOf(PUSH_GROUP), dueOrder = listOf("push"),
            dueGroupKey = "push", attribution = mapOf(LAST_TIME to DayAttribution(LAST_TIME, "push", emptyList())),
        )
    }
}
