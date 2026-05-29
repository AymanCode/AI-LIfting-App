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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.ayman.ecolift.ui.theme.AnimatedCounter
import com.ayman.ecolift.ui.theme.AnimatedVolumeCounter
import com.ayman.ecolift.ui.theme.bounceClick
import com.ayman.ecolift.ui.theme.rememberHeavyHaptic
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import java.util.Locale
import kotlinx.coroutines.delay
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTopBar(
    dateLabel: String,
    cycleSlotLabel: String?,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        modifier = modifier.statusBarsPadding(),
        windowInsets = WindowInsets(0),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1C1E)
                )
                if (cycleSlotLabel != null) {
                    Text(
                        text = cycleSlotLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4DB6AC)
                    )
                } else {
                    Text(
                        text = "No cycle slot",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onPreviousDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous Day",
                    tint = Color(0xFF1C1C1E)
                )
            }
        },
        actions = {
            IconButton(onClick = onNextDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next Day",
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
fun SplitChip(
    slot: SplitSlot,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isSelected) Color.White else Color(0xFF1C1C1E)
    val borderColor = if (isSelected) Color.Transparent else Color(0xFF1C1C1E).copy(alpha = 0.15f)
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
                        .background(Color(0xFF4DB6AC))
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
                    color = if (isSelected) Color.White.copy(alpha = 0.75f) else Color(0xFF4DB6AC)
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(
                text = "Today's workout",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E)
            )
            Text(
                text = "Choose a saved plan to load its exercises",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93)
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (set.isCompleted) Color(0xFF4DB6AC).copy(alpha = 0.07f) else Color.Transparent)
            .drawBehind {
                if (set.isCompleted) {
                    drawRect(
                        color = Color(0xFF4DB6AC),
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height)
                    )
                }
            }
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (set.isCompleted) Color(0xFF4DB6AC) else Color(0xFFF2F0EB))
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
                        color = Color(0xFF1C1C1E)
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
                placeholder = "LBS",
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
                    tint = Color(0xFF8E8E93).copy(alpha = 0.45f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        set.restSeconds?.let { restSeconds ->
            Text(
                text = "Rest ${formatRestDuration(restSeconds)} before set",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4DB6AC).copy(alpha = 0.78f),
                modifier = Modifier.padding(start = 40.dp, top = 3.dp)
            )
        }
    }
}

@Composable
fun ExerciseLogCard(
    exerciseName: String,
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
    val completedCardColor = Color(0xFFF3FAF9)
    val isFullyCompleted = sets.isNotEmpty() && sets.all { it.isCompleted }
    val cardContainerColor by animateColorAsState(
        targetValue = if (isCollapsed || isFullyCompleted) completedCardColor else Color.White,
        animationSpec = tween(durationMillis = 220),
        label = "exercise_card_container_color"
    )
    val cardCornerRadius by animateDpAsState(
        targetValue = if (isCollapsed) 12.dp else 16.dp,
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
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCollapsed) 1.dp else 2.dp)
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
                modifier = Modifier.padding(vertical = 16.dp)
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
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                heavyHaptic()
                                onToggleCollapsed()
                            }
                            .padding(end = 12.dp)
                            .padding(vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = exerciseName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1C1E),
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
                                color = Color(0xFF4DB6AC).copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                
                    OutlinedButton(
                        onClick = { onInteraction(); onAddSet() },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0xFF4DB6AC)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4DB6AC)),
                        modifier = Modifier.height(32.dp).bounceClick(onClick = { onInteraction(); onAddSet() }),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("+ SET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Column Headers
            if (sets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SET", modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LBS", modifier = Modifier.weight(0.58f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("REPS", modifier = Modifier.weight(0.42f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
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
private fun FinishedExerciseRow(
    exerciseName: String,
    isNewPB: Boolean,
    sets: List<LoggedSet>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completedCount = sets.count { it.isCompleted }
    val topSet = sets
        .filter { it.weight.toFloatOrNull() != null }
        .maxByOrNull { it.weight.toFloatOrNull() ?: 0f }
    val summary = topSet?.let { "${it.weight} x ${it.reps.ifBlank { "-" }}" }
        ?: "${sets.size} set${if (sets.size == 1) "" else "s"}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRect(
                    color = Color(0xFF4DB6AC),
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
            .clickable(onClick = onExpand)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF4DB6AC)),
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
                    color = Color(0xFF1C1C1E),
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
                color = Color(0xFF8E8E93)
            )
        }
        Text(
            text = "Edit",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF4DB6AC)
        )
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
    allowDecimal: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onActivate: () -> Unit = {},
) {
    val displayValue = suggestedValue ?: value
    val isShowingSuggestion = suggestedValue != null

    Row(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, Color(0xFF1C1C1E).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp)),
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
                text = displayValue.ifBlank { placeholder },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = when {
                    displayValue.isBlank() -> Color(0xFF8E8E93)
                    isShowingSuggestion -> Color(0xFF1C1C1E).copy(alpha = 0.48f)
                    else -> Color(0xFF1C1C1E)
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
            .fillMaxHeight(),
        color = Color.Transparent,
        contentColor = Color(0xFF8E8E93),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E8E93)
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
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (kind == NumberInputKind.Weight) "Weight" else "Reps"
    val accent = Color(0xFF4DB6AC)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFF1C1C1E).copy(alpha = 0.08f)),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E8E93)
                    )
                    Text(
                        text = value.ifBlank { "-" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E)
                    )
                }
                NumberKeyboardKey(
                    label = "DONE",
                    onClick = onDone,
                    color = accent,
                    contentColor = Color.White,
                    modifier = Modifier.width(84.dp).height(42.dp)
                )
            }

            KeyboardKeyRow {
                NumberKeyboardKey("1", onClick = { onKey("1") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("2", onClick = { onKey("2") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey("3", onClick = { onKey("3") }, modifier = Modifier.weight(1f))
                NumberKeyboardKey(
                    label = "BW",
                    onClick = onToggleBodyweight,
                    color = if (isBodyweight) accent.copy(alpha = 0.16f) else Color(0xFFF7F7F7),
                    contentColor = if (isBodyweight) accent else Color(0xFF1C1C1E),
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
    color: Color = Color(0xFFF7F7F7),
    contentColor: Color = Color(0xFF1C1C1E),
    enabled: Boolean = true,
) {
    val effectiveContentColor = if (enabled) contentColor else Color(0xFF8E8E93).copy(alpha = 0.38f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) color else Color(0xFFF7F7F7).copy(alpha = 0.45f),
        contentColor = effectiveContentColor,
        border = BorderStroke(1.dp, Color(0xFF1C1C1E).copy(alpha = if (enabled) 0.07f else 0.03f)),
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
        color = Color(0xFF4DB6AC).copy(alpha = 0.13f),
        border = BorderStroke(1.dp, Color(0xFF4DB6AC).copy(alpha = 0.24f))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2A9D8F)
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
                color = Color(0xFF1C1C1E)
            )
            Text(
                text = "${plateLoad.targetLabel} total · 45 lb bar",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8E8E93)
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF3FAF9),
            border = BorderStroke(1.dp, Color(0xFF4DB6AC).copy(alpha = 0.18f))
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
                    color = Color(0xFF8E8E93)
                )
                Text(
                    text = plateLoad.perSideLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1C1C1E),
                    textAlign = TextAlign.End
                )
            }
        }

        if (plateLoad.note != null) {
            Text(
                text = plateLoad.note,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93)
            )
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("Close", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4DB6AC))
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
        border = BorderStroke(1.dp, Color(0xFF4DB6AC).copy(alpha = 0.18f)),
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
                    .background(Color(0xFF4DB6AC).copy(alpha = pulseAlpha))
            )
            Text(
                text = "Rest",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E)
            )
            Text(
                text = timeString,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF4DB6AC),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { heavyHaptic(); onDone() },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF8E8E93))
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
            color = Color(0xFF1C1C1E)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8E8E93),
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
                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF1C1C1E)),
                fontWeight = FontWeight.Bold,
            )
        } else {
            AnimatedCounter(
                targetValue = targetValue,
                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF1C1C1E)),
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8E8E93),
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search or add exercise...", color = Color(0xFF8E8E93)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF8E8E93)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onAddExercise(ExerciseSearchResult(query, "CUSTOM")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF4DB6AC))
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF4DB6AC),
                unfocusedBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            singleLine = true
        )
        
        AnimatedVisibility(
            visible = isSearchActive && results.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(durationMillis = 90)),
            exit = fadeOut(animationSpec = tween(durationMillis = 60))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    results.forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddExercise(result) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = result.name, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1C1C1E))
                                if (result.muscleGroups.isNotBlank()) {
                                    Text(text = result.muscleGroups, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                                }
                            }
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF4DB6AC), modifier = Modifier.size(20.dp))
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
            tint = Color(0xFF4DB6AC).copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No exercises yet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1C1C1E)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Search above to start logging",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E8E93)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
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
    restTimerSeconds: Int?,
    onCancelRestTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var finishedExerciseIds by remember { mutableStateOf(emptySet<Long>()) }
    var plateSheetWeight by rememberSaveable { mutableStateOf<String?>(null) }
    var activeNumberInput by remember { mutableStateOf<NumberInputTarget?>(null) }
    val plateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(dateLabel) {
        finishedExerciseIds = emptySet()
        activeNumberInput = null
    }

    val orderedExercises = exercises
        .mapIndexed { index, exercise -> IndexedExercise(index, exercise) }
    val activeTarget = activeNumberInput
    val activeSet = activeTarget?.let { target ->
        exercises.getOrNull(target.exerciseIndex)?.sets?.getOrNull(target.setIndex)
    }
    val isNumberKeyboardVisible = activeTarget != null && activeSet != null
    val keyboardHeightPadding = 306.dp

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

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LogTopBar(
                dateLabel = dateLabel,
                cycleSlotLabel = cycleSlotLabel,
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay
            )
        },
        containerColor = Color(0xFFF2F0EB)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = when {
                        isNumberKeyboardVisible -> keyboardHeightPadding
                        restTimerSeconds != null -> 76.dp
                        else -> 12.dp
                    }
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                SearchBarWithDropdown(
                    query = searchQuery,
                    results = searchResults,
                    isSearchActive = isSearchActive,
                    onQueryChange = onSearchQueryChange,
                    onAddExercise = onAddExercise
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
                key = { it.exercise.exerciseId }
            ) { item ->
                val i = item.originalIndex
                val exercise = item.exercise
                val isCollapsed = exercise.exerciseId in finishedExerciseIds
                ExerciseLogCard(
                    exerciseName = exercise.exerciseName,
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
                    onShowPlates = { setIndex -> plateSheetWeight = exercise.sets[setIndex].weight },
                    modifier = Modifier.animateItem(),
                    onInteraction = {}
                )
            }
            if (exercises.isEmpty() && !isSearchActive) {
                item { EmptyLogState() }
            }
        }

            restTimerSeconds?.let { seconds ->
                RestTimerPill(
                    elapsedSeconds = seconds,
                    onDone = onCancelRestTimer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .padding(bottom = if (isNumberKeyboardVisible) keyboardHeightPadding else 12.dp)
                )
            }

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
                                willBeBodyweight -> ""
                                else -> "0"
                            }
                        )
                    },
                    onDone = { activeNumberInput = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            plateSheetWeight?.let { weight ->
                ModalBottomSheet(
                    onDismissRequest = { plateSheetWeight = null },
                    sheetState = plateSheetState,
                    containerColor = Color.White
                ) {
                    PlateCalculatorSheet(
                        weightText = weight,
                        onClose = { plateSheetWeight = null }
                    )
                }
            }
    }
}
}

private data class IndexedExercise(
    val originalIndex: Int,
    val exercise: ExerciseLog,
)

@Preview(showBackground = true)
@Composable
fun LogScreenPreview() {
    MaterialTheme {
        LogScreen(
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
            restTimerSeconds = 47,
            onCancelRestTimer = {}
        )
    }
}
