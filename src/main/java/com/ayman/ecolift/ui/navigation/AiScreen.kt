package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.agent.model.AgentTurnLog
import com.ayman.ecolift.ui.viewmodel.AiMessageUi
import com.ayman.ecolift.ui.viewmodel.AiPendingActionUi
import com.ayman.ecolift.ui.viewmodel.OrchestratorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// AI screen color palette
private val AiBg        = Color(0xFF06080B)
private val AiCard      = Color(0xFF0F141B)
private val AiCardLight = Color(0xFF131922)
private val AiTeal      = Color(0xFF2DD4BF)
private val AiTealGlow  = Color(0x332DD4BF)
private val AiSlate800  = Color(0xFF1E293B)
private val AiSlate700  = Color(0xFF334155)
private val AiSlate400  = Color(0xFF94A3B8)
private val AiSlate300  = Color(0xFFCBD5E1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen() {
    val viewModel: OrchestratorViewModel = viewModel()
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val recentTurns by viewModel.recentTurns.collectAsStateWithLifecycle()
    val listState   = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebug   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { applied ->
            val result = snackbarHostState.showSnackbar(
                message     = applied.text,
                actionLabel = "Undo",
                duration    = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(applied.auditId)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    if (showDebug) {
        DebugSheet(
            turns     = recentTurns,
            onClear   = { viewModel.clearTurnLog() },
            onSeed    = { viewModel.seedDebugData(); showDebug = false },
            onDismiss = { showDebug = false }
        )
    }

    Scaffold(
        containerColor  = AiBg,
        snackbarHost    = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // Terminal-style header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AiBg)
                    .border(width = 1.dp, color = AiSlate800)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Debug icon (left)
                IconButton(onClick = { showDebug = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.BugReport, contentDescription = "Debug", tint = AiSlate400, modifier = Modifier.size(20.dp))
                }
                // Centered title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = AiTeal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("IRONMIND", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                    Text(
                        "Active Session",
                        color      = AiTeal.copy(alpha = 0.7f),
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier   = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.size(40.dp)) // balance the debug icon
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Message feed
            LazyColumn(
                state               = listState,
                modifier            = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                contentPadding      = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    if (msg.isUser) UserLogEntry(msg) else AiInsightCard(msg)
                }

                if (uiState.isWorking) {
                    item { InclineRobotLoader() }
                }

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

            // Bottom: shortcuts + input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AiBg)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(uiState.shortcuts) { shortcut ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AiCard)
                                .border(1.dp, AiSlate800, RoundedCornerShape(8.dp))
                                .clickable { viewModel.applyShortcut(shortcut.prompt) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(shortcut.title, color = AiSlate300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AiCard)
                        .border(1.dp, AiSlate800, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(">", color = AiTeal, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    TextField(
                        value         = uiState.input,
                        onValueChange = { viewModel.updateInput(it) },
                        placeholder   = {
                            Text(
                                "Log your data or run query…",
                                color      = AiSlate700,
                                fontSize   = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor             = AiTeal,
                            focusedTextColor        = Color.White,
                            unfocusedTextColor      = Color.White,
                            disabledContainerColor  = Color.Transparent,
                            disabledIndicatorColor  = Color.Transparent,
                        ),
                        modifier  = Modifier.weight(1f),
                        singleLine = false,
                        maxLines  = 4,
                        enabled   = !uiState.isWorking
                    )
                    val hasInput = uiState.input.isNotBlank()
                    IconButton(
                        onClick  = { viewModel.sendMessage() },
                        enabled  = hasInput && !uiState.isWorking,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hasInput && !uiState.isWorking) AiTeal else Color.Transparent)
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Send,
                            contentDescription = "Send",
                            tint     = if (hasInput && !uiState.isWorking) Color.White else AiSlate700,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// User message

@Composable
private fun UserLogEntry(msg: AiMessageUi) {
    Column(modifier = Modifier.padding(start = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AiSlate800)
                    .border(1.dp, AiSlate700, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Build, contentDescription = null, tint = AiSlate300, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("LOGGED ENTRY", color = AiSlate400, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text(
            text       = msg.text,
            color      = Color.White,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 40.dp)
        )
    }
}

// AI response card

@Composable
private fun AiInsightCard(msg: AiMessageUi) {
    val borderColor = if (msg.isError) Color(0xFFEF4444) else AiTeal
    val iconTint    = if (msg.isError) Color(0xFFEF4444) else AiTeal
    val glowBg      = if (msg.isError) Color(0x33EF4444) else AiTealGlow

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AiCard)
            .border(1.dp, AiSlate800, RoundedCornerShape(16.dp))
    ) {
        // Teal/red left accent strip
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(borderColor)
                .align(Alignment.CenterStart)
        )
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(bottom = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(glowBg)
                        .border(1.dp, borderColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (msg.isError) Icons.Rounded.Warning else Icons.Rounded.Star,
                        contentDescription = null,
                        tint     = iconTint,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text       = if (msg.isError) "Error" else "IronMind",
                    color      = Color.White,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text       = msg.text,
                color      = AiSlate300,
                fontSize   = 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}

// Loading animation

@Composable
private fun InclineRobotLoader() {
    val transition = rememberInfiniteTransition(label = "robot_curl")

    val armAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 135f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arm_angle"
    )
    val intensity by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "intensity"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AiCardLight)
            .border(1.dp, AiSlate800, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val s = size.width / 160f
                scale(s, pivot = Offset.Zero) {

                    // Bench
                    val benchBack = Path().apply {
                        moveTo(50f, 130f); lineTo(15f, 45f)
                        lineTo(25f, 40f);  lineTo(60f, 125f); close()
                    }
                    drawPath(benchBack, AiSlate800)
                    val benchSeat = Path().apply {
                        moveTo(50f, 120f); lineTo(95f, 120f)
                        lineTo(95f, 130f); lineTo(50f, 130f); close()
                    }
                    drawPath(benchSeat, AiSlate800)

                    // Antenna + head
                    drawLine(AiSlate400, Offset(20f, 30f), Offset(15f, 10f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawCircle(AiTeal, radius = 3f, center = Offset(15f, 10f))
                    drawCircle(AiSlate700, radius = 14f, center = Offset(20f, 30f))
                    drawCircle(Color(0xFF00E5FF), radius = 4f, center = Offset(28f, 27f))

                    // Torso
                    rotate(22f, pivot = Offset(35f, 75f)) {
                        drawRoundRect(
                            color        = AiSlate700,
                            topLeft      = Offset(20f, 45f),
                            size         = Size(30f, 60f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }

                    // Upper arm
                    drawCircle(Color(0xFF0F172A), radius = 11f, center = Offset(45f, 65f))
                    drawLine(AiSlate700, Offset(45f, 65f), Offset(45f, 110f), strokeWidth = 15f, cap = StrokeCap.Round)
                    drawCircle(AiTeal, radius = 9f, center = Offset(45f, 110f))

                    // Rotating forearm + dumbbell
                    rotate(armAngle, pivot = Offset(45f, 110f)) {
                        drawLine(AiSlate400, Offset(45f, 110f), Offset(45f, 150f), strokeWidth = 11f, cap = StrokeCap.Round)

                        // Handle
                        drawLine(AiSlate300, Offset(16f, 150f), Offset(74f, 150f), strokeWidth = 6f, cap = StrokeCap.Round)
                        // Left weight
                        drawRoundRect(Color(0xFF0F172A), Offset(14f, 132f), Size(14f, 36f), CornerRadius(6f, 6f))
                        drawRoundRect(AiSlate800, Offset(10f, 136f), Size(8f, 28f), CornerRadius(4f, 4f))
                        // Right weight
                        drawRoundRect(Color(0xFF0F172A), Offset(62f, 132f), Size(14f, 36f), CornerRadius(6f, 6f))
                        drawRoundRect(AiSlate800, Offset(72f, 136f), Size(8f, 28f), CornerRadius(4f, 4f))

                        // Hand
                        drawCircle(AiTeal, radius = 9f, center = Offset(45f, 150f))

                        // Bicep strain line (fades in/out with intensity)
                        val strain = Path().apply {
                            moveTo(25f, 105f)
                            quadraticBezierTo(15f, 115f, 25f, 125f)
                        }
                        drawPath(strain, Color(0xFF00E5FF).copy(alpha = intensity), style = Stroke(2f, cap = StrokeCap.Round))
                    }

                    // Sweat dots
                    drawCircle(Color(0xFF00E5FF).copy(alpha = intensity), radius = 2.5f, center = Offset(36f, 18f))
                    drawCircle(Color(0xFF00E5FF).copy(alpha = intensity), radius = 1.5f, center = Offset(42f, 24f))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = AiTeal, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("CALCULATING TRAJECTORY", color = AiTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

// Confirmation card

@Composable
private fun PatchPreviewCard(
    action: AiPendingActionUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AiCard)
            .border(1.dp, AiTeal.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(action.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(action.detail, color = AiSlate300, fontSize = 13.sp, lineHeight = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AiTeal, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(action.confirmLabel, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AiSlate300)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

// Debug sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugSheet(
    turns: List<AgentTurnLog>,
    onClear: () -> Unit,
    onSeed: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxHeight(0.75f)) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Turn Log (${turns.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = onSeed) { Text("Seed Data", style = MaterialTheme.typography.labelMedium) }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider()
            if (turns.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No turns logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(turns, key = { it.id }) { TurnLogRow(it, fmt) }
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
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(turn.turnKind, color = kindColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(fmt.format(Date(turn.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(turn.userText, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${turn.latencyMs} ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                turn.auditId?.let { Text("audit=$it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                turn.errorMessage?.let { Text("err: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
