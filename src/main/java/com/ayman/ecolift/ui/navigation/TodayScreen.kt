package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.theme.AccentTeal
import com.ayman.ecolift.ui.theme.AccentTeal12
import com.ayman.ecolift.ui.theme.AccentTeal15
import com.ayman.ecolift.ui.theme.AccentTeal20
import com.ayman.ecolift.ui.theme.AccentTeal35
import com.ayman.ecolift.ui.theme.BackgroundElevated
import com.ayman.ecolift.ui.theme.BackgroundPrimary
import com.ayman.ecolift.ui.theme.BackgroundSubtle
import com.ayman.ecolift.ui.theme.BackgroundSurface
import com.ayman.ecolift.ui.theme.BorderDefault
import com.ayman.ecolift.ui.theme.BorderSubtle
import com.ayman.ecolift.ui.theme.ChevronColor
import com.ayman.ecolift.ui.theme.MuscleGroupTag
import com.ayman.ecolift.ui.theme.SearchIcon
import com.ayman.ecolift.ui.theme.SearchPlaceholder
import com.ayman.ecolift.ui.theme.TextInactive
import com.ayman.ecolift.ui.theme.TextMuted
import com.ayman.ecolift.ui.theme.TextPrimary
import com.ayman.ecolift.ui.theme.TextSecondary
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.ui.viewmodel.CycleSlotUi
import com.ayman.ecolift.ui.viewmodel.ExerciseChipUi
import com.ayman.ecolift.ui.viewmodel.LogExerciseUi
import com.ayman.ecolift.ui.viewmodel.LogSetUi
import com.ayman.ecolift.ui.viewmodel.LogViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(viewModel: LogViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

// Exercise card
    LaunchedEffect(uiState.exercises.size) {
        if (uiState.exercises.isNotEmpty()) {
            val cycleOffset = if (uiState.cycleEnabled && uiState.cycleSlot == null) 1 else 0
            listState.animateScrollToItem(cycleOffset + uiState.exercises.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            DateHeader(
                label = uiState.currentDateLabel,
                slotLabel = uiState.cycleSlot?.shortLabel,
                onPrevious = viewModel::goToPreviousDay,
                onNext = viewModel::goToNextDay,
            )

            uiState.alternativeForDate?.let { originalDate ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Dynamic Swap: session from $originalDate moved here.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            AddExerciseBar(
                input = uiState.exerciseInput,
                suggestions = uiState.inlineSuggestions,
                quickAdd = uiState.quickAddExercises,
                onInputChange = viewModel::updateExerciseInput,
                onSuggestionClick = viewModel::useSuggestion,
                onAdd = viewModel::addExerciseFromInput,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.cycleEnabled && uiState.cycleSlot == null) {
                    item {
                        CyclePickerCard(
                            options = uiState.cycleOptions,
                            onSelect = viewModel::assignCycleSlot,
                        )
                    }
                }

                items(uiState.exercises, key = { it.exerciseId }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        onAddSet = { viewModel.addSet(exercise.exerciseId) },
                        onWeightStep = viewModel::adjustWeight,
                        onWeightChange = viewModel::updateWeight,
                        onRepsStep = viewModel::adjustReps,
                        onRepsChange = viewModel::updateReps,
                        onToggleBodyweight = viewModel::toggleBodyweight,
                        onToggleCompleted = viewModel::toggleCompleted,
                        onDeleteSet = { setId -> viewModel.deleteSet(setId) },
                        onUpdateName = viewModel::updateExerciseName,
                    )
                }

                item { Spacer(Modifier.height(100.dp)) }
            }
        }

        // Floating rest timer
        uiState.restStopwatchSeconds?.let { seconds ->
            RestTimerOverlay(
                seconds = seconds,
                onCancel = viewModel::cancelRestTimer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
    }
}

@Composable
private fun RestTimerOverlay(
    seconds: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mins = seconds / 60
    val secs = (seconds % 60).toString().padStart(2, '0')
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = BackgroundSurface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Resting",
                style = TextStyle(fontSize = 11.sp, color = TextMuted, letterSpacing = 0.04.sp)
            )
            Text(
                text = "$mins:$secs",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = "Done",
                modifier = Modifier.clickable(onClick = onCancel),
                style = TextStyle(fontSize = 12.sp, color = AccentTeal, fontWeight = FontWeight.Bold)
            )
        }
    }
}

// Date header

@Composable
private fun DateHeader(
    label: String,
    slotLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BackgroundPrimary,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous day",
                    tint = ChevronColor
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = slotLabel ?: "NO CYCLE SLOT",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = if (slotLabel != null) AccentTeal else TextInactive,
                        fontWeight = FontWeight.W800,
                        letterSpacing = 0.08.sp
                    )
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next day",
                    tint = ChevronColor
                )
            }
        }
    }
}

// Pinned search and add bar

@Composable
private fun AddExerciseBar(
    input: String,
    suggestions: List<String>,
    quickAdd: List<ExerciseChipUi>,
    onInputChange: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = { 
                Text(
                    "Search or add exercise...", 
                    color = SearchPlaceholder,
                    fontSize = 13.sp
                ) 
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SearchIcon) },
            trailingIcon = {
                if (input.isNotBlank()) {
                    TextButton(onClick = onAdd) { Text("Add", color = AccentTeal) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, AccentTeal20, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BackgroundSurface,
                unfocusedContainerColor = BackgroundSurface,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = AccentTeal
            )
        )

        if (suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column {
                    suggestions.forEach { suggestion ->
                        Text(
                            text = suggestion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionClick(suggestion) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

// Cycle slot picker

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CyclePickerCard(
    options: List<CycleSlotUi>,
    onSelect: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "What are you hitting today?",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Select a slot to pre-load your last session",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    val isExpected = option.isExpected
                    val isSelected = option.isSelected
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(option.type.toLong()) },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = option.label.uppercase(),
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.W800,
                                        color = if (isSelected) Color.White else if (isExpected) AccentTeal else TextInactive
                                    )
                                )
                                Text(
                                    text = if (isSelected) "SELECTED" else if (isExpected) "Expected ✦" else "Slot",
                                    style = TextStyle(
                                        fontSize = 9.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.7f) else if (isExpected) AccentTeal.copy(alpha = 0.5f) else BorderSubtle
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (isSelected) AccentTeal else if (isExpected) AccentTeal12 else BackgroundElevated,
                            selectedContainerColor = AccentTeal,
                            labelColor = if (isSelected) Color.White else if (isExpected) AccentTeal else TextInactive,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AccentTeal else if (isExpected) AccentTeal else BorderSubtle,
                            selectedBorderColor = AccentTeal,
                            borderWidth = 1.5.dp,
                            selectedBorderWidth = 1.5.dp
                        )
                    )
                }
            }
        }
    }
}

// Exercise card

@Composable
private fun ExerciseCard(
    exercise: LogExerciseUi,
    onAddSet: () -> Unit,
    onWeightStep: (Long, Int) -> Unit,
    onWeightChange: (Long, String) -> Unit,
    onRepsStep: (Long, Int) -> Unit,
    onRepsChange: (Long, String) -> Unit,
    onToggleBodyweight: (Long) -> Unit,
    onToggleCompleted: (Long) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateName: (Long, String) -> Unit,
) {
    val allCompleted = exercise.sets.isNotEmpty() && exercise.sets.all { it.completed }
    val cardColor by animateColorAsState(
        targetValue = if (allCompleted) AccentTeal12 else BackgroundSurface,
        animationSpec = tween(durationMillis = 1200),
        label = "CardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    var isEditingName by remember(exercise.name) { mutableStateOf(false) }
                    var editedName by remember(exercise.name) { mutableStateOf(exercise.name) }

                    if (isEditingName) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            textStyle = MaterialTheme.typography.titleLarge,
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (editedName.isNotBlank()) {
                                        onUpdateName(exercise.exerciseId, editedName)
                                        isEditingName = false
                                    }
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = AccentTeal)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentTeal,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clickable { isEditingName = true }
                        )
                    }

                    Text(
                        text = exercise.muscleGroups,
                        style = TextStyle(
                            fontSize = 9.sp,
                            color = MuscleGroupTag,
                            fontWeight = FontWeight.W400,
                            letterSpacing = 0.06.sp
                        )
                    )
                }
                
                Surface(
                    onClick = onAddSet,
                    shape = RoundedCornerShape(8.dp),
                    color = AccentTeal12,
                    border = BorderStroke(1.dp, AccentTeal35)
                ) {
                    Text(
                        "+ SET",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.W800,
                            color = AccentTeal
                        )
                    )
                }
            }

            SetColumnHeader()

            Column(modifier = Modifier.animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )) {
            exercise.sets.forEach { set ->
                SetRow(
                    set = set,
                    onWeightStep = { delta -> onWeightStep(set.id, delta) },
                    onWeightChange = { onWeightChange(set.id, it) },
                    onRepsStep = { delta -> onRepsStep(set.id, delta) },
                    onRepsChange = { onRepsChange(set.id, it) },
                    onToggleBodyweight = { onToggleBodyweight(set.id) },
                    onToggleCompleted = { onToggleCompleted(set.id) },
                    onDelete = { onDeleteSet(set.id) },
                )
                set.restAfterSeconds?.let { RestTimeIndicator(it) }
            }
            } // end animateContentSize Column
        }
    }
}

@Composable
private fun SetColumnHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("SET", Modifier.weight(1f))
        HeaderCell("LBS", Modifier.weight(2.2f))
        HeaderCell("REPS", Modifier.weight(2f))
        HeaderCell("BW", Modifier.weight(1f))
        HeaderCell("✓", Modifier.weight(1f))
        Spacer(Modifier.weight(0.6f))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = TextStyle(
            fontSize = 8.sp,
            fontWeight = FontWeight.W700,
            color = TextMuted,
            letterSpacing = 0.06.sp
        )
    )
}

// Set row

@Composable
private fun SetRow(
    set: LogSetUi,
    onWeightStep: (Int) -> Unit,
    onWeightChange: (String) -> Unit,
    onRepsStep: (Int) -> Unit,
    onRepsChange: (String) -> Unit,
    onToggleBodyweight: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowBg by animateColorAsState(
        targetValue = if (set.completed) AccentTeal12 else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "RowBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (set.completed) AccentTeal15 else BackgroundElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = set.setNumber.toString(),
                fontWeight = FontWeight.SemiBold,
                color = if (set.completed) AccentTeal else TextSecondary,
            )
        }

        val weightDisplay = when {
            set.isBodyweight && (set.weightLbs ?: 0) == 0 -> "BW"
            set.isBodyweight -> "BW+${WeightLbs.formatStored(set.weightLbs)}"
            (set.weightLbs ?: 0) == 0 -> ""
            else -> WeightLbs.formatStored(set.weightLbs)
        }
        NumberInputBox(
            modifier = Modifier.weight(2.2f),
            displayValue = weightDisplay,
            editable = !set.isBodyweight,
            onValueChange = onWeightChange,
            onDecrease = { onWeightStep(-50) },
            onIncrease = { onWeightStep(50) },
            onLongDecrease = { onWeightStep(-250) },
            onLongIncrease = { onWeightStep(250) },
            keyboardType = KeyboardType.Decimal,
        )

        NumberInputBox(
            modifier = Modifier.weight(2f),
            displayValue = if ((set.reps ?: 0) == 0) "" else set.reps.toString(),
            editable = true,
            onValueChange = onRepsChange,
            onDecrease = { onRepsStep(-1) },
            onIncrease = { onRepsStep(1) },
            onLongDecrease = { onRepsStep(-5) },
            onLongIncrease = { onRepsStep(5) },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (set.isBodyweight) AccentTeal12 else BackgroundElevated)
                    .clickable(onClick = onToggleBodyweight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "BW",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (set.isBodyweight) AccentTeal else TextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            TactileCheckButton(isCompleted = set.completed, onToggle = onToggleCompleted)
        }

        Box(
            modifier = Modifier
                .weight(0.6f)
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(BackgroundElevated)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "x",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// Number input

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NumberInputBox(
    modifier: Modifier,
    displayValue: String,
    editable: Boolean,
    onValueChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onLongDecrease: () -> Unit,
    onLongIncrease: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    var hasFocused by remember { mutableStateOf(false) }
    var draftText by remember(displayValue) { mutableStateOf(displayValue) }

    LaunchedEffect(displayValue) {
        draftText = displayValue
    }

    Row(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BackgroundElevated)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .combinedClickable(
                    onClick = onDecrease,
                    onLongClick = onLongDecrease
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("−", fontSize = 11.sp, fontWeight = FontWeight.W300, color = MuscleGroupTag)
        }
        
        if (editable) {
            BasicTextField(
                value = draftText,
                onValueChange = {
                    draftText = it
                    onValueChange(it)
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.W800,
                    fontSize = 13.sp,
                    color = TextPrimary,
                ),
                cursorBrush = SolidColor(TextPrimary),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !hasFocused) {
                            hasFocused = true
                            if (draftText == "0" || draftText.isEmpty()) {
                                draftText = ""
                                onValueChange("")
                            }
                        }
                        if (!focusState.isFocused) {
                            draftText = displayValue
                        }
                    },
                singleLine = true,
            )
        } else {
            Text(
                text = displayValue,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.W800,
                fontSize = 13.sp,
                color = TextPrimary
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .combinedClickable(
                    onClick = onIncrease,
                    onLongClick = onLongIncrease
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 11.sp, fontWeight = FontWeight.W700, color = AccentTeal)
        }
    }
}

// Rest time indicator

@Composable
private fun RestTimeIndicator(seconds: Int) {
    val mins = seconds / 60
    val secs = (seconds % 60).toString().padStart(2, '0')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(BorderSubtle.copy(alpha = 0.35f)))
        Text(
            text = "  $mins:$secs rest  ",
            style = TextStyle(
                fontSize = 9.sp,
                color = TextMuted,
                fontFeatureSettings = "tnum",
                letterSpacing = 0.05.sp,
            )
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(BorderSubtle.copy(alpha = 0.35f)))
    }
}

// Tactile checkmark button

@Composable
private fun TactileCheckButton(isCompleted: Boolean, onToggle: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.78f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CheckScale"
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isCompleted) AccentTeal12 else BackgroundElevated)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Done",
            tint = if (isCompleted) AccentTeal else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

// Small bodyweight toggle

@Composable
private fun SmallToggle(
    modifier: Modifier,
    active: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Box(modifier = modifier.padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
