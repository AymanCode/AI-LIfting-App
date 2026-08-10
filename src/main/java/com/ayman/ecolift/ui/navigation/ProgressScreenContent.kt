package com.ayman.ecolift.ui.navigation

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogType
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.viewmodel.ProgressMetric
import com.ayman.ecolift.ui.viewmodel.ProgressOrganizationMode
import com.ayman.ecolift.ui.viewmodel.TimeframeFilter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Short axis-chip caption for a timeframe, e.g. "3M". */
private val TimeframeFilter.label: String
    get() = when (this) {
        TimeframeFilter.ONE_MONTH -> "1M"
        TimeframeFilter.THREE_MONTHS -> "3M"
        TimeframeFilter.SIX_MONTHS -> "6M"
        TimeframeFilter.ONE_YEAR -> "1Y"
        TimeframeFilter.ALL_TIME -> "All"
    }

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
    val sessions: Int,
    val id: Long? = null
)

data class ProgressSplitPage(
    val id: Long,
    val name: String,
    val exercises: List<ProgressExercise>,
)

private val ProgressInk = Color(0xFFEEF6F2)
private val ProgressBackground = Color(0xFF0A100F)
private val ProgressDarkOnAccent = Color(0xFF06100F)

private fun mutedText(alpha: Float): Color = ProgressInk.copy(alpha = alpha)

private fun progressBodyStyle(
    fontSize: Int = 13,
    lineHeight: Int = fontSize + 4,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
    fontFamily = LogUiFontFamily,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

private fun progressNumberStyle(
    fontSize: Int,
    lineHeight: Int = fontSize + 2,
): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

private fun progressAsideStyle(fontSize: Int = 12): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Medium,
    fontSize = fontSize.sp,
    lineHeight = (fontSize + 3).sp,
    letterSpacing = 0.sp,
)

private fun progressAsymmetricShape(
    topStart: Int,
    topEnd: Int,
    bottomEnd: Int,
    bottomStart: Int,
): RoundedCornerShape = RoundedCornerShape(
    topStart = topStart.dp,
    topEnd = topEnd.dp,
    bottomEnd = bottomEnd.dp,
    bottomStart = bottomStart.dp,
)

@Composable
fun ProgressExerciseListItem(
    exerciseName: String,
    muscleGroups: String,
    lastSetLabel: String,
    trendPercent: Float,
    sessions: Int,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(14.dp),
    showDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val muscleGroupLabel = progressMuscleGroupLabel(muscleGroups)
    val needsMuscleGroup = progressNeedsMuscleGroup(muscleGroups)
    val trendLabel = progressTrendLabel(trendPercent)
    val isPositiveTrend = trendPercent > 0.05f
    val isNegativeTrend = trendPercent < -0.05f
    val trendColor = when {
        isPositiveTrend -> palette.complete
        isNegativeTrend -> palette.danger
        else -> palette.inkSubtle
    }
    // A row inside one grouped surface (see ProgressExerciseList) — no per-row card or border.
    // A shared translucent fill plus a hairline divider carry the grouping (#4/#6).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.glassFill)
            .clickable(onClick = onClick)
    ) {
        if (showDivider) {
            HorizontalDivider(
                thickness = Dp.Hairline,
                color = palette.glassStroke.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
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
                    text = muscleGroupLabel.uppercase(Locale.US),
                    style = detailSectionLabel(),
                    color = if (needsMuscleGroup) palette.accentStrong else palette.inkSubtle
                )
                Text(
                    text = lastSetLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(
                modifier = Modifier.width(92.dp),
                horizontalAlignment = Alignment.End
            ) {
                trendLabel?.let { label ->
                    if (isPositiveTrend) {
                        Icon(Icons.Default.TrendingUp, contentDescription = "Up", tint = palette.complete, modifier = Modifier.size(16.dp))
                    } else if (isNegativeTrend) {
                        Icon(Icons.Default.TrendingDown, contentDescription = "Down", tint = palette.danger, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = trendColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

internal fun progressNeedsMuscleGroup(muscleGroups: String): Boolean =
    muscleGroups.trim().isBlank()

internal fun progressMuscleGroupLabel(muscleGroups: String): String {
    val groups = muscleGroups
        .split("·")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (groups.isEmpty()) return "Identify muscle group"

    return groups.joinToString(" + ") { group ->
        group
            .lowercase(Locale.US)
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                }
            }
    }
}

internal fun progressTrendLabel(trendPercent: Float): String? = when {
    trendPercent > 0.05f -> "+${"%.1f".format(Locale.US, trendPercent)}%"
    trendPercent < -0.05f -> "%.1f".format(Locale.US, trendPercent) + "%"
    else -> null
}

@Composable
private fun ProgressMuscleGroupSelector(
    muscleGroups: String,
    onMuscleGroupChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalGlassPalette.current
    val accent = palette.accentStrong
    val selected = progressPrimaryMuscleGroup(muscleGroups)
    val needsMuscleGroup = progressNeedsMuscleGroup(muscleGroups)

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .height(32.dp)
                .sizeIn(maxWidth = 224.dp),
            shape = progressAsymmetricShape(13, 4, 15, 5),
            color = accent.copy(alpha = 0.08f),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (needsMuscleGroup) {
                    accent.copy(alpha = 0.55f)
                } else {
                    accent.copy(alpha = 0.45f)
                },
            ),
            contentColor = accent,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = progressMuscleGroupLabel(muscleGroups),
                    style = progressBodyStyle(fontSize = 12, lineHeight = 15, weight = FontWeight.Medium),
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (needsMuscleGroup) "Identify muscle group" else "Change muscle group",
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.sizeIn(minWidth = 208.dp, maxWidth = 232.dp, maxHeight = 360.dp),
            shape = progressAsymmetricShape(18, 8, 20, 7),
            containerColor = ProgressBackground.copy(alpha = 0.98f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.16f)),
        ) {
            ProgressMuscleGroupChoices.forEach { choice ->
                val isSelected = choice == selected
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(progressAsymmetricShape(11, 4, 13, 5))
                        .background(
                            if (isSelected) accent.copy(alpha = 0.12f) else Color.Transparent,
                        ),
                    text = {
                        Text(
                            text = progressMuscleGroupLabel(choice),
                            style = progressBodyStyle(fontSize = 12, lineHeight = 15, weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) ProgressInk else mutedText(0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(progressAsymmetricShape(7, 2, 8, 3))
                                .background(
                                    if (isSelected) accent else Color.White.copy(alpha = 0.04f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ProgressDarkOnAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onMuscleGroupChange(choice)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = mutedText(0.7f),
                        leadingIconColor = mutedText(0.7f),
                    ),
                )
            }
        }
    }
}

private val ProgressMuscleGroupChoices = listOf(
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

private fun progressPrimaryMuscleGroup(muscleGroups: String): String? =
    muscleGroups
        .split("·")
        .firstOrNull()
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it in ProgressMuscleGroupChoices }

@Composable
private fun detailSectionLabel() =
    progressBodyStyle(fontSize = 12, lineHeight = 15, weight = FontWeight.Medium)

/** Editorial text tab with a small animated accent underline — shared design language with the cardio tab. */
@Composable
private fun MetricTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val accent = palette.accentStrong
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else mutedText(0.5f),
        animationSpec = tween(180),
        label = "metric_tab_text",
    )
    val barColor by animateColorAsState(
        targetValue = if (selected) accent else Color.Transparent,
        animationSpec = tween(180),
        label = "metric_tab_bar",
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = progressBodyStyle(fontSize = 13, lineHeight = 16, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                WavyRule(color = barColor, modifier = Modifier.matchParentSize(), strokeWidth = 2.2f)
            }
        }
    }
}

/** Value-over-caps-label stat column; display font carries the weight instead of icons and rules. */
@Composable
private fun StatColumn(
    value: String,
    unit: String?,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val palette = LocalGlassPalette.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = LogType.completedSummary.copy(fontSize = 18.sp, lineHeight = 21.sp),
                color = if (emphasized) palette.accentStrong else palette.ink,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                )
            }
        }
        Text(label, style = detailSectionLabel(), color = palette.inkSubtle)
    }
}

private data class BestSetDisplay(
    val load: String,
    val unit: String,
    val reps: String?,
)

private fun bestSetDisplay(bestSetLabel: String, currentPr: Float): BestSetDisplay {
    val parts = bestSetLabel
        .takeIf { it.isNotBlank() }
        ?.split(Regex("\\s*[x×]\\s*"), limit = 2)
        ?: emptyList()
    val load = parts.getOrNull(0)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: formatWholeNumber(currentPr)
    val reps = parts.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "0" }
    val unit = if (load.any { it.isLetter() }) "" else "lbs"
    return BestSetDisplay(load = load, unit = unit, reps = reps)
}

private fun formatWholeNumber(value: Float): String =
    String.format(Locale.US, "%,.0f", value)

private fun metricLabel(metric: ProgressMetric): String =
    when (metric) {
        ProgressMetric.ESTIMATED_1RM -> "Est. 1RM"
        ProgressMetric.WEIGHT -> "Weight"
        ProgressMetric.VOLUME -> "Volume"
    }

private fun pastRangeText(range: TimeframeFilter): String =
    when (range) {
        TimeframeFilter.ONE_MONTH -> "past month"
        TimeframeFilter.THREE_MONTHS -> "past 3 months"
        TimeframeFilter.SIX_MONTHS -> "past 6 months"
        TimeframeFilter.ONE_YEAR -> "past year"
        TimeframeFilter.ALL_TIME -> "all time"
    }

private fun inPastRangeText(range: TimeframeFilter): String =
    when (range) {
        TimeframeFilter.ALL_TIME -> "all time"
        else -> "in the ${pastRangeText(range)}"
    }

private fun metricValue(point: ExerciseDataPoint, metric: ProgressMetric): Float =
    when (metric) {
        ProgressMetric.ESTIMATED_1RM -> point.estimatedOneRm
        ProgressMetric.WEIGHT -> point.maxWeight
        ProgressMetric.VOLUME -> point.totalVolume
    }

private fun formatMetricValue(value: Float, metric: ProgressMetric): String =
    when (metric) {
        ProgressMetric.VOLUME -> formatVolumeLbs(value)
        else -> "${formatWholeNumber(value)} lbs"
    }

private fun nextPaletteChoice(current: GlassPaletteChoice): GlassPaletteChoice {
    val choices = GlassPaletteChoice.entries
    val nextIndex = (choices.indexOf(current) + 1) % choices.size
    return choices[nextIndex]
}

private fun chartHeadline(
    dataPoints: List<ExerciseDataPoint>,
    selectedMetric: ProgressMetric,
): String {
    if (dataPoints.isEmpty()) return "Log sessions to build your chart"
    val latest = metricValue(dataPoints.last(), selectedMetric)
    if (dataPoints.size == 1) return "Latest ${metricLabel(selectedMetric).lowercase(Locale.US)} is ${formatMetricValue(latest, selectedMetric)}"

    val first = metricValue(dataPoints.first(), selectedMetric)
    val delta = latest - first
    val formatted = formatMetricValue(kotlin.math.abs(delta), selectedMetric)
    return when {
        kotlin.math.abs(delta) <= 0.5f -> "Holding steady at ${formatMetricValue(latest, selectedMetric)}"
        delta > 0f -> "${metricLabel(selectedMetric)} up $formatted"
        else -> "${metricLabel(selectedMetric)} down $formatted"
    }
}

private fun truckEquivalentLabel(totalVolume: Float): String {
    if (totalVolume <= 0f) return "log more sets for context"
    val equivalents = listOf(
        35_000f to ("semi truck" to "semi trucks"),
        25_000f to ("school bus" to "school buses"),
        9_000f to ("elephant" to "elephants"),
        4_000f to ("pickup truck" to "pickup trucks"),
    )
    val (threshold, labels) = equivalents.firstOrNull { totalVolume / it.first >= 1f }
        ?: equivalents.last()
    val count = (totalVolume / threshold).roundToInt().coerceAtLeast(1)
    return "about $count ${if (count == 1) labels.first else labels.second}"
}

@Composable
private fun WavyRule(
    color: Color,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(10.dp),
    strokeWidth: Float = 1.3f,
) {
    Canvas(modifier = modifier) {
        val middle = size.height / 2f
        val segment = 12.dp.toPx().coerceAtLeast(6f)
        val amplitude = (size.height * 0.32f).coerceAtLeast(1.5f)
        val path = Path().apply { moveTo(0f, middle) }
        var x = 0f
        var rise = true
        while (x < size.width) {
            val next = minOf(x + segment, size.width)
            val controlY = middle + if (rise) -amplitude else amplitude
            path.quadraticBezierTo((x + next) / 2f, controlY, next, middle)
            x = next
            rise = !rise
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun SketchIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = if (flipped) {
        progressAsymmetricShape(6, 15, 5, 17)
    } else {
        progressAsymmetricShape(14, 5, 16, 6)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.size(37.dp),
        shape = shape,
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private fun sketchPlaquePath(width: Float, height: Float): Path {
    val inset = 9f
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    val amplitude = 3f
    val wavelength = 16f
    val step = 4f
    val path = Path()

    fun wave(distance: Float): Float =
        sin((distance / wavelength) * 2f * PI).toFloat() * amplitude

    path.moveTo(left, top + wave(0f))

    var x = left
    while (x <= right) {
        path.lineTo(x, top + wave(x - left))
        x += step
    }

    var y = top
    while (y <= bottom) {
        path.lineTo(right + wave(y - top), y)
        y += step
    }

    x = right
    while (x >= left) {
        path.lineTo(x, bottom - wave(right - x))
        x -= step
    }

    y = bottom
    while (y >= top) {
        path.lineTo(left - wave(bottom - y), y)
        y -= step
    }

    path.close()
    return path
}

@Composable
private fun SketchPlaque(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val accent = palette.accentStrong
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val path = sketchPlaquePath(size.width, size.height)
            drawPath(path = path, color = accent.copy(alpha = 0.07f))
            drawPath(
                path = path,
                color = accent.copy(alpha = 0.55f),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            content()
        }
    }
}

@Composable
fun ProgressDetailScreen(
    exerciseName: String,
    muscleGroups: String,
    dataPoints: List<ExerciseDataPoint>,
    selectedRange: TimeframeFilter,
    selectedMetric: ProgressMetric,
    currentPr: Float,
    bestSetLabel: String,
    prDate: LocalDate?,
    estimatedOneRm: Float,
    totalVolume: Float,
    workoutCount: Int,
    onBack: () -> Unit,
    onRangeChange: (TimeframeFilter) -> Unit,
    onMetricChange: (ProgressMetric) -> Unit,
    onMuscleGroupChange: (String) -> Unit,
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val accent = palette.accentStrong
    val bestSet = bestSetDisplay(bestSetLabel, currentPr)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = ProgressBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .statusBarsPadding()
                .background(ProgressBackground)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(900f, -80f),
                        radius = 760f,
                    )
                ),
            contentPadding = PaddingValues(start = 14.dp, top = 20.dp, end = 14.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SketchIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = ProgressInk,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Text(
                        text = "Progress",
                        style = progressBodyStyle(fontSize = 12, lineHeight = 15),
                        color = mutedText(0.45f),
                    )
                    SketchIconButton(onClick = { onPaletteChoiceChange(nextPaletteChoice(paletteChoice)) }, flipped = true) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Change accent",
                            tint = ProgressInk,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = exerciseName,
                            style = progressBodyStyle(fontSize = 20, lineHeight = 23, weight = FontWeight.SemiBold),
                            color = ProgressInk,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ProgressMuscleGroupSelector(
                            muscleGroups = muscleGroups,
                            onMuscleGroupChange = onMuscleGroupChange,
                        )
                    }
                    WavyRule(
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .padding(top = 5.dp),
                    )
                }
            }

            item {
                SketchPlaque(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Your best set",
                                style = detailSectionLabel(),
                                color = mutedText(0.55f),
                            )
                            Text(
                                text = "PR",
                                style = progressBodyStyle(fontSize = 10, lineHeight = 13, weight = FontWeight.SemiBold),
                                fontWeight = FontWeight.Bold,
                                color = ProgressDarkOnAccent,
                                modifier = Modifier
                                    .clip(progressAsymmetricShape(8, 3, 9, 4))
                                    .background(accent)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = bestSet.load,
                                style = progressNumberStyle(
                                    fontSize = if (bestSet.load.length > 6) 38 else 56,
                                    lineHeight = 58,
                                ),
                                color = Color.White,
                                maxLines = 1,
                            )
                            Column(modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)) {
                                if (bestSet.unit.isNotBlank()) {
                                    Text(
                                        text = bestSet.unit,
                                        style = progressBodyStyle(fontSize = 14, lineHeight = 17),
                                        color = mutedText(0.6f),
                                    )
                                }
                                bestSet.reps?.let { reps ->
                                    Text(
                                        text = "for $reps reps",
                                        style = progressAsideStyle(fontSize = 16),
                                        color = accent,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = prDate?.let { "Unbeaten since ${it.format(DateTimeFormatter.ofPattern("MMM d"))}" } ?: "No PR date yet",
                                style = progressBodyStyle(fontSize = 13, lineHeight = 16),
                                color = mutedText(0.7f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "still the one to beat",
                                style = progressAsideStyle(fontSize = 12),
                                color = mutedText(0.45f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    ProgressMetric.values().forEach { metric ->
                        MetricTab(
                            label = metricLabel(metric),
                            selected = metric == selectedMetric,
                            onClick = { onMetricChange(metric) },
                        )
                    }
                }
            }

            item {
                SketchChartCard(
                    dataPoints = dataPoints,
                    selectedMetric = selectedMetric,
                    selectedRange = selectedRange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top,
                ) {
                    TimeframeFilter.values().forEach { range ->
                        val selected = range == selectedRange
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRangeChange(range) }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = range.label,
                                style = progressBodyStyle(fontSize = 12, lineHeight = 15, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else mutedText(0.45f),
                            )
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height(6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    WavyRule(
                                        color = accent,
                                        modifier = Modifier.matchParentSize(),
                                        strokeWidth = 2f,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Your numbers",
                        style = progressBodyStyle(fontSize = 12, lineHeight = 15),
                        color = mutedText(0.5f),
                    )
                    Text(
                        text = "swipe ->",
                        style = progressBodyStyle(fontSize = 12, lineHeight = 15),
                        color = mutedText(0.45f),
                    )
                }
            }

            item {
                StatsSnapRail(
                    totalVolume = totalVolume,
                    estimatedOneRm = estimatedOneRm,
                    workoutCount = workoutCount,
                    selectedRange = selectedRange,
                )
            }
        }
    }
}

@Composable
private fun SketchChartCard(
    dataPoints: List<ExerciseDataPoint>,
    selectedMetric: ProgressMetric,
    selectedRange: TimeframeFilter,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val accent = palette.accentStrong
    val shape = progressAsymmetricShape(20, 7, 22, 8)
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.White.copy(alpha = 0.025f),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.14f)),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(start = 10.dp, top = 13.dp, end = 10.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = chartHeadline(dataPoints, selectedMetric),
                    style = progressBodyStyle(fontSize = 13, lineHeight = 16),
                    color = mutedText(0.7f),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pastRangeText(selectedRange),
                    style = progressAsideStyle(fontSize = 12),
                    color = mutedText(0.45f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (dataPoints.size < 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.ShowChart,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "Not enough data yet",
                        style = progressBodyStyle(fontSize = 13, lineHeight = 16, weight = FontWeight.Medium),
                        color = ProgressInk,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = "Log one more session to draw the line",
                        style = progressBodyStyle(fontSize = 12, lineHeight = 15),
                        color = mutedText(0.55f),
                    )
                }
            } else {
                SketchProgressChart(
                    dataPoints = dataPoints,
                    selectedMetric = selectedMetric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        dataPoints.first().date.format(DateTimeFormatter.ofPattern("MMM d")),
                        style = progressBodyStyle(fontSize = 10, lineHeight = 13),
                        color = mutedText(0.55f),
                    )
                    Text(
                        dataPoints.last().date.format(DateTimeFormatter.ofPattern("MMM d")),
                        style = progressBodyStyle(fontSize = 10, lineHeight = 13),
                        color = mutedText(0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SketchProgressChart(
    dataPoints: List<ExerciseDataPoint>,
    selectedMetric: ProgressMetric,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val accent = palette.accentStrong
    val values = dataPoints.map { metricValue(it, selectedMetric) }
    val rawMinY = values.minOrNull() ?: 0f
    val rawMaxY = values.maxOrNull() ?: 1f
    val minY = if (rawMinY == rawMaxY) (rawMinY - 1f).coerceAtLeast(0f) else rawMinY * 0.95f
    val maxY = if (rawMinY == rawMaxY) rawMaxY + 1f else rawMaxY * 1.05f
    val yRange = (maxY - minY).coerceAtLeast(1f)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val plotLeft = 18.dp.toPx()
            val plotRight = size.width - 42.dp.toPx()
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val plotTop = 16.dp.toPx()
            val plotBottom = size.height - 28.dp.toPx()
            val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

            fun yFor(value: Float): Float {
                val ratio = (value - minY) / yRange
                return plotBottom - ratio * plotHeight
            }

            for (i in 0..2) {
                val y = plotTop + plotHeight * (i / 2f)
                val grid = Path().apply { moveTo(8.dp.toPx(), y) }
                var x = 8.dp.toPx()
                var rise = true
                val segment = 16.dp.toPx()
                while (x < plotRight) {
                    val next = minOf(x + segment, plotRight)
                    grid.quadraticBezierTo((x + next) / 2f, y + if (rise) -3f else 3f, next, y)
                    x = next
                    rise = !rise
                }
                drawPath(
                    path = grid,
                    color = Color.White.copy(alpha = 0.07f),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            val xStep = plotWidth / (dataPoints.size - 1).coerceAtLeast(1)
            val linePath = Path()
            val fillPath = Path()
            values.forEachIndexed { index, value ->
                val x = plotLeft + index * xStep
                val y = yFor(value)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, plotBottom)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(plotRight, plotBottom)
            fillPath.close()

            drawPath(path = fillPath, color = accent.copy(alpha = 0.08f))
            drawPath(
                path = linePath,
                color = accent,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )

            values.forEachIndexed { index, value ->
                val x = plotLeft + index * xStep
                val y = yFor(value)
                val mark = 4.5.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset(x - mark, y - mark),
                    end = Offset(x + mark, y + mark),
                    strokeWidth = 2.1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(x + mark, y - mark),
                    end = Offset(x - mark, y + mark),
                    strokeWidth = 2.1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val latestX = plotLeft + values.lastIndex * xStep
            val latestY = yFor(values.last())
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.White.toArgb()
                textSize = 13.dp.toPx()
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                formatWholeNumber(values.last()),
                latestX.coerceIn(plotLeft + 18.dp.toPx(), plotRight - 18.dp.toPx()),
                (latestY - 10.dp.toPx()).coerceAtLeast(12.dp.toPx()),
                labelPaint,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(104.dp)
                .padding(top = 10.dp, end = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text(formatWholeNumber(maxY), style = progressBodyStyle(fontSize = 10, lineHeight = 13), color = mutedText(0.35f))
            Text(formatWholeNumber(minY + (maxY - minY) / 2f), style = progressBodyStyle(fontSize = 10, lineHeight = 13), color = mutedText(0.35f))
            Text(formatWholeNumber(minY), style = progressBodyStyle(fontSize = 10, lineHeight = 13), color = mutedText(0.35f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatsSnapRail(
    totalVolume: Float,
    estimatedOneRm: Float,
    workoutCount: Int,
    selectedRange: TimeframeFilter,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val listState = rememberLazyListState()
        val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        val firstWidth = maxWidth * 0.76f
        val nextWidth = maxWidth * 0.60f

        LazyRow(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 14.dp),
        ) {
            item {
                SketchStatCard(
                    title = "Total weight you moved",
                    value = formatWholeNumber(totalVolume),
                    unit = "lbs",
                    aside = inPastRangeText(selectedRange),
                    chip = truckEquivalentLabel(totalVolume),
                    accent = true,
                    modifier = Modifier.width(firstWidth),
                )
            }
            item {
                SketchStatCard(
                    title = "Projected max",
                    value = formatWholeNumber(estimatedOneRm),
                    unit = "lbs",
                    aside = "an estimate, not a target",
                    modifier = Modifier.width(nextWidth),
                    flipped = true,
                )
            }
            item {
                SketchStatCard(
                    title = "Times trained",
                    value = "$workoutCount",
                    unit = if (workoutCount == 1) "session" else "sessions",
                    aside = inPastRangeText(selectedRange),
                    sessionSquares = workoutCount,
                    modifier = Modifier.width(nextWidth),
                )
            }
        }
    }
}

@Composable
private fun VolumeGlyph(
    color: Color,
    modifier: Modifier = Modifier.size(14.dp),
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.6.dp.toPx()
        val h = size.height
        val w = size.width
        val y = h * 0.62f
        drawLine(color, Offset(w * 0.12f, y), Offset(w * 0.66f, y), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.66f, y), Offset(w * 0.82f, h * 0.48f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, h * 0.36f), Offset(w * 0.58f, h * 0.36f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.58f, h * 0.36f), Offset(w * 0.66f, y), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawCircle(color.copy(alpha = 0.95f), radius = 1.9.dp.toPx(), center = Offset(w * 0.28f, h * 0.75f))
        drawCircle(color.copy(alpha = 0.95f), radius = 1.9.dp.toPx(), center = Offset(w * 0.68f, h * 0.75f))
    }
}

@Composable
private fun SessionSquares(count: Int, modifier: Modifier = Modifier) {
    val accent = LocalGlassPalette.current.accentStrong
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count.coerceAtMost(8)) { index ->
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(
                        if (index % 2 == 0) {
                            progressAsymmetricShape(3, 1, 4, 1)
                        } else {
                            progressAsymmetricShape(1, 4, 1, 3)
                        }
                    )
                    .background(accent),
            )
        }
        if (count > 8) {
            Text(
                text = "+${count - 8}",
                style = progressBodyStyle(fontSize = 10, lineHeight = 12, weight = FontWeight.Medium),
                color = mutedText(0.55f),
            )
        }
    }
}

@Composable
private fun SketchStatCard(
    title: String,
    value: String,
    unit: String,
    aside: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    flipped: Boolean = false,
    chip: String? = null,
    sessionSquares: Int? = null,
) {
    val palette = LocalGlassPalette.current
    val accentColor = palette.accentStrong
    val shape = if (flipped) {
        progressAsymmetricShape(9, 23, 8, 21)
    } else {
        progressAsymmetricShape(22, 8, 24, 9)
    }
    Surface(
        modifier = modifier.height(154.dp),
        shape = shape,
        color = if (accent) accentColor.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.035f),
        border = BorderStroke(
            1.5.dp,
            if (accent) accentColor.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.15f),
        ),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = progressBodyStyle(fontSize = 12, lineHeight = 15),
                color = mutedText(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = progressNumberStyle(
                        fontSize = if (value.length > 6) 28 else 32,
                        lineHeight = 33,
                    ),
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = unit,
                    style = progressBodyStyle(fontSize = 13, lineHeight = 16),
                    color = mutedText(0.55f),
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                sessionSquares?.let {
                    SessionSquares(
                        count = it,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    )
                }
            }
            Text(
                text = aside,
                style = progressAsideStyle(fontSize = 12),
                color = mutedText(0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chip?.let {
                val chipShape = progressAsymmetricShape(12, 4, 14, 5)
                Row(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .clip(chipShape)
                        .background(Color.White.copy(alpha = 0.035f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.15f), chipShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VolumeGlyph(color = accentColor)
                    Text(
                        text = it,
                        style = progressBodyStyle(fontSize = 12, lineHeight = 15),
                        color = mutedText(0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyProgressDetailScreenUnused(
    exerciseName: String,
    muscleGroups: String,
    dataPoints: List<ExerciseDataPoint>,
    selectedRange: TimeframeFilter,
    selectedMetric: ProgressMetric,
    currentPr: Float,
    bestSetLabel: String,
    prDate: LocalDate?,
    estimatedOneRm: Float,
    totalVolume: Float,
    workoutCount: Int,
    onBack: () -> Unit,
    onRangeChange: (TimeframeFilter) -> Unit,
    onMetricChange: (ProgressMetric) -> Unit,
    onMuscleGroupChange: (String) -> Unit,
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
                title = {},
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
            // Hero: exercise identity + current PR as the headline, no container
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text(
                        text = exerciseName,
                        style = LogType.exerciseTitle,
                        color = palette.ink,
                    )
                    ProgressMuscleGroupSelector(
                        muscleGroups = muscleGroups,
                        onMuscleGroupChange = onMuscleGroupChange,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            Text("BEST LOGGED SET", style = detailSectionLabel(), color = palette.inkSubtle)
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    text = bestSetLabel.ifBlank { "${currentPr.toInt()} lbs" },
                                    style = LogType.railValue.copy(fontSize = 44.sp, lineHeight = 46.sp),
                                    color = palette.ink,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        prDate?.let {
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(bottom = 5.dp),
                            ) {
                                Text(
                                    text = "Personal best",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.accentStrong,
                                )
                                Text(
                                    text = "logged ${it.format(DateTimeFormatter.ofPattern("MMM d"))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inkSubtle,
                                )
                            }
                        }
                    }
                }
            }

            // Metric switcher: editorial text tabs, not a boxed segmented control
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProgressMetric.values().forEach { metric ->
                        MetricTab(
                            label = when (metric) {
                                ProgressMetric.ESTIMATED_1RM -> "Est. 1RM"
                                ProgressMetric.WEIGHT -> "Weight"
                                ProgressMetric.VOLUME -> "Volume"
                            },
                            selected = metric == selectedMetric,
                            onClick = { onMetricChange(metric) },
                        )
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
                                    ProgressMetric.ESTIMATED_1RM -> dataPoints[0].estimatedOneRm
                                    ProgressMetric.WEIGHT -> dataPoints[0].maxWeight
                                    ProgressMetric.VOLUME -> dataPoints[0].totalVolume
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
                        val insight = progressChartInsight(dataPoints, selectedMetric, selectedRange)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            insight?.let {
                                Text(
                                    text = it,
                                    color = palette.inkMuted,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                            ) {
                            val values = dataPoints.map {
                                when (selectedMetric) {
                                    ProgressMetric.ESTIMATED_1RM -> it.estimatedOneRm
                                    ProgressMetric.WEIGHT -> it.maxWeight
                                    ProgressMetric.VOLUME -> it.totalVolume
                                }
                            }
                            val rawMinY = values.minOrNull() ?: 0f
                            val rawMaxY = values.maxOrNull() ?: 100f
                            val minY = if (rawMinY == rawMaxY) (rawMinY - 1f).coerceAtLeast(0f) else rawMinY * 0.95f
                            val maxY = if (rawMinY == rawMaxY) rawMaxY + 1f else rawMaxY * 1.05f
                            val yRange = (maxY - minY).coerceAtLeast(1f)

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Plot band: Y-axis labels (left gutter) + plot area
                                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    // Y-axis labels, aligned to the three gridlines
                                    Column(
                                        modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text("%.0f".format(maxY), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                                        Text("%.0f".format(minY + (maxY - minY) / 2), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                                        Text("%.0f".format(minY), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                                    }

                                    // Plot area
                                    Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
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

                                        // Tooltip (drawn on top of the plot)
                                        tooltipData?.let { (offset, point) ->
                                            val density = LocalDensity.current
                                            val tooltipStart = with(density) {
                                                (offset.x - 40.dp.toPx()).coerceAtLeast(0f).toDp()
                                            }
                                            val tooltipTop = with(density) {
                                                (offset.y - 40.dp.toPx()).coerceAtLeast(0f).toDp()
                                            }
                                            val value = when (selectedMetric) {
                                                ProgressMetric.ESTIMATED_1RM -> point.estimatedOneRm
                                                ProgressMetric.WEIGHT -> point.maxWeight
                                                ProgressMetric.VOLUME -> point.totalVolume
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

                                // X-axis labels, below the plot
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(dataPoints.first().date.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                                    Text(dataPoints.last().date.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle)
                                }
                            }
                            }
                        }
                    }
                }
            }

            // Range: quiet text controls tucked under the chart, right-aligned
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    TimeframeFilter.values().forEach { range ->
                        val selected = range == selectedRange
                        Text(
                            text = range.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) palette.accentStrong else palette.inkSubtle,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRangeChange(range) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Stat strip: three typographic columns — no boxes, no icon rows
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    StatColumn(
                        value = "%.1f".format(estimatedOneRm),
                        unit = "lbs",
                        label = "EST. 1RM",
                        emphasized = true,
                        modifier = Modifier.weight(1f),
                    )
                    StatColumn(
                        value = formatVolumeLbs(totalVolume).removeSuffix(" lbs"),
                        unit = "lbs",
                        label = "VOLUME · ${selectedRange.label.uppercase(Locale.US)}",
                        modifier = Modifier.weight(1f),
                    )
                    StatColumn(
                        value = "$workoutCount",
                        unit = null,
                        label = if (workoutCount == 1) "SESSION" else "SESSIONS",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

internal fun progressChartInsight(
    dataPoints: List<ExerciseDataPoint>,
    selectedMetric: ProgressMetric,
    selectedRange: TimeframeFilter,
): String? {
    if (dataPoints.size < 2) return null

    fun value(point: ExerciseDataPoint): Float = when (selectedMetric) {
        ProgressMetric.ESTIMATED_1RM -> point.estimatedOneRm
        ProgressMetric.WEIGHT -> point.maxWeight
        ProgressMetric.VOLUME -> point.totalVolume
    }

    val first = value(dataPoints.first())
    val latest = value(dataPoints.last())
    val delta = latest - first
    val metricName = when (selectedMetric) {
        ProgressMetric.ESTIMATED_1RM -> "Estimated 1RM"
        ProgressMetric.WEIGHT -> "Best weight"
        ProgressMetric.VOLUME -> "Volume"
    }
    val formattedDelta = when (selectedMetric) {
        ProgressMetric.VOLUME -> formatVolumeLbs(kotlin.math.abs(delta))
        else -> "${kotlin.math.abs(delta).toInt()} lbs"
    }
    val direction = when {
        delta > 0.5f -> "up"
        delta < -0.5f -> "down"
        else -> "unchanged"
    }

    return if (direction == "unchanged") {
        "$metricName unchanged over ${selectedRange.label}"
    } else {
        "$metricName $direction $formattedDelta over ${selectedRange.label}"
    }
}

private fun formatVolumeLbs(volume: Float): String {
    return "${formatWholeNumber(volume)} lbs"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProgressScreen(
    exercises: List<ProgressExercise>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onExerciseClick: (ProgressExercise) -> Unit,
    organizationMode: ProgressOrganizationMode = ProgressOrganizationMode.PROGRESS,
    splitPages: List<ProgressSplitPage> = emptyList(),
    selectedSplitIndex: Int = 0,
    onOrganizationModeChange: (ProgressOrganizationMode) -> Unit = {},
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
                    .padding(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = if (organizationMode == ProgressOrganizationMode.SPLIT) 0.dp else 8.dp
                    )
            )

            if (organizationMode == ProgressOrganizationMode.PROGRESS) {
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
            }

            when (organizationMode) {
                ProgressOrganizationMode.PROGRESS -> ProgressExerciseList(
                    exercises = exercises,
                    emptyText = "No exercises match this search",
                    onExerciseClick = onExerciseClick,
                    modifier = Modifier.fillMaxWidth()
                )
                ProgressOrganizationMode.SPLIT -> {
                    if (splitPages.isEmpty()) {
                        ProgressEmptyState(
                            text = "No saved split exercises yet",
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
    selectedMode: ProgressOrganizationMode,
    onModeChange: (ProgressOrganizationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = modifier.glassPanel(palette, RoundedCornerShape(14.dp), strong = true),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ProgressOrganizationMode.values().forEach { mode ->
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
                            ProgressOrganizationMode.PROGRESS -> "Progress"
                            ProgressOrganizationMode.SPLIT -> "Splits"
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
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = modifier
        ) {
            itemsIndexed(
                items = exercises,
                key = { _, exercise -> exercise.id ?: exercise.name.hashCode().toLong() },
                contentType = { _, _ -> "progressExercise" }
            ) { index, exercise ->
                val isFirst = index == 0
                val isLast = index == exercises.lastIndex
                // Only the ends of the group are rounded, so the rows read as one surface (#2/#4).
                val rowShape = when {
                    isFirst && isLast -> RoundedCornerShape(20.dp)
                    isFirst -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    isLast -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                    else -> RectangleShape
                }
                ProgressExerciseListItem(
                    exerciseName = exercise.name,
                    muscleGroups = exercise.muscleGroups,
                    lastSetLabel = exercise.lastSetLabel,
                    trendPercent = exercise.trendPercent,
                    sessions = exercise.sessions,
                    onClick = { onExerciseClick(exercise) },
                    shape = rowShape,
                    showDivider = !isFirst
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
                ProgressExercise("Bench Press", "Chest · Triceps", "Last: 185 × 10", 2.5f, sessions = 4),
                ProgressExercise("Squat", "Legs", "Last: 225 × 5", -1.2f, sessions = 3),
                ProgressExercise("Deadlift", "", "Last: 315 × 3", 0f, sessions = 1),
                ProgressExercise("Overhead Press", "Shoulders", "Last: 135 × 8", 5.0f, sessions = 4),
                ProgressExercise("Barbell Row", "Back", "Last: 185 × 8", -0.5f, sessions = 3)
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
            selectedRange = TimeframeFilter.ONE_MONTH,
            selectedMetric = ProgressMetric.ESTIMATED_1RM,
            currentPr = 185f,
            bestSetLabel = "185 x 5",
            prDate = LocalDate.now(),
            estimatedOneRm = 246f,
            totalVolume = 10000f,
            workoutCount = 6,
            onBack = {},
            onRangeChange = {},
            onMetricChange = {},
            onMuscleGroupChange = {},
        )
    }
}
