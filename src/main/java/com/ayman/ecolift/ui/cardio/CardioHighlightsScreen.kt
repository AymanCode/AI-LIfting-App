package com.ayman.ecolift.ui.cardio

import com.ayman.ecolift.web.util.countLabel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayman.ecolift.cardio.*
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.ui.theme.DawnPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.web.util.DAY_ABBREV
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val MONTH_NAMES_FULL = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

internal fun formatRecordKindLabel(kind: RecordKind, machine: CardioActivityType?): String = when (kind) {
    RecordKind.LONGEST_SESSION -> "Longest session"
    RecordKind.FARTHEST -> if (machine == CardioActivityType.TREADMILL || machine == CardioActivityType.RUN) "Farthest run" else "Farthest"
    RecordKind.FASTEST_MILE -> "Fastest mile"
    RecordKind.BEST_SPLIT_500 -> "Best 500m split"
    RecordKind.MOST_FLOORS -> "Most floors"
    RecordKind.HIGHEST_POWER -> "Best avg power"
    RecordKind.BIGGEST_WEEK -> "Biggest week"
    RecordKind.BIGGEST_MONTH -> "Biggest month"
}

private fun formatRecordValue(kind: RecordKind, machine: CardioActivityType?, value: Double): Pair<String, String> = when (kind) {
    RecordKind.LONGEST_SESSION -> Pair(formatDuration(value.toInt()), "")
    RecordKind.FARTHEST -> {
        if (machine == CardioActivityType.ROW || machine == CardioActivityType.SWIM) {
            Pair(formatThousands(value.roundToInt()), "m")
        } else {
            val miles = (value / 1609.344 * 100).roundToInt() / 100.0
            Pair(miles.toString(), "mi")
        }
    }
    RecordKind.FASTEST_MILE -> {
        val totalSec = value.roundToInt()
        val m = totalSec / 60
        val s = totalSec % 60
        Pair("$m:${s.toString().padStart(2, '0')}", "/mi")
    }
    RecordKind.BEST_SPLIT_500 -> {
        val totalSec = value.roundToInt()
        val m = totalSec / 60
        val s = totalSec % 60
        Pair("$m:${s.toString().padStart(2, '0')}", "/500m")
    }
    RecordKind.MOST_FLOORS -> Pair(value.roundToInt().toString(), "floors")
    RecordKind.HIGHEST_POWER -> Pair(value.roundToInt().toString(), "W")
    RecordKind.BIGGEST_WEEK -> Pair(formatDurationCompact(value.toInt()), "")
    RecordKind.BIGGEST_MONTH -> {
        val miles = (value / 1609.344 * 10).roundToInt() / 10.0
        Pair(miles.toString(), "mi")
    }
}

@Composable
fun CardioHighlightsScreen(
    viewModel: CardioViewModel,
    onBack: () -> Unit,
    onMachine: (CardioActivityType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = todayLocalDate()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val subNavTitles = listOf("Days", "Machines", "Records", "Months")
    var selectedSubNav by remember { mutableStateOf(0) }

    val daysRuns = remember(state.sessions, today) {
        CardioInsights.dayRuns(state.sessions, today)
    }
    val weekStreak = remember(state.sessions, today) {
        CardioInsights.weekStreak(state.sessions, today, minSessions = 2)
    }
    val recordsList = remember(state.sessions, today) {
        CardioInsights.records(state.sessions, today)
    }
    val monthBarsList = remember(state.sessions, today) {
        CardioInsights.monthBars(state.sessions, today, months = 4)
    }
    val yearHours = remember(state.sessions, today) {
        CardioInsights.yearColumns(state.sessions, today.year)
    }

    // Machines that have at least 1 session
    val machinesWithSessions = remember(state.sessions) {
        CardioActivityType.entries.filter { type ->
            state.sessions.any { CardioActivitySpecs.fromStoredType(it.activityType) == type }
        }
    }

    val skyGradient = remember {
        Brush.verticalGradient(
            0f to DawnPalette.skyTop,
            0.42f to DawnPalette.skyMiddle,
            1f to DawnPalette.skyBottom,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(skyGradient),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DawnPalette.ink,
                        )
                    }
                    Text(
                        text = "Highlights",
                        fontFamily = SheetSerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Light,
                        fontSize = 28.sp,
                        color = DawnPalette.ink,
                    )
                }
                Text(
                    text = MONTH_NAMES_FULL.getOrNull(today.month.ordinal) ?: "",
                    fontFamily = LogUiFontFamily,
                    fontSize = 13.sp,
                    color = DawnPalette.muted,
                )
            }

            // Sticky Sub-Nav
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subNavTitles.forEachIndexed { idx, title ->
                    val isSelected = (idx == selectedSubNav)
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) DawnPalette.accentSoft else DawnPalette.panel,
                        border = BorderStroke(1.dp, if (isSelected) DawnPalette.accent else DawnPalette.line),
                        modifier = Modifier.clickable {
                            selectedSubNav = idx
                            coroutineScope.launch {
                                val scrollIdx = when (idx) {
                                    0 -> 0 // Days
                                    1 -> 5 // Machines
                                    2 -> 7 // Records
                                    3 -> 9 // Months
                                    else -> 0
                                }
                                listState.animateScrollToItem(scrollIdx)
                            }
                        },
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (isSelected) DawnPalette.accentText else DawnPalette.muted,
                            fontSize = 13.sp,
                            fontFamily = LogUiFontFamily,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            // Scrollable Content
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ==================== SECTION 1: DAYS ====================
                item {
                    val daysInCurrentMonth = remember(today) {
                        val firstOfNextMonth = if (today.month.ordinal == 11) LocalDate(today.year + 1, 1, 1) else LocalDate(today.year, today.month.ordinal + 2, 1)
                        (firstOfNextMonth.toEpochDays() - LocalDate(today.year, today.month.ordinal + 1, 1).toEpochDays()).toInt()
                    }
                    val firstDayOfMonth = remember(today) {
                        LocalDate(today.year, today.month.ordinal + 1, 1)
                    }
                    val startOffset = (firstDayOfMonth.dayOfWeek.ordinal) % 7 // Monday = 0, Sunday = 6

                    val runRange = remember(daysRuns, today) {
                        val start = daysRuns.currentRunStart
                        if (start != null && start.month == today.month && start.year == today.year) {
                            (start.dayOfMonth..today.dayOfMonth).toSet()
                        } else emptySet<Int>()
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Day of week headers (M T W T F S S)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                                Text(
                                    text = d,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp,
                                    fontFamily = LogUiFontFamily,
                                    color = DawnPalette.muted,
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        val totalCells = startOffset + daysInCurrentMonth
                        val numWeeks = (totalCells + 6) / 7

                        for (week in 0 until numWeeks) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                for (col in 0..6) {
                                    val cellIdx = week * 7 + col
                                    val dayNum = cellIdx - startOffset + 1

                                    if (dayNum in 1..daysInCurrentMonth) {
                                        val inRun = runRange.contains(dayNum)
                                        val isRunStart = inRun && dayNum == runRange.minOrNull()
                                        val isRunEnd = inRun && dayNum == runRange.maxOrNull()

                                        val bandShape = when {
                                            isRunStart && isRunEnd -> CircleShape
                                            isRunStart -> RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp)
                                            isRunEnd -> RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp)
                                            else -> RoundedCornerShape(0.dp)
                                        }

                                        val cellDateStr = "${today.year}-${(today.month.ordinal + 1).toString().padStart(2, '0')}-${dayNum.toString().padStart(2, '0')}"
                                        val daySessions = state.sessions.filter { it.date == cellDateStr }
                                        val totalDuration = daySessions.sumOf { it.durationSec ?: 0 }
                                        val longest = daySessions.maxByOrNull { it.durationSec ?: 0 }
                                        val machine = longest?.let { CardioActivitySpecs.fromStoredType(it.activityType) }
                                        val isToday = (dayNum == today.dayOfMonth)

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .then(
                                                    if (inRun) Modifier.background(DawnPalette.accent.copy(alpha = 0.12f), bandShape)
                                                    else Modifier
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Canvas(modifier = Modifier.size(24.dp)) {
                                                val center = Offset(size.width / 2f, size.height / 2f)

                                                if (dayNum > today.dayOfMonth) {
                                                    drawCircle(
                                                        color = DawnPalette.ink.copy(alpha = 0.14f),
                                                        radius = 2.5.dp.toPx(),
                                                        center = center,
                                                    )
                                                } else if (totalDuration == 0) {
                                                    if (isToday) {
                                                        drawCircle(
                                                            color = DawnPalette.ink.copy(alpha = 0.4f),
                                                            radius = 4.5.dp.toPx(),
                                                            center = center,
                                                            style = Stroke(
                                                                width = 1.5.dp.toPx(),
                                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f),
                                                            ),
                                                        )
                                                    } else {
                                                        drawCircle(
                                                            color = DawnPalette.ink.copy(alpha = 0.14f),
                                                            radius = 2.5.dp.toPx(),
                                                            center = center,
                                                        )
                                                    }
                                                } else {
                                                    val color = machine?.let { DawnPalette.machine(it) } ?: DawnPalette.accent
                                                    val radiusDp = when {
                                                        totalDuration <= 900 -> 5.5.dp
                                                        totalDuration <= 2100 -> 7.5.dp
                                                        else -> 9.5.dp
                                                    }
                                                    drawCircle(
                                                        color = color,
                                                        radius = radiusDp.toPx(),
                                                        center = center,
                                                    )
                                                    if (isToday) {
                                                        drawCircle(
                                                            color = DawnPalette.ink,
                                                            radius = (radiusDp + 2.5.dp).toPx(),
                                                            center = center,
                                                            style = Stroke(width = 2.dp.toPx()),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Trio numbers
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = daysRuns.daysMovedThisMonth.toString(),
                                fontFamily = SheetSerifFontFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 34.sp,
                                color = DawnPalette.ink,
                            )
                            Text(
                                text = "days moved\nof ${daysRuns.daysElapsedThisMonth} so far",
                                fontFamily = LogUiFontFamily,
                                fontSize = 11.sp,
                                color = DawnPalette.muted,
                                lineHeight = 15.sp,
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = daysRuns.currentRunLength.toString(),
                                    fontFamily = SheetSerifFontFamily,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 34.sp,
                                    color = DawnPalette.ink,
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "days",
                                    fontFamily = SheetSerifFontFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 16.sp,
                                    color = DawnPalette.ink,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            val startLabel = daysRuns.currentRunStart?.let {
                                val m = MONTH_ABBREV.getOrNull(it.month.ordinal) ?: ""
                                "$m ${it.dayOfMonth}"
                            } ?: ""
                            Text(
                                text = "current run\n${if (startLabel.isNotBlank()) "$startLabel to today" else "no active run"}",
                                fontFamily = LogUiFontFamily,
                                fontSize = 11.sp,
                                color = DawnPalette.muted,
                                lineHeight = 15.sp,
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = daysRuns.longestRunLength.toString(),
                                    fontFamily = SheetSerifFontFamily,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 34.sp,
                                    color = DawnPalette.ink,
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "days",
                                    fontFamily = SheetSerifFontFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 16.sp,
                                    color = DawnPalette.ink,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            val startLabel = daysRuns.longestRunStart?.let { "${MONTH_ABBREV.getOrNull(it.month.ordinal) ?: ""} ${it.dayOfMonth}" } ?: ""
                            val endLabel = daysRuns.longestRunEnd?.let { "${MONTH_ABBREV.getOrNull(it.month.ordinal) ?: ""} ${it.dayOfMonth}" } ?: ""
                            Text(
                                text = "longest run\n${if (startLabel.isNotBlank()) "$startLabel to $endLabel" else "none yet"}",
                                fontFamily = LogUiFontFamily,
                                fontSize = 11.sp,
                                color = DawnPalette.muted,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }

                // Weeks in a row subhead
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "Weeks in a row",
                            fontFamily = SheetSerifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                            color = DawnPalette.ink,
                        )
                        Text(
                            text = "with 2 or more sessions",
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }

                // Weeks in a row pills
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(weekStreak.weeksInARow) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(DawnPalette.accent),
                            )
                        }
                        if (!weekStreak.currentWeekQualifies) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp),
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRoundRect(
                                        color = DawnPalette.ink.copy(alpha = 0.4f),
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f),
                                        ),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                                    )
                                }
                            }
                        }
                        Text(
                            text = weekStreak.weeksInARow.toString(),
                            fontFamily = SheetSerifFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Light,
                            color = DawnPalette.ink,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    val weekSentence = if (!weekStreak.currentWeekQualifies) {
                        "This week needs ${weekStreak.currentWeekNeeds} more session${if (weekStreak.currentWeekNeeds > 1) "s" else ""} to make it ${weekStreak.weeksInARow + 1}."
                    } else {
                        "This week already counts."
                    }
                    Text(
                        text = weekSentence,
                        fontFamily = LogUiFontFamily,
                        fontSize = 12.sp,
                        color = DawnPalette.muted,
                    )
                }

                // ==================== SECTION 2: MACHINES ====================
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "By machine",
                            fontFamily = SheetSerifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                            color = DawnPalette.ink,
                        )
                    }
                }

                items(machinesWithSessions) { mType ->
                    val mSpec = CardioActivitySpecs.specFor(mType)
                    val mSessions = state.sessions.filter { CardioActivitySpecs.fromStoredType(it.activityType) == mType }
                    val defaultMetric = CardioProgress.metricsFor(mType).first()
                    val progress = remember(mSessions, today) {
                        CardioProgress.machineProgress(state.sessions, mType, defaultMetric, today)
                    }
                    val sparklineValues = progress.points.map { it.value }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMachine(mType) },
                        color = Color.Transparent,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(DawnPalette.machine(mType), CircleShape),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = mSpec.label,
                                            fontFamily = LogUiFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp,
                                            color = DawnPalette.ink,
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "${defaultMetric.label} · ${countLabel(mSessions.size, "session")}",
                                        fontFamily = LogUiFontFamily,
                                        fontSize = 11.5.sp,
                                        color = DawnPalette.muted,
                                    )
                                }

                                Sparkline(
                                    values = sparklineValues,
                                    color = DawnPalette.machine(mType),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )

                                Column(horizontalAlignment = Alignment.End) {
                                    if (mSessions.size < 3) {
                                        Text(
                                            text = "3 sessions to start",
                                            fontFamily = LogUiFontFamily,
                                            fontSize = 12.sp,
                                            color = DawnPalette.muted,
                                        )
                                    } else {
                                        val delta = progress.delta
                                        val deltaStr = if (delta != null) {
                                            val sign = if (delta >= 0) "+" else "−"
                                            val rounded = (abs(delta) * 10).roundToInt() / 10.0
                                            "$sign$rounded"
                                        } else "—"
                                        val improved = if (defaultMetric.higherIsBetter) (delta ?: 0.0) > 0 else (delta ?: 0.0) < 0

                                        Text(
                                            text = deltaStr,
                                            fontFamily = SheetSerifFontFamily,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = if (improved) DawnPalette.accentText else DawnPalette.muted,
                                        )
                                        Text(
                                            text = progress.unit,
                                            fontFamily = LogUiFontFamily,
                                            fontSize = 11.sp,
                                            color = DawnPalette.muted,
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(DawnPalette.line),
                            )
                        }
                    }
                }

                // ==================== SECTION 3: RECORDS ====================
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "Personal bests",
                            fontFamily = SheetSerifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                            color = DawnPalette.ink,
                        )
                        Text(
                            text = "all machines",
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }

                if (recordsList.isEmpty()) {
                    item {
                        Text(
                            text = dawnDataLine(listOf(filler("Records start at "), data("3"), filler(" sessions"))),
                            lineHeight = 17.sp,
                        )
                    }
                } else {
                    items(recordsList) { rec ->
                        val (valStr, unitStr) = formatRecordValue(rec.kind, rec.machine, rec.value)
                        val kindLabel = formatRecordKindLabel(rec.kind, rec.machine)
                        val machineLabel = rec.machine?.let { CardioActivitySpecs.specFor(it).label } ?: ""
                        val dateLabel = "${MONTH_ABBREV.getOrNull(rec.date.month.ordinal) ?: ""} ${rec.date.dayOfMonth}"
                        val metaLabel = if (machineLabel.isNotBlank()) "$machineLabel · $dateLabel" else dateLabel

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = kindLabel,
                                            fontFamily = LogUiFontFamily,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = DawnPalette.ink,
                                        )
                                        if (rec.isNew) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = DawnPalette.accent,
                                            ) {
                                                Text(
                                                    text = "NEW",
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    fontFamily = LogUiFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 10.sp,
                                                    color = DawnPalette.pillText,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = metaLabel,
                                        fontFamily = LogUiFontFamily,
                                        fontSize = 11.5.sp,
                                        color = DawnPalette.muted,
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = valStr,
                                            fontFamily = SheetSerifFontFamily,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = DawnPalette.ink,
                                        )
                                        if (unitStr.isNotBlank()) {
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = unitStr,
                                                fontFamily = LogUiFontFamily,
                                                fontSize = 11.sp,
                                                color = DawnPalette.muted,
                                                modifier = Modifier.padding(bottom = 3.dp),
                                            )
                                        }
                                    }
                                    if (rec.isNew && rec.previous != null) {
                                        val prevStr = formatRecordValue(rec.kind, rec.machine, rec.previous).first
                                        Text(
                                            text = "was $prevStr",
                                            fontFamily = LogUiFontFamily,
                                            fontSize = 11.sp,
                                            color = DawnPalette.accentText,
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(DawnPalette.line),
                            )
                        }
                    }
                }

                // ==================== SECTION 4: MONTHS ====================
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "Month by month",
                            fontFamily = SheetSerifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                            color = DawnPalette.ink,
                        )
                        Text(
                            text = "time moving",
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }

                items(monthBarsList) { mb ->
                    val monthName = MONTH_ABBREV.getOrNull(mb.month - 1) ?: ""
                    val durationText = formatDurationCompact(mb.totalSeconds)
                    val suffix = if (mb.isCurrent) " so far" else ""
                    val maxMonthSec = monthBarsList.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1) ?: 1

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = monthName,
                                fontFamily = SheetSerifFontFamily,
                                fontStyle = FontStyle.Italic,
                                fontSize = 15.sp,
                                color = DawnPalette.muted,
                                modifier = Modifier.width(54.dp),
                            )

                            // Segmented bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DawnPalette.ink.copy(alpha = 0.08f)),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    val totalFraction = (mb.totalSeconds.toFloat() / maxMonthSec).coerceIn(0f, 1f)
                                    mb.secondsByMachine.forEach { (mType, sec) ->
                                        val segFrac = if (mb.totalSeconds > 0) (sec.toFloat() / mb.totalSeconds) * totalFraction else 0f
                                        if (segFrac > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(segFrac.coerceAtLeast(0.001f))
                                                    .background(DawnPalette.machine(mType)),
                                            )
                                        }
                                    }
                                    if (totalFraction < 1f) {
                                        Spacer(modifier = Modifier.weight((1f - totalFraction).coerceAtLeast(0.001f)))
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Text(
                                text = "$durationText$suffix",
                                fontFamily = SheetSerifFontFamily,
                                fontSize = 16.sp,
                                color = DawnPalette.ink,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(DawnPalette.line),
                        )
                    }
                }

                // Month Legend
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        machinesWithSessions.forEach { type ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(DawnPalette.machine(type), CircleShape),
                                )
                                Text(
                                    text = CardioActivitySpecs.specFor(type).label,
                                    fontFamily = LogUiFontFamily,
                                    fontSize = 11.sp,
                                    color = DawnPalette.muted,
                                )
                            }
                        }
                    }
                }

                // The Year
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "The year",
                            fontFamily = SheetSerifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                            color = DawnPalette.ink,
                        )
                        Text(
                            text = "hours per month · ${today.year}",
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }

                item {
                    val maxMonthSec = yearHours.maxOrNull()?.coerceAtLeast(1) ?: 1
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            val curMonthIdx = today.month.ordinal
                            yearHours.forEachIndexed { i, sec ->
                                val frac = (sec.toFloat() / maxMonthSec).coerceIn(0f, 1f)
                                val isNow = (i == curMonthIdx)
                                val barHeight = (60 * frac).coerceAtLeast(if (sec > 0) 4f else 0f).dp

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(DawnPalette.accent.copy(alpha = 0.75f))
                                        .then(
                                            if (isNow) Modifier.border(1.5.dp, DawnPalette.ink, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            else Modifier
                                        ),
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Month initials
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D").forEach { initial ->
                                Text(
                                    text = initial,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontFamily = LogUiFontFamily,
                                    fontSize = 9.sp,
                                    color = DawnPalette.muted,
                                )
                            }
                        }
                    }
                }

                // Summary sentence under year
                item {
                    val totalSec = state.sessions.sumOf { it.durationSec ?: 0 }
                    val totalHours = totalSec / 3600
                    val totalCount = state.sessions.size
                    val earliestOverall = state.sessions.lastOrNull()
                    val sinceLabel = earliestOverall?.let { s ->
                        runCatching {
                            val d = LocalDate.parse(s.date)
                            MONTH_NAMES_FULL.getOrNull(d.month.ordinal) ?: d.month.name
                        }.getOrNull()
                    } ?: "this year"

                    val limitText = if (totalCount == 1000) " in your last 1000 sessions." else "."
                    Text(
                        text = "${countLabel(totalHours.toInt(), "hour")} and ${countLabel(totalCount, "session")} since $sinceLabel$limitText",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontFamily = LogUiFontFamily,
                        fontSize = 12.sp,
                        color = DawnPalette.muted,
                    )
                }
            }
        }
    }
}
