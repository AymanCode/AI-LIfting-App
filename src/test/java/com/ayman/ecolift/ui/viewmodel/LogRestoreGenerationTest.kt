package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.agent.assist.SetSuggestion
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.DataGeneration
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNull
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

    @Test
    fun restore_clears_hints_and_the_rest_timer() = runVmTest {
        val harness = Harness()
        harness.showDate()
        seedHints(harness.vm)

        val before = harness.vm.splitSwitchTransientStateSnapshot()
        assertEquals(SET_ID, before.activeRestSetId)
        assertEquals(setOf(SET_ID), before.assistSetIds)
        assertEquals(setOf(SET_ID), before.warningSetIds)
        assertEquals(setOf(SET_ID), before.adjustmentSetIds)
        assertEquals(setOf(EXERCISE_ID), before.recommendationExerciseIds)
        assertEquals(EXERCISE_ID, harness.vm.restStopwatch.value?.exerciseId)

        harness.generation.replace { }
        runCurrent()

        val after = harness.vm.splitSwitchTransientStateSnapshot()
        assertNull(after.activeRestSetId)
        assertTrue(after.assistSetIds.isEmpty())
        assertTrue(after.warningSetIds.isEmpty())
        assertTrue(after.adjustmentSetIds.isEmpty())
        assertTrue(after.recommendationExerciseIds.isEmpty())
        assertNull(harness.vm.restStopwatch.value)
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

    private fun seedHints(vm: LogViewModel) {
        setFlow(
            vm,
            "_smartAdjustments",
            mapOf(
                SET_ID to SmartSetAdjustment(
                    setId = SET_ID,
                    sourceSetId = SET_ID,
                    suggestedWeightLbs = 1400,
                    hasWeightSuggestion = true,
                    suggestedIsBodyweight = false,
                    suggestedReps = 8,
                ),
            ),
        )
        setFlow(
            vm,
            "_assistSuggestions",
            mapOf(
                SET_ID to SetSuggestion(
                    weightStored = 1400,
                    reps = 8,
                    confidence = WeightSuggestion.Confidence.HIGH,
                    localReason = "last session",
                ),
            ),
        )
        setFlow(vm, "_inputWarnings", mapOf(SET_ID to "That weight is far from the last set"))
        setFlow(vm, "_recommendationStates", mapOf(EXERCISE_ID to RecommendationState.Done))
        val restClass = Class.forName("com.ayman.ecolift.ui.viewmodel.ActiveRest")
        val rest = restClass.declaredConstructors.first { it.parameterCount == 3 }
            .apply { isAccessible = true }
            .newInstance(EXERCISE_ID, SET_ID, 1_000L)
        LogViewModel::class.java.getDeclaredField("activeRest").apply { isAccessible = true }.set(vm, rest)
        setFlow(vm, "_restStopwatch", RestStopwatch(exerciseId = EXERCISE_ID, elapsedSeconds = 12))
    }

    private fun setFlow(vm: LogViewModel, name: String, value: Any?) {
        val field = LogViewModel::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as MutableStateFlow<Any?>
        flow.value = value
    }

    private companion object {
        const val DATE = "2026-08-14"
        const val SET_ID = 101L
        const val EXERCISE_ID = 10L
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
