package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.ai.ExerciseMuscleClassifier
import com.ayman.ecolift.test.runVmTest
import com.ayman.ecolift.web.AppMode
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.CurrentAppMode
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.demo.DemoGenAiEngine
import com.ayman.ecolift.web.demo.DemoRepos
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.SyncErrors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MuscleGroupFailureTest {
    @Test
    fun a_failed_muscle_group_change_on_progress_says_so() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 7, 5)))
        val failing = object : ExerciseRepository by base.exercises {
            override suspend fun updateMuscleGroups(id: Long, muscleGroups: String) = error("network down")
        }
        val viewModel = ProgressViewModel(
            repos = object : AppRepos by base { override val exercises: ExerciseRepository = failing },
            engine = DemoGenAiEngine(),
        )
        val exerciseId = base.exercises.getAll().first().id

        val change = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { SyncErrors.events.first() } }
        viewModel.updateExerciseMuscleGroup(exerciseId, "CHEST")
        assertEquals("Couldn't change the muscle group.", change.await().message)
    }

    @Test
    fun a_failed_muscle_group_change_on_log_says_so() = runVmTest {
        CurrentAppMode.mode = AppMode.Authenticated
        val base = DemoRepos(DemoData.generate(LocalDate(2026, 7, 5)))
        // The Log's own startup backfill also calls updateMuscleGroups (for unclassified lifts
        // only), so the fake fails for one already-classified lift and the test taps that one.
        val target = base.exercises.getAll().first { !ExerciseMuscleClassifier.shouldClassify(it) }
        val failing = object : ExerciseRepository by base.exercises {
            override suspend fun updateMuscleGroups(id: Long, muscleGroups: String) {
                if (id == target.id) error("network down") else base.exercises.updateMuscleGroups(id, muscleGroups)
            }
        }
        val viewModel = LogViewModel(
            object : AppRepos by base { override val exercises: ExerciseRepository = failing },
            isOnline = { false },
        )

        val message = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) { viewModel.uiState.first { it.uiMessage != null }.uiMessage }
        }
        viewModel.updateExerciseMuscleGroup(target.id, "CHEST")
        assertEquals("Couldn't change the muscle group.", message.await())
    }
}
