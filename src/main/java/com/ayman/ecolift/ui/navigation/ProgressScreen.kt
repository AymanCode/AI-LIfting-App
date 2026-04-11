package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.ProgressExerciseUi
import com.ayman.ecolift.ui.viewmodel.ProgressPointUi
import com.ayman.ecolift.ui.viewmodel.ProgressViewModel

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (uiState.selectedExerciseName.isBlank()) {
                            "Pick an exercise to see max weight per session."
                        } else {
                            "Tracking max weight per session for ${uiState.selectedExerciseName}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProgressChart(points = uiState.chartPoints)
                }
            }
        }

        items(uiState.exercises, key = { it.exerciseId }) { exercise ->
            ExercisePickerRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                exercise = exercise,
                selected = exercise.exerciseId == uiState.selectedExerciseId,
                onClick = { viewModel.selectExercise(exercise.exerciseId) },
            )
        }
    }
}

@Composable
private fun ExercisePickerRow(
    modifier: Modifier,
    exercise: ProgressExerciseUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 4.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = exercise.name,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${exercise.sessions} sessions",
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProgressChart(points: List<ProgressPointUi>) {
    if (points.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Log sessions to populate the chart.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    var selectedIndex by remember(points) { mutableStateOf(points.lastIndex) }
    val maxWeight = points.maxOf { it.weightLbs }
    val minWeight = points.minOf { it.weightLbs }
    val midWeight = (maxWeight + minWeight) / 2

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.width(36.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    AxisLabel(maxWeight)
                    AxisLabel(midWeight)
                    AxisLabel(minWeight)
                }

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val density = LocalDensity.current
                    val widthPx = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()
                    val minRange = (maxWeight - minWeight).coerceAtLeast(1)
                    val pointOffsets = points.mapIndexed { index, point ->
                        val x = if (points.size == 1) {
                            widthPx / 2f
                        } else {
                            widthPx * index / (points.size - 1)
                        }
                        val y = heightPx - (((point.weightLbs - minWeight).toFloat() / minRange) * heightPx)
                        Offset(x, y)
                    }
                    val lineColor = MaterialTheme.colorScheme.primary
                    val pointFillColor = MaterialTheme.colorScheme.surface
                    val selectedPointColor = MaterialTheme.colorScheme.secondary
                    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(points) {
                                detectTapGestures { tapOffset ->
                                    val nearest = pointOffsets
                                        .mapIndexed { index, point -> index to (point - tapOffset).getDistance() }
                                        .minByOrNull { it.second }
                                    if (nearest != null && nearest.second <= with(density) { 24.dp.toPx() }) {
                                        selectedIndex = nearest.first
                                    }
                                }
                            }
                    ) {
                        repeat(3) { step ->
                            val y = size.height / 2f * step
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 2f,
                            )
                        }
                        val path = Path().apply {
                            pointOffsets.forEachIndexed { index, offset ->
                                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                            }
                        }
                        drawPath(path = path, color = lineColor, style = Stroke(width = 6f))
                        pointOffsets.forEachIndexed { index, offset ->
                            drawCircle(color = pointFillColor, radius = 10f, center = offset)
                            drawCircle(
                                color = if (index == selectedIndex) selectedPointColor else lineColor,
                                radius = 7f,
                                center = offset,
                            )
                        }
                    }

                    val selectedPoint = points[selectedIndex]
                    val selectedOffset = pointOffsets[selectedIndex]
                    Surface(
                        modifier = Modifier.padding(
                            start = with(density) { (selectedOffset.x.toDp() - 40.dp).coerceAtLeast(0.dp) },
                            top = with(density) { (selectedOffset.y.toDp() - 54.dp).coerceAtLeast(0.dp) },
                        ),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "${selectedPoint.weightLbs} lbs",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = selectedPoint.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEach { point ->
                    Text(
                        text = point.label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AxisLabel(value: Int) {
    Text(
        text = value.toString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
