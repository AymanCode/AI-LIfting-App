package com.ayman.ecolift.ui.cardio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

private val MONTH_NAMES_FULL = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private fun formatMetricDisplay(metric: ProgressMetric, value: Double): String = when (metric) {
    ProgressMetric.SPEED, ProgressMetric.SPEED_AT_HR -> {
        val rounded = (value * 10).roundToInt() / 10.0
        rounded.toString()
    }
    ProgressMetric.PACE, ProgressMetric.SPLIT, ProgressMetric.SPLIT_AT_HR, ProgressMetric.PACE_100 -> {
        val totalSec = value.roundToInt()
        val m = totalSec / 60
        val s = totalSec % 60
        "$m:${s.toString().padStart(2, '0')}"
    }
    ProgressMetric.DISTANCE -> {
        val rounded = (value * 10).roundToInt() / 10.0
        rounded.toString()
    }
    ProgressMetric.FLOORS_PER_MIN -> {
        val rounded = (value * 10).roundToInt() / 10.0
        rounded.toString()
    }
    ProgressMetric.FLOORS -> value.roundToInt().toString()
    ProgressMetric.DURATION -> value.roundToInt().toString()
    ProgressMetric.POWER, ProgressMetric.POWER_AT_HR -> value.roundToInt().toString()
    ProgressMetric.CADENCE -> value.roundToInt().toString()
    ProgressMetric.STROKE_RATE -> value.roundToInt().toString()
    ProgressMetric.RESISTANCE -> {
        val rounded = (value * 10).roundToInt() / 10.0
        rounded.toString()
    }
}

private data class MonthArcData(
    val fraction: Float,
    val valueStr: String,
    val unitStr: String,
    val captionStr: String,
)

@Composable
fun CardioMachineScreen(
    viewModel: CardioViewModel,
    type: CardioActivityType,
    onBack: () -> Unit,
    onEditSession: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = todayLocalDate()

    val machineSpec = remember(type) { CardioActivitySpecs.specFor(type) }
    val machineSessions = remember(state.sessions, type) {
        state.sessions.filter { CardioActivitySpecs.fromStoredType(it.activityType) == type }
    }

    // Earliest session month name
    val earliestSession = machineSessions.lastOrNull()
    val firstMonthName = remember(earliestSession) {
        earliestSession?.let { s ->
            runCatching {
                val d = LocalDate.parse(s.date)
                MONTH_NAMES_FULL.getOrNull(d.month.ordinal) ?: d.month.name
            }.getOrNull()
        } ?: MONTH_NAMES_FULL.getOrNull(today.month.ordinal) ?: "Recently"
    }

    val metrics = remember(type) { CardioProgress.metricsFor(type) }
    var selectedMetricIdx by remember(type) { mutableStateOf(0) }
    val selectedMetric = metrics.getOrElse(selectedMetricIdx) { metrics.first() }

    val progressList = remember(machineSessions, type, today) {
        metrics.map { m -> CardioProgress.machineProgress(state.sessions, type, m, today) }
    }
    val disabledIndices = remember(progressList) {
        progressList.mapIndexedNotNull { idx, p -> if (!p.available) idx else null }.toSet()
    }
    val currentProgress = progressList.getOrElse(selectedMetricIdx) { progressList.first() }

    // 3 month arcs data
    val monthArcList = remember(machineSessions, today, type) {
        val currentMonth = today.month.ordinal + 1
        val currentYear = today.year

        // 3 months: [2 months ago, 1 month ago, current month]
        val targetMonths = (2 downTo 0).map { offset ->
            var y = currentYear
            var m = currentMonth - offset
            while (m <= 0) {
                m += 12
                y -= 1
            }
            Pair(y, m)
        }

        val monthTotals = targetMonths.map { (y, m) ->
            val monthSessions = machineSessions.filter { s ->
                val d = runCatching { LocalDate.parse(s.date) }.getOrNull() ?: return@filter false
                d.year == y && (d.month.ordinal + 1) == m && d <= today
            }

            val totalMetric = when (type) {
                CardioActivityType.ROW, CardioActivityType.SWIM -> {
                    monthSessions.sumOf { it.distanceM ?: 0.0 }
                }
                CardioActivityType.STAIR_CLIMBER -> {
                    monthSessions.sumOf { (it.floors ?: 0).toDouble() }
                }
                else -> {
                    monthSessions.sumOf { (it.distanceM ?: 0.0) / 1609.344 }
                }
            }
            Triple(Pair(y, m), totalMetric, monthSessions.size)
        }

        val maxVal = monthTotals.maxOfOrNull { it.second }?.coerceAtLeast(0.001) ?: 1.0

        monthTotals.map { (ym, total, count) ->
            val (y, m) = ym
            val isThisMonth = (y == currentYear && m == currentMonth)
            val monthName = MONTH_NAMES_FULL.getOrNull(m - 1) ?: "Month"
            val fraction = (total / maxVal).toFloat().coerceIn(0f, 1f)

            val (valStr, unitStr) = when (type) {
                CardioActivityType.ROW, CardioActivityType.SWIM -> {
                    Pair(total.roundToInt().toString(), "m")
                }
                CardioActivityType.STAIR_CLIMBER -> {
                    Pair(total.roundToInt().toString(), "floors")
                }
                else -> {
                    val rounded = (total * 10).roundToInt() / 10.0
                    Pair(rounded.toString(), "mi")
                }
            }

            val caption = if (isThisMonth) {
                val shortName = MONTH_ABBREV.getOrNull(m - 1) ?: monthName
                "$shortName, so far · $count"
            } else {
                "$monthName · $count"
            }

            MonthArcData(fraction, valStr, unitStr, caption)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Back button + Machine Name + "since {firstMonth} · {n} sessions"
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            text = machineSpec.label,
                            fontFamily = SheetSerifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Light,
                            fontSize = 28.sp,
                            color = DawnPalette.ink,
                        )
                    }
                    Text(
                        text = "since $firstMonthName · ${countLabel(machineSessions.size, "session")}",
                        fontFamily = LogUiFontFamily,
                        fontSize = 12.sp,
                        color = DawnPalette.muted,
                    )
                }
            }

            // Metric selection chips
            item {
                ChipRow(
                    labels = metrics.map { it.label },
                    selectedIndex = selectedMetricIdx,
                    disabled = disabledIndices,
                    onSelect = { selectedMetricIdx = it },
                )
            }

            // Hero section: big number + sentence
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val rawLatest = machineSessions.firstNotNullOfOrNull { CardioProgress.metricValue(it, selectedMetric) }
                    val displayVal = currentProgress.latest ?: rawLatest
                    val bigValueStr = displayVal?.let { formatMetricDisplay(selectedMetric, it) } ?: "—"

                    val unitContext = if (selectedMetric.atHeartRate && currentProgress.referenceHeartRate != null) {
                        "${currentProgress.unit} at ${currentProgress.referenceHeartRate} bpm"
                    } else {
                        currentProgress.unit
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = bigValueStr,
                            fontFamily = SheetSerifFontFamily,
                            fontWeight = FontWeight.Light,
                            fontSize = 56.sp,
                            color = DawnPalette.ink,
                        )
                        Text(
                            text = unitContext,
                            fontFamily = LogUiFontFamily,
                            fontSize = 14.sp,
                            color = DawnPalette.muted,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Hero sentence with bolding on the first clause
                    val sentence = currentProgress.sentence
                    val thanIdx = sentence.indexOf("than")
                    val annotatedSentence = buildAnnotatedString {
                        if (thanIdx > 0) {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = DawnPalette.ink)) {
                                append(sentence.substring(0, thanIdx))
                            }
                            withStyle(SpanStyle(color = DawnPalette.muted)) {
                                append(sentence.substring(thanIdx))
                            }
                        } else {
                            withStyle(SpanStyle(color = DawnPalette.muted)) {
                                append(sentence)
                            }
                        }
                    }

                    Text(
                        text = annotatedSentence,
                        fontFamily = LogUiFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            // Line chart
            item {
                val firstDateLabel = currentProgress.points.firstOrNull()?.date?.let {
                    val m = MONTH_ABBREV.getOrNull(it.month.ordinal) ?: ""
                    "$m ${it.dayOfMonth}"
                } ?: ""
                val lastDateLabel = currentProgress.points.lastOrNull()?.date?.let {
                    val m = MONTH_ABBREV.getOrNull(it.month.ordinal) ?: ""
                    "$m ${it.dayOfMonth}"
                } ?: ""
                val midDateLabel = if (selectedMetric.atHeartRate) {
                    "hollow = no heart rate"
                } else if (!selectedMetric.higherIsBetter) {
                    "lower is faster"
                } else {
                    ""
                }

                DawnLineChart(
                    points = currentProgress.points,
                    excluded = currentProgress.excluded,
                    higherIsBetter = selectedMetric.higherIsBetter,
                    formatValue = { v -> formatMetricDisplay(selectedMetric, v) },
                    firstLabel = firstDateLabel,
                    midLabel = midDateLabel,
                    lastLabel = lastDateLabel,
                    contentDescription = "${selectedMetric.label} progression chart",
                )
            }

            // 3 Month Arcs
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    monthArcList.forEach { arc ->
                        MonthArc(
                            fraction = arc.fraction,
                            value = arc.valueStr,
                            unit = arc.unitStr,
                            caption = arc.captionStr,
                        )
                    }
                }
            }

            // Sessions on this machine, newest first
            item {
                Spacer(Modifier.height(12.dp))
                DawnSectionTitle(text = "Sessions on this machine")
            }

            if (machineSessions.isEmpty()) {
                item {
                    DawnEyebrow("No sessions", Modifier.padding(vertical = 8.dp))
                }
            } else {
                items(machineSessions, key = { it.id }) { session ->
                    val whenLabel = when (session.date) {
                        today.toString() -> "Today"
                        today.minusDays(1L).toString() -> "Yesterday"
                        else -> runCatching { LocalDate.parse(session.date) }.getOrNull()?.let {
                            val monthName = MONTH_ABBREV.getOrNull(it.month.ordinal) ?: ""
                            "$monthName ${it.dayOfMonth}"
                        } ?: session.date
                    }

                    SessionRow(
                        session = session,
                        whenLabel = whenLabel,
                        onClick = { viewModel.selectSession(session.id) },
                    )
                }
            }
        }

        // Shared session sheet overlay
        val selectedId = state.selectedSessionId
        if (selectedId != null) {
            val session = state.sessions.firstOrNull { it.id == selectedId }
            if (session != null) {
                val deltas = remember(session, state.sessions) {
                    CardioInsights.sessionDeltas(session, state.sessions)
                }
                CardioSessionSheet(
                    session = session,
                    deltas = deltas,
                    onEdit = {
                        viewModel.selectSession(null)
                        viewModel.startEditDraft(session.id)
                        onEditSession?.invoke(session.id)
                    },
                    onDelete = {
                        viewModel.deleteSession(session.id)
                    },
                    onMachine = {
                        viewModel.selectSession(null)
                    },
                    onDismiss = {
                        viewModel.selectSession(null)
                    },
                )
            }
        }
    }
}
