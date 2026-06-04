package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.ExerciseRepository
import com.ayman.ecolift.data.SetRepository
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutRepository
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val exerciseRepository = ExerciseRepository(database.exerciseDao())
    private val setRepository = SetRepository(database)
    private val workoutRepository = WorkoutRepository(database)

    private val selectedExerciseId = MutableStateFlow<Long?>(null)
    private val timeframe = MutableStateFlow(TimeframeFilter.ONE_MONTH)
    private val selectedMetric = MutableStateFlow(ProgressMetric.ESTIMATED_1RM)
    private val organizationMode = MutableStateFlow(ProgressOrganizationMode.PROGRESS)
    private val searchQuery = MutableStateFlow("")
    private val selectedSplitIndex = MutableStateFlow(0)
    
    private val historyStartDate = LocalDate.of(2000, 1, 1).toString()
    private val userBodyweightLbs: StateFlow<Int?> = workoutRepository.observeUserBodyweightLbs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _exerciseList = MutableStateFlow<List<ProgressExerciseUi>>(emptyList())

    private val splitSources = combine(
        workoutRepository.observeCycleSlots(),
        workoutRepository.observeAllSplitExercises(),
        workoutRepository.observeAllWorkoutDays(),
    ) { slots, rows, days ->
        val slotIds = slots.map { it.id }.toSet()
        val assignedSlotDays = days.filter { it.cycleSlotId != null && it.cycleSlotId in slotIds }
        val completedSetsByDate = setRepository
            .getCompletedSetsForDates(assignedSlotDays.map { it.date }.distinct())
            .groupBy { it.date }
        val latestDates = days
            .filter { it.cycleSlotId != null && it.cycleSlotId in slotIds && completedSetsByDate.containsKey(it.date) }
            .groupBy { it.cycleSlotId!! }
            .values
            .mapNotNull { splitDays -> splitDays.maxByOrNull(WorkoutDay::date)?.date }
            .distinct()
        val setsByDate = latestDates.associateWith { completedSetsByDate[it].orEmpty() }
        buildProgressSplitSources(
            slots = slots,
            savedRows = rows,
            workoutDays = days,
            setsByDate = setsByDate,
        )
    }
    
    init {
        viewModelScope.launch {
            combine(
                exerciseRepository.exercises,
                setRepository.observeExerciseProgressSummaries(),
                userBodyweightLbs,
            ) { _, summaries, userBodyweight ->
                val lastSessionSetsByDate = setRepository
                    .getCompletedSetsForDates(summaries.map { it.lastSessionDate }.distinct())
                    .groupBy { it.date }

                summaries.map { summary ->
                    val historySets = setRepository.getSetsSince(summary.exerciseId, historyStartDate)
                    val trend = buildProgressChartPoints(
                        filteredSets = historySets,
                        isBodyweight = summary.isBodyweight,
                        userBodyWeight = userBodyweight,
                    ).takeLast(10).map { it.volume }

                    val latestVol = trend.lastOrNull()?.toFloat() ?: 0f
                    val prevVol = if (trend.size >= 2) trend[trend.size - 2].toFloat() else 0f
                    val change = if (prevVol > 0f) (latestVol - prevVol) / prevVol else 0f

                    val lastSets = lastSessionSetsByDate[summary.lastSessionDate].orEmpty()
                        .filter { it.exerciseId == summary.exerciseId }
                    val lastSet = lastSets.maxByOrNull {
                        effectiveLoadStored(it, summary.isBodyweight, userBodyweight)
                    }

                    ProgressExerciseUi(
                        exerciseId = summary.exerciseId,
                        name = summary.exerciseName,
                        sessions = summary.sessionCount,
                        lastSessionDate = WorkoutDates.formatAxis(summary.lastSessionDate),
                        lastSessionSummary = lastSet?.let {
                            formatProgressSetLabel(it, summary.isBodyweight)
                        } ?: "No sets",
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
        userBodyweightLbs,
    ) { id, filter, userBodyweight ->
        Triple(id, filter, userBodyweight)
    }.flatMapLatest { (id: Long?, filter: TimeframeFilter, userBodyweight: Int?) ->
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
                setRepository.observeSetsSince(id, sinceDate.toString()),
                setRepository.observeSetsSince(id, historyStartDate)
            ) { exercises: List<com.ayman.ecolift.data.Exercise>, filteredSets: List<WorkoutSet>, allTimeSets: List<WorkoutSet> ->
                val exercise = exercises.find { it.id == id }
                val isBodyweight = exercise?.isBodyweight ?: false

                val chartPoints = buildProgressChartPoints(
                    filteredSets = filteredSets,
                    isBodyweight = isBodyweight,
                    userBodyWeight = userBodyweight
                )

                DetailData(
                    exerciseName = exercise?.name.orEmpty(),
                    isBodyweight = isBodyweight,
                    chartPoints = chartPoints,
                    stats = buildProgressStats(
                        allTimeSets = allTimeSets,
                        timeframe = filter,
                        isBodyweight = isBodyweight,
                        userBodyWeight = userBodyweight,
                        now = now
                    )
                )
            }
        }
    }

    private val listState = combine(
        _exerciseList,
        organizationMode,
        searchQuery,
        selectedSplitIndex,
        splitSources,
    ) { exercises, mode, query, splitIndex, splits ->
        val visibleExercises = organizeProgressExercises(exercises, query)
        val splitPages = buildProgressSplitPages(exercises, splits, query)
        val normalizedSplitIndex = normalizeProgressSplitIndex(splitIndex, splitPages.size)
        ProgressListData(
            exercises = exercises,
            organizationMode = mode,
            searchQuery = query,
            visibleExercises = visibleExercises,
            splitPages = splitPages,
            selectedSplitIndex = normalizedSplitIndex,
        )
    }

    val uiState: StateFlow<ProgressUiState> = combine(
        listState,
        selectedExerciseId,
        timeframe,
        selectedMetric,
        detailState
    ) { list: ProgressListData, selectedId: Long?, filter: TimeframeFilter, metric: ProgressMetric, detail: DetailData? ->
        ProgressUiState(
            exercises = list.exercises,
            organizationMode = list.organizationMode,
            searchQuery = list.searchQuery,
            visibleExercises = list.visibleExercises,
            splitPages = list.splitPages,
            selectedSplitIndex = list.selectedSplitIndex,
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

    fun selectExercise(exerciseId: Long?) { selectedExerciseId.value = exerciseId }
    fun setTimeframe(filter: TimeframeFilter) { timeframe.value = filter }
    fun setMetric(metric: ProgressMetric) { selectedMetric.value = metric }
    fun setOrganizationMode(mode: ProgressOrganizationMode) { organizationMode.value = mode }
    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setSelectedSplitIndex(index: Int) { selectedSplitIndex.value = index }
    fun showPreviousSplit() {
        selectedSplitIndex.value = normalizeProgressSplitIndex(
            index = selectedSplitIndex.value - 1,
            pageCount = uiState.value.splitPages.size,
        )
    }
    fun showNextSplit() {
        selectedSplitIndex.value = normalizeProgressSplitIndex(
            index = selectedSplitIndex.value + 1,
            pageCount = uiState.value.splitPages.size,
        )
    }
}

private data class DetailData(
    val exerciseName: String,
    val isBodyweight: Boolean,
    val chartPoints: List<ProgressPointUi>,
    val stats: ProgressStatsUi
)

private data class ProgressListData(
    val exercises: List<ProgressExerciseUi>,
    val organizationMode: ProgressOrganizationMode,
    val searchQuery: String,
    val visibleExercises: List<ProgressExerciseUi>,
    val splitPages: List<ProgressSplitPageUi>,
    val selectedSplitIndex: Int,
)

internal data class ProgressSplitSource(
    val splitId: Long,
    val name: String,
    val exerciseIds: List<Long>,
)

internal fun buildProgressSplitSources(
    slots: List<CycleSlot>,
    savedRows: List<SplitExercise>,
    workoutDays: List<WorkoutDay>,
    setsByDate: Map<String, List<WorkoutSet>>,
): List<ProgressSplitSource> {
    val rowsBySplit = savedRows.groupBy { it.splitId }
    val latestDayBySplit = workoutDays
        .filter { it.cycleSlotId != null }
        .groupBy { it.cycleSlotId!! }
        .mapValues { (_, splitDays) -> splitDays.maxByOrNull(WorkoutDay::date) }

    return slots.map { slot ->
        val savedExerciseIds = rowsBySplit[slot.id].orEmpty()
            .sortedBy { it.orderIndex }
            .map { it.exerciseId }
            .distinct()
        val latestSessionExerciseIds = latestDayBySplit[slot.id]?.date
            ?.let { date -> setsByDate[date].orEmpty().map(WorkoutSet::exerciseId).distinct() }
            .orEmpty()

        ProgressSplitSource(
            splitId = slot.id,
            name = slot.name,
            exerciseIds = savedExerciseIds.ifEmpty { latestSessionExerciseIds },
        )
    }
}

internal fun organizeProgressExercises(
    exercises: List<ProgressExerciseUi>,
    searchQuery: String,
): List<ProgressExerciseUi> {
    val query = searchQuery.trim()
    return exercises
        .asSequence()
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedWith(
            compareByDescending<ProgressExerciseUi> { it.changePercentage }
                .thenBy { it.name.lowercase(Locale.US) }
        )
        .toList()
}

internal fun buildProgressSplitPages(
    exercises: List<ProgressExerciseUi>,
    splits: List<ProgressSplitSource>,
    searchQuery: String,
): List<ProgressSplitPageUi> {
    val byId = exercises.associateBy { it.exerciseId }
    return splits.map { split ->
        val splitExercises = organizeProgressExercises(
            exercises = split.exerciseIds.mapNotNull { byId[it] },
            searchQuery = searchQuery,
        )
        ProgressSplitPageUi(split.splitId, split.name, splitExercises)
    }
}

internal fun normalizeProgressSplitIndex(index: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    return index.coerceIn(0, pageCount - 1)
}

internal fun buildProgressChartPoints(
    filteredSets: List<WorkoutSet>,
    isBodyweight: Boolean,
    userBodyWeight: Int?,
): List<ProgressPointUi> {
    return filteredSets
        .groupBy { it.date }
        .toSortedMap()
        .map { (date, sets) ->
            val maxSet = sets.maxByOrNull { effectiveLoadStored(it, isBodyweight, userBodyWeight) } ?: sets.first()
            ProgressPointUi(
                date = date,
                label = WorkoutDates.formatAxis(date),
                volume = calculateSessionVolume(sets, isBodyweight, userBodyWeight),
                estimated1RM = calc1RM(
                    weight = maxSet.weightLbs ?: 0,
                    reps = maxSet.reps ?: 0,
                    isBodyweight = isBodyweight || maxSet.isBodyweight,
                    userBodyWeight = userBodyWeight
                ),
                maxWeight = sets.maxOf { effectiveLoadStored(it, isBodyweight, userBodyWeight) },
                maxReps = sets.maxOf { it.reps ?: 0 },
                reps = maxSet.reps ?: 0
            )
        }
}

internal fun buildProgressStats(
    allTimeSets: List<WorkoutSet>,
    timeframe: TimeframeFilter,
    isBodyweight: Boolean,
    userBodyWeight: Int?,
    now: LocalDate,
): ProgressStatsUi {
    // For testing with future dates, anchor the reference to the latest data if it's beyond 'now'
    val latestDataDate = allTimeSets.maxOfOrNull { LocalDate.parse(it.date) }
    val referenceDate = if (latestDataDate != null && latestDataDate.isAfter(now)) latestDataDate else now

    val currentPeriodStart = when (timeframe) {
        TimeframeFilter.ONE_MONTH -> referenceDate.minusMonths(1)
        TimeframeFilter.THREE_MONTHS -> referenceDate.minusMonths(3)
        TimeframeFilter.SIX_MONTHS -> referenceDate.minusMonths(6)
        TimeframeFilter.ONE_YEAR -> referenceDate.minusYears(1)
        TimeframeFilter.ALL_TIME -> LocalDate.of(2000, 1, 1)
    }

    val currentSets = allTimeSets.filter { !LocalDate.parse(it.date).isBefore(currentPeriodStart) }

    // Latest session in the window drives the current estimated 1RM.
    val sessionsInPeriod = currentSets.groupBy { it.date }.toSortedMap().values.toList()
    val latestSession = sessionsInPeriod.lastOrNull().orEmpty()

    val currentPr = allTimeSets.maxOfOrNull {
        effectiveLoadStored(it, isBodyweight, userBodyWeight)
    } ?: 0

    val current1RM = latestEstimatedOneRepMax(latestSession, isBodyweight, userBodyWeight)
        ?: latestEstimatedOneRepMax(allTimeSets, isBodyweight, userBodyWeight)
        ?: 0f

    val currentVolume = calculateSessionVolume(currentSets, isBodyweight, userBodyWeight)
    val currentWorkoutCount = currentSets.map { it.date }.distinct().size

    return ProgressStatsUi(
        currentPr = WeightLbs.formatStored(currentPr),
        currentPrLbs = WeightLbs.toLbs(currentPr).toFloat(),
        est1Rm = String.format(Locale.US, "%.1f", current1RM),
        totalVolume = formatVolume(currentVolume),
        totalVolumeLbs = currentVolume,
        workoutCount = currentWorkoutCount,
    )
}

private fun latestEstimatedOneRepMax(
    sets: List<WorkoutSet>,
    isBodyweight: Boolean,
    userBodyWeight: Int?,
): Float? {
    if (sets.isEmpty()) return null
    val latestSession = sets
        .groupBy { it.date }
        .toSortedMap()
        .values
        .lastOrNull()
        .orEmpty()
    val maxSet = latestSession.maxByOrNull {
        effectiveLoadStored(it, isBodyweight, userBodyWeight)
    } ?: return null
    return calc1RM(
        weight = maxSet.weightLbs ?: 0,
        reps = maxSet.reps ?: 0,
        isBodyweight = isBodyweight || maxSet.isBodyweight,
        userBodyWeight = userBodyWeight
    )
}

private fun calc1RM(weight: Int, reps: Int, isBodyweight: Boolean, userBodyWeight: Int?): Float {
    val baseWeight = WeightLbs.toLbs(weight)
    val adjustedWeight = if (isBodyweight) baseWeight + (userBodyWeight ?: 0) else baseWeight
    return (adjustedWeight * (1 + reps / 30f)).toFloat()
}

private fun calculateSessionVolume(
    sets: List<WorkoutSet>,
    isBodyweight: Boolean,
    userBodyWeight: Int?,
): Int {
    return sets.sumOf {
        val weight = WeightLbs.toLbs(it.weightLbs)
        val reps = it.reps ?: 0
        val effectiveWeight = if (isBodyweight || it.isBodyweight) weight + (userBodyWeight ?: 0) else weight
        (effectiveWeight * reps).toInt()
    }
}

private fun effectiveLoadStored(
    set: WorkoutSet,
    isBodyweightExercise: Boolean,
    userBodyWeight: Int?,
): Int {
    val addedLoad = set.weightLbs ?: 0
    return if (isBodyweightExercise || set.isBodyweight) {
        addedLoad + ((userBodyWeight ?: 0) * 10)
    } else {
        addedLoad
    }
}

private fun formatProgressSetLabel(set: WorkoutSet, isBodyweightExercise: Boolean): String {
    val reps = set.reps ?: 0
    if (isBodyweightExercise || set.isBodyweight) {
        val addedLoad = set.weightLbs?.takeIf { it > 0 }
        val load = if (addedLoad == null) {
            "BW"
        } else {
            "BW + ${WeightLbs.formatStored(addedLoad)}"
        }
        return "$load x $reps"
    }
    return "${WeightLbs.formatStored(set.weightLbs)} x $reps"
}

private fun formatVolume(volume: Int): String {
    return if (volume >= 1000) String.format(Locale.US, "%.1fk", volume / 1000f) else volume.toString()
}
