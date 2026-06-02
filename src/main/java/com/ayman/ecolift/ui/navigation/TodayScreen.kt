package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.LogViewModel
import com.ayman.ecolift.data.WeightLbs
import androidx.compose.ui.Modifier

@Composable
fun TodayScreen(
    viewModel: LogViewModel = viewModel(),
    modifier: Modifier = Modifier,
    initialSplitId: Long? = null,
    chromeReveal: ChromeRevealState = remember { ChromeRevealState() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val workedDays by viewModel.workedDays.collectAsStateWithLifecycle()
    var consumedInitialSplit by rememberSaveable(initialSplitId) { mutableStateOf(false) }

    LaunchedEffect(initialSplitId) {
        if (initialSplitId != null && !consumedInitialSplit) {
            viewModel.assignCycleSlot(initialSplitId)
            consumedInitialSplit = true
        }
    }

    val cycleSlotLabel = uiState.cycleSlot?.shortLabel
    val splits = uiState.cycleOptions.map { option ->
        SplitSlot(
            id = option.type.toLong(),
            displayName = option.label,
            slotLabel = if (option.isExpected) "Suggested" else "Saved",
            isExpected = option.isExpected
        )
    }

    val selectedSplitId = uiState.cycleSlot?.type?.toLong()

    val exercises = uiState.exercises.map { ex ->
        ExerciseLog(
            exerciseId = ex.exerciseId,
            exerciseName = ex.name,
            muscleGroups = ex.muscleGroups,
            previousSession = ex.lastSessionHint,
            isNewPB = ex.isNewPB,
            sets = ex.sets.map { set ->
                LoggedSet(
                    setNumber = set.setNumber,
                    weight = WeightLbs.formatStored(set.weightLbs),
                    reps = set.reps?.toString() ?: "",
                    suggestedWeight = set.suggestedWeightLbs?.let(WeightLbs::formatStored),
                    suggestedReps = set.suggestedReps?.toString(),
                    isBodyweight = set.isBodyweight,
                    isCompleted = set.completed,
                    restSeconds = set.restAfterSeconds
                )
            }
        )
    }

    val totalSets = uiState.exercises.sumOf { it.sets.size }
    val totalVolumeLbs = uiState.exercises.sumOf { ex ->
        ex.sets.filter { it.completed }.sumOf { set ->
            ((set.weightLbs ?: 0) * (set.reps ?: 0))
        }
    }

    val searchResults = uiState.predictiveExercises.map { ex ->
        ExerciseSearchResult(
            name = ex.name,
            muscleGroups = ex.muscleGroups
        )
    }

    LogScreen(
        currentDate = uiState.currentDate,
        dateLabel = uiState.currentDateLabel,
        cycleSlotLabel = cycleSlotLabel,
        splits = splits,
        selectedSplitId = selectedSplitId,
        exercises = exercises,
        searchQuery = uiState.exerciseInput,
        searchResults = searchResults,
        isSearchActive = uiState.exerciseInput.isNotBlank(),
        totalSets = totalSets,
        totalVolumeLbs = totalVolumeLbs,
        workedDays = workedDays,
        onDateSelected = viewModel::selectDate,
        onPreviousDay = viewModel::goToPreviousDay,
        onNextDay = viewModel::goToNextDay,
        onSelectSplit = { id -> if (id != null) viewModel.assignCycleSlot(id) },
        onSearchQueryChange = viewModel::updateExerciseInput,
        onAddExercise = { result ->
            if (result.muscleGroups == "CUSTOM") {
                viewModel.addExerciseFromInput()
            } else {
                val originalEx = uiState.predictiveExercises.find { it.name == result.name }
                if (originalEx != null) {
                    viewModel.useSuggestion(originalEx)
                } else {
                    viewModel.updateExerciseInput(result.name)
                    viewModel.addExerciseFromInput()
                }
            }
        },
        onAddSet = { exIndex -> viewModel.addSet(uiState.exercises[exIndex].exerciseId) },
        onCompleteSet = { exIndex, setIndex ->
            viewModel.toggleCompleted(uiState.exercises[exIndex].sets[setIndex].id)
        },
        onDeleteSet = { exIndex, setIndex ->
            viewModel.deleteSet(uiState.exercises[exIndex].sets[setIndex].id)
        },
        onWeightChange = { exIndex, setIndex, value ->
            viewModel.updateWeight(uiState.exercises[exIndex].sets[setIndex].id, value)
        },
        onWeightStep = { exIndex, setIndex, delta ->
            viewModel.adjustWeight(uiState.exercises[exIndex].sets[setIndex].id, delta)
        },
        onRepsChange = { exIndex, setIndex, value ->
            viewModel.updateReps(uiState.exercises[exIndex].sets[setIndex].id, value)
        },
        onRepsStep = { exIndex, setIndex, delta ->
            viewModel.adjustReps(uiState.exercises[exIndex].sets[setIndex].id, delta)
        },
        onToggleBodyweight = { exIndex, setIndex ->
            viewModel.toggleBodyweight(uiState.exercises[exIndex].sets[setIndex].id)
        },
        onSetFocused = { exIndex, setIndex ->
            viewModel.focusSetInput(uiState.exercises[exIndex].sets[setIndex].id)
        },
        onFinishExercise = { exIndex ->
            viewModel.finishExercise(uiState.exercises[exIndex].exerciseId)
        },
        onMuscleGroupChange = { exIndex, muscleGroup ->
            viewModel.updateExerciseMuscleGroup(uiState.exercises[exIndex].exerciseId, muscleGroup)
        },
        restTimerSeconds = uiState.restStopwatchSeconds,
        onCancelRestTimer = viewModel::cancelRestTimer,
        chromeReveal = chromeReveal,
        modifier = modifier
    )
}
