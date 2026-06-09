package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.AvailableSessionUi
import com.ayman.ecolift.ui.viewmodel.CycleEntry
import com.ayman.ecolift.ui.viewmodel.CycleArchiveViewModel
import com.ayman.ecolift.ui.viewmodel.Split
import com.ayman.ecolift.ui.viewmodel.SplitCycle
import com.ayman.ecolift.ui.viewmodel.SplitExerciseRef
import com.ayman.ecolift.ui.viewmodel.SplitTabMode
import com.ayman.ecolift.ui.viewmodel.SplitUiState
import com.ayman.ecolift.ui.viewmodel.SplitViewModel
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import kotlin.math.roundToInt
import java.time.LocalDate

import com.ayman.ecolift.ui.theme.*

// ... rest of the file ...

// ============================================================================
// Stateful entry
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    viewModel: SplitViewModel = viewModel(),
    archiveViewModel: CycleArchiveViewModel = viewModel(),
    onNavigateToLog: (splitId: Long) -> Unit = {},
    onNavigateToExerciseProgress: (exerciseId: Long) -> Unit = {},
    onNavigateToArchiveDetail: (archiveId: Long) -> Unit = {},
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val availableSessions by viewModel.availableSessions.collectAsStateWithLifecycle()
    val workedDays by viewModel.workedDays.collectAsStateWithLifecycle()
    val archives by archiveViewModel.archives.collectAsStateWithLifecycle()
    var tabMode by remember { mutableStateOf(SplitTabMode.CURRENT) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }

    var detailSplitId by remember { mutableStateOf<Long?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val splitTypes = remember(state.splits) {
        state.splits.map { split -> 
            SplitType(
                id = split.id, 
                name = split.name,
                exerciseCount = split.exercises.size,
                lastRunLabel = split.lastPerformedEpochDay?.let { relativeAge(it) } ?: "Never run"
            )
        }
    }

    CycleSplitScreen(
        splits = splitTypes,
        gymDays = workedDays,
        splitCycleEnabled = state.cycle.enabled,
        currentSplitIndex = state.cycle.currentIndex,
        onToggleSplitCycle = viewModel::toggleCycle,
        onLoadWorkout = { 
            val currentId = state.splits.getOrNull(state.cycle.currentIndex)?.id
            if (currentId != null) {
                viewModel.advanceCycle()
                onNavigateToLog(currentId)
            }
        },
        onEditSplit = { detailSplitId = it.id },
        onAddSplit = { showAddDialog = true },
        onSplitOptions = { detailSplitId = it.id },
        tabMode = tabMode,
        onTabModeChange = { tabMode = it },
        archives = archives,
        onOpenArchive = onNavigateToArchiveDetail,
        onArchiveCurrentCycle = { showArchiveDialog = true },
        paletteChoice = paletteChoice,
        onPaletteChoiceChange = onPaletteChoiceChange,
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

    if (showArchiveDialog) {
        ArchiveCycleDialog(
            loadDefaults = { archiveViewModel.defaultArchiveWindow() },
            checkOverlap = { start, end -> archiveViewModel.overlapCount(start, end) },
            onConfirm = { name, start, end ->
                archiveViewModel.archiveCurrentCycle(name, start, end)
                showArchiveDialog = false
            },
            onDismiss = { showArchiveDialog = false },
        )
    }

    val detailSplit = detailSplitId?.let { id -> state.splits.firstOrNull { it.id == id } }
    if (detailSplit != null) {
        val palette = LocalGlassPalette.current
        ModalBottomSheet(
            onDismissRequest = { detailSplitId = null },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            scrimColor = palette.scrim,
            dragHandle = { Spacer(Modifier.height(6.dp)) },
        ) {
            SplitDetailSheetV2(
                split = detailSplit,
                availableSessions = availableSessions,
                onLoad = {
                    viewModel.advanceCycle()
                    onNavigateToLog(detailSplit.id)
                    detailSplitId = null
                },
                onRename = { newName -> viewModel.renameSplit(detailSplit.id, newName) },
                onDelete = {
                    viewModel.deleteSplit(detailSplit.id)
                    detailSplitId = null
                },
                onSaveFromDate = { date -> viewModel.saveSplitFromDate(detailSplit.id, date) },
                onClearSaved = { viewModel.clearSavedExercises(detailSplit.id) },
                onOpenExerciseProgress = onNavigateToExerciseProgress,
            )
        }
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
        color = BackgroundPrimary,
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
                            color = TextInactive,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    item {
                        Text(
                            "Hold + drag a split to reorder",
                            color = TextInactive,
                            fontSize = 10.sp,
                            letterSpacing = 0.sp,
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
            containerColor = BackgroundSurface,
            dragHandle = { Spacer(Modifier.height(6.dp)) },
        ) {
            SplitDetailSheetV2(
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
        color = BackgroundPrimary,
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
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Workout rotation settings",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
@Composable
private fun EnableCycleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
        border = BorderStroke(1.dp, BorderDefault),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Enable Split Cycle",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Pre-load exercises based on your rotation",
                    color = TextInactive,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentTeal,
                    checkedBorderColor = AccentTeal,
                    uncheckedThumbColor = TextInactive,
                    uncheckedTrackColor = BackgroundSurface,
                    uncheckedBorderColor = BorderSubtle,
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
        colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
        border = BorderStroke(1.dp, AccentTeal20),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(AccentTeal))
                Spacer(Modifier.width(8.dp))
                Text(
                    "TODAY · DAY ${cycle.currentIndex + 1} OF ${cycle.order.size}",
                    color = AccentTeal,
                    style = MaterialTheme.typography.labelLarge.copy(color = AccentTeal),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${today.name} Day",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W800),
            )
            Spacer(Modifier.height(2.dp))
            val exCount = today.exercises.size
            val durTxt = if (today.estimatedDurationMin > 0) "~${today.estimatedDurationMin} min" else "no history"
            val savedBadge = if (today.isSaved) " · saved" else ""
            Text(
                "$exCount exercises · $durTxt · last run ${relativeAge(today.lastPerformedEpochDay)}$savedBadge",
                color = TextInactive,
                style = MaterialTheme.typography.bodySmall,
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentTeal,
                        contentColor = BackgroundPrimary,
                    ),
                ) { Text("Load Workout", style = MaterialTheme.typography.titleSmall.copy(color = BackgroundPrimary)) }
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                ) { Text("Edit", style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}

@Composable
private fun RotationStrip(cycle: SplitCycle) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundElevated),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 13.dp)) {
            Text("ROTATION", style = MaterialTheme.typography.labelLarge)
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
                                .background(if (isCurrent) AccentTeal else BorderSubtle)
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = entry.label,
                            color = if (isCurrent) AccentTeal else TextInactive,
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
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(
            text = actionLabel,
            color = AccentTeal,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
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
        colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
        border = BorderStroke(
            1.dp,
            if (isDragging) AccentTeal35 else BorderDefault,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = TextInactive,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            split.name,
                            color = TextPrimary,
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
                        color = TextInactive,
                        fontSize = 11.sp,
                    )
                }
                val firstVol = split.recentVolume.firstOrNull() ?: 0f
                val lastVol = split.recentVolume.lastOrNull() ?: 0f
                val volColor = when {
                    split.recentVolume.size < 2 -> if (isToday) AccentTeal else TextInactive
                    lastVol >= firstVol -> AccentTeal
                    else -> ErrorRed
                }
                MiniSparkline(
                    values = split.recentVolume,
                    color = volColor,
                    modifier = Modifier.width(56.dp).height(22.dp),
                )
            }
            if (split.exercises.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    split.exercises.joinToString(" · ") { it.displayName.shortened() },
                    color = TextInactive,
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
            .background(AccentTeal15)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            "TODAY",
            color = AccentTeal,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun SavedBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(BackgroundSubtle)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            "SAVED",
            color = TextInactive,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
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
            .background(if (dim) BackgroundSubtle else BackgroundElevated)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            color = if (dim) TextInactive else TextPrimary,
            fontSize = 11.sp,
        )
    }
}

// ============================================================================
// Detail sheet (rename + save-from-day + exercise list + delete)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
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
    val palette = LocalGlassPalette.current
    var showSavePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    var name by remember(split.id) { mutableStateOf(split.name) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        Text("SPLIT · CLICK NAME TO RENAME", style = MaterialTheme.typography.labelLarge.copy(color = AccentTeal))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = BackgroundElevated,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = AccentTeal,
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
            color = TextInactive,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onLoad,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = BackgroundPrimary,
                ),
            ) { Text("Load into log", fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            OutlinedButton(
                onClick = { confirmDelete = true },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ErrorRedSoft),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EXERCISES", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (split.isSaved) {
                Text(
                    "CLEAR SAVED",
                    color = TextInactive,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClearSaved() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (split.exercises.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FitnessCenter,
                    contentDescription = null,
                    tint = AccentTeal.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No exercises yet",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Save exercises from a past workout to build this split",
                    color = TextInactive,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showSavePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentTeal,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add Exercises", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
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
                    border = BorderStroke(1.dp, AccentTeal),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitDetailSheetV2(
    split: Split,
    availableSessions: List<AvailableSessionUi>,
    onLoad: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSaveFromDate: (String) -> Unit,
    onClearSaved: () -> Unit,
    onOpenExerciseProgress: (exerciseId: Long) -> Unit,
) {
    val palette = LocalGlassPalette.current
    var showSavePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember(split.id) { mutableStateOf(split.name) }
    val durationText = if (split.estimatedDurationMin > 0) "~${split.estimatedDurationMin} min" else "No history"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .glassPanel(palette, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), strong = true)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(palette.glassStrokeStrong),
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Edit split",
            style = MaterialTheme.typography.labelLarge,
            color = palette.accentStrong,
            fontWeight = FontWeight.W800,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.W800,
                color = palette.ink,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.glassStrokeStrong,
                unfocusedBorderColor = palette.glassStroke,
                focusedContainerColor = palette.glassFillStrong,
                unfocusedContainerColor = palette.glassFill,
                focusedTextColor = palette.ink,
                unfocusedTextColor = palette.ink,
                cursorColor = palette.accentStrong,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        LaunchedEffect(name) {
            if (name.isNotBlank() && name.trim() != split.name) onRename(name.trim())
        }

        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SplitMetricPill("${split.exercises.size} exercises")
            SplitMetricPill("Last run ${relativeAge(split.lastPerformedEpochDay)}")
            SplitMetricPill(durationText)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onLoad,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent.copy(alpha = 0.90f),
                    contentColor = palette.ink,
                ),
            ) {
                Text("Start workout", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = palette.ink)
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.40f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.danger),
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = palette.danger)
                Spacer(Modifier.width(5.dp))
                Text("Delete", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = palette.danger)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Exercises", style = MaterialTheme.typography.titleSmall, color = palette.ink)
                Text("Tap any lift to view progress", color = palette.inkSubtle, fontSize = 11.sp)
            }
            if (split.isSaved) {
                Text(
                    "Reset",
                    color = palette.inkSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClearSaved() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (split.exercises.isEmpty()) {
            EmptySplitExercises(onAddExercises = { showSavePicker = true })
        } else {
            split.exercises.forEach { exercise ->
                SplitExerciseEditRow(
                    ref = exercise,
                    onClick = { onOpenExerciseProgress(exercise.exerciseId) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showSavePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.glassStrokeStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accentStrong),
            ) {
                Text(
                    "Update exercises from a workout",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.accentStrong,
                )
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
private fun SplitMetricPill(text: String) {
    val palette = LocalGlassPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(palette.accent.copy(alpha = 0.12f))
            .border(1.dp, palette.glassStroke, RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = palette.inkMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptySplitExercises(onAddExercises: () -> Unit) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.FitnessCenter,
            contentDescription = null,
            tint = palette.accentStrong.copy(alpha = 0.58f),
            modifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No exercises yet",
            color = palette.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Choose a previous workout to fill this split.",
            color = palette.inkSubtle,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAddExercises,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent.copy(alpha = 0.90f),
                contentColor = palette.ink,
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = palette.ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add exercises", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.ink)
        }
    }
}

@Composable
private fun SplitExerciseEditRow(ref: SplitExerciseRef, onClick: () -> Unit) {
    val palette = LocalGlassPalette.current
    val trend = ref.recentMaxVolume
    val delta = trend.deltaPercentOrNull()
    val first = trend.firstOrNull() ?: 0f
    val last = trend.lastOrNull() ?: 0f
    val trendColor = when {
        trend.size < 2 -> palette.inkSubtle
        last >= first -> palette.complete
        else -> palette.danger
    }
    val trendLabel = when {
        trend.isEmpty() -> "No history yet"
        delta == null -> "Recent trend steady"
        delta > 0 -> "+${"%.1f".format(delta)}% over last ${trend.size} sessions"
        delta < 0 -> "${"%.1f".format(delta)}% over last ${trend.size} sessions"
        else -> "Recent trend steady"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, palette.glassStroke),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FitnessCenter,
                    contentDescription = null,
                    tint = palette.accentStrong,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ref.displayName,
                    color = palette.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    trendLabel,
                    color = when {
                        trend.isEmpty() || delta == null -> palette.inkSubtle
                        delta > 0 -> palette.complete
                        delta < 0 -> palette.danger
                        else -> palette.inkSubtle
                    },
                    fontSize = 11.sp,
                )
            }
            MiniSparkline(
                values = trend,
                color = trendColor,
                modifier = Modifier.width(64.dp).height(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(">", color = palette.inkSubtle, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ExerciseProgressRow(ref: SplitExerciseRef, onClick: () -> Unit) {
    val palette = LocalGlassPalette.current
    val trend = ref.recentMaxVolume
    val delta = trend.deltaPercentOrNull()

    val first = trend.firstOrNull() ?: 0f
    val last = trend.lastOrNull() ?: 0f
    val trendColor = when {
        trend.size < 2 -> palette.inkSubtle
        last >= first -> palette.complete
        else -> palette.danger
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ref.displayName,
                    color = palette.ink,
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
                        trend.isEmpty() || delta == null -> palette.inkSubtle
                        delta > 0 -> palette.complete
                        delta < 0 -> palette.danger
                        else -> palette.inkSubtle
                    },
                    fontSize = 11.sp,
                )
            }
            MiniSparkline(
                values = trend,
                color = trendColor,
                modifier = Modifier.width(64.dp).height(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("›", color = palette.inkSubtle, fontSize = 20.sp)
        }
    }
}

// ============================================================================
// Dialogs
// ============================================================================

@Composable
private fun AddSplitDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val palette = LocalGlassPalette.current
    val dialogFill = palette.glassFillStrong.copy(alpha = 0.94f)
    val fieldFill = palette.glassFillStrong.copy(alpha = 0.88f)
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .glassPanel(palette, RoundedCornerShape(18.dp), strong = true),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = dialogFill),
            border = BorderStroke(1.dp, palette.glassStrokeStrong),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Add split",
                    color = palette.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Name it whatever makes sense to you - \"Upper A\", \"Heavy day\", \"Arms\", anything.",
                    color = palette.inkSubtle,
                    fontSize = 11.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Split name", color = palette.inkSubtle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accentStrong,
                        unfocusedBorderColor = palette.glassStroke,
                        focusedContainerColor = fieldFill,
                        unfocusedContainerColor = fieldFill,
                        cursorColor = palette.accentStrong,
                        focusedTextColor = palette.ink,
                        unfocusedTextColor = palette.ink,
                    ),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = palette.inkSubtle)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentStrong,
                            contentColor = palette.pageTop,
                        ),
                    ) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun ArchiveCycleDialog(
    loadDefaults: suspend () -> Pair<String, String>,
    checkOverlap: suspend (String, String) -> Int,
    onConfirm: (name: String, start: String, end: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var overlap by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val defaults = loadDefaults()
        start = defaults.first
        end = defaults.second
        loaded = true
    }

    val validRange = remember(start, end) {
        runCatching { !LocalDate.parse(start).isAfter(LocalDate.parse(end)) }
            .getOrDefault(false)
    }

    LaunchedEffect(start, end, loaded, validRange) {
        overlap = if (loaded && validRange) checkOverlap(start, end) else 0
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .glassPanel(palette, RoundedCornerShape(18.dp), strong = true),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, palette.glassStrokeStrong),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Archive cycle",
                    color = palette.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Freeze this cycle's progress between two dates. Sets outside the range are not counted.",
                    color = palette.inkSubtle,
                    fontSize = 11.sp,
                )
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accentStrong,
                    unfocusedBorderColor = palette.glassStroke,
                    focusedContainerColor = palette.glassFillStrong,
                    unfocusedContainerColor = palette.glassFill,
                    cursorColor = palette.accentStrong,
                    focusedTextColor = palette.ink,
                    unfocusedTextColor = palette.ink,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Cycle name (optional)", color = palette.inkSubtle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Start (YYYY-MM-DD)", color = palette.inkSubtle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("End (YYYY-MM-DD)", color = palette.inkSubtle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                if (loaded && !validRange) {
                    Text(
                        "Enter valid dates with start on or before end.",
                        color = palette.danger,
                        fontSize = 11.sp,
                    )
                }
                if (overlap > 0) {
                    Text(
                        "This range overlaps $overlap existing ${if (overlap == 1) "archive" else "archives"}.",
                        color = palette.danger,
                        fontSize = 11.sp,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = palette.inkSubtle)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(name.trim(), start.trim(), end.trim()) },
                        enabled = validRange,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentStrong,
                            contentColor = palette.pageTop,
                        ),
                    ) {
                        Text("Archive")
                    }
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
    val palette = LocalGlassPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .glassPanel(palette, RoundedCornerShape(18.dp), strong = true),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, palette.glassStrokeStrong),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Delete \"$splitName\"?",
                    color = palette.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Past workout days stay logged. Only the split entry and its saved exercise list are removed.",
                    color = palette.inkSubtle,
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = palette.inkSubtle)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.danger,
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
    val palette = LocalGlassPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .glassPanel(palette, RoundedCornerShape(18.dp), strong = true),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, palette.glassStrokeStrong),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Pick a workout day",
                    color = palette.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "The exercises from that day become this split's saved list. Reps/weights stay with the log itself.",
                    color = palette.inkSubtle,
                    fontSize = 11.sp,
                )

                if (sessions.isEmpty()) {
                    Text(
                        "No logged workouts yet. Log a session in the Log tab first, then come back.",
                        color = palette.inkSubtle,
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
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, palette.glassStroke),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassPanel(palette, RoundedCornerShape(10.dp))
                                    .clickable { onPick(s.date) },
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            s.date,
                                            color = palette.ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${s.exerciseCount} exercises",
                                            color = palette.inkSubtle,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    if (s.preview.isNotEmpty()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            s.preview.joinToString(" · "),
                                            color = palette.inkSubtle,
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
                        Text("Cancel", color = palette.inkSubtle)
                    }
                }
            }
        }
    }
}

// ============================================================================
// Primitives
// ============================================================================

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
