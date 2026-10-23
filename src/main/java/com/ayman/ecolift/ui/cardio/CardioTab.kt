package com.ayman.ecolift.ui.cardio

import com.ayman.ecolift.web.util.countLabel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioFieldRules
import com.ayman.ecolift.cardio.CardioInsights
import com.ayman.ecolift.cardio.DayDot
import com.ayman.ecolift.cardio.RecordKind
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.ui.theme.DawnBackground
import com.ayman.ecolift.ui.theme.DawnPalette
import com.ayman.ecolift.ui.theme.DawnTheme
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.ui.theme.TAB_ARRIVAL_FRAMES
import com.ayman.ecolift.ui.theme.TabArrival
import com.ayman.ecolift.ui.theme.TabWarmupShell
import com.ayman.ecolift.ui.viewmodel.CardioUiState
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.web.util.noun
import com.ayman.ecolift.web.util.DAY_ABBREV
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

@Composable
fun CardioTab(
    viewModel: CardioViewModel,
    onLog: () -> Unit,
    onEditSession: (Long) -> Unit = {},
    onScan: () -> Unit,
    onHighlights: () -> Unit,
    onAllSessions: () -> Unit,
    onMachine: (CardioActivityType) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CardioTabContent(
        state = state,
        onLog = onLog,
        onEditSession = onEditSession,
        onScan = onScan,
        onHighlights = onHighlights,
        onAllSessions = onAllSessions,
        onMachine = onMachine,
        onRetry = viewModel::refreshScansLeft,
        onSelectSession = viewModel::selectSession,
        onOpenDay = viewModel::openDay,
        onUndoSave = viewModel::undoLastSave,
        onDeleteSession = viewModel::deleteSession,
    )
}

/**
 * The Cardio tab as a screen of plain data: everything [CardioTab] draws, with the view model's
 * few actions passed in, so the shader warm-up can draw the real thing with made-up sessions.
 */
@Composable
internal fun CardioTabContent(
    state: CardioUiState,
    onLog: () -> Unit,
    onEditSession: (Long) -> Unit,
    onScan: () -> Unit,
    onHighlights: () -> Unit,
    onAllSessions: () -> Unit,
    onMachine: (CardioActivityType) -> Unit,
    onRetry: () -> Unit,
    onSelectSession: (Long?) -> Unit,
    onOpenDay: (LocalDate?) -> Unit,
    onUndoSave: () -> Unit,
    onDeleteSession: (Long) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    highlightsInteraction: MutableInteractionSource? = null,
) {
    val today = todayLocalDate()

    var highlightedId by remember { mutableStateOf<Long?>(null) }
    val lastSavedId = state.lastSavedId

    LaunchedEffect(lastSavedId) {
        if (lastSavedId != null) {
            highlightedId = lastSavedId
            delay(900)
            highlightedId = null
        }
    }

    val dots = remember(state.sessions, today) { CardioInsights.weekArc(state.sessions, today) }
    val totals = remember(state.sessions, today) { CardioInsights.weekTotals(state.sessions, today) }
    val dayRuns = remember(state.sessions, today) { CardioInsights.dayRuns(state.sessions, today) }
    val weekStreak = remember(state.sessions, today) { CardioInsights.weekStreak(state.sessions, today) }
    val records = remember(state.sessions, today) { CardioInsights.records(state.sessions, today) }

    val hasAnySessions = state.sessions.isNotEmpty()
    val totalLoggedDays = remember(state.sessions) { state.sessions.map { it.date }.distinct().size }

    val rolling7Start = today.minusDays(6L)
    val recentSessions = remember(state.sessions, today) {
        state.sessions.filter { s ->
            val d = runCatching { LocalDate.parse(s.date) }.getOrNull()
            d != null && d >= rolling7Start && d <= today
        }.sortedWith(compareByDescending<CardioSession> { it.date }.thenByDescending { it.createdAt }).take(3)
    }

    val dayName = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val monthName = MONTH_ABBREV.getOrNull(today.monthNumber - 1) ?: ""
    val dateHeaderStr = "$dayName, $monthName ${today.dayOfMonth}"

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    DawnTitle(text = "Cardio")
                    Text(
                        text = dateHeaderStr,
                        fontFamily = LogUiFontFamily,
                        fontSize = 13.sp,
                        color = DawnPalette.muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }

            if (state.loading && state.sessions.isEmpty()) {
                // Loading state
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            color = DawnPalette.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = "Loading sessions…",
                            fontFamily = LogUiFontFamily,
                            fontSize = 13.sp,
                            color = DawnPalette.muted,
                        )
                    }
                }
            } else if (state.loadFailed && state.sessions.isEmpty()) {
                // Load failed state
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Couldn't load sessions.",
                            fontFamily = LogUiFontFamily,
                            fontSize = 14.sp,
                            color = DawnPalette.muted,
                        )
                        TextButton(onClick = onRetry) {
                            Text(
                                text = "Retry",
                                fontFamily = LogUiFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = DawnPalette.accentText,
                            )
                        }
                    }
                }
            } else if (!hasAnySessions) {
                // Empty state
                item {
                    WeekArc(
                        dots = dots,
                        today = today,
                        showSun = false,
                        onDotTap = {},
                    )
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Nothing logged",
                            fontFamily = SheetSerifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Light,
                            fontSize = 36.sp,
                            lineHeight = 38.sp,
                            color = DawnPalette.ink,
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    DawnPill(
                        text = "Log a session",
                        onClick = onLog,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        DawnScanLink(onClick = onScan)
                    }
                }
            } else {
                // Active state
                item {
                    // The key sits in the band under the horizon while the arc is still new to the
                    // user (fewer than 7 logged days), drawn with the arc's own marks.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        WeekArc(
                            dots = dots,
                            today = today,
                            showSun = true,
                            onDotTap = { dot ->
                                val daySessions = state.sessions.filter { it.date == dot.date.toString() }
                                if (daySessions.size == 1) {
                                    onSelectSession(daySessions.first().id)
                                } else if (daySessions.size > 1) {
                                    onOpenDay(dot.date)
                                }
                            },
                        )
                        if (totalLoggedDays < 7) {
                            ArcLegend(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                        }
                    }

                    Spacer(Modifier.height(if (totalLoggedDays < 7) 14.dp else 8.dp))

                    // Totals row
                    val totalMins = totals.seconds / 60
                    val h = totalMins / 60
                    val m = totalMins % 60
                    val timeDurationStr = if (h > 0) "$h h $m min" else "$m min"

                    val distanceMiles = totals.metres / 1609.344
                    val distStr = if (totals.metres > 0) {
                        val rounded = (distanceMiles * 10).roundToInt() / 10.0
                        "$rounded mi"
                    } else null

                    val calStr = if (totals.calories > 0) "${CardioFieldRules.display(com.ayman.ecolift.cardio.CardioEntryField.CALORIES, CardioActivityType.TREADMILL, totals.calories.toDouble())} cal" else null
                    val countStr = "${totals.count} session${if (totals.count == 1) "" else "s"}"

                    val rightParts = listOfNotNull(distStr, calStr, countStr).joinToString(" · ")

                    val leftAnnotated = buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = SheetSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 30.sp, color = DawnPalette.ink)) {
                            append("$timeDurationStr ")
                        }
                        withStyle(SpanStyle(fontFamily = SheetSerifFontFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light, fontSize = 30.sp, color = DawnPalette.ink)) {
                            append("last 7 days")
                        }
                    }

                    val textMeasurer = rememberTextMeasurer()
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val density = LocalDensity.current
                        val leftWidthPx = textMeasurer.measure(leftAnnotated).size.width
                        val rightWidthPx = textMeasurer.measure(
                            text = rightParts,
                            style = androidx.compose.ui.text.TextStyle(fontFamily = LogUiFontFamily, fontSize = 11.sp),
                        ).size.width
                        val totalNeededPx = leftWidthPx + rightWidthPx + with(density) { 12.dp.toPx() }
                        val fitsOneLine = totalNeededPx <= constraints.maxWidth

                        if (fitsOneLine) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    text = leftAnnotated,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = rightParts,
                                    fontFamily = LogUiFontFamily,
                                    fontSize = 11.sp,
                                    color = DawnPalette.muted,
                                    maxLines = 1,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = leftAnnotated,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = rightParts,
                                    fontFamily = LogUiFontFamily,
                                    fontSize = 11.sp,
                                    color = DawnPalette.muted,
                                    maxLines = 1,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                item {
                    DawnPill(
                        text = "Log a session",
                        onClick = onLog,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        DawnScanLink(onClick = onScan)
                    }
                }

                // Highlights card
                item {
                    val line1 = if (dayRuns.currentRunLength > 0) {
                        "${dayRuns.currentRunLength}-day run · ${dayRuns.daysMovedThisMonth} of ${countLabel(dayRuns.daysElapsedThisMonth, "day")} this month"
                    } else {
                        "${dayRuns.daysMovedThisMonth} of ${countLabel(dayRuns.daysElapsedThisMonth, "day")} this month"
                    }

                    val newestRec = records.firstOrNull { it.isNew }
                    val line2Parts = if (newestRec != null) {
                        // The record's name is the one Highlights prints for it.
                        val recDayName = newestRec.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                        listOf(filler("New best · "), data(formatRecordKindLabel(newestRec.kind, newestRec.machine)), filler(" · "), data(recDayName))
                    } else if (weekStreak.weeksInARow > 0) {
                        listOf(data("${weekStreak.weeksInARow}"), filler(" ${noun(weekStreak.weeksInARow, "week")} in a row"))
                    } else {
                        listOf(filler("Next: "), data("2 sessions"), filler(" this week"))
                    }
                    val line2 = dawnDataLine(line2Parts + filler(" Highlights \u203a"))

                    Surface(
                        onClick = onHighlights,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = DawnPalette.panel,
                        border = BorderStroke(1.dp, DawnPalette.line),
                        interactionSource = highlightsInteraction,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line1,
                                    fontFamily = LogUiFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = DawnPalette.ink,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(text = line2)
                            }
                        }
                    }
                }

                if (recentSessions.isNotEmpty()) {
                    item {
                        DawnSectionTitle(text = "Last 7 days")
                    }

                    items(recentSessions, key = { it.id }) { session ->
                        val whenLabel = when (session.date) {
                            today.toString() -> "Today"
                            today.minusDays(1L).toString() -> "Yesterday"
                            else -> runCatching { LocalDate.parse(session.date) }.getOrNull()?.let { DAY_ABBREV[it.dayOfWeek.ordinal] } ?: session.date
                        }
                        SessionRow(
                            session = session,
                            whenLabel = whenLabel,
                            onClick = { onSelectSession(session.id) },
                            highlighted = (session.id == highlightedId),
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        DawnGhostPill(
                            text = "All sessions · ${state.sessions.size}",
                            onClick = onAllSessions,
                        )
                    }
                }
            }
        }

        // Toast overlay
        state.toast?.let { toast ->
            val text = buildAnnotatedString {
                append(toast.text)
                if (!toast.emphasis.isNullOrBlank()) {
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(toast.emphasis)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                DawnToast(
                    text = text,
                    action = toast.action,
                    onAction = onUndoSave,
                    visible = true,
                )
            }
        }

        // Session Sheet
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
                        onSelectSession(null)
                        onEditSession(session.id)
                    },
                    onDelete = {
                        onDeleteSession(session.id)
                    },
                    onMachine = {
                        val actType = CardioActivitySpecs.fromStoredType(session.activityType)
                        onSelectSession(null)
                        onMachine(actType)
                    },
                    onDismiss = {
                        onSelectSession(null)
                    },
                )
            }
        }

        // Day Sheet
        val daySheetDate = state.daySheetDate
        if (daySheetDate != null) {
            val daySessions = state.sessions.filter { it.date == daySheetDate.toString() }
            val sheetDayName = daySheetDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            val sheetMonthName = MONTH_ABBREV.getOrNull(daySheetDate.monthNumber - 1) ?: ""
            DawnSheet(onDismiss = { onOpenDay(null) }) {
                DawnSectionTitle(text = "$sheetDayName, $sheetMonthName ${daySheetDate.dayOfMonth}")
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    daySessions.forEach { s ->
                        SessionRow(
                            session = s,
                            whenLabel = sheetDayName,
                            onClick = {
                                onOpenDay(null)
                                onSelectSession(s.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shader warm-up (see ShaderWarmup)
// ---------------------------------------------------------------------------------------------

private const val CARDIO_WARMUP = "cardio-tab"
private const val CARDIO_WARMUP_DELAY_MS = 8_500L

/**
 * Compiles the Cardio tab's shaders while nothing is moving (see [ShaderWarmup]): the real screen
 * on the Dawn sky with made-up sessions, drawn in the tab transition's pose behind the app's opaque
 * background (the first visit), with the highlights card pressed the way a finger landing on it to
 * scroll presses it (a press draws its own state layer). Nothing here reads the view model or
 * changes anything: every callback is a no-op, and the press is only drawn.
 */
@Composable
internal fun CardioTabWarmup(bottomReserved: Dp) {
    TabWarmupShell(CARDIO_WARMUP, CARDIO_WARMUP_DELAY_MS, TAB_ARRIVAL_FRAMES, bottomReserved) {
        val state = remember { warmupCardioState() }
        val press = remember { MutableInteractionSource() }
        LaunchedEffect(press) { press.emit(PressInteraction.Press(Offset(200f, 60f))) }
        TabArrival {
            Box(Modifier.fillMaxSize()) {
                DawnBackground()
                CardioWarmupScreen(state, rememberLazyListState(), press)
            }
        }
    }
}

@Composable
private fun CardioWarmupScreen(state: CardioUiState, listState: LazyListState, highlightsInteraction: MutableInteractionSource?) {
    DawnTheme {
        CardioTabContent(
            state = state,
            onLog = {},
            onEditSession = {},
            onScan = {},
            onHighlights = {},
            onAllSessions = {},
            onMachine = {},
            onRetry = {},
            onSelectSession = {},
            onOpenDay = {},
            onUndoSave = {},
            onDeleteSession = {},
            listState = listState,
            highlightsInteraction = highlightsInteraction,
        )
    }
}

/** A treadmill run, a row, a ride and a workout on the elliptical this week, and an older run. */
private fun warmupCardioState(): CardioUiState {
    val today = todayLocalDate()
    var id = 0L
    fun session(daysAgo: Long, type: CardioActivityType, seconds: Int, metres: Double, calories: Int, build: (CardioSession) -> CardioSession = { it }): CardioSession {
        id++
        return build(
            CardioSession(
                id = id, localUuid = "warmup-$id", date = today.minusDays(daysAgo).toString(), activityType = type.name,
                durationSec = seconds, distanceM = metres, calories = calories, source = "manual", createdAt = id, updatedAt = id,
            ),
        )
    }
    return CardioUiState(
        loading = false,
        sessions = listOf(
            session(1, CardioActivityType.TREADMILL, 1_800, 4_680.0, 375) { it.copy(avgHeartRate = 148, avgSpeed = 2.59, avgInclinePercent = 2.0) },
            session(2, CardioActivityType.ROW, 1_080, 3_000.0, 220) { it.copy(paceSecPer500m = 150, strokeRateSpm = 24) },
            session(3, CardioActivityType.BIKE, 2_700, 15_000.0, 410) { it.copy(cadenceRpm = 80, avgPowerWatts = 140) },
            session(5, CardioActivityType.ELLIPTICAL, 1_200, 3_200.0, 180) { it.copy(resistanceLevel = 8.0) },
            session(14, CardioActivityType.RUN, 2_400, 6_400.0, 520),
        ),
    )
}
