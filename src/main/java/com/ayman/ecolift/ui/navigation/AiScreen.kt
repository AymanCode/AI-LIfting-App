package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.ui.viewmodel.AiMessageUi
import com.ayman.ecolift.ui.viewmodel.AiPendingActionUi
import com.ayman.ecolift.ui.viewmodel.AiShortcutUi
import com.ayman.ecolift.ui.viewmodel.AiUiState
import com.ayman.ecolift.ui.viewmodel.OrchestratorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen() {
    val viewModel: OrchestratorViewModel = viewModel()
    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val recentTurns by viewModel.recentTurns.collectAsStateWithLifecycle()
    val listState  = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebug  by remember { mutableStateOf(false) }

    // Undo snackbar — collect one-shot events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { applied ->
            val result = snackbarHostState.showSnackbar(
                message      = applied.text,
                actionLabel  = "Undo",
                duration     = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo(applied.auditId)
            }
        }
    }

    // Auto-scroll to newest message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    if (showDebug) {
        DebugSheet(
            turns    = recentTurns,
            onClear  = { viewModel.clearTurnLog() },
            onDismiss = { showDebug = false }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("IronMind AI", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { showDebug = true }) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = "Debug log",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state            = listState,
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentPadding   = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Shortcut chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Shortcuts",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        uiState.shortcuts.forEach { shortcut ->
                            FilterChip(
                                selected  = false,
                                onClick   = { viewModel.applyShortcut(shortcut.prompt) },
                                label     = { Text(shortcut.title) }
                            )
                        }
                    }
                }

                // Chat messages
                items(uiState.messages, key = { it.id }) { msg ->
                    ChatMessage(msg)
                }

                // Thinking indicator
                if (uiState.isWorking) {
                    item { ThinkingIndicator() }
                }

                // Confirmation card for destructive patches
                uiState.pendingAction?.let { action ->
                    item {
                        PatchPreviewCard(
                            action    = action,
                            onConfirm = { viewModel.confirmPending() },
                            onDismiss = { viewModel.dismissPending() }
                        )
                    }
                }
            }

            ChatInput(
                value         = uiState.input,
                onValueChange = { viewModel.updateInput(it) },
                onSend        = { viewModel.sendMessage() },
                enabled       = !uiState.isWorking
            )
        }
    }
}

// ── Chat message bubble ────────────────────────────────────────────────

@Composable
private fun ChatMessage(message: AiMessageUi) {
    val isUser    = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor   = when {
        isUser         -> MaterialTheme.colorScheme.primary
        message.isError -> MaterialTheme.colorScheme.errorContainer
        else           -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isUser         -> MaterialTheme.colorScheme.onPrimary
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else           -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = if (isUser)
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    else
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(color = bgColor, shape = shape, tonalElevation = 2.dp) {
            Text(
                text      = message.text,
                color     = textColor,
                style     = MaterialTheme.typography.bodyLarge,
                modifier  = Modifier.padding(12.dp)
            )
        }
    }
}

// ── Confirmation card for destructive patches ──────────────────────────

@Composable
private fun PatchPreviewCard(
    action: AiPendingActionUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(action.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(action.detail, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(action.confirmLabel)
                }
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

// ── Thinking indicator ─────────────────────────────────────────────────

@Composable
private fun ThinkingIndicator() {
    Surface(
        shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Text(
            text     = "IronMind is thinking…",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

// ── Chat input bar ─────────────────────────────────────────────────────

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier            = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value         = value,
                onValueChange = onValueChange,
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Tell IronMind what you did…") },
                shape         = RoundedCornerShape(24.dp),
                maxLines      = 4,
                enabled       = enabled,
                colors        = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            FloatingActionButton(
                onClick        = onSend,
                containerColor = if (enabled) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.surfaceVariant,
                contentColor   = if (enabled) MaterialTheme.colorScheme.onPrimary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                shape          = RoundedCornerShape(16.dp),
                modifier       = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

// ── Debug sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugSheet(
    turns: List<AgentTurnLog>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.75f)) {
            // Header
            Row(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Turn Log (${turns.size})",
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear log", tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider()

            if (turns.isEmpty()) {
                Box(
                    modifier        = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No turns logged yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(turns, key = { it.id }) { turn ->
                        TurnLogRow(turn, fmt)
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnLogRow(turn: AgentTurnLog, fmt: SimpleDateFormat) {
    val kindColor = when (turn.turnKind) {
        "Applied"           -> MaterialTheme.colorScheme.tertiary
        "NeedsConfirmation" -> MaterialTheme.colorScheme.primary
        "Error"             -> MaterialTheme.colorScheme.error
        else                -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape  = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Text(
                    turn.turnKind,
                    color      = kindColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 12.sp
                )
                Text(
                    fmt.format(Date(turn.timestamp)),
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text     = turn.userText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style    = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${turn.latencyMs} ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                turn.auditId?.let { Text("audit=$it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                turn.errorMessage?.let { Text("err: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
