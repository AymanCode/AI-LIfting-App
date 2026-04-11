package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.WorkoutDates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val exerciseRepository = ExerciseRepository(database)
    private val setRepository = SetRepository(database)

    private val selectedExercise = MutableStateFlow<Long?>(null)
    val selectedExerciseState: StateFlow<Long?> = selectedExercise

    val uiState: StateFlow<ProgressUiState> = combine(
        exerciseRepository.exercises,
        setRepository.allSets,
        selectedExercise,
    ) { exercises, allSets, selectedExerciseId ->
        val exercisesWithSets = exercises
            .mapNotNull { exercise ->
                val exerciseSets = allSets.filter { it.exerciseId == exercise.id }
                if (exerciseSets.isEmpty()) {
                    null
                } else {
                    ProgressExerciseUi(
                        exerciseId = exercise.id,
                        name = exercise.name,
                        sessions = exerciseSets.map { it.date }.distinct().size,
                    )
                }
            }
            .sortedBy { it.name }
        val effectiveSelected = selectedExerciseId ?: exercisesWithSets.firstOrNull()?.exerciseId
        val selectedExerciseName = exercisesWithSets
            .firstOrNull { it.exerciseId == effectiveSelected }
            ?.name
            .orEmpty()
        val chartPoints = allSets
            .filter { it.exerciseId == effectiveSelected }
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, sets) ->
                ProgressPointUi(
                    date = date,
                    label = WorkoutDates.formatAxis(date),
                    weightLbs = sets.maxOf { it.weightLbs },
                )
            }
        ProgressUiState(
            exercises = exercisesWithSets,
            selectedExerciseId = effectiveSelected,
            selectedExerciseName = selectedExerciseName,
            chartPoints = chartPoints,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProgressUiState(),
    )

    fun selectExercise(exerciseId: Long) {
        selectedExercise.value = exerciseId
    }
}
