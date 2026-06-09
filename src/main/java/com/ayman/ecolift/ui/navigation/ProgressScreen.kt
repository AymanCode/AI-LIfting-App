package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.viewmodel.ProgressOrganizationMode
import com.ayman.ecolift.ui.viewmodel.ProgressViewModel
import com.ayman.ecolift.ui.viewmodel.TimeframeFilter
import java.time.LocalDate

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel = viewModel(),
    onOpenBackups: () -> Unit = {},
    initialExerciseId: Long? = null,
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialExerciseId) {
        if (initialExerciseId != null) {
            viewModel.selectExercise(initialExerciseId)
        }
    }

    if (uiState.selectedExerciseId != null) {
        val dataPoints = uiState.chartPoints.map { pt ->
            ExerciseDataPoint(
                date = LocalDate.parse(pt.date),
                estimatedOneRm = pt.estimated1RM,
                maxWeight = pt.maxWeight.toFloat(),
                totalVolume = pt.volume.toFloat()
            )
        }

        val range = when (uiState.timeframe) {
            TimeframeFilter.ONE_MONTH -> TimeRangeV2.ONE_MONTH
            TimeframeFilter.THREE_MONTHS -> TimeRangeV2.THREE_MONTHS
            TimeframeFilter.SIX_MONTHS -> TimeRangeV2.SIX_MONTHS
            TimeframeFilter.ONE_YEAR -> TimeRangeV2.ONE_YEAR
            TimeframeFilter.ALL_TIME -> TimeRangeV2.ALL
        }

        val metric = when (uiState.selectedMetric) {
            com.ayman.ecolift.ui.viewmodel.ProgressMetric.ESTIMATED_1RM -> ProgressMetricV2.ESTIMATED_1RM
            com.ayman.ecolift.ui.viewmodel.ProgressMetric.WEIGHT -> ProgressMetricV2.WEIGHT
            com.ayman.ecolift.ui.viewmodel.ProgressMetric.VOLUME -> ProgressMetricV2.VOLUME
        }

        val stats = uiState.stats

        ProgressDetailScreen(
            exerciseName = uiState.selectedExerciseName,
            muscleGroups = "Selected Exercise",
            dataPoints = dataPoints,
            selectedRange = range,
            selectedMetric = metric,
            currentPr = stats?.currentPrLbs ?: 0f,
            prDate = stats?.currentPrDate,
            estimatedOneRm = stats?.est1Rm?.toFloatOrNull() ?: 0f,
            totalVolume = stats?.totalVolumeLbs?.toFloat() ?: 0f,
            workoutCount = stats?.workoutCount ?: 0,
            onBack = { viewModel.selectExercise(null) },
            onRangeChange = { r ->
                viewModel.setTimeframe(
                    when (r) {
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
                        ProgressMetricV2.WEIGHT -> com.ayman.ecolift.ui.viewmodel.ProgressMetric.WEIGHT
                        ProgressMetricV2.VOLUME -> com.ayman.ecolift.ui.viewmodel.ProgressMetric.VOLUME
                    }
                )
            },
            paletteChoice = paletteChoice,
            onPaletteChoiceChange = onPaletteChoiceChange,
            modifier = modifier
        )
    } else {
        fun com.ayman.ecolift.ui.viewmodel.ProgressExerciseUi.toProgressExercise(): ProgressExercise =
            ProgressExercise(
                id = exerciseId,
                name = name,
                muscleGroups = "N/A",
                lastSetLabel = lastSessionSummary,
                trendPercent = changePercentage
            )

        val exercises = uiState.visibleExercises.map { it.toProgressExercise() }
        val splitPages = uiState.splitPages.map { page ->
            ProgressSplitPage(
                id = page.splitId,
                name = page.name,
                exercises = page.exercises.map { it.toProgressExercise() }
            )
        }

        ProgressScreen(
            exercises = exercises,
            organizationMode = when (uiState.organizationMode) {
                ProgressOrganizationMode.PROGRESS -> ProgressOrganizationModeV2.PROGRESS
                ProgressOrganizationMode.SPLIT -> ProgressOrganizationModeV2.SPLIT
            },
            splitPages = splitPages,
            selectedSplitIndex = uiState.selectedSplitIndex,
            searchQuery = uiState.searchQuery,
            onSearchChange = viewModel::setSearchQuery,
            onOrganizationModeChange = { mode ->
                viewModel.setOrganizationMode(
                    when (mode) {
                        ProgressOrganizationModeV2.PROGRESS -> ProgressOrganizationMode.PROGRESS
                        ProgressOrganizationModeV2.SPLIT -> ProgressOrganizationMode.SPLIT
                    }
                )
            },
            onSelectedSplitIndexChange = viewModel::setSelectedSplitIndex,
            onPreviousSplit = viewModel::showPreviousSplit,
            onNextSplit = viewModel::showNextSplit,
            onExerciseClick = { clicked ->
                val exId = clicked.id ?: uiState.exercises.find { it.name == clicked.name }?.exerciseId
                if (exId != null) {
                    viewModel.selectExercise(exId)
                }
            },
            paletteChoice = paletteChoice,
            onPaletteChoiceChange = onPaletteChoiceChange,
            modifier = modifier
        )
    }
}
