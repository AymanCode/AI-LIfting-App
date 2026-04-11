package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.CycleSlotUi
import com.ayman.ecolift.ui.viewmodel.ExerciseChipUi
import com.ayman.ecolift.ui.viewmodel.LogExerciseUi
import com.ayman.ecolift.ui.viewmodel.LogSetUi
import com.ayman.ecolift.ui.viewmodel.LogViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(viewModel: LogViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Main Content ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.pendingReviews.isNotEmpty()) {
                    item {
                        PendingReviewBanner(
                            expanded = uiState.reviewsExpanded,
                            reviewCount = uiState.pendingReviews.size,
                            reviews = uiState.pendingReviews.map { "${it.rawInput} — ${it.dateLogged}" },
                            onToggle = viewModel::toggleReviewsExpanded,
                            onResolve = { index ->
                                viewModel.markReviewResolved(uiState.pendingReviews[index].id)
                            },
                        )
                    }
                }

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
                        onDeleteSet = viewModel::deleteSet,
                        onUpdateName = viewModel::updateExerciseName,
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // ── Floating Rest Timer ──────────────────────────────────────────────
        uiState.restTimerSeconds?.let { seconds ->
            RestTimerOverlay(
                seconds = seconds,
                onCancel = viewModel::cancelRestTimer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Rest: ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Cancel",
                modifier = Modifier.clickable(onClick = onCancel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Date header ───────────────────────────────────────────────────────────────

@Composable
private fun DateHeader(
    label: String,
    slotLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (slotLabel != null) {
                Text(
                    text = slotLabel,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
}

// ── Pinned search / add bar ───────────────────────────────────────────────────

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
            placeholder = { Text("Search or type new exercise…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (input.isNotBlank()) {
                    TextButton(onClick = onAdd) { Text("Add") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
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
        } else if (quickAdd.isNotEmpty() && input.isBlank()) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickAdd.forEach { exercise ->
                    Surface(
                        onClick = { onSuggestionClick(exercise.name) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(text = exercise.name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// ── Pending review banner ─────────────────────────────────────────────────────

@Composable
private fun PendingReviewBanner(
    expanded: Boolean,
    reviewCount: Int,
    reviews: List<String>,
    onToggle: () -> Unit,
    onResolve: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "$reviewCount pending review${if (reviewCount == 1) "" else "s"}  ${if (expanded) "▲" else "▼"}",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (expanded) {
                reviews.forEachIndexed { index, review ->
                    Text(
                        text = review,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable { onResolve(index) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ── Cycle slot picker ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CyclePickerCard(
    options: List<CycleSlotUi>,
    onSelect: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "What are you hitting today?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                text = "Select a slot — the matching prior session is pre-loaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option.isExpected,
                        onClick = { onSelect(option.type) },
                        label = { Text(if (option.isExpected) "${option.label} ✦" else option.label) },
                    )
                }
            }
        }
    }
}

// ── Exercise card ─────────────────────────────────────────────────────────────

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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    var isEditingName by remember { mutableStateOf(false) }
                    var editedName by remember { mutableStateOf(exercise.name) }

                    if (isEditingName) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (editedName.isNotBlank()) {
                                        onUpdateName(exercise.exerciseId, editedName)
                                        isEditingName = false
                                    }
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            text = exercise.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.clickable { isEditingName = true }
                        )
                    }

                    if (exercise.lastSessionHint != null) {
                        Text(
                            text = "Last: ${exercise.lastSessionHint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                
                // Est. 1RM Badge
                if (exercise.estimated1RM > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (exercise.isNewPB) MaterialTheme.colorScheme.tertiaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = "Est. 1RM: ${exercise.estimated1RM}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (exercise.isNewPB) MaterialTheme.colorScheme.onTertiaryContainer 
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (exercise.isNewPB) {
                            Text(
                                text = "NEW PB! ✦",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            SetColumnHeader()

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
            }

            TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Set")
            }
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
        HeaderCell("Set", Modifier.weight(1f))
        HeaderCell("Lbs", Modifier.weight(2.2f))
        HeaderCell("Reps", Modifier.weight(2f))
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
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Set row ───────────────────────────────────────────────────────────────────

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
    val rowBg = if (set.completed)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = set.setNumber.toString(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val weightDisplay = when {
            set.isBodyweight && set.weightLbs == 0 -> "BW"
            set.isBodyweight -> "BW+${set.weightLbs}"
            else -> set.weightLbs.toString()
        }
        NumberInputBox(
            modifier = Modifier.weight(2.2f),
            displayValue = weightDisplay,
            editable = !set.isBodyweight,
            onValueChange = onWeightChange,
            onDecrease = { onWeightStep(-5) },
            onIncrease = { onWeightStep(5) },
            onLongDecrease = { onWeightStep(-25) },
            onLongIncrease = { onWeightStep(25) },
        )

        NumberInputBox(
            modifier = Modifier.weight(2f),
            displayValue = set.reps.toString(),
            editable = true,
            onValueChange = onRepsChange,
            onDecrease = { onRepsStep(-1) },
            onIncrease = { onRepsStep(1) },
            onLongDecrease = { onRepsStep(-5) },
            onLongIncrease = { onRepsStep(5) },
        )

        SmallToggle(
            modifier = Modifier.weight(1f),
            active = set.isBodyweight,
            label = "BW",
            onClick = onToggleBodyweight,
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (set.completed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable(onClick = onToggleCompleted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Done",
                    tint = if (set.completed) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(0.6f)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Number input — clears on first focus so user can just type ────────────────

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
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    var hasFocused by remember(displayValue) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 2.dp, vertical = 4.dp),
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
            Text("−", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        if (editable) {
            BasicTextField(
                value = displayValue,
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = onSurface,
                ),
                cursorBrush = SolidColor(onSurface),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !hasFocused) {
                            hasFocused = true
                            if (displayValue == "0") onValueChange("")
                        }
                    },
                singleLine = true,
            )
        } else {
            Text(
                text = displayValue,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
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
            Text("+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Small BW toggle pill ──────────────────────────────────────────────────────

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
