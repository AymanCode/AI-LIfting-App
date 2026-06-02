package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.Size
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
import com.ayman.ecolift.ui.theme.bounceClick
import com.ayman.ecolift.ui.theme.rememberHeavyHaptic
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SplitSlot(
    val id: Long,
    val displayName: String,
    val slotLabel: String,
    val isExpected: Boolean
)

data class LoggedSet(
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
    val exerciseIndex: Int,
    val setIndex: Int,
    val kind: NumberInputKind,
    val value: String,
)

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
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        modifier = modifier.statusBarsPadding(),
        windowInsets = WindowInsets(0),
        title = {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDateClick)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF171A1C)
                )
                if (cycleSlotLabel != null) {
                    Text(
                        text = cycleSlotLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF149C8A)
                    )
                } else {
                    Text(
                        text = "No cycle slot",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF66706E)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onPreviousDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous Day",
                    tint = Color(0xFF171A1C)
                )
            }
        },
        actions = {
            IconButton(onClick = onNextDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next Day",
                    tint = Color(0xFF171A1C)
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color(0xFFF4F6F5)
        )
    )
}

@Composable
fun SplitChip(
    slot: SplitSlot,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF171A1C) else Color.White
    val textColor = if (isSelected) Color.White else Color(0xFF171A1C)
    val borderColor = if (isSelected) Color.Transparent else Color(0xFF171A1C).copy(alpha = 0.15f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
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
                        .background(Color(0xFF149C8A))
                )
            }
            Text(
                text = slot.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
            if (slot.isExpected) {
                Text(
                    text = slot.slotLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White.copy(alpha = 0.75f) else Color(0xFF149C8A)
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
                color = Color(0xFF171A1C)
            )
            Text(
                text = "Choose a saved plan to load its exercises",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF66706E)
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
                    onClick = { onSelect(if (isSelected) null else slot.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogSetRow(
    set: LoggedSet,
    onWeightChange: (String) -> Unit,
    onWeightStep: (Int) -> Unit,
    onRepsChange: (String) -> Unit,
    onRepsStep: (Int) -> Unit,
    onCompleteSet: () -> Unit,
    onDeleteSet: () -> Unit,
    onWeightInput: () -> Unit,
    onRepsInput: () -> Unit,
    modifier: Modifier = Modifier,
    onShowPlates: () -> Unit = {},
) {
    val lightHaptic = rememberLightHaptic()
    val heavyHaptic = rememberHeavyHaptic()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    fun requestInputVisibility() {
        scope.launch {
            delay(220)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
            modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .clip(RoundedCornerShape(10.dp))
            .background(if (set.isCompleted) Color(0xFFEAF7F4).copy(alpha = 0.68f) else Color.Transparent)
            .drawBehind {
                if (set.isCompleted) {
                    drawRect(
                        color = Color(0xFF149C8A),
                        topLeft = Offset.Zero,
                        size = Size(2.dp.toPx(), size.height)
                    )
                }
            }
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (set.isCompleted) Color(0xFF149C8A) else Color(0xFFEAF0EE))
                    .bounceClick {
                        heavyHaptic()
                        onCompleteSet()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (set.isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Text(
                        text = set.setNumber.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF171A1C)
                    )
                }
            }

            NumberInputWithSteppers(
                value = set.weight,
                suggestedValue = set.suggestedWeight,
                onValueChange = onWeightChange,
                onDecrement = { lightHaptic(); onWeightStep(-5) },
                onIncrement = { lightHaptic(); onWeightStep(5) },
                modifier = Modifier
                    .weight(0.58f)
                    .alpha(if (set.isCompleted) 0.92f else 1f),
                placeholder = if (set.isBodyweight) "BW" else "LBS",
                isBodyweight = set.isBodyweight,
                allowDecimal = true,
                onActivate = {
                    requestInputVisibility()
                    onWeightInput()
                },
                onLongPress = {
                    if (!set.isBodyweight && set.weight.isNotBlank()) {
                        onShowPlates()
                    }
                }
            )

            NumberInputWithSteppers(
                value = set.reps,
                suggestedValue = set.suggestedReps,
                onValueChange = onRepsChange,
                onDecrement = { lightHaptic(); onRepsStep(-1) },
                onIncrement = { lightHaptic(); onRepsStep(1) },
                modifier = Modifier
                    .weight(0.42f)
                    .alpha(if (set.isCompleted) 0.92f else 1f),
                placeholder = "REPS",
                allowDecimal = false,
                onActivate = {
                    requestInputVisibility()
                    onRepsInput()
                }
            )

            IconButton(onClick = onDeleteSet, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Delete set",
                    tint = Color(0xFF66706E).copy(alpha = 0.45f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        set.restSeconds?.let { restSeconds ->
            Text(
                text = "Rest ${formatRestDuration(restSeconds)} before set",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF149C8A).copy(alpha = 0.78f),
                modifier = Modifier.padding(start = 40.dp, top = 3.dp)
            )
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
    onToggleCollapsed: () -> Unit,
    onCompleteSet: (Int) -> Unit,
    onDeleteSet: (Int) -> Unit,
    onWeightChange: (Int, String) -> Unit,
    onWeightStep: (Int, Int) -> Unit,
    onRepsChange: (Int, String) -> Unit,
    onRepsStep: (Int, Int) -> Unit,
    onWeightInput: (Int) -> Unit,
    onRepsInput: (Int) -> Unit,
    onShowPlates: (Int) -> Unit,
    onMuscleGroupChange: (String) -> Unit,
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
    val completedCardColor = Color(0xFFF5FAF8)
    val isFullyCompleted = sets.isNotEmpty() && sets.all { it.isCompleted }
    val cardContainerColor by animateColorAsState(
        targetValue = if (isCollapsed || isFullyCompleted) completedCardColor else Color.White,
        animationSpec = tween(durationMillis = 220),
        label = "exercise_card_container_color"
    )
    val cardCornerRadius by animateDpAsState(
        targetValue = if (isCollapsed) 10.dp else 12.dp,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.82f),
        label = "exercise_card_corner_radius"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = animatedOffset
                alpha = 1f - (abs(animatedOffset) / 900f).coerceIn(0f, 0.18f)
            }
            .pointerInput(isCollapsed) {
                if (isCollapsed) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragOffset) > 96f) {
                            heavyHaptic()
                            onToggleCollapsed()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount).coerceIn(-140f, 140f)
                    }
                )
            },
        shape = RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = BorderStroke(1.dp, Color(0xFFDDE6E3)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCollapsed) 0.dp else 1.dp)
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
                    onExpand = onToggleCollapsed
                )
                return@AnimatedContent
            }

            Column(
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
                                    heavyHaptic()
                                    onToggleCollapsed()
                                }
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exerciseName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF171A1C),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isNewPB) {
                                Spacer(Modifier.width(8.dp))
                                PrBadge()
                            }
                        }
                        if (previousSession != null) {
                            Text(
                                text = previousSession,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF149C8A).copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        MuscleGroupSelector(
                            muscleGroups = muscleGroups,
                            onMuscleGroupChange = onMuscleGroupChange,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                
                    OutlinedButton(
                        onClick = { onInteraction(); onAddSet() },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF149C8A)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF149C8A)),
                        modifier = Modifier.height(34.dp).bounceClick(onClick = { onInteraction(); onAddSet() }),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Set", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Column Headers
            if (sets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SET", modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF66706E))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (sets.any { it.isBodyweight }) "LOAD" else "LBS", modifier = Modifier.weight(0.58f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF66706E))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("REPS", modifier = Modifier.weight(0.42f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF66706E))
                    Spacer(modifier = Modifier.width(4.dp))
                    Spacer(modifier = Modifier.width(32.dp))
                }
                
                // Set Rows
                sets.forEachIndexed { index, set ->
                    LogSetRow(
                        set = set,
                        onWeightChange = { onWeightChange(index, it) },
                        onWeightStep = { onWeightStep(index, it) },
                        onRepsChange = { onRepsChange(index, it) },
                        onRepsStep = { onRepsStep(index, it) },
                        onCompleteSet = { onCompleteSet(index) },
                        onDeleteSet = { onDeleteSet(index) },
                        onWeightInput = { onWeightInput(index) },
                        onRepsInput = { onRepsInput(index) },
                        onShowPlates = { onShowPlates(index) },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun MuscleGroupSelector(
    muscleGroups: String,
    onMuscleGroupChange: (String) -> Unit,
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
            color = Color(0xFFEAF0EE),
            border = BorderStroke(1.dp, Color(0xFFDDE6E3)),
            contentColor = Color(0xFF3F4947)
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
                    color = Color(0xFF3F4947),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Change muscle group",
                    tint = Color(0xFF66706E),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MuscleGroupChoices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatMuscleGroupLabel(choice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF171A1C)
                        )
                    },
                    leadingIcon = if (choice == selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF149C8A),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onMuscleGroupChange(choice)
                    }
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
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                drawRect(
                    color = Color(0xFF149C8A),
                    topLeft = Offset.Zero,
                    size = Size(2.dp.toPx(), size.height)
                )
            }
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF149C8A)),
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
                    color = Color(0xFF171A1C),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isNewPB) {
                    Spacer(Modifier.width(8.dp))
                    PrBadge()
                }
            }
            Text(
                text = "$completedCount/${sets.size} sets complete · Top $summary",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF66706E)
            )
        }
        Text(
            text = "Edit",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF149C8A)
        )
    }
}

private fun formatLoggedSetLoad(set: LoggedSet): String =
    if (set.isBodyweight) {
        if (set.weight.isBlank()) "BW" else "BW + ${set.weight}"
    } else {
        set.weight.ifBlank { "-" }
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
    val accent = Color(0xFF149C8A)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFF171A1C).copy(alpha = 0.08f)),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
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
                    modifier = Modifier.weight(1f)
                )
                NumberKeyboardIconKey(
                    imageVector = switchIcon,
                    contentDescription = switchDescription,
                    onClick = onSwitchField,
                    modifier = Modifier
                        .width(52.dp)
                        .height(54.dp)
                )
            }

            KeyboardKeyRow {
                NumberKeyboardKey("1", onClick = { onKey("1") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("2", onClick = { onKey("2") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("3", onClick = { onKey("3") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey(
                    label = "BW",
                    onClick = onToggleBodyweight,
                    color = if (isBodyweight) accent.copy(alpha = 0.16f) else Color(0xFFF1F4F3),
                    contentColor = if (isBodyweight) accent else Color(0xFF171A1C),
                    modifier = Modifier.weight(1f)
                )
            }
            KeyboardKeyRow {
                NumberKeyboardKey("4", onClick = { onKey("4") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("5", onClick = { onKey("5") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("6", onClick = { onKey("6") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("DEL", onClick = onBackspace, modifier = Modifier.weight(1f))
            }
            KeyboardKeyRow {
                NumberKeyboardKey("7", onClick = { onKey("7") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("8", onClick = { onKey("8") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("9", onClick = { onKey("9") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("CLR", onClick = onClear, modifier = Modifier.weight(1f))
            }
            KeyboardKeyRow {
                NumberKeyboardKey(
                    label = ".",
                    onClick = { if (kind == NumberInputKind.Weight) onKey(".") },
                    enabled = kind == NumberInputKind.Weight,
                    modifier = Modifier.weight(1f)
                )
                NumberKeyboardKey("0", onClick = { onKey("0") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("00", onClick = { onKey("00") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("OK", onClick = onDone, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NumberKeyboardDisplayField(
    label: String,
    value: String,
    isPlaceholder: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFF171A1C).copy(alpha = 0.12f)),
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
                color = Color(0xFF66706E),
                maxLines = 1
            )
            Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPlaceholder) Color(0xFF66706E) else Color(0xFF171A1C),
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
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF1F4F3),
        contentColor = Color(0xFF171A1C),
        border = BorderStroke(1.dp, Color(0xFF171A1C).copy(alpha = 0.07f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color(0xFF171A1C),
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
    color: Color = Color(0xFFF1F4F3),
    contentColor: Color = Color(0xFF171A1C),
    enabled: Boolean = true,
) {
    val effectiveContentColor = if (enabled) contentColor else Color(0xFF66706E).copy(alpha = 0.38f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) color else Color(0xFFF1F4F3).copy(alpha = 0.45f),
        contentColor = effectiveContentColor,
        border = BorderStroke(1.dp, Color(0xFF171A1C).copy(alpha = if (enabled) 0.07f else 0.03f)),
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
private fun PrBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(22.dp),
        shape = RoundedCornerShape(50),
        color = Color(0xFF149C8A).copy(alpha = 0.13f),
        border = BorderStroke(1.dp, Color(0xFF149C8A).copy(alpha = 0.24f))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF149C8A)
            )
        }
    }
}

@Composable
private fun PlateCalculatorSheet(
    weightText: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plateLoad = remember(weightText) { calculatePlateLoad(weightText) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Plate load",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A1C)
            )
            Text(
                text = "${plateLoad.targetLabel} total · 45 lb bar",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF66706E)
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFEAF7F4),
            border = BorderStroke(1.dp, Color(0xFF149C8A).copy(alpha = 0.18f))
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
                    color = Color(0xFF66706E)
                )
                Text(
                    text = plateLoad.perSideLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF171A1C),
                    textAlign = TextAlign.End
                )
            }
        }

        if (plateLoad.note != null) {
            Text(
                text = plateLoad.note,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF66706E)
            )
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("Close", style = MaterialTheme.typography.labelMedium, color = Color(0xFF149C8A))
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
                    color = Color(0xFF66706E)
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF66706E)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { addExerciseAndClose(ExerciseSearchResult(query, "CUSTOM")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF149C8A))
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFF171A1C),
                fontWeight = FontWeight.SemiBold
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF149C8A),
                unfocusedBorderColor = Color(0xFFDDE6E3),
                cursorColor = Color(0xFF149C8A)
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
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDDE6E3)),
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
                                    color = Color(0xFF171A1C),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF149C8A), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyLogState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.FitnessCenter,
            contentDescription = null,
            tint = Color(0xFF149C8A).copy(alpha = 0.72f),
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No exercises yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF171A1C)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Search above to start today's log",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF66706E)
        )
    }
}

@Composable
private fun LogCalendarSheet(
    selectedDate: LocalDate,
    workedDays: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onClose: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var visibleMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val weekLabels = remember { listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") }
    val firstDayOfMonth = visibleMonth.atDay(1)
    val leadingDays = firstDayOfMonth.dayOfWeek.value % 7
    val gridStart = firstDayOfMonth.minusDays(leadingDays.toLong())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
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
                    tint = Color(0xFF171A1C)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = visibleMonth.format(monthFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF171A1C)
                )
                TextButton(onClick = { onDateSelected(today) }) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF149C8A)
                    )
                }
            }

            IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = Color(0xFF171A1C)
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
                    color = Color(0xFF66706E),
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
                color = Color(0xFF66706E),
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
    modifier: Modifier = Modifier
) {
    val isSelected = date == selectedDate
    val isToday = date == today
    val containerColor = when {
        isSelected -> Color(0xFF149C8A)
        isToday -> Color(0xFF149C8A).copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> Color.White
        isInVisibleMonth -> Color(0xFF171A1C)
        else -> Color(0xFF66706E).copy(alpha = 0.45f)
    }
    val border = if (isToday && !isSelected) {
        BorderStroke(1.dp, Color(0xFF149C8A).copy(alpha = 0.55f))
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
                style = MaterialTheme.typography.bodyMedium,
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
                        .background(if (isSelected) Color.White else Color(0xFF149C8A))
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
    onCompleteSet: (Int, Int) -> Unit,
    onDeleteSet: (Int, Int) -> Unit,
    onWeightChange: (Int, Int, String) -> Unit,
    onWeightStep: (Int, Int, Int) -> Unit,
    onRepsChange: (Int, Int, String) -> Unit,
    onRepsStep: (Int, Int, Int) -> Unit,
    onToggleBodyweight: (Int, Int) -> Unit,
    onSetFocused: (Int, Int) -> Unit,
    onFinishExercise: (Int) -> Unit,
    onMuscleGroupChange: (Int, String) -> Unit,
    restTimerSeconds: Int?,
    onCancelRestTimer: () -> Unit,
    chromeReveal: ChromeRevealState = ChromeRevealState(),
    modifier: Modifier = Modifier
) {
    var finishedExerciseIds by remember { mutableStateOf(emptySet<Long>()) }
    var plateSheetWeight by rememberSaveable { mutableStateOf<String?>(null) }
    var isCalendarVisible by rememberSaveable { mutableStateOf(false) }
    var activeNumberInput by remember { mutableStateOf<NumberInputTarget?>(null) }
    val plateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val calendarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
    
    LaunchedEffect(currentDate) {
        finishedExerciseIds = emptySet()
        activeNumberInput = null
        chromeReveal.locked = false
        chromeReveal.snap(1f)
    }

    DisposableEffect(lifecycleOwner, focusManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                activeNumberInput = null
                plateSheetWeight = null
                isCalendarVisible = false
                focusManager.clearFocus()
                chromeReveal.locked = false
                chromeReveal.snap(1f)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val orderedExercises = exercises
        .mapIndexed { index, exercise -> IndexedExercise(index, exercise) }
    val activeTarget = activeNumberInput
    val activeSet = activeTarget?.let { target ->
        exercises.getOrNull(target.exerciseIndex)?.sets?.getOrNull(target.setIndex)
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
        activeNumberInput = null
        plateSheetWeight = null
        focusManager.clearFocus()
        isCalendarVisible = false
        onDateSelected(date.toString())
        chromeReveal.locked = false
        revealChrome()
    }

    fun commitNumberInput(target: NumberInputTarget, rawValue: String) {
        val allowDecimal = target.kind == NumberInputKind.Weight
        val maxLength = if (allowDecimal) 6 else 3
        val nextValue = sanitizeNumberInput(rawValue, allowDecimal).take(maxLength)
        activeNumberInput = target.copy(value = nextValue)
        when (target.kind) {
            NumberInputKind.Weight -> onWeightChange(target.exerciseIndex, target.setIndex, nextValue)
            NumberInputKind.Reps -> onRepsChange(target.exerciseIndex, target.setIndex, nextValue)
        }
    }

    fun activateNumberInput(exerciseIndex: Int, setIndex: Int, kind: NumberInputKind) {
        val set = exercises.getOrNull(exerciseIndex)?.sets?.getOrNull(setIndex) ?: return
        focusManager.clearFocus()
        chromeReveal.locked = true
        scope.launch { chromeReveal.animateTo(0f) }
        onSetFocused(exerciseIndex, setIndex)
        activeNumberInput = NumberInputTarget(
            exerciseIndex = exerciseIndex,
            setIndex = setIndex,
            kind = kind,
            value = if (kind == NumberInputKind.Weight) set.weight else set.reps,
        )
    }

    fun appendNumberInput(rawKey: String) {
        val target = activeNumberInput ?: return
        val allowDecimal = target.kind == NumberInputKind.Weight
        if (rawKey == "." && (!allowDecimal || target.value.contains("."))) return
        val candidate = when {
            target.value == "0" && rawKey != "." -> rawKey
            else -> target.value + rawKey
        }
        commitNumberInput(target, candidate)
    }

    fun switchNumberInputField() {
        val target = activeNumberInput ?: return
        val set = exercises.getOrNull(target.exerciseIndex)?.sets?.getOrNull(target.setIndex) ?: return
        val nextKind = if (target.kind == NumberInputKind.Weight) NumberInputKind.Reps else NumberInputKind.Weight
        activeNumberInput = target.copy(
            kind = nextKind,
            value = if (nextKind == NumberInputKind.Weight) set.weight else set.reps
        )
        onSetFocused(target.exerciseIndex, target.setIndex)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F5))
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
                SearchBarWithDropdown(
                    query = searchQuery,
                    results = searchResults,
                    isSearchActive = isSearchActive,
                    onQueryChange = onSearchQueryChange,
                    onAddExercise = onAddExercise,
                    onFocusChanged = { focused ->
                        if (!focused) revealChrome()
                    }
                )
            }
            if (splits.isNotEmpty()) {
                item {
                    SplitSelectorStrip(
                        splits = splits,
                        selectedId = selectedSplitId,
                        onSelect = onSelectSplit
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
                ExerciseLogCard(
                    exerciseName = exercise.exerciseName,
                    muscleGroups = exercise.muscleGroups,
                    previousSession = exercise.previousSession,
                    isNewPB = exercise.isNewPB,
                    sets = exercise.sets,
                    isCollapsed = isCollapsed,
                    onAddSet = { onAddSet(i) },
                    onToggleCollapsed = {
                        if (!isCollapsed) onFinishExercise(i)
                        finishedExerciseIds = if (isCollapsed) {
                            finishedExerciseIds - exercise.exerciseId
                        } else {
                            finishedExerciseIds + exercise.exerciseId
                        }
                    },
                    onCompleteSet = { setIndex -> onCompleteSet(i, setIndex) },
                    onDeleteSet = { setIndex -> onDeleteSet(i, setIndex) },
                    onWeightChange = { setIndex, value -> onWeightChange(i, setIndex, value) },
                    onWeightStep = { setIndex, delta -> onWeightStep(i, setIndex, delta) },
                    onRepsChange = { setIndex, value -> onRepsChange(i, setIndex, value) },
                    onRepsStep = { setIndex, delta -> onRepsStep(i, setIndex, delta) },
                    onWeightInput = { setIndex -> activateNumberInput(i, setIndex, NumberInputKind.Weight) },
                    onRepsInput = { setIndex -> activateNumberInput(i, setIndex, NumberInputKind.Reps) },
                    onMuscleGroupChange = { muscleGroup -> onMuscleGroupChange(i, muscleGroup) },
                    onShowPlates = { setIndex ->
                        chromeReveal.locked = true
                        scope.launch { chromeReveal.animateTo(0f) }
                        plateSheetWeight = exercise.sets[setIndex].weight
                    },
                    modifier = Modifier.animateItem(),
                    onInteraction = {}
                )
            }
            if (exercises.isEmpty() && !isSearchActive) {
                item { EmptyLogState() }
            }
        }

            // Always composed; the scroll-linked fade is a draw-phase graphicsLayer read
            // (no recomposition while scrolling). At reveal 0 it slides fully off the top
            // edge, so the hidden bar can't intercept taps over the list.
            LogTopBar(
                dateLabel = dateLabel,
                cycleSlotLabel = cycleSlotLabel,
                onDateClick = ::openCalendarSheet,
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay,
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
                        commitNumberInput(activeTarget, activeTarget.value.dropLast(1))
                    },
                    onClear = {
                        commitNumberInput(activeTarget, "")
                    },
                    onToggleBodyweight = {
                        val willBeBodyweight = !activeSet.isBodyweight
                        onToggleBodyweight(activeTarget.exerciseIndex, activeTarget.setIndex)
                        activeNumberInput = activeTarget.copy(
                            value = when {
                                activeTarget.kind != NumberInputKind.Weight -> activeTarget.value
                                willBeBodyweight -> activeSet.weight
                                else -> activeSet.weight
                            }
                        )
                    },
                    onSwitchField = ::switchNumberInputField,
                    onDone = {
                        activeNumberInput = null
                        chromeReveal.locked = false
                        revealChrome()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navigationBarPadding)
                )
            }

            if (isCalendarVisible) {
                ModalBottomSheet(
                    onDismissRequest = ::closeCalendarSheet,
                    sheetState = calendarSheetState,
                    containerColor = Color.White
                ) {
                    LogCalendarSheet(
                        selectedDate = selectedCalendarDate,
                        workedDays = workedDays,
                        onDateSelected = ::selectCalendarDate,
                        onClose = ::closeCalendarSheet
                    )
                }
            }

            plateSheetWeight?.let { weight ->
                ModalBottomSheet(
                    onDismissRequest = ::closePlateSheet,
                    sheetState = plateSheetState,
                    containerColor = Color.White
                ) {
                    PlateCalculatorSheet(
                        weightText = weight,
                        onClose = ::closePlateSheet
                    )
                }
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
                        LoggedSet(1, "185", "10", false, true),
                        LoggedSet(2, "185", "8", false, true, restSeconds = 94),
                        LoggedSet(3, "185", "5", false, false)
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
            onCompleteSet = { _, _ -> },
            onDeleteSet = { _, _ -> },
            onWeightChange = { _, _, _ -> },
            onWeightStep = { _, _, _ -> },
            onRepsChange = { _, _, _ -> },
            onRepsStep = { _, _, _ -> },
            onToggleBodyweight = { _, _ -> },
            onSetFocused = { _, _ -> },
            onFinishExercise = {},
            onMuscleGroupChange = { _, _ -> },
            restTimerSeconds = null,
            onCancelRestTimer = {}
        )
    }
}
