package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.ui.navigation.LoggedSet
import com.ayman.ecolift.ui.navigation.resolvedRotationSet
import com.ayman.ecolift.ui.navigation.rotationDisplayedRepsOf
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Regression for the Pull Up flow reproduced in the 2026-08-23 screen recording.
 *
 * A scrub is a direct edit of the selected row. A proposed cascade may occupy the explicit
 * suggestion slot, but it must not replace the primary number, become a different row's scrub
 * baseline, or reach persistence merely because the exercise is parked with DONE.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PullUpSetEditIsolationTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun set_two_scrub_keeps_set_three_as_a_separate_unaccepted_suggestion_and_done_does_not_save_it() =
        runVmTest {
            val harness = Harness(StandardTestDispatcher(testScheduler))
            val initialRows = harness.showPullUp()
            val selectedSetTwo = initialRows.selected(setNumber = 2, id = SET_TWO_ID, syncId = SET_TWO_SYNC_ID)

            harness.viewModel.adjustReps(selectedSetTwo.id, delta = 3)
            // The cascade hint is cast once the scrub settles (steps commit after STEP_COMMIT_DELAY_MS).
            val editedRows = harness.awaitUiReps(10, 10, 7) { rows ->
                rows.single { it.id == SET_THREE_ID }.suggestedReps != null
            }
            harness.awaitPersistedReps(10, 10, 7)

            assertEquals(
                listOf(10, 10, 7),
                editedRows.map(LogSetUi::reps),
                "Only set 2 was edited; set 3's stored/UI model value must remain 7",
            )
            assertEquals(
                10,
                editedRows.single { it.id == SET_THREE_ID }.suggestedReps,
                "The cascade remains available only in the dedicated suggestion slot",
            )
            assertEquals(
                listOf(10, 10, 7),
                editedRows.map { rotationDisplayedRepsOf(it.asLoggedSet()) },
                "An unaccepted suggestion must not masquerade as the primary displayed value",
            )
            assertEquals(
                listOf(SET_TWO_SYNC_ID),
                harness.setRepository.writes.map(WorkoutSet::syncId),
                "The set 2 gesture must issue no writes for set 1 or set 3",
            )

            harness.viewModel.finishExercise(PULL_UP_ID)
            runCurrent()

            assertEquals(
                listOf(10, 10, 7),
                harness.setRepository.persistedReps(),
                "DONE logs the sets as they stand; it is not consent to persist set 3's suggestion",
            )
            assertTrue(
                harness.setRepository.writes.filter { it.syncId == SET_THREE_SYNC_ID }.all { it.reps == 7 },
                "DONE must not manufacture a set 3 update from an unaccepted suggestion",
            )
        }

    @Test
    fun direct_set_three_scrub_starts_from_its_own_stored_seven_not_the_pending_ten() = runVmTest {
        val harness = Harness(StandardTestDispatcher(testScheduler))
        val initialRows = harness.showPullUp()
        val selectedSetTwo = initialRows.selected(setNumber = 2, id = SET_TWO_ID, syncId = SET_TWO_SYNC_ID)

        harness.viewModel.adjustReps(selectedSetTwo.id, delta = 3)
        val rowsWithSuggestion = harness.awaitUiReps(10, 10, 7) { rows ->
            rows.single { it.id == SET_THREE_ID }.suggestedReps != null
        }
        assertEquals(10, rowsWithSuggestion.single { it.id == SET_THREE_ID }.suggestedReps)

        val selectedSetThree = rowsWithSuggestion.selected(
            setNumber = 3,
            id = SET_THREE_ID,
            syncId = SET_THREE_SYNC_ID,
        )
        harness.viewModel.adjustReps(selectedSetThree.id, delta = 1)

        val directlyEditedRows = harness.awaitUiReps(10, 10, 8)
        harness.awaitPersistedReps(10, 10, 8)
        val setThree = directlyEditedRows.single { it.id == SET_THREE_ID }

        assertEquals(8, setThree.reps, "Set 3 must scrub from its own stored 7")
        assertNull(
            setThree.suggestedReps,
            "A direct set 3 edit consumes/dismisses the old proposal instead of stepping from it",
        )
        assertEquals(8, rotationDisplayedRepsOf(setThree.asLoggedSet()))
        assertEquals(listOf(10, 10, 8), harness.setRepository.persistedReps())
        assertEquals(
            listOf(SET_TWO_SYNC_ID, SET_THREE_SYNC_ID),
            harness.setRepository.writes.map(WorkoutSet::syncId),
            "Each gesture must persist only the row selected by its stable sync identity",
        )
    }

    private class Harness(stepCommitContext: CoroutineContext = EmptyCoroutineContext) {
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(DATE),
            exercises = listOf(
                Exercise(
                    id = PULL_UP_ID,
                    name = "Pull Up",
                    muscleGroups = "BACK",
                    isBodyweight = true,
                ),
            ),
            sets = SEEDED_SETS,
            settings = UserSettings(),
            cycle = Cycle(numTypes = 1, isActive = false),
            slots = listOf(CycleSlot(id = 1L, name = "Pull", orderIndex = 0)),
            splitExercises = emptyList(),
            workoutDays = listOf(WorkoutDay(date = DATE)),
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(SEEDED_SETS),
        )
        private val base = DemoRepos(seed)
        val setRepository = PersistingSetRepository(base.sets)
        private val repos: AppRepos = object : AppRepos by base {
            override val sets: SetRepository = setRepository
        }
        val viewModel = LogViewModel(repos, isOnline = { false }, stepCommitContext = stepCommitContext)

        suspend fun showPullUp(): List<LogSetUi> {
            viewModel.selectDate(DATE)
            return awaitUiReps(10, 7, 7)
        }

        suspend fun awaitUiReps(
            vararg expected: Int,
            alsoWhen: (List<LogSetUi>) -> Boolean = { true },
        ): List<LogSetUi> = withTimeout(TIMEOUT) {
            viewModel.uiState
                .map { state ->
                    state.exercises
                        .singleOrNull { it.exerciseId == PULL_UP_ID }
                        ?.sets
                        ?.sortedBy(LogSetUi::setNumber)
                        .orEmpty()
                }
                .first { rows -> rows.map(LogSetUi::reps) == expected.toList() && alsoWhen(rows) }
        }

        suspend fun awaitPersistedReps(vararg expected: Int) {
            withTimeout(TIMEOUT) {
                setRepository.sets.first {
                    setRepository.persistedReps() == expected.toList()
                }
            }
        }
    }

    /** A real write-through repository seam: UI optimism and persistence are asserted separately. */
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

        override suspend fun updateSet(set: WorkoutSet, observed: WorkoutSet?): LocalCommit<WorkoutSet> {
            return LocalCommit.Committed(persist(set))
        }

        fun persistedReps(): List<Int?> = mutableSets.value
            .filter { it.date == DATE && it.exerciseId == PULL_UP_ID }
            .sortedBy(WorkoutSet::setNumber)
            .map(WorkoutSet::reps)

        private fun persist(candidate: WorkoutSet): WorkoutSet {
            val index = mutableSets.value.indexOfFirst { existing ->
                if (candidate.syncId.isNotBlank()) {
                    existing.syncId == candidate.syncId
                } else {
                    existing.id == candidate.id
                }
            }
            check(index >= 0) { "Unknown set write: id=${candidate.id}, syncId=${candidate.syncId}" }
            val existing = mutableSets.value[index]
            val persisted = candidate.copy(id = existing.id, syncId = existing.syncId)
            recordedWrites += persisted
            mutableSets.value = mutableSets.value.toMutableList().also { it[index] = persisted }
            return persisted
        }
    }

    private fun List<LogSetUi>.selected(setNumber: Int, id: Long, syncId: String): LoggedSet =
        requireNotNull(
            resolvedRotationSet(
                visibleSets = map { it.asLoggedSet() },
                preferredSetNumber = setNumber,
                preferredSetId = id,
                preferredSetSyncId = syncId,
            ),
        )

    private fun LogSetUi.asLoggedSet(): LoggedSet = LoggedSet(
        id = id,
        syncId = syncId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        weight = WeightLbs.formatStored(weightLbs),
        reps = reps?.toString().orEmpty(),
        isBodyweight = isBodyweight,
        isCompleted = completed,
        suggestedWeight = suggestedWeightLbs?.let(WeightLbs::formatStored),
        suggestedReps = suggestedReps?.toString(),
        hasAssistSuggestion = hasAssistSuggestion,
        inputWarning = inputWarning,
        restSeconds = restAfterSeconds,
    )

    private companion object {
        const val DATE = "2026-08-14"
        const val PULL_UP_ID = 42L
        const val SET_ONE_ID = 101L
        const val SET_TWO_ID = 102L
        const val SET_THREE_ID = 103L
        const val SET_ONE_SYNC_ID = "pull-up-set-1"
        const val SET_TWO_SYNC_ID = "pull-up-set-2"
        const val SET_THREE_SYNC_ID = "pull-up-set-3"
        const val TIMEOUT = 2_000L

        val SEEDED_SETS = listOf(
            WorkoutSet(
                id = SET_ONE_ID,
                syncId = SET_ONE_SYNC_ID,
                exerciseId = PULL_UP_ID,
                date = DATE,
                setNumber = 1,
                reps = 10,
                isBodyweight = true,
            ),
            WorkoutSet(
                id = SET_TWO_ID,
                syncId = SET_TWO_SYNC_ID,
                exerciseId = PULL_UP_ID,
                date = DATE,
                setNumber = 2,
                reps = 7,
                isBodyweight = true,
            ),
            WorkoutSet(
                id = SET_THREE_ID,
                syncId = SET_THREE_SYNC_ID,
                exerciseId = PULL_UP_ID,
                date = DATE,
                setNumber = 3,
                reps = 7,
                isBodyweight = true,
            ),
        )
    }
}
