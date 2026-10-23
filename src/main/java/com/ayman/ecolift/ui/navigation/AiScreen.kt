package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ayman.ecolift.ui.viewmodel.DeskDecisionUi
import com.ayman.ecolift.web.demo.DemoGuard
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.hub.HubSwipe
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.viewmodel.AiChangesCardUi
import com.ayman.ecolift.ui.viewmodel.AiHistoryCardUi
import com.ayman.ecolift.ui.viewmodel.HubViewModel
import com.ayman.ecolift.ui.viewmodel.OrchestratorViewModel
import com.ayman.ecolift.web.LocalAppServices
import com.ayman.ecolift.web.LocalRepos
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun AiScreen(
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    userBodyweightLbs: Int? = null,
    onUserBodyweightChange: (Int?) -> Unit = {},
    smartSuggestEnabled: Boolean = true,
    onSmartSuggestChange: (Boolean) -> Unit = {},
    openSettingsOnStart: Boolean = false,
    onSettingsOpenedOnStart: () -> Unit = {},
    onOpenNoticed: (Long) -> Unit = {},
    onOpenGroup: (Long) -> Unit = {},
    sharedHubViewModel: HubViewModel? = null,
    modifier: Modifier = Modifier
) {
    val repos = LocalRepos.current
    val services = LocalAppServices.current
    val viewModel: OrchestratorViewModel = viewModel {
        OrchestratorViewModel(repos, services.genAiEngine())
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The hub lives on this screen, so its state is collected here rather than in
    // AppNavigation, which only needs the badge count.
    val hubViewModel: HubViewModel = sharedHubViewModel ?: viewModel { HubViewModel(repos) }
    val hubBuild by hubViewModel.build.collectAsStateWithLifecycle()
    val desk by hubViewModel.desk.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<DeskDecisionUi?>(null) }
    var retireTarget by remember { mutableStateOf<DeskDecisionUi?>(null) }
    LaunchedEffect(viewModel, hubViewModel) {
        viewModel.sessionMessages.collect(hubViewModel::updateSessionMessages)
    }
    val hubExpandedItemId by hubViewModel.expandedItemId.collectAsStateWithLifecycle()
    val hubTopSets by hubViewModel.topSets.collectAsStateWithLifecycle()
    val claudeChanges by hubViewModel.claudeChanges.collectAsStateWithLifecycle()
    val hubMessage by hubViewModel.hubMessage.collectAsStateWithLifecycle()
    // Opening IronMind is the only time the list is fetched, and it marks the changes as seen.
    LaunchedEffect(Unit) { hubViewModel.loadClaudeChanges() }

    // The smart-split slips left with learned splits (spec 2026-09-28): a deep link still pending
    // from before opens the hub with nothing expanded, so it is only cleared.
    LaunchedEffect(Unit) { IronMindDeepLink.clear() }

    val mappedMessages = uiState.messages.map { msg ->
        if (msg.isUser) {
            IronMindMessage.UserMessage(
                text = msg.text,
                timestamp = clockLabel(msg.timestampMillis),
                timestampMillis = msg.timestampMillis,
                messageId = msg.id,
            )
        } else {
            IronMindMessage.AiMessage(
                text = msg.text,
                timestamp = clockLabel(msg.timestampMillis),
                timestampMillis = msg.timestampMillis,
                card = msg.card?.toIronMindCard() ?: if (uiState.pendingAction != null && msg == uiState.messages.last()) {
                    StatsPayload(
                        exerciseName = "Needs review",
                        pr = null,
                        estimatedOneRm = null,
                        lastSession = uiState.pendingAction?.title,
                        sessionCount = null
                    )
                } else null,
                recovery = msg.recovery?.let {
                    RecoveryPayload(
                        title = it.title,
                        detail = it.detail,
                        originalText = it.originalText,
                        suggestedTemplate = it.suggestedTemplate,
                        saveDate = it.saveDate,
                        canTryModel = it.canTryModel
                    )
                }
            )
        }
    }

    val quickActions = uiState.shortcuts.map { shortcut ->
        QuickAction(label = shortcut.title, query = shortcut.prompt)
    }

    IronMindScreen(
        // Only an abnormal state earns a line in the header. A healthy model reports nothing,
        // because "Ready" was a permanent label that told the user something they could assume.
        sessionLabel = when {
            uiState.statusHeadline.isNotBlank() -> uiState.statusHeadline
            uiState.isModelReady -> null
            else -> "AI unavailable"
        },
        messages = mappedMessages,
        quickActions = quickActions,
        inputQuery = uiState.input,
        isThinking = uiState.isWorking,
        onQueryChange = viewModel::updateInput,
        onSend = viewModel::sendMessage,
        onQuickAction = { action ->
            viewModel.applyShortcut(action.query)
            viewModel.sendMessage()
        },
        onRecoveryAction = { recovery, action ->
            when (action) {
                RecoveryAction.EditOriginal -> viewModel.editRecoveryDraft(recovery.originalText)
                RecoveryAction.UseTemplate -> viewModel.useRecoveryTemplate(recovery.suggestedTemplate)
                RecoveryAction.SaveForReview -> viewModel.saveRecoveryForReview(
                    originalText = recovery.originalText,
                    saveDate = recovery.saveDate
                )
                RecoveryAction.TryAi -> viewModel.tryRecoveryWithModel(recovery.originalText)
            }
        },
        onSettings = { },
        accountEmail = null,
        onSignOut = { },
        hubBuild = hubBuild,
        desk = desk,
        onKeepGroup = { if (!DemoGuard.blockIfDemo()) renameTarget = it },
        onRetireGroup = { if (!DemoGuard.blockIfDemo()) retireTarget = it },
        onOpenGroup = onOpenGroup,
        onOpenNoticed = onOpenNoticed,
        hubExpandedItemId = hubExpandedItemId,
        hubTopSets = hubTopSets,
        onHubToggleExpand = hubViewModel::toggleExpanded,
        onHubSwipe = hubViewModel::dismiss,
        onHubSetValueChange = hubViewModel::updateSetValues,
        onHubToggleChecked = hubViewModel::setChecked,
        onHubRemoveSet = hubViewModel::removeSet,
        onHubAddSet = hubViewModel::addSet,
        onHubConfirm = { hubViewModel.dismiss(it, HubSwipe.Confirm) },
        onHubClearOpenSets = { hubViewModel.dismiss(it, HubSwipe.Clear) },
        hubMessage = hubMessage,
        onHubMessageShown = hubViewModel::consumeHubMessage,
        claudeChanges = claudeChanges,
        onUndoClaudeChange = hubViewModel::undoClaudeChange,
        paletteChoice = paletteChoice,
        onPaletteChoiceChange = onPaletteChoiceChange,
        userBodyweightLbs = userBodyweightLbs,
        onUserBodyweightChange = onUserBodyweightChange,
        smartSuggestEnabled = smartSuggestEnabled,
        onSmartSuggestChange = onSmartSuggestChange,
        openSettingsOnStart = openSettingsOnStart,
        onSettingsOpenedOnStart = onSettingsOpenedOnStart,
        modifier = modifier
    )
    renameTarget?.let { target ->
        RenameSplitSheet(initialName = "", onDismiss = { renameTarget = null }, onConfirm = { name ->
            hubViewModel.nameDeskGroup(target.slotId, name)
            renameTarget = null
        })
    }
    retireTarget?.let { target ->
        ConfirmRetireDialog(splitName = target.title, onDismiss = { retireTarget = null }, onConfirm = {
            hubViewModel.retireDeskGroup(target.slotId)
            retireTarget = null
        })
    }
}

private fun com.ayman.ecolift.ui.viewmodel.AiMessageCardUi.toIronMindCard(): IronMindCardPayload =
    when (this) {
        is AiHistoryCardUi -> HistoryCardPayload(
            exerciseName = exerciseName,
            prLine = prLine,
            sessions = sessions.map { HistoryCardRowPayload(it.date, it.setLabel) },
        )
        is AiChangesCardUi -> ChangesCardPayload(changes)
    }

@OptIn(kotlin.time.ExperimentalTime::class)
private fun clockLabel(epochMillis: Long): String {
    val time = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .time
    val hour12 = ((time.hour + 11) % 12) + 1
    val minutes = time.minute.toString().padStart(2, '0')
    val amPm = if (time.hour < 12) "AM" else "PM"
    return "$hour12:$minutes $amPm"
}
