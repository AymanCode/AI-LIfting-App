package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.splits.SplitSwitchIntent
import com.ayman.ecolift.data.splits.SplitSwitchOutcome
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.LogGlassPalette
import com.ayman.ecolift.ui.theme.palette
import com.ayman.ecolift.ui.viewmodel.LogViewModel
import com.ayman.ecolift.web.LocalRepos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    initialSplitId: Long? = null,
    initialDate: String? = null,
    initialExerciseId: Long? = null,
    onReturnToProgress: (() -> Unit)? = null,
    onOpenExerciseProgress: (Long, String, Int) -> Unit = { _, _, _ -> },
    isReportOpen: () -> Boolean = { false },
    chromeReveal: ChromeRevealState = remember { ChromeRevealState() },
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    palette: LogGlassPalette = paletteChoice.palette(),
) {
    val repos = LocalRepos.current
    val viewModel: LogViewModel = viewModel { LogViewModel(repos) }
    var consumedInitialDate by rememberSaveable(initialDate) { mutableStateOf(false) }
    LaunchedEffect(initialDate) {
        if (!consumedInitialDate) {
            initialDate?.let(viewModel::selectDate)
            consumedInitialDate = true
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val restStopwatch = viewModel.restStopwatch.collectAsStateWithLifecycle()
    val restTimerSeconds = remember(restStopwatch) {
        derivedStateOf { restStopwatch.value?.elapsedSeconds }
    }
    val restExerciseId by remember(restStopwatch) {
        derivedStateOf { restStopwatch.value?.exerciseId }
    }
    val workedDays by viewModel.workedDays.collectAsStateWithLifecycle()
    val logGestureHintSessions by viewModel.logGestureHintSessions.collectAsStateWithLifecycle()
    val pendingSplitSwitch by viewModel.pendingSplitSwitch.collectAsStateWithLifecycle()
    val clipboardLifts by viewModel.clipboardLifts.collectAsStateWithLifecycle()
    val clipboardPrograms by viewModel.clipboardPrograms.collectAsStateWithLifecycle()
    val chosenEmptyDaySlot by viewModel.chosenEmptyDaySlot.collectAsStateWithLifecycle()
    val putAwayEmptyDay by viewModel.putAwayEmptyDay.collectAsStateWithLifecycle()
    val ticketInclusion by viewModel.ticketInclusion.collectAsStateWithLifecycle()
    // The new history projection has no collector (and no work) on a Log with cards.
    val emptyDayLedger = if (uiState.exercises.isEmpty()) {
        viewModel.emptyDayLedger.collectAsStateWithLifecycle().value
    } else {
        com.ayman.ecolift.ui.viewmodel.EmptyDayLedgerUi()
    }
    val attributedSlotId by viewModel.attributedSlotId.collectAsStateWithLifecycle()
    val dayUndo by viewModel.dayUndo.collectAsStateWithLifecycle()
    val exerciseSwap by viewModel.exerciseSwap.collectAsStateWithLifecycle()
    val exerciseSwapUndo by viewModel.exerciseSwapUndo.collectAsStateWithLifecycle()
    val swapUndoBusy by viewModel.swapUndoBusy.collectAsStateWithLifecycle()
    var consumedInitialSplit by rememberSaveable(initialSplitId) { mutableStateOf(false) }
    var initialSplitRequestId by remember(initialSplitId) { mutableStateOf<Long?>(null) }

    LaunchedEffect(initialSplitId, consumedInitialSplit) {
        val splitId = initialSplitId ?: return@LaunchedEffect
        if (consumedInitialSplit) return@LaunchedEffect
        viewModel.splitSwitchOutcomes
            .onSubscription {
                // The Splits tab's Load: seed-only, like the ticket's Start (spec 2026-09-28).
                viewModel.awaitSplitChoices(splitId)
                initialSplitRequestId = viewModel.loadProgram(
                    splitId,
                    emptySet(),
                    SplitSwitchIntent.DeepLinkLoad,
                )
            }
            .first { outcome ->
                outcome is SplitSwitchOutcome.Applied &&
                    outcome.requestId == initialSplitRequestId
            }
        consumedInitialSplit = true
        initialSplitRequestId = null
    }

    // The day's group comes from its checked sets (the model), not from what was loaded.
    val selectedSplitId = attributedSlotId
    val cycleSlotLabel = clipboardPrograms.firstOrNull { it.slotId == attributedSlotId }?.name
        ?: uiState.cycleSlot?.shortLabel

    val exercises = remember(uiState.exercises) {
        uiState.exercises.map { ex ->
            ExerciseLog(
                cardId = ex.cardId,
                cardPosition = ex.cardPosition,
                displayMode = ex.displayMode,
                exerciseId = ex.exerciseId,
                exerciseName = ex.name,
                muscleGroups = ex.muscleGroups,
                showTakenChip = ex.showTakenChip,
                takenGroupKey = ex.takenGroupKey,
                takenChipLabel = ex.takenChipLabel,
                suggestedMuscleGroup = ex.suggestedMuscleGroup,
                lastTopSet = ex.lastTopSet,
                bestTopSet = ex.bestTopSet,
                showBaselineHint = ex.showBaselineHint,
                isNewPB = ex.isNewPB,
                recommendationState = ex.recommendationState,
                ghostSets = ex.ghostSets,
                lastSessionDate = ex.lastSessionDate,
                e1rmHistory = ex.e1rmHistory,
                userBodyweightLbs = ex.userBodyweightLbs,
                sets = ex.sets.map { set ->
                    LoggedSet(
                        id = set.id,
                        syncId = set.syncId,
                        exerciseId = set.exerciseId,
                        setNumber = set.setNumber,
                        weight = WeightLbs.formatStored(set.weightLbs),
                        reps = set.reps?.toString() ?: "",
                        suggestedWeight = set.suggestedWeightLbs?.let(WeightLbs::formatStored),
                        suggestedReps = set.suggestedReps?.toString(),
                        hasAssistSuggestion = set.hasAssistSuggestion,
                        inputWarning = set.inputWarning,
                        isBodyweight = set.isBodyweight,
                        isCompleted = set.completed,
                        isWarmup = set.isWarmup,
                        restSeconds = set.restAfterSeconds
                    )
                }
            )
        }
    }

    val totalSets = uiState.exercises.sumOf { it.sets.size }
    val totalVolumeLbs = uiState.exercises.sumOf { ex ->
        ex.sets.filter { it.completed }.sumOf { set -> WeightLbs.toLbs(set.weightLbs) * (set.reps ?: 0) }
    }.roundToInt()

    val searchResults = uiState.predictiveExercises.map { ex ->
        ExerciseSearchResult(
            exerciseId = ex.id,
            name = ex.name,
            muscleGroups = ex.muscleGroups
        )
    }

    val unsavedInputs by viewModel.unsavedSetInputs.collectAsStateWithLifecycle()
    val unsavedEditing = remember(unsavedInputs) {
        UnsavedSetEditing(unsavedInputs, viewModel::retryUnsavedSet, viewModel::discardUnsavedSet)
    }
    CompositionLocalProvider(LocalUnsavedSetEditing provides unsavedEditing) {
        LogScreen(
            initialExerciseId = initialExerciseId,
            onReturnToProgress = onReturnToProgress,
            onOpenExerciseProgress = { id, number -> onOpenExerciseProgress(id, uiState.currentDate, number) },
            isReportOpen = isReportOpen,
            currentDate = uiState.currentDate,
            dateLabel = uiState.currentDateLabel,
            cycleSlotLabel = cycleSlotLabel,
            selectedSplitId = selectedSplitId,
            exercises = exercises,
            searchQuery = uiState.exerciseInput,
            searchResults = searchResults,
            pendingExerciseResolution = uiState.pendingExerciseResolution,
            clipboardLifts = clipboardLifts,
            exerciseSwap = exerciseSwap,
            exerciseSwapUndo = exerciseSwapUndo,
            swapUndoBusy = swapUndoBusy,
            onOpenExerciseSwap = viewModel::openExerciseSwap,
            onSwapQuery = viewModel::changeSwapQuery,
            onSearchSwapLibrary = viewModel::searchSwapLibrary,
            onDismissExerciseSwap = viewModel::dismissExerciseSwap,
            onSwapExercise = viewModel::swapExercise,
            onUndoExerciseSwap = viewModel::undoExerciseSwap,
            clipboardPrograms = clipboardPrograms,
            chosenEmptyDaySlot = chosenEmptyDaySlot,
            onChooseEmptyDaySlot = viewModel::chooseEmptyDaySlot,
            putAwayEmptyDay = putAwayEmptyDay,
            onPutAwayEmptyDay = viewModel::putAwayEmptyDay,
            onBringBackEmptyDay = viewModel::bringBackEmptyDay,
            onCheckClipboardPick = viewModel::checkClipboardPick,
            onAddClipboardPicks = viewModel::addClipboardPicks,
            onClipProgram = { slotId, skipped -> viewModel.loadProgram(slotId, skipped) },
            ticketInclusion = ticketInclusion,
            emptyDayLedger = emptyDayLedger,
            onSwapTicketRow = { program, usual, chosen -> viewModel.swapTicketRow(uiState.currentDate, program, usual, chosen) },
            onDropTicketRow = { program, usual -> viewModel.dropTicketRow(uiState.currentDate, program, usual) },
            onUndoTicketSwap = { program, usual -> viewModel.undoTicketSwap(uiState.currentDate, program, usual) },
            onToggleTicketRow = { program, exerciseId -> viewModel.toggleTicketRow(uiState.currentDate, program, exerciseId) },
            onStartProposal = { program -> viewModel.startProposal(uiState.currentDate, program) },
            onRenameProposal = viewModel::renameProposal,
            onRetireSplit = viewModel::retireSplit,
            totalSets = totalSets,
            totalVolumeLbs = totalVolumeLbs,
            workedDays = workedDays,
            onDateSelected = viewModel::selectDate,
            onPreviousDay = viewModel::goToPreviousDay,
            onNextDay = viewModel::goToNextDay,
            onRefresh = viewModel::refreshWorkoutFromCloud,
            pendingSplitSwitch = pendingSplitSwitch,
            onClearSplit = viewModel::clearCycleSlot,
            onConfirmSplitSwitch = viewModel::confirmSplitSwitch,
            onCancelSplitSwitch = viewModel::cancelSplitSwitch,
            onSearchQueryChange = viewModel::updateExerciseInput,
            onConfirmExercisesSame = viewModel::confirmExercisesSame,
            onConfirmExercisesDifferent = viewModel::confirmExercisesDifferent,
            onAddSet = viewModel::addSet,
            onAddSetFrom = { exerciseId, sourceSetId -> viewModel.addSetFrom(exerciseId, sourceSetId) },
            onCompleteSet = viewModel::toggleCompleted,
            onWeightChange = viewModel::updateWeight,
            onWeightStep = viewModel::adjustWeight,
            onRepsChange = viewModel::updateReps,
            onRepsStep = viewModel::adjustReps,
            onToggleBodyweight = viewModel::toggleBodyweight,
            onToggleWarmup = viewModel::toggleWarmup,
            onSetFocused = viewModel::focusSetInput,
            onFinishExercise = viewModel::finishExercise,
            onCardDisplayModeChange = viewModel::updateCardDisplayMode,
            onMuscleGroupChange = viewModel::updateExerciseMuscleGroup,
            restTimerSeconds = restTimerSeconds,
            restExerciseId = restExerciseId,
            onCancelRestTimer = viewModel::cancelRestTimer,
            onAssistSuggestionConsumed = viewModel::onAssistSuggestionConsumed,
            onRecommend = viewModel::requestRecommendations,
            onCheckInput = { setId, exerciseId, weightStored, reps, isBodyweight ->
                viewModel.checkSetInput(setId, exerciseId, weightStored, reps, isBodyweight)
            },
            onDismissInputWarning = viewModel::dismissInputWarning,
            logGestureHintSessions = logGestureHintSessions,
            onGestureHintShown = viewModel::recordLogGestureHintShown,
            uiMessage = uiState.uiMessage,
            onUiMessageShown = viewModel::consumeUiMessage,
            dayUndo = dayUndo,
            onUndoDayChange = viewModel::undoDayChange,
            onDismissDayUndo = viewModel::dismissDayUndo,
            onDeleteSets = viewModel::deleteSets,
            chromeReveal = chromeReveal,
            paletteChoice = paletteChoice,
            onPaletteChoiceChange = onPaletteChoiceChange,
            palette = palette,
            modifier = modifier
        )
    }
}
