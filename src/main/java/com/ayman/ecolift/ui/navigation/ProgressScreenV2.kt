package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogType
import com.ayman.ecolift.ui.theme.glassPanel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class TimeRangeV2(val label: String) { ONE_MONTH("1M"), THREE_MONTHS("3M"), SIX_MONTHS("6M"), ONE_YEAR("1Y"), ALL("All") }
enum class ProgressMetricV2 { ESTIMATED_1RM, WEIGHT, VOLUME }
enum class InsightTypeV2 { POSITIVE, NEUTRAL, NEGATIVE }
enum class ProgressOrganizationModeV2 { PROGRESS, SPLIT }

data class ExerciseDataPoint(
    val date: LocalDate,
    val estimatedOneRm: Float,
    val maxWeight: Float,
    val totalVolume: Float
)

data class ProgressExercise(
    val name: String,
    val muscleGroups: String,
    val lastSetLabel: String,
    val trendPercent: Float,
    val id: Long? = null
)

data class ProgressSplitPage(
    val id: Long,
    val name: String,
    val exercises: List<ProgressExercise>,
)

@Composable
fun ProgressExerciseListItem(
    exerciseName: String,
    muscleGroups: String,
    lastSetLabel: String,
    trendPercent: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, palette.glassStroke),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exerciseName,
                    style = LogType.exerciseTitle,
                    color = palette.ink,
                    maxLines = 1
                )
                Text(
                    text = muscleGroups.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkSubtle,
                    letterSpacing = 0.sp
                )
                Text(
                    text = lastSetLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Column(
                modifier = Modifier.width(74.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (trendPercent > 0.05f) {
                    Icon(Icons.Default.TrendingUp, contentDescription = "Up", tint = palette.complete, modifier = Modifier.size(16.dp))
                    Text(
                        text = "+${"%.1f".format(trendPercent)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.complete,
                        fontWeight = FontWeight.Bold
                    )
                } else if (trendPercent < -0.05f) {
                    Icon(Icons.Default.TrendingDown, contentDescription = "Down", tint = palette.danger, modifier = Modifier.size(16.dp))
                    Text(
                        text = "${"%.1f".format(trendPercent)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.danger,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(Icons.Default.Remove, contentDescription = "Neutral", tint = palette.inkSubtle, modifier = Modifier.size(16.dp))
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.inkSubtle,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    delta: String?,
    deltaPositive: Boolean?,
    subLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, shape),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, palette.glassStroke),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = LogType.railValue,
                color = palette.ink
            )
            if (delta != null) {
                val deltaColor = when (deltaPositive) {
                    true -> palette.complete
                    false -> palette.danger
                    null -> palette.inkSubtle
                }
                Text(
                    text = delta,
                    style = MaterialTheme.typography.bodySmall,
                    color = deltaColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkSubtle,
                    modifier = Modifier.padding(top = if (delta != null) 2.dp else 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressDetailScreen(
    exerciseName: String,
    muscleGroups: String,
    dataPoints: List<ExerciseDataPoint>,
    selectedRange: TimeRangeV2,
    selectedMetric: ProgressMetricV2,
    insightText: String,
    insightType: InsightTypeV2,
    currentPr: Float,
    currentPrDeltaPercent: Float,
    prDate: LocalDate?,
    estimatedOneRm: Float,
    estimatedOneRmDeltaPercent: Float,
    totalVolume: Float,
    volumeDeltaPercent: Float,
    workoutCount: Int,
    workoutCountDeltaPercent: Float,
    onBack: () -> Unit,
    onRangeChange: (TimeRangeV2) -> Unit,
    onMetricChange: (ProgressMetricV2) -> Unit,
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                windowInsets = WindowInsets(0),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = exerciseName.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "Performance overview",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkSubtle
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.glassPanel(palette, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = palette.ink
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time range chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(TimeRangeV2.values().toList()) { range ->
                        FilterChip(
                            selected = range == selectedRange,
                            onClick = { onRangeChange(range) },
                            label = { Text(range.label) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = palette.glassFill,
                                selectedContainerColor = palette.accent.copy(alpha = 0.90f),
                                labelColor = palette.inkMuted,
                                selectedLabelColor = palette.ink
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = range == selectedRange,
                                borderColor = palette.glassStroke,
                                selectedBorderColor = palette.glassStrokeStrong
                            )
                        )
                    }
                }
            }

            // Metric segmented control
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPanel(palette, RoundedCornerShape(14.dp), strong = true),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, palette.glassStrokeStrong),
                    shadowElevation = 0.dp
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ProgressMetricV2.values().forEach { metric ->
                            val isSelected = metric == selectedMetric
                            TextButton(
                                onClick = { onMetricChange(metric) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    containerColor = if (isSelected) palette.accentStrong.copy(alpha = 0.18f) else Color.Transparent,
                                    contentColor = if (isSelected) palette.accentStrong else palette.inkMuted
                                )
                            ) {
                                Text(
                                    text = when (metric) {
                                        ProgressMetricV2.ESTIMATED_1RM -> "Est. 1RM"
                                        ProgressMetricV2.WEIGHT -> "Weight"
                                        ProgressMetricV2.VOLUME -> "Volume"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Chart section
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPanel(palette, RoundedCornerShape(14.dp), strong = true),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, palette.glassStrokeStrong),
                    shadowElevation = 0.dp
                ) {
                    if (dataPoints.size < 2) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.ShowChart,
                                contentDescription = null,
                                tint = palette.accentStrong.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Not enough data yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = palette.ink,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                text = "Log ${maxOf(0, 2 - dataPoints.size)} more session(s) to see your trend",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (dataPoints.size == 1) {
                                val value = when (selectedMetric) {
                                    ProgressMetricV2.ESTIMATED_1RM -> dataPoints[0].estimatedOneRm
                                    ProgressMetricV2.WEIGHT -> dataPoints[0].maxWeight
                                    ProgressMetricV2.VOLUME -> dataPoints[0].totalVolume
                                }
                                Text(
                                    text = "%.0f".format(value),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.ink,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    } else {
                        var tooltipData by remember(dataPoints, selectedMetric) { mutableStateOf<Pair<Offset, ExerciseDataPoint>?>(null) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .padding(14.dp)
                        ) {
                            val values = dataPoints.map {
                                when (selectedMetric) {
                                    ProgressMetricV2.ESTIMATED_1RM -> it.estimatedOneRm
                                    ProgressMetricV2.WEIGHT -> it.maxWeight
                                    ProgressMetricV2.VOLUME -> it.totalVolume
                                }
                            }
                            val rawMinY = values.minOrNull() ?: 0f
                            val rawMaxY = values.maxOrNull() ?: 100f
                            val minY = if (rawMinY == rawMaxY) (rawMinY - 1f).coerceAtLeast(0f) else rawMinY * 0.95f
                            val maxY = if (rawMinY == rawMaxY) rawMaxY + 1f else rawMaxY * 1.05f
                            val yRange = (maxY - minY).coerceAtLeast(1f)
                            
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(dataPoints, selectedMetric) {
                                        detectTapGestures { offset ->
                                            val xStep = size.width / (dataPoints.size - 1).coerceAtLeast(1)
                                            val index = (offset.x / xStep).toInt().coerceIn(0, dataPoints.size - 1)
                                            val xPos = index * xStep
                                            val valRatio = (values[index] - minY) / yRange
                                            val yPos = size.height - (valRatio * size.height)
                                            tooltipData = Pair(Offset(xPos.toFloat(), yPos.toFloat()), dataPoints[index])
                                        }
                                    }
                            ) {
                                val width = size.width
                                val height = size.height
                                
                                // Draw grid lines
                                for (i in 0..2) {
                                    val y = height * (i / 2f)
                                    drawLine(
                                        color = palette.ink.copy(alpha = 0.10f),
                                        start = Offset(0f, y),
                                        end = Offset(width, y),
                                        strokeWidth = 1f
                                    )
                                }
                                
                                if (dataPoints.size > 1) {
                                    val path = Path()
                                    val fillPath = Path()
                                    
                                    val xStep = width / (dataPoints.size - 1)
                                    var prevX = 0f
                                    var prevY = height - (((values[0] - minY) / yRange) * height)
                                    
                                    path.moveTo(prevX, prevY)
                                    fillPath.moveTo(prevX, height)
                                    fillPath.lineTo(prevX, prevY)
                                    
                                    for (i in 1 until dataPoints.size) {
                                        val x = i * xStep
                                        val y = height - (((values[i] - minY) / yRange) * height)
                                        
                                        val controlX = (prevX + x) / 2
                                        path.cubicTo(controlX, prevY, controlX, y, x, y)
                                        fillPath.cubicTo(controlX, prevY, controlX, y, x, y)
                                        
                                        prevX = x
                                        prevY = y
                                    }
                                    
                                    fillPath.lineTo(width, height)
                                    fillPath.close()
                                    
                                    drawPath(
                                        path = fillPath,
                                        color = palette.accent.copy(alpha = 0.12f)
                                    )
                                    
                                    drawPath(
                                        path = path,
                                        color = palette.accentStrong,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                    )
                                    
                                    // Draw data point circles
                                    for (i in 0 until dataPoints.size) {
                                        val x = i * xStep
                                        val y = height - (((values[i] - minY) / yRange) * height)
                                        drawCircle(
                                            color = palette.ink,
                                            radius = 6.dp.toPx(),
                                            center = Offset(x, y)
                                        )
                                        drawCircle(
                                            color = palette.accentStrong,
                                            radius = 4.dp.toPx(),
                                            center = Offset(x, y)
                                        )
                                    }
                                }
                            }
                            
                            // Y-axis labels
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("%.0f".format(maxY), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                                Text("%.0f".format(minY + (maxY - minY) / 2), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                                Text("%.0f".format(minY), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                            }
                            
                            // X-axis labels
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dataPoints.first().date.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                                Text(dataPoints.last().date.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                            }
                            
                            // Tooltip
                            tooltipData?.let { (offset, point) ->
                                val density = LocalDensity.current
                                val tooltipStart = with(density) {
                                    (offset.x - 40.dp.toPx()).coerceAtLeast(0f).toDp()
                                }
                                val tooltipTop = with(density) {
                                    (offset.y - 40.dp.toPx()).coerceAtLeast(0f).toDp()
                                }
                                val value = when (selectedMetric) {
                                    ProgressMetricV2.ESTIMATED_1RM -> point.estimatedOneRm
                                    ProgressMetricV2.WEIGHT -> point.maxWeight
                                    ProgressMetricV2.VOLUME -> point.totalVolume
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = tooltipStart, top = tooltipTop)
                                        .background(palette.pageBottom.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("%.0f".format(value), color = palette.ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text(point.date.format(DateTimeFormatter.ofPattern("MMM d")), color = palette.inkMuted, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Insight banner
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPanel(palette, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, palette.glassStroke),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (insightType) {
                                        InsightTypeV2.POSITIVE -> palette.complete
                                        InsightTypeV2.NEUTRAL -> palette.inkSubtle
                                        InsightTypeV2.NEGATIVE -> palette.danger
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = insightText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.ink
                        )
                    }
                }
            }

            // 2x2 stat grid
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(240.dp), // Fixed height to allow grid within LazyColumn
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        StatCard(
                            label = "Current PR",
                            value = "${currentPr.toInt()} lbs",
                            delta = formatProgressDeltaPercent(currentPrDeltaPercent),
                            deltaPositive = deltaDirection(currentPrDeltaPercent),
                            subLabel = prDate?.let { "Set ${it.format(DateTimeFormatter.ofPattern("MMM d"))}" }
                        )
                    }
                    item {
                        StatCard(
                            label = "Est. 1RM",
                            value = "${"%.1f".format(estimatedOneRm)} lbs",
                            delta = formatProgressDeltaPercent(estimatedOneRmDeltaPercent),
                            deltaPositive = deltaDirection(estimatedOneRmDeltaPercent),
                            subLabel = "vs previous period"
                        )
                    }
                    item {
                        StatCard(
                            label = "Total Volume (${selectedRange.label})",
                            value = formatVolumeLbs(totalVolume),
                            delta = formatProgressDeltaPercent(volumeDeltaPercent),
                            deltaPositive = deltaDirection(volumeDeltaPercent)
                        )
                    }
                    item {
                        StatCard(
                            label = "Workouts (${selectedRange.label})",
                            value = "$workoutCount",
                            delta = formatProgressDeltaPercent(workoutCountDeltaPercent),
                            deltaPositive = deltaDirection(workoutCountDeltaPercent)
                        )
                    }
                }
            }
        }
    }
}

internal fun formatProgressDeltaPercent(delta: Float): String? {
    if (abs(delta) < 0.05f) return null
    val sign = if (delta > 0f) "+" else ""
    return String.format(Locale.US, "%s%.1f%%", sign, delta)
}

private fun deltaDirection(delta: Float): Boolean? {
    return when {
        delta > 0.05f -> true
        delta < -0.05f -> false
        else -> null
    }
}

private fun formatVolumeLbs(volume: Float): String {
    return if (volume >= 1000f) {
        String.format(Locale.US, "%.1fk lbs", volume / 1000f)
    } else {
        "${volume.toInt()} lbs"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProgressScreen(
    exercises: List<ProgressExercise>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onExerciseClick: (ProgressExercise) -> Unit,
    organizationMode: ProgressOrganizationModeV2 = ProgressOrganizationModeV2.PROGRESS,
    splitPages: List<ProgressSplitPage> = emptyList(),
    selectedSplitIndex: Int = 0,
    onOrganizationModeChange: (ProgressOrganizationModeV2) -> Unit = {},
    onSelectedSplitIndexChange: (Int) -> Unit = {},
    onPreviousSplit: () -> Unit = {},
    onNextSplit: () -> Unit = {},
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ProgressOrganizationControl(
                selectedMode = organizationMode,
                onModeChange = onOrganizationModeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(58.dp),
                placeholder = {
                    Text(
                        "Search exercises...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkSubtle
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.inkMuted) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.ink,
                    fontWeight = FontWeight.SemiBold
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.glassFillStrong,
                    unfocusedContainerColor = palette.glassFill,
                    focusedBorderColor = palette.glassStrokeStrong,
                    unfocusedBorderColor = palette.glassStroke,
                    cursorColor = palette.accentStrong
                ),
                singleLine = true
            )

            when (organizationMode) {
                ProgressOrganizationModeV2.PROGRESS -> ProgressExerciseList(
                    exercises = exercises,
                    emptyText = "No exercises match this search",
                    onExerciseClick = onExerciseClick,
                    modifier = Modifier.fillMaxWidth()
                )
                ProgressOrganizationModeV2.SPLIT -> {
                    if (splitPages.isEmpty()) {
                        ProgressEmptyState(
                            text = if (searchQuery.isBlank()) {
                                "No saved split exercises yet"
                            } else {
                                "No split exercises match this search"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val pagerState = rememberPagerState(initialPage = selectedSplitIndex) { splitPages.size }

                        LaunchedEffect(selectedSplitIndex, splitPages.size) {
                            if (splitPages.isNotEmpty() && pagerState.currentPage != selectedSplitIndex) {
                                pagerState.animateScrollToPage(selectedSplitIndex)
                            }
                        }

                        LaunchedEffect(pagerState.currentPage) {
                            if (pagerState.currentPage != selectedSplitIndex) {
                                onSelectedSplitIndexChange(pagerState.currentPage)
                            }
                        }

                        SplitProgressHeader(
                            page = splitPages[selectedSplitIndex],
                            pageIndex = selectedSplitIndex,
                            pageCount = splitPages.size,
                            canGoPrevious = selectedSplitIndex > 0,
                            canGoNext = selectedSplitIndex < splitPages.lastIndex,
                            onPrevious = onPreviousSplit,
                            onNext = onNextSplit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { page ->
                            ProgressExerciseList(
                                exercises = splitPages[page].exercises,
                                emptyText = "No exercises in this split",
                                onExerciseClick = onExerciseClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressOrganizationControl(
    selectedMode: ProgressOrganizationModeV2,
    onModeChange: (ProgressOrganizationModeV2) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = modifier.glassPanel(palette, RoundedCornerShape(14.dp), strong = true),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, palette.glassStrokeStrong),
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ProgressOrganizationModeV2.values().forEach { mode ->
                val selected = mode == selectedMode
                TextButton(
                    onClick = { onModeChange(mode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = if (selected) palette.accentStrong.copy(alpha = 0.18f) else Color.Transparent,
                        contentColor = if (selected) palette.ink else palette.inkMuted
                    )
                ) {
                    Text(
                        text = when (mode) {
                            ProgressOrganizationModeV2.PROGRESS -> "Progress"
                            ProgressOrganizationModeV2.SPLIT -> "Splits"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitProgressHeader(
    page: ProgressSplitPage,
    pageIndex: Int,
    pageCount: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = modifier
            .glassPanel(palette, RoundedCornerShape(16.dp), strong = true)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious, enabled = canGoPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Previous split",
                tint = if (canGoPrevious) palette.ink else palette.inkSubtle.copy(alpha = 0.45f)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = page.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Split ${pageIndex + 1} of $pageCount",
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle,
                textAlign = TextAlign.Center
            )
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Next split",
                tint = if (canGoNext) palette.ink else palette.inkSubtle.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun ProgressExerciseList(
    exercises: List<ProgressExercise>,
    emptyText: String,
    onExerciseClick: (ProgressExercise) -> Unit,
    modifier: Modifier = Modifier
) {
    if (exercises.isEmpty()) {
        ProgressEmptyState(text = emptyText, modifier = modifier)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier
        ) {
            items(
                items = exercises,
                key = { it.id ?: it.name.hashCode().toLong() },
                contentType = { "progressExercise" }
            ) { exercise ->
                ProgressExerciseListItem(
                    exerciseName = exercise.name,
                    muscleGroups = exercise.muscleGroups,
                    lastSetLabel = exercise.lastSetLabel,
                    trendPercent = exercise.trendPercent,
                    onClick = { onExerciseClick(exercise) }
                )
            }
        }
    }
}

@Composable
private fun ProgressEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .glassPanel(palette, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProgressScreenPreview() {
    MaterialTheme {
        ProgressScreen(
            exercises = listOf(
                ProgressExercise("Bench Press", "Chest, Triceps", "Last: 185 × 10", 2.5f),
                ProgressExercise("Squat", "Legs", "Last: 225 × 5", -1.2f),
                ProgressExercise("Deadlift", "Back, Legs", "Last: 315 × 3", 0f),
                ProgressExercise("Overhead Press", "Shoulders", "Last: 135 × 8", 5.0f),
                ProgressExercise("Barbell Row", "Back", "Last: 185 × 8", -0.5f)
            ),
            searchQuery = "",
            onSearchChange = {},
            onExerciseClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProgressDetailScreenPreview() {
    MaterialTheme {
        val dataPoints = listOf(
            ExerciseDataPoint(LocalDate.now().minusDays(30), 200f, 150f, 1500f),
            ExerciseDataPoint(LocalDate.now().minusDays(24), 205f, 155f, 1550f),
            ExerciseDataPoint(LocalDate.now().minusDays(18), 210f, 160f, 1600f),
            ExerciseDataPoint(LocalDate.now().minusDays(12), 220f, 170f, 1700f),
            ExerciseDataPoint(LocalDate.now().minusDays(6), 230f, 180f, 1800f),
            ExerciseDataPoint(LocalDate.now(), 246f, 185f, 1850f)
        )
        ProgressDetailScreen(
            exerciseName = "Bench Press",
            muscleGroups = "Chest, Triceps",
            dataPoints = dataPoints,
            selectedRange = TimeRangeV2.ONE_MONTH,
            selectedMetric = ProgressMetricV2.ESTIMATED_1RM,
            insightText = "Trending upwards! Your estimated 1RM has increased by 15 lbs this month.",
            insightType = InsightTypeV2.POSITIVE,
            currentPr = 185f,
            currentPrDeltaPercent = 2.5f,
            prDate = LocalDate.now(),
            estimatedOneRm = 246f,
            estimatedOneRmDeltaPercent = 5f,
            totalVolume = 10000f,
            volumeDeltaPercent = 8f,
            workoutCount = 6,
            workoutCountDeltaPercent = 20f,
            onBack = {},
            onRangeChange = {},
            onMetricChange = {}
        )
    }
}
