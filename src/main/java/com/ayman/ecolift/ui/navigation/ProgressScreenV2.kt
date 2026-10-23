package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.R
import com.ayman.ecolift.agent.assist.AnalysisSource
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.selectableBodyPartGroups
import com.ayman.ecolift.data.selectableBodyPartParts
import com.ayman.ecolift.ui.month.MonthSheetScreen
import com.ayman.ecolift.ui.theme.EspressoEyebrow
import com.ayman.ecolift.ui.theme.EspressoMuscleDot
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoRangeTrack
import com.ayman.ecolift.ui.theme.EspressoShape
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogType
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.QuietIcon
import com.ayman.ecolift.ui.theme.QuietIconName
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.viewmodel.AnalysisState
import com.ayman.ecolift.ui.viewmodel.ChangelogChapter
import com.ayman.ecolift.ui.viewmodel.ChangelogEvent
import com.ayman.ecolift.ui.viewmodel.ExerciseReport
import com.ayman.ecolift.ui.viewmodel.MonthViewModel
import com.ayman.ecolift.ui.viewmodel.Pace
import com.ayman.ecolift.ui.viewmodel.ProgressHeroUi
import com.ayman.ecolift.ui.viewmodel.TimeframeFilter
import com.ayman.ecolift.ui.viewmodel.TopSet
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.formatLoad
import com.ayman.ecolift.ui.viewmodel.formatTopSet
import com.ayman.ecolift.ui.viewmodel.progressWindowStart
import com.ayman.ecolift.ui.viewmodel.shortDate
import com.ayman.ecolift.ui.viewmodel.supportingSetLabel
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.formatFixed
import com.ayman.ecolift.web.util.formatWholeGrouped
import com.ayman.ecolift.web.util.noun
import com.ayman.ecolift.web.util.shareProgressRecap
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TimeRangeV2(val label: String) { TWO_WEEKS("2W"), ONE_MONTH("1M"), THREE_MONTHS("3M"), SIX_MONTHS("6M"), ONE_YEAR("1Y"), ALL("All") }
enum class ProgressMetricV2 { ESTIMATED_1RM, WEIGHT, VOLUME, REPS }
enum class ProgressOrganizationModeV2 { HIGHLIGHTS, PROGRESS, MONTH }

internal fun resolveProgressContentMode(
    requestedMode: ProgressOrganizationModeV2,
): ProgressOrganizationModeV2 = requestedMode

enum class ProgressSortModeV2 { TREND, RECENT, NAME, SESSIONS, BODY_PART }

data class ExerciseDataPoint(
    val date: LocalDate,
    val estimatedOneRm: Float,
    val maxWeight: Float,
    val totalVolume: Float,
    val maxReps: Float = 0f,
    val evidence: String = "",
    val estimateEvidence: String = "",
    val repEvidence: String = "",
    val volumeEvidence: String = "",
)

data class ProgressExercise(
    val name: String,
    val muscleGroups: String,
    val suggestedMuscleGroup: String? = null,
    val lastSetLabel: String,
    val trendPercent: Float,
    val rangeStatLabel: String? = null,
    val rangeStatSign: Int = 0,
    val rangeStatContext: String? = null,
    val firstSessionIsoDate: String? = null,
    val monthDeltaLabel: String? = null,
    val monthDeltaSign: Int = 0,
    val monthDeltaSubLabel: String? = null,
    val sessions: Int,
    val id: Long? = null,
    val lastSessionDate: String = "",
    val lastSessionIsoDate: String = "",
    val trend: List<Int> = emptyList(),
    val overview: com.ayman.ecolift.ui.viewmodel.QuietProgressOverview = com.ayman.ecolift.ui.viewmodel.QuietProgressOverview(),
    val report: com.ayman.ecolift.ui.viewmodel.ExerciseReport = com.ayman.ecolift.ui.viewmodel.ExerciseReport(),
    val isBodyweight: Boolean = false,
)

private val ProgressInk = EspressoPalette.ink
private val ProgressBackground = EspressoPalette.bg
private val ProgressDarkOnAccent = EspressoPalette.onColor

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
    monthDeltaLabel: String?,
    monthDeltaSign: Int,
    monthDeltaSubLabel: String?,
    sessions: Int,
    suggestedMuscleGroup: String? = null,
    onMuscleGroupChange: (String) -> Unit = {},
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(14.dp),
    showDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val trendLabel = monthDeltaLabel
    val isPositiveTrend = monthDeltaSign > 0
    val isNegativeTrend = monthDeltaSign < 0
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressMuscleGroupChip(
                    muscleGroups = muscleGroups,
                    suggestedMuscleGroup = suggestedMuscleGroup,
                    onMuscleGroupChange = onMuscleGroupChange,
                    modifier = Modifier.padding(top = 5.dp),
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
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = trendColor,
                        fontWeight = if (monthDeltaSign == 0) FontWeight.Medium else FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                    monthDeltaSubLabel?.let { subLabel ->
                        Text(
                            text = subLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkSubtle,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

internal fun progressNeedsMuscleGroup(muscleGroups: String): Boolean =
    selectableBodyPartParts(muscleGroups).isEmpty()

internal fun progressMuscleGroupLabel(muscleGroups: String): String {
    val groups = selectableBodyPartParts(muscleGroups)

    if (groups.isEmpty()) return "+ Tag muscle"

    return groups.joinToString(" + ") { group ->
        group
            .lowercase()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }
}

internal fun progressTrendLabel(trendPercent: Float): String? = when {
    trendPercent > 0.05f -> "+${formatFixed(trendPercent.toDouble(), 1)}%"
    trendPercent < -0.05f -> "${formatFixed(trendPercent.toDouble(), 1)}%"
    else -> null
}

@Composable
private fun ProgressMuscleGroupChip(
    muscleGroups: String,
    suggestedMuscleGroup: String?,
    onMuscleGroupChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalGlassPalette.current
    val selected = progressPrimaryMuscleGroup(muscleGroups)
    val needsMuscleGroup = progressNeedsMuscleGroup(muscleGroups)
    val suggestion = suggestedMuscleGroup?.takeIf { needsMuscleGroup }
    val label = when {
        suggestion != null -> "${progressMuscleGroupLabel(suggestion)}?"
        needsMuscleGroup -> "+ Tag muscle"
        else -> progressMuscleGroupLabel(muscleGroups)
    }
    val chipColor = if (needsMuscleGroup) palette.inkSubtle else palette.accentStrong
    val chipBorder = if (needsMuscleGroup) palette.inkSubtle.copy(alpha = 0.34f) else palette.accentStrong.copy(alpha = 0.45f)

    Box(modifier = modifier) {
        Surface(
            onClick = {
                if (suggestion != null) onMuscleGroupChange(suggestion) else expanded = true
            },
            modifier = Modifier
                .height(28.dp)
                .sizeIn(maxWidth = 220.dp),
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            border = BorderStroke(1.dp, chipBorder),
            contentColor = chipColor,
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = if (suggestion != null) 4.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = label,
                    style = progressBodyStyle(fontSize = 11, lineHeight = 14, weight = FontWeight.Medium),
                    color = chipColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (suggestion != null) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Choose muscle group",
                        tint = palette.inkSubtle,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { expanded = true }
                            .padding(3.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (needsMuscleGroup) "Tag muscle" else "Change muscle group",
                        tint = chipColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
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
                        .background(if (isSelected) palette.accentStrong.copy(alpha = 0.12f) else Color.Transparent),
                    text = {
                        Text(
                            text = progressMuscleGroupLabel(choice),
                            style = progressBodyStyle(
                                fontSize = 12,
                                lineHeight = 15,
                                weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
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
                                .background(if (isSelected) palette.accentStrong else Color.White.copy(alpha = 0.04f)),
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

@Composable
private fun ProgressMuscleGroupSelector(
    muscleGroups: String,
    suggestedMuscleGroup: String?,
    onMuscleGroupChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProgressMuscleGroupChip(
        muscleGroups = muscleGroups,
        suggestedMuscleGroup = suggestedMuscleGroup,
        onMuscleGroupChange = onMuscleGroupChange,
        modifier = modifier,
    )
}

private val ProgressMuscleGroupChoices = selectableBodyPartGroups

private fun progressPrimaryMuscleGroup(muscleGroups: String): String? =
    selectableBodyPartParts(muscleGroups).firstOrNull()

@Composable
private fun detailSectionLabel() =
    progressBodyStyle(fontSize = 12, lineHeight = 15, weight = FontWeight.Medium)

/** Espresso: a small pill; the active one sits on the quiet panel tone. */
@Composable
private fun MetricTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) EspressoPalette.ink else EspressoPalette.dim,
        animationSpec = tween(180),
        label = "metric_tab_text",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) EspressoPalette.panel2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = progressBodyStyle(fontSize = 12, lineHeight = 15, weight = if (selected) FontWeight.Medium else FontWeight.Normal),
        )
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

private fun formatWholeNumber(value: Float): String = formatWholeGrouped(value.toDouble())

private fun metricLabel(metric: ProgressMetricV2): String =
    when (metric) {
        ProgressMetricV2.ESTIMATED_1RM -> "Est. 1RM"
        ProgressMetricV2.WEIGHT -> "Top set"
        ProgressMetricV2.REPS -> "Reps"
        ProgressMetricV2.VOLUME -> "Volume"
    }

internal fun progressExplainerLines(
    bestSetLabel: String,
    currentPrLbs: Float,
    est1RmLbs: Float,
    totalVolumeLbs: Float,
    rangeText: String,
    equivalenceLabel: String?,
    isBodyweight: Boolean,
    userBodyweightLbs: Int?,
): List<String> {
    val lines = mutableListOf<String>()
    if (bestSetLabel.isNotBlank() && currentPrLbs > 0f) {
        lines += "Best set: $bestSetLabel"
    }
    if (isBodyweight) {
        lines += "Bodyweight progress uses working reps and recorded added load."
    } else if (est1RmLbs > 0f) {
        val est1Rm = formatWholeNumber(est1RmLbs)
        lines += "Est. 1RM $est1Rm lb"
    }
    if (totalVolumeLbs > 0f) {
        val totalVolume = formatWholeNumber(totalVolumeLbs)
        val equivalence = equivalenceLabel?.let { " — $it" }.orEmpty()
        lines += "Working volume: $totalVolume lb $rangeText"
    }
    return lines
}

private fun metricValue(point: ExerciseDataPoint, metric: ProgressMetricV2): Float =
    when (metric) {
        ProgressMetricV2.ESTIMATED_1RM -> point.estimatedOneRm
        ProgressMetricV2.WEIGHT -> point.maxWeight
        ProgressMetricV2.REPS -> point.maxReps
        ProgressMetricV2.VOLUME -> point.totalVolume
    }

internal fun formatMetricValue(value: Float, metric: ProgressMetricV2): String =
    when (metric) {
        ProgressMetricV2.WEIGHT -> "${com.ayman.ecolift.data.WeightLbs.formatStored((value * 10).roundToInt())} lb"
        ProgressMetricV2.REPS -> formatWholeNumber(value) + " reps"
        ProgressMetricV2.VOLUME -> formatVolumeLbs(value)
        else -> "${formatWholeNumber(value)} lb"
    }

internal fun progressDateFraction(date: LocalDate, start: LocalDate, end: LocalDate): Float =
    ((date.toEpochDays() - start.toEpochDays()).toFloat() / (end.toEpochDays() - start.toEpochDays()).coerceAtLeast(1)).coerceIn(0f, 1f)

@Composable
fun ProgressDetailScreen(
    exerciseName: String,
    muscleGroups: String,
    suggestedMuscleGroup: String?,
    dataPoints: List<ExerciseDataPoint>,
    windowStart: LocalDate = dataPoints.firstOrNull()?.date ?: LocalDate(2000, 1, 1),
    windowEnd: LocalDate = com.ayman.ecolift.web.util.todayLocalDate(),
    selectedRange: TimeRangeV2,
    selectedMetric: ProgressMetricV2,
    currentPr: Float,
    bestSetLabel: String,
    prDate: LocalDate?,
    estimatedOneRm: Float,
    totalVolume: Float,
    workoutCount: Int,
    isBodyweight: Boolean = false,
    userBodyweightLbs: Int? = null,
    report: ExerciseReport = ExerciseReport(),
    onBack: () -> Unit,
    onRangeChange: (TimeRangeV2) -> Unit,
    onMetricChange: (ProgressMetricV2) -> Unit,
    onMuscleGroupChange: (String) -> Unit,
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    modifier: Modifier = Modifier,
    assistEnabled: Boolean = false,
    analysis: AnalysisState = AnalysisState.Idle,
    onToggleAssist: () -> Unit = {},
    onAnalyze: () -> Unit = {},
    onClearAnalysis: () -> Unit = {},
    exerciseId: Long? = null,
    onOpenWorkout: (LocalDate) -> Unit = {},
    origin: ExerciseReportOrigin = ExerciseReportOrigin(),
) {
    // Espresso: the muscle colour owns the page (spec 2026-09-24-progress-report-path-design.md).
    val accent = rotationRegionColor(muscleGroups)
    var explainerOpen by rememberSaveable { mutableStateOf(false) }
    var aiOpen by rememberSaveable { mutableStateOf(false) }
    // Nothing is selected until a chart point is tapped; tapping it again closes its bubble. Stored as
    // the ISO date (rememberSaveable can't save LocalDate); a stale date simply matches no point.
    var selectedDateIso by rememberSaveable(exerciseId, windowStart.toString(), windowEnd.toString()) { mutableStateOf<String?>(null) }
    val selectedDate = dataPoints.firstOrNull { it.date.toString() == selectedDateIso }?.date
    // Older weights the user unfolded, keyed by the chapter's newest session date.
    val unfolded = rememberSaveable(exerciseId, windowStart.toString(), windowEnd.toString(), saver = ReportUnfoldedSaver) {
        mutableStateMapOf<String, Boolean>()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = ProgressBackground,
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(ProgressBackground)) {
            ReportMasthead(
                exerciseName = exerciseName,
                accent = accent,
                muscleGroups = muscleGroups,
                suggestedMuscleGroup = suggestedMuscleGroup,
                onMuscleGroupChange = onMuscleGroupChange,
                onBack = onBack,
                origin = origin,
                modifier = Modifier.statusBarsPadding(),
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                ReportRangeTrack(selected = selectedRange, onSelect = onRangeChange)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 140.dp),
            ) {
                item(key = "chart") {
                    ReportChartCard(
                        dataPoints = dataPoints,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        selectedRange = selectedRange,
                        onRangeChange = onRangeChange,
                        selectedMetric = selectedMetric,
                        onMetricChange = onMetricChange,
                        accent = accent,
                        report = report,
                        isBodyweight = isBodyweight,
                        selectedDate = selectedDate,
                        onTapPoint = { date -> selectedDateIso = if (selectedDateIso == date.toString()) null else date.toString() },
                        onDragPoint = { date -> selectedDateIso = date.toString() },
                        onOpenWorkout = onOpenWorkout,
                        explainerOpen = explainerOpen,
                        onToggleExplainer = {
                            if (selectedMetric != ProgressMetricV2.ESTIMATED_1RM) {
                                onMetricChange(ProgressMetricV2.ESTIMATED_1RM)
                                explainerOpen = true
                            } else {
                                explainerOpen = !explainerOpen
                            }
                        },
                    )
                }
                if (report.sessions >= 2) {
                    item(key = "score") { ReportScoreRow(report = report, modifier = Modifier.padding(top = 10.dp)) }
                }
                if (report.chapters.isNotEmpty()) {
                    item(key = "changelog") {
                        EspressoEyebrow("Changelog", color = EspressoPalette.ink, modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 12.dp))
                    }
                    report.nextStep?.let { next ->
                        item(key = "next-weight") {
                            Column {
                                NextWeightCard(loadStored = next.targetLoadStored, isBodyweight = isBodyweight, accent = accent)
                                ChapterConnector(kind = ConnectorKind.Ghost, accent = accent)
                            }
                        }
                    }
                    report.chapters.forEachIndexed { index, chapter ->
                        val key = chapter.events.first().date
                        // The weight you're on and the one before stay open, as does any short run.
                        val foldable = index >= 2 && chapter.events.size > 2
                        item(key = "chapter-$key") {
                            ReportChapter(
                                chapter = chapter,
                                current = index == 0,
                                folded = foldable && unfolded[key] != true,
                                foldable = foldable,
                                onToggleFold = { unfolded[key] = unfolded[key] != true },
                                suggestedReps = if (index == 0) report.suggestedReps else null,
                                isBodyweight = isBodyweight,
                                accent = accent,
                                onOpenWorkout = onOpenWorkout,
                            )
                        }
                        if (index < report.chapters.lastIndex) {
                            val heavier = chapter.loadStored > report.chapters[index + 1].loadStored
                            item(key = "connector-$key") {
                                ChapterConnector(kind = if (heavier) ConnectorKind.Up else ConnectorKind.Down, accent = accent)
                            }
                        }
                    }
                }
                item(key = "ask") {
                    AskIronMindButton(open = aiOpen, accent = accent, onClick = { aiOpen = !aiOpen }, modifier = Modifier.padding(top = 18.dp))
                }
                item(key = "ironmind") {
                    AnimatedVisibility(visible = aiOpen) {
                        ReportIronMindPanel(
                            assistEnabled = assistEnabled,
                            analysis = analysis,
                            accent = accent,
                            onToggleAssist = onToggleAssist,
                            onAnalyze = onAnalyze,
                            onClearAnalysis = onClearAnalysis,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A 1.5 dp dashed outline: in this report, dashed always means "not yet" (a suggestion or the next weight). */
private fun Modifier.dashedOutline(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius((radius.toPx() - stroke / 2f).coerceAtLeast(0f)),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

private val ReportUnfoldedSaver = listSaver<SnapshotStateMap<String, Boolean>, String>(
    save = { chapters -> chapters.filterValues { it }.keys.toList() },
    restore = { keys -> mutableStateMapOf<String, Boolean>().apply { keys.forEach { put(it, true) } } },
)

/** The Log band grows into the report header; the chip names the origin entry to pop back to. */
@Composable
private fun ReportMasthead(
    exerciseName: String,
    accent: Color,
    muscleGroups: String,
    suggestedMuscleGroup: String?,
    onMuscleGroupChange: (String) -> Unit,
    onBack: () -> Unit,
    origin: ExerciseReportOrigin,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(EspressoShape.masthead)
            .background(accent)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(EspressoPalette.onColor)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Back to ${origin.backLabel}"
                }
                .clickable(role = Role.Button, onClick = onBack)
                .sizeIn(minHeight = 44.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = EspressoPalette.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(origin.backLabel, style = EspressoType.chipBold.copy(fontSize = 13.sp, lineHeight = 15.sp), color = EspressoPalette.ink)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            origin.exerciseNumber?.takeIf { origin.tab == "log" }?.let { number ->
                Text("${rotationExercisePositionLabel(number)} ·", style = EspressoType.eyebrow, color = EspressoPalette.onColor.copy(alpha = .72f))
                Spacer(Modifier.width(4.dp))
            }
            ReportMuscleChip(
                muscleGroups = muscleGroups,
                suggestedMuscleGroup = suggestedMuscleGroup,
                accent = accent,
                onMuscleGroupChange = onMuscleGroupChange,
                onColorBand = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        ReportTitle(text = exerciseName, color = EspressoPalette.onColor)
    }
}

/** One line, stepping down to 24 sp; a name that still doesn't fit wraps to two lines rather than being clipped. */
@Composable
private fun ReportTitle(text: String, color: Color) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val base = EspressoType.mastTitleSmall
        val measurer = rememberTextMeasurer()
        val maxPx = constraints.maxWidth
        val fitted = remember(text, maxPx) {
            var size = base.fontSize.value
            var fits = false
            while (size >= 24f) {
                val width = measurer.measure(
                    text = text,
                    style = base.copy(fontSize = size.sp, lineHeight = (size * 1.02f).sp),
                    softWrap = false,
                    maxLines = 1,
                ).size.width
                if (width <= maxPx) { fits = true; break }
                size -= 2f
            }
            if (fits) size else null
        }
        if (fitted != null) {
            Text(text, style = base.copy(fontSize = fitted.sp, lineHeight = (fitted * 1.02f).sp), color = color, maxLines = 1, softWrap = false)
        } else {
            Text(text, style = base.copy(fontSize = 26.sp, lineHeight = 28.sp), color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The range as one track: every option sits in the container, the active one on panel2. */
@Composable
private fun ReportRangeTrack(selected: TimeRangeV2, onSelect: (TimeRangeV2) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TimeRangeV2.values().forEach { range ->
            val on = range == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) EspressoPalette.panel2 else Color.Transparent)
                    .semantics { role = Role.Tab; contentDescription = range.label }
                    .clickable(role = Role.Tab) { onSelect(range) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = range.label,
                    style = EspressoType.chip.copy(fontSize = 12.5.sp, lineHeight = 14.sp),
                    color = if (on) EspressoPalette.ink else EspressoPalette.dim,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * The muscle, as a chip above the title. Tagged: the muscle's tint and a caret. Untagged with a
 * guess: dashed "CHEST?" (tap accepts, × opens the list). Untagged: dashed "MUSCLE".
 */
@Composable
private fun ReportMuscleChip(
    muscleGroups: String,
    suggestedMuscleGroup: String?,
    accent: Color,
    onMuscleGroupChange: (String) -> Unit,
    onColorBand: Boolean = false,
) {
    val labelInk = if (onColorBand) EspressoPalette.onColor else EspressoPalette.ink
    var expanded by remember { mutableStateOf(false) }
    val needsMuscleGroup = progressNeedsMuscleGroup(muscleGroups)
    val suggestion = suggestedMuscleGroup?.takeIf { needsMuscleGroup }
    val label = when {
        suggestion != null -> "${progressMuscleGroupLabel(suggestion)}?"
        needsMuscleGroup -> "Muscle"
        else -> progressMuscleGroupLabel(muscleGroups)
    }
    val shape = RoundedCornerShape(13.dp)
    Box {
        Row(
            modifier = Modifier
                .height(30.dp)
                .sizeIn(maxWidth = 240.dp)
                .clip(shape)
                .then(if (needsMuscleGroup) Modifier.dashedOutline(labelInk.copy(alpha = .45f), 13.dp) else if (onColorBand) Modifier else Modifier.background(accent.copy(alpha = 0.17f)))
                .semantics {
                    role = Role.Button
                    contentDescription = when {
                        suggestion != null -> "Tag as ${progressMuscleGroupLabel(suggestion)}"
                        needsMuscleGroup -> "Tag muscle"
                        else -> "Muscle group $label"
                    }
                }
                .clickable(role = Role.Button) { if (suggestion != null) onMuscleGroupChange(suggestion) else expanded = true }
                .padding(start = 11.dp, end = if (suggestion != null) 4.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = EspressoType.eyebrow.copy(letterSpacing = 1.8.sp),
                color = labelInk.copy(alpha = .8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suggestion != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Choose muscle group",
                    tint = labelInk,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { expanded = true }
                        .padding(4.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (onColorBand) labelInk else if (needsMuscleGroup) EspressoPalette.dim else accent,
                    modifier = Modifier.padding(start = 5.dp).size(14.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.sizeIn(minWidth = 200.dp, maxWidth = 232.dp, maxHeight = 360.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = EspressoPalette.panel,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, EspressoPalette.hair2),
        ) {
            val selected = progressPrimaryMuscleGroup(muscleGroups)
            ProgressMuscleGroupChoices.forEach { choice ->
                val isSelected = choice == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = progressMuscleGroupLabel(choice),
                            style = EspressoType.body.copy(fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (isSelected) EspressoPalette.ink else EspressoPalette.dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { EspressoMuscleDot(color = rotationRegionColor(choice), size = 10) },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = EspressoPalette.ink, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onMuscleGroupChange(choice)
                    },
                )
            }
        }
    }
}

/** The chart comes first: metric track, the live pace as a chip, the chart with its tap bubble, and the ⓘ card. */
@Composable
private fun ReportChartCard(
    dataPoints: List<ExerciseDataPoint>,
    windowStart: LocalDate,
    windowEnd: LocalDate,
    selectedRange: TimeRangeV2,
    onRangeChange: (TimeRangeV2) -> Unit,
    selectedMetric: ProgressMetricV2,
    onMetricChange: (ProgressMetricV2) -> Unit,
    accent: Color,
    report: ExerciseReport,
    isBodyweight: Boolean,
    selectedDate: LocalDate?,
    onTapPoint: (LocalDate) -> Unit,
    onDragPoint: (LocalDate) -> Unit,
    onOpenWorkout: (LocalDate) -> Unit,
    explainerOpen: Boolean,
    onToggleExplainer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTrend = selectedMetric == ProgressMetricV2.ESTIMATED_1RM
    val pace = if (isTrend) livePace(report.pace, com.ayman.ecolift.web.util.todayLocalDate()) else null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(EspressoPalette.panel)
            .padding(12.dp),
    ) {
        ReportMetricTrack(
            isBodyweight = isBodyweight,
            selected = selectedMetric,
            onSelect = onMetricChange,
            explainerOpen = explainerOpen,
            onToggleExplainer = onToggleExplainer,
        )
        if (dataPoints.isEmpty()) {
            // No last session means the report is still loading (or nothing is logged up to the
            // period's end): keep the card's shape rather than claim there are no sessions.
            if (report.lastSessionDate == null) {
                Spacer(Modifier.fillMaxWidth().height(196.dp))
            } else {
                ReportEmptyRange(
                    selectedRange = selectedRange,
                    lastSessionDate = report.lastSessionDate,
                    windowEnd = windowEnd,
                    onRangeChange = onRangeChange,
                )
            }
            return@Column
        }
        if (pace != null) PaceChip(pace = pace, modifier = Modifier.align(Alignment.End).padding(top = 10.dp))
        val annotations = if (isTrend) {
            ChartAnnotations(
                steps = report.newHeaviest.map { LocalDate.parse(it.date) to it.loadStored },
                prDate = report.unbeatenSince?.let(LocalDate::parse),
                pace = pace,
            )
        } else {
            null
        }
        val events = report.events
        SketchProgressChart(
            dataPoints = dataPoints,
            windowStart = windowStart,
            windowEnd = windowEnd,
            selectedMetric = selectedMetric,
            accent = accent,
            isBodyweight = isBodyweight,
            selectedDate = selectedDate,
            onSelect = { onDragPoint(it.date) },
            onTap = { onTapPoint(it.date) },
            annotations = annotations,
            bubble = { point ->
                PointBubble(
                    date = point.date,
                    top = events.firstOrNull { it.date == point.date.toString() }?.to,
                    isBodyweight = isBodyweight,
                    accent = accent,
                    onOpen = { onOpenWorkout(point.date) },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(196.dp),
        )
        // The set behind the chart's latest Trend value, so the card and the line always agree.
        val estimateSet = report.latestE1rmSet
        val e1rm = report.latestE1rmLbs
        AnimatedVisibility(visible = explainerOpen && isTrend && estimateSet != null && e1rm != null) {
            if (estimateSet != null && e1rm != null) {
                EstOneRmCard(set = estimateSet, e1rm = e1rm, isBodyweight = isBodyweight, accent = accent, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

/** Metric tabs as one track inside the chart card; Trend carries the ⓘ. Bodyweight has no Trend, as before. */
@Composable
private fun ReportMetricTrack(
    isBodyweight: Boolean,
    selected: ProgressMetricV2,
    onSelect: (ProgressMetricV2) -> Unit,
    explainerOpen: Boolean,
    onToggleExplainer: () -> Unit,
) {
    val metrics = listOf(ProgressMetricV2.ESTIMATED_1RM, ProgressMetricV2.WEIGHT, ProgressMetricV2.REPS, ProgressMetricV2.VOLUME)
        .filter { !(isBodyweight && it == ProgressMetricV2.ESTIMATED_1RM) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(EspressoPalette.bg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        metrics.forEach { metric ->
            val on = metric == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) EspressoPalette.panel2 else Color.Transparent)
                    .semantics { role = Role.Tab; contentDescription = metricLabel(metric) }
                    .clickable(role = Role.Tab) { onSelect(metric) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metricLabel(metric),
                    style = EspressoType.chip.copy(fontSize = 12.sp, lineHeight = 14.sp),
                    color = if (on) EspressoPalette.ink else EspressoPalette.dim,
                    maxLines = 1,
                    softWrap = false,
                )
                if (metric == ProgressMetricV2.ESTIMATED_1RM) InfoDot(open = explainerOpen, onClick = onToggleExplainer)
            }
        }
    }
}

/** The live pace as data: a dotted mark like the chart's projection, the target and its date. */
@Composable
private fun PaceChip(pace: Pace, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(EspressoPalette.panel2)
            .semantics(mergeDescendants = true) { contentDescription = "Pace about ${pace.targetLbs} by ${shortDate(pace.date)}" }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(width = 14.dp, height = 2.dp)) {
            drawLine(
                color = EspressoPalette.ink.copy(alpha = 0.55f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
            )
        }
        Text("≈${pace.targetLbs}", style = reportData(11.5.sp), color = EspressoPalette.ink, modifier = Modifier.padding(start = 7.dp))
        Text(" · ", style = EspressoType.body.copy(fontSize = 11.5.sp), color = EspressoPalette.faint)
        Text(shortDate(pace.date), style = reportData(11.5.sp), color = EspressoPalette.dim)
    }
}

/** Clash semibold for a number, set or date (data is never plain). */
private fun reportData(size: TextUnit, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = weight, fontSize = size, lineHeight = (size.value + 2f).sp, letterSpacing = (-0.2).sp)

/** A tapped point: its date, top set and an Open button. The whole bubble opens that workout. */
@Composable
private fun PointBubble(date: LocalDate, top: TopSet?, isBodyweight: Boolean, accent: Color, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(EspressoPalette.panel2)
            .border(1.dp, EspressoPalette.hair2, shape)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Open workout ${shortDate(date.toString())}" }
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(shortDate(date.toString()), style = reportData(11.sp), color = EspressoPalette.dim, maxLines = 1, softWrap = false)
        if (top != null) {
            Text(
                formatTopSet(top, isBodyweight),
                style = reportData(13.sp, FontWeight.Bold),
                color = EspressoPalette.ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = EspressoPalette.onColor,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** ⓘ: est. 1RM shown as the user's own best set of the session becoming one rep. */
@Composable
private fun EstOneRmCard(set: TopSet, e1rm: Int, isBodyweight: Boolean, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EspressoPalette.panel2)
            .padding(14.dp),
    ) {
        EspressoEyebrow("Est. 1RM")
        Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTopSet(set, isBodyweight), style = reportData(22.sp, FontWeight.Bold).copy(letterSpacing = (-0.8).sp), color = EspressoPalette.ink)
            Text("≈", style = reportData(15.sp), color = EspressoPalette.faint, modifier = Modifier.padding(horizontal = 10.dp))
            Text("$e1rm × 1", style = reportData(22.sp, FontWeight.Bold).copy(letterSpacing = (-0.8).sp), color = accent)
        }
    }
}

/** The smallest established report suggestion reaching [date]; suggestions start at 1M. */
internal fun reportRangeContaining(date: LocalDate, windowEnd: LocalDate): TimeRangeV2 =
    TimeRangeV2.values().filterNot { it == TimeRangeV2.TWO_WEEKS }.first { range ->
        val filter = when (range) {
            TimeRangeV2.TWO_WEEKS -> TimeframeFilter.TWO_WEEKS
            TimeRangeV2.ONE_MONTH -> TimeframeFilter.ONE_MONTH
            TimeRangeV2.THREE_MONTHS -> TimeframeFilter.THREE_MONTHS
            TimeRangeV2.SIX_MONTHS -> TimeframeFilter.SIX_MONTHS
            TimeRangeV2.ONE_YEAR -> TimeframeFilter.ONE_YEAR
            TimeRangeV2.ALL -> TimeframeFilter.ALL_TIME
        }
        range == TimeRangeV2.ALL || date >= progressWindowStart(filter, windowEnd)
    }

/** Nothing in range: when the lift was last done, and the smallest range that shows it. */
@Composable
private fun ReportEmptyRange(
    selectedRange: TimeRangeV2,
    lastSessionDate: String,
    windowEnd: LocalDate,
    onRangeChange: (TimeRangeV2) -> Unit,
) {
    val jump = reportRangeContaining(LocalDate.parse(lastSessionDate), windowEnd).takeIf { it != selectedRange }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No sessions in ${selectedRange.label}",
            style = reportData(19.sp, FontWeight.Bold).copy(letterSpacing = (-0.5).sp),
            color = EspressoPalette.ink,
        )
        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            EspressoEyebrow("Last")
            Text(shortDate(lastSessionDate), style = reportData(14.sp), color = EspressoPalette.ink, modifier = Modifier.padding(start = 8.dp))
        }
        if (jump != null) {
            val shape = RoundedCornerShape(13.dp)
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .height(40.dp)
                    .clip(shape)
                    .background(EspressoPalette.panel2)
                    .border(1.dp, EspressoPalette.hair2, shape)
                    .semantics { role = Role.Button; contentDescription = "Show ${jump.label}" }
                    .clickable(role = Role.Button) { onRangeChange(jump) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Show ${jump.label}", style = EspressoType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = EspressoPalette.ink)
            }
        }
    }
}

/** Added, records and sets a week: the page's three numbers, each said once. Needs two sessions to compare. */
@Composable
private fun ReportScoreRow(report: ExerciseReport, modifier: Modifier = Modifier) {
    val top = report.topSet
    val first = report.firstTopSet
    val added: Triple<String, String, Boolean>? = if (top != null && first != null) {
        val lb = top.loadStored - first.loadStored
        if (lb != 0) {
            Triple((if (lb > 0) "+" else "−") + WeightLbs.formatStored(abs(lb)), "lb", lb > 0)
        } else {
            val reps = top.reps - first.reps
            Triple((if (reps > 0) "+" else if (reps < 0) "−" else "") + abs(reps), if (abs(reps) == 1) "rep" else "reps", reps > 0)
        }
    } else {
        null
    }
    val perWeek = if (report.setsPerWeek < 10f) formatFixed(report.setsPerWeek.toDouble(), 1) else "${report.setsPerWeek.roundToInt()}"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(20.dp))
            .background(EspressoPalette.panel),
    ) {
        ScoreCell(label = "Added", value = added?.first ?: "—", unit = added?.second, positive = added?.third == true, modifier = Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(EspressoPalette.hair))
        ScoreCell(label = "Records", value = "${report.records}", unit = null, positive = false, modifier = Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(EspressoPalette.hair))
        ScoreCell(label = "Sets", value = perWeek, unit = "/wk", positive = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScoreCell(label: String, value: String, unit: String?, positive: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = "$label $value${unit?.let { " $it" }.orEmpty()}" }
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
    ) {
        EspressoEyebrow(label)
        Row(modifier = Modifier.padding(top = 10.dp)) {
            Text(
                text = value,
                style = reportData(25.sp, FontWeight.Bold).copy(lineHeight = 26.sp, letterSpacing = (-1).sp),
                color = if (positive) EspressoPalette.up else EspressoPalette.ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = reportData(12.sp).copy(letterSpacing = 0.sp),
                    color = EspressoPalette.dim,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline().padding(start = 2.dp),
                )
            }
        }
    }
}

/** "80 lb", "BW", "BW +25 lb": the number big, the unit and the BW prefix small, all on one baseline. */
@Composable
private fun WeightLabel(loadStored: Int, isBodyweight: Boolean, size: TextUnit, color: Color, outlined: Boolean = false) {
    val stroke = with(LocalDensity.current) { 1.4.dp.toPx() }
    val big = TextStyle(
        fontFamily = LogDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size,
        letterSpacing = (-size.value * 0.045f).sp,
        drawStyle = if (outlined) Stroke(width = stroke) else null,
    )
    val small = reportData((size.value * 0.33f).coerceAtLeast(12f).sp).copy(letterSpacing = 0.sp)
    Row {
        when {
            isBodyweight && loadStored == 0 -> Text("BW", style = big, color = color, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
            else -> {
                if (isBodyweight) {
                    Text("BW", style = small, color = EspressoPalette.dim, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline().padding(end = 5.dp))
                }
                Text(
                    text = if (isBodyweight) "+${WeightLbs.formatStored(loadStored)}" else WeightLbs.formatStored(loadStored),
                    style = big,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                Text("lb", style = small, color = EspressoPalette.dim, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline().padding(start = 3.dp))
            }
        }
    }
}

/** The next weight, when the lift's own steps repeat: dashed, outlined numerals, a suggestion rather than a rule. */
@Composable
private fun NextWeightCard(loadStored: Int, isBodyweight: Boolean, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dashedOutline(accent.copy(alpha = 0.6f), 20.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Suggested next weight ${formatLoad(loadStored, isBodyweight)}${if (isBodyweight && loadStored == 0) "" else " lb"}"
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        WeightLabel(loadStored = loadStored, isBodyweight = isBodyweight, size = 32.sp, color = accent, outlined = true)
    }
}

private enum class ConnectorKind { Up, Down, Ghost }

/** The rail between weights: an up arrow in the muscle colour when the newer weight is heavier, down on panel2 when lighter. */
@Composable
private fun ChapterConnector(kind: ConnectorKind, accent: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.width(2.dp).fillMaxHeight()) {
            val x = size.width / 2f
            if (kind == ConnectorKind.Ghost) {
                drawLine(accent.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), strokeWidth = size.width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            } else {
                drawLine(EspressoPalette.hair2, Offset(x, 0f), Offset(x, size.height), strokeWidth = size.width)
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when (kind) {
                        ConnectorKind.Up -> accent
                        ConnectorKind.Down -> EspressoPalette.panel2
                        ConnectorKind.Ghost -> EspressoPalette.bg
                    },
                )
                .then(if (kind == ConnectorKind.Ghost) Modifier.dashedOutline(accent, 11.dp) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (kind == ConnectorKind.Down) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = when (kind) {
                    ConnectorKind.Up -> EspressoPalette.onColor
                    ConnectorKind.Down -> EspressoPalette.ink
                    ConnectorKind.Ghost -> accent
                },
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * One weight's run of sessions. The weight you're on is the big card with the Suggested row on top;
 * older runs are cards of rows, and long old runs fold to their best reps until tapped.
 */
@Composable
private fun ReportChapter(
    chapter: ChangelogChapter,
    current: Boolean,
    folded: Boolean,
    foldable: Boolean,
    onToggleFold: () -> Unit,
    suggestedReps: Int?,
    isBodyweight: Boolean,
    accent: Color,
    onOpenWorkout: (LocalDate) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val card = if (current) {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                drawRect(EspressoPalette.panel)
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.2f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.85f))
            }
            .border(1.5.dp, accent.copy(alpha = 0.55f), shape)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    } else {
        Modifier.fillMaxWidth().clip(shape).background(EspressoPalette.panel)
    }
    if (folded) {
        val best = chapter.events.maxOf { it.to.reps }
        Row(
            modifier = card
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "${formatLoad(chapter.loadStored, isBodyweight)}${if (isBodyweight && chapter.loadStored == 0) "" else " lb"}, best $best reps, show sessions"
                }
                .clickable(role = Role.Button, onClick = onToggleFold)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeightLabel(loadStored = chapter.loadStored, isBodyweight = isBodyweight, size = 24.sp, color = EspressoPalette.ink)
            Spacer(Modifier.weight(1f))
            Text("×", style = reportData(11.sp), color = EspressoPalette.dim)
            Text("$best", style = reportData(15.sp, FontWeight.Bold), color = EspressoPalette.dim)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = EspressoPalette.faint, modifier = Modifier.padding(start = 8.dp).size(16.dp))
        }
        return
    }
    Column(modifier = if (current) card else card.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (foldable) Modifier.clickable(role = Role.Button, onClick = onToggleFold) else Modifier)
                .padding(bottom = if (current) 12.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeightLabel(
                loadStored = chapter.loadStored,
                isBodyweight = isBodyweight,
                size = if (current) 42.sp else 24.sp,
                color = if (current) accent else EspressoPalette.ink,
            )
            if (foldable) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Fold", tint = EspressoPalette.faint, modifier = Modifier.size(16.dp))
            }
        }
        if (suggestedReps != null) SuggestedRow(reps = suggestedReps, accent = accent)
        chapter.events.forEachIndexed { index, event ->
            SessionRow(event = event, isBodyweight = isBodyweight, divider = index > 0, onOpen = { onOpenWorkout(LocalDate.parse(event.date)) })
        }
    }
}

/** The next rep PR at this weight, dashed: a suggestion, never the headline. */
@Composable
private fun SuggestedRow(reps: Int, accent: Color) {
    val stroke = with(LocalDensity.current) { 1.1.dp.toPx() }
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .dashedOutline(accent.copy(alpha = 0.6f), 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Suggested: $reps reps, one more than your best at this weight" }
            .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EspressoEyebrow("Suggested")
        Text(
            text = "$reps",
            style = reportData(15.sp, FontWeight.Bold).copy(drawStyle = Stroke(width = stroke)),
            color = accent,
            modifier = Modifier
                .padding(start = 10.dp)
                .dashedOutline(accent, 8.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "+1 rep",
            style = reportData(11.5.sp),
            color = EspressoPalette.ink,
            modifier = Modifier
                .dashedOutline(accent, 9.dp)
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

/**
 * Which pills fit in [available] px: all of them, or the top set's pill plus as many others (in set
 * order) as fit beside a "+N" pill of [plusWidth]. Returned in set order.
 */
internal fun fitPills(widths: List<Int>, topIndex: Int, available: Int, gap: Int, plusWidth: Int): List<Int> {
    if (widths.sum() + gap * (widths.size - 1).coerceAtLeast(0) <= available) return widths.indices.toList()
    val chosen = mutableListOf<Int>()
    var used = plusWidth
    if (topIndex in widths.indices) { chosen += topIndex; used += gap + widths[topIndex] }
    for (i in widths.indices) {
        if (i == topIndex) continue
        if (used + gap + widths[i] > available) break
        chosen += i
        used += gap + widths[i]
    }
    return chosen.sorted()
}

/** A session's sets as pills (top set bold), folding what doesn't fit the row into "+N" so no number is ever cut. */
@Composable
private fun SessionPills(event: ChangelogEvent, isBodyweight: Boolean, modifier: Modifier = Modifier) {
    val topIndex = event.sets.indexOf(event.to)
    SubcomposeLayout(modifier = modifier) { constraints ->
        val gap = 4.dp.roundToPx()
        val pills = subcompose("pills") {
            event.sets.forEachIndexed { i, set -> SetPill(set = set, top = event.to, isTop = i == topIndex, isBodyweight = isBodyweight) }
        }.map { it.measure(Constraints()) }
        val probe = subcompose("probe") { MorePill(event.sets.size) }.first().measure(Constraints())
        val shown = fitPills(pills.map { it.width }, topIndex, constraints.maxWidth, gap, probe.width)
        val hidden = event.sets.size - shown.size
        val more = if (hidden > 0) subcompose("more") { MorePill(hidden) }.first().measure(Constraints()) else null
        val height = (shown.map { pills[it].height } + listOfNotNull(more?.height)).maxOrNull() ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            shown.forEach { i ->
                pills[i].placeRelative(x, (height - pills[i].height) / 2)
                x += pills[i].width + gap
            }
            more?.placeRelative(x, (height - more.height) / 2)
        }
    }
}

@Composable
private fun SetPill(set: TopSet, top: TopSet, isTop: Boolean, isBodyweight: Boolean) {
    if (isTop) {
        Text(
            text = "${set.reps}",
            style = reportData(13.5.sp, FontWeight.Bold),
            color = EspressoPalette.ink,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(EspressoPalette.panel2).padding(horizontal = 9.dp, vertical = 5.dp),
        )
    } else {
        Text(
            text = supportingSetLabel(set, top, isBodyweight).replace(" × ", "×"),
            style = reportData(11.5.sp),
            color = EspressoPalette.dim,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.border(1.dp, EspressoPalette.hair2, RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun MorePill(count: Int) {
    Text("+$count", style = reportData(11.5.sp), color = EspressoPalette.faint, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 2.dp))
}

/** A session: its date, its sets in order (top set bold), a chip only if it beat something, and a way into the workout. */
@Composable
private fun SessionRow(event: ChangelogEvent, isBodyweight: Boolean, divider: Boolean, onOpen: () -> Unit) {
    val win = event.win
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(EspressoPalette.hair))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    // Every set is read out, including any the row folded into "+N".
                    contentDescription = "Open ${shortDate(event.date)}: ${formatTopSet(event.to, isBodyweight)}" + (win?.let { ", ${it.label}" } ?: "") +
                        "; sets " + event.sets.joinToString(", ") { formatTopSet(it, isBodyweight) }
                }
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shortDate(event.date), style = reportData(12.5.sp), color = EspressoPalette.dim, maxLines = 1, softWrap = false, modifier = Modifier.width(52.dp))
            SessionPills(event = event, isBodyweight = isBodyweight, modifier = Modifier.weight(1f))
            if (win != null) {
                Text(
                    text = win.label,
                    style = reportData(11.5.sp).copy(letterSpacing = (-0.1).sp),
                    color = EspressoPalette.onColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (win.isReps) EspressoPalette.up else EspressoPalette.ink)
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = EspressoPalette.faint,
                modifier = Modifier.padding(start = 6.dp).size(14.dp),
            )
        }
    }
}

@Composable
private fun AskIronMindButton(open: Boolean, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (open) EspressoPalette.panel2 else EspressoPalette.panel)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Ask IronMind" }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuietIcon(QuietIconName.Sparkles, null, Modifier.size(16.dp), accent)
        Text(
            text = "Ask IronMind",
            style = EspressoType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = EspressoPalette.ink,
            modifier = Modifier.padding(start = 9.dp),
        )
    }
}

/**
 * IronMind: "Analyze this lift" is the panel's one action, with the AI switch that arms it beside
 * it (off, the button stays in place, unlit); the answer below gets icon buttons. The button
 * above already says IronMind, so the panel carries no title of its own.
 */
@Composable
private fun ReportIronMindPanel(
    assistEnabled: Boolean,
    analysis: AnalysisState,
    accent: Color,
    onToggleAssist: () -> Unit,
    onAnalyze: () -> Unit,
    onClearAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(EspressoPalette.panel)
            .padding(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val loading = analysis is AnalysisState.Loading
            val fill by animateColorAsState(if (assistEnabled) accent else EspressoPalette.panel2, tween(180), label = "Analyze fill")
            val ink by animateColorAsState(if (assistEnabled) EspressoPalette.onColor else EspressoPalette.faint, tween(180), label = "Analyze ink")
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(fill)
                    .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Analyze this lift" }
                    .clickable(enabled = assistEnabled && !loading, role = Role.Button, onClick = onAnalyze),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = EspressoPalette.onColor, strokeWidth = 2.dp)
                } else {
                    QuietIcon(QuietIconName.Sparkles, null, Modifier.size(15.dp), ink)
                    Text(
                        text = "Analyze this lift",
                        style = EspressoType.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                        color = ink,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .semantics(mergeDescendants = true) {
                        role = Role.Switch
                        contentDescription = "AI assist"
                        stateDescription = if (assistEnabled) "On" else "Off"
                    }
                    .clickable(role = Role.Switch, onClick = onToggleAssist)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI", style = EspressoType.eyebrow, color = if (assistEnabled) EspressoPalette.ink else EspressoPalette.dim)
                val knob by animateDpAsState(if (assistEnabled) 19.dp else 3.dp, tween(160), label = "AI switch knob")
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(width = 40.dp, height = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (assistEnabled) accent else EspressoPalette.panel2)
                        .border(1.dp, if (assistEnabled) accent else EspressoPalette.hair2, RoundedCornerShape(12.dp)),
                ) {
                    Box(
                        Modifier
                            .offset(x = knob, y = 3.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (assistEnabled) EspressoPalette.onColor else EspressoPalette.dim),
                    )
                }
            }
        }
        (analysis as? AnalysisState.Ready)?.let { ready ->
            var collapsed by remember(ready) { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(EspressoPalette.panel2)
                    .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (ready.analysis.source == AnalysisSource.Groq) "AI" else "Local",
                        style = reportData(10.sp),
                        color = EspressoPalette.onColor,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(EspressoPalette.ink).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    PanelIconButton(Icons.Default.Refresh, "Run again", onAnalyze)
                    PanelIconButton(if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, if (collapsed) "Show answer" else "Hide answer") { collapsed = !collapsed }
                    PanelIconButton(Icons.Default.Close, "Dismiss answer", onClearAnalysis)
                }
                if (!collapsed) {
                    Text(
                        text = ready.analysis.text,
                        style = EspressoType.body.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = EspressoPalette.ink,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(EspressoPalette.bg)
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = EspressoPalette.ink, modifier = Modifier.size(15.dp))
    }
}

/** 16dp bordered "i" on the Trend tab; toggles the est. 1RM card under the chart. */
@Composable
private fun InfoDot(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 6.dp)
            .size(16.dp)
            .clip(CircleShape)
            .border(1.dp, EspressoPalette.hair2, CircleShape)
            .semantics { role = Role.Button; contentDescription = "What Est. 1RM means" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn, not typeset: the 9sp glyph sat about 2.5dp below the ring's centre.
        val color = if (open) EspressoPalette.ink else EspressoPalette.dim
        Canvas(Modifier.size(16.dp)) {
            val x = size.width / 2
            val stem = 1.3.dp.toPx()
            drawCircle(color, radius = 0.85.dp.toPx(), center = Offset(x, 4.9.dp.toPx()))
            drawLine(color, Offset(x, 7.dp.toPx()), Offset(x, 11.1.dp.toPx()), strokeWidth = stem, cap = StrokeCap.Round)
        }
    }
}

internal fun progressPointAtFraction(
    points: List<ExerciseDataPoint>, start: LocalDate, end: LocalDate, fraction: Float,
): ExerciseDataPoint? = points.minByOrNull {
    kotlin.math.abs(progressDateFraction(it.date, start, end) - fraction.coerceIn(0f, 1f))
}

/** Trend-tab-only chart decorations: new-heaviest-weight notches, the latest all-time-best flag, the pace ray. */
private data class ChartAnnotations(
    val steps: List<Pair<LocalDate, Int>>,
    val prDate: LocalDate?,
    val pace: Pace?,
)

internal fun progressAxisColumnWidth(widestLabel: Dp): Dp = 40.dp

@Composable
private fun SketchProgressChart(
    dataPoints: List<ExerciseDataPoint>,
    windowStart: LocalDate = dataPoints.firstOrNull()?.date ?: LocalDate(2000, 1, 1),
    windowEnd: LocalDate = com.ayman.ecolift.web.util.todayLocalDate(),
    selectedMetric: ProgressMetricV2,
    accent: Color,
    modifier: Modifier = Modifier,
    isBodyweight: Boolean = false,
    selectedDate: LocalDate? = null,
    onSelect: (ExerciseDataPoint) -> Unit = {},
    annotations: ChartAnnotations? = null,
    onTap: ((ExerciseDataPoint) -> Unit)? = null,
    bubble: (@Composable (ExerciseDataPoint) -> Unit)? = null,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTap by rememberUpdatedState(onTap)
    val values = dataPoints.map { metricValue(it, selectedMetric) }
    val yTicks = progressAxisTicks(values.minOrNull() ?: 0f, values.maxOrNull() ?: 1f)
    val minY = yTicks.first()
    val maxY = yTicks.last()
    val yRange = (maxY - minY).coerceAtLeast(1f)
    val connectPoints = dataPoints.size >= 2

    val plotTopInset = 16.dp
    val plotBottomInset = 22.dp

    val textMeasurer = rememberTextMeasurer()
    val axisStyle = progressBodyStyle(fontSize = 10, lineHeight = 13).copy(color = mutedText(0.35f))
    val tickLabels = yTicks.map { textMeasurer.measure(formatWholeNumber(it), style = axisStyle) }
    val axisColumn = with(LocalDensity.current) {
        progressAxisColumnWidth((tickLabels.maxOfOrNull { it.size.width } ?: 0).toDp())
    }
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier.matchParentSize()
                .pointerInput(dataPoints, windowStart, windowEnd) {
                    detectTapGestures { offset ->
                        progressPointAtFraction(dataPoints, windowStart, windowEnd,
                            (offset.x - 8.dp.toPx()) / (size.width - 48.dp.toPx()))?.let { point -> (currentOnTap ?: currentOnSelect)(point) }
                    }
                }
                .pointerInput(dataPoints, windowStart, windowEnd) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        progressPointAtFraction(dataPoints, windowStart, windowEnd,
                            (change.position.x - 8.dp.toPx()) / (size.width - 48.dp.toPx()))?.let { point -> currentOnSelect(point) }
                    }
                }
        ) {
            val plotLeft = 8.dp.toPx()
            val plotRight = size.width - axisColumn.toPx()
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val plotTop = plotTopInset.toPx()
            val plotBottom = size.height - plotBottomInset.toPx()
            val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

            fun yFor(value: Float): Float {
                val ratio = (value - minY) / yRange
                return plotBottom - ratio * plotHeight
            }
            fun xForDate(date: LocalDate) = plotLeft + progressDateFraction(date, windowStart, windowEnd) * plotWidth
            fun xFor(index: Int) = xForDate(dataPoints[index].date)

            // One gridline per round tick, its value right-aligned in the 40dp column beside the plot.
            yTicks.forEachIndexed { i, tick ->
                val y = yFor(tick)
                drawLine(
                    color = EspressoPalette.hair2.copy(alpha = if (i == 0) 0.8f else 0.45f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val tickLabel = tickLabels[i]
                drawText(tickLabel, topLeft = Offset(size.width - 2.dp.toPx() - tickLabel.size.width, y - tickLabel.size.height / 2f))
            }

            // Month ticks along the x axis.
            run {
                var cursor = LocalDate(windowStart.year, windowStart.month, 1)
                if (cursor < windowStart) cursor = cursor.plus(DatePeriod(months = 1))
                val monthDates = buildList {
                    var m = cursor
                    while (m <= windowEnd) {
                        add(m)
                        m = m.plus(DatePeriod(months = 1))
                    }
                }
                val startIsExtra = monthDates.firstOrNull() != windowStart
                val tickDates = if (startIsExtra) listOf(windowStart) + monthDates else monthDates
                val monthStyle = TextStyle(
                    color = EspressoPalette.faint,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SheetSansFontFamily,
                    letterSpacing = 1.2.sp,
                )
                val gap = 8.dp.toPx()
                val labels = tickDates.mapIndexed { idx, date ->
                    val x = xForDate(date)
                    val layout = textMeasurer.measure(MONTH_ABBREV[date.month.ordinal].uppercase(), style = monthStyle)
                    Triple(x, layout, if (idx == 0) x else (x - layout.size.width / 2f).coerceAtLeast(plotLeft))
                }
                var prevLabelEnd = Float.NEGATIVE_INFINITY
                labels.forEachIndexed { idx, (x, layout, left) ->
                    drawLine(EspressoPalette.hair2, Offset(x, plotBottom), Offset(x, plotBottom + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
                    // The window-start label gives way when it would crowd the first month name.
                    val next = labels.getOrNull(idx + 1)
                    val yields = idx == 0 && startIsExtra && next != null && left + layout.size.width + gap > next.third
                    if (!yields && left >= prevLabelEnd + gap) {
                        drawText(layout, topLeft = Offset(left, plotBottom + 6.dp.toPx()))
                        prevLabelEnd = left + layout.size.width
                    }
                }
            }

            // Trend-only: a short notch on the baseline for each new heaviest weight. Every notch
            // stays; its load label only when it clears the previous one.
            var lastStepLabelEnd = Float.NEGATIVE_INFINITY
            annotations?.steps?.sortedBy { it.first }?.forEach { (date, load) ->
                val x = xForDate(date)
                drawLine(
                    color = accent,
                    start = Offset(x, plotBottom),
                    end = Offset(x, plotBottom - 6.dp.toPx()),
                    strokeWidth = 1.4.dp.toPx(),
                )
                val stepLabel = textMeasurer.measure(
                    formatLoad(load, isBodyweight),
                    style = TextStyle(color = accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, fontFamily = LogDisplayFontFamily),
                )
                val labelLeft = x - stepLabel.size.width / 2f
                if (labelLeft >= lastStepLabelEnd + 5.dp.toPx()) {
                    drawText(stepLabel, topLeft = Offset(labelLeft, plotBottom - 8.dp.toPx() - stepLabel.size.height))
                    lastStepLabelEnd = labelLeft + stepLabel.size.width
                }
            }

            fun xForSelected(index: Int) = xFor(index)
            dataPoints.indexOfFirst { it.date == selectedDate }.takeIf { it >= 0 }?.let { index ->
                val x = xForSelected(index)
                drawLine(accent.copy(alpha = 0.4f), Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1.dp.toPx())
                drawCircle(accent, radius = 4.dp.toPx(), center = Offset(x, yFor(values[index])))
            }
            val linePath = Path()
            val fillPath = Path()
            values.forEachIndexed { index, value ->
                val x = xFor(index)
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
            fillPath.lineTo(xFor(dataPoints.lastIndex), plotBottom)
            fillPath.close()

            if (connectPoints) {
                drawPath(path = fillPath, color = accent.copy(alpha = 0.14f))
                drawPath(
                    path = linePath,
                    color = accent,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            values.forEachIndexed { index, value ->
                val x = xFor(index)
                val y = yFor(value)
                if (index == values.lastIndex) {
                    drawCircle(accent.copy(alpha = 0.25f), 8.dp.toPx(), Offset(x, y))
                    drawCircle(accent, 5.dp.toPx(), Offset(x, y))
                } else {
                    drawCircle(accent, (if (values.size > 30) 1.6.dp else 2.6.dp).toPx(), Offset(x, y))
                }
            }

            // Trend-only: latest all-time-best flag and the pace projection.
            annotations?.prDate?.let { prDate ->
                val idx = dataPoints.indexOfFirst { it.date == prDate }
                if (idx >= 0) {
                    val x = xFor(idx)
                    val y = yFor(values[idx])
                    drawLine(EspressoPalette.up, Offset(x, y - 7.dp.toPx()), Offset(x, y - 19.dp.toPx()), strokeWidth = 1.dp.toPx())
                    val flag = Path().apply {
                        moveTo(x, y - 19.dp.toPx())
                        lineTo(x + 9.dp.toPx(), y - 16.dp.toPx())
                        lineTo(x, y - 13.dp.toPx())
                        close()
                    }
                    drawPath(flag, color = EspressoPalette.up)
                }
            }
            annotations?.pace?.let { pace ->
                val lastDate = dataPoints.last().date
                val lastValue = values.last()
                val paceDate = LocalDate.parse(pace.date)
                val daysBetween = (paceDate.toEpochDays() - lastDate.toEpochDays()).toFloat()
                val slopePerDay = if (daysBetween != 0f) (pace.targetLbs - lastValue) / daysBetween else Float.NaN
                val daysToWindowEnd = (windowEnd.toEpochDays() - lastDate.toEpochDays()).toFloat()
                val projected = lastValue + slopePerDay * daysToWindowEnd
                if (slopePerDay.isFinite() && projected.isFinite()) {
                    drawLine(
                        color = EspressoPalette.ink.copy(alpha = 0.55f),
                        start = Offset(xFor(dataPoints.lastIndex), yFor(lastValue)),
                        end = Offset(plotRight, yFor(projected)),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f)),
                    )
                }
            }

            val latestX = xFor(values.lastIndex)
            val latestY = yFor(values.last())
            val label = textMeasurer.measure(
                formatMetricValue(values.last(), selectedMetric),
                style = TextStyle(
                    color = EspressoPalette.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LogDisplayFontFamily,
                ),
            )
            // Up and to the left of the latest point, so it never sits on the best-yet flag (which
            // points right); flips to the right only when there is no room on the left.
            val leftOfPoint = latestX - 8.dp.toPx() - label.size.width
            val labelX = if (leftOfPoint >= plotLeft) leftOfPoint else latestX + 10.dp.toPx()
            val labelBaselineY = (latestY - 10.dp.toPx()).coerceAtLeast(12.dp.toPx())
            drawText(
                textLayoutResult = label,
                topLeft = Offset(x = labelX, y = (labelBaselineY - label.size.height).coerceAtLeast(0f)),
            )
        }
        val bubbleIndex = if (bubble != null && selectedDate != null) dataPoints.indexOfFirst { it.date == selectedDate } else -1
        if (bubble != null && bubbleIndex >= 0) {
            val point = dataPoints[bubbleIndex]
            val value = values[bubbleIndex]
            Layout(content = { bubble(point) }, modifier = Modifier.matchParentSize()) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val placeable = measurables.first().measure(Constraints(maxWidth = width, maxHeight = height))
                val plotLeft = 8.dp.toPx()
                val plotWidth = (width - 40.dp.toPx() - plotLeft).coerceAtLeast(1f)
                val plotTop = plotTopInset.toPx()
                val plotBottom = height - plotBottomInset.toPx()
                val px = plotLeft + progressDateFraction(point.date, windowStart, windowEnd) * plotWidth
                val py = plotBottom - (value - minY) / yRange * (plotBottom - plotTop).coerceAtLeast(1f)
                val gap = 12.dp.toPx()
                val x = (px - placeable.width / 2f).roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                val above = (py - gap - placeable.height).roundToInt()
                val y = if (above >= 0) above else (py + gap).roundToInt().coerceAtMost((height - placeable.height).coerceAtLeast(0))
                layout(width, height) { placeable.place(x, y) }
            }
        }
    }
}

/** A projection whose date has already gone by is no longer a pace; drop it rather than show a past date. */
internal fun livePace(pace: Pace?, today: LocalDate): Pace? = pace?.takeIf { LocalDate.parse(it.date) >= today }

/**
 * Gridline values for the progress chart: at most four equal intervals of a round step
 * (1, 2, 2.5 or 5 × 10ⁿ) covering [min, max]. Spans under 20 are widened around their middle,
 * never below 0, so a flat history doesn't magnify a one-pound wobble.
 */
internal fun progressAxisTicks(min: Float, max: Float): List<Float> {
    var lo = min
    var hi = max
    if (hi - lo < 20f) {
        val mid = (lo + hi) / 2f
        lo = (mid - 10f).coerceAtLeast(0f)
        hi = lo + 20f
    }
    var magnitude = 1f
    while (true) {
        for (m in floatArrayOf(1f, 2f, 2.5f, 5f)) {
            val step = m * magnitude
            val first = kotlin.math.floor(lo / step).toInt()
            val last = kotlin.math.ceil(hi / step).toInt()
            if (last - first <= 4) return (first..last).map { it * step }
        }
        magnitude *= 10f
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

internal fun progressChartInsight(
    dataPoints: List<ExerciseDataPoint>,
    selectedMetric: ProgressMetricV2,
    selectedRange: TimeRangeV2,
): String? {
    if (dataPoints.size < 2) return null

    fun value(point: ExerciseDataPoint): Float = when (selectedMetric) {
        ProgressMetricV2.ESTIMATED_1RM -> point.estimatedOneRm
        ProgressMetricV2.WEIGHT -> point.maxWeight
        ProgressMetricV2.REPS -> point.maxReps
        ProgressMetricV2.VOLUME -> point.totalVolume
    }

    val first = value(dataPoints.first())
    val latest = value(dataPoints.last())
    val delta = latest - first
    val metricName = when (selectedMetric) {
        ProgressMetricV2.ESTIMATED_1RM -> "Est. 1RM"
        ProgressMetricV2.WEIGHT -> "Best weight"
        ProgressMetricV2.REPS -> "Reps"
        ProgressMetricV2.VOLUME -> "Working volume"
    }
    val formattedDelta = when (selectedMetric) {
        ProgressMetricV2.REPS -> formatWholeNumber(kotlin.math.abs(delta)) + " reps"
        ProgressMetricV2.VOLUME -> formatVolumeLbs(kotlin.math.abs(delta))
        else -> "${kotlin.math.abs(delta).toInt()} lb"
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
    return "${formatWholeNumber(volume)} lb"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProgressScreen(
    exercises: List<ProgressExercise>,
    isPending: Boolean = false,
    windowStart: LocalDate = com.ayman.ecolift.web.util.todayLocalDate(),
    windowEnd: LocalDate = com.ayman.ecolift.web.util.todayLocalDate(),
    onWindowEndChange: (LocalDate) -> Unit = {},
    selectedRange: TimeRangeV2 = TimeRangeV2.THREE_MONTHS,
    onRangeChange: (TimeRangeV2) -> Unit = {},
    hero: ProgressHeroUi? = null,
    heroSessionsThisMonthLabel: String? = null,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onExerciseClick: (ProgressExercise) -> Unit,
    onMuscleGroupChange: (Long, String) -> Unit = { _, _ -> },
    organizationMode: ProgressOrganizationModeV2 = ProgressOrganizationModeV2.PROGRESS,
    sortMode: ProgressSortModeV2 = ProgressSortModeV2.TREND,
    onSortModeChange: (ProgressSortModeV2) -> Unit = {},
    onOrganizationModeChange: (ProgressOrganizationModeV2) -> Unit = {},
    onOpenHighlights: () -> Unit = {},
    highlights: com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot = com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot(emptyList()),
    onOpenHighlightCard: (Int) -> Unit = {},
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    groupWall: com.ayman.ecolift.ui.viewmodel.GroupWallUi = com.ayman.ecolift.ui.viewmodel.GroupWallUi(),
    heldAvailable: Boolean = false,
    showAllLifts: Boolean = false,
    onShowAllLiftsChange: (Boolean) -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
    betweenPanels: (@Composable (index: Int) -> Unit)? = null,
    onOpenEmphasis: () -> Unit = {},
    onOpenMonthReport: (Long, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val activeOrganizationMode = resolveProgressContentMode(organizationMode)
    val quietStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    if (activeOrganizationMode == ProgressOrganizationModeV2.PROGRESS) {
        quietStateHolder.SaveableStateProvider("quiet-progress") {
            QuietProgressOverviewScreen(
                isPending = isPending,
                exercises = exercises, selectedRange = selectedRange, onRangeChange = onRangeChange,
                windowStart = windowStart, windowEnd = windowEnd, onWindowEndChange = onWindowEndChange,
                searchQuery = searchQuery, onSearchChange = onSearchChange,
                sortMode = sortMode, onSortModeChange = onSortModeChange,
                onExerciseClick = onExerciseClick, onOrganizationModeChange = onOrganizationModeChange,
                onOpenHighlights = onOpenHighlights, modifier = modifier,
                groupWall = groupWall, heldAvailable = heldAvailable,
                showAllLifts = showAllLifts, onShowAllLiftsChange = onShowAllLiftsChange,
                onOpenReport = onOpenReport, betweenPanels = betweenPanels,
                onOpenEmphasis = onOpenEmphasis,
            )
        }
        return
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { innerPadding ->
        BoxWithConstraints(Modifier.padding(innerPadding).fillMaxSize()) {
        // Same width rule as the Splits view, so the header never shifts when switching views at 320 dp.
        val compact = maxWidth < 359.dp
        Column(modifier = Modifier.fillMaxSize()) {
            // Every view draws the one Progress header; Month draws it in the sheet's theme. The masthead pads
            // the status bar itself, so on Month the sheet colour runs under the status bar too.
            val onMastSelect: (Int) -> Unit = { index ->
                when (index) {
                    0 -> onOpenHighlights()
                    1 -> onOrganizationModeChange(ProgressOrganizationModeV2.PROGRESS)
                    2 -> onOrganizationModeChange(ProgressOrganizationModeV2.MONTH)
                }
            }
            val month = activeOrganizationMode == ProgressOrganizationModeV2.MONTH
            ProgressMasthead(selectedIndex = if (month) 2 else 0, onSelect = onMastSelect, compact = compact, sheet = month)

            if (activeOrganizationMode == ProgressOrganizationModeV2.PROGRESS) {
                hero?.let {
                    ProgressFactStrip(
                        hero = it,
                        onClick = onOpenHighlights,
                    )
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(58.dp),
                    placeholder = {
                        Text(
                            "Search exercises…",
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
                ProgressSortControl(
                    selectedMode = sortMode,
                    onModeChange = onSortModeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }

            if (activeOrganizationMode == ProgressOrganizationModeV2.PROGRESS) {
                EspressoRangeTrack(
                    TimeRangeV2.entries, selectedRange, { it.label }, onRangeChange,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fill = true,
                )
            }

            when (activeOrganizationMode) {
                ProgressOrganizationModeV2.PROGRESS -> ProgressExerciseList(
                    exercises = exercises,
                    emptyText = "No exercises match this search",
                    onExerciseClick = onExerciseClick,
                    onMuscleGroupChange = onMuscleGroupChange,
                    modifier = Modifier.fillMaxWidth(),
                    showSessions = sortMode == ProgressSortModeV2.SESSIONS,
                    resetScrollKey = sortMode,
                )
                ProgressOrganizationModeV2.HIGHLIGHTS -> com.ayman.ecolift.ui.highlights.ProgressHighlightsStack(
                    snapshot = highlights,
                    onOpenCard = onOpenHighlightCard,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                ProgressOrganizationModeV2.MONTH -> {
                    val monthRepos = LocalRepos.current
                    val monthViewModel: MonthViewModel = viewModel { MonthViewModel(monthRepos) }
                    val monthState by monthViewModel.uiState.collectAsState()
                    MonthSheetScreen(
                        state = monthState,
                        onStepMonth = monthViewModel::stepMonth,
                        onShare = { text -> shareProgressRecap(text) {} },
                        onOpenLift = { id -> onOpenMonthReport(id, monthState.yearMonth.atEndOfMonth().toString()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ProgressHeroCard(
    hero: ProgressHeroUi,
    shareStatus: String?,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val truckCount = remember(hero.equivalenceLabel) {
        Regex("\\d+").find(hero.equivalenceLabel)?.value.orEmpty()
    }
    val equivalence = hero.equivalenceLabel
        .removePrefix("about ")
        .removePrefix("About ")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(548.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.pageTop),
        ) {
            val heroWidth = maxWidth
            Image(
                painter = painterResource(R.drawable.progress_truck_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomEnd,
                colorFilter = ColorFilter.tint(
                    color = palette.accent,
                    blendMode = BlendMode.Color,
                ),
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to palette.scrim.copy(alpha = 0.92f),
                            0.42f to palette.pageTop.copy(alpha = 0.42f),
                            0.72f to palette.pageBottom.copy(alpha = 0.14f),
                            1f to palette.scrim.copy(alpha = 0.96f),
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            0f to palette.scrim.copy(alpha = 0.94f),
                            0.58f to palette.pageTop.copy(alpha = 0.45f),
                            1f to Color.Transparent,
                        )
                    ),
            )
            if (truckCount.isNotBlank()) {
                Text(
                    text = truckCount,
                    style = progressNumberStyle(
                        fontSize = if (heroWidth < 380.dp) 104 else 126,
                        lineHeight = if (heroWidth < 380.dp) 108 else 130,
                    ),
                    color = palette.complete.copy(alpha = 0.20f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 18.dp),
                )
            }
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "YOUR TRAINING REEL",
                    style = progressBodyStyle(fontSize = 11, lineHeight = 14, weight = FontWeight.Bold),
                    color = palette.complete,
                )
                Text(
                    text = "The work so far.",
                    style = progressBodyStyle(
                        fontSize = if (heroWidth < 360.dp) 28 else 31,
                        lineHeight = if (heroWidth < 360.dp) 33 else 36,
                        weight = FontWeight.SemiBold,
                    ),
                    color = palette.ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "You kept showing up.\nEvery finished set now adds up to",
                    style = progressBodyStyle(fontSize = 14, lineHeight = 20),
                    color = palette.inkMuted,
                    modifier = Modifier
                        .widthIn(max = 242.dp)
                        .padding(top = 16.dp),
                )
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = if (hero.lifetimeVolumeLabel == "0") "${hero.totalRepCount}" else hero.lifetimeVolumeLabel,
                        style = progressNumberStyle(
                            fontSize = when {
                                heroWidth < 340.dp -> 40
                                hero.lifetimeVolumeLabel.length > 9 -> 46
                                else -> 53
                            },
                            lineHeight = if (heroWidth < 340.dp) 44 else 56,
                        ),
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (hero.lifetimeVolumeLabel == "0") "working reps" else "lb · working volume",
                        style = progressBodyStyle(fontSize = 14, lineHeight = 18, weight = FontWeight.Medium),
                        color = palette.inkMuted,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = palette.complete,
                    )
                    Text(
                        text = "About $equivalence",
                        style = progressBodyStyle(fontSize = 14, lineHeight = 18, weight = FontWeight.Medium),
                        color = palette.complete,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                MilestoneLoadTrack(
                    progress = hero.milestoneProgress,
                    label = hero.milestoneLabel,
                )
                Surface(
                    onClick = onShare,
                    modifier = Modifier
                        .padding(top = 17.dp)
                        .sizeIn(minHeight = 48.dp, minWidth = 168.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = palette.complete,
                    contentColor = palette.pageTop,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = shareStatus ?: "Share recap",
                            style = progressBodyStyle(fontSize = 14, lineHeight = 18, weight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneLoadTrack(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val normalizedProgress = progress.coerceIn(0f, 1f)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(12) { index ->
                val segmentEnd = (index + 1) / 12f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (normalizedProgress >= segmentEnd) {
                                palette.complete
                            } else {
                                Color.White.copy(alpha = 0.09f)
                            }
                        ),
                )
            }
        }
        Text(
            text = label,
            style = progressBodyStyle(fontSize = 11, lineHeight = 14, weight = FontWeight.Medium),
            color = palette.inkSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressHeroStatColumn(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = progressBodyStyle(fontSize = 18, lineHeight = 21, weight = FontWeight.SemiBold),
            color = palette.ink,
            maxLines = 1,
        )
        Text(
            text = caption,
            style = progressBodyStyle(fontSize = 12, lineHeight = 15),
            color = palette.inkMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProgressSortControl(
    selectedMode: ProgressSortModeV2,
    onModeChange: (ProgressSortModeV2) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Sort",
            style = progressBodyStyle(fontSize = 12, lineHeight = 15),
            color = palette.inkSubtle,
            modifier = Modifier.padding(end = 2.dp),
        )
        ProgressSortModeV2.values().forEach { mode ->
            val selected = mode == selectedMode
            Surface(
                onClick = { onModeChange(mode) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) palette.accentStrong.copy(alpha = 0.18f) else palette.glassFill,
                border = BorderStroke(
                    1.dp,
                    if (selected) palette.glassStrokeStrong else palette.glassStroke,
                ),
                contentColor = if (selected) palette.ink else palette.inkMuted,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = when (mode) {
                        ProgressSortModeV2.TREND -> "Trend"
                        ProgressSortModeV2.RECENT -> "Recent"
                        ProgressSortModeV2.NAME -> "Name"
                        ProgressSortModeV2.SESSIONS -> "Sessions"
                        ProgressSortModeV2.BODY_PART -> "Muscle"
                    },
                    style = progressBodyStyle(
                        fontSize = 12,
                        lineHeight = 15,
                        weight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressFactStrip(
    hero: ProgressHeroUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = palette.glassFill,
        border = BorderStroke(1.dp, palette.glassStroke),
        contentColor = palette.ink,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            VolumeGlyph(color = palette.complete, modifier = Modifier.size(14.dp))
            Text(
                text = if (hero.lifetimeVolumeLabel == "0") "${countLabel(hero.totalRepCount, "rep")}" else "${hero.lifetimeVolumeLabel} lb",
                style = progressNumberStyle(fontSize = 15, lineHeight = 18),
                color = palette.ink,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "All time · ${hero.equivalenceLabel}",
                style = progressBodyStyle(fontSize = 12, lineHeight = 15),
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Open highlights",
                modifier = Modifier.size(18.dp),
                tint = palette.inkMuted,
            )
        }
    }
}

@Composable
private fun ProgressTimelineEntry(
    exercise: ProgressExercise,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(292.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressTimelineRail(
            exercise = exercise,
            isFirst = isFirst,
            isLast = isLast,
            modifier = Modifier
                .width(67.dp)
                .fillMaxHeight(),
        )
        ProgressTimelineWinCard(
            exercise = exercise,
            useBars = index % 2 == 1,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun ProgressTimelineRail(
    exercise: ProgressExercise,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val date = highlightDate(exercise)
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val x = size.width - 8.dp.toPx()
            val nodeY = 27.dp.toPx()
            val strokeWidth = 1.dp.toPx()
            if (!isFirst) {
                drawLine(
                    color = palette.complete.copy(alpha = 0.72f),
                    start = Offset(x, 0f),
                    end = Offset(x, nodeY),
                    strokeWidth = strokeWidth,
                )
            }
            if (!isLast) {
                drawLine(
                    color = palette.complete.copy(alpha = 0.72f),
                    start = Offset(x, nodeY),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth,
                )
            }
            drawCircle(
                color = ProgressBackground,
                radius = 8.dp.toPx(),
                center = Offset(x, nodeY),
            )
            drawCircle(
                color = palette.complete,
                radius = 7.dp.toPx(),
                center = Offset(x, nodeY),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawCircle(
                color = palette.complete.copy(alpha = 0.7f),
                radius = 3.dp.toPx(),
                center = Offset(x, nodeY),
            )
        }
        Column(
            modifier = Modifier.width(46.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = date.monthDay,
                style = progressBodyStyle(fontSize = 11, lineHeight = 14, weight = FontWeight.Bold),
                color = palette.complete,
                maxLines = 1,
            )
            Text(
                text = date.year,
                style = progressBodyStyle(fontSize = 10, lineHeight = 13, weight = FontWeight.Medium),
                color = palette.complete.copy(alpha = 0.84f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProgressTimelineWinCard(
    exercise: ProgressExercise,
    useBars: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = palette.complete.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, palette.complete.copy(alpha = 0.26f)),
        contentColor = palette.ink,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "NEW BEST",
                        style = progressBodyStyle(fontSize = 10, lineHeight = 13, weight = FontWeight.Bold),
                        color = palette.complete,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, palette.glassStroke),
                    ) {
                        Text(
                            text = exercise.lastSetLabel,
                            style = progressBodyStyle(fontSize = 10, lineHeight = 13, weight = FontWeight.Medium),
                            color = palette.inkMuted,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = exercise.name,
                    style = progressBodyStyle(fontSize = 18, lineHeight = 22, weight = FontWeight.SemiBold),
                    color = palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Text(
                    text = highlightDeltaHeadline(exercise),
                    style = progressNumberStyle(fontSize = 27, lineHeight = 31),
                    color = palette.complete,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Latest: ${exercise.lastSetLabel}",
                    style = progressBodyStyle(fontSize = 11, lineHeight = 14),
                    color = palette.inkMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 5.dp),
                )
                ProgressWinEvidenceChart(
                    values = exercise.trend,
                    useBars = useBars,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(top = 7.dp),
                )
                Text(
                    text = progressWinSentence(exercise),
                    style = progressBodyStyle(fontSize = 11, lineHeight = 15),
                    color = palette.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            HorizontalDivider(
                thickness = Dp.Hairline,
                color = palette.complete.copy(alpha = 0.22f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(43.dp)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Open progress",
                    style = progressBodyStyle(fontSize = 12, lineHeight = 15, weight = FontWeight.SemiBold),
                    color = palette.ink,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = palette.complete,
                )
            }
        }
    }
}

@Composable
private fun ProgressWinEvidenceChart(
    values: List<Int>,
    useBars: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val points = values.takeLast(8)
    Canvas(modifier = modifier) {
        repeat(3) { index ->
            val y = size.height * index / 2f
            drawLine(
                color = palette.glassStroke.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        if (points.isEmpty()) return@Canvas
        val low = points.minOrNull() ?: 0
        val high = points.maxOrNull() ?: low
        val range = (high - low).coerceAtLeast(1).toFloat()
        if (useBars) {
            val gap = 4.dp.toPx()
            val barWidth = ((size.width - gap * (points.size - 1)) / points.size).coerceAtLeast(2f)
            points.forEachIndexed { index, value ->
                val normalized = (value - low) / range
                val barHeight = size.height * (0.30f + normalized * 0.70f)
                drawRoundRect(
                    color = if (index == points.lastIndex) palette.complete else palette.inkSubtle.copy(alpha = 0.34f),
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }
        } else {
            val path = Path()
            val spacing = if (points.size == 1) 0f else size.width / (points.size - 1)
            points.forEachIndexed { index, value ->
                val normalized = (value - low) / range
                val x = if (points.size == 1) size.width else index * spacing
                val y = size.height - (size.height * (0.16f + normalized * 0.68f))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = palette.complete,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            points.forEachIndexed { index, value ->
                val normalized = (value - low) / range
                val x = if (points.size == 1) size.width else index * spacing
                val y = size.height - (size.height * (0.16f + normalized * 0.68f))
                drawCircle(
                    color = palette.complete,
                    radius = if (index == points.lastIndex) 4.dp.toPx() else 2.5.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }
    }
}

@Composable
private fun ProgressRecapStats(
    hero: ProgressHeroUi,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = palette.glassFillStrong,
        border = BorderStroke(1.dp, palette.glassStroke),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stats = listOf(
                formatWholeGrouped(hero.sessionCount.toDouble()) to "sessions",
                formatWholeGrouped(hero.completedSetCount.toDouble()) to "sets",
                formatWholeGrouped(hero.totalRepCount.toDouble()) to "reps",
                formatWholeGrouped(hero.cyclesDone.toDouble()) to "cycles",
            )
            stats.forEachIndexed { index, stat ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stat.first,
                        style = progressBodyStyle(fontSize = 17, lineHeight = 20, weight = FontWeight.SemiBold),
                        color = palette.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = stat.second,
                        style = progressBodyStyle(fontSize = 10, lineHeight = 13),
                        color = palette.inkMuted,
                        maxLines = 1,
                    )
                }
                if (index < stats.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(Dp.Hairline)
                            .height(40.dp)
                            .background(palette.glassStroke),
                    )
                }
            }
        }
    }
}

private data class ProgressHighlightDate(
    val monthDay: String,
    val year: String,
)

private fun highlightEditMonth(
    sessionsThisMonthLabel: String?,
    wins: List<ProgressExercise>,
): String {
    val sessionMonth = sessionsThisMonthLabel
        ?.takeIf { " in " in it }
        ?.substringAfterLast(" in ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    if (sessionMonth != null) return sessionMonth

    val latestDate = wins
        .mapNotNull { exercise ->
            exercise.lastSessionIsoDate
                .takeIf { it.isNotBlank() }
                ?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
        }
        .maxOrNull()
    return latestDate?.let { MONTH_ABBREV[it.monthNumber - 1] } ?: "recent"
}

private fun highlightDate(exercise: ProgressExercise): ProgressHighlightDate {
    val parsed = exercise.lastSessionIsoDate
        .takeIf { it.isNotBlank() }
        ?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
    return if (parsed != null) {
        ProgressHighlightDate(
            monthDay = "${MONTH_ABBREV[parsed.monthNumber - 1].uppercase()} ${parsed.dayOfMonth}",
            year = parsed.year.toString(),
        )
    } else {
        ProgressHighlightDate(
            monthDay = exercise.lastSessionDate.ifBlank { "RECENT" }.uppercase(),
            year = "",
        )
    }
}

private fun highlightDeltaHeadline(exercise: ProgressExercise): String {
    val delta = exercise.monthDeltaLabel.orEmpty()
    val context = exercise.monthDeltaSubLabel.orEmpty()
    return when {
        context.startsWith("at ") -> "$delta $context"
        delta.contains("lb") -> "$delta this month"
        else -> delta
    }
}

@Composable
private fun ProgressWinsEmpty(modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = palette.glassFill,
        border = BorderStroke(1.dp, palette.glassStroke),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Your next win is being built",
                style = progressBodyStyle(fontSize = 15, lineHeight = 19, weight = FontWeight.SemiBold),
                color = palette.ink,
            )
            Text(
                text = "When a lift gains weight or reps, its card will land here automatically.",
                style = progressBodyStyle(fontSize = 12, lineHeight = 17),
                color = palette.inkMuted,
            )
        }
    }
}

private fun progressWinSentence(exercise: ProgressExercise): String {
    val delta = exercise.monthDeltaLabel.orEmpty().removePrefix("+")
    val context = exercise.monthDeltaSubLabel.orEmpty()
    return when {
        delta.contains("lb") -> "More load. Same standard. The work is showing."
        delta.contains("rep") && context.startsWith("at ") -> "You held the weight and found another gear."
        delta.contains("rep") -> "More clean reps than before."
        else -> "The number moved because you kept showing up."
    }
}

private fun buildProgressShareText(
    hero: ProgressHeroUi,
    sessionsThisMonthLabel: String?,
    wins: List<ProgressExercise>,
): String {
    val leadWin = wins.firstOrNull()?.let { " Lead win: ${it.name} ${it.monthDeltaLabel}." }.orEmpty()
    val month = sessionsThisMonthLabel?.let { " $it." }.orEmpty()
    return "My EcoLift recap: ${hero.lifetimeVolumeLabel} lb moved across " +
        "${countLabel(hero.sessionCount, "session")}, ${countLabel(hero.completedSetCount, "completed set")}, and " +
        "${formatWholeGrouped(hero.totalRepCount.toDouble())} ${noun(hero.totalRepCount, "rep")}.${month}$leadWin"
}

@Composable
private fun ProgressExerciseList(
    exercises: List<ProgressExercise>,
    emptyText: String,
    onExerciseClick: (ProgressExercise) -> Unit,
    onMuscleGroupChange: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    showSessions: Boolean = false,
    resetScrollKey: Any? = null,
) {
    if (exercises.isEmpty()) {
        ProgressEmptyState(text = emptyText, modifier = modifier)
    } else {
        val listState = rememberLazyListState()
        var previousResetScrollKey by rememberSaveable { mutableStateOf(resetScrollKey?.toString()) }
        LaunchedEffect(resetScrollKey) {
            if (previousResetScrollKey != resetScrollKey?.toString()) {
                previousResetScrollKey = resetScrollKey?.toString()
                listState.scrollToItem(0)
            }
        }
        LazyColumn(
            state = listState,
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
                val sessionsLabel = if (exercise.sessions == 1) "1 session" else "${exercise.sessions} sessions"
                ProgressExerciseListItem(
                    exerciseName = exercise.name,
                    muscleGroups = exercise.muscleGroups,
                    lastSetLabel = if (showSessions) {
                        listOf(exercise.lastSetLabel, sessionsLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    } else {
                        exercise.lastSetLabel
                    },
                    trendPercent = exercise.trendPercent,
                    monthDeltaLabel = exercise.rangeStatLabel,
                    monthDeltaSign = exercise.rangeStatSign,
                    monthDeltaSubLabel = exercise.rangeStatContext,
                    sessions = exercise.sessions,
                    suggestedMuscleGroup = exercise.suggestedMuscleGroup,
                    onMuscleGroupChange = { group ->
                        exercise.id?.let { onMuscleGroupChange(it, group) }
                    },
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
