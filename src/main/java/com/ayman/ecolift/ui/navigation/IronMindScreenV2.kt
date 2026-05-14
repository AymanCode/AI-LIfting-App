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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class IronMindMessage {
    data class UserMessage(val text: String, val timestamp: String) : IronMindMessage()
    data class AiMessage(
        val text: String,
        val timestamp: String,
        val statsCard: StatsPayload? = null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronMindTopBar(
    sessionLabel: String?,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        modifier = modifier.statusBarsPadding(),
        windowInsets = WindowInsets(0),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "IronMind",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1C1E)
                )
                Text(
                    text = sessionLabel ?: "No active session",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sessionLabel != null) Color(0xFF4DB6AC) else Color(0xFF8E8E93)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF1C1C1E)
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color(0xFFF2F0EB)
        )
    )
}

@Composable
fun UserMessageBubble(
    message: IronMindMessage.UserMessage,
    modifier: Modifier = Modifier
) {
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
                    .background(
                        color = Color(0xFF1C1C1E),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93),
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F0EB), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(
            text = stats.exerciseName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8E8E93),
            letterSpacing = 0.8.sp
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = Color(0xFF1C1C1E).copy(alpha = 0.1f)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E8E93)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1C1C1E)
        )
    }
}

@Composable
fun AiMessageBubble(
    message: IronMindMessage.AiMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF4DB6AC).copy(alpha = 0.15f), CircleShape)
                .padding(end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF4DB6AC)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .padding(start = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xFFFFFFFF),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .border(
                        border = BorderStroke(0.5.dp, Color(0xFF1C1C1E).copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1C1C1E)
                    )
                    if (message.statsCard != null) {
                        InlineStatsCard(
                            stats = message.statsCard,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AiThinkingBubble(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF4DB6AC).copy(alpha = 0.15f), CircleShape)
                .padding(end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF4DB6AC)
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .background(
                    color = Color(0xFFFFFFFF),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                )
                .border(
                    border = BorderStroke(0.5.dp, Color(0xFF1C1C1E).copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                )
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
                            .background(Color(0xFF8E8E93).copy(alpha = alpha), CircleShape)
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Suggested",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8E8E93),
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
                            containerColor = Color.Transparent,
                            labelColor = Color(0xFF1C1C1E)
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = Color(0xFF4DB6AC).copy(alpha = 0.5f)
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        border = BorderStroke(0.5.dp, Color(0xFF1C1C1E).copy(alpha = 0.08f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ">",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4DB6AC),
                modifier = Modifier.padding(end = 8.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF1C1C1E)
                ),
                cursorBrush = SolidColor(Color(0xFF4DB6AC)),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Log your data or run query...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF8E8E93)
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
                    tint = if (isEnabled) Color(0xFF4DB6AC) else Color(0xFF8E8E93).copy(alpha = 0.3f)
                )
            }
        }
    }
}

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
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            IronMindTopBar(
                sessionLabel = sessionLabel,
                onSettings = onSettings
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
        containerColor = Color(0xFFF2F0EB)
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
                    is IronMindMessage.AiMessage -> AiMessageBubble(message = message)
                    is IronMindMessage.AiThinking -> AiThinkingBubble() // Handled above, but exhaustive
                }
            }
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
