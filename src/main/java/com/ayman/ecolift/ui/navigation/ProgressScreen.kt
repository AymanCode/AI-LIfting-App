package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.viewmodel.ProgressOrganizationMode
import com.ayman.ecolift.ui.viewmodel.ProgressProjection
import com.ayman.ecolift.ui.viewmodel.ProgressScope
import com.ayman.ecolift.ui.viewmodel.ProgressSortMode
import com.ayman.ecolift.ui.viewmodel.ProgressUiState
import com.ayman.ecolift.ui.viewmodel.ProgressViewModel
import com.ayman.ecolift.ui.viewmodel.TimeframeFilter
import com.ayman.ecolift.ui.viewmodel.progressWindowStart
import com.ayman.ecolift.web.LocalAppServices
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate

@Composable
fun ProgressScreen(
    onOpenBackups: () -> Unit = {},
    onOpenWorkout: (String, Long) -> Unit = { _, _ -> },
    onOpenHighlightCard: (com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot, Int) -> Unit = { _, _ -> },
    initialExerciseId: Long? = null,
    reportOrigin: ExerciseReportOrigin = ExerciseReportOrigin(),
    onReportBack: (() -> Unit)? = null,
    onOpenExerciseReport: ((Long, String, String) -> Unit)? = null,
    onOpenMonthReport: ((Long, String, String) -> Unit)? = null,
    onOpenEmphasis: () -> Unit = {},
    initialReportRange: String? = null,
    initialReportEnd: String? = null,
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val repos = LocalRepos.current
    val services = LocalAppServices.current
    val viewModel: ProgressViewModel = viewModel {
        ProgressViewModel(repos, services.genAiEngine(), initialTimeframe =
            initialReportRange?.let { value -> TimeframeFilter.entries.firstOrNull { it.name == value } }
                ?: if (initialExerciseId != null) TimeframeFilter.THREE_MONTHS else TimeframeFilter.ONE_MONTH,
            scope = initialExerciseId?.let { ProgressScope.Report(it) } ?: ProgressScope.Overview)
    }
    DisposableEffect(viewModel, isVisible) {
        viewModel.setVisible(isVisible)
        onDispose { viewModel.setVisible(false) }
    }
    val projection by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = (projection as? ProgressProjection.Ready)?.state ?: ProgressUiState()
    val assistEnabled by viewModel.assistEnabled.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val highlights = uiState.highlights

    var consumedInitialReport by rememberSaveable(initialExerciseId) { mutableStateOf(false) }
    LaunchedEffect(initialExerciseId) {
        if (initialExerciseId != null && !consumedInitialReport) {
            initialReportRange?.let { value ->
                TimeframeFilter.values().firstOrNull { it.name == value }?.let(viewModel::setTimeframe)
            }
            initialReportEnd?.let { value ->
                runCatching { LocalDate.parse(value) }.getOrNull()?.let(viewModel::setWindowEnd)
            }
            viewModel.selectExercise(initialExerciseId)
            consumedInitialReport = true
        }
    }

    val range = when (uiState.timeframe) {
        TimeframeFilter.TWO_WEEKS -> TimeRangeV2.TWO_WEEKS
        TimeframeFilter.ONE_MONTH -> TimeRangeV2.ONE_MONTH
        TimeframeFilter.THREE_MONTHS -> TimeRangeV2.THREE_MONTHS
        TimeframeFilter.SIX_MONTHS -> TimeRangeV2.SIX_MONTHS
        TimeframeFilter.ONE_YEAR -> TimeRangeV2.ONE_YEAR
        TimeframeFilter.ALL_TIME -> TimeRangeV2.ALL
    }
    fun changeRange(r: TimeRangeV2) = viewModel.setTimeframe(when (r) {
        TimeRangeV2.TWO_WEEKS -> TimeframeFilter.TWO_WEEKS
        TimeRangeV2.ONE_MONTH -> TimeframeFilter.ONE_MONTH
        TimeRangeV2.THREE_MONTHS -> TimeframeFilter.THREE_MONTHS
        TimeRangeV2.SIX_MONTHS -> TimeframeFilter.SIX_MONTHS
        TimeRangeV2.ONE_YEAR -> TimeframeFilter.ONE_YEAR
        TimeRangeV2.ALL -> TimeframeFilter.ALL_TIME
    })
    val windowEnd = uiState.windowEndIsoDate?.let(LocalDate::parse) ?: todayLocalDate()
    val overviewStateHolder = rememberSaveableStateHolder()
    var isMonthSelected by rememberSaveable { mutableStateOf(false) }
    val windowStart = if (uiState.timeframe == TimeframeFilter.ALL_TIME) {
        uiState.exercises.mapNotNull { it.firstSessionIsoDate }.minOrNull()?.let(LocalDate::parse) ?: windowEnd
    } else {
        progressWindowStart(uiState.timeframe, windowEnd)
    }

    if (initialExerciseId != null || uiState.selectedExerciseId != null) {
        val dataPoints = uiState.chartPoints.map { pt ->
            ExerciseDataPoint(
                date = LocalDate.parse(pt.date),
                estimatedOneRm = pt.estimated1RM,
                maxWeight = com.ayman.ecolift.data.WeightLbs.toLbs(pt.maxWeight).toFloat(),
                totalVolume = pt.volume.toFloat(),
                maxReps = pt.maxReps.toFloat(),
                estimateEvidence = pt.estimateEvidence,
                repEvidence = pt.repEvidence,
                volumeEvidence = "${countLabel(pt.workingSetCount, "working set")} · ${countLabel(pt.totalWorkingReps, "rep")}",
                evidence = (if (uiState.isBodyweight) "BW" + (if (pt.maxWeight > 0) " + ${com.ayman.ecolift.data.WeightLbs.formatStored(pt.maxWeight)} lb" else "")
                    else "${com.ayman.ecolift.data.WeightLbs.formatStored(pt.maxWeight)} lb") + " × ${pt.repsAtTopWeight}",
            )
        }

        val range = when (uiState.timeframe) {
            TimeframeFilter.TWO_WEEKS -> TimeRangeV2.TWO_WEEKS
            TimeframeFilter.ONE_MONTH -> TimeRangeV2.ONE_MONTH
            TimeframeFilter.THREE_MONTHS -> TimeRangeV2.THREE_MONTHS
            TimeframeFilter.SIX_MONTHS -> TimeRangeV2.SIX_MONTHS
            TimeframeFilter.ONE_YEAR -> TimeRangeV2.ONE_YEAR
            TimeframeFilter.ALL_TIME -> TimeRangeV2.ALL
        }

        val metric = when (uiState.selectedMetric) {
            com.ayman.ecolift.ui.viewmodel.ProgressMetric.ESTIMATED_1RM -> if (uiState.isBodyweight) ProgressMetricV2.REPS else ProgressMetricV2.ESTIMATED_1RM
            com.ayman.ecolift.ui.viewmodel.ProgressMetric.REPS -> ProgressMetricV2.REPS
            com.ayman.ecolift.ui.viewmodel.ProgressMetric.WEIGHT -> ProgressMetricV2.WEIGHT
            com.ayman.ecolift.ui.viewmodel.ProgressMetric.VOLUME -> ProgressMetricV2.VOLUME
        }

        val stats = uiState.stats

        val selectedId = uiState.selectedExerciseId

        ProgressDetailScreen(
            exerciseName = uiState.selectedExerciseName,
            muscleGroups = uiState.selectedExerciseMuscleGroups,
            suggestedMuscleGroup = uiState.selectedExerciseSuggestedMuscleGroup,
            dataPoints = dataPoints,
            windowStart = windowStart,
            windowEnd = windowEnd,
            selectedRange = range,
            selectedMetric = metric,
            currentPr = stats?.currentPrLbs ?: 0f,
            bestSetLabel = stats?.bestSetLabel.orEmpty(),
            prDate = stats?.currentPrDate,
            estimatedOneRm = stats?.est1Rm?.toFloatOrNull() ?: 0f,
            totalVolume = stats?.totalVolumeLbs?.toFloat() ?: 0f,
            workoutCount = stats?.workoutCount ?: 0,
            isBodyweight = uiState.isBodyweight,
            userBodyweightLbs = uiState.userBodyweightLbs,
            report = uiState.report ?: com.ayman.ecolift.ui.viewmodel.ExerciseReport(),
            onBack = onReportBack ?: { viewModel.selectExercise(null) },
            origin = reportOrigin,
            onOpenWorkout = { date -> selectedId?.let { onOpenWorkout(date.toString(), it) } },
            onRangeChange = { r ->
                viewModel.setTimeframe(
                    when (r) {
                        TimeRangeV2.TWO_WEEKS -> TimeframeFilter.TWO_WEEKS
                        TimeRangeV2.ONE_MONTH -> TimeframeFilter.ONE_MONTH
                        TimeRangeV2.THREE_MONTHS -> TimeframeFilter.THREE_MONTHS
                        TimeRangeV2.SIX_MONTHS -> TimeframeFilter.SIX_MONTHS
                        TimeRangeV2.ONE_YEAR -> TimeframeFilter.ONE_YEAR
                        TimeRangeV2.ALL -> TimeframeFilter.ALL_TIME
                    }
                )
            },
            onMetricChange = { m ->
                viewModel.setMetric(
                    when (m) {
                        ProgressMetricV2.ESTIMATED_1RM -> com.ayman.ecolift.ui.viewmodel.ProgressMetric.ESTIMATED_1RM
                        ProgressMetricV2.REPS -> com.ayman.ecolift.ui.viewmodel.ProgressMetric.REPS
                        ProgressMetricV2.WEIGHT -> com.ayman.ecolift.ui.viewmodel.ProgressMetric.WEIGHT
                        ProgressMetricV2.VOLUME -> com.ayman.ecolift.ui.viewmodel.ProgressMetric.VOLUME
                    }
                )
            },
            onMuscleGroupChange = viewModel::updateSelectedExerciseMuscleGroup,
            paletteChoice = paletteChoice,
            onPaletteChoiceChange = onPaletteChoiceChange,
            modifier = modifier,
            assistEnabled = assistEnabled,
            analysis = analysis,
            onToggleAssist = viewModel::toggleAssist,
            onAnalyze = { if (selectedId != null) viewModel.analyzeExercise(selectedId) },
            onClearAnalysis = viewModel::clearAnalysis,
            exerciseId = selectedId,
        )
    } else {
        // Overview-only subscriptions never start for a pushed report.
        val groupWall = uiState.groupWall
        val heldAvailable = uiState.heldAvailable
        val showAllLifts by viewModel.allLiftsShown.collectAsStateWithLifecycle()

        fun com.ayman.ecolift.ui.viewmodel.ProgressExerciseUi.toProgressExercise(): ProgressExercise =
            ProgressExercise(
                id = exerciseId,
                name = name,
                muscleGroups = muscleGroups,
                suggestedMuscleGroup = suggestedMuscleGroup,
                lastSetLabel = lastSessionSummary,
                trendPercent = changePercentage,
                rangeStatLabel = rangeStatLabel,
                rangeStatSign = rangeStatSign,
                rangeStatContext = rangeStatContext,
                firstSessionIsoDate = firstSessionIsoDate,
                monthDeltaLabel = monthDeltaLabel,
                monthDeltaSign = monthDeltaSign,
                monthDeltaSubLabel = monthDeltaSubLabel,
                sessions = sessions,
                lastSessionDate = lastSessionDate,
                lastSessionIsoDate = lastSessionIsoDate,
                trend = trend,
                overview = overview,
                report = report,
                isBodyweight = isBodyweight,
            )

        val exercises = uiState.visibleExercises.map { it.toProgressExercise() }

        // MONTH is a UI-only segment: the sheet has its own view model and doesn't participate
        // in ProgressViewModel's organizationMode (which drives exercise filtering/sorting), so
        // it's tracked here rather than added to that enum.

        fun openHighlights() {
            // Highlights is the stack mode. A card pushes the immersive reel above this state.
            isMonthSelected = false
            viewModel.setOrganizationMode(ProgressOrganizationMode.HIGHLIGHTS)
        }

        overviewStateHolder.SaveableStateProvider("overview") {
            ProgressScreen(
                exercises = exercises,
                isPending = projection == ProgressProjection.Pending,
                windowStart = windowStart,
                windowEnd = windowEnd,
                onWindowEndChange = viewModel::setWindowEnd,
                selectedRange = range,
                onRangeChange = ::changeRange,
                hero = uiState.hero,
                heroSessionsThisMonthLabel = uiState.heroSessionsThisMonthLabel,
                organizationMode = if (isMonthSelected) {
                    ProgressOrganizationModeV2.MONTH
                } else {
                    when (uiState.organizationMode) {
                        ProgressOrganizationMode.PROGRESS -> ProgressOrganizationModeV2.PROGRESS
                        ProgressOrganizationMode.HIGHLIGHTS -> ProgressOrganizationModeV2.HIGHLIGHTS
                    }
                },
                sortMode = when (uiState.sortMode) {
                    ProgressSortMode.TREND -> ProgressSortModeV2.TREND
                    ProgressSortMode.RECENT -> ProgressSortModeV2.RECENT
                    ProgressSortMode.NAME -> ProgressSortModeV2.NAME
                    ProgressSortMode.SESSIONS -> ProgressSortModeV2.SESSIONS
                    ProgressSortMode.BODY_PART -> ProgressSortModeV2.BODY_PART
                },
                onSortModeChange = { mode ->
                    viewModel.setSortMode(
                        when (mode) {
                            ProgressSortModeV2.TREND -> ProgressSortMode.TREND
                            ProgressSortModeV2.RECENT -> ProgressSortMode.RECENT
                            ProgressSortModeV2.NAME -> ProgressSortMode.NAME
                            ProgressSortModeV2.SESSIONS -> ProgressSortMode.SESSIONS
                            ProgressSortModeV2.BODY_PART -> ProgressSortMode.BODY_PART
                        }
                    )
                },
                searchQuery = uiState.searchQuery,
                onSearchChange = viewModel::setSearchQuery,
                onMuscleGroupChange = viewModel::updateExerciseMuscleGroup,
                onOrganizationModeChange = { mode ->
                    when (mode) {
                        ProgressOrganizationModeV2.MONTH -> {
                            isMonthSelected = true
                        }
                        ProgressOrganizationModeV2.PROGRESS -> {
                            isMonthSelected = false
                            viewModel.setOrganizationMode(ProgressOrganizationMode.PROGRESS)
                        }
                        ProgressOrganizationModeV2.HIGHLIGHTS -> {
                            openHighlights()
                        }
                    }
                },
                onOpenHighlights = ::openHighlights,
                highlights = highlights,
                onOpenHighlightCard = { page -> onOpenHighlightCard(highlights, page) },
                onOpenEmphasis = onOpenEmphasis,
                onOpenMonthReport = { id, end ->
                    if (onOpenMonthReport != null) onOpenMonthReport(id, TimeframeFilter.ONE_MONTH.name, end)
                    else viewModel.selectExercise(id)
                },
                onExerciseClick = { clicked ->
                    val exId = clicked.id ?: uiState.exercises.find { it.name == clicked.name }?.exerciseId
                    if (exId != null) {
                        if (onOpenExerciseReport != null) onOpenExerciseReport(exId, uiState.timeframe.name, windowEnd.toString()) else viewModel.selectExercise(exId)
                    }
                },
                paletteChoice = paletteChoice,
                onPaletteChoiceChange = onPaletteChoiceChange,
                groupWall = groupWall,
                heldAvailable = heldAvailable,
                showAllLifts = showAllLifts,
                onShowAllLiftsChange = viewModel::setShowAllLifts,
                // A tile pushes a report; the overview back-stack entry keeps its saved list state.
                onOpenReport = { id -> if (onOpenExerciseReport != null) onOpenExerciseReport(id, uiState.timeframe.name, windowEnd.toString()) else viewModel.selectExercise(id) },
                modifier = modifier
            )
        }
    }
}
