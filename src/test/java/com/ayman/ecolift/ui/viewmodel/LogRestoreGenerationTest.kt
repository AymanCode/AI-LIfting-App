package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.DataGeneration
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogRestoreGenerationTest {
    @BeforeTest
    fun setUp() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @AfterTest
    fun tearDown() {
        CurrentAppMode.mode = AppMode.Authenticated
    }

    @Test
    fun pending_scrub_does_not_overwrite_a_restored_set() = runVmTest {
        val harness = Harness()
        harness.showDate()

        harness.vm.adjustWeight(SET_ID, delta = 50)
        harness.generation.replace {
            harness.data.currentSets.value = listOf(harness.restored)
        }
        advanceTimeBy(STEP_COMMIT_DELAY_MS)
        runCurrent()

        assertEquals(RESTORED_WEIGHT, harness.data.currentSets.value.single().weightLbs)
        assertEquals(RESTORED_WEIGHT, harness.shownWeight())
    }

    @Test
    fun immediate_edit_waiting_on_the_repository_does_not_overwrite_a_restored_set() = runVmTest {
        val harness = Harness()
        harness.showDate()
        harness.data.updateGate = CompletableDeferred()

        harness.vm.toggleWarmup(SET_ID)
        val replacement = async {
            harness.generation.replace {
                harness.data.currentSets.value = listOf(harness.restored)
            }
        }
        yield()
        harness.data.updateGate?.complete(Unit)
        replacement.await()

        assertEquals(RESTORED_WEIGHT, harness.data.currentSets.value.single().weightLbs)
        assertFalse(harness.data.currentSets.value.single().isWarmup)
    }

    @Test
    fun restore_drops_a_retained_retry_and_leaves_the_restored_set() = runVmTest {
        val harness = Harness()
        harness.showDate()
        harness.data.updateFailure = NotCommittedReason.StorageUnavailable

        harness.vm.toggleWarmup(SET_ID)
        withTimeout(TIMEOUT) { harness.vm.unsavedSetInputs.first { it.isNotEmpty() } }
        harness.data.updateFailure = null
        harness.generation.replace {
            harness.data.currentSets.value = listOf(harness.restored)
        }
        harness.vm.unsavedSetInputs.value.keys.forEach { harness.vm.retryUnsavedSet(it) }
        runCurrent()

        assertTrue(harness.vm.unsavedSetInputs.value.isEmpty())
        assertEquals(RESTORED_WEIGHT, harness.data.currentSets.value.single().weightLbs)
        assertFalse(harness.data.currentSets.value.single().isWarmup)
    }

    private class Harness {
        val restored = ORIGINAL.copy(weightLbs = RESTORED_WEIGHT)
        val data = ViewModelMutationTestRepos(listOf(ORIGINAL))
        val generation = DataGeneration()
        private val repos: AppRepos = object : AppRepos by data.repos {
            override val dataGeneration = generation
        }
        val vm = LogViewModel(
            repos,
            isOnline = { false },
            derivationContext = kotlinx.coroutines.Dispatchers.Main.immediate,
        )

        suspend fun showDate() {
            vm.selectDate(DATE)
            withTimeout(TIMEOUT) {
                vm.uiState.first { it.currentDate == DATE && it.exercises.sumOf { exercise -> exercise.sets.size } == 1 }
            }
        }

        fun shownWeight(): Int? = vm.uiState.value.exercises.single().sets.single().weightLbs
    }

    private companion object {
        const val DATE = "2026-08-14"
        const val SET_ID = 101L
        const val RESTORED_WEIGHT = 1000
        const val TIMEOUT = 2_000L
        val ORIGINAL = WorkoutSet(
            id = SET_ID,
            exerciseId = 10L,
            date = DATE,
            setNumber = 1,
            weightLbs = 1350,
            reps = 8,
            syncId = "restore-set-1",
        )
    }
}
