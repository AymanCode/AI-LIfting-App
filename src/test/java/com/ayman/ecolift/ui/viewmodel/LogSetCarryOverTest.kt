package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ayman, 2026-09-21: "when you do a set for a specific weight and rep, that doesn't carry over to
 * the remaining sets". Last session 135 × 8/5/3; today set 1 is logged at 140 × 7, so sets 2 and 3
 * should open at 140 × 4 and 140 × 2 — the same weight, the same rep drop-off as last time.
 *
 * The carry-over is a hint while set 1 is still being scrubbed (a scrub never makes another row
 * look edited, see [PullUpSetEditIsolationTest]); tapping Log on set 1 is what writes it into the
 * sets that are still open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogSetCarryOverTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun logging_set_one_at_a_new_weight_and_reps_reseeds_the_open_sets_by_last_sessions_shape() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustWeight(SET_ONE_ID, delta = 50)
        harness.viewModel.adjustReps(SET_ONE_ID, delta = -1)
        // The rows and the hint map reach the UI as separate emissions, and the repository writes
        // run in launched coroutines, so wait for each before asserting on it.
        val scrubbed = harness.awaitUi(listOf(1400 to 7, 1350 to 5, 1350 to 3)) { rows ->
            rows[1].suggestedReps == 4 && rows[2].suggestedReps == 2
        }
        assertEquals(1400, scrubbed[1].suggestedWeightLbs, "Set 2 carries the hint while set 1 is being scrubbed")
        harness.awaitPersisted(listOf(1400 to 7, 1350 to 5, 1350 to 3))
        assertEquals(
            listOf(SET_ONE_SYNC_ID),
            harness.setRepository.writes.map(WorkoutSet::syncId),
            "Scrubbing set 1 writes set 1 only, once the steps settle",
        )

        harness.viewModel.toggleCompleted(SET_ONE_ID)
        runCurrent()

        val logged = harness.awaitUi(listOf(1400 to 7, 1400 to 4, 1400 to 2)) { rows ->
            rows.map(LogSetUi::completed) == listOf(true, false, false) &&
                rows[1].suggestedReps == null &&
                rows[2].suggestedReps == null
        }
        assertNull(logged[1].suggestedWeightLbs, "Once written the carry-over is no longer a pending hint")
        harness.awaitPersisted(listOf(1400 to 7, 1400 to 4, 1400 to 2))
    }

    @Test
    fun a_later_set_edited_by_hand_keeps_its_own_numbers_when_set_one_is_logged() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustWeight(SET_ONE_ID, delta = 50)
        harness.awaitUi(listOf(1400 to 8, 1350 to 5, 1350 to 3))
        harness.viewModel.adjustReps(SET_THREE_ID, delta = 2)
        harness.awaitUi(listOf(1400 to 8, 1350 to 5, 1350 to 5))

        harness.viewModel.toggleCompleted(SET_ONE_ID)
        runCurrent()

        harness.awaitUi(listOf(1400 to 8, 1400 to 5, 1350 to 5))
        harness.awaitPersisted(listOf(1400 to 8, 1400 to 5, 1350 to 5))
    }

    @Test
    fun clearing_and_typing_a_half_pound_carries_the_value_through_all_three_sets() = runVmTest {
        val harness = Harness(weight = 650)
        harness.showBench()

        for (input in listOf("", "6", "65", "65.", "65.5")) {
            harness.viewModel.updateWeight(SET_ONE_ID, input)
            runCurrent()
        }
        harness.awaitUi(listOf(655 to 8, 650 to 5, 650 to 3)) { rows ->
            rows[1].suggestedWeightLbs == 655 && rows[2].suggestedWeightLbs == 655
        }
        harness.viewModel.toggleCompleted(SET_ONE_ID)
        harness.awaitUi(listOf(655 to 8, 655 to 5, 655 to 3))
        harness.awaitPersisted(listOf(655 to 8, 655 to 5, 655 to 3))

        for (input in listOf("65.", "65", "6", "", "6", "65", "65.", "65.5")) {
            harness.viewModel.updateWeight(SET_TWO_ID, input)
            runCurrent()
        }
        harness.viewModel.toggleCompleted(SET_TWO_ID)
        harness.awaitUi(listOf(655 to 8, 655 to 5, 655 to 3)) { rows ->
            rows.map(LogSetUi::completed) == listOf(true, true, false)
        }
        harness.awaitPersisted(listOf(655 to 8, 655 to 5, 655 to 3))
    }

    @Test
    fun keyboard_replacement_preserves_rep_offsets_despite_intermediate_single_digits() = runVmTest {
        val harness = Harness()
        harness.showBench()
        for (input in listOf("", "1", "10")) {
            harness.viewModel.updateReps(SET_ONE_ID, input)
            runCurrent()
        }
        harness.viewModel.toggleCompleted(SET_ONE_ID)
        harness.awaitUi(listOf(1350 to 10, 1350 to 7, 1350 to 5))
        harness.awaitPersisted(listOf(1350 to 10, 1350 to 7, 1350 to 5))
    }

    // Tape steps commit once (2026-10-08 perf fix): the row on screen moves per step, the
    // repository write waits until the scrub has been quiet for STEP_COMMIT_DELAY_MS.

    @Test
    fun ten_tape_steps_write_the_set_once_with_the_final_row() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        repeat(10) { harness.viewModel.adjustWeight(SET_ONE_ID, delta = 5) }
        runCurrent()
        assertEquals(1400, harness.viewModel.sessionSetSnapshot().sets.single { it.id == SET_ONE_ID }.weightLbs)
        assertEquals(emptyList(), harness.setRepository.writes, "No write while the tape is still moving")

        advanceTimeBy(STEP_COMMIT_DELAY_MS - 1)
        runCurrent()
        assertEquals(emptyList(), harness.setRepository.writes)

        advanceTimeBy(2)
        runCurrent()
        harness.awaitPersisted(listOf(1400 to 8, 1350 to 5, 1350 to 3))
        assertEquals(listOf(1400), harness.setRepository.writes.map { it.weightLbs })
        assertEquals(listOf(SET_ONE_SYNC_ID), harness.setRepository.writes.map(WorkoutSet::syncId))
    }

    @Test
    fun logging_right_after_a_step_commits_the_stepped_row_before_the_completion() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustWeight(SET_ONE_ID, delta = 50)
        harness.viewModel.toggleCompleted(SET_ONE_ID)
        runCurrent()
        harness.awaitPersisted(listOf(1400 to 8, 1400 to 5, 1400 to 3))

        val setOneWrites = harness.setRepository.writes.filter { it.syncId == SET_ONE_SYNC_ID }
        assertEquals(listOf(1400 to false, 1400 to true), setOneWrites.map { it.weightLbs to it.completed })
        advanceTimeBy(STEP_COMMIT_DELAY_MS * 2)
        runCurrent()
        assertEquals(2, harness.setRepository.writes.count { it.syncId == SET_ONE_SYNC_ID }, "The flushed step is not written again")
    }

    @Test
    fun changing_the_day_flushes_a_pending_step() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustReps(SET_ONE_ID, delta = 2)
        harness.viewModel.selectDate(LAST_TIME)
        runCurrent()
        harness.awaitPersisted(listOf(1350 to 10, 1350 to 5, 1350 to 3))

        assertTrue(testScheduler.currentTime < STEP_COMMIT_DELAY_MS, "The day change wrote it, not the timer")
        assertEquals(listOf(1350 to 10), harness.setRepository.writes.map { it.weightLbs to it.reps })
        advanceTimeBy(STEP_COMMIT_DELAY_MS * 2)
        runCurrent()
        assertEquals(1, harness.setRepository.writes.size)
        assertEquals(listOf(1350 to 10, 1350 to 5, 1350 to 3), harness.setRepository.persistedToday())
    }

    // Fix-up round (2026-10-08): writes survive clearing, re-read the row, follow id remaps.

    @Test
    fun a_step_write_started_before_clearing_still_reaches_the_repository() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()
        val gate = CompletableDeferred<Unit>()
        harness.setRepository.writeGate = gate

        harness.viewModel.adjustWeight(SET_ONE_ID, delta = 50)
        advanceTimeBy(STEP_COMMIT_DELAY_MS + 1)
        runCurrent()
        assertEquals(1, harness.viewModel.stepCommitsInFlight, "The write is parked in the repository")

        ViewModelStore().apply { put("log", harness.viewModel) }.clear()
        gate.complete(Unit)
        harness.awaitPersisted(listOf(1400 to 8, 1350 to 5, 1350 to 3))
        assertEquals(0, harness.viewModel.stepCommitsInFlight)
        assertEquals(listOf(1400), harness.setRepository.writes.map { it.weightLbs })
    }

    @Test
    fun clearing_with_a_step_still_in_the_buffer_writes_it() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustReps(SET_ONE_ID, delta = 2)
        ViewModelStore().apply { put("log", harness.viewModel) }.clear()
        harness.awaitPersisted(listOf(1350 to 10, 1350 to 5, 1350 to 3))
        assertTrue(testScheduler.currentTime < STEP_COMMIT_DELAY_MS, "Clearing wrote it, not the timer")
        assertEquals(1, harness.setRepository.writes.size)
    }

    @Test
    fun a_step_on_a_row_moved_meanwhile_keeps_the_new_date_and_gets_the_stepped_weight() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustWeight(SET_ONE_ID, delta = 50)
        harness.setRepository.replaceRow({ it.syncId == SET_ONE_SYNC_ID }) { it.copy(date = MOVED_TO, completed = true) }
        advanceTimeBy(STEP_COMMIT_DELAY_MS + 1)
        runCurrent()
        withTimeout(TIMEOUT) { harness.setRepository.sets.first { harness.setRepository.writes.isNotEmpty() } }

        val written = harness.setRepository.writes.single()
        assertEquals(MOVED_TO to 1400, written.date to written.weightLbs)
        assertTrue(written.completed, "Fields a step cannot change come from the row as it is now")
        assertEquals(MOVED_TO, harness.setRepository.sets.value.single { it.syncId == SET_ONE_SYNC_ID }.date)
    }

    @Test
    fun a_step_on_a_row_deleted_meanwhile_writes_nothing() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustWeight(SET_ONE_ID, delta = 50)
        harness.setRepository.removeRow { it.syncId == SET_ONE_SYNC_ID }
        harness.awaitUi(listOf(1350 to 5, 1350 to 3))
        advanceTimeBy(STEP_COMMIT_DELAY_MS * 2)
        runCurrent()

        assertEquals(emptyList(), harness.setRepository.writes)
        assertTrue(harness.setRepository.sets.value.none { it.syncId == SET_ONE_SYNC_ID })
    }

    @Test
    fun a_pending_step_follows_a_legacy_temp_id_remap() = runVmTest {
        val tempRow = TODAY_COPIES[0].copy(id = TEMP_ID, syncId = "")
        val harness = Harness(
            stepCommitContext = StandardTestDispatcher(testScheduler),
            todayRows = listOf(tempRow) + TODAY_COPIES.drop(1),
        )
        harness.showBench()

        harness.viewModel.adjustWeight(TEMP_ID, delta = 50)
        harness.awaitUi(listOf(1400 to 8, 1350 to 5, 1350 to 3))
        // The legacy sync drains the insert: same row, server id, still no syncId.
        harness.setRepository.replaceRow({ it.id == TEMP_ID }) { it.copy(id = REAL_ID) }
        val remapped = harness.awaitUi(listOf(1400 to 8, 1350 to 5, 1350 to 3)) { rows -> rows[0].id == REAL_ID }
        assertEquals(1400, remapped[0].weightLbs, "The overlay still holds the stepped weight after the remap")
        assertEquals(emptyList(), harness.setRepository.writes)

        harness.viewModel.adjustWeight(REAL_ID, delta = 5)
        advanceTimeBy(STEP_COMMIT_DELAY_MS * 2)
        runCurrent()
        harness.awaitPersisted(listOf(1405 to 8, 1350 to 5, 1350 to 3))
        assertEquals(listOf(REAL_ID to 1405), harness.setRepository.writes.map { it.id to it.weightLbs }, "One write, real id")
    }

    @Test
    fun done_flushes_a_pending_step_so_its_hints_stay_cleared() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustWeight(SET_ONE_ID, delta = 50)
        harness.viewModel.finishExercise(BENCH_ID)
        harness.awaitPersisted(listOf(1400 to 8, 1350 to 5, 1350 to 3))
        assertTrue(testScheduler.currentTime < STEP_COMMIT_DELAY_MS, "Done wrote the step, not the timer")
        advanceTimeBy(STEP_COMMIT_DELAY_MS * 2)
        runCurrent()

        harness.awaitUi(listOf(1400 to 8, 1350 to 5, 1350 to 3)) { rows ->
            rows.all { it.suggestedWeightLbs == null && it.suggestedReps == null }
        }
        // The step, then Done logging each open set as it stands; the timer adds nothing.
        assertEquals(4, harness.setRepository.writes.size)
        assertEquals(listOf(1400 to 8, 1350 to 5, 1350 to 3), harness.setRepository.writes.drop(1).map { it.weightLbs to it.reps })
    }

    @Test
    fun done_logs_the_open_working_sets_as_they_stand_and_leaves_a_skipped_warm_up() = runVmTest {
        val harness = Harness(
            todayRows = TODAY_COPIES.mapIndexed { index, set -> if (index == 0) set.copy(isWarmup = true) else set },
        )
        harness.showBench()

        harness.viewModel.finishExercise(BENCH_ID)
        val today = withTimeout(TIMEOUT) {
            harness.setRepository.sets.first { rows ->
                rows.filter { it.date == TODAY && !it.isWarmup }.all(WorkoutSet::completed)
            }
        }.filter { it.date == TODAY }.sortedBy(WorkoutSet::setNumber)

        assertEquals(listOf(false, true, true), today.map(WorkoutSet::completed), "A skipped warm-up stays unlogged")
        assertEquals(listOf(1350 to 8, 1350 to 5, 1350 to 3), today.map { it.weightLbs to it.reps }, "Logged exactly as shown")
        assertEquals(listOf("bench-today-2", "bench-today-3"), harness.setRepository.writes.map(WorkoutSet::syncId))
    }

    @Test
    fun flushing_pending_steps_writes_them_before_the_timer() = runVmTest {
        val harness = Harness(stepCommitContext = StandardTestDispatcher(testScheduler))
        harness.showBench()

        harness.viewModel.adjustWeight(SET_ONE_ID, delta = 50)
        harness.viewModel.adjustReps(SET_TWO_ID, delta = 1)
        // What the PWA's visibilitychange → hidden listener calls.
        harness.viewModel.flushPendingSetCommits()
        harness.awaitPersisted(listOf(1400 to 8, 1350 to 6, 1350 to 3))
        assertEquals(listOf(SET_ONE_SYNC_ID, "bench-today-2"), harness.setRepository.writes.map(WorkoutSet::syncId))

        advanceTimeBy(STEP_COMMIT_DELAY_MS * 2)
        runCurrent()
        assertEquals(2, harness.setRepository.writes.size, "The timers do not write again")
    }

    private class Harness(
        private val weight: Int = 1350,
        stepCommitContext: CoroutineContext = EmptyCoroutineContext,
        todayRows: List<WorkoutSet> = TODAY_COPIES,
    ) {
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(TODAY),
            exercises = listOf(Exercise(id = BENCH_ID, name = "Bench Press", muscleGroups = "CHEST")),
            sets = (LAST_SESSION + todayRows).map { it.copy(weightLbs = weight) },
            settings = UserSettings(),
            cycle = Cycle(numTypes = 1, isActive = false),
            slots = listOf(CycleSlot(id = 1L, name = "Push", orderIndex = 0)),
            splitExercises = emptyList(),
            workoutDays = listOf(WorkoutDay(date = TODAY)),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(LAST_SESSION + todayRows),
        )
        private val base = DemoRepos(seed)
        val setRepository = PersistingSetRepository(base.sets)
        private val repos: AppRepos = object : AppRepos by base {
            override val sets: SetRepository = setRepository
        }
        val viewModel = LogViewModel(repos, isOnline = { false }, stepCommitContext = stepCommitContext)

        suspend fun showBench(): List<LogSetUi> {
            viewModel.selectDate(TODAY)
            return awaitUi(listOf(weight to 8, weight to 5, weight to 3))
        }

        private fun benchRows(state: LogUiState): List<LogSetUi> = state.exercises
            .singleOrNull { it.exerciseId == BENCH_ID }
            ?.sets
            ?.sortedBy(LogSetUi::setNumber)
            .orEmpty()

        suspend fun awaitUi(
            expected: List<Pair<Int, Int>>,
            alsoWhen: (List<LogSetUi>) -> Boolean = { true },
        ): List<LogSetUi> {
            var lastSeen: List<LogSetUi> = emptyList()
            return try {
                withTimeout(TIMEOUT) {
                    viewModel.uiState
                        .map(::benchRows)
                        .first { rows ->
                            lastSeen = rows
                            rows.map { it.weightLbs to it.reps } == expected && alsoWhen(rows)
                        }
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError("UI rows never settled at $expected; last seen $lastSeen", e)
            }
        }

        suspend fun awaitPersisted(expected: List<Pair<Int, Int>>) {
            try {
                withTimeout(TIMEOUT) {
                    setRepository.sets.first { setRepository.persistedToday() == expected }
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError(
                    "Persisted rows never became $expected; last seen ${setRepository.persistedToday()}",
                    e,
                )
            }
        }
    }

    /** Write-through seam so UI optimism and persistence can be asserted separately. */
    private class PersistingSetRepository(delegate: SetRepository) : SetRepository by delegate {
        private val mutableSets = MutableStateFlow(delegate.sets.value)
        override val sets: StateFlow<List<WorkoutSet>> = mutableSets.asStateFlow()
        private val recordedWrites = mutableListOf<WorkoutSet>()
        val writes: List<WorkoutSet> get() = recordedWrites.toList()

        override fun observeSetsForDate(date: String): Flow<List<WorkoutSet>> =
            mutableSets.map { SetQueries.forDate(it, date) }

        override suspend fun getAllSets(): List<WorkoutSet> = mutableSets.value

        override suspend fun getSetsForDate(date: String): List<WorkoutSet> =
            SetQueries.forDate(mutableSets.value, date)

        override suspend fun getSetsByDate(date: String): List<WorkoutSet> = getSetsForDate(date)

        override suspend fun getSetsForDates(dates: List<String>): List<WorkoutSet> =
            mutableSets.value.filter { it.date in dates.toSet() }

        override suspend fun getCompletedSetsForDates(dates: List<String>): List<WorkoutSet> =
            mutableSets.value.filter { it.completed && it.date in dates.toSet() }

        override suspend fun getById(id: Long): WorkoutSet? =
            mutableSets.value.firstOrNull { it.id == id }

        override suspend fun update(set: WorkoutSet): LocalCommit<WorkoutSet> = LocalCommit.Committed(persist(set))

        /** When set, a step write parks here, as one waiting on the repository's mutation lock would. */
        var writeGate: CompletableDeferred<Unit>? = null

        override suspend fun updateSet(set: WorkoutSet, observed: WorkoutSet?): LocalCommit<WorkoutSet> {
            writeGate?.await()
            return LocalCommit.Committed(persist(set))
        }

        /** Another path (a refresh, a move) rewrites a row outside the view model. */
        fun replaceRow(match: (WorkoutSet) -> Boolean, transform: (WorkoutSet) -> WorkoutSet) {
            mutableSets.value = mutableSets.value.map { if (match(it)) transform(it) else it }
        }

        fun removeRow(match: (WorkoutSet) -> Boolean) {
            mutableSets.value = mutableSets.value.filterNot(match)
        }

        fun persistedToday(): List<Pair<Int?, Int?>> = mutableSets.value
            .filter { it.date == TODAY && it.exerciseId == BENCH_ID }
            .sortedBy(WorkoutSet::setNumber)
            .map { it.weightLbs to it.reps }

        private fun persist(candidate: WorkoutSet): WorkoutSet {
            val index = mutableSets.value.indexOfFirst { existing ->
                if (candidate.syncId.isNotBlank()) existing.syncId == candidate.syncId else existing.id == candidate.id
            }
            check(index >= 0) { "Unknown set write: id=${candidate.id}, syncId=${candidate.syncId}" }
            val existing = mutableSets.value[index]
            val persisted = candidate.copy(id = existing.id, syncId = existing.syncId)
            recordedWrites += persisted
            mutableSets.value = mutableSets.value.toMutableList().also { it[index] = persisted }
            return persisted
        }
    }

    private companion object {
        const val LAST_TIME = "2026-09-14"
        const val TODAY = "2026-09-21"
        const val BENCH_ID = 7L
        const val SET_ONE_ID = 201L
        const val SET_TWO_ID = 202L
        const val SET_THREE_ID = 203L
        const val SET_ONE_SYNC_ID = "bench-today-1"
        const val TEMP_ID = -5L
        const val REAL_ID = 205L
        const val MOVED_TO = "2026-09-22"
        const val TIMEOUT = 2_000L

        private fun bench(id: Long, syncId: String, date: String, setNumber: Int, reps: Int, completed: Boolean) =
            WorkoutSet(
                id = id,
                syncId = syncId,
                exerciseId = BENCH_ID,
                date = date,
                setNumber = setNumber,
                weightLbs = 1350,
                reps = reps,
                completed = completed,
            )

        val LAST_SESSION = listOf(
            bench(101L, "bench-last-1", LAST_TIME, 1, 8, completed = true),
            bench(102L, "bench-last-2", LAST_TIME, 2, 5, completed = true),
            bench(103L, "bench-last-3", LAST_TIME, 3, 3, completed = true),
        )

        /** Today's rows as the whole-session copy leaves them: last time's numbers, unchecked. */
        val TODAY_COPIES = listOf(
            bench(SET_ONE_ID, SET_ONE_SYNC_ID, TODAY, 1, 8, completed = false),
            bench(SET_TWO_ID, "bench-today-2", TODAY, 2, 5, completed = false),
            bench(SET_THREE_ID, "bench-today-3", TODAY, 3, 3, completed = false),
        )
    }
}
