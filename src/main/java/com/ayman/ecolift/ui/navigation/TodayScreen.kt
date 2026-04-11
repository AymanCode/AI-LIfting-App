package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.CycleSlotUi
import com.ayman.ecolift.ui.viewmodel.LogExerciseUi
import com.ayman.ecolift.ui.viewmodel.LogSetUi
import com.ayman.ecolift.ui.viewmodel.LogViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(viewModel: LogViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DateNavigator(
                label = uiState.currentDateLabel,
                slotLabel = uiState.cycleSlot?.label,
                onPrevious = viewModel::goToPreviousDay,
                onNext = viewModel::goToNextDay,
            )
        }

        if (uiState.pendingReviews.isNotEmpty()) {
            item {
                PendingReviewBanner(
                    expanded = uiState.reviewsExpanded,
                    reviewCount = uiState.pendingReviews.size,
                    reviews = uiState.pendingReviews.map { "${it.rawInput} | ${it.dateLogged}" },
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

        if (uiState.swapNotices.isNotEmpty()) {
            items(uiState.swapNotices, key = { "${it.title}-${it.detail}" }) { notice ->
                SwapNoticeCard(
                    title = notice.title,
                    detail = notice.detail,
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
            )
        }

        item {
            AddExerciseCard(
                input = uiState.exerciseInput,
                suggestions = uiState.inlineSuggestions,
                onInputChange = viewModel::updateExerciseInput,
                onSuggestionClick = viewModel::useSuggestion,
                onAdd = viewModel::addExerciseFromInput,
            )
        }
    }
}

@Composable
private fun SwapNoticeCard(
    title: String,
    detail: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateNavigator(
    label: String,
    slotLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPrevious) { Text("Prev") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (slotLabel != null) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(slotLabel) },
                        )
                    }
                }
                TextButton(onClick = onNext) { Text("Next") }
            }
            Text(
                text = "Log the day, assign a split slot, and let the app preload the matching prior occurrence.",
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PendingReviewBanner(
    expanded: Boolean,
    reviewCount: Int,
    reviews: List<String>,
    onToggle: () -> Unit,
    onResolve: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = "$reviewCount pending exercise review${if (reviewCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = if (expanded) "Tap a row to mark it reviewed." else "Tap to expand the review queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            if (expanded) {
                reviews.forEachIndexed { index, review ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResolve(index) },
                        shape = RoundedCornerShape(0.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    ) {
                        Text(
                            text = review,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CyclePickerCard(
    options: List<CycleSlotUi>,
    onSelect: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pick today's split slot",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "The app loads the same slot from occurrence N-1, not just the last matching day type.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option.isExpected,
                        onClick = { onSelect(option.type) },
                        label = {
                            Text(
                                text = if (option.isExpected) "${option.label} | Next" else option.label
                            )
                        },
                    )
                }
            }
        }
    }
}

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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (exercise.lastSessionHint != null) {
                    Text(
                        text = "Last matching session: ${exercise.lastSessionHint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SetHeader()

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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddSet)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add Set",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderText("Set", Modifier.weight(0.65f))
        HeaderText("Weight", Modifier.weight(1.8f))
        HeaderText("Reps", Modifier.weight(1.4f))
        HeaderText("BW", Modifier.weight(0.9f))
        HeaderText("Done", Modifier.weight(0.9f))
        HeaderText("", Modifier.weight(0.55f))
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (set.completed) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(0.65f),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = set.setNumber.toString(),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            MetricControl(
                modifier = Modifier.weight(1.8f),
                displayValue = if (set.isBodyweight) {
                    if (set.weightLbs == 0) "BW" else "BW+${set.weightLbs}"
                } else {
                    set.weightLbs.toString()
                },
                editable = !set.isBodyweight,
                onValueChange = onWeightChange,
                onDecrease = { onWeightStep(-5) },
                onIncrease = { onWeightStep(5) },
            )

            MetricControl(
                modifier = Modifier.weight(1.4f),
                displayValue = set.reps.toString(),
                editable = true,
                onValueChange = onRepsChange,
                onDecrease = { onRepsStep(-1) },
                onIncrease = { onRepsStep(1) },
            )

            TogglePill(
                modifier = Modifier.weight(0.9f),
                text = "BW",
                selected = set.isBodyweight,
                onClick = onToggleBodyweight,
            )

            TogglePill(
                modifier = Modifier.weight(0.9f),
                text = if (set.completed) "Yes" else "No",
                selected = set.completed,
                onClick = onToggleCompleted,
            )

            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .size(28.dp)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun MetricControl(
    modifier: Modifier,
    displayValue: String,
    editable: Boolean,
    onValueChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StepButton(text = "-", onClick = onDecrease)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (editable) {
                    OutlinedTextField(
                        value = displayValue,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                } else {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            StepButton(text = "+", onClick = onIncrease)
        }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(30.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TogglePill(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AddExerciseCard(
    input: String,
    suggestions: List<String>,
    onInputChange: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add Exercise",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search or type exercise") },
            )
            if (suggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        suggestions.forEach { suggestion ->
                            Text(
                                text = suggestion,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionClick(suggestion) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAdd)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add to Log",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
