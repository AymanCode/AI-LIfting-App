package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.OrchestratorViewModel
import java.time.format.DateTimeFormatter
import java.time.LocalTime

@Composable
fun AiScreen(
    viewModel: OrchestratorViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mappedMessages = uiState.messages.map { msg ->
        if (msg.isUser) {
            IronMindMessage.UserMessage(
                text = msg.text,
                timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
            )
        } else {
            IronMindMessage.AiMessage(
                text = msg.text,
                timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")),
                statsCard = if (uiState.pendingAction != null && msg == uiState.messages.last()) {
                    StatsPayload(
                        exerciseName = "Action Required",
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
        sessionLabel = when {
            uiState.statusHeadline.isNotBlank() -> uiState.statusHeadline
            uiState.isModelReady -> "Ready"
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
        modifier = modifier
    )
}
