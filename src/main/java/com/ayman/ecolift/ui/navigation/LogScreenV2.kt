package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.AnimatedCounter
import com.ayman.ecolift.ui.theme.AnimatedVolumeCounter
import com.ayman.ecolift.ui.theme.bounceClick
import com.ayman.ecolift.ui.theme.rememberHeavyHaptic
import com.ayman.ecolift.ui.theme.rememberLightHaptic
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
    val isCompleted: Boolean
)

data class ExerciseLog(
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroups: String,
    val previousSession: String?,
    val sets: List<LoggedSet>
)

data class ExerciseSearchResult(val name: String, val muscleGroups: String)

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

@Composable
fun LogSetRow(
    set: LoggedSet,
    onWeightChange: (String) -> Unit,
    onWeightStep: (Int) -> Unit,
    onRepsChange: (String) -> Unit,
    onRepsStep: (Int) -> Unit,
    onToggleBodyweight: () -> Unit,
    onCompleteSet: () -> Unit,
    onDeleteSet: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {}
) {
    val lightHaptic = rememberLightHaptic()
    val heavyHaptic = rememberHeavyHaptic()

    Row(
        modifier = modifier
            .fillMaxWidth()
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
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Set number — shows checkmark icon when completed
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (set.isCompleted) Color(0xFF4DB6AC) else Color(0xFFF2F0EB)),
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

        // LBS input — with haptic steppers
        NumberInputWithSteppers(
            value = set.weight,
            onValueChange = onWeightChange,
            onDecrement = { lightHaptic(); onWeightStep(-5) },
            onIncrement = { lightHaptic(); onWeightStep(5) },
            modifier = Modifier
                .weight(0.35f)
                .alpha(if (set.isCompleted) 0.45f else 1f),
            isLocked = set.isCompleted,
            placeholder = "LBS",
            onFocus = onFocus
        )

        // REPS input — with haptic steppers
        NumberInputWithSteppers(
            value = set.reps,
            onValueChange = onRepsChange,
            onDecrement = { lightHaptic(); onRepsStep(-1) },
            onIncrement = { lightHaptic(); onRepsStep(1) },
            modifier = Modifier
                .weight(0.30f)
                .alpha(if (set.isCompleted) 0.45f else 1f),
            isLocked = set.isCompleted,
            placeholder = "REPS",
            onFocus = onFocus
        )

        // BW toggle
        SuggestionChip(
            onClick = onToggleBodyweight,
            label = { Text("BW", style = MaterialTheme.typography.labelSmall) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = if (set.isBodyweight) Color(0xFF4DB6AC).copy(alpha = 0.15f) else Color.Transparent,
                labelColor = if (set.isBodyweight) Color(0xFF4DB6AC) else Color(0xFF8E8E93)
            ),
            border = SuggestionChipDefaults.suggestionChipBorder(
                enabled = true,
                borderColor = if (set.isBodyweight) Color(0xFF4DB6AC).copy(alpha = 0.4f) else Color(0xFF8E8E93).copy(alpha = 0.25f)
            ),
            modifier = Modifier.height(30.dp)
        )

        // Complete: 48dp touch target — heavy haptic on completion
        IconButton(
            onClick = {
                heavyHaptic()
                onCompleteSet()
            },
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = if (set.isCompleted) "Uncheck set" else "Complete set",
                tint = if (set.isCompleted) Color(0xFF4DB6AC) else Color(0xFF8E8E93).copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp)
            )
        }

        // Delete — 44dp touch target
        IconButton(onClick = onDeleteSet, modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Delete set",
                tint = Color(0xFF8E8E93).copy(alpha = 0.45f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun ExerciseLogCard(
    exerciseName: String,
    muscleGroups: String,
    previousSession: String?,
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
    onToggleBodyweight: (Int) -> Unit,
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
    val cardContainerColor by animateColorAsState(
        targetValue = if (isCollapsed) completedCardColor else Color.White,
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
            modifier = Modifier.animateContentSize(
                animationSpec = spring(stiffness = 520f, dampingRatio = 0.86f)
            ),
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = 140)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 90)))
            },
            label = "exercise_finish_collapse"
        ) { collapsed ->
            if (collapsed) {
                FinishedExerciseRow(
                    exerciseName = exerciseName,
                    sets = sets,
                    onExpand = onToggleCollapsed
                )
                return@AnimatedContent
            }

            Column(
                modifier = Modifier
                    .animateContentSize(animationSpec = spring(stiffness = 650f, dampingRatio = 0.82f))
                    .padding(vertical = 16.dp)
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
                            .padding(vertical = 2.dp)
                    ) {
                    Text(
                        text = exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4DB6AC).copy(alpha = 0.7f))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = muscleGroups.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1C1C1E).copy(alpha = 0.45f),
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Medium
                        )
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
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                    TextButton(
                        onClick = {
                            heavyHaptic()
                            onToggleCollapsed()
                        },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Done", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Column Headers
            if (sets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SET", modifier = Modifier.weight(0.07f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LBS", modifier = Modifier.weight(0.35f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REPS", modifier = Modifier.weight(0.30f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BW", modifier = Modifier.weight(0.12f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("✓", modifier = Modifier.weight(0.10f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                    Spacer(modifier = Modifier.width(6.dp))
                    Spacer(modifier = Modifier.weight(0.06f))
                }
                
                // Set Rows
                sets.forEachIndexed { index, set ->
                    LogSetRow(
                        set = set,
                        onWeightChange = { onWeightChange(index, it) },
                        onWeightStep = { onWeightStep(index, it) },
                        onRepsChange = { onRepsChange(index, it) },
                        onRepsStep = { onRepsStep(index, it) },
                        onToggleBodyweight = { onToggleBodyweight(index) },
                        onCompleteSet = { onCompleteSet(index) },
                        onDeleteSet = { onDeleteSet(index) },
                        onFocus = onInteraction
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
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E)
            )
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

@Composable
fun NumberInputWithSteppers(
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    placeholder: String = "",
    onFocus: () -> Unit = {}
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    
    // Sync internal state with external value changes (e.g. from steppers or parent state)
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, Color(0xFF1C1C1E).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxHeight()
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .bounceClick { if (!isLocked) onDecrement() },
            contentAlignment = Alignment.Center
        ) {
            Text("-", color = Color(0xFF8E8E93), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        
        BasicTextField(
            value = fieldValue,
            onValueChange = { newVal ->
                if (isLocked) return@BasicTextField
                
                val filtered = newVal.text.filter { it.isDigit() || it == '.' }
                val clean = if (filtered.length > 1 && filtered.startsWith("0") && filtered[1] != '.') {
                    filtered.trimStart('0').ifEmpty { "0" }
                } else {
                    filtered
                }
                
                // Maintain cursor position or selection
                fieldValue = newVal.copy(text = clean)
                onValueChange(clean)
            },
            modifier = Modifier.weight(0.5f).onFocusChanged { focusState ->
                if (focusState.isFocused && !isLocked) {
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                    onFocus()
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                color = Color(0xFF1C1C1E)
            ),
            singleLine = true,
            enabled = !isLocked,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    if (fieldValue.text.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                    }
                    innerTextField()
                }
            }
        )
        
        Box(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxHeight()
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .bounceClick { if (!isLocked) onIncrement() },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color(0xFF8E8E93), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
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
        
        AnimatedVisibility(visible = isSearchActive && results.isNotEmpty()) {
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
                                Text(text = result.muscleGroups, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
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
    restTimerSeconds: Int?,
    onCancelRestTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var finishedExerciseIds by remember { mutableStateOf(emptySet<Long>()) }
    var activeExerciseId by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(dateLabel) {
        finishedExerciseIds = emptySet()
        activeExerciseId = null
    }

    val orderedExercises = exercises
        .mapIndexed { index, exercise -> IndexedExercise(index, exercise) }
        .let { indexed ->
            val active = indexed.filter { it.exercise.exerciseId == activeExerciseId }
            val unfinished = indexed.filter { it.exercise.exerciseId !in finishedExerciseIds && it.exercise.exerciseId != activeExerciseId }
            val finished = indexed.filter { it.exercise.exerciseId in finishedExerciseIds && it.exercise.exerciseId != activeExerciseId }
            active + unfinished + finished
        }

    Scaffold(
        modifier = modifier,
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                    muscleGroups = exercise.muscleGroups,
                    previousSession = exercise.previousSession,
                    sets = exercise.sets,
                    isCollapsed = isCollapsed,
                    onAddSet = { onAddSet(i); activeExerciseId = exercise.exerciseId },
                    onToggleCollapsed = {
                        if (!isCollapsed && activeExerciseId == exercise.exerciseId) {
                            activeExerciseId = null
                        }
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
                    onToggleBodyweight = { setIndex -> onToggleBodyweight(i, setIndex) },
                    modifier = Modifier.animateItem(),
                    onInteraction = { activeExerciseId = exercise.exerciseId }
                )
            }
            if (exercises.isEmpty() && !isSearchActive) {
                item { EmptyLogState() }
            }
        }
        
        AnimatedVisibility(
            visible = restTimerSeconds != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            if (restTimerSeconds != null) {
                RestTimerPill(
                    elapsedSeconds = restTimerSeconds,
                    onDone = onCancelRestTimer
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
                    sets = listOf(
                        LoggedSet(1, "185", "10", false, true),
                        LoggedSet(2, "185", "8", false, true),
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
            restTimerSeconds = 47,
            onCancelRestTimer = {}
        )
    }
}
