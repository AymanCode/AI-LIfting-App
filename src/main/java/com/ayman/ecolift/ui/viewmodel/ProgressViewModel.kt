package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val exerciseRepository = ExerciseRepository(database.exerciseDao())
    private val setRepository = SetRepository(database)

    private val selectedExerciseId = MutableStateFlow<Long?>(null)
    private val timeframe = MutableStateFlow(TimeframeFilter.THREE_MONTHS)
    private val selectedMetric = MutableStateFlow(ProgressMetric.ESTIMATED_1RM)
    
    private val userBodyWeight = 180 

    private val _exerciseList = MutableStateFlow<List<ProgressExerciseUi>>(emptyList())
    
    init {
        viewModelScope.launch {
            combine(
                exerciseRepository.exercises,
                setRepository.observeExerciseProgressSummaries()
            ) { _, summaries ->
                val now = LocalDate.now()
                val thirtyDaysAgo = now.minusDays(30)
                val sixtyDaysAgo = now.minusDays(60)

                val last30Volumes = setRepository.getVolumesSince(thirtyDaysAgo.toString()).associateBy { it.exerciseId }
                val prev60Volumes = setRepository.getVolumesSince(sixtyDaysAgo.toString()).associateBy { it.exerciseId }
                val lastSessionSetsByDate = setRepository
                    .getSetsForDates(summaries.map { it.lastSessionDate }.distinct())
                    .groupBy { it.date }

                summaries.map { summary ->
                    val last30Vol = last30Volumes[summary.exerciseId]?.volume?.toInt() ?: 0
                    val total60Vol = prev60Volumes[summary.exerciseId]?.volume?.toInt() ?: 0
                    val prev30Vol = total60Vol - last30Vol
                    val change = if (prev30Vol > 0) (last30Vol - prev30Vol).toFloat() / prev30Vol else 0f

                    val trend = setRepository.getVolumeHistory(summary.exerciseId, 10).map { it.volume.toInt() }.reversed()
                    val lastSets = lastSessionSetsByDate[summary.lastSessionDate].orEmpty()
                        .filter { it.exerciseId == summary.exerciseId }
                    val lastSet = lastSets.maxByOrNull { it.weightLbs ?: 0 }

                    ProgressExerciseUi(
                        exerciseId = summary.exerciseId,
                        name = summary.exerciseName,
                        sessions = summary.sessionCount,
                        lastSessionDate = WorkoutDates.formatAxis(summary.lastSessionDate),
                        lastSessionSummary = if (lastSet != null) "${lastSet.weightLbs ?: 0} x ${lastSet.reps ?: 0}" else "No sets",
                        changePercentage = change * 100,
                        trend = trend
                    )
                }.sortedBy { it.name }
            }.collect {
                _exerciseList.value = it
            }
        }
    }

    private val detailState = combine(
        selectedExerciseId,
        timeframe,
        selectedMetric
    ) { id, filter, metric ->
        Triple(id, filter, metric)
    }.flatMapLatest { (id: Long?, filter: TimeframeFilter, _: ProgressMetric) ->
        if (id == null) {
            MutableStateFlow<DetailData?>(null)
        } else {
            val now = LocalDate.now()
            val sinceDate = when (filter) {
                TimeframeFilter.ONE_MONTH -> now.minusMonths(1)
                TimeframeFilter.THREE_MONTHS -> now.minusMonths(3)
                TimeframeFilter.SIX_MONTHS -> now.minusMonths(6)
                TimeframeFilter.ONE_YEAR -> now.minusYears(1)
                TimeframeFilter.ALL_TIME -> LocalDate.of(2000, 1, 1)
            }
            
            combine(
                exerciseRepository.exercises,
                flow<List<WorkoutSet>> { emit(setRepository.getSetsSince(id, sinceDate.toString())) }
            ) { exercises: List<com.ayman.ecolift.data.Exercise>, filteredSets: List<WorkoutSet> ->
                val exercise = exercises.find { it.id == id }
                val isBodyweight = exercise?.isBodyweight ?: false
                
                val chartPoints = filteredSets
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

                // Stats calculation (last 30 vs prev 30)
                val thirtyDaysAgo = now.minusDays(30)
                val sixtyDaysAgo = now.minusDays(60)
                
                // We need more data for stats than the filtered timeframe might provide
                val allExerciseSets = setRepository.getRecentHistoryForExercise(id, sixtyDaysAgo.toString()) 
                
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

                val stats = ProgressStatsUi(
                    currentPr = "$currentPr",
                    currentPrDelta = if (prevPr > 0) (currentPr - prevPr).toFloat() / prevPr * 100 else 0f,
                    est1Rm = String.format(Locale.US, "%.1f", current1RM),
                    est1RmDelta = if (prev1RM > 0) (current1RM - prev1RM) / prev1RM * 100 else 0f,
                    totalVolume = formatVolume(calculateSessionVolume(last30Sets, isBodyweight)),
                    volumeDelta = calculateVolumeDelta(last30Sets, prev30Sets, isBodyweight),
                    workoutCount = last30Sets.map { it.date }.distinct().size,
                    workoutCountDelta = last30Sets.map { it.date }.distinct().size - prev30Sets.map { it.date }.distinct().size
                )

                DetailData(
                    exerciseName = exercise?.name.orEmpty(),
                    isBodyweight = isBodyweight,
                    chartPoints = chartPoints,
                    stats = stats
                )
            }
        }
    }

    val uiState: StateFlow<ProgressUiState> = combine(
        _exerciseList,
        selectedExerciseId,
        timeframe,
        selectedMetric,
        detailState
    ) { exercises: List<ProgressExerciseUi>, selectedId: Long?, filter: TimeframeFilter, metric: ProgressMetric, detail: DetailData? ->
        ProgressUiState(
            exercises = exercises,
            selectedExerciseId = selectedId,
            selectedExerciseName = detail?.exerciseName.orEmpty(),
            isBodyweight = detail?.isBodyweight ?: false,
            chartPoints = detail?.chartPoints ?: emptyList(),
            timeframe = filter,
            selectedMetric = metric,
            stats = detail?.stats
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

private data class DetailData(
    val exerciseName: String,
    val isBodyweight: Boolean,
    val chartPoints: List<ProgressPointUi>,
    val stats: ProgressStatsUi
)
