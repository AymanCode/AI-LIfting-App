package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.ui.viewmodel.ProgressExerciseUi
import com.ayman.ecolift.ui.viewmodel.ProgressPointUi
import com.ayman.ecolift.ui.viewmodel.ProgressViewModel
import com.ayman.ecolift.ui.viewmodel.TimeframeFilter

import java.util.Locale

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState.selectedExerciseId,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "ProgressNavigation"
    ) { selectedId ->
        if (selectedId == null) {
            ProgressList(
                exercises = uiState.exercises,
                onExerciseClick = { viewModel.selectExercise(it) }
            )
        } else {
            ExerciseDetail(
                uiState = uiState,
                onBack = { viewModel.selectExercise(null) },
                onTimeframeSelect = { viewModel.setTimeframe(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressList(
    exercises: List<ProgressExerciseUi>,
    onExerciseClick: (Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredExercises = remember(searchQuery, exercises) {
        exercises.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Progress",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search exercises...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredExercises, key = { it.exerciseId }) { exercise ->
                ExerciseProgressCard(exercise, onClick = { onExerciseClick(exercise.exerciseId) })
            }
        }
    }
}

@Composable
private fun ExerciseProgressCard(exercise: ProgressExerciseUi, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Last: ${exercise.lastSessionDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Sparkline placeholder
            Box(modifier = Modifier.width(80.dp).height(30.dp)) {
                Sparkline(points = exercise.trend)
            }
        }
    }
}

@Composable
private fun Sparkline(points: List<Int>) {
    if (points.size < 2) return
    val max = points.max().toFloat()
    val min = points.min().toFloat()
    val range = (max - min).coerceAtLeast(1f)
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val path = Path()
        
        points.forEachIndexed { i, p ->
            val x = i * (width / (points.size - 1))
            val y = height - ((p - min) / range) * height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = Color(0xFF42A5F5),
            style = Stroke(width = 4f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetail(
    uiState: com.ayman.ecolift.ui.viewmodel.ProgressUiState,
    onBack: () -> Unit,
    onTimeframeSelect: (TimeframeFilter) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.selectedExerciseName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimeframeFilterRow(uiState.timeframe, onTimeframeSelect)
            
            Card(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    ProgressChart(points = uiState.chartPoints)
                }
            }

            MetricGrid(uiState.chartPoints)
        }
    }
}

@Composable
private fun TimeframeFilterRow(
    selected: TimeframeFilter,
    onSelect: (TimeframeFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeframeFilter.values().forEach { filter ->
            val isSelected = filter == selected
            val label = when (filter) {
                TimeframeFilter.THREE_MONTHS -> "3 Months"
                TimeframeFilter.YTD -> "YTD"
                TimeframeFilter.ALL_TIME -> "All Time"
            }
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun MetricGrid(points: List<ProgressPointUi>) {
    val latest = points.lastOrNull() ?: return
    val maxWeight = points.maxOfOrNull { it.maxWeight } ?: 0
    val maxVolume = points.maxOfOrNull { it.volume } ?: 0
    val maxReps = points.maxOfOrNull { it.maxReps } ?: 0
    val est1RM = points.maxOfOrNull { it.estimated1RM } ?: 0f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Est. 1RM", String.format(Locale.US, "%.1f", est1RM), "lbs", Modifier.weight(1f))
            MetricCard("Max Weight", maxWeight.toString(), "lbs", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Max Volume", maxVolume.toString(), "lbs", Modifier.weight(1f))
            MetricCard("Max Reps", maxReps.toString(), "reps", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun ProgressChart(points: List<ProgressPointUi>) {
    if (points.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data for this timeframe")
        }
        return
    }

    val volumes = points.map { it.volume.toFloat() }
    val max = volumes.max().coerceAtLeast(1f)
    val min = volumes.min()
    val range = (max - min).coerceAtLeast(1f)

    Row(modifier = Modifier.fillMaxSize()) {
        // Y-Axis Labels
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatVolume(max.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = formatVolume(((max + min) / 2).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = formatVolume(min.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val width = size.width
            val height = size.height

            // Grid lines
            val gridLines = 3
            for (i in 0 until gridLines) {
                val y = i * (height / (gridLines - 1))
                drawLine(
                    color = Color.Gray.copy(alpha = 0.1f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val path = Path()
            val fillPath = Path()
            
            points.forEachIndexed { i, p ->
                val x = if (points.size > 1) i * (width / (points.size - 1)) else width / 2f
                val y = height - ((p.volume - min) / range) * height
                
                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    val prevX = (i - 1) * (width / (points.size - 1))
                    val prevY = height - ((points[i-1].volume - min) / range) * height
                    path.cubicTo(
                        (prevX + x) / 2f, prevY,
                        (prevX + x) / 2f, y,
                        x, y
                    )
                    fillPath.cubicTo(
                        (prevX + x) / 2f, prevY,
                        (prevX + x) / 2f, y,
                        x, y
                    )
                }
                if (i == points.lastIndex && points.size > 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF42A5F5).copy(alpha = 0.3f), Color.Transparent)
                )
            )
            drawPath(
                path = path,
                color = Color(0xFF42A5F5),
                style = Stroke(width = 6f)
            )
        }
    }
}

private fun formatVolume(volume: Int): String {
    return if (volume >= 1000) {
        String.format(Locale.US, "%.1fk", volume / 1000f)
    } else {
        volume.toString()
    }
}
