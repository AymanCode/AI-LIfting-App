package com.ayman.ecolift.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.agent.assist.AssistService
import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.ai.ExerciseMuscleClassifier
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.UNCLASSIFIED_MUSCLE_GROUPS
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDates
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.data.normalizeBodyPartGroup
import com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot
import com.ayman.ecolift.data.progress.highlights.buildHighlightReelSnapshot
import com.ayman.ecolift.data.progress.volumeEquivalence
import com.ayman.ecolift.data.selectableBodyPartParts
import com.ayman.ecolift.data.suggestMuscleGroup
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.Diagnostics
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.supabase.SyncErrors
import com.ayman.ecolift.web.supabase.UserVisibleSyncException
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.formatFixed
import com.ayman.ecolift.web.util.groupThousands
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.minusMonths
import com.ayman.ecolift.web.util.minusYears
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val repos: AppRepos,
    private val engine: LocalGenAiEngine,
    initialTimeframe: TimeframeFilter = TimeframeFilter.ONE_MONTH,
    private val scope: ProgressScope = ProgressScope.Overview,
    private val today: () -> LocalDate = ::todayLocalDate,
    private val derivationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val exerciseRepository = repos.exercises
    private val setRepository = repos.sets
    private val workoutRepository = repos.workout

    private val highlightDate = MutableStateFlow(today())

    private val visible = MutableStateFlow(false)

    private val highlights = combine(
        exerciseRepository.exercises, setRepository.sets, highlightDate,
    ) { exercises, sets, date ->
        Diagnostics.count("progress.highlights")
        buildHighlightReelSnapshot(exercises, sets, date)
    }

    private val assist = AssistService(
        tools = AgentToolsImpl(repos),
        engine = engine,
    )
    private val _assistEnabled = MutableStateFlow(false)
    val assistEnabled: StateFlow<Boolean> = _assistEnabled
    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis

    private val selectedExerciseId = MutableStateFlow<Long?>((scope as? ProgressScope.Report)?.exerciseId)
    private val timeframe = MutableStateFlow(initialTimeframe)
    private val windowEnd = MutableStateFlow(today())
    private val timeWindow = combine(timeframe, windowEnd, ::Pair)
    private val selectedMetric = MutableStateFlow(ProgressMetric.ESTIMATED_1RM)
    private val organizationMode = MutableStateFlow(ProgressOrganizationMode.PROGRESS)
    private val sortMode = MutableStateFlow(ProgressSortMode.TREND)
    private val searchQuery = MutableStateFlow("")
    val searchText: StateFlow<String> = searchQuery.asStateFlow()
    private val showAllLifts = MutableStateFlow(false)

    /** "All lifts" swaps the held landing for the full library; the library shows alone with no held lifts. */
    val allLiftsShown: StateFlow<Boolean> = showAllLifts
    fun setShowAllLifts(show: Boolean) { showAllLifts.value = show }

    private fun safeCanonicalId(id: Long): Long = runCatching { exerciseRepository.canonicalId(id) }.getOrDefault(id)

    // Keep sections and their source history together so a wall never mixes two data revisions.
    private val heldSources = combine(
        repos.learnedSplits.model,
        exerciseRepository.exercises,
        setRepository.sets,
        searchQuery,
        workoutRepository.observeAllWorkoutDays(),
    ) { model, exercises, sets, query, days ->
        GroupWallSource(
            buildHeldSections(model, today().toString(), exercises, sets, ::safeCanonicalId, query),
            model, exercises, sets, days, query,
        )
    }

    // Declared before the wall: property initializers run in order, and the wall's combine reads it.
    private val userBodyweightLbs = workoutRepository.observeUserBodyweightLbs()

    /** One projection per history/search/window/bodyweight change; composition only renders these values. */
    private val groupWall = combine(heldSources, timeWindow, userBodyweightLbs) { source, window, bodyweight ->
        Diagnostics.count("progress.wall")
        val (filter, end) = window
        val start = if (filter == TimeframeFilter.ALL_TIME) {
            source.sets.filter { com.ayman.ecolift.data.WorkoutMetrics.isStrengthEligible(it) && it.date <= end.toString() }
                .minOfOrNull { it.date }?.let(LocalDate::parse) ?: end
        } else {
            progressWindowStart(filter, end)
        }
        buildGroupWall(source, start, end, ::safeCanonicalId, bodyweight)
    }

    /** True once the model holds any lift; false shows the library directly. */
    private val heldAvailable = repos.learnedSplits.model
        .map { it.roles.isNotEmpty() }

    private val historyStartDate = LocalDate(2000, 1, 1).toString()

    private val exerciseList = combine(
        exerciseRepository.exercises,
        setRepository.observeExerciseProgressSummaries(),
        userBodyweightLbs,
        timeWindow,
    ) { exercises, summaries, userBodyweight, window ->
        Diagnostics.count("progress.list")
        val (filter, now) = window
        val muscleGroupsById = exercises.associate { exercise ->
            val groups = if (ExerciseMuscleClassifier.shouldClassify(exercise)) {
                ""
            } else {
                exercise.muscleGroups
            }
            exercise.id to groups
        }
        val taggedExerciseCatalog = exercises.filter {
            selectableBodyPartParts(it.muscleGroups).isNotEmpty()
        }
        val monthStart = today().minusMonths(1)

        summaries.map { summary ->
            val historySets = setRepository.getSetsSince(summary.exerciseId, historyStartDate)
            val chartPoints = buildProgressChartPoints(
                filteredSets = historySets,
                isBodyweight = summary.isBodyweight,
                userBodyWeight = userBodyweight,
            )
            val rangePoints = progressPointsInWindow(chartPoints, progressWindowStart(filter, now), now)
            val e1rmTrend = rangePoints.map { if (summary.isBodyweight) it.maxReps.toFloat() else it.estimated1RM }
            val overview = buildQuietProgressOverview(historySets, summary.isBodyweight, progressWindowStart(filter, now), now)
            val report = buildExerciseReport(historySets, summary.isBodyweight, progressWindowStart(filter, now), now)
            val change = if (overview.points.size > 1 && overview.points.first().value > 0f) {
                (overview.points.last().value / overview.points.first().value - 1f) * 100f
            } else {
                0f
            }
            val rangeDelta = calculateProgressWindowStat(rangePoints, summary.isBodyweight, userBodyweight)
            val monthDelta = calculatePastMonthDeltaLabel(
                points = chartPoints,
                isBodyweight = summary.isBodyweight,
                monthStart = monthStart,
            )

            val lastSets = historySets.filter { it.isCheckedWorking && it.date == rangePoints.lastOrNull()?.date }
            val lastSet = lastSets.maxWithOrNull(compareBy<WorkoutSet> {
                effectiveLoadStored(it, summary.isBodyweight, userBodyweight)
            }.thenBy { it.reps ?: 0 })
            val muscleGroups = muscleGroupsById[summary.exerciseId].orEmpty()
            val exercise = exercises.firstOrNull { it.id == summary.exerciseId }

            ProgressExerciseUi(
                exerciseId = summary.exerciseId,
                name = summary.exerciseName,
                muscleGroups = muscleGroups,
                suggestedMuscleGroup = if (selectableBodyPartParts(muscleGroups).isEmpty() && exercise != null) {
                    suggestMuscleGroup(exercise.name, taggedExerciseCatalog)?.group
                } else {
                    null
                },
                sessions = rangePoints.size,
                lastSessionDate = rangePoints.lastOrNull()?.date?.let(WorkoutDates::formatAxis).orEmpty(),
                lastSessionIsoDate = rangePoints.lastOrNull()?.date.orEmpty(),
                lastSessionSummary = lastSet?.let {
                    formatProgressSetLabel(it, summary.isBodyweight)
                } ?: "—",
                changePercentage = change,
                rangeStatLabel = rangeDelta?.label ?: when (rangePoints.size) { 0 -> "No sessions"; 1 -> "1 session"; else -> "${rangePoints.size} sessions" },
                rangeStatSign = rangeDelta?.sign ?: 0,
                rangeStatContext = rangeDelta?.subLabel,
                firstSessionIsoDate = chartPoints.firstOrNull()?.date,
                monthDeltaLabel = monthDelta?.label,
                monthDeltaSign = monthDelta?.sign ?: 0,
                monthDeltaSubLabel = monthDelta?.subLabel,
                trend = e1rmTrend.map { it.roundToInt() },
                overview = overview,
                report = report,
                isBodyweight = summary.isBodyweight,
            )
        }.sortedBy { it.name }
    }.flowOn(derivationDispatcher)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(0), replay = 1)

    private val heroData = combine(
        exerciseRepository.exercises,
        setRepository.sets,
        workoutRepository.observeArchivedCycles(),
        userBodyweightLbs,
    ) { exercises, sets, archivedCycles, userBodyweight ->
        Diagnostics.count("progress.hero")
        buildProgressHeroData(
            exercises = exercises,
            allSets = sets,
            cyclesDone = archivedCycles.size,
            userBodyweight = userBodyweight,
            today = today(),
        )
    }

    private val detailState = combine(
        selectedExerciseId,
        timeWindow,
        userBodyweightLbs,
    ) { id, window, userBodyweight ->
        Triple(id, window, userBodyweight)
    }.flatMapLatest { (id, window, userBodyweight) ->
        val (filter, now) = window
        if (id == null) {
            MutableStateFlow<DetailData?>(null)
        } else {
            val sinceDate = when (filter) {
                TimeframeFilter.TWO_WEEKS -> now.minusDays(13)
                TimeframeFilter.ONE_MONTH -> now.minusMonths(1)
                TimeframeFilter.THREE_MONTHS -> now.minusMonths(3)
                TimeframeFilter.SIX_MONTHS -> now.minusMonths(6)
                TimeframeFilter.ONE_YEAR -> now.minusYears(1)
                TimeframeFilter.ALL_TIME -> LocalDate(2000, 1, 1)
            }

            combine(
                exerciseRepository.exercises,
                setRepository.observeSetsSince(id, sinceDate.toString()),
                setRepository.observeSetsSince(id, historyStartDate)
            ) { exercises: List<com.ayman.ecolift.data.Exercise>, filteredSets: List<WorkoutSet>, allTimeSets: List<WorkoutSet> ->
                Diagnostics.count("progress.detail")
                val exercise = exercises.find { it.id == id }
                val isBodyweight = exercise?.isBodyweight ?: false
                val muscleGroups = exercise
                    ?.takeUnless(ExerciseMuscleClassifier::shouldClassify)
                    ?.muscleGroups
                    .orEmpty()

                val chartPoints = buildProgressChartPoints(
                    filteredSets = filteredSets.filter { it.isCheckedWorking && it.date <= now.toString() },
                    isBodyweight = isBodyweight,
                    userBodyWeight = userBodyweight
                )
                val report = buildExerciseReport(allTimeSets, isBodyweight, sinceDate, now)

                DetailData(
                    exerciseId = id,
                    timeframe = filter,
                    exerciseName = exercise?.name.orEmpty(),
                    muscleGroups = muscleGroups,
                    suggestedMuscleGroup = if (selectableBodyPartParts(muscleGroups).isEmpty() && exercise != null && allTimeSets.isNotEmpty()) {
                        suggestMuscleGroup(exercise.name, exercises.filter {
                            selectableBodyPartParts(it.muscleGroups).isNotEmpty()
                        })?.group
                    } else {
                        null
                    },
                    isBodyweight = isBodyweight,
                    userBodyweightLbs = userBodyweight,
                    chartPoints = chartPoints,
                    stats = buildProgressStats(
                        allTimeSets = allTimeSets,
                        timeframe = filter,
                        isBodyweight = isBodyweight,
                        userBodyWeight = userBodyweight,
                        now = now
                    ),
                    report = report,
                )
            }
        }
    }

    private val listState = combine(
        exerciseList,
        organizationMode,
        combine(searchQuery, sortMode, ::Pair),
    ) { exercises, mode, (query, sort) ->
        val visibleExercises = organizeProgressExercises(exercises, query, sort)
        ProgressListData(
            exercises = exercises,
            organizationMode = mode,
            sortMode = sort,
            searchQuery = query,
            visibleExercises = visibleExercises,
        )
    }

    private val scopedListState: Flow<ProgressListData> = when (scope) {
        ProgressScope.Overview -> listState
        is ProgressScope.Report -> flowOf(ProgressListData(emptyList(), ProgressOrganizationMode.PROGRESS,
            ProgressSortMode.TREND, "", emptyList()))
    }

    private val overviewData: Flow<ProgressOverviewData> = when (scope) {
        ProgressScope.Overview -> combine(groupWall, heldAvailable, highlights, ::ProgressOverviewData)
        is ProgressScope.Report -> flowOf(ProgressOverviewData())
    }

    private val projection = combine(
        combine(
            scopedListState,
            selectedExerciseId,
            timeframe,
            selectedMetric,
            detailState
        ) { list: ProgressListData, selectedId: Long?, filter: TimeframeFilter, metric: ProgressMetric, detail: DetailData? ->
            val currentDetail = detail?.takeIf { it.exerciseId == selectedId && it.timeframe == filter }
            ProgressUiState(
                exercises = list.exercises,
                organizationMode = list.organizationMode,
                sortMode = list.sortMode,
                searchQuery = list.searchQuery,
                visibleExercises = list.visibleExercises,
                selectedExerciseId = selectedId,
                selectedExerciseName = currentDetail?.exerciseName.orEmpty(),
                selectedExerciseMuscleGroups = currentDetail?.muscleGroups.orEmpty(),
                selectedExerciseSuggestedMuscleGroup = currentDetail?.suggestedMuscleGroup,
                isBodyweight = currentDetail?.isBodyweight ?: false,
                userBodyweightLbs = currentDetail?.userBodyweightLbs,
                chartPoints = currentDetail?.chartPoints ?: emptyList(),
                timeframe = filter,
                selectedMetric = metric,
                stats = currentDetail?.stats,
                report = currentDetail?.report,
            )
        },
        if (scope == ProgressScope.Overview) heroData else flowOf(ProgressHeroData(null, null)),
        windowEnd,
        overviewData,
    ) { state, heroData, end, overview ->
        ProgressProjection.Ready(state.copy(
            windowEndIsoDate = end.toString(),
            hero = heroData.hero,
            heroSessionsThisMonthLabel = heroData.sessionsThisMonthLabel,
            groupWall = overview.wall,
            heldAvailable = overview.heldAvailable,
            highlights = overview.highlights,
        ))
    }

    val uiState: StateFlow<ProgressProjection> = visible.flatMapLatest { isVisible ->
        if (isVisible) projection else emptyFlow()
    }.flowOn(derivationDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(0),
            initialValue = ProgressProjection.Pending,
        )

    fun setVisible(isVisible: Boolean) { visible.value = isVisible }

    fun selectExercise(exerciseId: Long?) {
        _analysis.value = AnalysisState.Idle
        if (exerciseId == null) {
            selectedExerciseId.value = null
            return
        }
        viewModelScope.launch {
            val canonicalExerciseId = exerciseRepository.canonicalId(exerciseId)
            selectedExerciseId.value = canonicalExerciseId
        }
    }
    fun setWindowEnd(date: LocalDate) { windowEnd.value = date.coerceIn(LocalDate(2000, 1, 1), today()) }
    fun setTimeframe(filter: TimeframeFilter) { timeframe.value = filter }
    fun setMetric(metric: ProgressMetric) { selectedMetric.value = metric }
    fun setOrganizationMode(mode: ProgressOrganizationMode) {
        if (mode == ProgressOrganizationMode.HIGHLIGHTS) highlightDate.value = today()
        organizationMode.value = mode
    }
    fun setSortMode(mode: ProgressSortMode) { sortMode.value = mode }
    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun updateSelectedExerciseMuscleGroup(muscleGroup: String) {
        val exerciseId = selectedExerciseId.value ?: return
        updateExerciseMuscleGroup(exerciseId, muscleGroup)
    }

    fun updateExerciseMuscleGroup(exerciseId: Long, muscleGroup: String) {
        val normalized = normalizeBodyPartGroup(muscleGroup) ?: return
        if (DemoGuard.blockIfDemo()) return
        viewModelScope.launch {
            try {
                exerciseRepository.updateMuscleGroups(exerciseId, normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                SyncErrors.report(UserVisibleSyncException("Couldn't change the muscle group.", failure))
            }
        }
    }

    fun toggleAssist() {
        _assistEnabled.update { !it }
        if (!_assistEnabled.value) _analysis.value = AnalysisState.Idle
    }

    fun analyzeExercise(exerciseId: Long) {
        if (!_assistEnabled.value) return
        _analysis.value = AnalysisState.Loading
        viewModelScope.launch {
            val result = runCatching { assist.analyzeExercise(exerciseId) }.getOrNull()
                ?: com.ayman.ecolift.agent.assist.ExerciseAnalysis(
                    "Couldn't analyze. Try again.",
                    com.ayman.ecolift.agent.assist.AnalysisSource.Local,
                    validated = true,
                )
            _analysis.value = AnalysisState.Ready(result)
        }
    }

    fun clearAnalysis() { _analysis.value = AnalysisState.Idle }
}

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Ready(val analysis: com.ayman.ecolift.agent.assist.ExerciseAnalysis) : AnalysisState
}

private data class DetailData(
    val exerciseId: Long,
    val timeframe: TimeframeFilter,
    val exerciseName: String,
    val muscleGroups: String,
    val suggestedMuscleGroup: String?,
    val isBodyweight: Boolean,
    val userBodyweightLbs: Int?,
    val chartPoints: List<ProgressPointUi>,
    val stats: ProgressStatsUi,
    val report: ExerciseReport,
)

private data class ProgressListData(
    val exercises: List<ProgressExerciseUi>,
    val organizationMode: ProgressOrganizationMode,
    val sortMode: ProgressSortMode,
    val searchQuery: String,
    val visibleExercises: List<ProgressExerciseUi>,
)

private data class ProgressHeroData(
    val hero: ProgressHeroUi?,
    val sessionsThisMonthLabel: String?,
)

private data class ProgressOverviewData(
    val wall: GroupWallUi = GroupWallUi(),
    val heldAvailable: Boolean = false,
    val highlights: HighlightReelSnapshot = HighlightReelSnapshot(emptyList()),
)

internal val FullMonthNames = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
)

internal fun organizeProgressExercises(
    exercises: List<ProgressExerciseUi>,
    searchQuery: String,
    sortMode: ProgressSortMode = ProgressSortMode.TREND,
): List<ProgressExerciseUi> {
    val query = searchQuery.trim()
    val comparator = when (sortMode) {
        ProgressSortMode.TREND ->
            compareByDescending<ProgressExerciseUi> { it.changePercentage }
                .thenBy { it.name.lowercase() }
        ProgressSortMode.RECENT -> compareByDescending<ProgressExerciseUi> { it.lastSessionIsoDate }.thenBy { it.name.lowercase() }
        ProgressSortMode.NAME -> compareBy<ProgressExerciseUi> { it.name.lowercase() }
        ProgressSortMode.SESSIONS ->
            compareByDescending<ProgressExerciseUi> { it.sessions }
                .thenBy { it.name.lowercase() }
        ProgressSortMode.BODY_PART ->
            // Unclassified exercises sink to the bottom instead of sorting as a letter.
            compareBy<ProgressExerciseUi> { it.muscleGroups == UNCLASSIFIED_MUSCLE_GROUPS }
                .thenBy { it.muscleGroups.lowercase() }
                .thenBy { it.name.lowercase() }
    }
    return exercises
        .asSequence()
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedWith(compareBy<ProgressExerciseUi> { it.overview.points.size < 2 }
            .then(comparator))
        .toList()
}

private fun buildProgressHeroData(
    exercises: List<Exercise>,
    allSets: List<WorkoutSet>,
    cyclesDone: Int,
    userBodyweight: Int?,
    today: LocalDate,
): ProgressHeroData {
    val completedSets = allSets.filter { it.isCheckedWorking && it.date <= today.toString() }
    val lifetimeTotals = calculateProgressLifetimeTotals(completedSets)
    val isBodyweightByExerciseId = exercises.associate { it.id to it.isBodyweight }
    val sessionsThisMonth = completedSets
        .mapNotNull { set -> runCatching { LocalDate.parse(set.date) }.getOrNull() }
        .filter { date -> date.year == today.year && date.monthNumber == today.monthNumber }
        .distinct()
        .size
    val sessionsThisMonthLabel = sessionsThisMonth
        .takeIf { it > 0 }
        ?.let { count ->
            val noun = if (count == 1) "session" else "sessions"
            "$count $noun in ${FullMonthNames[today.monthNumber - 1]}"
        }

    if (completedSets.isEmpty()) {
        return ProgressHeroData(hero = null, sessionsThisMonthLabel = sessionsThisMonthLabel)
    }

    val lifetimeVolume = calculateSessionVolume(
        sets = completedSets,
        isBodyweightByExerciseId = isBodyweightByExerciseId,
        userBodyWeight = userBodyweight,
    )
    val equivalence = volumeEquivalence(lifetimeVolume.toFloat())
    val monthStart = today.minusMonths(1)

    return ProgressHeroData(
        hero = ProgressHeroUi(
            lifetimeVolumeLabel = groupThousands(lifetimeVolume),
            equivalenceLabel = equivalence?.label ?: "working reps",
            milestoneProgress = equivalence?.progressToNext ?: 0f,
            milestoneLabel = equivalence?.nextMilestoneLabel ?: "Recorded working sets",
            sessionCount = completedSets.map(WorkoutSet::date).distinct().size,
            completedSetCount = lifetimeTotals.completedSetCount,
            totalRepCount = lifetimeTotals.totalRepCount,
            cyclesDone = cyclesDone,
            prsThisMonth = calculatePrsThisMonth(
                completedSets = completedSets,
                isBodyweightByExerciseId = isBodyweightByExerciseId,
                userBodyweight = userBodyweight,
                monthStart = monthStart,
            ),
        ),
        sessionsThisMonthLabel = sessionsThisMonthLabel,
    )
}

internal data class ProgressLifetimeTotals(
    val completedSetCount: Int,
    val totalRepCount: Int,
)

internal fun calculateProgressLifetimeTotals(sets: List<WorkoutSet>): ProgressLifetimeTotals {
    val completedSets = sets.filter { it.isCheckedWorking }
    return ProgressLifetimeTotals(
        completedSetCount = completedSets.size,
        totalRepCount = completedSets.sumOf { it.reps ?: 0 },
    )
}

private fun calculatePrsThisMonth(
    completedSets: List<WorkoutSet>,
    isBodyweightByExerciseId: Map<Long, Boolean>,
    userBodyweight: Int?,
    monthStart: LocalDate,
): Int {
    return completedSets
        .filter { com.ayman.ecolift.data.WorkoutMetrics.isStrengthEligible(it) }
        .groupBy { it.exerciseId }
        .count { (exerciseId, exerciseSets) ->
            val isBodyweight = isBodyweightByExerciseId[exerciseId] ?: false
            var runningMax: Int? = null
            var lastIncreaseDate: LocalDate? = null
            exerciseSets
                .groupBy { it.date }
                .entries
                .sortedBy { it.key }
                .forEachIndexed { index, (dateText, setsForDate) ->
                    val sessionMax = setsForDate.maxOf {
                        effectiveLoadStored(it, isBodyweight, userBodyweight)
                    }
                    val previousMax = runningMax
                    if (previousMax == null) {
                        runningMax = sessionMax
                    } else if (sessionMax > previousMax) {
                        runningMax = sessionMax
                        if (index > 0) {
                            lastIncreaseDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
                        }
                    }
                }
            lastIncreaseDate?.let { it >= monthStart } == true
        }
}

internal fun buildProgressChartPoints(
    filteredSets: List<WorkoutSet>,
    isBodyweight: Boolean,
    userBodyWeight: Int?,
): List<ProgressPointUi> {
    return filteredSets
        .filter { it.isCheckedWorking }
        .groupBy { it.date }
        .entries.sortedBy { it.key }
        .map { (date, sets) ->
            val workingSets = sets.filter { com.ayman.ecolift.data.WorkoutMetrics.isStrengthEligible(it) }
            val bestEstimatedOneRepMaxSet = workingSets.maxByOrNull {
                estimatedOneRepMax(it, isBodyweight, userBodyWeight)
            } ?: sets.first()
            val maxWeight = workingSets.maxOfOrNull { effectiveLoadStored(it, isBodyweight, userBodyWeight) } ?: 0
            ProgressPointUi(
                date = date,
                label = WorkoutDates.formatAxis(date),
                estimateEvidence = formatProgressSetLabel(bestEstimatedOneRepMaxSet, isBodyweight),
                repEvidence = workingSets.maxByOrNull { it.reps ?: 0 }?.let { formatProgressSetLabel(it, isBodyweight) }.orEmpty(),
                workingSetCount = workingSets.size,
                totalWorkingReps = workingSets.sumOf { it.reps ?: 0 },
                volume = calculateSessionVolume(sets, isBodyweight, userBodyWeight),
                estimated1RM = estimatedOneRepMax(bestEstimatedOneRepMaxSet, isBodyweight, userBodyWeight),
                maxWeight = maxWeight,
                maxReps = workingSets.maxOfOrNull { it.reps ?: 0 } ?: 0,
                reps = bestEstimatedOneRepMaxSet.reps ?: 0,
                repsAtTopWeight = workingSets
                    .filter {
                        effectiveLoadStored(it, isBodyweight, userBodyWeight) == maxWeight
                    }
                    .maxOfOrNull { it.reps ?: 0 } ?: 0,
            )
        }
}

internal fun calculateE1rmChangePercentage(points: List<ProgressPointUi>): Float {
    val e1rmTrend = points.takeLast(10).map { it.estimated1RM }
    val latest = e1rmTrend.lastOrNull() ?: return 0f
    val previous = e1rmTrend.getOrNull(e1rmTrend.size - 2) ?: return 0f
    return if (previous > 0f) (latest - previous) / previous * 100f else 0f
}

internal data class PastMonthDelta(
    val label: String,
    val sign: Int,
    val subLabel: String,
)

internal fun calculatePastMonthDeltaLabel(
    points: List<ProgressPointUi>,
    isBodyweight: Boolean,
    monthStart: LocalDate,
): PastMonthDelta? {
    val monthPoints = points.filter { LocalDate.parse(it.date) >= monthStart }
    if (monthPoints.size < 2) return null

    val first = monthPoints.first()
    val latest = monthPoints.last()
    if (isBodyweight) {
        val repsDelta = latest.maxReps - first.maxReps
        if (repsDelta == 0) {
            return PastMonthDelta(label = "steady", sign = 0, subLabel = "past month")
        }
        return PastMonthDelta(
            label = signedRepsLabel(repsDelta),
            sign = repsDelta.compareTo(0),
            subLabel = "past month",
        )
    }

    val weightDelta = latest.maxWeight - first.maxWeight
    if (weightDelta != 0) {
        return PastMonthDelta(
            label = signedWeightLabel(weightDelta),
            sign = weightDelta.compareTo(0),
            subLabel = "past month",
        )
    }

    val repsDelta = latest.repsAtTopWeight - first.repsAtTopWeight
    if (repsDelta != 0) {
        val subLabel = latest.maxWeight
            .takeIf { it > 0 }
            ?.let { "at ${WeightLbs.formatStored(it)} lb" }
            ?: "past month"
        return PastMonthDelta(
            label = signedRepsLabel(repsDelta),
            sign = repsDelta.compareTo(0),
            subLabel = subLabel,
        )
    }

    return PastMonthDelta(label = "steady", sign = 0, subLabel = "past month")
}

private fun signedRepsLabel(delta: Int): String {
    val prefix = when {
        delta > 0 -> "+"
        delta < 0 -> "-"
        else -> ""
    }
    return "$prefix${countLabel(kotlin.math.abs(delta), "rep")}"
}

private fun signedWeightLabel(delta: Int): String {
    val prefix = when {
        delta > 0 -> "+"
        delta < 0 -> "-"
        else -> ""
    }
    return "$prefix${WeightLbs.formatStored(kotlin.math.abs(delta))} lb"
}

/**
 * Picks the narrowest timeframe whose window contains at least two distinct session
 * dates (enough to render a trend line), scanning most-recent -> widest. Falls back to
 * ALL_TIME when no window has two sessions.
 */
internal fun bestTimeframeFor(
    sessionDates: List<LocalDate>,
    now: LocalDate,
): TimeframeFilter {
    val distinctDates = sessionDates.distinct()
    fun countSince(start: LocalDate) = distinctDates.count { it >= start }
    return when {
        countSince(now.minusMonths(1)) >= 2 -> TimeframeFilter.ONE_MONTH
        countSince(now.minusMonths(3)) >= 2 -> TimeframeFilter.THREE_MONTHS
        countSince(now.minusMonths(6)) >= 2 -> TimeframeFilter.SIX_MONTHS
        countSince(now.minusYears(1)) >= 2 -> TimeframeFilter.ONE_YEAR
        else -> TimeframeFilter.ALL_TIME
    }
}

internal fun buildProgressStats(
    allTimeSets: List<WorkoutSet>,
    timeframe: TimeframeFilter,
    isBodyweight: Boolean,
    userBodyWeight: Int?,
    now: LocalDate,
): ProgressStatsUi {
    val referenceDate = now

    val currentPeriodStart = when (timeframe) {
        TimeframeFilter.TWO_WEEKS -> referenceDate.minusDays(13)
        TimeframeFilter.ONE_MONTH -> referenceDate.minusMonths(1)
        TimeframeFilter.THREE_MONTHS -> referenceDate.minusMonths(3)
        TimeframeFilter.SIX_MONTHS -> referenceDate.minusMonths(6)
        TimeframeFilter.ONE_YEAR -> referenceDate.minusYears(1)
        TimeframeFilter.ALL_TIME -> LocalDate(2000, 1, 1)
    }

    val currentSets = allTimeSets.filter { it.isCheckedWorking && LocalDate.parse(it.date) in currentPeriodStart..now }

    // Latest session in the window drives the current estimated 1RM.
    val sessionsInPeriod = currentSets.groupBy { it.date }.entries.sortedBy { it.key }.map { it.value }
    val latestSession = sessionsInPeriod.lastOrNull().orEmpty()

    val currentPrSet = allTimeSets.filter { it.isCheckedWorking && it.date <= now.toString() }.maxWithOrNull(compareBy<WorkoutSet> {
        effectiveLoadStored(it, isBodyweight, userBodyWeight)
    }.thenBy { it.reps ?: 0 }.thenBy { it.date })
    val currentPr = currentPrSet
        ?.let { effectiveLoadStored(it, isBodyweight, userBodyWeight) }
        ?: 0
    val currentPrDate = currentPrSet?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val current1RM = latestEstimatedOneRepMax(latestSession, isBodyweight, userBodyWeight)
        ?: 0f

    val currentVolume = calculateSessionVolume(currentSets, isBodyweight, userBodyWeight)
    val currentWorkoutCount = currentSets.map { it.date }.distinct().size

    return ProgressStatsUi(
        currentPr = WeightLbs.formatStored(currentPr),
        currentPrLbs = WeightLbs.toLbs(currentPr).toFloat(),
        bestSetLabel = currentPrSet?.let { formatProgressSetLabel(it, isBodyweight) }.orEmpty(),
        est1Rm = formatFixed(current1RM.toDouble(), 1),
        totalVolume = formatVolume(currentVolume),
        totalVolumeLbs = currentVolume,
        workoutCount = currentWorkoutCount,
        currentPrDate = currentPrDate,
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
        .entries.sortedBy { it.key }
        .lastOrNull()?.value
        .orEmpty()
    val maxSet = latestSession.maxByOrNull {
        estimatedOneRepMax(it, isBodyweight, userBodyWeight)
    } ?: return null
    return estimatedOneRepMax(maxSet, isBodyweight, userBodyWeight)
}

private fun estimatedOneRepMax(
    set: WorkoutSet,
    isBodyweightExercise: Boolean,
    userBodyWeight: Int?,
): Float {
    return com.ayman.ecolift.data.WorkoutMetrics.estimatedOneRepMax(set, isBodyweightExercise)?.toFloat() ?: 0f
}

internal fun calculateSessionVolume(
    sets: List<WorkoutSet>,
    isBodyweight: Boolean,
    userBodyWeight: Int?,
): Int = com.ayman.ecolift.data.WorkoutMetrics.roundedVolumeInt(sets)

internal fun calculateSessionVolume(
    sets: List<WorkoutSet>,
    isBodyweightByExerciseId: Map<Long, Boolean>,
    userBodyWeight: Int?,
): Int = com.ayman.ecolift.data.WorkoutMetrics.roundedVolumeInt(sets)

private fun effectiveLoadStored(
    set: WorkoutSet,
    isBodyweightExercise: Boolean,
    userBodyWeight: Int?,
): Int = set.weightLbs ?: 0

private fun formatProgressSetLabel(set: WorkoutSet, isBodyweightExercise: Boolean): String {
    val reps = set.reps ?: 0
    if (isBodyweightExercise || set.isBodyweight) {
        val addedLoad = set.weightLbs?.takeIf { it > 0 }
        val load = if (addedLoad == null) {
            "BW"
        } else {
            "BW + ${WeightLbs.formatStored(addedLoad)}"
        }
        return "$load × $reps"
    }
    return "${WeightLbs.formatStored(set.weightLbs)} × $reps"
}

private fun formatVolume(volume: Int): String {
    return if (volume >= 1000) "${formatFixed(volume / 1000.0, 1)}k" else volume.toString()
}

/** Shared inclusive date bounds for overview comparisons and exercise charts. */
internal fun progressWindowStart(filter: TimeframeFilter, now: LocalDate): LocalDate = when (filter) {
    TimeframeFilter.TWO_WEEKS -> now.minusDays(13)
    TimeframeFilter.ONE_MONTH -> now.minusMonths(1)
    TimeframeFilter.THREE_MONTHS -> now.minusMonths(3)
    TimeframeFilter.SIX_MONTHS -> now.minusMonths(6)
    TimeframeFilter.ONE_YEAR -> now.minusYears(1)
    TimeframeFilter.ALL_TIME -> LocalDate(2000, 1, 1)
}

internal fun progressPointsInWindow(points: List<ProgressPointUi>, start: LocalDate, end: LocalDate) =
    points.filter { LocalDate.parse(it.date) in start..end }.sortedBy { it.date }

internal fun calculateWindowChangePercentage(points: List<ProgressPointUi>): Float {
    if (points.size < 2) return 0f
    val first = points.first().estimated1RM
    return if (first > 0f) (points.last().estimated1RM - first) / first * 100f else 0f
}

internal fun calculateProgressWindowStat(points: List<ProgressPointUi>, isBodyweight: Boolean, userBodyweight: Int? = null): PastMonthDelta? {
    if (points.size < 2) return null
    val first = points.first()
    val last = points.last()
    val weight = last.maxWeight - first.maxWeight
    val reps = last.repsAtTopWeight - first.repsAtTopWeight
    if (weight == 0 && reps != 0) return PastMonthDelta(
        signedRepsLabel(reps), reps.compareTo(0),
        if (isBodyweight) {
            val added = last.maxWeight
            if (added == 0) "at BW" else "at BW ${if (added > 0) "+" else "−"}${WeightLbs.formatStored(kotlin.math.abs(added))} lb"
        } else {
            "at ${WeightLbs.formatStored(last.maxWeight)} lb"
        },
    )
    if (weight != 0 && first.repsAtTopWeight == last.repsAtTopWeight) return PastMonthDelta(
        signedWeightLabel(weight), weight.compareTo(0), "at ${countLabel(last.repsAtTopWeight, "rep")}",
    )
    if (isBodyweight) return null
    val change = calculateWindowChangePercentage(points)
    val prefix = if (change > 0) "+" else ""
    return PastMonthDelta(
        if (kotlin.math.abs(change) < 0.05f) "0%" else "$prefix${formatFixed(change.toDouble(), 1)}%",
        if (kotlin.math.abs(change) < 0.05f) 0 else change.compareTo(0f), "Est. 1RM",
    )
}
