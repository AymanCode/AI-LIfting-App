package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.UserVisibleSyncException
import com.ayman.ecolift.web.supabase.sync.LocalCommit
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogViewModelLocalCommitTest {
    private val rows = listOf(
        WorkoutSet(101, 10, "2026-08-14", 1, 1000, 8, syncId = "commit-set-1"),
        WorkoutSet(102, 10, "2026-08-14", 2, 1000, 6, syncId = "commit-set-2"),
    )

    private suspend fun show(vm: LogViewModel) {
        vm.selectDate("2026-08-14")
        withTimeout(5_000) { vm.uiState.first { it.exercises.sumOf { exercise -> exercise.sets.size } == 2 } }
    }

    /** Subscribes before the refusal. The strip is the only message, so this stays suspended. */
    private fun TestScope.listenForSnackbar(): Deferred<UserVisibleSyncException> =
        async(start = CoroutineStart.UNDISPATCHED) { SyncErrors.events.first() }

    private fun TestScope.assertStripWithoutSnackbar(pending: Deferred<UserVisibleSyncException>) {
        advanceUntilIdle()
        assertTrue(pending.isActive)
        pending.cancel()
    }

    @Test fun failed_check_keeps_the_unchecked_row_next_values_and_one_failure() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        data.updateFailure = NotCommittedReason.QuotaExceeded
        val snackbar = listenForSnackbar()
        vm.toggleCompleted(101)
        val unsaved = withTimeout(5_000) { vm.unsavedSetInputs.first { it.isNotEmpty() } }
        assertEquals(listOf(NotCommittedReason.QuotaExceeded), unsaved.values.map { it.reason })
        assertStripWithoutSnackbar(snackbar)
        val state = withTimeout(5_000) { vm.uiState.first { it.exercises.first().sets.first().completed == false } }
        assertEquals(false, state.exercises.first().sets.first().completed)
        assertEquals(rows, data.currentSets.value)
        assertEquals(emptyList(), data.updates)
        assertNull(vm.restStopwatch.value)
    }

    @Test fun failed_uncheck_keeps_the_checked_row_and_existing_rest() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        vm.toggleCompleted(101)
        withTimeout(5_000) { data.currentSets.first { it.first().completed } }
        val rest = withTimeout(5_000) { vm.restStopwatch.first { it != null } }
        data.updateFailure = NotCommittedReason.StorageUnavailable
        vm.toggleCompleted(101)
        withTimeout(5_000) { vm.unsavedSetInputs.first { it.isNotEmpty() } }
        assertEquals(rows.first().copy(completed = true), data.currentSets.value.first())
        assertEquals(rows.last(), data.currentSets.value.last())
        assertNotNull(rest)
        assertEquals(rest?.exerciseId, vm.restStopwatch.value?.exerciseId)
    }

    @Test fun a_refused_rep_edit_is_kept_as_unsaved_input_outside_the_session_rows_until_a_retry_saves_it() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        data.updateFailure = NotCommittedReason.StorageUnavailable
        vm.updateReps(101, "12")

        val unsaved = withTimeout(5_000) { vm.unsavedSetInputs.first { it.isNotEmpty() } }
        assertEquals(listOf(12), unsaved.values.map { it.attempted.reps })
        assertEquals(listOf(NotCommittedReason.StorageUnavailable), unsaved.values.map { it.reason })
        val state = withTimeout(5_000) { vm.uiState.first { it.exercises.first().sets.first().reps == 8 } }
        assertEquals(listOf(8, 6), state.exercises.first().sets.map { it.reps })
        assertEquals(rows, data.currentSets.value)

        data.updateFailure = null
        vm.retryUnsavedSet(unsaved.keys.single())
        withTimeout(5_000) { vm.unsavedSetInputs.first { it.isEmpty() } }
        assertEquals(12, data.currentSets.value.first().reps)
    }

    @Test fun failed_scrub_restores_the_value_before_the_first_step() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        data.updateFailure = NotCommittedReason.QuotaExceeded
        vm.adjustWeight(101, 50)
        vm.adjustWeight(101, 50)
        vm.flushPendingSetCommits()
        withTimeout(5_000) { vm.unsavedSetInputs.first { it.isNotEmpty() } }
        val state = withTimeout(5_000) { vm.uiState.first { it.exercises.first().sets.first().weightLbs == 1000 } }
        assertEquals(1000, state.exercises.first().sets.first().weightLbs)
        assertEquals(rows, data.currentSets.value)
    }

    @Test fun failed_batch_delete_returns_the_result_for_the_screen_to_restore_hidden_rows() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        data.deleteFailure = NotCommittedReason.QuotaExceeded
        val snackbar = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { SyncErrors.events.first() }
        }
        val result = CompletableDeferred<LocalCommit<Unit>>()
        vm.deleteSets(listOf(101, 102)) { result.complete(it) }
        assertEquals(LocalCommit.NotCommitted(NotCommittedReason.QuotaExceeded), withTimeout(5_000) { result.await() })
        assertEquals("Set not saved · device storage full", snackbar.await().message)
        assertEquals(rows, data.currentSets.value)
        assertEquals(emptyList(), data.deletedIds.value)
    }

    @Test fun completion_focus_callback_waits_for_commit_and_failure_does_not_invoke_it() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        val gate = CompletableDeferred<Unit>()
        data.updateGate = gate
        data.updateFailure = NotCommittedReason.QuotaExceeded
        val focused = mutableListOf<Long>()
        vm.toggleCompleted(101) { focused += 102 }
        withTimeout(5_000) { vm.uiState.first { it.exercises.first().sets.first().completed } }
        assertEquals(emptyList(), focused)
        assertEquals(rows, data.currentSets.value)
        gate.complete(Unit)
        withTimeout(5_000) { vm.unsavedSetInputs.first { it.isNotEmpty() } }
        assertEquals(emptyList(), focused)
        assertEquals(rows, data.currentSets.value)
    }

    @Test fun committed_completion_invokes_focus_once_after_the_repository_changed() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        val gate = CompletableDeferred<Unit>()
        data.updateGate = gate
        val observed = CompletableDeferred<List<WorkoutSet>>()
        var calls = 0
        vm.toggleCompleted(101) {
            calls++
            observed.complete(data.currentSets.value)
        }
        withTimeout(5_000) { vm.uiState.first { it.exercises.first().sets.first().completed } }
        assertEquals(0, calls)
        gate.complete(Unit)
        assertEquals(listOf(rows.first().copy(completed = true), rows.last()), withTimeout(5_000) { observed.await() })
        assertEquals(1, calls)
    }

    @Test fun a_retry_that_saves_clears_only_its_own_input_and_keeps_a_newer_refused_edit() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        data.updateFailure = NotCommittedReason.StorageUnavailable
        vm.updateReps(101, "12")
        val key = withTimeout(5_000) { vm.unsavedSetInputs.first { it.isNotEmpty() } }.keys.single()
        val gate = CompletableDeferred<Unit>()
        data.updateGate = gate
        data.updateFailure = null
        vm.retryUnsavedSet(key)

        data.updateGate = null
        data.updateFailure = NotCommittedReason.StorageUnavailable
        vm.updateReps(101, "14")
        withTimeout(5_000) { vm.unsavedSetInputs.first { it[key]?.attempted?.reps == 14 } }
        data.updateFailure = null
        gate.complete(Unit)
        withTimeout(5_000) { data.currentSets.first { it.first().reps == 12 } }

        assertEquals(listOf(14), withTimeout(5_000) { vm.unsavedSetInputs.first { it.isNotEmpty() } }.values.map { it.attempted.reps })
    }

    @Test fun a_refused_check_runs_its_completion_step_only_when_the_retry_saves() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val data = ViewModelMutationTestRepos(rows)
        val vm = LogViewModel(data.repos, isOnline = { false })
        show(vm)
        data.updateFailure = NotCommittedReason.QuotaExceeded
        val focused = mutableListOf<Long>()
        vm.toggleCompleted(101) { focused += 102 }
        val unsaved = withTimeout(5_000) { vm.unsavedSetInputs.first { it.isNotEmpty() } }
        assertEquals(listOf(true), unsaved.values.map { it.attempted.completed })
        assertEquals(emptyList(), focused)
        assertEquals(rows, data.currentSets.value)

        data.updateFailure = null
        vm.retryUnsavedSet(unsaved.keys.single())
        withTimeout(5_000) { vm.unsavedSetInputs.first { it.isEmpty() } }

        assertEquals(listOf(102L), focused)
        assertEquals(true, data.currentSets.value.first().completed)
    }
}
