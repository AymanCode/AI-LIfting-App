package com.ayman.ecolift.ui

import androidx.compose.runtime.StateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.WorkoutRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.ui.navigation.TodayScreen.Companion.totalSetCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class TodayUiState(
    val workoutId: Long? = null,
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val showCreateOption: Boolean = false,
    val recentExercises: List<Exercise> = emptyList(),
    val activeExercise: Exercise? = null,
    val weight: Double = 135.0,
    val reps: Int = 8,
    val groupedSets: List<ExerciseGroup> = emptyList()
)

data class ExerciseGroup(
    val exercise: Exercise,
    val sets: List<WorkoutSet>
)

class TodayViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val setRepository: SetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState

    init {
        viewModelScope.launch {
            workoutId = workoutRepository.getOrCreateTodayWorkout().id
            recentExercises = exerciseRepository.getAll()
            setRepository.getSetsForWorkout(workoutId).collect { groupedSets ->
                _uiState.value = uiState.value.copy(groupedSets = groupedSets)
            }
        }
    }

    fun onSearchChange(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.value = uiState.value.copy(
                    searchQuery = query,
                    searchResults = emptyList(),
                    showCreateOption = false
                )
            } else {
                val results = exerciseRepository.match(query, exerciseRepository.getAll())
                _uiState.value = uiState.value.copy(
                    searchQuery = query,
                    searchResults = results.take(3),
                    showCreateOption = !results.any { it.canonicalName.lowercase() == query.trim().lowercase() }
                )
            }
        }
    }

    fun selectExercise(exercise: Exercise) {
        viewModelScope.launch {
            _uiState.value = uiState.value.copy(
                activeExercise = exercise,
                searchQuery = "",
                searchResults = emptyList(),
                showCreateOption = false,
                weight = 135.0,
                reps = 8
            )
        }
    }

    fun createAndSelectExercise(name: String) {
        viewModelScope.launch {
            val newExercise = exerciseRepository.createExercise(name)
            selectExercise(newExercise)
        }
    }

    fun clearActiveExercise() {
        viewModelScope.launch {
            _uiState.value = uiState.value.copy(activeExercise = null)
        }
    }

    fun incrementWeight(long: Boolean) {
        viewModelScope.launch {
            val newWeight = if (long) weight + 25.0 else weight + 5.0
            _uiState.value = uiState.value.copy(weight = newWeight.clamp(0.0, Double.MAX_VALUE))
        }
    }

    fun decrementWeight(long: Boolean) {
        viewModelScope.launch {
            val newWeight = if (long) weight - 25.0 else weight - 5.0
            _uiState.value = uiState.value.copy(weight = newWeight.clamp(0.0, Double.MAX_VALUE))
        }
    }

    fun incrementReps(long: Boolean) {
        viewModelScope.launch {
            val newReps = if (long) reps + 5 else reps + 1
            _uiState.value = uiState.value.copy(reps = newReps.clamp(0, Int.MAX_VALUE))
        }
    }

    fun decrementReps(long: Boolean) {
        viewModelScope.launch {
            val newReps = if (long) reps - 5 else reps - 1
            _uiState.value = uiState.value.copy(reps = newReps.clamp(0, Int.MAX_VALUE))
        }
    }

    fun logSet() {
        viewModelScope.launch {
            requireNotNull(workoutId)
            requireNotNull(activeExercise)
            setRepository.logSet(workoutId, activeExercise.id, weight, reps)
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            setRepository.deleteSet(setId)
        }
    }

    fun endWorkout() {
        viewModelScope.launch {
            workoutRepository.endWorkout(workoutId)
            workoutId = workoutRepository.getOrCreateTodayWorkout().id
        }
    }
}
