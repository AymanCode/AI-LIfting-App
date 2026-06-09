package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.GlassPaletteSwitch
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.glassPanel

sealed class IronMindMessage {
    data class UserMessage(val text: String, val timestamp: String) : IronMindMessage()
    data class AiMessage(
        val text: String,
        val timestamp: String,
        val statsCard: StatsPayload? = null,
        val recovery: RecoveryPayload? = null
    ) : IronMindMessage()
    object AiThinking : IronMindMessage()
}

data class StatsPayload(
    val exerciseName: String,
    val pr: String?,
    val estimatedOneRm: String?,
    val lastSession: String?,
    val sessionCount: Int?
)

data class QuickAction(val label: String, val query: String)

data class RecoveryPayload(
    val title: String,
    val detail: String,
    val originalText: String,
    val suggestedTemplate: String,
    val saveDate: String,
    val canTryModel: Boolean
)

enum class RecoveryAction {
    EditOriginal,
    UseTemplate,
    SaveForReview,
    TryAi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronMindTopBar(
    sessionLabel: String?,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    CenterAlignedTopAppBar(
        modifier = modifier.statusBarsPadding(),
        windowInsets = WindowInsets(0),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "IronMind",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.ink
                )
                Text(
                    text = sessionLabel ?: "No active session",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sessionLabel != null) palette.accentStrong else palette.inkSubtle
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onSettings,
                modifier = Modifier.glassPanel(palette, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = palette.ink
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
fun UserMessageBubble(
    message: IronMindMessage.UserMessage,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(0.78f),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .glassPanel(palette, shape, strong = true)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink
                )
            }
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun InlineStatsCard(
    stats: StatsPayload,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(
            text = stats.exerciseName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.inkSubtle,
            letterSpacing = 0.sp
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = palette.glassStroke
        )
        if (stats.pr != null) {
            StatRow("Current PR", stats.pr)
        }
        if (stats.estimatedOneRm != null) {
            StatRow("Est. 1RM", stats.estimatedOneRm)
        }
        if (stats.lastSession != null) {
            StatRow("Last session", stats.lastSession)
        }
        if (stats.sessionCount != null) {
            StatRow("Total sessions", "${stats.sessionCount}")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = palette.ink
        )
    }
}

@Composable
fun AiMessageBubble(
    message: IronMindMessage.AiMessage,
    onRecoveryAction: (RecoveryPayload, RecoveryAction) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val bubbleShape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(palette.accentStrong.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                style = MaterialTheme.typography.labelMedium,
                color = palette.accentStrong
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(start = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .glassPanel(palette, bubbleShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.ink
                    )
                    if (message.statsCard != null) {
                        InlineStatsCard(
                            stats = message.statsCard,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    if (message.recovery != null) {
                        RecoveryDraftSection(
                            recovery = message.recovery,
                            onAction = onRecoveryAction,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecoveryDraftSection(
    recovery: RecoveryPayload,
    onAction: (RecoveryPayload, RecoveryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 8.dp),
            color = palette.glassStroke
        )
        Text(
            text = "Original",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.inkSubtle
        )
        Text(
            text = recovery.originalText,
            style = MaterialTheme.typography.bodySmall,
            color = palette.ink,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .glassPanel(palette, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        )
        Text(
            text = "Template",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.inkSubtle,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = recovery.suggestedTemplate,
            style = MaterialTheme.typography.bodySmall,
            color = palette.ink,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .glassPanel(palette, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onAction(recovery, RecoveryAction.EditOriginal) },
                label = { Text("Edit", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = palette.glassFillStrong,
                    labelColor = palette.ink
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = palette.glassStroke
                )
            )
            AssistChip(
                onClick = { onAction(recovery, RecoveryAction.UseTemplate) },
                label = { Text("Use template", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = palette.glassFillStrong,
                    labelColor = palette.ink
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = palette.glassStroke
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onAction(recovery, RecoveryAction.SaveForReview) },
                label = { Text("Save", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = palette.glassFillStrong,
                    labelColor = palette.ink
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = palette.glassStroke
                )
            )
            AssistChip(
                onClick = { onAction(recovery, RecoveryAction.TryAi) },
                enabled = recovery.canTryModel,
                label = { Text(if (recovery.canTryModel) "Try AI" else "AI offline", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = palette.glassFillStrong,
                    labelColor = palette.ink,
                    disabledContainerColor = palette.glassFill,
                    disabledLabelColor = palette.inkSubtle
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = recovery.canTryModel,
                    borderColor = palette.glassStroke
                )
            )
        }
    }
}

@Composable
fun AiThinkingBubble(
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val bubbleShape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(palette.accentStrong.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                style = MaterialTheme.typography.labelMedium,
                color = palette.accentStrong
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .glassPanel(palette, bubbleShape)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val infiniteTransition = rememberInfiniteTransition(label = "thinking")
                for (i in 0..2) {
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900, delayMillis = i * 150),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_alpha_$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(palette.inkMuted.copy(alpha = alpha), CircleShape)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun QuickActionsRow(
    actions: List<QuickAction>,
    onAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), strong = true),
        color = Color.Transparent,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Suggested",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = palette.inkSubtle,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(actions) { action ->
                    AssistChip(
                        onClick = { onAction(action) },
                        label = { Text(action.label, style = MaterialTheme.typography.labelMedium) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = palette.glassFillStrong,
                            labelColor = palette.ink
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = palette.glassStroke
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun IronMindInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), strong = true),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(8.dp)
                    .background(palette.accentStrong, CircleShape)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.ink
                ),
                cursorBrush = SolidColor(palette.accentStrong),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Log your data or run query...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = palette.inkSubtle
                            )
                        )
                    }
                    innerTextField()
                }
            )
            val isEnabled = query.isNotBlank()
            IconButton(
                onClick = onSend,
                enabled = isEnabled,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (isEnabled) palette.accentStrong else palette.inkSubtle.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun IronMindSettingsSheet(
    selectedPalette: GlassPaletteChoice,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit,
    userBodyweightLbs: Int?,
    onUserBodyweightChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    var bodyweightText by remember(userBodyweightLbs) {
        mutableStateOf(userBodyweightLbs?.toString().orEmpty())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(palette, RoundedCornerShape(22.dp), strong = true)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = "Color theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle
                    )
                }
                GlassPaletteSwitch(
                    selected = selectedPalette,
                    onSelect = onPaletteChoiceChange,
                    palette = palette
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bodyweight",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.glassFillStrong.copy(alpha = 0.62f),
                        border = BorderStroke(1.dp, palette.glassStroke),
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BasicTextField(
                                value = bodyweightText,
                                onValueChange = { rawValue ->
                                    val parsed = rawValue.filter { it.isDigit() }.take(4).toIntOrNull()
                                    val normalizedBodyweight = parsed?.takeIf { it > 0 }
                                    bodyweightText = normalizedBodyweight?.toString().orEmpty()
                                    onUserBodyweightChange(normalizedBodyweight)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                cursorBrush = SolidColor(palette.accentStrong),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    color = palette.ink,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp),
                                decorationBox = { innerTextField ->
                                    if (bodyweightText.isEmpty()) {
                                        Text(
                                            text = "--",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.inkSubtle
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            Text(
                                text = "lb",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.inkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronMindScreen(
    sessionLabel: String?,
    messages: List<IronMindMessage>,
    quickActions: List<QuickAction>,
    inputQuery: String,
    isThinking: Boolean,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onSettings: () -> Unit,
    onRecoveryAction: (RecoveryPayload, RecoveryAction) -> Unit = { _, _ -> },
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    userBodyweightLbs: Int? = null,
    onUserBodyweightChange: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    var isSettingsVisible by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            IronMindTopBar(
                sessionLabel = sessionLabel,
                onSettings = {
                    isSettingsVisible = true
                    onSettings()
                }
            )
        },
        bottomBar = {
            Column {
                if (quickActions.isNotEmpty()) {
                    QuickActionsRow(
                        actions = quickActions,
                        onAction = onQuickAction
                    )
                }
                IronMindInputBar(
                    query = inputQuery,
                    onQueryChange = onQueryChange,
                    onSend = onSend
                )
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isThinking) {
                item(key = "thinking") {
                    AiThinkingBubble()
                }
            }
            items(messages.reversed(), key = { it.hashCode() }) { message ->
                when (message) {
                    is IronMindMessage.UserMessage -> UserMessageBubble(message = message)
                    is IronMindMessage.AiMessage -> AiMessageBubble(
                        message = message,
                        onRecoveryAction = onRecoveryAction
                    )
                    is IronMindMessage.AiThinking -> AiThinkingBubble() // Handled above, but exhaustive
                }
            }
        }
    }

    if (isSettingsVisible) {
        ModalBottomSheet(
            onDismissRequest = { isSettingsVisible = false },
            sheetState = settingsSheetState,
            containerColor = Color.Transparent,
            contentColor = palette.ink,
            dragHandle = null
        ) {
            IronMindSettingsSheet(
                selectedPalette = paletteChoice,
                onPaletteChoiceChange = { choice ->
                    onPaletteChoiceChange(choice)
                    isSettingsVisible = false
                },
                userBodyweightLbs = userBodyweightLbs,
                onUserBodyweightChange = onUserBodyweightChange,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IronMindScreenPreview() {
    MaterialTheme {
        IronMindScreen(
            sessionLabel = "Chest & Back Day",
            messages = listOf(
                IronMindMessage.AiMessage(
                    text = "Ready to lift! Let me know what you're doing.",
                    timestamp = "10:00 AM"
                ),
                IronMindMessage.UserMessage(
                    text = "How is my Bench Press trending?",
                    timestamp = "10:05 AM"
                ),
                IronMindMessage.AiMessage(
                    text = "Your bench press has been improving nicely. You hit a PR of 185 lbs last week. Your estimated 1RM is now 246 lbs.",
                    timestamp = "10:06 AM",
                    statsCard = StatsPayload(
                        exerciseName = "Bench Press",
                        pr = "185 lbs",
                        estimatedOneRm = "246 lbs",
                        lastSession = "185 lbs x 10",
                        sessionCount = 12
                    )
                )
            ),
            quickActions = listOf(
                QuickAction("Trend for Bench Press", "trend bench press"),
                QuickAction("Analyze last workout", "analyze last workout"),
                QuickAction("Log a set", "log")
            ),
            inputQuery = "",
            isThinking = false,
            onQueryChange = {},
            onSend = {},
            onQuickAction = {},
            onSettings = {}
        )
    }
}
