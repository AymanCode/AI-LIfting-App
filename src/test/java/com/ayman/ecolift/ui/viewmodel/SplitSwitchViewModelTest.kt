package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.data.splits.SplitSwitchIntent
import com.ayman.ecolift.data.splits.SplitSwitchOutcome
import com.ayman.ecolift.data.splits.SplitSwitchTarget
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.SplitReplacementCoordinator
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.sync.DayPendingWrite
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import com.ayman.ecolift.web.supabase.sync.SplitReplacementJournal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitSwitchViewModelTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun saved_a_to_saved_b_replaces_assignment_and_rows() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val outcome = nextOutcome(harness.viewModel)

        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        val applied = assertIs<SplitSwitchOutcome.Applied>(outcome.await())

        assertEquals(requestId, applied.requestId)
        assertEquals(SLOT_B, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(listOf(EXERCISE_B), harness.setRepository.forDate(DATE).map { it.exerciseId })
    }

    @Test
    fun selected_saved_split_converts_to_clear_and_untouched_day_needs_no_confirmation() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val outcome = nextOutcome(harness.viewModel)

        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_A))
        val applied = assertIs<SplitSwitchOutcome.Applied>(outcome.await())

        assertEquals(requestId, applied.requestId)
        assertNull(harness.viewModel.pendingSplitSwitch.value)
        assertNull(harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertTrue(harness.setRepository.forDate(DATE).isEmpty())
    }

    @Test
    fun toggling_off_an_edited_day_requires_confirmation() = runVmTest {
        val harness = Harness()
        harness.setRepository.mutateSet(CURRENT_A_SET_ID) { it.copy(reps = 9) }
        harness.showDate()

        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_A))
        val pending = harness.pending(requestId)

        assertEquals(SplitSwitchTarget.Clear, pending.request.target)
        assertEquals(1, pending.affectedSetCount)
        assertEquals(SLOT_A, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(listOf(EXERCISE_A), harness.setRepository.forDate(DATE).map { it.exerciseId })
    }

    @Test
    fun cancel_preserves_assignment_and_rows() = runVmTest {
        val harness = Harness()
        harness.setRepository.mutateSet(CURRENT_A_SET_ID) { it.copy(reps = 9) }
        harness.showDate()
        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        harness.pending(requestId)
        val outcome = nextOutcome(harness.viewModel)

        harness.viewModel.cancelSplitSwitch(requestId)

        assertIs<SplitSwitchOutcome.Cancelled>(outcome.await())
        assertEquals(SLOT_A, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(9, harness.setRepository.forDate(DATE).single().reps)
        assertTrue(harness.setRepository.replacementDates.isEmpty())
    }

    @Test
    fun confirm_after_changing_date_is_stale_and_mutates_nothing() = runVmTest {
        val harness = Harness()
        harness.setRepository.mutateSet(CURRENT_A_SET_ID) { it.copy(reps = 9) }
        harness.showDate()
        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        harness.pending(requestId)
        harness.viewModel.selectDate(OTHER_DATE)
        val outcome = nextOutcome(harness.viewModel)

        harness.viewModel.confirmSplitSwitch(requestId)

        assertIs<SplitSwitchOutcome.Cancelled>(outcome.await())
        assertEquals(SLOT_A, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(9, harness.setRepository.forDate(DATE).single().reps)
        assertTrue(harness.setRepository.replacementDates.isEmpty())
    }

    @Test
    fun no_confirm_request_that_changes_date_during_preparation_only_mutates_captured_date() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val pause = harness.pauseNextPreparation()
        val outcome = nextOutcome(harness.viewModel)
        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        pause.entered.await()

        harness.viewModel.selectDate(OTHER_DATE)
        pause.release.complete(Unit)
        val applied = assertIs<SplitSwitchOutcome.Applied>(outcome.await())

        assertEquals(requestId, applied.requestId)
        assertEquals(listOf(DATE), harness.setRepository.replacementDates)
        assertEquals(SLOT_B, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertNull(harness.workoutRepository.day(OTHER_DATE)?.cycleSlotId)
        assertTrue(harness.setRepository.forDate(OTHER_DATE).isEmpty())
    }

    @Test
    fun rapid_a_then_b_requests_leave_only_b() = runVmTest {
        val harness = Harness(currentDay = null, currentRows = emptyList())
        harness.showDate(expectedSetCount = 0)
        val pause = harness.pauseNextPreparation()
        assertNotNull(harness.viewModel.assignCycleSlot(SLOT_A))
        pause.entered.await()
        val outcome = nextOutcome(harness.viewModel)

        val requestB = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        pause.release.complete(Unit)
        val applied = assertIs<SplitSwitchOutcome.Applied>(outcome.await())

        assertEquals(requestB, applied.requestId)
        assertEquals(SLOT_B, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(listOf(EXERCISE_B), harness.setRepository.forDate(DATE).map { it.exerciseId })
        assertEquals(listOf(DATE), harness.setRepository.replacementDates)
    }

    @Test
    fun clean_request_becomes_confirmation_when_a_set_changes_before_lock_acquisition() = runVmTest {
        val mutations: List<(Harness) -> Unit> = listOf(
            { it.setRepository.mutateSet(CURRENT_A_SET_ID) { set -> set.copy(completed = true) } },
            { it.setRepository.mutateSet(CURRENT_A_SET_ID) { set -> set.copy(reps = 11) } },
            {
                it.setRepository.addLocal(
                    workoutSet(
                        id = ADDED_SET_ID,
                        exerciseId = EXERCISE_A,
                        date = DATE,
                        setNumber = 2,
                        weightLbs = 100,
                        reps = 8,
                    ),
                )
            },
        )

        mutations.forEach { mutate ->
            val harness = Harness()
            harness.showDate()
            val pause = harness.pauseNextPreparation()
            val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
            pause.entered.await()
            mutate(harness)
            pause.release.complete(Unit)

            val pending = harness.pending(requestId)
            assertTrue(pending.affectedSetCount >= 1)
            assertEquals(SLOT_A, harness.workoutRepository.day(DATE)?.cycleSlotId)
            assertTrue(harness.setRepository.replacementDates.isEmpty())
        }
    }

    @Test
    fun confirmed_request_is_confirmed_again_when_precondition_changes() = runVmTest {
        val harness = Harness()
        harness.setRepository.mutateSet(CURRENT_A_SET_ID) { it.copy(reps = 9) }
        harness.showDate()
        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        harness.pending(requestId)
        val pause = harness.pauseNextPreparation()

        harness.viewModel.confirmSplitSwitch(requestId)
        pause.entered.await()
        harness.setRepository.mutateSet(CURRENT_A_SET_ID) { it.copy(reps = 10) }
        pause.release.complete(Unit)
        val fresh = harness.pending(requestId)

        assertEquals(requestId, fresh.request.requestId)
        assertEquals(SLOT_A, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(10, harness.setRepository.forDate(DATE).single().reps)
        assertTrue(harness.setRepository.replacementDates.isEmpty())
    }

    @Test
    fun local_preparation_failure_emits_failed_without_success_or_mutation() = runVmTest {
        val harness = Harness()
        harness.journal.failNextSave = true
        harness.showDate()
        val outcome = nextOutcome(harness.viewModel)

        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        val failed = assertIs<SplitSwitchOutcome.Failed>(outcome.await())

        assertEquals(requestId, failed.requestId)
        assertEquals(SLOT_A, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(listOf(EXERCISE_A), harness.setRepository.forDate(DATE).map { it.exerciseId })
        assertTrue(harness.setRepository.replacementDates.isEmpty())
        assertNull(harness.viewModel.pendingSplitSwitch.value)
        assertEquals(
            failed.message,
            withTimeout(TIMEOUT) {
                harness.viewModel.uiState.first { it.uiMessage == failed.message }.uiMessage
            },
        )
    }

    @Test
    fun durable_replacement_clears_all_outgoing_set_transient_state() = runVmTest {
        val history = defaultHistory() + workoutSet(
            id = 12,
            exerciseId = EXERCISE_A,
            date = HISTORY_DATE,
            setNumber = 2,
            weightLbs = 95,
            reps = 8,
            completed = true,
        )
        val current = listOf(
            cleanCurrentA(),
            workoutSet(
                id = SECOND_A_SET_ID,
                exerciseId = EXERCISE_A,
                date = DATE,
                setNumber = 2,
                weightLbs = 95,
                reps = 8,
            ),
        )
        val harness = Harness(currentRows = current, historyRows = history)
        harness.showDate(expectedSetCount = 2)
        harness.viewModel.requestRecommendations(EXERCISE_A)
        // Working is enough: the snapshot keys off the recommendation map, and the recommender
        // never reaches Done against the fakes, which supply no equipment or catalog data.
        withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { state ->
                state.exercises.firstOrNull { it.exerciseId == EXERCISE_A }
                    ?.recommendationState != RecommendationState.Idle
            }
        }
        // Put the warning on the still-open sibling. LOG intentionally dismisses advisory state
        // for the row being completed, while this test needs each outgoing transient channel to
        // remain populated until the split replacement clears the entire exercise.
        harness.viewModel.checkSetInput(SECOND_A_SET_ID, EXERCISE_A, 95, 999, false)
        harness.viewModel.toggleCompleted(CURRENT_A_SET_ID)
        // Edit set 1 only after logging it: logging writes the hints a set cast onto later open
        // sets into those rows (LogSetCarryOverTest), so a hint raised before LOG would be gone
        // by the time the snapshot is taken. A hint raised afterwards stays pending.
        harness.viewModel.updateReps(CURRENT_A_SET_ID, "9")
        withTimeout(TIMEOUT) {
            harness.viewModel.restStopwatch.first { it != null }
        }
        withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { state ->
                val exercise = state.exercises.firstOrNull { it.exerciseId == EXERCISE_A }
                exercise != null &&
                    exercise.sets.any { it.inputWarning != null } &&
                    exercise.sets.any { it.id == SECOND_A_SET_ID && it.suggestedReps != null }
            }
        }
        withTimeout(TIMEOUT) {
            harness.setRepository.sets.first { rows -> rows.any { it.id == CURRENT_A_SET_ID && it.completed } }
        }
        val before = harness.viewModel.splitSwitchTransientStateSnapshot()
        val outgoingIds = current.mapTo(linkedSetOf()) { it.id }
        assertEquals(CURRENT_A_SET_ID, before.activeRestSetId)
        // LogViewModel's assist tools are UnavailableAgentTools, so no suggestion is stored.
        assertTrue(
            before.warningSetIds.any { it in outgoingIds },
            "Expected an outgoing input warning; snapshot=$before",
        )
        assertTrue(
            before.adjustmentSetIds.any { it in outgoingIds },
            "Expected an outgoing smart-adjustment target; snapshot=$before",
        )
        assertTrue(
            before.adjustmentSourceSetIds.any { it in outgoingIds },
            "Expected an outgoing smart-adjustment source; snapshot=$before",
        )
        assertTrue(
            EXERCISE_A in before.recommendationExerciseIds,
            "Expected the outgoing exercise recommendation state; snapshot=$before",
        )

        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        harness.pending(requestId)
        val outcome = nextOutcome(harness.viewModel)
        harness.viewModel.confirmSplitSwitch(requestId)
        assertIs<SplitSwitchOutcome.Applied>(outcome.await())
        val after = harness.viewModel.splitSwitchTransientStateSnapshot()

        assertNull(after.activeRestSetId)
        assertTrue(after.assistSetIds.none { it in outgoingIds })
        assertTrue(after.warningSetIds.none { it in outgoingIds })
        assertTrue(after.adjustmentSetIds.none { it in outgoingIds })
        assertTrue(after.adjustmentSourceSetIds.none { it in outgoingIds })
        assertFalse(EXERCISE_A in after.recommendationExerciseIds)
    }

    @Test
    fun deep_link_to_assigned_split_applies_without_clearing_but_user_retap_clears() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val deepLinkOutcome = nextOutcome(harness.viewModel)

        val deepLinkId = assertNotNull(
            harness.viewModel.assignCycleSlot(SLOT_A, SplitSwitchIntent.DeepLinkLoad),
        )
        assertEquals(deepLinkId, assertIs<SplitSwitchOutcome.Applied>(deepLinkOutcome.await()).requestId)
        assertEquals(SLOT_A, harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(listOf(EXERCISE_A), harness.setRepository.forDate(DATE).map { it.exerciseId }.distinct())
        val retapOutcome = nextOutcome(harness.viewModel)

        val retapId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_A))
        assertEquals(retapId, assertIs<SplitSwitchOutcome.Applied>(retapOutcome.await()).requestId)
        assertNull(harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertTrue(harness.setRepository.forDate(DATE).isEmpty())
    }

    @Test
    fun saved_split_occurrence_does_not_continue_from_before_cycle_start() = runVmTest {
        val harness = Harness(
            currentDay = null,
            currentRows = emptyList(),
            cycleStartDate = DATE,
            priorDays = listOf(
                WorkoutDay(
                    date = HISTORY_DATE,
                    cycleSlotType = 1,
                    cycleSlotOccurrence = 9,
                    cycleSlotId = SLOT_B,
                ),
            ),
        )
        harness.showDate(expectedSetCount = 0)
        val state = withTimeout(TIMEOUT) {
            harness.viewModel.uiState.first { it.currentDate == DATE && it.cycleOptions.isNotEmpty() }
        }
        assertEquals(listOf(SLOT_A, SLOT_B), state.cycleOptions.map { it.slotId })
        val outcome = nextOutcome(harness.viewModel)

        val requestId = assertNotNull(harness.viewModel.assignCycleSlot(SLOT_B))
        assertEquals(requestId, assertIs<SplitSwitchOutcome.Applied>(outcome.await()).requestId)

        assertEquals(1, harness.workoutRepository.day(DATE)?.cycleSlotOccurrence)
    }

    @Test
    fun clip_on_leaves_the_unticked_lifts_out_of_the_seed() = runVmTest {
        val harness = Harness(splitExercises = defaultSplitExercises() + SplitExercise(3, SLOT_B, EXERCISE_C, 1))
        harness.showDate()
        val outcome = nextOutcome(harness.viewModel)

        val requestId = assertNotNull(harness.viewModel.loadProgram(SLOT_B, setOf(EXERCISE_C)))

        assertEquals(requestId, assertIs<SplitSwitchOutcome.Applied>(outcome.await()).requestId)
        // Seed-only (spec 2026-09-28): the lifts land, the day's split is left alone.
        assertNull(harness.workoutRepository.day(DATE)?.cycleSlotId)
        assertEquals(listOf(EXERCISE_B), harness.setRepository.forDate(DATE).map { it.exerciseId })
    }

    @Test
    fun start_waits_for_the_saved_boards_then_loads_the_boards_in_lifts_in_order() = runVmTest {
        val harness = Harness(splitExercises = defaultSplitExercises() + SplitExercise(3, SLOT_B, EXERCISE_C, 1))
        harness.showDate()
        harness.workoutRepository.boards.value = SplitChoicesState.Loading
        assertNull(harness.viewModel.loadProgram(SLOT_B, emptySet()))
        assertEquals(listOf(EXERCISE_A), harness.setRepository.forDate(DATE).map { it.exerciseId })

        val saved = mapOf(SLOT_B to board(SLOT_B, listOf(EXERCISE_C, EXERCISE_B)))
        harness.workoutRepository.boards.value = SplitChoicesState.Ready(saved, confirmed = saved)
        harness.viewModel.awaitSplitChoices(SLOT_B)
        val outcome = nextOutcome(harness.viewModel)
        val requestId = assertNotNull(harness.viewModel.loadProgram(SLOT_B, emptySet(), SplitSwitchIntent.DeepLinkLoad))
        assertEquals(requestId, assertIs<SplitSwitchOutcome.Applied>(outcome.await()).requestId)
        assertEquals(listOf(EXERCISE_C, EXERCISE_B), harness.setRepository.forDate(DATE).map { it.exerciseId })
    }

    @Test
    fun an_empty_board_loads_nothing_instead_of_the_saved_rows() = runVmTest {
        val harness = Harness()
        harness.showDate()
        val empty = mapOf(SLOT_B to board(SLOT_B, emptyList(), listOf(EXERCISE_B)))
        harness.workoutRepository.boards.value = SplitChoicesState.Ready(empty, confirmed = empty)
        assertNull(harness.viewModel.loadProgram(SLOT_B, emptySet()))
        assertEquals(listOf(EXERCISE_A), harness.setRepository.forDate(DATE).map { it.exerciseId })
    }

    @Test
    fun a_split_cannot_start_while_boards_are_unread_or_its_board_is_saving() = runVmTest {
        val harness = Harness(splitExercises = defaultSplitExercises() + SplitExercise(3, SLOT_B, EXERCISE_C, 1))
        harness.showDate()
        val chosen = mapOf(SLOT_B to board(SLOT_B, listOf(EXERCISE_C)))
        suspend fun programB(startable: Boolean) = withTimeout(TIMEOUT) {
            harness.viewModel.clipboardPrograms.first { programs -> programs.any { it.slotId == SLOT_B && it.startable == startable } }
        }.single { it.slotId == SLOT_B }

        harness.workoutRepository.boards.value = SplitChoicesState.Loading
        assertNull(harness.viewModel.startProposal(DATE, programB(startable = false)))
        harness.workoutRepository.boards.value = SplitChoicesState.Ready(chosen, pendingSlots = setOf(SLOT_B), confirmed = emptyMap())
        val saving = programB(startable = false)
        assertNull(harness.viewModel.startProposal(DATE, saving))
        assertNull(harness.viewModel.loadProgram(SLOT_B, emptySet()))
        assertEquals(listOf(EXERCISE_A), harness.setRepository.forDate(DATE).map { it.exerciseId })

        harness.workoutRepository.boards.value = SplitChoicesState.Ready(chosen, confirmed = chosen)
        val settled = programB(startable = true)
        val outcome = nextOutcome(harness.viewModel)
        val requestId = assertNotNull(harness.viewModel.startProposal(DATE, settled))
        assertEquals(requestId, assertIs<SplitSwitchOutcome.Applied>(outcome.await()).requestId)
        assertEquals(listOf(EXERCISE_C), harness.setRepository.forDate(DATE).map { it.exerciseId })
    }

    private fun board(slotId: Long, inSplit: List<Long>, options: List<Long> = emptyList()) =
        SplitMemberChoice(slotId, 1, EffectiveSplitMembers.of(inSplit, options))

    private fun TestScope.nextOutcome(viewModel: LogViewModel): Deferred<SplitSwitchOutcome> =
        async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(TIMEOUT) { viewModel.splitSwitchOutcomes.first() }
        }

    private class Harness(
        currentDay: WorkoutDay? = WorkoutDay(
            date = DATE,
            cycleSlotType = 0,
            cycleSlotOccurrence = 2,
            cycleSlotId = SLOT_A,
        ),
        currentRows: List<WorkoutSet> = listOf(cleanCurrentA()),
        historyRows: List<WorkoutSet> = defaultHistory(),
        splitExercises: List<SplitExercise> = defaultSplitExercises(),
        cycleStartDate: String? = null,
        priorDays: List<WorkoutDay> = emptyList(),
    ) {
        private val slots = listOf(
            CycleSlot(SLOT_A, "A", 0),
            CycleSlot(SLOT_B, "B", 1),
        )
        private val initialDays = listOfNotNull(
            WorkoutDay(HISTORY_DATE, cycleSlotType = 0, cycleSlotOccurrence = 1, cycleSlotId = SLOT_A),
            currentDay,
        ) + priorDays
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(DATE),
            exercises = listOf(
                Exercise(EXERCISE_A, "Exercise A", "CHEST"),
                Exercise(EXERCISE_B, "Exercise B", "BACK"),
                Exercise(EXERCISE_C, "Exercise C", "LEGS"),
            ),
            sets = historyRows + currentRows,
            settings = UserSettings(),
            cycle = Cycle(numTypes = slots.size, isActive = true, startDate = cycleStartDate),
            slots = slots,
            splitExercises = splitExercises,
            workoutDays = initialDays,
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(historyRows + currentRows),
        )
        private val base = DemoRepos(seed)
        val setRepository = MutableSetRepository(base.sets, historyRows + currentRows)
        val workoutRepository = MutableWorkoutRepository(base.workout, seed.cycle, slots, splitExercises, initialDays)
        val journal = MemoryJournal()
        private var nextPreparationPause: PreparationPause? = null
        private val coordinator = SplitReplacementCoordinator(
            journal = journal,
            replaceSetDayBatch = setRepository::replaceDayBatch,
            enqueueDayBatch = workoutRepository::enqueueDayBatch,
            withDrainLock = { block ->
                val pause = nextPreparationPause.also { nextPreparationPause = null }
                if (pause != null) {
                    pause.entered.complete(Unit)
                    pause.release.await()
                }
                block()
            },
            kickEngine = {},
        )
        val repos: AppRepos = object : AppRepos by base {
            override val sets: SetRepository = setRepository
            override val workout: WorkoutRepository = workoutRepository
            override val splitReplacement: SplitReplacementCoordinator = coordinator
        }
        val viewModel = LogViewModel(repos, derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate)

        suspend fun showDate(date: String = DATE, expectedSetCount: Int = setRepository.forDate(DATE).size) {
            viewModel.selectDate(date)
            withTimeout(TIMEOUT) {
                viewModel.uiState.first { state ->
                    state.currentDate == date && state.exercises.sumOf { it.sets.size } == expectedSetCount
                }
            }
            yield()
        }

        suspend fun pending(requestId: Long): PendingSplitSwitchConfirmation = withTimeout(TIMEOUT) {
            assertNotNull(
                viewModel.pendingSplitSwitch.first { it?.request?.requestId == requestId },
            )
        }

        fun pauseNextPreparation(): PreparationPause = PreparationPause().also {
            check(nextPreparationPause == null)
            nextPreparationPause = it
        }
    }

    private class MutableSetRepository(
        private val delegate: SetRepository,
        initialSets: List<WorkoutSet>,
    ) : SetRepository by delegate {
        private val mutableSets = MutableStateFlow(initialSets.toList())
        override val sets: StateFlow<List<WorkoutSet>> = mutableSets.asStateFlow()
        private var nextId = -1L
        val replacementDates = mutableListOf<String>()

        fun forDate(date: String): List<WorkoutSet> = SetQueries.forDate(mutableSets.value, date)

        fun mutateSet(id: Long, transform: (WorkoutSet) -> WorkoutSet) {
            mutableSets.value = mutableSets.value.map { if (it.id == id) transform(it) else it }
        }

        fun addLocal(set: WorkoutSet) {
            mutableSets.value = mutableSets.value + set
        }

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
        override suspend fun getRecentHistoryForExercise(exerciseId: Long, beforeDate: String): List<WorkoutSet> =
            SetQueries.completedHistoryBeforeDate(mutableSets.value, exerciseId, beforeDate).take(100)

        override suspend fun getMaxWeightBeforeDate(exerciseId: Long, beforeDate: String): Int? =
            SetQueries.maxCompletedWeightBefore(mutableSets.value, exerciseId, beforeDate)

        override suspend fun update(set: WorkoutSet): LocalCommit<WorkoutSet> {
            mutateSet(set.id) { set }
            return LocalCommit.Committed(set)
        }

        override suspend fun updateSet(set: WorkoutSet, observed: WorkoutSet?): LocalCommit<WorkoutSet> {
            return update(set)
        }

        override suspend fun delete(id: Long) {
            mutableSets.value = mutableSets.value.filterNot { it.id == id }
        }

        override suspend fun deleteSet(id: Long) {
            delete(id)
        }

        override suspend fun replaceDayBatch(
            date: String,
            desiredSets: List<WorkoutSet>,
            cardModes: Map<Long, WorkoutCardDisplayMode>,
            precondition: () -> Unit,
        ): LocalCommit<Unit> {
            precondition()
            replacementDates += date
            val inserted = desiredSets.map { it.copy(id = nextId--) }
            mutableSets.value = mutableSets.value.filterNot { it.date == date } + inserted

            return LocalCommit.Committed(Unit)
        }
    }

    private class MutableWorkoutRepository(
        private val delegate: WorkoutRepository,
        cycleValue: Cycle,
        slots: List<CycleSlot>,
        splitExercises: List<SplitExercise>,
        days: List<WorkoutDay>,
    ) : WorkoutRepository by delegate {
        override val cycle: StateFlow<Cycle> = MutableStateFlow(cycleValue).asStateFlow()
        val boards = MutableStateFlow<SplitChoicesState>(SplitChoicesState.Ready(emptyMap(), confirmed = emptyMap()))
        override val splitMemberChoices: StateFlow<SplitChoicesState> = boards
        private val mutableSlots = MutableStateFlow(slots.toList())
        private val mutableSplitExercises = MutableStateFlow(splitExercises.toList())
        private val mutableDays = MutableStateFlow(days.toList())

        fun day(date: String): WorkoutDay? = mutableDays.value.firstOrNull { it.date == date }

        override fun observeWorkoutDay(date: String): Flow<WorkoutDay?> =
            mutableDays.map { rows -> rows.firstOrNull { it.date == date } }

        override fun observeAllWorkoutDays(): Flow<List<WorkoutDay>> = mutableDays
        override fun observeCycleSlots(): Flow<List<CycleSlot>> = mutableSlots
        override suspend fun getCycleSlots(): List<CycleSlot> = mutableSlots.value
        override fun observeSplitExercises(splitId: Long): Flow<List<SplitExercise>> =
            mutableSplitExercises.map { rows -> rows.filter { it.splitId == splitId }.sortedBy { it.orderIndex } }

        override fun observeAllSplitExercises(): Flow<List<SplitExercise>> = mutableSplitExercises

        override suspend fun enqueueDayBatch(op: DayPendingWrite) {
            val day = WorkoutDay(
                date = op.date,
                cycleSlotType = op.cycleSlotType,
                cycleSlotOccurrence = op.cycleSlotOccurrence,
                alternativeForDate = op.alternativeForDate,
                cycleSlotId = op.cycleSlotId,
            )
            mutableDays.value = mutableDays.value.filterNot { it.date == op.date } + day
        }
    }

    private class MemoryJournal : SplitReplacementJournal {
        var entries: List<PreparedSplitReplacement> = emptyList()
        var failNextSave = false

        override fun load(): Result<List<PreparedSplitReplacement>> = Result.success(entries.toList())

        override fun save(replacement: PreparedSplitReplacement): Result<Unit> = runCatching {
            if (failNextSave) {
                failNextSave = false
                error("journal save failed")
            }
            entries = entries.filterNot { it.date == replacement.date } + replacement
        }

        override fun clear(date: String): Result<Unit> = runCatching {
            entries = entries.filterNot { it.date == date }
        }
    }

    private class PreparationPause {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    companion object {
        private const val DATE = "2026-08-14"
        private const val OTHER_DATE = "2026-08-15"
        private const val HISTORY_DATE = "2026-08-01"
        private const val SLOT_A = 1L
        private const val SLOT_B = 2L
        private const val EXERCISE_A = 10L
        private const val EXERCISE_B = 20L
        private const val EXERCISE_C = 30L
        private const val CURRENT_A_SET_ID = 101L
        private const val SECOND_A_SET_ID = 102L
        private const val ADDED_SET_ID = 103L
        private const val TIMEOUT = 2_000L

        private fun cleanCurrentA() = workoutSet(
            id = CURRENT_A_SET_ID,
            exerciseId = EXERCISE_A,
            date = DATE,
            setNumber = 1,
            weightLbs = 100,
            reps = 8,
        )

        private fun defaultHistory() = listOf(
            workoutSet(1, EXERCISE_A, HISTORY_DATE, 1, 100, 8, completed = true),
            workoutSet(2, EXERCISE_B, HISTORY_DATE, 1, 200, 5, completed = true),
            workoutSet(3, EXERCISE_C, HISTORY_DATE, 1, 150, 10, completed = true),
        )

        private fun defaultSplitExercises() = listOf(
            SplitExercise(1, SLOT_A, EXERCISE_A, 0),
            SplitExercise(2, SLOT_B, EXERCISE_B, 0),
        )

        private fun workoutSet(
            id: Long,
            exerciseId: Long,
            date: String,
            setNumber: Int,
            weightLbs: Int? = null,
            reps: Int? = null,
            isBodyweight: Boolean = false,
            completed: Boolean = false,
            restTimeSeconds: Int? = null,
        ) = WorkoutSet(
            id = id,
            exerciseId = exerciseId,
            date = date,
            setNumber = setNumber,
            weightLbs = weightLbs,
            reps = reps,
            isBodyweight = isBodyweight,
            completed = completed,
            restTimeSeconds = restTimeSeconds,
        )
    }
}
