package com.ayman.ecolift.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.CardioPrimaryMetric
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogType
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.viewmodel.CardioUiState
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardioScreen(
    viewModel: CardioViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.handleOcrCaptureResult(success)
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onHealthPermissionsResult(granted)
    }
    val filteredSessions = state.sessions.filter { session ->
        state.selectedFilter == null || activityTypeFor(session) == state.selectedFilter
    }
    val groupedSessions = filteredSessions.groupBy(CardioSession::date)
    val selectedSession = state.selectedSessionId?.let { selectedId ->
        state.sessions.firstOrNull { it.id == selectedId }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Cardio",
                style = MaterialTheme.typography.headlineLarge,
                color = palette.ink,
            )
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = LogType.meta,
                color = palette.inkSubtle,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item(key = "summary") {
            CardioSummaryBand(
                sessions = state.sessions,
                watchCaloriesToday = state.healthConnectCaloriesToday.takeIf { state.healthConnectPermissionGranted },
                onWatchTap = viewModel::refreshHealthConnectCalories,
            )
        }

        item(key = "actions") {
            CardioActionRail(
                isOcrRunning = state.isOcrRunning,
                ocrMessage = state.ocrMessage,
                onScan = {
                    val uri = viewModel.createOcrCaptureUri()
                    cameraLauncher.launch(uri)
                },
                onManual = viewModel::openManualEntry,
            )
        }

        if (state.healthConnectAvailable && !state.healthConnectPermissionGranted) {
            item(key = "health-connect") {
                HealthConnectCard(
                    onConnect = { healthPermissionLauncher.launch(viewModel.healthConnectPermissions) },
                )
            }
        }

        if (state.sessions.isNotEmpty()) {
            item(key = "history-header") {
                HistoryHeader(
                    selectedFilter = state.selectedFilter,
                    onFilterSelected = viewModel::selectFilter,
                )
            }
        }

        if (filteredSessions.isEmpty()) {
            item(key = "empty") {
                CardioEmptyState(
                    hasAnySessions = state.sessions.isNotEmpty(),
                    onManual = viewModel::openManualEntry,
                    onClearFilter = { viewModel.selectFilter(null) },
                )
            }
        } else {
            groupedSessions.forEach { (date, sessions) ->
                item(key = "day-$date") {
                    DayCard(
                        date = date,
                        sessions = sessions,
                        onOpen = { viewModel.selectSession(it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        item(key = "bottom-spacer") { Spacer(Modifier.height(96.dp)) }
    }

    if (state.showEntrySheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeEntrySheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.pageBottom,
            contentColor = palette.ink,
        ) {
            CardioEntrySheet(
                state = state,
                onActivityChange = viewModel::updateActivityType,
                onFieldChange = viewModel::updateEntryField,
                onSave = viewModel::saveEntry,
                onCancel = viewModel::closeEntrySheet,
            )
        }
    }

    if (selectedSession != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeSessionDetail,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.pageBottom,
            contentColor = palette.ink,
        ) {
            CardioDetailSheet(
                session = selectedSession,
                onClose = viewModel::closeSessionDetail,
                onEdit = { viewModel.openEditEntry(selectedSession.id) },
                onDelete = { viewModel.deleteSession(selectedSession.id) },
            )
        }
    }
}

// region Summary

@Composable
private fun CardioSummaryBand(
    sessions: List<CardioSession>,
    watchCaloriesToday: Int?,
    onWatchTap: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val today = LocalDate.now()
    val window = (6 downTo 0L).map { today.minusDays(it) }
    val byDate = sessions.groupBy { runCatching { LocalDate.parse(it.date) }.getOrNull() }
    val recentSessions = window.flatMap { byDate[it].orEmpty() }
    val perDayMinutes = window.map { day ->
        day to (byDate[day].orEmpty().sumOf { it.durationSec ?: 0 } / 60)
    }
    val durationSec = recentSessions.sumOf { it.durationSec ?: 0 }
    val distanceMiles = recentSessions.sumOf { it.distanceM ?: 0.0 } / METERS_PER_MILE
    val calories = recentSessions.mapNotNull(CardioSession::calories).sum()

    // No container: the hero number sits directly on the ambient background.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("THIS WEEK", color = palette.inkSubtle, style = sectionLabelStyle())
                Text(
                    text = formatDurationCompact(durationSec),
                    color = palette.ink,
                    style = LogType.railValue.copy(fontSize = 40.sp, lineHeight = 42.sp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (watchCaloriesToday != null) {
                WatchChip(calories = watchCaloriesToday, onTap = onWatchTap)
            }
        }
        Text(
            text = if (recentSessions.isEmpty()) {
                "No sessions this week — log one below."
            } else {
                buildList {
                    add(if (recentSessions.size == 1) "1 session" else "${recentSessions.size} sessions")
                    if (distanceMiles > 0.05) add("%.1f mi".format(Locale.US, distanceMiles))
                    if (calories > 0) add("%,d cal".format(Locale.US, calories))
                }.joinToString("  ·  ")
            },
            color = palette.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (recentSessions.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            WeekSparkline(perDayMinutes = perDayMinutes)
        }
    }
}

@Composable
private fun WatchChip(calories: Int, onTap: () -> Unit) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Watch,
            contentDescription = "Refresh watch calories",
            tint = palette.accentStrong,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "$calories cal today",
            color = palette.inkMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun sectionLabelStyle() =
    MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp)

@Composable
private fun WeekSparkline(perDayMinutes: List<Pair<LocalDate, Int>>) {
    val palette = LocalGlassPalette.current
    val today = LocalDate.now()
    val maxMinutes = (perDayMinutes.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val barAreaHeight = 26.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        perDayMinutes.forEach { (day, minutes) ->
            val isToday = day == today
            val fraction = minutes.toFloat() / maxMinutes
            val barHeight = if (minutes == 0) 3.dp else (barAreaHeight * fraction).coerceAtLeast(6.dp)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(modifier = Modifier.height(barAreaHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    minutes == 0 -> palette.glassStroke.copy(alpha = 0.55f)
                                    isToday -> palette.accentStrong
                                    else -> palette.accent.copy(alpha = 0.45f)
                                },
                            ),
                    )
                }
                Text(
                    text = day.dayOfWeek.name.take(1),
                    color = if (isToday) palette.accentStrong else palette.inkSubtle,
                    style = LogType.railLabel,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

// endregion

// region Actions

@Composable
private fun CardioActionRail(
    isOcrRunning: Boolean,
    ocrMessage: String?,
    onScan: () -> Unit,
    onManual: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanActionTile(isOcrRunning = isOcrRunning, onClick = onScan)
            ManualActionTile(onClick = onManual)
        }
        if (ocrMessage != null && !isOcrRunning) {
            Text(
                text = ocrMessage,
                color = palette.inkMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ScanActionTile(
    isOcrRunning: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.accentStrong.copy(alpha = if (isOcrRunning) 0.55f else 0.92f))
            .clickable(enabled = !isOcrRunning, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isOcrRunning) {
            CircularProgressIndicator(
                color = palette.pageBottom,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = palette.pageBottom,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = if (isOcrRunning) "Reading…" else "Scan machine",
            color = palette.pageBottom,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ManualActionTile(
    onClick: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = palette.accentStrong,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Log manually",
            color = palette.ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HealthConnectCard(
    onConnect: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Watch,
            contentDescription = null,
            tint = palette.inkSubtle,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "Sync watch workouts with Health Connect",
            color = palette.inkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Connect",
            color = palette.accentStrong,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onConnect)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

// endregion

// region History

@Composable
private fun HistoryHeader(
    selectedFilter: CardioActivityType?,
    onFilterSelected: (CardioActivityType?) -> Unit,
) {
    val palette = LocalGlassPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("HISTORY", style = sectionLabelStyle(), color = palette.inkSubtle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "all") {
                FilterTab(
                    label = "All",
                    selected = selectedFilter == null,
                    onClick = { onFilterSelected(null) },
                )
            }
            items(CardioActivitySpecs.entryOrder, key = { it.name }) { type ->
                FilterTab(
                    label = CardioActivitySpecs.labelFor(type),
                    selected = selectedFilter == type,
                    onClick = { onFilterSelected(if (selectedFilter == type) null else type) },
                )
            }
        }
    }
}

@Composable
private fun FilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val textColor by animateColorAsState(
        targetValue = if (selected) palette.ink else palette.inkSubtle,
        animationSpec = tween(180),
        label = "tab_text",
    )
    val barColor by animateColorAsState(
        targetValue = if (selected) palette.accentStrong else Color.Transparent,
        animationSpec = tween(180),
        label = "tab_bar",
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor),
        )
    }
}

@Composable
private fun DayCard(
    date: String,
    sessions: List<CardioSession>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val totalSec = sessions.sumOf { it.durationSec ?: 0 }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDateLabel(date).uppercase(Locale.US),
                color = palette.inkSubtle,
                style = sectionLabelStyle(),
            )
            val daySummary = buildList {
                if (sessions.size > 1) add("${sessions.size} sessions")
                if (totalSec > 0) add(formatDurationCompact(totalSec))
            }.joinToString(" · ")
            if (daySummary.isNotEmpty()) {
                Text(
                    text = daySummary,
                    color = palette.inkSubtle,
                    style = LogType.railLabel,
                )
            }
        }
        sessions.forEachIndexed { index, session ->
            TimelineSessionRow(
                session = session,
                isLast = index == sessions.lastIndex,
                onOpen = { onOpen(session.id) },
            )
        }
    }
}

/**
 * A timeline node: circular activity marker on a thin vertical rail, content
 * hanging off it. Circles + rail give cardio its own identity against the
 * Log tab's glass cards while staying inside the same palette system.
 */
@Composable
private fun TimelineSessionRow(
    session: CardioSession,
    isLast: Boolean,
    onOpen: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val type = activityTypeFor(session)
    val accent = activityAccent(type, palette)
    val metrics = sessionMetricsOrdered(session)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
        ) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 36.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(palette.glassStroke.copy(alpha = 0.35f)),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = activityIcon(type),
                    contentDescription = CardioActivitySpecs.labelFor(type),
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp, bottom = if (isLast) 4.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = session.activityLabel ?: CardioActivitySpecs.labelFor(type),
                    color = palette.ink,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (session.source != CardioSessionSource.MANUAL) {
                    SourceBadge(session.source)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = metrics.firstOrNull() ?: "—",
                    color = palette.ink,
                    style = LogType.completedSummary,
                )
            }
            val metaLine = metrics.drop(1).take(3).joinToString("  ·  ")
            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    color = palette.inkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String) {
    val palette = LocalGlassPalette.current
    Text(
        text = sourceLabel(source),
        color = palette.inkSubtle,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.glassFill.copy(alpha = 0.44f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun CardioEmptyState(
    hasAnySessions: Boolean,
    onManual: () -> Unit,
    onClearFilter: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasAnySessions) {
            Text(
                text = "No sessions of this type",
                color = palette.ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onClearFilter) {
                Text("Show all", color = palette.accentStrong)
            }
        } else {
            ActivitySymbolTile(type = CardioActivityType.RUN, size = 56.dp)
            Text(
                text = "No cardio yet",
                color = palette.ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Scan a machine screen or log a session manually.",
                color = palette.inkSubtle,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onManual,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentStrong,
                    contentColor = palette.pageBottom,
                ),
            ) {
                Text("Log a session")
            }
        }
    }
}

// endregion

// region Entry sheet

@Composable
private fun CardioEntrySheet(
    state: CardioUiState,
    onActivityChange: (CardioActivityType) -> Unit,
    onFieldChange: (CardioEntryField, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val spec = CardioActivitySpecs.specFor(state.selectedActivityType)
    val metricFields = spec.fields.filterNot { it == CardioEntryField.NOTES }
    val isEditing = state.editingSessionId != null
    val isOcr = state.entrySource == CardioSessionSource.OCR
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = when {
                    isEditing -> "Edit session"
                    isOcr -> "Confirm scan"
                    else -> "Log cardio"
                },
                color = palette.ink,
                style = LogType.exerciseTitle,
            )
            if (isOcr) {
                Text(
                    text = "Scanned",
                    color = palette.accentStrong,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.accentStrong.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CardioActivitySpecs.entryOrder, key = { it.name }) { type ->
                ActivityChip(
                    type = type,
                    selected = type == state.selectedActivityType,
                    onClick = { onActivityChange(type) },
                )
            }
        }

        val essentialSet = buildSet {
            add(CardioEntryField.DURATION)
            add(CardioEntryField.DISTANCE_MILES)
            add(CardioEntryField.CALORIES)
            add(primaryMetricField(spec.primaryMetric))
        }
        val essentialFields = metricFields.filter { it in essentialSet }
        val advancedFields = metricFields.filterNot { it in essentialSet }
        var showAdvanced by remember(state.selectedActivityType, state.editingSessionId) {
            mutableStateOf(advancedFields.any { state.valueFor(it).isNotBlank() })
        }

        FieldGrid(fields = essentialFields, state = state, onFieldChange = onFieldChange)

        if (advancedFields.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (showAdvanced) "Fewer metrics" else "More metrics",
                    color = palette.accentStrong,
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = palette.accentStrong,
                    modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(
                visible = showAdvanced,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FieldGrid(fields = advancedFields, state = state, onFieldChange = onFieldChange)
                }
            }
        }

        if (CardioEntryField.NOTES in spec.fields) {
            OutlinedTextField(
                value = state.notesText,
                onValueChange = { onFieldChange(CardioEntryField.NOTES, it) },
                label = { Text("Notes") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.entryError?.let {
            Text(it, color = palette.danger, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = palette.inkMuted)
            }
            Button(
                onClick = onSave,
                enabled = state.canSaveEntry,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentStrong,
                    contentColor = palette.pageBottom,
                    disabledContainerColor = palette.glassFillStrong,
                    disabledContentColor = palette.inkSubtle,
                ),
            ) {
                Text(if (isEditing) "Save changes" else "Save")
            }
        }
    }
}

@Composable
private fun FieldGrid(
    fields: List<CardioEntryField>,
    state: CardioUiState,
    onFieldChange: (CardioEntryField, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        fields.chunked(2).forEach { rowFields ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowFields.forEach { field ->
                    EntryField(
                        field = field,
                        value = state.valueFor(field),
                        onValueChange = { onFieldChange(field, sanitizeFieldInput(field, it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowFields.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun primaryMetricField(metric: CardioPrimaryMetric): CardioEntryField = when (metric) {
    CardioPrimaryMetric.DURATION -> CardioEntryField.DURATION
    CardioPrimaryMetric.DISTANCE -> CardioEntryField.DISTANCE_MILES
    CardioPrimaryMetric.SPEED -> CardioEntryField.AVG_SPEED_MPH
    CardioPrimaryMetric.PACE_500M -> CardioEntryField.PACE_500M
    CardioPrimaryMetric.CADENCE -> CardioEntryField.CADENCE_RPM
    CardioPrimaryMetric.FLOORS -> CardioEntryField.FLOORS
}

@Composable
private fun ActivityChip(
    type: CardioActivityType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val accent = activityAccent(type, palette)
    val container by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.18f) else palette.glassFill.copy(alpha = 0.30f),
        animationSpec = tween(180),
        label = "activity_container",
    )
    val border by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.72f) else palette.glassStroke.copy(alpha = 0.55f),
        animationSpec = tween(180),
        label = "activity_border",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = activityIcon(type),
            contentDescription = null,
            tint = if (selected) accent else palette.inkMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = CardioActivitySpecs.labelFor(type),
            color = if (selected) palette.ink else palette.inkMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun EntryField(
    field: CardioEntryField,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = fieldCopy(field)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(copy.label) },
        placeholder = { Text(copy.placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = copy.keyboardType),
        colors = fieldColors(),
        modifier = modifier,
    )
}

private fun sanitizeFieldInput(field: CardioEntryField, value: String): String = when (field) {
    CardioEntryField.DURATION, CardioEntryField.PACE_500M ->
        value.filter { it.isDigit() || it == ':' }.take(8)
    else -> value
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalGlassPalette.current.accentStrong,
    unfocusedBorderColor = LocalGlassPalette.current.glassStroke,
    focusedLabelColor = LocalGlassPalette.current.accentStrong,
    unfocusedLabelColor = LocalGlassPalette.current.inkSubtle,
    focusedTextColor = LocalGlassPalette.current.ink,
    unfocusedTextColor = LocalGlassPalette.current.ink,
    cursorColor = LocalGlassPalette.current.accentStrong,
)

// endregion

// region Detail sheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioDetailSheet(
    session: CardioSession,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val type = activityTypeFor(session)
    var confirmDelete by remember(session.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), strong = true)
            .padding(18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivitySymbolTile(type = type, size = 56.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.activityLabel ?: CardioActivitySpecs.labelFor(type),
                    color = palette.ink,
                    style = LogType.exerciseTitle,
                )
                Text(
                    text = "${formatDateLabel(session.date)} · ${sourceLabel(session.source)}",
                    color = palette.inkSubtle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.inkSubtle)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sessionMetricPairs(session).forEach { (label, value) ->
                DetailMetric(label = label, value = value)
            }
        }

        session.notes.takeIf { it.isNotBlank() }?.let {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notes", color = palette.inkSubtle, style = MaterialTheme.typography.labelSmall)
                Text(it, color = palette.inkMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = palette.danger),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = if (confirmDelete) "Tap to confirm" else "Delete",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = palette.accentStrong, modifier = Modifier.size(16.dp))
                Text("Edit", color = palette.accentStrong, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentStrong, contentColor = palette.pageBottom),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.glassFill.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, color = palette.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, color = palette.inkSubtle, style = MaterialTheme.typography.labelSmall)
    }
}

// endregion

// region Shared visuals

@Composable
private fun ActivitySymbolTile(
    type: CardioActivityType,
    size: Dp,
) {
    val palette = LocalGlassPalette.current
    val accent = activityAccent(type, palette)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.30f),
                        accent.copy(alpha = 0.10f),
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.40f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = activityIcon(type),
            contentDescription = CardioActivitySpecs.labelFor(type),
            tint = accent,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

private fun activityIcon(type: CardioActivityType): ImageVector = when (type) {
    CardioActivityType.TREADMILL -> Icons.Filled.Speed
    CardioActivityType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    CardioActivityType.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
    CardioActivityType.ROW -> Icons.Filled.Rowing
    CardioActivityType.SWIM -> Icons.Filled.Pool
    CardioActivityType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    CardioActivityType.ELLIPTICAL -> Icons.Filled.AllInclusive
    CardioActivityType.STAIR_CLIMBER -> Icons.Filled.Stairs
    CardioActivityType.OTHER -> Icons.Filled.MoreHoriz
}

private fun activityAccent(type: CardioActivityType, palette: GlassPalette): Color = when (type) {
    CardioActivityType.TREADMILL -> palette.accentStrong
    CardioActivityType.RUN -> palette.auraCyan
    CardioActivityType.BIKE -> palette.auraGreen
    CardioActivityType.ROW -> palette.auraBlue
    CardioActivityType.SWIM -> lerp(palette.auraCyan, palette.auraBlue, 0.45f)
    CardioActivityType.WALK -> lerp(palette.auraGreen, palette.accentStrong, 0.5f)
    CardioActivityType.ELLIPTICAL -> lerp(palette.auraBlue, palette.auraCyan, 0.5f)
    CardioActivityType.STAIR_CLIMBER -> lerp(palette.accent, palette.auraBlue, 0.35f)
    CardioActivityType.OTHER -> palette.inkMuted
}

// endregion

// region Field copy + formatting

private data class FieldCopy(
    val label: String,
    val placeholder: String,
    val keyboardType: KeyboardType,
)

private fun fieldCopy(field: CardioEntryField): FieldCopy = when (field) {
    CardioEntryField.DURATION -> FieldCopy("Duration", "32:15", KeyboardType.Text)
    CardioEntryField.DISTANCE_MILES -> FieldCopy("Miles", "2.50", KeyboardType.Decimal)
    CardioEntryField.AVG_SPEED_MPH -> FieldCopy("Avg mph", "6.2", KeyboardType.Decimal)
    CardioEntryField.AVG_INCLINE_PERCENT -> FieldCopy("Incline %", "5.0", KeyboardType.Decimal)
    CardioEntryField.CALORIES -> FieldCopy("Calories", "310", KeyboardType.Number)
    CardioEntryField.AVG_HEART_RATE -> FieldCopy("Avg HR", "142", KeyboardType.Number)
    CardioEntryField.MAX_HEART_RATE -> FieldCopy("Max HR", "166", KeyboardType.Number)
    CardioEntryField.CADENCE_RPM -> FieldCopy("RPM", "82", KeyboardType.Number)
    CardioEntryField.RESISTANCE_LEVEL -> FieldCopy("Level", "8", KeyboardType.Decimal)
    CardioEntryField.AVG_POWER_WATTS -> FieldCopy("Watts", "185", KeyboardType.Number)
    CardioEntryField.STROKE_RATE_SPM -> FieldCopy("Stroke rate", "28", KeyboardType.Number)
    CardioEntryField.PACE_500M -> FieldCopy("Split / 500m", "2:00", KeyboardType.Text)
    CardioEntryField.FLOORS -> FieldCopy("Floors", "42", KeyboardType.Number)
    CardioEntryField.STEPS -> FieldCopy("Steps", "860", KeyboardType.Number)
    CardioEntryField.NOTES -> FieldCopy("Notes", "Machine, route, effort", KeyboardType.Text)
}

private fun CardioUiState.valueFor(field: CardioEntryField): String = when (field) {
    CardioEntryField.DURATION -> durationText
    CardioEntryField.DISTANCE_MILES -> distanceText
    CardioEntryField.AVG_SPEED_MPH -> avgSpeedText
    CardioEntryField.AVG_INCLINE_PERCENT -> inclineText
    CardioEntryField.CALORIES -> caloriesText
    CardioEntryField.AVG_HEART_RATE -> heartRateText
    CardioEntryField.MAX_HEART_RATE -> maxHeartRateText
    CardioEntryField.CADENCE_RPM -> cadenceText
    CardioEntryField.RESISTANCE_LEVEL -> resistanceText
    CardioEntryField.AVG_POWER_WATTS -> powerText
    CardioEntryField.STROKE_RATE_SPM -> strokeRateText
    CardioEntryField.PACE_500M -> pace500Text
    CardioEntryField.FLOORS -> floorsText
    CardioEntryField.STEPS -> stepsText
    CardioEntryField.NOTES -> notesText
}

private fun activityTypeFor(session: CardioSession): CardioActivityType =
    CardioActivitySpecs.fromStoredType(session.activityType)

private fun sourceLabel(source: String): String = when (source) {
    CardioSessionSource.OCR -> "Scanned"
    CardioSessionSource.HEALTH_CONNECT -> "Watch"
    else -> "Manual"
}

/**
 * Fixed-priority metric list: the first entry is the row's right-aligned anchor,
 * the rest feed the secondary line — so nothing is ever shown twice and a row
 * never anchors on "—" while it still has something meaningful to say.
 */
private fun sessionMetricsOrdered(session: CardioSession): List<String> {
    val type = activityTypeFor(session)
    return buildList {
        session.durationSec?.let { add(formatDuration(it)) }
        session.distanceM?.let {
            add(if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) formatMeters(it) else formatMiles(it))
        }
        session.floors?.let { add("$it floors") }
        session.calories?.let { add("%,d cal".format(Locale.US, it)) }
        session.avgHeartRate?.let { add("$it bpm") }
        session.avgSpeed?.let { add(formatMph(it)) }
        session.avgInclinePercent?.let { add(formatPercent(it)) }
        session.cadenceRpm?.let { add("$it rpm") }
        session.paceSecPer500m?.let { add("${formatDuration(it)}/500m") }
        session.steps?.let { add("%,d steps".format(Locale.US, it)) }
    }
}

private fun sessionMetricPairs(session: CardioSession): List<Pair<String, String>> {
    val type = activityTypeFor(session)
    return buildList {
        session.durationSec?.let { add("Duration" to formatDuration(it)) }
        session.distanceM?.let {
            add("Distance" to if (type == CardioActivityType.ROW || type == CardioActivityType.SWIM) formatMeters(it) else formatMiles(it))
        }
        session.avgSpeed?.let { add("Avg speed" to formatMph(it)) }
        session.avgInclinePercent?.let { add("Incline" to formatPercent(it)) }
        session.calories?.let { add("Calories" to "%,d cal".format(Locale.US, it)) }
        session.avgHeartRate?.let { add("Avg HR" to "$it bpm") }
        session.maxHeartRate?.let { add("Max HR" to "$it bpm") }
        session.cadenceRpm?.let { add("RPM" to "$it rpm") }
        session.resistanceLevel?.let { add("Level" to formatNumber(it)) }
        session.avgPowerWatts?.let { add("Power" to "$it w") }
        session.strokeRateSpm?.let { add("Stroke rate" to "$it spm") }
        session.paceSecPer500m?.let { add("Split" to "${formatDuration(it)}/500m") }
        session.floors?.let { add("Floors" to it.toString()) }
        session.steps?.let { add("Steps" to "%,d".format(Locale.US, it)) }
    }
}

private fun formatDateLabel(date: String): String =
    runCatching {
        val parsed = LocalDate.parse(date)
        val today = LocalDate.now()
        when (parsed) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> parsed.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
    }.getOrDefault(date)

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.US, hours, minutes, secs) else "%d:%02d".format(Locale.US, minutes, secs)
}

private fun formatDurationCompact(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        seconds <= 0 -> "0m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun formatMiles(meters: Double): String =
    "%.2f mi".format(Locale.US, meters / METERS_PER_MILE)

private fun formatMeters(meters: Double): String =
    "%,d m".format(Locale.US, meters.roundToInt())

private fun formatMph(metersPerSecond: Double): String =
    "%.1f mph".format(Locale.US, metersPerSecond / METERS_PER_SECOND_PER_MPH)

private fun formatPercent(value: Double): String =
    "%.1f%%".format(Locale.US, value)

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.1f".format(Locale.US, value)

private const val METERS_PER_MILE = 1609.344
private const val METERS_PER_SECOND_PER_MPH = 0.44704

// endregion
