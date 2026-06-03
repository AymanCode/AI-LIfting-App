package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ayman.ecolift.ui.theme.AnimatedCounter
import com.ayman.ecolift.ui.theme.AnimatedVolumeCounter
import com.ayman.ecolift.ui.theme.DefaultLogGlassPalette
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.GlassPaletteSwitch
import com.ayman.ecolift.ui.theme.LogGlassPalette
import com.ayman.ecolift.ui.theme.LogMaterialTypography
import com.ayman.ecolift.ui.theme.LogType
import com.ayman.ecolift.ui.theme.bounceClick
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.theme.palette
import com.ayman.ecolift.ui.theme.rememberHeavyHaptic
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class SplitSlot(
    val id: Long,
    val displayName: String,
    val slotLabel: String,
    val isExpected: Boolean
)

data class LoggedSet(
    val id: Long,
    val setNumber: Int,
    val weight: String,
    val reps: String,
    val isBodyweight: Boolean,
    val isCompleted: Boolean,
    val suggestedWeight: String? = null,
    val suggestedReps: String? = null,
    val restSeconds: Int? = null
)

@Immutable
data class ExerciseLog(
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroups: String,
    val previousSession: String?,
    val isNewPB: Boolean = false,
    val sets: List<LoggedSet>
)

data class ExerciseSearchResult(val name: String, val muscleGroups: String)

private enum class NumberInputKind { Weight, Reps }

private data class NumberInputTarget(
    val setId: Long,
    val kind: NumberInputKind,
    val value: String,
    val replaceOnNextKey: Boolean = false,
)

private data class SetActionTarget(
    val exerciseId: Long,
    val set: LoggedSet,
)

private enum class RowSwipeIntent { Delete, Copy }

private fun LazyListState.isAtTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

private fun LazyListState.isAtBottom(): Boolean {
    val last = layoutInfo.visibleItemsInfo.lastOrNull()
    return layoutInfo.totalItemsCount == 0 ||
        (last != null &&
            last.index == layoutInfo.totalItemsCount - 1 &&
            last.offset + last.size <= layoutInfo.viewportEndOffset)
}

private fun Modifier.backgroundChromeGestureStrip(
    enabled: Boolean,
    onRevealChrome: () -> Unit
): Modifier = pointerInput(enabled, onRevealChrome) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startPosition = down.position
        var lastPosition = startPosition
        var isUp = false

        while (!isUp) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
            lastPosition = change.position
            if (change.changedToUpIgnoreConsumed()) {
                isUp = true
            }
        }

        val distance = (lastPosition - startPosition).getDistance()
        val touchSlop = viewConfiguration.touchSlop

        if (distance < touchSlop) {
            onRevealChrome()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTopBar(
    dateLabel: String,
    cycleSlotLabel: String?,
    onDateClick: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        modifier = modifier.statusBarsPadding(),
        windowInsets = WindowInsets(0),
        title = {
            Column(
                modifier = Modifier
                    .glassPanel(palette, RoundedCornerShape(16.dp), strong = true)
                    .clickable(onClick = onDateClick)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dateLabel,
                    style = LogType.dateTitle,
                    color = palette.ink
                )
                if (cycleSlotLabel != null) {
                    Text(
                        text = cycleSlotLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accentStrong
                    )
                } else {
                    Text(
                        text = "No cycle slot",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkSubtle
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onPreviousDay,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .glassPanel(palette, CircleShape)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous Day",
                    tint = palette.ink
                )
            }
        },
        actions = {
            IconButton(
                onClick = onNextDay,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .glassPanel(palette, CircleShape)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next Day",
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
fun SplitChip(
    slot: SplitSlot,
    isSelected: Boolean,
    onClick: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (isSelected) palette.ink.copy(alpha = 0.92f) else palette.glassFillStrong,
        border = BorderStroke(1.dp, if (isSelected) palette.glassStrokeStrong else palette.glassStroke),
        tonalElevation = if (isSelected) 0.dp else 1.dp,
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (slot.isExpected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                )
            }
                Text(
                    text = slot.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) Color.White else palette.ink
                )
            if (slot.isExpected) {
                Text(
                    text = slot.slotLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White.copy(alpha = 0.76f) else palette.accentStrong
                )
            }
        }
    }
}

@Composable
fun SplitSelectorStrip(
    splits: List<SplitSlot>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier
) {
    if (splits.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(
                text = "Today's workout",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink
            )
            Text(
                text = "Choose a saved plan to load its exercises",
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            items(items = splits, key = { it.id }) { slot ->
                val isSelected = selectedId == slot.id
                SplitChip(
                    slot = slot,
                    isSelected = isSelected,
                    onClick = { onSelect(if (isSelected) null else slot.id) },
                    palette = palette
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogSetRow(
    set: LoggedSet,
    palette: LogGlassPalette,
    onWeightStep: (Int) -> Unit,
    onRepsStep: (Int) -> Unit,
    onCompleteSet: () -> Unit,
    onDeleteSet: (LoggedSet) -> Unit,
    onAddSetFrom: () -> Unit,
    onWeightInput: () -> Unit,
    onRepsInput: () -> Unit,
    modifier: Modifier = Modifier,
    onShowPlates: () -> Unit = {},
    onOpenActions: () -> Unit = {},
) {
    val lightHaptic = rememberLightHaptic()
    val heavyHaptic = rememberHeavyHaptic()
    var swipeOffset by remember { mutableStateOf(0f) }
    var swipeIntent by remember { mutableStateOf<RowSwipeIntent?>(null) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = 720f, dampingRatio = 0.82f),
        label = "set_vertical_swipe"
    )
    val swipeThresholdPx = with(LocalDensity.current) { 54.dp.toPx() }
    val rowShape = RoundedCornerShape(14.dp)
    val rowFill = if (set.isCompleted) {
        Brush.linearGradient(
            listOf(
                palette.complete.copy(alpha = 0.30f),
                palette.glassFillStrong,
                palette.auraCyan.copy(alpha = 0.18f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                palette.glassFill.copy(alpha = 0.86f),
                palette.glassFillStrong.copy(alpha = 0.42f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = animatedSwipeOffset
                alpha = 1f - (abs(animatedSwipeOffset) / 320f).coerceIn(0f, 0.12f)
            }
            .semantics {
                contentDescription = "Set ${set.setNumber}, ${formatLoggedSetLoad(set)} pounds, ${set.reps.ifBlank { "no" }} reps"
                stateDescription = if (set.isCompleted) "Completed" else "Incomplete"
                customActions = listOf(
                    CustomAccessibilityAction(if (set.isCompleted) "Mark incomplete" else "Mark complete") {
                        onCompleteSet()
                        true
                    },
                    CustomAccessibilityAction("Add copied set") {
                        onAddSetFrom()
                        true
                    },
                    CustomAccessibilityAction("Delete set") {
                        onDeleteSet(set)
                        true
                    },
                    CustomAccessibilityAction("Edit weight") {
                        onWeightInput()
                        true
                    },
                    CustomAccessibilityAction("Edit reps") {
                        onRepsInput()
                        true
                    },
                )
            }
            .pointerInput(set.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var last = start
                    var axis: String? = null
                    var isUp = false

                    while (!isUp) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        last = change.position
                        if (change.changedToUpIgnoreConsumed()) {
                            isUp = true
                            continue
                        }
                        val dx = last.x - start.x
                        val dy = last.y - start.y
                        if (axis == null && (abs(dx) > viewConfiguration.touchSlop || abs(dy) > viewConfiguration.touchSlop)) {
                            axis = if (abs(dy) > abs(dx) * 1.25f) "vertical" else "other"
                        }
                        if (axis == "vertical") {
                            change.consume()
                            swipeOffset = dy.coerceIn(-swipeThresholdPx * 1.35f, swipeThresholdPx * 1.35f) * 0.42f
                            swipeIntent = when {
                                dy <= -swipeThresholdPx * 0.56f -> RowSwipeIntent.Delete
                                dy >= swipeThresholdPx * 0.56f -> RowSwipeIntent.Copy
                                else -> null
                            }
                        }
                    }

                    val finalIntent = swipeIntent
                    val finalDy = last.y - start.y
                    swipeOffset = 0f
                    swipeIntent = null
                    if (axis == "vertical" && abs(finalDy) >= swipeThresholdPx) {
                        heavyHaptic()
                        if (finalIntent == RowSwipeIntent.Delete || finalDy < 0f) {
                            onDeleteSet(set)
                        } else {
                            onAddSetFrom()
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    heavyHaptic()
                    onOpenActions()
                }
            )
            .glassPanel(palette, rowShape, completed = set.isCompleted)
            .background(rowFill, rowShape)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = {
                    heavyHaptic()
                    onCompleteSet()
                },
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (set.isCompleted) palette.complete else palette.glassFillStrong,
                border = BorderStroke(1.dp, if (set.isCompleted) palette.complete else palette.glassStroke),
                contentColor = if (set.isCompleted) Color.White else palette.ink
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (set.isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .border(
                                    width = 2.dp,
                                    color = palette.inkMuted.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(5.dp)
                                )
                        )
                    }
                }
            }

            if (set.isCompleted) {
                CompletedSetSummary(
                    set = set,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassDragValue(
                        value = set.weight,
                        suggestedValue = set.suggestedWeight,
                        label = if (set.isBodyweight) "load" else "lbs",
                        placeholder = if (set.isBodyweight) "BW" else "0",
                        isBodyweight = set.isBodyweight,
                        step = 5,
                        palette = palette,
                        onStep = { delta -> lightHaptic(); onWeightStep(delta) },
                        onActivate = onWeightInput,
                        onLongPress = {
                            if (!set.isBodyweight && set.weight.isNotBlank()) onShowPlates()
                        }
                    )

                    GlassDragValue(
                        value = set.reps,
                        suggestedValue = set.suggestedReps,
                        label = "reps",
                        placeholder = "0",
                        step = 1,
                        palette = palette,
                        onStep = { delta -> lightHaptic(); onRepsStep(delta) },
                        onActivate = onRepsInput
                    )

                    set.restSeconds?.let { restSeconds ->
                        Text(
                            text = "Rest ${formatRestDuration(restSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accentStrong,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = swipeIntent != null,
            enter = fadeIn(tween(80)),
            exit = fadeOut(tween(80)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val isDelete = swipeIntent == RowSwipeIntent.Delete
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isDelete) palette.danger.copy(alpha = 0.94f) else palette.accentStrong.copy(alpha = 0.94f),
                contentColor = Color.White
            ) {
                Text(
                    text = if (isDelete) "Remove set" else "Copy set",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

    }
}

@Composable
private fun CompletedSetSummary(
    set: LoggedSet,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = shape,
        color = palette.glassFillStrong.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, palette.glassStrokeStrong),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = completedLoadLabel(set),
                modifier = Modifier.weight(1f),
                style = LogType.completedSummary,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "x",
                style = LogType.completedSummary,
                fontWeight = FontWeight.Medium,
                color = palette.inkSubtle
            )
            Text(
                text = "${set.reps.ifBlank { "-" }} reps",
                style = LogType.completedSummary,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassDragValue(
    value: String,
    suggestedValue: String?,
    label: String,
    placeholder: String,
    step: Int,
    palette: LogGlassPalette,
    onStep: (Int) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    isBodyweight: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val displayValue = suggestedValue ?: value
    val isShowingSuggestion = suggestedValue != null
    val fieldText = when {
        isBodyweight && displayValue.isBlank() -> "BW"
        else -> displayValue.ifBlank { placeholder }
    }
    val isPlaceholder = displayValue.isBlank() && !isBodyweight
    val density = LocalDensity.current
    val tickPx = with(density) { 26.dp.toPx() }
    var dragCarry by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val numericValue = displayValue.toFloatOrNull() ?: 0f
    val targetFill = when (step) {
        5 -> (0.22f + numericValue / 360f).coerceIn(0.16f, 0.88f)
        else -> (0.18f + numericValue / 28f).coerceIn(0.16f, 0.88f)
    }
    val railFill by animateFloatAsState(
        targetValue = if (isDragging) (targetFill + 0.10f).coerceAtMost(0.96f) else targetFill,
        animationSpec = tween(140),
        label = "drag_rail_fill"
    )
    val railShape = RoundedCornerShape(12.dp)
    val railGradient = Brush.horizontalGradient(
        listOf(
            palette.accent.copy(alpha = if (isDragging) 0.68f else 0.46f),
            palette.auraBlue.copy(alpha = if (isDragging) 0.64f else 0.42f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(railShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        palette.glassFillStrong.copy(alpha = if (isDragging) 0.92f else 0.74f),
                        palette.glassFill.copy(alpha = if (isDragging) 0.76f else 0.54f)
                    )
                ),
                railShape
            )
            .border(
                width = 1.dp,
                color = if (isDragging) palette.accentStrong.copy(alpha = 0.52f) else palette.glassStroke,
                shape = railShape
            )
            .combinedClickable(
                onClick = onActivate,
                onLongClick = { onLongPress?.invoke() }
            )
            .pointerInput(step) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragCarry = 0f
                        isDragging = true
                    },
                    onDragCancel = {
                        dragCarry = 0f
                        isDragging = false
                    },
                    onDragEnd = {
                        dragCarry = 0f
                        isDragging = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragCarry += dragAmount
                        val ticks = (dragCarry / tickPx).roundToInt()
                        if (ticks != 0) {
                            onStep(ticks * step)
                            dragCarry -= ticks * tickPx
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(railFill)
                .background(railGradient)
                .align(Alignment.CenterStart)
        )

        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label.uppercase(Locale.US),
                modifier = Modifier.width(46.dp),
                style = LogType.railLabel,
                color = palette.inkMuted.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = fieldText,
                modifier = Modifier.weight(1f),
                style = LogType.railValue,
                color = when {
                    isPlaceholder -> palette.inkSubtle
                    isBodyweight && displayValue.isBlank() -> palette.accentStrong
                    isShowingSuggestion -> palette.ink.copy(alpha = 0.52f)
                    isDragging -> palette.accentStrong
                    else -> palette.ink
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )

            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.inkSubtle.copy(alpha = if (isDragging) 0.70f else 0.46f))
                )
            }
        }
    }
}

@Composable
fun ExerciseLogCard(
    exerciseName: String,
    muscleGroups: String,
    previousSession: String?,
    isNewPB: Boolean,
    sets: List<LoggedSet>,
    isCollapsed: Boolean,
    onAddSet: () -> Unit,
    onAddSetFrom: (Long) -> Unit,
    onToggleCollapsed: () -> Unit,
    onSwipeFinishAndCollapse: () -> Unit,
    onCompleteSet: (Long) -> Unit,
    onDeleteSet: (LoggedSet) -> Unit,
    onWeightStep: (Long, Int) -> Unit,
    onRepsStep: (Long, Int) -> Unit,
    onWeightInput: (Long) -> Unit,
    onRepsInput: (Long) -> Unit,
    onShowPlates: (LoggedSet) -> Unit,
    onOpenSetActions: (LoggedSet) -> Unit,
    onMuscleGroupChange: (String) -> Unit,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {}
) {
    val heavyHaptic = rememberHeavyHaptic()
    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.78f),
        label = "exercise_swipe_offset"
    )
    val isFullyCompleted = sets.isNotEmpty() && sets.all { it.isCompleted }
    val cardCornerRadius by animateDpAsState(
        targetValue = if (isCollapsed) 14.dp else 18.dp,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.82f),
        label = "exercise_card_corner_radius"
    )
    val completionGlow by animateFloatAsState(
        targetValue = if (isFullyCompleted || isCollapsed) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "exercise_complete_glow"
    )
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val cardSwipeStartLimitPx = with(LocalDensity.current) { 154.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = animatedOffset
                alpha = 1f - (abs(animatedOffset) / 900f).coerceIn(0f, 0.18f)
            }
            .pointerInput(isCollapsed, isFullyCompleted, sets.size, cardSwipeStartLimitPx) {
                if (isCollapsed || sets.isEmpty()) return@pointerInput
                var isCardSwipeActive = false
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        isCardSwipeActive = isFullyCompleted || startOffset.y <= cardSwipeStartLimitPx
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        if (isCardSwipeActive && abs(dragOffset) > 96f) {
                            heavyHaptic()
                            onSwipeFinishAndCollapse()
                        }
                        dragOffset = 0f
                        isCardSwipeActive = false
                    },
                    onDragCancel = {
                        dragOffset = 0f
                        isCardSwipeActive = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isCardSwipeActive) {
                            change.consume()
                            dragOffset = (dragOffset + dragAmount).coerceIn(-140f, 140f)
                        }
                    }
                )
            }
            .drawBehind {
                if (completionGlow > 0f) {
                    drawCircle(
                        color = palette.complete.copy(alpha = 0.18f * completionGlow),
                        radius = size.minDimension * 0.72f,
                        center = Offset(size.width * 0.78f, size.height * 0.20f)
                    )
                }
            }
            .glassPanel(palette, cardShape, strong = isFullyCompleted || isCollapsed, completed = isFullyCompleted || isCollapsed)
            .padding(vertical = if (isCollapsed) 0.dp else 4.dp)
    ) {
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = 140)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 90)))
            },
            label = "exercise_finish_collapse"
        ) { collapsed ->
            if (collapsed) {
                FinishedExerciseRow(
                    exerciseName = exerciseName,
                    isNewPB = isNewPB,
                    sets = sets,
                    onExpand = onToggleCollapsed,
                    palette = palette
                )
                return@AnimatedContent
            }

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                            .padding(vertical = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isFullyCompleted) {
                                        heavyHaptic()
                                        onToggleCollapsed()
                                    }
                                }
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exerciseName,
                                modifier = Modifier.weight(1f),
                                style = LogType.exerciseTitle,
                                color = palette.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isNewPB) {
                                Spacer(Modifier.width(8.dp))
                                PrBadge(palette = palette)
                            }
                        }
                        if (previousSession != null) {
                            Text(
                                text = previousSession,
                                style = LogType.meta,
                                color = palette.accentStrong.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        MuscleGroupSelector(
                            muscleGroups = muscleGroups,
                            onMuscleGroupChange = onMuscleGroupChange,
                            palette = palette,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                
                    Surface(
                        onClick = { onInteraction(); onAddSet() },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.glassFillStrong,
                        border = BorderStroke(1.dp, palette.glassStrokeStrong),
                        contentColor = palette.accentStrong,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.accentStrong)
                        Spacer(Modifier.width(4.dp))
                        Text("Set", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = palette.accentStrong)
                        }
                    }
            }

            GlassExerciseProgress(
                completed = sets.count { it.isCompleted },
                total = sets.size,
                palette = palette
            )

            if (sets.isNotEmpty()) {
                sets.forEach { set ->
                    LogSetRow(
                        set = set,
                        palette = palette,
                        onWeightStep = { onWeightStep(set.id, it) },
                        onRepsStep = { onRepsStep(set.id, it) },
                        onCompleteSet = { onCompleteSet(set.id) },
                        onDeleteSet = onDeleteSet,
                        onAddSetFrom = { onAddSetFrom(set.id) },
                        onWeightInput = { onWeightInput(set.id) },
                        onRepsInput = { onRepsInput(set.id) },
                        onShowPlates = { onShowPlates(set) },
                        onOpenActions = { onOpenSetActions(set) },
                    )
                }
            } else {
                Text(
                    text = "Add a set to start logging this exercise",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSubtle,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
}

@Composable
private fun GlassExerciseProgress(
    completed: Int,
    total: Int,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else completed.toFloat() / total.toFloat(),
        animationSpec = tween(260),
        label = "exercise_progress"
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.38f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(palette.auraBlue, palette.complete)))
            )
        }
        Text(
            text = "$completed/$total",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (total > 0 && completed == total) palette.complete else palette.inkSubtle
        )
    }
}

@Composable
private fun MuscleGroupSelector(
    muscleGroups: String,
    onMuscleGroupChange: (String) -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = primaryMuscleGroup(muscleGroups)

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .height(28.dp)
                .sizeIn(maxWidth = 190.dp),
            shape = RoundedCornerShape(50),
            color = palette.glassFillStrong.copy(alpha = 0.64f),
            border = BorderStroke(1.dp, palette.glassStroke),
            contentColor = palette.inkMuted
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatMuscleGroupLabel(muscleGroups),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Change muscle group",
                    tint = palette.inkSubtle,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.sizeIn(minWidth = 208.dp, maxWidth = 232.dp, maxHeight = 360.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = palette.glassFillStrong.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, palette.glassStrokeStrong)
        ) {
            MuscleGroupChoices.forEach { choice ->
                val isSelected = choice == selected
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (isSelected) {
                                palette.accent.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            }
                        ),
                    text = {
                        Text(
                            text = formatMuscleGroupLabel(choice),
                            style = LogType.menuItem,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) palette.ink else palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (isSelected) palette.complete.copy(alpha = 0.94f) else palette.glassFill.copy(alpha = 0.82f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) palette.complete else palette.glassStroke,
                                    shape = RoundedCornerShape(7.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                    tint = Color(0xFF06100F),
                                    modifier = Modifier.size(14.dp)
                            )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onMuscleGroupChange(choice)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = palette.inkMuted,
                        leadingIconColor = palette.inkMuted
                    )
                )
            }
        }
    }
}

@Composable
private fun FinishedExerciseRow(
    exerciseName: String,
    isNewPB: Boolean,
    sets: List<LoggedSet>,
    onExpand: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier
) {
    val completedCount = sets.count { it.isCompleted }
    val topSet = sets
        .filter { it.weight.toFloatOrNull() != null || it.isBodyweight }
        .maxByOrNull { it.weight.toFloatOrNull() ?: 0f }
    val summary = topSet?.let { "${formatLoggedSetLoad(it)} x ${it.reps.ifBlank { "-" }}" }
        ?: "${sets.size} set${if (sets.size == 1) "" else "s"}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(14.dp), strong = true, completed = true)
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(palette.complete),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exerciseName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isNewPB) {
                    Spacer(Modifier.width(8.dp))
                    PrBadge(palette = palette)
                }
            }
            Text(
                text = "$completedCount/${sets.size} sets complete · Top $summary",
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle
            )
        }
        Text(
            text = "Edit",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.accentStrong
        )
    }
}

private fun formatLoggedSetLoad(set: LoggedSet): String =
    if (set.isBodyweight) {
        if (set.weight.isBlank()) "BW" else "BW + ${set.weight}"
    } else {
        set.weight.ifBlank { "-" }
    }

private fun completedLoadLabel(set: LoggedSet): String =
    if (set.isBodyweight) {
        formatLoggedSetLoad(set)
    } else {
        "${set.weight.ifBlank { "-" }} lbs"
    }

private val MuscleGroupChoices = listOf(
    "CHEST",
    "BACK",
    "SHOULDERS",
    "BICEPS",
    "TRICEPS",
    "QUADS",
    "HAMSTRINGS",
    "GLUTES",
    "CALVES",
    "CORE",
    "FOREARMS",
    "FULL BODY",
    "CARDIO",
    "OTHER",
)

private fun primaryMuscleGroup(muscleGroups: String): String? =
    muscleGroups
        .split("·")
        .firstOrNull()
        ?.trim()
        ?.uppercase()
        ?.takeIf { it in MuscleGroupChoices }

private fun formatMuscleGroupLabel(muscleGroups: String): String {
    val groups = muscleGroups
        .split("·")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (groups.isEmpty()) return "Choose category"

    return groups.joinToString(" + ") { group ->
        group
            .lowercase()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                }
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberInputWithSteppers(
    value: String,
    suggestedValue: String? = null,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    placeholder: String = "",
    isBodyweight: Boolean = false,
    allowDecimal: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onActivate: () -> Unit = {},
) {
    val displayValue = suggestedValue ?: value
    val isShowingSuggestion = suggestedValue != null
    val fieldText = when {
        isBodyweight && displayValue.isBlank() -> "BW"
        else -> displayValue.ifBlank { placeholder }
    }
    val isPlaceholder = displayValue.isBlank() && !isBodyweight

    Row(
        modifier = modifier
            .height(44.dp)
            .border(1.dp, Color(0xFF171A1C).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .background(Color(0xFFF9FBFA), RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberStepperButton(label = "-", onClick = onDecrement)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .combinedClickable(
                    onClick = onActivate,
                    onLongClick = { onLongPress?.invoke() },
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fieldText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = when {
                    isPlaceholder -> Color(0xFF66706E)
                    isBodyweight && displayValue.isBlank() -> Color(0xFF149C8A)
                    isShowingSuggestion -> Color(0xFF171A1C).copy(alpha = 0.48f)
                    else -> Color(0xFF171A1C)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        NumberStepperButton(label = "+", onClick = onIncrement)
    }
}

@Composable
private fun NumberStepperButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .fillMaxHeight(),
        color = Color.Transparent,
        contentColor = Color(0xFF66706E),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF66706E)
            )
        }
    }
}

@Composable
private fun CustomNumberKeyboard(
    value: String,
    kind: NumberInputKind,
    isBodyweight: Boolean,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onToggleBodyweight: () -> Unit,
    onSwitchField: () -> Unit,
    onDone: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier,
) {
    val label = when {
        kind == NumberInputKind.Weight && isBodyweight -> "Added load"
        kind == NumberInputKind.Weight -> "Weight"
        else -> "Reps"
    }
    val placeholder = when {
        kind == NumberInputKind.Weight && isBodyweight -> "BW"
        kind == NumberInputKind.Weight -> "LBS"
        else -> "REPS"
    }
    val displayValue = when {
        kind == NumberInputKind.Weight && isBodyweight && value.isBlank() -> "BW"
        value.isBlank() -> placeholder
        else -> value
    }
    val isPlaceholder = value.isBlank() && !(kind == NumberInputKind.Weight && isBodyweight)
    val switchIcon = if (kind == NumberInputKind.Weight) {
        Icons.AutoMirrored.Outlined.KeyboardArrowRight
    } else {
        Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    }
    val switchDescription = if (kind == NumberInputKind.Weight) "Switch to reps" else "Switch to weight"
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = palette.glassFillStrong,
        border = BorderStroke(1.dp, palette.glassStrokeStrong),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NumberKeyboardDisplayField(
                    label = label,
                    value = displayValue,
                    isPlaceholder = isPlaceholder,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
                NumberKeyboardIconKey(
                    imageVector = switchIcon,
                    contentDescription = switchDescription,
                    onClick = onSwitchField,
                    palette = palette,
                    modifier = Modifier
                        .width(52.dp)
                        .height(54.dp)
                )
            }

            KeyboardKeyRow {
                NumberKeyboardKey("1", onClick = { onKey("1") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("2", onClick = { onKey("2") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("3", onClick = { onKey("3") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey(
                    label = "BW",
                    onClick = onToggleBodyweight,
                    color = if (isBodyweight) palette.accent.copy(alpha = 0.18f) else palette.glassFill,
                    contentColor = if (isBodyweight) palette.accentStrong else palette.ink,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
            KeyboardKeyRow {
                NumberKeyboardKey("4", onClick = { onKey("4") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("5", onClick = { onKey("5") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("6", onClick = { onKey("6") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("DEL", onClick = onBackspace, palette = palette, modifier = Modifier.weight(1f))
            }
            KeyboardKeyRow {
                NumberKeyboardKey("7", onClick = { onKey("7") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("8", onClick = { onKey("8") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("9", onClick = { onKey("9") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("CLR", onClick = onClear, palette = palette, modifier = Modifier.weight(1f))
            }
            KeyboardKeyRow {
                NumberKeyboardKey(
                    label = ".",
                    onClick = { if (kind == NumberInputKind.Weight) onKey(".") },
                    enabled = kind == NumberInputKind.Weight,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
                NumberKeyboardKey("0", onClick = { onKey("0") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey("00", onClick = { onKey("00") }, palette = palette, modifier = Modifier.weight(1f))
                NumberKeyboardKey(
                    "OK",
                    onClick = onDone,
                    color = palette.accentStrong,
                    contentColor = Color.White,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NumberKeyboardDisplayField(
    label: String,
    value: String,
    isPlaceholder: Boolean,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp),
        color = palette.glassFillStrong,
        border = BorderStroke(1.dp, palette.glassStroke),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.inkSubtle,
                maxLines = 1
            )
            Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPlaceholder) palette.inkSubtle else palette.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NumberKeyboardIconKey(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    palette: LogGlassPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = palette.glassFill,
        contentColor = palette.ink,
        border = BorderStroke(1.dp, palette.glassStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = palette.ink,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun KeyboardKeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun NumberKeyboardKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    contentColor: Color? = null,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    enabled: Boolean = true,
) {
    val effectiveColor = color ?: palette.glassFill
    val effectiveContentColor = if (enabled) {
        contentColor ?: palette.ink
    } else {
        palette.inkSubtle.copy(alpha = 0.42f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) effectiveColor else palette.glassFill.copy(alpha = 0.45f),
        contentColor = effectiveContentColor,
        border = BorderStroke(1.dp, palette.glassStroke.copy(alpha = if (enabled) 1f else 0.45f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = effectiveContentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun PrBadge(
    modifier: Modifier = Modifier,
    palette: LogGlassPalette = DefaultLogGlassPalette
) {
    Surface(
        modifier = modifier.height(22.dp),
        shape = RoundedCornerShape(50),
        color = palette.accent.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.26f))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.accentStrong
            )
        }
    }
}

@Composable
private fun PlateCalculatorSheet(
    weightText: String,
    onClose: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier
) {
    val plateLoad = remember(weightText) { calculatePlateLoad(weightText) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), strong = true)
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Plate load",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = "${plateLoad.targetLabel} total · 45 lb bar",
                style = MaterialTheme.typography.labelMedium,
                color = palette.inkSubtle
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = palette.complete.copy(alpha = 0.13f),
            border = BorderStroke(1.dp, palette.glassStrokeStrong)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Per side",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.inkSubtle
                )
                Text(
                    text = plateLoad.perSideLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink,
                    textAlign = TextAlign.End
                )
            }
        }

        if (plateLoad.note != null) {
            Text(
                text = plateLoad.note,
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle
            )
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("Close", style = MaterialTheme.typography.labelMedium, color = palette.accentStrong)
        }
    }
}

@Composable
private fun GlassSetActionSheet(
    target: SetActionTarget,
    palette: LogGlassPalette,
    onComplete: () -> Unit,
    onAddCopiedSet: () -> Unit,
    onEditWeight: () -> Unit,
    onEditReps: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), strong = true)
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Set ${target.set.setNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = "${formatLoggedSetLoad(target.set)} lb · ${target.set.reps.ifBlank { "-" }} reps",
                style = MaterialTheme.typography.labelMedium,
                color = palette.inkSubtle
            )
        }

        GlassSheetActionButton(
            label = if (target.set.isCompleted) "Mark incomplete" else "Mark complete",
            palette = palette,
            onClick = onComplete
        )
        GlassSheetActionButton(
            label = "Add copied set",
            palette = palette,
            onClick = onAddCopiedSet
        )
        GlassSheetActionButton(
            label = "Edit weight",
            palette = palette,
            onClick = onEditWeight
        )
        GlassSheetActionButton(
            label = "Edit reps",
            palette = palette,
            onClick = onEditReps
        )
        GlassSheetActionButton(
            label = "Delete set",
            palette = palette,
            isDanger = true,
            onClick = onDelete
        )
    }
}

@Composable
private fun GlassSheetActionButton(
    label: String,
    palette: LogGlassPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isDanger) palette.danger.copy(alpha = 0.12f) else palette.glassFill,
        border = BorderStroke(1.dp, if (isDanger) palette.danger.copy(alpha = 0.32f) else palette.glassStroke),
        contentColor = if (isDanger) palette.danger else palette.ink
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDanger) palette.danger else palette.ink
            )
        }
    }
}

private data class PlateLoad(
    val targetLabel: String,
    val perSideLabel: String,
    val note: String? = null,
)

private fun calculatePlateLoad(weightText: String): PlateLoad {
    val target = weightText.toDoubleOrNull()
        ?: return PlateLoad(targetLabel = "No weight", perSideLabel = "Enter weight", note = "Add a loaded weight first.")

    val barWeight = 45.0
    if (target <= barWeight) {
        return PlateLoad(
            targetLabel = "${formatPlateNumber(target)} lb",
            perSideLabel = "Empty bar",
        )
    }

    val plateSizes = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    var remaining = (target - barWeight) / 2.0
    val parts = mutableListOf<String>()

    plateSizes.forEach { plate ->
        val count = (remaining / plate).toInt()
        if (count > 0) {
            parts += if (count == 1) formatPlateNumber(plate) else "${formatPlateNumber(plate)} x $count"
            remaining -= plate * count
        }
    }

    val note = if (remaining > 0.05) {
        "Add ${formatPlateNumber(remaining)} lb more per side if your gym has micro plates."
    } else {
        null
    }

    return PlateLoad(
        targetLabel = "${formatPlateNumber(target)} lb",
        perSideLabel = parts.ifEmpty { listOf("Empty bar") }.joinToString(" + "),
        note = note,
    )
}

private fun formatPlateNumber(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

private fun replacementCandidate(previous: String, candidate: String): String {
    if (previous.isBlank() || candidate == previous) return candidate
    val inserted = when {
        candidate.startsWith(previous) -> candidate.removePrefix(previous)
        candidate.endsWith(previous) -> candidate.removeSuffix(previous)
        candidate.contains(previous) -> candidate.replaceFirst(previous, "")
        else -> candidate
    }
    return normalizeReplacementCandidate(inserted)
}

private fun followUpReplacementCandidate(previous: String, candidate: String): String {
    if (previous.isBlank()) return candidate
    val normalized = normalizeReplacementCandidate(candidate)
    if (normalized == candidate) return candidate
    val trimmedNormalized = normalized.trimStart('0')
    return if (
        normalized.startsWith(previous) ||
        trimmedNormalized.startsWith(previous)
    ) {
        normalized
    } else {
        candidate
    }
}

private fun normalizeReplacementCandidate(candidate: String): String {
    // Some Android decimal keyboards report a whole-text replacement as "old.0N"
    // when driven by synthetic input. Treat that as the new integer the user typed.
    return if (
        candidate.startsWith(".0") &&
        candidate.drop(2).isNotBlank() &&
        candidate.drop(2).all { it.isDigit() }
    ) {
        candidate.drop(2)
    } else {
        candidate
    }
}

private fun sanitizeNumberInput(input: String, allowDecimal: Boolean): String {
    val builder = StringBuilder()
    var sawDot = false
    input.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            allowDecimal && char == '.' && !sawDot -> {
                builder.append(char)
                sawDot = true
            }
        }
    }
    return builder.toString()
}

private fun formatRestDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) "%d:%02d".format(minutes, remainingSeconds) else "${remainingSeconds}s"
}

fun formatVolume(v: Int): String = if (v >= 1000) "${v / 1000}.${(v % 1000) / 100}k" else "$v"

@Composable
fun RestTimerPill(
    elapsedSeconds: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeString = "%d:%02d".format(minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "timer_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val heavyHaptic = rememberHeavyHaptic()

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .sizeIn(minWidth = 164.dp)
            .height(42.dp),
        shape = RoundedCornerShape(50),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFF149C8A).copy(alpha = 0.18f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF149C8A).copy(alpha = pulseAlpha))
            )
            Text(
                text = "Rest",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF171A1C)
            )
            Text(
                text = timeString,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF149C8A),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { heavyHaptic(); onDone() },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF66706E))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss rest timer",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF171A1C)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF66706E),
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun AnimatedStatPill(targetValue: Int, label: String, useVolumeFormat: Boolean = false) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (useVolumeFormat) {
            AnimatedVolumeCounter(
                targetValue = targetValue,
                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF171A1C)),
                fontWeight = FontWeight.Bold,
            )
        } else {
            AnimatedCounter(
                targetValue = targetValue,
                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF171A1C)),
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF66706E),
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
fun SearchBarWithDropdown(
    query: String,
    results: List<ExerciseSearchResult>,
    isSearchActive: Boolean,
    onQueryChange: (String) -> Unit,
    onAddExercise: (ExerciseSearchResult) -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    fun addExerciseAndClose(result: ExerciseSearchResult) {
        focusManager.clearFocus()
        onAddExercise(result)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                },
            placeholder = {
                Text(
                    "Search or add exercise...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkSubtle
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.inkSubtle) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { addExerciseAndClose(ExerciseSearchResult(query, "CUSTOM")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = palette.accentStrong)
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = palette.ink,
                fontWeight = FontWeight.SemiBold
            ),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.glassFillStrong,
                unfocusedContainerColor = palette.glassFill,
                focusedBorderColor = palette.glassStrokeStrong,
                unfocusedBorderColor = palette.glassStroke,
                cursorColor = palette.accentStrong
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            singleLine = true
        )
        
        AnimatedVisibility(
            visible = isSearchActive && isFocused && results.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(durationMillis = 90)),
            exit = fadeOut(animationSpec = tween(durationMillis = 60))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.glassFillStrong),
                border = BorderStroke(1.dp, palette.glassStroke),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    results.forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addExerciseAndClose(result) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = palette.accentStrong, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyLogState(
    modifier: Modifier = Modifier,
    palette: LogGlassPalette = DefaultLogGlassPalette
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(18.dp), strong = true)
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.FitnessCenter,
            contentDescription = null,
            tint = palette.accentStrong.copy(alpha = 0.72f),
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No exercises yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Search above to start today's log",
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle
        )
    }
}

@Composable
private fun LogCalendarSheet(
    selectedDate: LocalDate,
    workedDays: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onClose: () -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette
) {
    val today = remember { LocalDate.now() }
    var visibleMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val weekLabels = remember { listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") }
    val firstDayOfMonth = visibleMonth.atDay(1)
    val leadingDays = firstDayOfMonth.dayOfWeek.value % 7
    val gridStart = firstDayOfMonth.minusDays(leadingDays.toLong())
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(sheetShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.glassFillStrong.copy(alpha = 0.98f),
                        palette.pageBottom.copy(alpha = 0.96f),
                        palette.pageTop.copy(alpha = 0.94f)
                    )
                ),
                sheetShape
            )
            .border(1.dp, palette.glassStrokeStrong, sheetShape)
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = palette.ink
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = visibleMonth.format(monthFormatter),
                    style = LogType.dateTitle,
                    color = palette.ink
                )
                TextButton(onClick = { onDateSelected(today) }) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accentStrong
                    )
                }
            }

            IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = palette.ink
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.inkSubtle,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(6) { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(7) { day ->
                        val date = gridStart.plusDays((week * 7 + day).toLong())
                        LogCalendarDay(
                            date = date,
                            selectedDate = selectedDate,
                            today = today,
                            isInVisibleMonth = YearMonth.from(date) == visibleMonth,
                            isWorkedDay = workedDays.contains(date),
                            onDateSelected = onDateSelected,
                            palette = palette,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "Close",
                color = palette.inkSubtle,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LogCalendarDay(
    date: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    isInVisibleMonth: Boolean,
    isWorkedDay: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    palette: LogGlassPalette = DefaultLogGlassPalette,
    modifier: Modifier = Modifier
) {
    val isSelected = date == selectedDate
    val isToday = date == today
    val containerColor = when {
        isSelected -> palette.accentStrong
        isToday -> palette.accent.copy(alpha = 0.13f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> Color.White
        isInVisibleMonth -> palette.ink
        else -> palette.inkSubtle.copy(alpha = 0.45f)
    }
    val border = if (isToday && !isSelected) {
        BorderStroke(1.dp, palette.glassStrokeStrong)
    } else {
        null
    }

    Surface(
        onClick = { onDateSelected(date) },
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = border
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                style = LogType.calendarDay,
                fontWeight = if (isSelected || isToday || isWorkedDay) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center
            )
            if (isWorkedDay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else palette.accentStrong)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LogScreen(
    currentDate: String,
    dateLabel: String,
    cycleSlotLabel: String?,
    splits: List<SplitSlot>,
    selectedSplitId: Long?,
    exercises: List<ExerciseLog>,
    searchQuery: String,
    searchResults: List<ExerciseSearchResult>,
    isSearchActive: Boolean,
    totalSets: Int,
    totalVolumeLbs: Int,
    workedDays: Set<LocalDate> = emptySet(),
    onDateSelected: (String) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectSplit: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddExercise: (ExerciseSearchResult) -> Unit,
    onAddSet: (Int) -> Unit,
    onAddSetFrom: (Long, Long) -> Unit,
    onCompleteSet: (Long) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onWeightChange: (Long, String) -> Unit,
    onWeightStep: (Long, Int) -> Unit,
    onRepsChange: (Long, String) -> Unit,
    onRepsStep: (Long, Int) -> Unit,
    onToggleBodyweight: (Long) -> Unit,
    onSetFocused: (Long) -> Unit,
    onFinishExercise: (Int) -> Unit,
    onMuscleGroupChange: (Int, String) -> Unit,
    restTimerSeconds: Int?,
    onCancelRestTimer: () -> Unit,
    chromeReveal: ChromeRevealState = ChromeRevealState(),
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    palette: LogGlassPalette = paletteChoice.palette(),
    modifier: Modifier = Modifier
) {
    var finishedExerciseIds by remember { mutableStateOf(emptySet<Long>()) }
    val pendingDeletedSetIdsState = remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeletedSetIds by pendingDeletedSetIdsState
    var plateSheetWeight by rememberSaveable { mutableStateOf<String?>(null) }
    var isCalendarVisible by rememberSaveable { mutableStateOf(false) }
    var activeNumberInput by remember { mutableStateOf<NumberInputTarget?>(null) }
    var activeSetActions by remember { mutableStateOf<SetActionTarget?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val plateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val calendarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val setActionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    chromeReveal.hideDistancePx = with(density) { 80.dp.toPx() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val selectedCalendarDate = remember(currentDate) {
        runCatching { LocalDate.parse(currentDate) }.getOrDefault(LocalDate.now())
    }
    val latestOnDeleteSet = rememberUpdatedState(onDeleteSet)

    fun flushPendingDeletes() {
        val ids = pendingDeletedSetIdsState.value
        if (ids.isEmpty()) return
        pendingDeletedSetIdsState.value = emptySet()
        ids.forEach { setId -> latestOnDeleteSet.value(setId) }
    }
    
    LaunchedEffect(currentDate) {
        flushPendingDeletes()
        finishedExerciseIds = emptySet()
        activeNumberInput = null
        activeSetActions = null
        chromeReveal.locked = false
        chromeReveal.snap(1f)
    }

    DisposableEffect(lifecycleOwner, focusManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                flushPendingDeletes()
                activeNumberInput = null
                activeSetActions = null
                plateSheetWeight = null
                isCalendarVisible = false
                focusManager.clearFocus()
                chromeReveal.locked = false
                chromeReveal.snap(1f)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            flushPendingDeletes()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val orderedExercises = exercises
        .mapIndexed { index, exercise -> IndexedExercise(index, exercise) }
    val activeTarget = activeNumberInput
    val activeSet = activeTarget?.let { target ->
        exercises.asSequence()
            .flatMap { it.sets.asSequence() }
            .firstOrNull { it.id == target.setId }
    }
    val isNumberKeyboardVisible = activeTarget != null && activeSet != null
    val keyboardHeightPadding = 306.dp
    // Content padding is kept CONSTANT with respect to chrome visibility. Hiding or
    // showing the top bar / bottom nav must never remeasure or resize the list while
    // the user is mid-scroll — that relayout-per-frame is what made scrolling stutter.
    // Instead the chrome fades over the list (see the AnimatedVisibility blocks below).
    // Room for the collapsible top bar and bottom nav is always reserved; while you
    // scroll through the middle that reserved space just sits off-screen, and the bars
    // fade back in over it when you reach an edge.
    val listTopPadding = 72.dp
    // Only the custom number keyboard changes the bottom inset — and that's a deliberate
    // tap, never a scroll — so animating this one is safe and smooth.
    val targetListBottomPadding = if (isNumberKeyboardVisible) {
        keyboardHeightPadding
    } else {
        navigationBarPadding + 76.dp
    }
    val listBottomPadding by animateDpAsState(
        targetValue = targetListBottomPadding,
        animationSpec = tween(durationMillis = 180),
        label = "log_list_bottom_padding"
    )

    // The bars hide proportionally to scroll as deltas arrive (see chromeNestedScroll).
    // This effect only reacts to the start/stop of a gesture — a single boolean that
    // flips at most twice per swipe — so it never runs per-frame. On settle it snaps the
    // bars to fully shown or hidden so you never rest on a half-faded bar.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling && !chromeReveal.locked) {
                    val target = when {
                        listState.isAtTop() || listState.isAtBottom() -> 1f
                        chromeReveal.reveal >= 0.5f -> 1f
                        else -> 0f
                    }
                    chromeReveal.animateTo(target)
                }
            }
    }

    val chromeNestedScroll = remember(chromeReveal) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                chromeReveal.applyScrollDelta(available.y)
                return Offset.Zero
            }
        }
    }

    fun revealChrome() {
        scope.launch { chromeReveal.animateTo(1f) }
    }

    fun closePlateSheet() {
        plateSheetWeight = null
        chromeReveal.locked = false
        revealChrome()
    }

    fun closeCalendarSheet() {
        isCalendarVisible = false
        chromeReveal.locked = false
        revealChrome()
    }

    fun openCalendarSheet() {
        activeNumberInput = null
        plateSheetWeight = null
        focusManager.clearFocus()
        chromeReveal.locked = true
        chromeReveal.snap(1f)
        isCalendarVisible = true
    }

    fun selectCalendarDate(date: LocalDate) {
        flushPendingDeletes()
        activeNumberInput = null
        plateSheetWeight = null
        activeSetActions = null
        focusManager.clearFocus()
        isCalendarVisible = false
        onDateSelected(date.toString())
        chromeReveal.locked = false
        revealChrome()
    }

    fun goToPreviousDay() {
        flushPendingDeletes()
        onPreviousDay()
    }

    fun goToNextDay() {
        flushPendingDeletes()
        onNextDay()
    }

    fun commitNumberInput(target: NumberInputTarget, rawValue: String) {
        val allowDecimal = target.kind == NumberInputKind.Weight
        val maxLength = if (allowDecimal) 6 else 3
        val nextValue = sanitizeNumberInput(rawValue, allowDecimal).take(maxLength)
        activeNumberInput = target.copy(value = nextValue, replaceOnNextKey = false)
        when (target.kind) {
            NumberInputKind.Weight -> onWeightChange(target.setId, nextValue)
            NumberInputKind.Reps -> onRepsChange(target.setId, nextValue)
        }
    }

    fun activateNumberInput(setId: Long, kind: NumberInputKind) {
        val set = exercises.asSequence()
            .flatMap { it.sets.asSequence() }
            .firstOrNull { it.id == setId } ?: return
        activeSetActions = null
        focusManager.clearFocus()
        chromeReveal.locked = true
        scope.launch { chromeReveal.animateTo(0f) }
        onSetFocused(setId)
        activeNumberInput = NumberInputTarget(
            setId = setId,
            kind = kind,
            value = if (kind == NumberInputKind.Weight) set.weight else set.reps,
            replaceOnNextKey = true,
        )
    }

    fun appendNumberInput(rawKey: String) {
        val target = activeNumberInput ?: return
        val allowDecimal = target.kind == NumberInputKind.Weight
        if (rawKey == "." && (!allowDecimal || target.value.contains("."))) return
        val candidate = when {
            target.replaceOnNextKey -> rawKey
            target.value == "0" && rawKey != "." -> rawKey
            else -> target.value + rawKey
        }
        commitNumberInput(target, candidate)
    }

    fun switchNumberInputField() {
        val target = activeNumberInput ?: return
        val set = exercises.asSequence()
            .flatMap { it.sets.asSequence() }
            .firstOrNull { it.id == target.setId } ?: return
        val nextKind = if (target.kind == NumberInputKind.Weight) NumberInputKind.Reps else NumberInputKind.Weight
        activeNumberInput = target.copy(
            kind = nextKind,
            value = if (nextKind == NumberInputKind.Weight) set.weight else set.reps,
            replaceOnNextKey = true,
        )
        onSetFocused(target.setId)
    }

    fun requestDeleteSet(set: LoggedSet) {
        if (set.id in pendingDeletedSetIds) return
        activeSetActions = null
        activeNumberInput = null
        pendingDeletedSetIds = pendingDeletedSetIds + set.id
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Set ${set.setNumber} removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            val isStillPending = set.id in pendingDeletedSetIdsState.value
            if (!isStillPending) return@launch
            pendingDeletedSetIdsState.value = pendingDeletedSetIdsState.value - set.id
            if (result != SnackbarResult.ActionPerformed) {
                onDeleteSet(set.id)
            }
        }
    }

    MaterialTheme(typography = LogMaterialTypography) {
        Box(
            modifier = modifier
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(chromeNestedScroll)
                    .padding(top = statusBarPadding)
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = listTopPadding,
                    end = 16.dp,
                    bottom = listBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                GlassPaletteSwitch(
                    selected = paletteChoice,
                    onSelect = onPaletteChoiceChange,
                    palette = palette
                )
            }
            item {
                SearchBarWithDropdown(
                    query = searchQuery,
                    results = searchResults,
                    isSearchActive = isSearchActive,
                    onQueryChange = onSearchQueryChange,
                    onAddExercise = onAddExercise,
                    onFocusChanged = { focused ->
                        if (!focused) revealChrome()
                    },
                    palette = palette
                )
            }
            if (splits.isNotEmpty()) {
                item {
                    SplitSelectorStrip(
                        splits = splits,
                        selectedId = selectedSplitId,
                        onSelect = onSelectSplit,
                        palette = palette
                    )
                }
            }
            items(
                items = orderedExercises,
                key = { it.exercise.exerciseId },
                contentType = { "exercise" }
            ) { item ->
                val i = item.originalIndex
                val exercise = item.exercise
                val isCollapsed = exercise.exerciseId in finishedExerciseIds
                val visibleSets = exercise.sets.filterNot { it.id in pendingDeletedSetIds }
                ExerciseLogCard(
                    exerciseName = exercise.exerciseName,
                    muscleGroups = exercise.muscleGroups,
                    previousSession = exercise.previousSession,
                    isNewPB = exercise.isNewPB,
                    sets = visibleSets,
                    isCollapsed = isCollapsed,
                    onAddSet = { onAddSet(i) },
                    onAddSetFrom = { setId -> onAddSetFrom(exercise.exerciseId, setId) },
                    onToggleCollapsed = {
                        if (!isCollapsed) onFinishExercise(i)
                        finishedExerciseIds = if (isCollapsed) {
                            finishedExerciseIds - exercise.exerciseId
                        } else {
                            finishedExerciseIds + exercise.exerciseId
                        }
                    },
                    onSwipeFinishAndCollapse = {
                        if (visibleSets.isNotEmpty()) {
                            visibleSets
                                .filterNot { it.isCompleted }
                                .forEach { set -> onCompleteSet(set.id) }
                            if (!isCollapsed) {
                                onFinishExercise(i)
                                finishedExerciseIds = finishedExerciseIds + exercise.exerciseId
                            }
                        }
                    },
                    onCompleteSet = { setId -> onCompleteSet(setId) },
                    onDeleteSet = { set -> requestDeleteSet(set) },
                    onWeightStep = { setId, delta -> onWeightStep(setId, delta) },
                    onRepsStep = { setId, delta -> onRepsStep(setId, delta) },
                    onWeightInput = { setId -> activateNumberInput(setId, NumberInputKind.Weight) },
                    onRepsInput = { setId -> activateNumberInput(setId, NumberInputKind.Reps) },
                    onMuscleGroupChange = { muscleGroup -> onMuscleGroupChange(i, muscleGroup) },
                    onShowPlates = { set ->
                        chromeReveal.locked = true
                        scope.launch { chromeReveal.animateTo(0f) }
                        plateSheetWeight = set.weight
                    },
                    onOpenSetActions = { set ->
                        activeNumberInput = null
                        activeSetActions = SetActionTarget(exercise.exerciseId, set)
                    },
                    palette = palette,
                    modifier = Modifier.animateItem(),
                    onInteraction = {}
                )
            }
            if (exercises.isEmpty() && !isSearchActive) {
                item { EmptyLogState(palette = palette) }
            }
        }

            // Always composed; the scroll-linked fade is a draw-phase graphicsLayer read
            // (no recomposition while scrolling). At reveal 0 it slides fully off the top
            // edge, so the hidden bar can't intercept taps over the list.
            LogTopBar(
                dateLabel = dateLabel,
                cycleSlotLabel = cycleSlotLabel,
                onDateClick = ::openCalendarSheet,
                onPreviousDay = ::goToPreviousDay,
                onNextDay = ::goToNextDay,
                palette = palette,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val r = chromeReveal.reveal
                        alpha = r
                        translationY = -(1f - r) * size.height
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(16.dp)
                    .fillMaxHeight()
                    .backgroundChromeGestureStrip(
                        enabled = !isNumberKeyboardVisible,
                        onRevealChrome = ::revealChrome
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(16.dp)
                    .fillMaxHeight()
                    .backgroundChromeGestureStrip(
                        enabled = !isNumberKeyboardVisible,
                        onRevealChrome = ::revealChrome
                    )
            )

            if (activeTarget != null && activeSet != null) {
                CustomNumberKeyboard(
                    value = activeTarget.value,
                    kind = activeTarget.kind,
                    isBodyweight = activeSet.isBodyweight,
                    onKey = ::appendNumberInput,
                    onBackspace = {
                        commitNumberInput(activeTarget.copy(replaceOnNextKey = false), activeTarget.value.dropLast(1))
                    },
                    onClear = {
                        commitNumberInput(activeTarget.copy(replaceOnNextKey = false), "")
                    },
                    onToggleBodyweight = {
                        val willBeBodyweight = !activeSet.isBodyweight
                        onToggleBodyweight(activeTarget.setId)
                        activeNumberInput = activeTarget.copy(
                            value = when {
                                activeTarget.kind != NumberInputKind.Weight -> activeTarget.value
                                willBeBodyweight -> activeSet.weight
                                else -> activeSet.weight
                            },
                            replaceOnNextKey = true,
                        )
                    },
                    onSwitchField = ::switchNumberInputField,
                    onDone = {
                        activeNumberInput = null
                        chromeReveal.locked = false
                        revealChrome()
                    },
                    palette = palette,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navigationBarPadding)
                )
            }

            if (isCalendarVisible) {
                ModalBottomSheet(
                    onDismissRequest = ::closeCalendarSheet,
                    sheetState = calendarSheetState,
                    containerColor = Color.Transparent,
                    scrimColor = palette.scrim
                ) {
                    LogCalendarSheet(
                        selectedDate = selectedCalendarDate,
                        workedDays = workedDays,
                        onDateSelected = ::selectCalendarDate,
                        onClose = ::closeCalendarSheet,
                        palette = palette
                    )
                }
            }

            plateSheetWeight?.let { weight ->
                ModalBottomSheet(
                    onDismissRequest = ::closePlateSheet,
                    sheetState = plateSheetState,
                    containerColor = Color.Transparent,
                    scrimColor = palette.scrim
                ) {
                    PlateCalculatorSheet(
                        weightText = weight,
                        onClose = ::closePlateSheet,
                        palette = palette
                    )
                }
            }

            activeSetActions?.let { target ->
                ModalBottomSheet(
                    onDismissRequest = { activeSetActions = null },
                    sheetState = setActionSheetState,
                    containerColor = Color.Transparent,
                    scrimColor = palette.scrim
                ) {
                    GlassSetActionSheet(
                        target = target,
                        palette = palette,
                        onComplete = {
                            activeSetActions = null
                            onCompleteSet(target.set.id)
                        },
                        onAddCopiedSet = {
                            activeSetActions = null
                            onAddSetFrom(target.exerciseId, target.set.id)
                        },
                        onEditWeight = {
                            activateNumberInput(target.set.id, NumberInputKind.Weight)
                        },
                        onEditReps = {
                            activateNumberInput(target.set.id, NumberInputKind.Reps)
                        },
                        onDelete = {
                            requestDeleteSet(target.set)
                        }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = navigationBarPadding + 14.dp)
            )
        }
    }
}

@Immutable
private data class IndexedExercise(
    val originalIndex: Int,
    val exercise: ExerciseLog,
)

@Preview(showBackground = true)
@Composable
fun LogScreenPreview() {
    MaterialTheme {
        LogScreen(
            currentDate = LocalDate.now().toString(),
            dateLabel = "Today",
            cycleSlotLabel = "Push Day · Slot 1",
            splits = listOf(
                SplitSlot(1, "Push", "Suggested", true),
                SplitSlot(2, "Pull", "Saved", false)
            ),
            selectedSplitId = 1,
            exercises = listOf(
                ExerciseLog(
                    exerciseId = 1L,
                    exerciseName = "Bench Press",
                    muscleGroups = "CHEST · TRICEPS",
                    previousSession = "Last: 185 × 10",
                    isNewPB = true,
                    sets = listOf(
                        LoggedSet(101L, 1, "185", "10", false, true),
                        LoggedSet(102L, 2, "185", "8", false, true, restSeconds = 94),
                        LoggedSet(103L, 3, "185", "5", false, false)
                    )
                ),
                ExerciseLog(
                    exerciseId = 2L,
                    exerciseName = "Lateral Raise",
                    muscleGroups = "SHOULDERS",
                    previousSession = null,
                    sets = emptyList()
                )
            ),
            searchQuery = "",
            searchResults = emptyList(),
            isSearchActive = false,
            totalSets = 3,
            totalVolumeLbs = 3330,
            onDateSelected = {},
            onPreviousDay = {},
            onNextDay = {},
            onSelectSplit = { _ -> },
            onSearchQueryChange = {},
            onAddExercise = {},
            onAddSet = {},
            onAddSetFrom = { _, _ -> },
            onCompleteSet = {},
            onDeleteSet = {},
            onWeightChange = { _, _ -> },
            onWeightStep = { _, _ -> },
            onRepsChange = { _, _ -> },
            onRepsStep = { _, _ -> },
            onToggleBodyweight = {},
            onSetFocused = {},
            onFinishExercise = {},
            onMuscleGroupChange = { _, _ -> },
            restTimerSeconds = null,
            onCancelRestTimer = {}
        )
    }
}
