package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Cycle
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.UserSettings
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.SplitAlternative
import com.ayman.ecolift.data.splits.SplitSwitchOutcome
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.ui.navigation.clipboardStamp
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.demo.DemoSeedData
import com.ayman.ecolift.web.supabase.ExerciseSwapRepository
import com.ayman.ecolift.web.supabase.ExerciseSwapRequest
import com.ayman.ecolift.web.supabase.ExerciseSwapResult
import com.ayman.ecolift.web.supabase.NoExerciseSwapRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.SplitReplacementCoordinator
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.supabase.sync.DayPendingWrite
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason
import com.ayman.ecolift.web.supabase.sync.PreparedSplitReplacement
import com.ayman.ecolift.web.supabase.sync.SplitReplacementJournal
import com.ayman.ecolift.web.supabase.sync.orThrow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 1 (SetRepository.deleteSets / replaceDayBatch / SplitReplacementCoordinator.restore) has
 * its own tests; this file drives the same harness *pattern* as SplitSwitchViewModelTest to
 * exercise the view model's day snapshot, offer, change watcher and undo behaviour built on top.
 *
 * LogViewModel's `viewModelScope`-launched work runs on a real (non-virtual) dispatcher in this
 * KMP wasmJs test target, so `advanceUntilIdle()` does not reliably drive it to completion (it
 * only drains the TestScope's own virtual scheduler). Every wait below therefore suspends for
 * real via `withTimeout(TIMEOUT) { flow.first { predicate } }`, exactly like
 * SplitSwitchViewModelTest's `showDate`/`pending`/`nextOutcome` helpers already do.
 *
 * Helper classes are named DayUndo* (not Harness/MutableSetRepository/...) because Kotlin/wasm
 * rejects two file-private classes sharing a name in the same package.
 */
class DayUndoViewModelTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun ticket_start_offers_undo_and_undo_empties_the_day() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        h.vm.loadProgram(slotId = UPPER_ID, skippedExerciseIds = emptySet())

        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        assertEquals("Upper Strength started", offer.copy.headline.joinToString("") { it.text } + " " + offer.copy.verb)
        assertTrue(h.setRepository.sets.value.any { it.date == DATE })
        // Seed-only: the day's split is never written by a Start.
        assertNull(h.workoutRepository.observeWorkoutDay(DATE).first()?.cycleSlotId)

        h.vm.undoDayChange(offer.id)
        h.vm.dayUndo.awaitValue { it is DayUndoState.Restored }
        assertEquals(DayUndoState.Restored(offer), h.vm.dayUndo.value)
        h.workoutRepository.observeWorkoutDay(DATE).awaitValue { it?.cycleSlotId == null }

        assertTrue(h.setRepository.sets.value.none { it.date == DATE })
    }

    @Test
    fun ticket_start_seeds_the_included_rows_in_ticket_order_and_assigns_nothing() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        val program = ClipboardProgramUi(
            slotId = UPPER_ID, name = "Upper Strength", position = 0, isExpected = true,
            lifts = listOf(ClipboardProgramLiftUi(EXERCISE_UPPER_1, "Upper One", null), ClipboardProgramLiftUi(EXERCISE_UPPER_2, "Upper Two", null)),
            groupKey = "c:10-20",
            rows = listOf(
                TicketRowUi(EXERCISE_UPPER_1, "Upper One", null, TicketRowKind.MEMBER, null, null, null),
                TicketRowUi(EXERCISE_UPPER_2, "Upper Two", null, TicketRowKind.MEMBER, null, null, null),
                TicketRowUi(EXERCISE_LOWER_1, "Lower One", null, TicketRowKind.TAIL, null, null, null),
            ),
        )
        h.vm.toggleTicketRow(DATE, program, EXERCISE_UPPER_2)
        h.vm.toggleTicketRow(DATE, program, EXERCISE_LOWER_1)
        assertNotNull(h.vm.startProposal(DATE, program))

        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        assertEquals("Upper Strength started", offer.copy.headline.joinToString("") { it.text } + " " + offer.copy.verb)
        assertEquals(listOf(EXERCISE_UPPER_1, EXERCISE_LOWER_1), h.setRepository.forDate(DATE).map { it.exerciseId }.distinct())
        assertNull(h.workoutRepository.observeWorkoutDay(DATE).first()?.cycleSlotId)
    }

    @Test
    fun swaps_survive_day_changes_seed_the_chosen_lift_and_append_without_removing_the_usual_lift() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        val program = ClipboardProgramUi(
            slotId = UPPER_ID, name = "Upper Strength", position = 0, isExpected = true,
            lifts = emptyList(), groupKey = "upper",
            rows = listOf(
                TicketRowUi(EXERCISE_UPPER_1, "Bench Press", "CHEST", TicketRowKind.MEMBER, null, null, null),
                TicketRowUi(EXERCISE_UPPER_2, "Row", "BACK", TicketRowKind.MEMBER, null, null, null),
            ),
        )
        h.vm.swapTicketRow(DATE, program, EXERCISE_UPPER_1, EXERCISE_MANUAL)
        val key = ticketInclusionKey(DATE, program.groupKey)
        h.vm.selectDate("2026-10-05")
        assertNull(h.vm.ticketSwaps.value[ticketInclusionKey("2026-10-05", program.groupKey)])
        h.vm.selectDate(DATE)
        assertEquals(mapOf(EXERCISE_UPPER_1 to EXERCISE_MANUAL), h.vm.ticketSwaps.value[key])
        assertNotNull(h.vm.startProposal(DATE, program))
        h.vm.dayUndo.awaitValue { it != null }
        assertEquals(listOf(EXERCISE_MANUAL, EXERCISE_UPPER_2), h.setRepository.forDate(DATE).map { it.exerciseId }.distinct())
        assertNull(h.workoutRepository.observeWorkoutDay(DATE).first()?.cycleSlotId)
        h.workoutRepository.appendRequests.awaitValue { it.size == 2 }
        assertEquals(listOf(UPPER_ID to EXERCISE_UPPER_1, UPPER_ID to EXERCISE_MANUAL), h.workoutRepository.appendRequests.value)
        assertEquals(listOf(SplitAlternative(UPPER_ID, EXERCISE_UPPER_1, EXERCISE_MANUAL)), h.queuedChoices.value)
        assertEquals(listOf(EXERCISE_UPPER_1, EXERCISE_UPPER_2, EXERCISE_MANUAL), h.workoutRepository.observeSplitExercises(UPPER_ID).first().map { it.exerciseId })
        h.vm.undoTicketSwap(DATE, program, EXERCISE_UPPER_1)
        assertTrue(h.vm.ticketSwaps.value[key].orEmpty().isEmpty())
        h.vm.dropTicketRow(DATE, program, EXERCISE_UPPER_1)
        assertEquals(setOf(EXERCISE_UPPER_2), h.vm.ticketInclusion.value[key])
    }

    @Test
    fun unsaved_proposal_seeds_swaps_without_an_append_or_message() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        val program = ClipboardProgramUi(-1, "Unsaved", 0, true, emptyList(), groupKey = "unsaved",
            rows = listOf(TicketRowUi(EXERCISE_UPPER_1, "Bench Press", "CHEST", TicketRowKind.MEMBER, null, null, null)))
        h.vm.swapTicketRow(DATE, program, EXERCISE_UPPER_1, EXERCISE_MANUAL)
        h.vm.startProposal(DATE, program)
        h.vm.dayUndo.awaitValue { it != null }
        assertEquals(listOf(EXERCISE_MANUAL), h.setRepository.forDate(DATE).map { it.exerciseId }.distinct())
        assertTrue(h.workoutRepository.appendRequests.value.isEmpty())
        assertNull(h.vm.uiState.awaitValue { it.currentDate == DATE }.uiMessage)
    }

    @Test
    fun missing_append_rpc_keeps_the_day_swap_and_reports_one_quiet_message() = runVmTest {
        val h = DayUndoHarness()
        h.workoutRepository.failAppend = true
        h.vm.selectDate(DATE)
        val program = ClipboardProgramUi(UPPER_ID, "Upper", 0, true, emptyList(), groupKey = "upper",
            rows = listOf(TicketRowUi(EXERCISE_UPPER_1, "Bench Press", "CHEST", TicketRowKind.MEMBER, null, null, null)))
        h.vm.swapTicketRow(DATE, program, EXERCISE_UPPER_1, EXERCISE_MANUAL)
        h.vm.startProposal(DATE, program)
        h.vm.uiState.awaitValue { it.uiMessage == "Your workout is saved. Split choices will retry when connected." }
        assertEquals(listOf(EXERCISE_MANUAL), h.setRepository.forDate(DATE).map { it.exerciseId }.distinct())
        assertNotNull(h.vm.dayUndo.value)
        assertEquals(listOf(EXERCISE_UPPER_1, EXERCISE_UPPER_2), h.workoutRepository.observeSplitExercises(UPPER_ID).first().map { it.exerciseId })
    }

    @Test
    fun card_choice_and_undo_use_the_scoped_repository_without_a_day_replacement() = runVmTest {
        val h = DayUndoHarness(currentRows = listOf(
            WorkoutSet(id = 1, exerciseId = EXERCISE_UPPER_1, date = DATE, setNumber = 1, weightLbs = 950, reps = 8, completed = true),
            WorkoutSet(id = 2, exerciseId = EXERCISE_UPPER_2, date = DATE, setNumber = 1, weightLbs = 600, reps = 10),
        ))
        h.vm.selectDate(DATE)
        val model = h.repos.learnedSplits.model.awaitValue { it.groups.any { g -> g.slotId == UPPER_ID } }
        val key = model.groups.first { it.slotId == UPPER_ID }.key
        h.vm.uiState.awaitValue { it.currentDate == DATE && it.exercises.isNotEmpty() }
        val before = h.setRepository.sets.value
        h.vm.useCardLiftToday(DATE, key, EXERCISE_UPPER_2, EXERCISE_MANUAL)
        val offer = assertNotNull(h.vm.exerciseSwapUndo.awaitValue { it != null })
        assertEquals(EXERCISE_UPPER_2, h.swapRequests.single().sourceId)
        assertEquals(EXERCISE_MANUAL, h.swapRequests.single().targetId)
        assertEquals(UPPER_ID, h.swapRequests.single().splitId)
        assertEquals(before, h.setRepository.sets.value)
        assertTrue(h.journal.entries.isEmpty())
        assertTrue(h.workoutRepository.appendRequests.value.isEmpty())
        assertNull(h.vm.dayUndo.value)
        h.vm.undoExerciseSwap { _, _ -> }
        h.vm.exerciseSwapUndo.awaitValue { it == null }
        assertEquals(listOf(offer.requestId), h.undoRequests)
    }

    @Test
    fun loading_a_program_makes_it_the_days_stamp_until_a_set_is_checked() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        // Lower is not the due group; the stamp must name what was loaded, not fall back to the due one.
        h.vm.loadProgram(slotId = LOWER_ID, skippedExerciseIds = emptySet())
        h.vm.dayUndo.awaitValue { it != null }

        assertEquals(LOWER_ID, h.vm.chosenEmptyDaySlot.value[DATE])
        val programs = listOf(
            ClipboardProgramUi(slotId = UPPER_ID, name = "Upper Strength", position = 0, isExpected = true,
                lifts = listOf(ClipboardProgramLiftUi(EXERCISE_UPPER_1, "Upper One", null))),
            ClipboardProgramUi(slotId = LOWER_ID, name = "Lower Strength", position = 1, isExpected = false,
                lifts = listOf(ClipboardProgramLiftUi(EXERCISE_LOWER_1, "Lower One", null))),
        )
        assertEquals("Lower Strength", clipboardStamp(programs, h.vm.chosenEmptyDaySlot.value[DATE], null, dayHasCheckedSet = false)?.name)
    }

    @Test
    fun putting_the_sheet_away_hides_it_for_that_date_only_and_leaves_the_rotation_alone() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        h.vm.uiState.awaitValue { it.currentDate == DATE }
        h.vm.chooseEmptyDaySlot(DATE, LOWER_ID)
        val cycleBefore = h.workoutRepository.cycle.value
        val attributedBefore = h.vm.attributedSlotId.value

        h.vm.putAwayEmptyDay(DATE)

        assertEquals(setOf(DATE), h.vm.putAwayEmptyDay.value)
        assertTrue(REPEATED_DATE !in h.vm.putAwayEmptyDay.value)
        // Nothing else moves: the placed group, inclusion, swaps, the rotation and the day's sets.
        assertEquals(mapOf(DATE to LOWER_ID), h.vm.chosenEmptyDaySlot.value)
        assertTrue(h.vm.ticketInclusion.value.isEmpty())
        assertTrue(h.vm.ticketSwaps.value.isEmpty())
        assertEquals(cycleBefore, h.workoutRepository.cycle.value)
        assertEquals(attributedBefore, h.vm.attributedSlotId.value)
        assertTrue(h.workoutRepository.appendRequests.value.isEmpty())
        assertTrue(h.setRepository.forDate(DATE).isEmpty())

        // Another date is put away on its own; bringing one back clears only that date.
        h.vm.putAwayEmptyDay(REPEATED_DATE)
        h.vm.bringBackEmptyDay(DATE)
        assertEquals(setOf(REPEATED_DATE), h.vm.putAwayEmptyDay.value)
        assertEquals(mapOf(DATE to LOWER_ID), h.vm.chosenEmptyDaySlot.value)
        h.vm.bringBackEmptyDay(REPEATED_DATE)
        assertTrue(h.vm.putAwayEmptyDay.value.isEmpty())
        assertEquals(cycleBefore, h.workoutRepository.cycle.value)
    }

    @Test
    fun undo_after_clear_restores_checked_sets_parked_cards_and_split() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        // A day that already carries a split (Start no longer writes one since learned splits).
        h.vm.assignCycleSlot(slotId = UPPER_ID)
        h.vm.dayUndo.awaitValue { it != null }

        // Check one set and park its card before clearing the split.
        val firstSetId = h.setRepository.sets.value.first { it.date == DATE }.id
        h.vm.toggleCompleted(firstSetId)
        h.setRepository.sets.awaitValue { sets -> sets.any { it.id == firstSetId && it.completed } }
        val cardId = h.setRepository.cards.value.first { it.date == DATE }.id
        h.vm.updateCardDisplayMode(cardId, WorkoutCardDisplayMode.COMPACT)
        h.setRepository.cards.awaitValue { cards -> cards.any { it.id == cardId && it.displayMode == WorkoutCardDisplayMode.COMPACT } }

        val beforeClearSignature = dayContentSignature(DATE, h.setRepository.sets.value, h.setRepository.cards.value)

        val requestId = assertNotNull(h.vm.clearCycleSlot())
        h.pending(requestId)
        h.vm.confirmSplitSwitch(requestId)

        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered && it.offer.copy.verb == "cleared" }).offer
        assertEquals("Upper Strength cleared", offer.copy.headline.joinToString("") { it.text } + " " + offer.copy.verb)
        assertTrue(offer.copy.detail.joinToString("") { it.text }.contains("checked"))

        h.vm.undoDayChange(offer.id)
        h.vm.dayUndo.awaitValue { it is DayUndoState.Restored }
        assertEquals(DayUndoState.Restored(offer), h.vm.dayUndo.value)
        h.workoutRepository.observeWorkoutDay(DATE).awaitValue { it?.cycleSlotId == UPPER_ID }

        assertEquals(beforeClearSignature, dayContentSignature(DATE, h.setRepository.sets.value, h.setRepository.cards.value))
    }

    @Test
    fun checking_a_set_clears_offer() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        h.vm.loadProgram(slotId = UPPER_ID, skippedExerciseIds = emptySet())
        h.vm.dayUndo.awaitValue { it != null }

        val setId = h.setRepository.sets.value.first { it.date == DATE }.id
        h.vm.toggleCompleted(setId)

        h.vm.dayUndo.awaitValue { it == null }
    }

    @Test
    fun date_change_clears_offer() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        h.vm.loadProgram(slotId = UPPER_ID, skippedExerciseIds = emptySet())
        h.vm.dayUndo.awaitValue { it != null }

        h.vm.goToNextDay()

        assertNull(h.vm.dayUndo.value)
    }

    @Test
    fun sync_bookkeeping_keeps_offer() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        h.vm.loadProgram(slotId = UPPER_ID, skippedExerciseIds = emptySet())
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer

        // Simulate a sync engine reconciling temp ids to server ids and bumping row revisions
        // with no content change: no repository method exists in this codebase to do that
        // generically outside a live server round trip, so the fake repository exposes the same
        // id+revision reassignment a real sync pass performs, leaving every other field alone.
        h.setRepository.reconcileIds(DATE)
        // Prove the reassignment actually reached the view model (not just the repository) before
        // asserting a negative, by waiting for the ids the UI renders to become the new, positive
        // server ids.
        h.vm.uiState.awaitValue { state -> state.exercises.any { ex -> ex.sets.any { it.id > 0 } } }

        assertEquals(offer.id, h.vm.dayUndo.value?.offer?.id)
    }

    @Test
    fun undo_runs_once_per_offer() = runVmTest {
        var restoreCalls = 0
        lateinit var h: DayUndoHarness
        h = DayUndoHarness(restoreSetDayBatch = { date, sets, cardModes, validate ->
            restoreCalls++
            h.setRepository.replaceDayBatch(date, sets, cardModes, validate)
        })
        h.vm.selectDate(DATE)
        h.vm.loadProgram(slotId = UPPER_ID, skippedExerciseIds = emptySet())
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer

        h.vm.undoDayChange(offer.id)
        h.vm.undoDayChange(offer.id)
        h.workoutRepository.observeWorkoutDay(DATE).awaitValue { it?.cycleSlotId == null }

        assertEquals(1, restoreCalls)
    }

    @Test
    fun failed_restore_keeps_offer() = runVmTest {
        val h = DayUndoHarness(restoreSetDayBatch = { _, _, _, _ -> throw RuntimeException("boom") })
        h.vm.selectDate(DATE)
        h.vm.loadProgram(slotId = UPPER_ID, skippedExerciseIds = emptySet())
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        val beforeSets = h.setRepository.sets.value.filter { it.date == DATE }

        h.vm.undoDayChange(offer.id)
        val message = h.vm.uiState.awaitValue { it.uiMessage != null }.uiMessage

        assertEquals("Couldn't undo. Try again.", message)
        assertEquals(DayUndoState.Offered(offer), h.vm.dayUndo.value)
        assertEquals(beforeSets, h.setRepository.sets.value.filter { it.date == DATE })
        assertTrue(h.journal.entries.isEmpty())
    }

    @Test
    fun cancelled_confirmation_offers_nothing() = runVmTest {
        val h = DayUndoHarness(
            currentDay = WorkoutDay(date = DATE, cycleSlotType = 0, cycleSlotOccurrence = 1, cycleSlotId = UPPER_ID),
            currentRows = listOf(
                WorkoutSet(
                    id = SEED_SET_ID,
                    exerciseId = EXERCISE_UPPER_1,
                    date = DATE,
                    setNumber = 1,
                    weightLbs = 100,
                    reps = 8,
                    completed = true,
                    syncId = "seed-1",
                ),
            ),
        )
        h.vm.selectDate(DATE)
        h.vm.uiState.awaitValue { it.currentDate == DATE }

        val requestId = assertNotNull(h.vm.loadProgram(slotId = LOWER_ID, skippedExerciseIds = emptySet()))
        h.pending(requestId)
        val outcome = nextOutcomeOn(h.vm)

        h.vm.cancelSplitSwitch(requestId)

        assertIs<SplitSwitchOutcome.Cancelled>(outcome.await())
        assertNull(h.vm.dayUndo.value)
        // A load the user backed out of does not rename the day's stamp.
        assertNull(h.vm.chosenEmptyDaySlot.value[DATE])
    }

    @Test
    fun offer_lookup_failure_does_not_break_the_split_switch_outcome() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        h.workoutRepository.throwOnGetCycleSlots = true
        val outcome = nextOutcomeOn(h.vm)

        val requestId = assertNotNull(h.vm.loadProgram(slotId = UPPER_ID, skippedExerciseIds = emptySet()))

        assertEquals(requestId, assertIs<SplitSwitchOutcome.Applied>(outcome.await()).requestId)
        assertTrue(h.setRepository.sets.value.any { it.date == DATE })
        assertNull(h.vm.dayUndo.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun delete_sets_publishes_session_once() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        h.vm.loadProgram(slotId = UPPER_ID, skippedExerciseIds = emptySet())
        h.vm.dayUndo.awaitValue { it != null }
        val ids = h.setRepository.sets.value.filter { it.date == DATE && it.exerciseId == EXERCISE_UPPER_1 }.map { it.id }
        assertTrue(ids.isNotEmpty())

        var emissionCount = 0
        // UNDISPATCHED so the collector reaches its first (baseline) emission synchronously,
        // before deleteSets runs, instead of merely being scheduled.
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.uiState.collect { emissionCount++ }
        }
        val baseline = emissionCount

        h.vm.deleteSets(ids)
        h.setRepository.sets.awaitValue { sets -> sets.none { it.id in ids } }
        h.vm.uiState.awaitValue { state -> state.exercises.none { it.exerciseId == EXERCISE_UPPER_1 } }
        // deleteSets updates uiState before this collector, which lives on the test scheduler, runs.
        runCurrent()
        job.cancel()

        assertTrue(
            emissionCount - baseline in 1..2,
            "expected 1-2 uiState emissions from deleteSets (one local edit + one repository echo), got ${emissionCount - baseline}",
        )
    }

    @Test
    fun retry_after_failed_restore_restores_snapshot() = runVmTest {
        var attempts = 0
        lateinit var h: DayUndoHarness
        h = DayUndoHarness(restoreSetDayBatch = { date, sets, modes, validate ->
            if (++attempts == 1) LocalCommit.NotCommitted(NotCommittedReason.QuotaExceeded)
            else h.setRepository.replaceDayBatch(date, sets, modes, validate)
        })
        h.vm.selectDate(DATE)
        h.vm.loadProgram(UPPER_ID, emptySet())
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        h.vm.undoDayChange(offer.id)
        h.vm.uiState.awaitValue { it.uiMessage == "Couldn't undo. Try again." }
        assertEquals(DayUndoState.Offered(offer), h.vm.dayUndo.value)
        h.vm.undoDayChange(offer.id)
        h.vm.dayUndo.awaitValue { it is DayUndoState.Restored }
        assertEquals(emptyList(), h.setRepository.forDate(DATE))
        assertEquals(2, attempts)
    }

    @Test
    fun watcher_ignores_restoring_offer_and_double_tap_runs_one_restore() = runVmTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        lateinit var h: DayUndoHarness
        h = DayUndoHarness(restoreSetDayBatch = { date, sets, modes, validate ->
            requests++
            val result = h.setRepository.replaceDayBatch(date, sets, modes, validate)
            entered.complete(Unit)
            release.await()
            result
        })
        h.vm.selectDate(DATE)
        h.vm.loadProgram(UPPER_ID, emptySet())
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        h.vm.undoDayChange(offer.id)
        entered.await()
        h.vm.uiState.awaitValue { it.exercises.isEmpty() }
        h.vm.undoDayChange(offer.id)
        assertEquals(DayUndoState.Restoring(offer), h.vm.dayUndo.value)
        assertEquals(1, requests)
        release.complete(Unit)
        h.vm.dayUndo.awaitValue { it is DayUndoState.Restored }
        assertEquals(DayUndoState.Restored(offer), h.vm.dayUndo.value)
    }

    @Test
    fun check_while_restore_waits_cancels_restore() = runVmTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var hold = false
        val h = DayUndoHarness(withDrainLock = { block ->
            if (hold) { entered.complete(Unit); release.await() }
            block()
        })
        h.vm.selectDate(DATE)
        h.vm.loadProgram(UPPER_ID, emptySet())
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        val original = h.setRepository.forDate(DATE)
        hold = true
        h.vm.undoDayChange(offer.id)
        entered.await()
        val later = original.last().copy(completed = true)
        h.setRepository.updateSet(later).orThrow()
        release.complete(Unit)
        h.vm.dayUndo.awaitValue { it == null }
        assertEquals(original.dropLast(1) + later, h.setRepository.forDate(DATE))
        assertEquals(emptyList(), h.journal.entries)
        assertNull(h.vm.uiState.value.uiMessage)
    }

    @Test
    fun date_change_during_restore_skips_the_undone_beat() = runVmTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        lateinit var h: DayUndoHarness
        h = DayUndoHarness(restoreSetDayBatch = { date, sets, modes, validate ->
            entered.complete(Unit)
            release.await()
            h.setRepository.replaceDayBatch(date, sets, modes, validate)
        })
        h.vm.selectDate(DATE)
        h.vm.loadProgram(UPPER_ID, emptySet())
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        h.vm.undoDayChange(offer.id)
        entered.await()
        h.vm.goToNextDay()
        release.complete(Unit)
        h.setRepository.sets.awaitValue { rows -> rows.none { it.date == DATE } }
        assertNull(h.vm.dayUndo.value)
    }

    @Test
    fun edit_after_failed_restore_clears_the_retained_offer() = runVmTest {
        val h = DayUndoHarness(restoreSetDayBatch = { _, _, _, _ -> LocalCommit.NotCommitted(NotCommittedReason.QuotaExceeded) })
        h.vm.selectDate(DATE)
        h.vm.loadProgram(UPPER_ID, emptySet())
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        h.vm.undoDayChange(offer.id)
        h.vm.uiState.awaitValue { it.uiMessage == "Couldn't undo. Try again." }
        assertEquals(DayUndoState.Offered(offer), h.vm.dayUndo.value)
        h.setRepository.updateSet(h.setRepository.forDate(DATE).first().copy(reps = 21)).orThrow()
        h.vm.dayUndo.awaitValue { it == null }
        assertEquals(21, h.setRepository.forDate(DATE).first().reps)
    }

    @Test
    fun delete_added_not_committed_keeps_rows_and_offer_then_retries() = runVmTest {
        val h = DayUndoHarness()
        h.vm.selectDate(DATE)
        h.vm.uiState.awaitValue { it.currentDate == DATE }
        h.vm.addClipboardPicks(listOf(ClipboardPickRequest(ClipboardPick.Existing(EXERCISE_UPPER_1, "Upper One"))))
        val offer = assertNotNull(h.vm.dayUndo.awaitValue { it is DayUndoState.Offered }).offer
        assertIs<DayUndoRestore.DeleteAdded>(offer.restore)
        val added = h.setRepository.forDate(DATE)
        h.setRepository.deleteFailure = NotCommittedReason.QuotaExceeded
        h.vm.undoDayChange(offer.id)
        h.vm.uiState.awaitValue { it.uiMessage == "Couldn't undo. Try again." }
        assertEquals(added, h.setRepository.forDate(DATE))
        assertEquals(DayUndoState.Offered(offer), h.vm.dayUndo.value)
        h.setRepository.deleteFailure = null
        h.vm.undoDayChange(offer.id)
        h.vm.dayUndo.awaitValue { it is DayUndoState.Restored }
        assertEquals(emptyList(), h.setRepository.forDate(DATE))
        assertEquals(DayUndoState.Restored(offer), h.vm.dayUndo.value)
    }

    private suspend fun <T> Flow<T>.awaitValue(predicate: (T) -> Boolean): T =
        withTimeout(TIMEOUT) { first(predicate) }

    private suspend fun DayUndoHarness.pending(requestId: Long): PendingSplitSwitchConfirmation =
        withTimeout(TIMEOUT) { assertNotNull(vm.pendingSplitSwitch.first { it?.request?.requestId == requestId }) }

    private fun TestScope.nextOutcomeOn(vm: LogViewModel): Deferred<SplitSwitchOutcome> =
        async(start = CoroutineStart.UNDISPATCHED) { withTimeout(TIMEOUT) { vm.splitSwitchOutcomes.first() } }

    private class DayUndoHarness(
        currentDay: WorkoutDay? = null,
        currentRows: List<WorkoutSet> = emptyList(),
        restoreSetDayBatch: (suspend (String, List<WorkoutSet>, Map<Long, WorkoutCardDisplayMode>, () -> Unit) -> LocalCommit<*>)? = null,
        withDrainLock: suspend (suspend () -> Unit) -> Unit = { block -> block() },
    ) {
        private val slots = listOf(
            CycleSlot(UPPER_ID, "Upper Strength", 0),
            CycleSlot(LOWER_ID, "Lower Strength", 1),
        )
        private val splitExercises = listOf(
            SplitExercise(1, UPPER_ID, EXERCISE_UPPER_1, 0),
            SplitExercise(2, UPPER_ID, EXERCISE_UPPER_2, 1),
            SplitExercise(3, LOWER_ID, EXERCISE_LOWER_1, 0),
        )
        private val initialDays = listOfNotNull(currentDay)
        private val seed = DemoSeedData(
            anchorDate = LocalDate.parse(DATE),
            exercises = listOf(
                Exercise(EXERCISE_UPPER_1, "Bench Press", "CHEST"),
                Exercise(EXERCISE_UPPER_2, "Row", "BACK"),
                Exercise(EXERCISE_LOWER_1, "Squat", "LEGS"),
                Exercise(EXERCISE_MANUAL, "Manual Lift", "SHOULDERS"),
                Exercise(EXERCISE_REPEAT_1, "Repeat Lift One", "ARMS"),
                Exercise(EXERCISE_REPEAT_2, "Repeat Lift Two", "CORE"),
            ),
            sets = currentRows,
            settings = UserSettings(),
            cycle = Cycle(numTypes = slots.size, isActive = true, startDate = DATE),
            slots = slots,
            splitExercises = splitExercises,
            workoutDays = initialDays,
            archives = emptyList(),
            cardio = emptyList(),
            pendingReviews = emptyList(),
            usageCounts = SetQueries.usageCounts(currentRows),
        )
        private val base = DemoRepos(seed)
        val setRepository = DayUndoFakeSetRepository(base.sets, currentRows)
        val workoutRepository = DayUndoFakeWorkoutRepository(base.workout, seed.cycle, slots, splitExercises, initialDays)
        val journal = DayUndoFakeJournal()
        private val coordinator = SplitReplacementCoordinator(
            journal = journal,
            replaceSetDayBatch = setRepository::replaceDayBatch,
            enqueueDayBatch = workoutRepository::enqueueDayBatch,
            withDrainLock = withDrainLock,
            kickEngine = {},
            restoreSetDayBatch = restoreSetDayBatch ?: setRepository::replaceDayBatch,
        )
        val queuedChoices = MutableStateFlow<List<SplitAlternative>>(emptyList())
        val swapRequests = mutableListOf<ExerciseSwapRequest>()
        val undoRequests = mutableListOf<String>()
        private val swapRepository = object : ExerciseSwapRepository by NoExerciseSwapRepository {
            override fun queueAlternatives(choices: List<SplitAlternative>) { queuedChoices.value += choices }
            override suspend fun flushAlternatives() {
                for (choice in queuedChoices.value) {
                    workoutRepository.appendSplitExercise(choice.splitId, choice.sourceId)
                    workoutRepository.appendSplitExercise(choice.splitId, choice.alternativeId)
                }
            }
            override suspend fun swap(request: ExerciseSwapRequest): ExerciseSwapResult {
                swapRequests += request
                return ExerciseSwapResult(true, "APPLIED", request.requestId, request.sourceId, request.targetId)
            }
            override suspend fun undo(requestId: String): ExerciseSwapResult {
                undoRequests += requestId
                return ExerciseSwapResult(true, "UNDONE", requestId)
            }
        }
        val repos: AppRepos = object : AppRepos by base {
            override val exerciseSwaps: ExerciseSwapRepository = swapRepository
            override val sets: SetRepository = setRepository
            override val workout: WorkoutRepository = workoutRepository
            override val splitReplacement: SplitReplacementCoordinator = coordinator
        }
        val vm = LogViewModel(repos, isOnline = { false })
    }

    private class DayUndoFakeSetRepository(
        private val delegate: SetRepository,
        initialSets: List<WorkoutSet>,
    ) : SetRepository by delegate {
        private val mutableSets = MutableStateFlow(initialSets.toList())
        override val sets: StateFlow<List<WorkoutSet>> = mutableSets.asStateFlow()
        private val mutableCards = MutableStateFlow<List<WorkoutExerciseCard>>(emptyList())
        override val cards: StateFlow<List<WorkoutExerciseCard>> = mutableCards.asStateFlow()
        var deleteFailure: NotCommittedReason? = null
        private var nextId = -1L
        private var nextSyncId = 0L
        private var nextServerId = 1_000L

        fun forDate(date: String): List<WorkoutSet> = SetQueries.forDate(mutableSets.value, date)

        fun addLocal(set: WorkoutSet) {
            mutableSets.value = mutableSets.value + set
        }

        fun mutateSet(id: Long, transform: (WorkoutSet) -> WorkoutSet) {
            mutableSets.value = mutableSets.value.map { if (it.id == id) transform(it) else it }
        }

        /** Reassigns ids and bumps revisions with no content change, like a sync reconciliation. */
        fun reconcileIds(date: String) {
            mutableSets.value = mutableSets.value.map { set ->
                if (set.date == date) set.copy(id = nextServerId++, rowRevision = set.rowRevision + 1) else set
            }
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
            deleteSets(listOf(id))
        }

        override suspend fun deleteSets(ids: List<Long>, precondition: () -> Unit): LocalCommit<Unit> {
            precondition()
            deleteFailure?.let { return LocalCommit.NotCommitted(it) }
            val targets = ids.toSet()
            mutableSets.value = mutableSets.value.filterNot { it.id in targets }

            return LocalCommit.Committed(Unit)
        }

        override suspend fun updateCardDisplayMode(cardId: String, displayMode: WorkoutCardDisplayMode): LocalCommit<Unit> {
            mutableCards.value = mutableCards.value.map { if (it.id == cardId) it.copy(displayMode = displayMode) else it }

            return LocalCommit.Committed(Unit)
        }

        override suspend fun addExerciseSession(
            date: String,
            exerciseId: Long,
            isBodyweightDefault: Boolean,
        ): LocalCommit<List<WorkoutSet>> {
            val existingForExercise = forDate(date).count { it.exerciseId == exerciseId }
            val newSet = WorkoutSet(
                id = nextId--,
                exerciseId = exerciseId,
                date = date,
                setNumber = existingForExercise + 1,
                weightLbs = 45,
                reps = 8,
                isBodyweight = isBodyweightDefault,
                syncId = "sync-${nextSyncId++}",
            )
            mutableSets.value = mutableSets.value + newSet
            return LocalCommit.Committed(listOf(newSet))
        }

        override suspend fun replaceDayBatch(
            date: String,
            desiredSets: List<WorkoutSet>,
            cardModes: Map<Long, WorkoutCardDisplayMode>,
            precondition: () -> Unit,
        ): LocalCommit<Unit> {
            precondition()
            val inserted = desiredSets.map { it.copy(id = nextId--, syncId = "sync-${nextSyncId++}") }
            mutableSets.value = mutableSets.value.filterNot { it.date == date } + inserted
            val order = desiredSets.map { it.exerciseId }.distinct()
            val newCards = order.mapIndexed { index, exerciseId ->
                WorkoutExerciseCard(
                    id = "card-$date-$exerciseId",
                    date = date,
                    exerciseId = exerciseId,
                    position = index,
                    displayMode = cardModes[exerciseId] ?: WorkoutCardDisplayMode.EXPANDED,
                    origin = WorkoutCardOrigin.SPLIT,
                )
            }
            mutableCards.value = mutableCards.value.filterNot { it.date == date } + newCards

            return LocalCommit.Committed(Unit)
        }
    }

    private class DayUndoFakeWorkoutRepository(
        private val delegate: WorkoutRepository,
        cycleValue: Cycle,
        slots: List<CycleSlot>,
        splitExercises: List<SplitExercise>,
        days: List<WorkoutDay>,
    ) : WorkoutRepository by delegate {
        override val cycle: StateFlow<Cycle> = MutableStateFlow(cycleValue).asStateFlow()
        private val mutableSlots = MutableStateFlow(slots.toList())
        private val mutableSplitExercises = MutableStateFlow(splitExercises.toList())
        private val mutableDays = MutableStateFlow(days.toList())
        val appendRequests = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())
        var failAppend = false

        override suspend fun appendSplitExercise(slotId: Long, exerciseId: Long) {
            appendRequests.value = appendRequests.value + (slotId to exerciseId)
            if (failAppend) error("append_split_exercise_v1 is missing")
            val rows = mutableSplitExercises.value
            val existing = rows.firstOrNull { it.splitId == slotId && it.exerciseId == exerciseId }
            mutableSplitExercises.value = if (existing != null) rows.map { if (it.id == existing.id) it.copy(userAdded = true) else it }
                else rows + SplitExercise(rows.size.toLong() + 1, slotId, exerciseId,
                    rows.filter { it.splitId == slotId }.maxOfOrNull { it.orderIndex }?.plus(1) ?: 0, userAdded = true)
        }

        override fun observeWorkoutDay(date: String): Flow<WorkoutDay?> =
            mutableDays.map { rows -> rows.firstOrNull { it.date == date } }

        override fun observeAllWorkoutDays(): Flow<List<WorkoutDay>> = mutableDays
        override fun observeCycleSlots(): Flow<List<CycleSlot>> = mutableSlots

        /** Lets a test make `offerSplitSwitchUndo`'s lookup fail without disturbing
         *  `buildCanonicalSplitSwitch`, which reads slots through [observeCycleSlots] instead. */
        var throwOnGetCycleSlots = false

        override suspend fun getCycleSlots(): List<CycleSlot> {
            if (throwOnGetCycleSlots) error("getCycleSlots failed")
            return mutableSlots.value
        }

        override suspend fun getSlotByIdIncludingArchived(id: Long): CycleSlot? =
            mutableSlots.value.firstOrNull { it.id == id }

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

    private class DayUndoFakeJournal : SplitReplacementJournal {
        var entries: List<PreparedSplitReplacement> = emptyList()

        override fun load(): Result<List<PreparedSplitReplacement>> = Result.success(entries.toList())

        override fun save(replacement: PreparedSplitReplacement): Result<Unit> = runCatching {
            entries = entries.filterNot { it.date == replacement.date } + replacement
        }

        override fun clear(date: String): Result<Unit> = runCatching {
            entries = entries.filterNot { it.date == date }
        }
    }

    companion object {
        private const val DATE = "2026-12-01"
        private const val REPEATED_DATE = "2026-09-23"
        private const val UPPER_ID = 1L
        private const val LOWER_ID = 2L
        private const val EXERCISE_UPPER_1 = 10L
        private const val EXERCISE_UPPER_2 = 20L
        private const val EXERCISE_LOWER_1 = 25L
        private const val EXERCISE_MANUAL = 30L
        private const val EXERCISE_REPEAT_1 = 40L
        private const val EXERCISE_REPEAT_2 = 50L
        private const val SEED_SET_ID = 101L
        private const val MANUAL_SET_ID = 102L
        private const val TIMEOUT = 2_000L
    }
}
