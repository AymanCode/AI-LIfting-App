package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.AiViewModel
import java.time.format.DateTimeFormatter
import java.time.LocalTime

@Composable
fun AiScreen(
    viewModel: AiViewModel = viewModel(),
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
                } else null
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
        onSettings = { },
        modifier = modifier
    )
}
