package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.viewmodel.ProgressViewModel
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

        val stats = uiState.stats

        ProgressDetailScreen(
            exerciseName = uiState.selectedExerciseName,
            muscleGroups = uiState.selectedExerciseMuscleGroups,
            dataPoints = dataPoints,
            selectedRange = uiState.timeframe,
            selectedMetric = uiState.selectedMetric,
            currentPr = stats?.currentPrLbs ?: 0f,
            bestSetLabel = stats?.bestSetLabel.orEmpty(),
            prDate = stats?.currentPrDate,
            estimatedOneRm = stats?.est1Rm?.toFloatOrNull() ?: 0f,
            totalVolume = stats?.totalVolumeLbs?.toFloat() ?: 0f,
            workoutCount = stats?.workoutCount ?: 0,
            onBack = { viewModel.selectExercise(null) },
            onRangeChange = viewModel::setTimeframe,
            onMetricChange = viewModel::setMetric,
            onMuscleGroupChange = viewModel::updateSelectedExerciseMuscleGroup,
            paletteChoice = paletteChoice,
            onPaletteChoiceChange = onPaletteChoiceChange,
            modifier = modifier
        )
    } else {
        fun com.ayman.ecolift.ui.viewmodel.ProgressExerciseUi.toProgressExercise(): ProgressExercise =
            ProgressExercise(
                id = exerciseId,
                name = name,
                muscleGroups = muscleGroups,
                lastSetLabel = lastSessionSummary,
                trendPercent = changePercentage,
                sessions = sessions
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
            organizationMode = uiState.organizationMode,
            splitPages = splitPages,
            selectedSplitIndex = uiState.selectedSplitIndex,
            searchQuery = uiState.searchQuery,
            onSearchChange = viewModel::setSearchQuery,
            onOrganizationModeChange = viewModel::setOrganizationMode,
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
