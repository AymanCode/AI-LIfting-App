package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.AvailableSessionUi
import com.ayman.ecolift.ui.viewmodel.CycleEntry
import com.ayman.ecolift.ui.viewmodel.Split
import com.ayman.ecolift.ui.viewmodel.SplitCycle
import com.ayman.ecolift.ui.viewmodel.SplitExerciseRef
import com.ayman.ecolift.ui.viewmodel.SplitUiState
import com.ayman.ecolift.ui.viewmodel.SplitViewModel
import kotlin.math.roundToInt
import java.time.LocalDate

// ============================================================================
// Theme tokens - terminal-style dark palette
// ============================================================================

private object SplitTheme {
    val Background      = Color(0xFF0A0A0B)
    val Surface         = Color(0xFF141518)
    val SurfaceSunken   = Color(0xFF101114)
    val Divider         = Color(0x0FFFFFFF)
    val ChipBg          = Color(0x0DFFFFFF)
    val ChipBgDim       = Color(0x08FFFFFF)

    val Accent          = Color(0xFF1DD3B0)
    val AccentOnAccent  = Color(0xFF04342C)
    val AccentBorder    = Color(0x381DD3B0)
    val AccentBgSoft    = Color(0x241DD3B0)

    val TextPrimary     = Color(0xFFF4F4F5)
    val TextSecondary   = Color(0xFF8A8A8E)
    val TextTertiary    = Color(0xFF6B6B70)
    val TextDim         = Color(0xFF5F5F64)
    val SegmentOff      = Color(0xFF222428)
    val DangerSoft      = Color(0x26C23A3A)
    val Danger          = Color(0xFFE57373)
}

private val LabelCaps = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.3.sp,
    color = SplitTheme.TextTertiary,
)

// ============================================================================
// Stateful entry
// ============================================================================

@Composable
fun SplitScreen(
    viewModel: SplitViewModel = viewModel(),
    onNavigateToLog: (splitId: Long) -> Unit = {},
    onNavigateToExerciseProgress: (exerciseId: Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val availableSessions by viewModel.availableSessions.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    SplitScreenContent(
        state = state,
        availableSessions = availableSessions,
        onToggleCycle = viewModel::toggleCycle,
        onLoadSplit = { splitId ->
            viewModel.advanceCycle()
            onNavigateToLog(splitId)
        },
        onRenameSplit = viewModel::renameSplit,
        onDeleteSplit = viewModel::deleteSplit,
        onReorderSplits = viewModel::reorderSplits,
        onSaveSplitFromDate = viewModel::saveSplitFromDate,
        onClearSavedExercises = viewModel::clearSavedExercises,
        onAddSplit = { showAddDialog = true },
        onOpenExerciseProgress = onNavigateToExerciseProgress,
    )

    if (showAddDialog) {
        AddSplitDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addSplit(name)
                showAddDialog = false
            },
        )
    }
}

// ============================================================================
// Stateless screen
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitScreenContent(
    state: SplitUiState,
    availableSessions: List<AvailableSessionUi>,
    onToggleCycle: (Boolean) -> Unit,
    onLoadSplit: (splitId: Long) -> Unit,
    onRenameSplit: (splitId: Long, newName: String) -> Unit,
    onDeleteSplit: (splitId: Long) -> Unit,
    onReorderSplits: (idsInOrder: List<Long>) -> Unit,
    onSaveSplitFromDate: (splitId: Long, date: String) -> Unit,
    onClearSavedExercises: (splitId: Long) -> Unit,
    onAddSplit: () -> Unit,
    onOpenExerciseProgress: (exerciseId: Long) -> Unit,
) {
    var detailSplitId by remember { mutableStateOf<Long?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SplitTheme.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Header()
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    EnableCycleCard(enabled = state.cycle.enabled, onToggle = onToggleCycle)
                    Spacer(Modifier.height(14.dp))
                }

                if (state.cycle.enabled && state.cycle.order.isNotEmpty()) {
                    item {
                        val today = state.today
                        if (today != null) {
                            TodayCard(
                                cycle = state.cycle,
                                today = today,
                                onLoad = { onLoadSplit(today.id) },
                                onEdit = { detailSplitId = today.id },
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    item {
                        RotationStrip(cycle = state.cycle)
                        Spacer(Modifier.height(18.dp))
                    }
                } else {
                    item { Spacer(Modifier.height(4.dp)) }
                }

                item {
                    SectionHeader(
                        label = "MY SPLITS",
                        actionLabel = "+ ADD TYPE",
                        onAction = onAddSplit,
                    )
                }

                if (state.splits.isEmpty()) {
                    item {
                        Text(
                            "No splits yet. Add one above - pick any name that fits your routine.",
                            color = SplitTheme.TextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    item {
                        Text(
                            "Hold + drag a split to reorder",
                            color = SplitTheme.TextDim,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                        )
                    }
                    item {
                        ReorderableSplitList(
                            splits = state.splits,
                            todayId = state.today?.id,
                            onCardTap = { detailSplitId = it },
                            onReorder = onReorderSplits,
                        )
                    }
                }
            }
        }
    }

    val detailSplit = detailSplitId?.let { id -> state.splits.firstOrNull { it.id == id } }
    if (detailSplit != null) {
        ModalBottomSheet(
            onDismissRequest = { detailSplitId = null },
            sheetState = sheetState,
            containerColor = SplitTheme.Surface,
            dragHandle = { Spacer(Modifier.height(6.dp)) },
        ) {
            SplitDetailSheet(
                split = detailSplit,
                availableSessions = availableSessions,
                onLoad = {
                    onLoadSplit(detailSplit.id)
                    detailSplitId = null
                },
                onRename = { newName -> onRenameSplit(detailSplit.id, newName) },
                onDelete = {
                    onDeleteSplit(detailSplit.id)
                    detailSplitId = null
                },
                onSaveFromDate = { date -> onSaveSplitFromDate(detailSplit.id, date) },
                onClearSaved = { onClearSavedExercises(detailSplit.id) },
                onOpenExerciseProgress = onOpenExerciseProgress,
            )
        }
    }
}

// ============================================================================
// Header / cycle toggle / today / rotation
// ============================================================================

@Composable
private fun Header() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SplitTheme.Background,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cycle / Split",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "WORKOUT ROTATION SETTINGS",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = SplitTheme.TextTertiary,
                    fontWeight = FontWeight.W800,
                    letterSpacing = 0.08.sp
                )
            )
        }
    }
}
@Composable
private fun EnableCycleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SplitTheme.Surface),
        border = BorderStroke(1.dp, SplitTheme.Divider),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Enable Split Cycle",
                    color = SplitTheme.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Pre-load exercises based on your rotation",
                    color = SplitTheme.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SplitTheme.Accent,
                    checkedBorderColor = SplitTheme.Accent,
                    uncheckedThumbColor = SplitTheme.TextTertiary,
                    uncheckedTrackColor = SplitTheme.Surface,
                    uncheckedBorderColor = SplitTheme.TextDim,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(
    cycle: SplitCycle,
    today: Split,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SplitTheme.Surface),
        border = BorderStroke(1.dp, SplitTheme.AccentBorder),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(SplitTheme.Accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    "TODAY · DAY ${cycle.currentIndex + 1} OF ${cycle.order.size}",
                    color = SplitTheme.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.3.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${today.name} day",
                color = SplitTheme.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
            )
            Spacer(Modifier.height(2.dp))
            val exCount = today.exercises.size
            val durTxt = if (today.estimatedDurationMin > 0) "~${today.estimatedDurationMin} min" else "no history"
            val savedBadge = if (today.isSaved) " · saved" else ""
            Text(
                "$exCount exercises · $durTxt · last run ${relativeAge(today.lastPerformedEpochDay)}$savedBadge",
                color = SplitTheme.TextSecondary,
                fontSize = 12.sp,
            )
            if (exCount > 0) {
                Spacer(Modifier.height(14.dp))
                ExerciseChips(today.exercises.map { it.displayName }, maxVisible = 4)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLoad,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SplitTheme.Accent,
                        contentColor = SplitTheme.AccentOnAccent,
                    ),
                ) { Text("Load workout", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC8C8CC)),
                ) { Text("Edit", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun RotationStrip(cycle: SplitCycle) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SplitTheme.SurfaceSunken),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 13.dp)) {
            Text("ROTATION", style = LabelCaps)
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cycle.order.forEachIndexed { index, entry ->
                    val isCurrent = index == cycle.currentIndex
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isCurrent) SplitTheme.Accent else SplitTheme.SegmentOff)
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = entry.label,
                            color = if (isCurrent) SplitTheme.Accent else SplitTheme.TextTertiary,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LabelCaps, modifier = Modifier.weight(1f))
        Text(
            text = actionLabel,
            color = SplitTheme.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

// ============================================================================
// Reorderable split list
// ============================================================================

@Composable
private fun ReorderableSplitList(
    splits: List<Split>,
    todayId: Long?,
    onCardTap: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val byId = splits.associateBy { it.id }

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    var order by remember { mutableStateOf(splits.map { it.id }) }
    // Re-sync when upstream list changes AND we're not mid-drag
    LaunchedEffect(splits) {
        if (draggingId == null) order = splits.map { it.id }
    }

    val orderRef = rememberUpdatedState(order)
    val onReorderRef = rememberUpdatedState(onReorder)

    var itemHeightPx by remember { mutableStateOf(0f) }
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }

    Column(Modifier.padding(horizontal = 0.dp)) {
        order.forEach { id ->
            val split = byId[id] ?: return@forEach
            val isDragging = draggingId == id

            val animatedY by animateFloatAsState(
                targetValue = if (isDragging) dragOffsetY else 0f,
                animationSpec = if (isDragging) spring(stiffness = 2000f) else spring(),
                label = "dragY",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .onSizeChanged {
                        if (itemHeightPx == 0f && it.height > 0) itemHeightPx = it.height.toFloat()
                    }
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = id
                                dragOffsetY = 0f
                            },
                            onDrag = { _, drag ->
                                dragOffsetY += drag.y
                                val unit = itemHeightPx + spacingPx
                                if (unit <= 0f) return@detectDragGesturesAfterLongPress
                                val currentList = orderRef.value
                                val currentIdx = currentList.indexOf(id)
                                if (currentIdx < 0) return@detectDragGesturesAfterLongPress
                                val target = (currentIdx + (dragOffsetY / unit).roundToInt())
                                    .coerceIn(0, currentList.lastIndex)
                                if (target != currentIdx) {
                                    order = currentList.toMutableList().apply {
                                        removeAt(currentIdx)
                                        add(target, id)
                                    }
                                    dragOffsetY -= (target - currentIdx) * unit
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffsetY = 0f
                                onReorderRef.value(orderRef.value)
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffsetY = 0f
                            },
                        )
                    }
            ) {
                SplitCard(
                    split = split,
                    isToday = todayId == split.id,
                    isDragging = isDragging,
                    translationY = animatedY,
                    onClick = { if (draggingId == null) onCardTap(split.id) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================================
// SplitCard
// ============================================================================

@Composable
private fun SplitCard(
    split: Split,
    isToday: Boolean,
    isDragging: Boolean = false,
    translationY: Float = 0f,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .graphicsLayerSafe(translationY, isDragging)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SplitTheme.Surface),
        border = BorderStroke(
            1.dp,
            if (isDragging) SplitTheme.AccentBorder else SplitTheme.Divider,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = SplitTheme.TextDim,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            split.name,
                            color = SplitTheme.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (isToday) {
                            Spacer(Modifier.width(7.dp))
                            TodayBadge()
                        }
                        if (split.isSaved) {
                            Spacer(Modifier.width(6.dp))
                            SavedBadge()
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${split.exercises.size} exercises · last run ${relativeAge(split.lastPerformedEpochDay)}",
                        color = SplitTheme.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                MiniSparkline(
                    values = split.recentVolume,
                    color = if (isToday) SplitTheme.Accent else SplitTheme.TextDim,
                    modifier = Modifier.width(56.dp).height(22.dp),
                )
            }
            if (split.exercises.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    split.exercises.joinToString(" · ") { it.displayName.shortened() },
                    color = SplitTheme.TextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Modifier.graphicsLayerSafe(ty: Float, dragging: Boolean): Modifier =
    this.graphicsLayer {
        translationY = ty
        scaleX = if (dragging) 1.02f else 1f
        scaleY = if (dragging) 1.02f else 1f
        shadowElevation = if (dragging) 16f else 0f
    }

@Composable
private fun TodayBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(SplitTheme.AccentBgSoft)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            "TODAY",
            color = SplitTheme.Accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun SavedBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(SplitTheme.ChipBg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            "SAVED",
            color = SplitTheme.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseChips(names: List<String>, maxVisible: Int) {
    val visible = names.take(maxVisible)
    val extra = names.size - visible.size
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        visible.forEach { Chip(text = it.shortened(), dim = false) }
        if (extra > 0) Chip(text = "+$extra", dim = true)
    }
}

@Composable
private fun Chip(text: String, dim: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (dim) SplitTheme.ChipBgDim else SplitTheme.ChipBg)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            color = if (dim) SplitTheme.TextTertiary else Color(0xFFC8C8CC),
            fontSize = 11.sp,
        )
    }
}

// ============================================================================
// Detail sheet (rename + save-from-day + exercise list + delete)
// ============================================================================

@Composable
private fun SplitDetailSheet(
    split: Split,
    availableSessions: List<AvailableSessionUi>,
    onLoad: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSaveFromDate: (String) -> Unit,
    onClearSaved: () -> Unit,
    onOpenExerciseProgress: (exerciseId: Long) -> Unit,
) {
    var showSavePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    var name by remember(split.id) { mutableStateOf(split.name) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 24.dp)
    ) {
        Text("SPLIT · CLICK NAME TO RENAME", style = LabelCaps.copy(color = SplitTheme.Accent))
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = SplitTheme.TextPrimary,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SplitTheme.AccentBorder,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = SplitTheme.SurfaceSunken,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = SplitTheme.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        LaunchedEffect(name) {
            // Commit rename when name is non-blank and differs from the live split name.
            if (name.isNotBlank() && name.trim() != split.name) onRename(name.trim())
        }

        Spacer(Modifier.height(10.dp))
        val durTxt = if (split.estimatedDurationMin > 0) "~${split.estimatedDurationMin} min" else "no history"
        Text(
            "${split.exercises.size} exercises · last run ${relativeAge(split.lastPerformedEpochDay)} · $durTxt",
            color = SplitTheme.TextSecondary,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onLoad,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SplitTheme.Accent,
                    contentColor = SplitTheme.AccentOnAccent,
                ),
            ) { Text("Load into log", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            OutlinedButton(
                onClick = { confirmDelete = true },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SplitTheme.DangerSoft),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitTheme.Danger),
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EXERCISES", style = LabelCaps, modifier = Modifier.weight(1f))
            if (split.isSaved) {
                Text(
                    "CLEAR SAVED",
                    color = SplitTheme.TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClearSaved() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (split.exercises.isEmpty()) {
            Text(
                "This split has no saved exercises yet.",
                color = SplitTheme.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { showSavePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SplitTheme.AccentBgSoft,
                    contentColor = SplitTheme.Accent,
                ),
            ) { Text("Save from a workout day", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
        } else {
            split.exercises.forEach { ex ->
                ExerciseProgressRow(
                    ref = ex,
                    onClick = { onOpenExerciseProgress(ex.exerciseId) },
                )
                Spacer(Modifier.height(6.dp))
            }
            if (!split.isSaved) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showSavePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SplitTheme.AccentBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitTheme.Accent),
                ) { Text("Save this list as the split", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            }
        }
    }

    if (showSavePicker) {
        SaveFromDayDialog(
            sessions = availableSessions,
            onDismiss = { showSavePicker = false },
            onPick = { date ->
                onSaveFromDate(date)
                showSavePicker = false
            },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            splitName = split.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun ExerciseProgressRow(ref: SplitExerciseRef, onClick: () -> Unit) {
    val trend = ref.recentMaxVolume
    val delta = trend.deltaPercentOrNull()

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SplitTheme.SurfaceSunken),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ref.displayName,
                    color = SplitTheme.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        trend.isEmpty() -> "no history yet"
                        delta == null -> "steady"
                        delta > 0 -> "+${"%.1f".format(delta)}% over last ${trend.size}"
                        delta < 0 -> "${"%.1f".format(delta)}% over last ${trend.size}"
                        else -> "steady"
                    },
                    color = when {
                        delta == null -> SplitTheme.TextSecondary
                        delta > 0 -> SplitTheme.Accent
                        else -> SplitTheme.TextSecondary
                    },
                    fontSize = 11.sp,
                )
            }
            MiniSparkline(
                values = trend,
                color = if ((delta ?: 0f) > 0) SplitTheme.Accent else SplitTheme.TextDim,
                modifier = Modifier.width(64.dp).height(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("›", color = SplitTheme.TextTertiary, fontSize = 20.sp)
        }
    }
}

// ============================================================================
// Dialogs
// ============================================================================

@Composable
private fun AddSplitDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SplitTheme.Surface),
            border = BorderStroke(1.dp, SplitTheme.Divider),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Add split",
                    color = SplitTheme.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Name it whatever makes sense to you - \"Upper A\", \"Heavy day\", \"Arms\", anything.",
                    color = SplitTheme.TextSecondary,
                    fontSize = 11.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Split name", color = SplitTheme.TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SplitTheme.AccentBorder,
                        unfocusedBorderColor = SplitTheme.Divider,
                        focusedContainerColor = SplitTheme.SurfaceSunken,
                        unfocusedContainerColor = SplitTheme.SurfaceSunken,
                        cursorColor = SplitTheme.Accent,
                        focusedTextColor = SplitTheme.TextPrimary,
                        unfocusedTextColor = SplitTheme.TextPrimary,
                    ),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SplitTheme.TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SplitTheme.Accent,
                            contentColor = SplitTheme.AccentOnAccent,
                        ),
                    ) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    splitName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SplitTheme.Surface),
            border = BorderStroke(1.dp, SplitTheme.Divider),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Delete \"$splitName\"?",
                    color = SplitTheme.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Past workout days stay logged. Only the split entry and its saved exercise list are removed.",
                    color = SplitTheme.TextSecondary,
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SplitTheme.TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC23A3A),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveFromDayDialog(
    sessions: List<AvailableSessionUi>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SplitTheme.Surface),
            border = BorderStroke(1.dp, SplitTheme.Divider),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Pick a workout day",
                    color = SplitTheme.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "The exercises from that day become this split's saved list. Reps/weights stay with the log itself.",
                    color = SplitTheme.TextSecondary,
                    fontSize = 11.sp,
                )

                if (sessions.isEmpty()) {
                    Text(
                        "No logged workouts yet. Log a session in the Log tab first, then come back.",
                        color = SplitTheme.TextTertiary,
                        fontSize = 12.sp,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(sessions.size, key = { sessions[it].date }) { i ->
                            val s = sessions[i]
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = SplitTheme.SurfaceSunken),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(s.date) },
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            s.date,
                                            color = SplitTheme.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${s.exerciseCount} exercises",
                                            color = SplitTheme.TextTertiary,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    if (s.preview.isNotEmpty()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            s.preview.joinToString(" · "),
                                            color = SplitTheme.TextDim,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = SplitTheme.TextSecondary)
                    }
                }
            }
        }
    }
}

// ============================================================================
// Primitives
// ============================================================================

@Composable
private fun MiniSparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        val maxV = values.max()
        val minV = values.min()
        val range = (maxV - minV).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        val padY = 2f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val yNorm = (v - minV) / range
            val y = size.height - padY - yNorm * (size.height - padY * 2)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// ============================================================================
// Utilities
// ============================================================================

private fun relativeAge(epochDay: Long?): String {
    if (epochDay == null) return "never"
    val today = LocalDate.now().toEpochDay()
    val days = today - epochDay
    return when {
        days <= 0L -> "today"
        days == 1L -> "1d ago"
        days < 7L -> "${days}d ago"
        days < 30L -> "${days / 7}w ago"
        days < 365L -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}

private fun String.shortened(): String {
    val trimmed = this.trim()
    if (trimmed.length <= 14) return trimmed
    return trimmed.split(" ").take(2).joinToString(" ")
}

private fun List<Float>.deltaPercentOrNull(): Float? {
    if (size < 2) return null
    val first = first().takeIf { it > 0f } ?: return null
    return ((last() - first) / first) * 100f
}
