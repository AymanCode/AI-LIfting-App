package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val exerciseRepository = ExerciseRepository(database)
    private val setRepository = SetRepository(database)

    private val selectedExerciseId = MutableStateFlow<Long?>(null)
    private val timeframe = MutableStateFlow(TimeframeFilter.THREE_MONTHS)
    
    // Constant for now, could be in a UserSettings repository later
    private val userBodyWeight = 180 

    val uiState: StateFlow<ProgressUiState> = combine(
        exerciseRepository.exercises,
        setRepository.allSets,
        selectedExerciseId,
        timeframe
    ) { exercises, allSets, selectedId, filter ->
        val exercisesWithSets = exercises
            .mapNotNull { exercise ->
                val exerciseSets = allSets.filter { it.exerciseId == exercise.id }
                if (exerciseSets.isEmpty()) {
                    null
                } else {
                    val sortedSets = exerciseSets.sortedByDescending { it.date }
                    val sessions = exerciseSets.map { it.date }.distinct()
                    val trend = sessions.take(5).reversed().map { date ->
                        calculateSessionVolume(exerciseSets.filter { it.date == date }, exercise.isBodyweight)
                    }
                    ProgressExerciseUi(
                        exerciseId = exercise.id,
                        name = exercise.name,
                        sessions = sessions.size,
                        lastSessionDate = WorkoutDates.formatAxis(sortedSets.first().date),
                        trend = trend
                    )
                }
            }
            .sortedBy { it.name }

        val effectiveSelected = selectedId 
        val selectedExercise = exercises.find { it.id == effectiveSelected }
        val selectedExerciseName = selectedExercise?.name.orEmpty()
        val isBodyweight = selectedExercise?.isBodyweight ?: false

        val filteredSets = allSets.filter { it.exerciseId == effectiveSelected }
            .filter { set ->
                val setDate = LocalDate.parse(set.date)
                when (filter) {
                    TimeframeFilter.THREE_MONTHS -> setDate.isAfter(LocalDate.now().minusMonths(3))
                    TimeframeFilter.YTD -> setDate.isAfter(LocalDate.now().withDayOfYear(1).minusDays(1))
                    TimeframeFilter.ALL_TIME -> true
                }
            }

        val chartPoints = if (selectedId == null) emptyList() else filteredSets
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, sets) ->
                val sessionVolume = calculateSessionVolume(sets, isBodyweight)
                val maxSet = sets.maxByOrNull { it.weightLbs } ?: sets.first()
                val est1RM = if (isBodyweight) {
                    (maxSet.weightLbs + userBodyWeight) * (1 + maxSet.reps / 30f)
                } else {
                    maxSet.weightLbs * (1 + maxSet.reps / 30f)
                }
                
                ProgressPointUi(
                    date = date,
                    label = WorkoutDates.formatAxis(date),
                    volume = sessionVolume,
                    estimated1RM = est1RM,
                    maxWeight = sets.maxOf { it.weightLbs },
                    maxReps = sets.maxOf { it.reps }
                )
            }

        ProgressUiState(
            exercises = exercisesWithSets,
            selectedExerciseId = effectiveSelected,
            selectedExerciseName = selectedExerciseName,
            isBodyweight = isBodyweight,
            chartPoints = chartPoints,
            timeframe = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProgressUiState(),
    )

    private fun calculateSessionVolume(sets: List<WorkoutSet>, isBodyweight: Boolean): Int {
        return sets.sumOf { 
            val effectiveWeight = if (isBodyweight) it.weightLbs + userBodyWeight else it.weightLbs
            effectiveWeight * it.reps 
        }
    }

    fun selectExercise(exerciseId: Long?) {
        selectedExerciseId.value = exerciseId
    }

    fun setTimeframe(filter: TimeframeFilter) {
        timeframe.value = filter
    }
}
