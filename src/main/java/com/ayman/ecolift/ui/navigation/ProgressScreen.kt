package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.ui.viewmodel.*
import java.util.Locale

@Composable
fun ProgressScreen(
    onOpenBackups: () -> Unit = {},
    viewModel: ProgressViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState.selectedExerciseId,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ProgressNavigation"
    ) { selectedId ->
        if (selectedId == null) {
            ProgressList(
                exercises = uiState.exercises,
                onExerciseClick = { viewModel.selectExercise(it) },
                onOpenBackups = onOpenBackups,
            )
        } else {
            ExerciseDetail(
                uiState = uiState,
                onBack = { viewModel.selectExercise(null) },
                onTimeframeSelect = { viewModel.setTimeframe(it) },
                onMetricSelect = { viewModel.setMetric(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressList(
    exercises: List<ProgressExerciseUi>,
    onExerciseClick: (Long) -> Unit,
    onOpenBackups: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredExercises = remember(searchQuery, exercises) {
        exercises.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        IconButton(
                            onClick = onOpenBackups,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Open backups")
                        }
                    }
                    
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "EXERCISE TRENDS AND STATS",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color(0xFF6B6B70), // SplitTheme.TextTertiary
                            fontWeight = FontWeight.W800,
                            letterSpacing = 0.08.sp
                        )
                    )

                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        placeholder = { Text("Search exercises...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "Last: ${exercise.lastSessionSummary}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "%+.1f%%", exercise.changePercentage),
                    color = if (exercise.changePercentage >= 0) Color(0xFF00C9A7) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Box(modifier = Modifier.width(60.dp).height(24.dp)) {
                    Sparkline(points = exercise.trend)
                }
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
        drawPath(path = path, color = Color(0xFF00C9A7), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetail(
    uiState: ProgressUiState,
    onBack: () -> Unit,
    onTimeframeSelect: (TimeframeFilter) -> Unit,
    onMetricSelect: (ProgressMetric) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.selectedExerciseName, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { TimeframeFilterRow(uiState.timeframe, onTimeframeSelect) }
            
            item {
                MetricToggle(uiState.selectedMetric, onMetricSelect)
                Card(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    ProgressChart(points = uiState.chartPoints, metric = uiState.selectedMetric)
                }
            }

            item { ProgressInsight(uiState) }

            item { MetricGrid(uiState.stats) }
        }
    }
}

@Composable
private fun MetricToggle(selected: ProgressMetric, onSelect: (ProgressMetric) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProgressMetric.values().forEach { metric ->
            val isSelected = metric == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { onSelect(metric) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = metric.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun TimeframeFilterRow(selected: TimeframeFilter, onSelect: (TimeframeFilter) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TimeframeFilter.values().forEach { filter ->
            val isSelected = filter == selected
            val label = when (filter) {
                TimeframeFilter.ONE_MONTH -> "1M"
                TimeframeFilter.THREE_MONTHS -> "3M"
                TimeframeFilter.SIX_MONTHS -> "6M"
                TimeframeFilter.ONE_YEAR -> "1Y"
                TimeframeFilter.ALL_TIME -> "All"
            }
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(label, fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ProgressInsight(uiState: ProgressUiState) {
    val stats = uiState.stats ?: return
    val insight = remember(stats) {
        when {
            stats.est1RmDelta > 2f -> "Strength increasing (+${String.format(Locale.US, "%.1f", stats.est1RmDelta)}%)"
            stats.est1RmDelta < -2f -> "Strength declining"
            stats.volumeDelta < -10f -> "Training frequency dropping"
            else -> "Plateau detected - consider changing intensity"
        }
    }
    
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF00C9A7), CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(insight, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MetricGrid(stats: ProgressStatsUi?) {
    if (stats == null) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Current PR", stats.currentPr, "lbs", stats.currentPrDelta, Modifier.weight(1f))
            StatCard("Est. 1RM", stats.est1Rm, "lbs", stats.est1RmDelta, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total Volume (30d)", stats.totalVolume, "lbs", stats.volumeDelta, Modifier.weight(1f))
            StatCard("Workouts (30d)", stats.workoutCount.toString(), "", stats.workoutCountDelta.toFloat(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, delta: Float, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Text(
                text = (if (delta >= 0) "+" else "") + String.format(Locale.US, "%.1f%s", delta, if(label.contains("Workouts")) "" else "%"),
                color = if (delta >= 0) Color(0xFF00C9A7) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProgressChart(points: List<ProgressPointUi>, metric: ProgressMetric) {
    if (points.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data") }
        return
    }

    val values = points.map { 
        when(metric) {
            ProgressMetric.ESTIMATED_1RM -> it.estimated1RM
            ProgressMetric.WEIGHT -> it.maxWeight.toFloat()
            ProgressMetric.VOLUME -> it.volume.toFloat()
        }
    }
    val max = values.max().coerceAtLeast(1f)
    val min = values.min()
    val chartMin = if (max == min) 0f else min
    val range = (max - chartMin).coerceAtLeast(1f)
    
    var selectedIndex by remember { mutableStateOf(-1) }

    val progress = remember(points, metric) { Animatable(0f) }
    LaunchedEffect(points, metric) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }

    val yAxisLabels = remember(values, metric) { buildYAxisLabels(min = chartMin, max = max, metric = metric) }
    val xAxisLabels = remember(points) { buildXAxisLabels(points) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.width(48.dp).fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yAxisLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            selectedIndex = findClosestPoint(offset, points.size, size.width.toFloat())
                        }
                    }
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { offset -> selectedIndex = findClosestPoint(offset, points.size, size.width.toFloat()) },
                            onDrag = { change, _ -> selectedIndex = findClosestPoint(change.position, points.size, size.width.toFloat()) },
                            onDragEnd = { selectedIndex = -1 }
                        )
                    }
                ) {
                    val width = size.width
                    val height = size.height
                    val spacing = width / (points.size - 1).coerceAtLeast(1)

                    drawLine(Color.Gray.copy(alpha = 0.16f), Offset(0f, height), Offset(width, height), 1.dp.toPx())
                    drawLine(Color.Gray.copy(alpha = 0.16f), Offset(0f, 0f), Offset(0f, height), 1.dp.toPx())

                    for (i in 0..3) {
                        val y = height - (i * height / 3)
                        drawLine(Color.Gray.copy(alpha = 0.1f), Offset(0f, y), Offset(width, y), 1.dp.toPx())
                    }

                    val prog = progress.value
                    val pointsPhase = 0.5f
                    val pointsT = (prog / pointsPhase).coerceIn(0f, 1f)
                    val lineT = ((prog - pointsPhase) / (1f - pointsPhase)).coerceIn(0f, 1f)

                    val xs = FloatArray(points.size)
                    val ys = FloatArray(points.size)
                    points.forEachIndexed { i, _ ->
                        xs[i] = if (points.size > 1) i * spacing else width / 2f
                        ys[i] = height - ((values[i] - chartMin) / range) * height
                    }

                    if (lineT > 0f && points.size > 1) {
                        val totalSegs = points.size - 1
                        val segFloat = lineT * totalSegs
                        val fullSegs = segFloat.toInt().coerceAtMost(totalSegs - 1)
                        val partial = segFloat - fullSegs
                        val reached = if (lineT >= 1f) totalSegs else fullSegs

                        val path = Path()
                        val fillPath = Path()
                        path.moveTo(xs[0], ys[0])
                        fillPath.moveTo(xs[0], height)
                        fillPath.lineTo(xs[0], ys[0])

                        for (i in 1..reached) {
                            path.lineTo(xs[i], ys[i])
                            fillPath.lineTo(xs[i], ys[i])
                        }
                        val endX: Float
                        val endY: Float
                        if (lineT < 1f) {
                            endX = xs[fullSegs] + (xs[fullSegs + 1] - xs[fullSegs]) * partial
                            endY = ys[fullSegs] + (ys[fullSegs + 1] - ys[fullSegs]) * partial
                            path.lineTo(endX, endY)
                            fillPath.lineTo(endX, endY)
                        } else {
                            endX = xs.last()
                            endY = ys.last()
                        }
                        fillPath.lineTo(endX, height)
                        fillPath.close()

                        drawPath(fillPath, Brush.verticalGradient(listOf(Color(0xFF00C9A7).copy(alpha = 0.2f), Color.Transparent)))
                        drawPath(path, Color(0xFF00C9A7), style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
                    }

                    val n = points.size
                    points.forEachIndexed { i, _ ->
                        val start = if (n > 1) i.toFloat() / n else 0f
                        val dur = 1f / n.coerceAtLeast(1)
                        val local = ((pointsT - start) / dur).coerceIn(0f, 1f)
                        val eased = 1f - (1f - local) * (1f - local)
                        if (eased > 0f) {
                            val outer = 4.dp.toPx() * eased
                            val inner = 2.dp.toPx() * eased
                            drawCircle(Color(0xFF00C9A7).copy(alpha = eased), outer, Offset(xs[i], ys[i]))
                            drawCircle(Color.White.copy(alpha = eased), inner, Offset(xs[i], ys[i]))
                        }
                    }

                    if (selectedIndex != -1) {
                        val x = selectedIndex * spacing
                        drawLine(Color.Gray.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    }
                }

                if (selectedIndex != -1 && selectedIndex < points.size) {
                    val point = points[selectedIndex]
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(point.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(metricTooltipLabel(point, metric), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(56.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                xAxisLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun findClosestPoint(offset: Offset, count: Int, width: Float): Int {
    if (count <= 1) return 0
    val spacing = width / (count - 1)
    return (offset.x / spacing).toInt().coerceIn(0, count - 1)
}

private fun buildYAxisLabels(min: Float, max: Float, metric: ProgressMetric): List<String> {
    val ticks = 4
    return (0 until ticks).map { index ->
        val fraction = 1f - (index / (ticks - 1).toFloat())
        val value = min + ((max - min) * fraction)
        formatAxisValue(value, metric)
    }
}

private fun buildXAxisLabels(points: List<ProgressPointUi>): List<String> {
    return when (points.size) {
        0 -> emptyList()
        1 -> listOf(points.first().label)
        2 -> listOf(points.first().label, points.last().label)
        else -> listOf(points.first().label, points[points.lastIndex / 2].label, points.last().label)
    }
}

private fun formatAxisValue(value: Float, metric: ProgressMetric): String {
    return when (metric) {
        ProgressMetric.ESTIMATED_1RM,
        ProgressMetric.WEIGHT -> WeightLbs.formatStored(value.toInt())
        ProgressMetric.VOLUME -> {
            if (value >= 1000f) String.format(Locale.US, "%.1fk", value / 1000f)
            else value.toInt().toString()
        }
    }
}

private fun metricTooltipLabel(point: ProgressPointUi, metric: ProgressMetric): String {
    return when (metric) {
        ProgressMetric.ESTIMATED_1RM -> "Est. 1RM ${String.format(Locale.US, "%.1f", point.estimated1RM)} lbs"
        ProgressMetric.WEIGHT -> "${WeightLbs.formatStored(point.maxWeight)} lbs x ${point.reps}"
        ProgressMetric.VOLUME -> "Volume ${formatAxisValue(point.volume.toFloat(), ProgressMetric.VOLUME)}"
    }
}
