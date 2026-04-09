package com.ayman.ecolift.ui

import androidx.compose.runtime.StateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class ProgressUiState(
    val allExercises: List<Exercise> = emptyList(),
    val selectedExerciseId: Long? = null,
    val dataPoints: List<SessionPoint> = emptyList()
)

data class SessionPoint(
    val dateEpochDay: Long,
    val maxEstimated1RM: Double,
    val totalVolume: Double
)

class ProgressViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val setRepository: SetRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState

    init {
        viewModelScope.launch {
            allExercises = exerciseRepository.getAll()
            selectedExerciseId = if (allExercises.isNotEmpty()) {
                allExercises.firstOrNull()?.id ?: null
            } else {
                null
            }
            loadDataPoints(selectedExerciseId)
        }
    }

    fun selectExercise(id: Long) {
        viewModelScope.launch {
            selectedExerciseId = id
            loadDataPoints(selectedExerciseId)
        }
    }

    suspend fun loadDataPoints(exerciseId: Long?): List<SessionPoint> {
        if (exerciseId == null) return emptyList()

        val sets = setRepository.getSetsForExercise(exerciseId)
        val workoutsByDateEpochDay = sets.groupBy { it.workoutId }.mapValues { (_, sets) ->
            sets.maxOfOrNull { it.dateEpochDay } ?: 0L
        }

        return workoutsByDateEpochDay.map { (workoutId, dateEpochDay) ->
            val setsForWorkout = sets.filter { it.workoutId == workoutId }
            val maxEstimated1RM = setsForWorkout.maxOf { it.weightLb * (1 + it.reps / 30.0) }
            val totalVolume = setsForWorkout.sumOf { it.weightLb * it.reps }
            SessionPoint(dateEpochDay, maxEstimated1RM, totalVolume)
        }.sortedBy { it.dateEpochDay }
    }
}
