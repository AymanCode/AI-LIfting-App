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
import java.util.Locale

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val exerciseRepository = ExerciseRepository(database)
    private val setRepository = SetRepository(database)

    private val selectedExerciseId = MutableStateFlow<Long?>(null)
    private val timeframe = MutableStateFlow(TimeframeFilter.THREE_MONTHS)
    private val selectedMetric = MutableStateFlow(ProgressMetric.ESTIMATED_1RM)
    
    private val userBodyWeight = 180 

    val uiState: StateFlow<ProgressUiState> = combine(
        exerciseRepository.exercises,
        setRepository.allSets,
        selectedExerciseId,
        timeframe,
        selectedMetric
    ) { exercises, allSets, selectedId, filter, metric ->
        val now = LocalDate.now()
        val thirtyDaysAgo = now.minusDays(30)
        val sixtyDaysAgo = now.minusDays(60)

        val exercisesWithSets = exercises
            .mapNotNull { exercise ->
                val exerciseSets = allSets.filter { it.exerciseId == exercise.id }
                if (exerciseSets.isEmpty()) {
                    null
                } else {
                    val sortedSets = exerciseSets.sortedByDescending { it.date }
                    val lastSet = sortedSets.first()
                    val sessions = exerciseSets.map { it.date }.distinct()
                    
                    val last30DaysSets = exerciseSets.filter { LocalDate.parse(it.date).isAfter(thirtyDaysAgo) }
                    val prev30DaysSets = exerciseSets.filter { 
                        val d = LocalDate.parse(it.date)
                        d.isAfter(sixtyDaysAgo) && d.isBefore(thirtyDaysAgo.plusDays(1))
                    }
                    
                    val last30Volume = calculateSessionVolume(last30DaysSets, exercise.isBodyweight)
                    val prev30Volume = calculateSessionVolume(prev30DaysSets, exercise.isBodyweight)
                    val change = if (prev30Volume > 0) (last30Volume - prev30Volume).toFloat() / prev30Volume else 0f

                    ProgressExerciseUi(
                        exerciseId = exercise.id,
                        name = exercise.name,
                        sessions = sessions.size,
                        lastSessionDate = WorkoutDates.formatAxis(lastSet.date),
                        lastSessionSummary = "${lastSet.weightLbs ?: 0} x ${lastSet.reps ?: 0}",
                        changePercentage = change * 100,
                        trend = sessions.take(10).reversed().map { date ->
                            calculateSessionVolume(exerciseSets.filter { it.date == date }, exercise.isBodyweight)
                        }
                    )
                }
            }
            .sortedBy { it.name }

        val selectedExercise = exercises.find { it.id == selectedId }
        val isBodyweight = selectedExercise?.isBodyweight ?: false

        val filteredSets = allSets.filter { it.exerciseId == selectedId }
            .filter { set ->
                val setDate = LocalDate.parse(set.date)
                when (filter) {
                    TimeframeFilter.ONE_MONTH -> setDate.isAfter(now.minusMonths(1))
                    TimeframeFilter.THREE_MONTHS -> setDate.isAfter(now.minusMonths(3))
                    TimeframeFilter.SIX_MONTHS -> setDate.isAfter(now.minusMonths(6))
                    TimeframeFilter.ONE_YEAR -> setDate.isAfter(now.minusYears(1))
                    TimeframeFilter.ALL_TIME -> true
                }
            }

        val chartPoints = if (selectedId == null) emptyList() else filteredSets
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, sets) ->
                val maxSet = sets.maxByOrNull { it.weightLbs ?: 0 } ?: sets.first()
                ProgressPointUi(
                    date = date,
                    label = WorkoutDates.formatAxis(date),
                    volume = calculateSessionVolume(sets, isBodyweight),
                    estimated1RM = calc1RM(maxSet.weightLbs ?: 0, maxSet.reps ?: 0, isBodyweight),
                    maxWeight = sets.maxOf { it.weightLbs ?: 0 },
                    maxReps = sets.maxOf { it.reps ?: 0 },
                    reps = maxSet.reps ?: 0
                )
            }

        val stats = if (selectedId != null) {
            val allExerciseSets = allSets.filter { it.exerciseId == selectedId }
            val last30Sets = allExerciseSets.filter { LocalDate.parse(it.date).isAfter(thirtyDaysAgo) }
            val prev30Sets = allExerciseSets.filter { 
                val d = LocalDate.parse(it.date)
                d.isAfter(sixtyDaysAgo) && d.isBefore(thirtyDaysAgo.plusDays(1))
            }

            val currentPr = allExerciseSets.maxOfOrNull { it.weightLbs ?: 0 } ?: 0
            val prevPr = allExerciseSets.filter { LocalDate.parse(it.date).isBefore(thirtyDaysAgo) }.maxOfOrNull { it.weightLbs ?: 0 } ?: currentPr
            
            val latestSets = last30Sets.groupBy { it.date }.values.lastOrNull() ?: emptyList()
            val latestMaxSet = latestSets.maxByOrNull { it.weightLbs ?: 0 }
            val current1RM = latestMaxSet?.let { calc1RM(it.weightLbs ?: 0, it.reps ?: 0, isBodyweight) } ?: 0f
            
            val prevMaxSet = prev30Sets.maxByOrNull { it.weightLbs ?: 0 }
            val prev1RM = prevMaxSet?.let { calc1RM(it.weightLbs ?: 0, it.reps ?: 0, isBodyweight) } ?: current1RM

            ProgressStatsUi(
                currentPr = "$currentPr",
                currentPrDelta = if (prevPr > 0) (currentPr - prevPr).toFloat() / prevPr * 100 else 0f,
                est1Rm = String.format(Locale.US, "%.1f", current1RM),
                est1RmDelta = if (prev1RM > 0) (current1RM - prev1RM) / prev1RM * 100 else 0f,
                totalVolume = formatVolume(calculateSessionVolume(last30Sets, isBodyweight)),
                volumeDelta = calculateVolumeDelta(last30Sets, prev30Sets, isBodyweight),
                workoutCount = last30Sets.map { it.date }.distinct().size,
                workoutCountDelta = last30Sets.map { it.date }.distinct().size - prev30Sets.map { it.date }.distinct().size
            )
        } else null

        ProgressUiState(
            exercises = exercisesWithSets,
            selectedExerciseId = selectedId,
            selectedExerciseName = selectedExercise?.name.orEmpty(),
            isBodyweight = isBodyweight,
            chartPoints = chartPoints,
            timeframe = filter,
            selectedMetric = metric,
            stats = stats
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProgressUiState(),
    )

    private fun calc1RM(weight: Int, reps: Int, isBodyweight: Boolean): Float {
        val w = if (isBodyweight) weight + userBodyWeight else weight
        return w * (1 + reps / 30f)
    }

    private fun calculateSessionVolume(sets: List<WorkoutSet>, isBodyweight: Boolean): Int {
        return sets.sumOf { 
            val weight = it.weightLbs ?: 0
            val reps = it.reps ?: 0
            val effectiveWeight = if (isBodyweight) weight + userBodyWeight else weight
            effectiveWeight * reps
        }
    }

    private fun calculateVolumeDelta(current: List<WorkoutSet>, prev: List<WorkoutSet>, isBodyweight: Boolean): Float {
        val curVol = calculateSessionVolume(current, isBodyweight)
        val preVol = calculateSessionVolume(prev, isBodyweight)
        return if (preVol > 0) (curVol - preVol).toFloat() / preVol * 100 else 0f
    }

    private fun formatVolume(volume: Int): String {
        return if (volume >= 1000) String.format(Locale.US, "%.1fk", volume / 1000f) else volume.toString()
    }

    fun selectExercise(exerciseId: Long?) { selectedExerciseId.value = exerciseId }
    fun setTimeframe(filter: TimeframeFilter) { timeframe.value = filter }
    fun setMetric(metric: ProgressMetric) { selectedMetric.value = metric }
}
