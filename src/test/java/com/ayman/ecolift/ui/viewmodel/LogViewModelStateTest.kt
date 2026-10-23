package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.ExerciseSwapRepository
import com.ayman.ecolift.web.supabase.ExerciseSwapRequest
import com.ayman.ecolift.web.supabase.ExerciseSwapResult
import com.ayman.ecolift.web.supabase.NoExerciseSwapRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.util.nowMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogViewModelStateTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun exercise_search_never_filters_or_reorders_the_current_workout() {
        val workout = listOf(
            set(id = 1L, exerciseId = 10L, setNumber = 1),
            set(id = 2L, exerciseId = 20L, setNumber = 1),
            set(id = 3L, exerciseId = 10L, setNumber = 2),
        )

        listOf("", "pu", "bench", "no exercise can match this").forEach { query ->
            assertEquals(
                workout,
                workoutSetsForLog(
                    currentSets = workout,
                    exerciseSearchQuery = query,
                ),
                "The add-exercise query must not change active workout rows for query '$query'",
            )
        }
    }

    @Test
    fun today_stays_expanded_even_when_every_set_is_checked() {
        val today = "2026-08-23"
        val completed = listOf(set(id = 1L, completed = true))

        assertEquals(
            WorkoutCardDisplayMode.EXPANDED,
            fallbackLogCardDisplayMode(date = today, today = today, sets = completed),
        )
    }

    @Test
    fun only_a_nonempty_fully_checked_historical_card_falls_back_to_compact() {
        val today = "2026-08-23"
        val yesterday = "2026-08-22"
        val tomorrow = "2026-08-24"
        val completed = listOf(
            set(id = 1L, setNumber = 1, completed = true),
            set(id = 2L, setNumber = 2, completed = true),
        )
        val mixed = completed.map { row ->
            if (row.id == 2L) row.copy(completed = false) else row
        }

        assertEquals(
            WorkoutCardDisplayMode.COMPACT,
            fallbackLogCardDisplayMode(date = yesterday, today = today, sets = completed),
        )
        assertEquals(
            WorkoutCardDisplayMode.EXPANDED,
            fallbackLogCardDisplayMode(date = yesterday, today = today, sets = mixed),
        )
        assertEquals(
            WorkoutCardDisplayMode.EXPANDED,
            fallbackLogCardDisplayMode(date = yesterday, today = today, sets = emptyList()),
        )
        assertEquals(
            WorkoutCardDisplayMode.EXPANDED,
            fallbackLogCardDisplayMode(date = tomorrow, today = today, sets = completed),
        )
    }

    @Test
    fun an_echoed_row_is_recognised_by_sync_id_even_after_its_temp_id_was_remapped() {
        val echoed = set(id = 704L, setNumber = 2).copy(syncId = "sync-2")
        val returned = set(id = -1L, setNumber = 2).copy(syncId = "sync-2")

        assertEquals(
            listOf(echoed),
            appendSessionSets(current = listOf(echoed), added = listOf(returned)),
        )
    }

    @Test
    fun an_echoed_row_is_recognised_by_id_when_it_carries_no_sync_id() {
        val echoed = set(id = -1L, setNumber = 2)
        val returned = set(id = -1L, setNumber = 2)

        assertEquals(
            listOf(echoed),
            appendSessionSets(current = listOf(echoed), added = listOf(returned)),
        )
    }

    @Test
    fun a_row_with_no_id_and_no_sync_id_falls_back_to_its_logical_address() {
        val anonymous = set(id = 0L, setNumber = 2)
        val identified = set(id = 55L, setNumber = 3).copy(syncId = "sync-3")

        assertEquals(
            listOf(anonymous),
            appendSessionSets(current = listOf(anonymous), added = listOf(anonymous.copy())),
            "An unidentifiable repeat of the same logical set must not be appended twice",
        )
        assertEquals(
            listOf(anonymous, identified),
            appendSessionSets(current = listOf(anonymous), added = listOf(identified)),
            "A row at a different logical address is a genuinely new set",
        )
        assertEquals(
            listOf(identified, anonymous),
            appendSessionSets(current = listOf(identified), added = listOf(anonymous)),
            "An identified row must never absorb an anonymous one",
        )
    }

    @Test
    fun rows_the_session_has_not_seen_are_appended_in_repository_order() {
        val existing = set(id = 1L, setNumber = 1).copy(syncId = "sync-1")
        val added = listOf(
            set(id = -1L, setNumber = 2).copy(syncId = "sync-2"),
            set(id = -2L, setNumber = 3).copy(syncId = "sync-3"),
        )

        assertEquals(listOf(existing) + added, appendSessionSets(listOf(existing), added))
        assertEquals(listOf(existing), appendSessionSets(listOf(existing), emptyList()))
    }

    @Test
    fun add_set_shows_one_row_when_the_repository_echoes_before_returning() = runVmTest {
        val harness = Harness()
        harness.showDate()

        harness.viewModel.addSet(EXERCISE_A)
        harness.awaitSets(DATE, expected = 2)

        assertTrue(harness.setRepository.echoRaced, "The echo-before-return race did not happen")
        assertEquals(2, harness.viewModel.sessionSetSnapshot().sets.size)
        assertEquals(2, harness.workoutSetCount())
    }

    @Test
    fun add_set_shows_the_new_row_once_when_the_repository_echo_arrives_late() = runVmTest {
        val harness = Harness(echoBeforeReturn = false)
        harness.showDate()

        harness.viewModel.addSet(EXERCISE_A)
        assertEquals(
            2,
            harness.viewModel.sessionSetSnapshot().sets.size,
            "A row the session has not seen yet must still appear the moment it is added",
        )

        harness.setRepository.flushLateEcho()
        harness.awaitSets(DATE, expected = 2)
        assertEquals(2, harness.viewModel.sessionSetSnapshot().sets.size)
        assertEquals(2, harness.workoutSetCount())
    }

    @Test
    fun adding_an_exercise_shows_one_card_when_the_repository_echoes_before_returning() = runVmTest {
        val harness = Harness()
        harness.showDate()

        harness.viewModel.useSuggestion(EXERCISE_B)
        harness.awaitSets(DATE, expected = 2)

        assertTrue(harness.setRepository.echoRaced, "The echo-before-return race did not happen")
        assertEquals(1, harness.setRepository.sessionAddsFor(EXERCISE_B))
        assertEquals(2, harness.viewModel.sessionSetSnapshot().sets.size)
        assertEquals(1, harness.viewModel.sessionSetSnapshot().sets.count { it.exerciseId == EXERCISE_B })
    }

    @Test
    fun an_add_abandoned_by_typing_writes_once_in_total_not_once_per_attempt() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val release = harness.exerciseRepository.pauseNextLookup()

        harness.viewModel.useSuggestion(EXERCISE_B)
        withTimeout(TIMEOUT) { harness.exerciseRepository.entered.await() }
        // Typing hands the add-exercise slot to whatever the user does next. The parked add must
        // then write nothing, or the retap below logs the same exercise a second time.
        harness.viewModel.updateExerciseInput("Exercise B")
        harness.viewModel.useSuggestion(EXERCISE_B)
        release.complete(Unit)
        harness.awaitSets(DATE, expected = 2)

        assertEquals(1, harness.setRepository.sessionAddsFor(EXERCISE_B))
        assertEquals(1, harness.viewModel.sessionSetSnapshot().sets.count { it.exerciseId == EXERCISE_B })
        assertEquals(2, harness.workoutSetCount())
    }

    @Test
    fun an_edit_landing_during_a_day_handover_never_relabels_the_old_rows() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        val release = harness.parkSessionCollectorOn(DATE)
        assertEquals(DATE, harness.viewModel.sessionSetSnapshot().date)

        // The collector is mid-refresh, so it cannot observe the new date yet: the session still
        // holds DATE's rows while the header has already moved to OTHER_DATE.
        harness.viewModel.selectDate(OTHER_DATE)
        harness.viewModel.adjustWeight(SET_ID, 5)

        val during = harness.viewModel.sessionSetSnapshot()
        assertTrue(
            during.sets.all { it.date == during.date },
            "Every published row must belong to the day it is published under",
        )
        assertEquals(DATE, during.date, "Yesterday's rows must not be republished under a new date")
        assertEquals(
            0,
            harness.setRepository.historyLookupsFor(OTHER_DATE),
            "The new day's top sets and PBs must not be computed from the previous day's rows",
        )

        release.complete(Unit)
        withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { it.currentDate == OTHER_DATE && it.exercises.isEmpty() }
        }
        // The step commits once the tape has been quiet for STEP_COMMIT_DELAY_MS.
        withTimeout(TIMEOUT) {
            harness.setRepository.sets.first { sets -> sets.any { it.date == DATE && it.weightLbs == 105 } }
        }
        assertEquals(105, harness.setRepository.forDate(DATE).single().weightLbs)
    }

    @Test
    fun an_add_during_a_day_handover_lands_on_the_new_day_exactly_once() = runVmTest {
        val harness = Harness()
        val release = harness.parkSessionCollectorOn(DATE)

        harness.viewModel.selectDate(OTHER_DATE)
        harness.viewModel.addSet(EXERCISE_A)

        val during = harness.viewModel.sessionSetSnapshot()
        assertTrue(
            during.sets.all { it.date == during.date },
            "An append aimed at the new day must not drag the old day's rows along with it",
        )

        release.complete(Unit)
        harness.awaitSets(OTHER_DATE, expected = 1)
        val snapshot = harness.viewModel.sessionSetSnapshot()
        assertEquals(OTHER_DATE, snapshot.date)
        assertEquals(1, snapshot.sets.size)
        assertEquals(1, harness.setRepository.forDate(OTHER_DATE).size)
        assertEquals(1, harness.setRepository.forDate(DATE).size, "The old day must be untouched")
    }

    @Test
    fun keypad_edit_uses_the_target_rows_stored_values_not_its_pending_hint() = runVmTest {
        val harness = Harness(currentSets = threeSetCurrent(weightLbs = 700, reps = 7))
        harness.showDate()

        // Set 1's direct change creates a visible but unaccepted 100 lb / 7 rep hint for set 2.
        harness.viewModel.updateWeight(SET_ID, "100")
        val hintedSecond = harness.awaitUiSet(SET_TWO_ID) {
            it.weightLbs == 700 && it.reps == 7 && it.suggestedWeightLbs == 1_000
        }
        assertEquals(700, hintedSecond.weightLbs)
        assertEquals(1_000, hintedSecond.suggestedWeightLbs)

        // Entering reps is an edit of reps only. It must not silently accept the pending weight.
        harness.viewModel.updateReps(SET_TWO_ID, "8")
        val persistedSecond = harness.awaitPersistedSet(SET_TWO_ID) { it.reps == 8 }
        val storedSecond = harness.viewModel.sessionSetSnapshot().sets.single { it.id == SET_TWO_ID }

        assertEquals(700, persistedSecond.weightLbs)
        assertEquals(8, persistedSecond.reps)
        assertEquals(700, storedSecond.weightLbs)
        assertEquals(8, storedSecond.reps)
        assertNull(
            harness.awaitUiSet(SET_TWO_ID) { it.reps == 8 }.suggestedWeightLbs,
            "The edited row's old proposal must be dismissed after keypad input",
        )
    }

    @Test
    fun logging_a_row_with_a_try_hint_persists_its_stored_value_not_the_hint() = runVmTest {
        val harness = Harness(currentSets = threeSetCurrent(weightLbs = null, reps = 7))
        harness.showDate()

        harness.viewModel.updateReps(SET_ID, "10")
        val hintedSecond = harness.awaitUiSet(SET_TWO_ID) {
            it.reps == 7 && it.suggestedReps == 10
        }
        assertEquals(7, hintedSecond.reps)
        assertEquals(10, hintedSecond.suggestedReps)

        harness.viewModel.toggleCompleted(SET_TWO_ID)
        val persistedSecond = harness.awaitPersistedSet(SET_TWO_ID) {
            it.reps == 7 && it.completed
        }
        val stored = harness.viewModel.sessionSetSnapshot().sets.sortedBy(WorkoutSet::setNumber)

        assertEquals(7, persistedSecond.reps)
        assertTrue(persistedSecond.completed)
        assertEquals(listOf(10, 7, 7), stored.map(WorkoutSet::reps))
        assertEquals(false, stored.single { it.id == SET_THREE_ID }.completed)
        assertNull(
            harness.awaitUiSet(SET_TWO_ID) { it.completed }.suggestedReps,
            "Logging dismisses the TRY hint instead of silently accepting it",
        )
    }

    @Test
    fun a_running_rest_names_the_lift_it_belongs_to_and_forgets_it_when_stopped() = runVmTest {
        val harness = Harness(currentSets = threeSetCurrent(weightLbs = 700, reps = 8))
        harness.showDate()

        harness.viewModel.toggleCompleted(SET_ID)
        val resting = withTimeout(TIMEOUT) {
            harness.viewModel.restStopwatch.first { it != null }
        }
        assertEquals(EXERCISE_A, resting?.exerciseId, "The rest tab takes its colour from this lift")

        harness.viewModel.cancelRestTimer()
        val stopped = withTimeout(TIMEOUT) {
            harness.viewModel.restStopwatch.first { it == null }
        }
        assertNull(stopped)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rest_ticks_emit_only_on_the_stopwatch_flow_and_stopping_clears_it() = runVmTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val harness = Harness(
                currentSets = threeSetCurrent(weightLbs = 700, reps = 8),
                restNowMillis = { testScheduler.currentTime },
            )
            store.put("rest-tick-test", harness.viewModel)
            val uiEmissions = mutableListOf<LogUiState>()
            val restEmissions = mutableListOf<RestStopwatch?>()
            val uiCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                harness.viewModel.uiState.collect { uiEmissions += it }
            }
            val restCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                harness.viewModel.restStopwatch.collect { restEmissions += it }
            }
            harness.showDate()
            runCurrent()
            assertEquals(listOf<RestStopwatch?>(null), restEmissions)

            harness.viewModel.toggleCompleted(SET_ID)
            runCurrent()
            assertEquals(RestStopwatch(EXERCISE_A, 0), restEmissions.last())
            assertTrue(uiEmissions.last().exercises.flatMap { it.sets }.single { it.id == SET_ID }.completed)
            val settledUiEmissionCount = uiEmissions.size

            repeat(3) { tick ->
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals(RestStopwatch(EXERCISE_A, tick + 1), restEmissions.last())
                assertEquals(
                    settledUiEmissionCount,
                    uiEmissions.size,
                    "A pure rest tick must not rebuild and emit the day's UI state",
                )
            }
            assertEquals(listOf(0, 1, 2, 3), restEmissions.filterNotNull().map { it.elapsedSeconds })

            harness.viewModel.cancelRestTimer()
            runCurrent()
            assertNull(restEmissions.last())
            assertNull(harness.viewModel.restStopwatch.value)
            val stoppedEmissionCount = restEmissions.size
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(stoppedEmissionCount, restEmissions.size, "Stopping cancels further ticks")
            assertEquals(settledUiEmissionCount, uiEmissions.size)
            uiCollector.cancel()
            restCollector.cancel()
        } finally {
            store.clear()
            runCurrent()
        }
    }

    private fun set(
        id: Long,
        exerciseId: Long = 10L,
        setNumber: Int = 1,
        completed: Boolean = false,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = "2026-08-23",
        setNumber = setNumber,
        completed = completed,
    )

    @Test
    fun aDayWithOnlyUncheckedSetsIsNotAWorkedDayUntilASetIsChecked() = runVmTest {
        val harness = Harness()
        val worked = withTimeout(TIMEOUT) { harness.viewModel.workedDays.first { it.isNotEmpty() } }
        assertEquals(setOf(LocalDate.parse(HISTORY_DATE)), worked, "the current day holds only an unchecked set")
        harness.setRepository.update(CURRENT.single().copy(completed = true))
        withTimeout(TIMEOUT) { harness.viewModel.workedDays.first { LocalDate.parse(DATE) in it } }
    }

    @Test
    fun cancellingSwapDoesNotChangeTheWorkoutOrSendARequest() = runVmTest {
        val swaps = PausableSwapRepository()
        val harness = Harness(swapRepository = swaps)
        harness.showDate()
        val before = harness.viewModel.sessionSetSnapshot()
        harness.viewModel.openExerciseSwap(EXERCISE_A)
        assertEquals(EXERCISE_A, harness.viewModel.exerciseSwap.value?.sourceId)
        harness.viewModel.changeSwapQuery("Exercise B")
        harness.viewModel.dismissExerciseSwap()
        assertNull(harness.viewModel.exerciseSwap.value)
        assertEquals(before, harness.viewModel.sessionSetSnapshot())
        assertTrue(swaps.requests.isEmpty())
    }

    @Test
    fun doubleTapSendsOnceAndADateChangeCannotFocusTheOldWorkout() = runVmTest {
        val swaps = PausableSwapRepository()
        val harness = Harness(swapRepository = swaps)
        harness.showDate()
        harness.viewModel.openExerciseSwap(EXERCISE_A)
        var focused = false
        harness.viewModel.swapExercise(EXERCISE_B) { _, _ -> focused = true }
        withTimeout(TIMEOUT) { swaps.entered.await() }
        harness.viewModel.swapExercise(EXERCISE_B) { _, _ -> focused = true }
        assertEquals(1, swaps.requests.size)
        harness.viewModel.selectDate("2026-08-24")
        swaps.release.complete(ExerciseSwapResult(true, "APPLIED", targetId = EXERCISE_B))
        withTimeout(TIMEOUT) { harness.viewModel.exerciseSwap.first { it == null } }
        assertTrue(!focused)
        assertNull(harness.viewModel.exerciseSwapUndo.value)
    }

    @Test
    fun retryReusesTheRequestIdentityAndAlreadyUndoneDoesNotOfferUndoAgain() = runVmTest {
        val swaps = PausableSwapRepository()
        val harness = Harness(swapRepository = swaps)
        harness.showDate()
        harness.viewModel.openExerciseSwap(EXERCISE_A)
        harness.viewModel.swapExercise(EXERCISE_B) { _, _ -> error("Failed swap must not focus") }
        withTimeout(TIMEOUT) { swaps.entered.await() }
        swaps.release.complete(ExerciseSwapResult(false, "PENDING_SYNC"))
        withTimeout(TIMEOUT) { harness.viewModel.exerciseSwap.first { it?.error != null } }
        swaps.release = CompletableDeferred()
        val focused = CompletableDeferred<Long>()
        harness.viewModel.swapExercise(EXERCISE_B) { id, _ -> focused.complete(id) }
        swaps.release.complete(ExerciseSwapResult(true, "UNDONE", targetId = EXERCISE_B))
        assertEquals(EXERCISE_A, withTimeout(TIMEOUT) { focused.await() })
        assertEquals(2, swaps.requests.size)
        assertEquals(swaps.requests.first().requestId, swaps.requests.last().requestId)
        assertNull(harness.viewModel.exerciseSwapUndo.value)
    }

    private class PausableSwapRepository : ExerciseSwapRepository by NoExerciseSwapRepository {
        val entered = CompletableDeferred<Unit>()
        var release = CompletableDeferred<ExerciseSwapResult>()
        val requests = mutableListOf<ExerciseSwapRequest>()
        override suspend fun swap(request: ExerciseSwapRequest): ExerciseSwapResult {
            requests += request
            entered.complete(Unit)
            return release.await()
        }
    }

    private class Harness(
        echoBeforeReturn: Boolean = true,
        currentSets: List<WorkoutSet> = CURRENT,
        swapRepository: ExerciseSwapRepository = NoExerciseSwapRepository,
        stepCommitContext: CoroutineContext = EmptyCoroutineContext,
        restNowMillis: () -> Long = ::nowMillis,
    ) {
        private val seededSets = HISTORY + currentSets
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(DATE),
            exercises = listOf(
                Exercise(EXERCISE_A, "Exercise A", "CHEST"),
                Exercise(EXERCISE_B, "Exercise B", "BACK"),
            ),
            sets = seededSets,
            settings = UserSettings(),
            cycle = Cycle(numTypes = 1, isActive = false),
            slots = listOf(CycleSlot(1L, "A", 0)),
            splitExercises = emptyList(),
            workoutDays = listOf(WorkoutDay(date = DATE)),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(seededSets),
        )
        private val base = DemoRepos(seed)
        val setRepository = EchoingSetRepository(base.sets, seededSets, echoBeforeReturn)
        val exerciseRepository = PausableExerciseRepository(base.exercises)
        private val repos: AppRepos = object : AppRepos by base {
            override val sets: SetRepository = setRepository
            override val exercises: ExerciseRepository = exerciseRepository
            override val exerciseSwaps: ExerciseSwapRepository = swapRepository
        }

        // Offline keeps the duplicate-exercise prompt out of the way; these tests are about the
        // write path, not about resolution.
        val viewModel = LogViewModel(
            repos,
            isOnline = { false },
            stepCommitContext = stepCommitContext,
            restNowMillis = restNowMillis,
        )

        init {
            setRepository.hasEchoed = { row ->
                viewModel.sessionSetSnapshot().sets.any { it.syncId == row.syncId }
            }
        }

        fun workoutSetCount(): Int = viewModel.uiState.value.exercises.sumOf { it.sets.size }

        suspend fun showDate(date: String = DATE) {
            viewModel.selectDate(date)
            awaitSets(date, setRepository.forDate(date).size)
        }

        suspend fun awaitSets(date: String, expected: Int) {
            withTimeout(TIMEOUT) {
                viewModel.uiState.first { state ->
                    state.currentDate == date && state.exercises.sumOf { it.sets.size } >= expected
                }
            }
        }

        suspend fun awaitUiSet(
            setId: Long,
            predicate: (LogSetUi) -> Boolean,
        ): LogSetUi = withTimeout(TIMEOUT) {
            viewModel.uiState
                .map { state -> state.exercises.flatMap(LogExerciseUi::sets).firstOrNull { it.id == setId } }
                .first { set -> set != null && predicate(set) }
        }!!

        suspend fun awaitPersistedSet(
            setId: Long,
            predicate: (WorkoutSet) -> Boolean,
        ): WorkoutSet = withTimeout(TIMEOUT) {
            setRepository.sets
                .map { sets -> sets.firstOrNull { it.id == setId } }
                .first { set -> set != null && predicate(set) }
        }!!

        /**
         * Shows [date] and leaves the session collector suspended inside its history refresh for
         * it. Until the returned latch is completed the collector cannot see any later date — the
         * handover window a real history refresh holds open. Returns the latch that releases it.
         */
        suspend fun parkSessionCollectorOn(date: String): CompletableDeferred<Unit> {
            val release = setRepository.pauseNextHistoryLookup()
            viewModel.selectDate(date)
            withTimeout(TIMEOUT) { setRepository.historyEntered.await() }
            return release
        }
    }

    /**
     * Mirrors the real repository's insert: the row is published into the observed StateFlow
     * before the add call returns, so the ViewModel's own collector can echo it back first.
     */
    private class EchoingSetRepository(
        private val delegate: SetRepository,
        initialSets: List<WorkoutSet>,
        private val echoBeforeReturn: Boolean,
    ) : SetRepository by delegate {
        private val mutableSets = MutableStateFlow(initialSets.toList())
        override val sets: StateFlow<List<WorkoutSet>> = mutableSets.asStateFlow()
        private var nextTempId = -1L
        private var nextSyncId = 0
        private val sessionAdds = mutableListOf<Long>()

        /** Set by the harness once the ViewModel exists; reports whether it observed the row yet. */
        var hasEchoed: (WorkoutSet) -> Boolean = { false }

        /** True once an add returned a row the ViewModel had already collected — the real race. */
        var echoRaced = false
            private set

        fun forDate(date: String): List<WorkoutSet> = SetQueries.forDate(mutableSets.value, date)

        fun sessionAddsFor(exerciseId: Long): Int = sessionAdds.count { it == exerciseId }

        override suspend fun getAllSets(): List<WorkoutSet> = mutableSets.value
        override fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> =
            mutableSets.map { SetQueries.forDate(it, date) }

        override suspend fun getSetsForDate(date: String): List<WorkoutSet> = forDate(date)
        override suspend fun getSetsByDate(date: String): List<WorkoutSet> = forDate(date)
        override suspend fun getSetsForDates(dates: List<String>): List<WorkoutSet> =
            mutableSets.value.filter { it.date in dates.toSet() }

        override suspend fun getCompletedSetsForDates(dates: List<String>): List<WorkoutSet> =
            mutableSets.value.filter { it.completed && it.date in dates.toSet() }

        override suspend fun getById(id: Long): WorkoutSet? = mutableSets.value.firstOrNull { it.id == id }

        val historyEntered = CompletableDeferred<Unit>()
        private var historyGate: CompletableDeferred<Unit>? = null
        private val historyLookups = mutableListOf<String>()

        fun pauseNextHistoryLookup(): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also { historyGate = it }

        fun historyLookupsFor(date: String): Int = historyLookups.count { it == date }

        override suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet> {
            historyLookups += beforeDate
            historyGate?.let { pending ->
                historyGate = null
                historyEntered.complete(Unit)
                pending.await()
            }
            return SetQueries.completedHistoryBeforeDate(mutableSets.value, exerciseId, beforeDate).take(100)
        }

        override suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String): Int? =
            SetQueries.maxCompletedWeightBefore(mutableSets.value, exerciseId, beforeDate)

        override suspend fun update(set: WorkoutSet): LocalCommit<WorkoutSet> {
            mutableSets.value = mutableSets.value.map { if (it.id == set.id) set else it }
            return LocalCommit.Committed(set)
        }

        override suspend fun updateSet(set: WorkoutSet, observed: WorkoutSet?): LocalCommit<WorkoutSet> {
            return update(set)
        }

        override suspend fun delete(id: Long) {
            mutableSets.value = mutableSets.value.filterNot { it.id == id }
        }

        override suspend fun deleteSet(id: Long) = delete(id)

        override suspend fun addSet(date: String, exerciseId: Long, copyFrom: WorkoutSet?): LocalCommit<WorkoutSet> {
            val current = SetQueries.forDateAndExercise(mutableSets.value, date, exerciseId)
            val template = copyFrom ?: current.lastOrNull()
            return LocalCommit.Committed(publish(
                WorkoutSet(
                    exerciseId = exerciseId,
                    date = date,
                    setNumber = SetQueries.nextSetNumber(current),
                    weightLbs = template?.weightLbs,
                    reps = template?.reps,
                ),
            ))
        }

        override suspend fun addExerciseSession(
            date: String,
            exerciseId: Long,
            isBodyweightDefault: Boolean,
        ): LocalCommit<List<WorkoutSet>> {
            sessionAdds += exerciseId
            val current = SetQueries.forDateAndExercise(mutableSets.value, date, exerciseId)
            return LocalCommit.Committed(listOf(
                publish(
                    WorkoutSet(
                        exerciseId = exerciseId,
                        date = date,
                        setNumber = SetQueries.nextSetNumber(current),
                        isBodyweight = isBodyweightDefault,
                    ),
                ),
            ))
        }

        private val lateEcho = mutableListOf<WorkoutSet>()

        /** Publishes rows an [echoBeforeReturn]`= false` repository held back until after the add. */
        fun flushLateEcho() {
            mutableSets.value = mutableSets.value + lateEcho
            lateEcho.clear()
        }

        private suspend fun publish(set: WorkoutSet): WorkoutSet {
            val optimistic = set.copy(id = nextTempId--, syncId = "sync-${++nextSyncId}")
            if (!echoBeforeReturn) {
                lateEcho += optimistic
                return optimistic
            }
            mutableSets.value = mutableSets.value + optimistic
            var spins = 0
            while (spins < ECHO_SPINS && !hasEchoed(optimistic)) {
                spins++
                yield()
            }
            if (hasEchoed(optimistic)) echoRaced = true
            return optimistic
        }
    }

    /** Lets a test park an add inside the ViewModel, mid-write, to open the concurrency window. */
    private class PausableExerciseRepository(
        private val delegate: ExerciseRepository,
    ) : ExerciseRepository by delegate {
        val entered = CompletableDeferred<Unit>()
        private var gate: CompletableDeferred<Unit>? = null

        fun pauseNextLookup(): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also { gate = it }

        override suspend fun getById(id: Long): Exercise? {
            gate?.let { pending ->
                gate = null
                entered.complete(Unit)
                pending.await()
            }
            return delegate.getById(id)
        }
    }

    private companion object {
        const val DATE = "2026-08-14"
        const val OTHER_DATE = "2026-08-15"
        const val HISTORY_DATE = "2026-08-01"
        const val EXERCISE_A = 10L
        const val EXERCISE_B = 20L
        const val SET_ID = 101L
        const val SET_TWO_ID = 102L
        const val SET_THREE_ID = 103L
        const val TIMEOUT = 2_000L
        const val ECHO_SPINS = 40

        val HISTORY = listOf(
            WorkoutSet(1L, EXERCISE_A, HISTORY_DATE, 1, 100, 8, completed = true),
            WorkoutSet(2L, EXERCISE_B, HISTORY_DATE, 1, 200, 5, completed = true),
        )
        val CURRENT = listOf(WorkoutSet(SET_ID, EXERCISE_A, DATE, 1, 100, 8))

        fun threeSetCurrent(weightLbs: Int?, reps: Int): List<WorkoutSet> = listOf(
            WorkoutSet(SET_ID, EXERCISE_A, DATE, 1, weightLbs, reps, syncId = "set-1"),
            WorkoutSet(SET_TWO_ID, EXERCISE_A, DATE, 2, weightLbs, reps, syncId = "set-2"),
            WorkoutSet(SET_THREE_ID, EXERCISE_A, DATE, 3, weightLbs, reps, syncId = "set-3"),
        )
    }
}
