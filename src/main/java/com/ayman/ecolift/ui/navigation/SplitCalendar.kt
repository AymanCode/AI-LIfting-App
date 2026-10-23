package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.splits.DayInfo
import com.ayman.ecolift.data.splits.DayMarks
import com.ayman.ecolift.data.splits.DayPr
import com.ayman.ecolift.data.progress.MuscleSlice
import com.ayman.ecolift.data.splits.DayTier
import com.ayman.ecolift.data.splits.dayTier
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.ShaderWarmup
import com.ayman.ecolift.ui.theme.rememberReducedMotion
import com.ayman.ecolift.ui.theme.splitAccentColor
import com.ayman.ecolift.web.util.YearMonthW
import com.ayman.ecolift.web.util.groupThousands
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

internal val SPLIT_MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val SPLIT_MONTH_ABBREVIATIONS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val WEEKDAY_ABBREVIATIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Overshoot for a sticker pressed onto the page. */
private val StickEasing = CubicBezierEasing(0.2f, 1.45f, 0.4f, 1f)

/** Overshoot for a note slapped down. */
private val SlapEasing = CubicBezierEasing(0.2f, 1.25f, 0.4f, 1f)

private const val FLIP_MS = 720
private const val BACK_FLIP_MS = 560

/** How a trained day reads on the page. */
private enum class DayKind { SPLIT, FREESTYLE, TEMP }

/**
 * The month as a kraft wall calendar (spec §01): dot stickers in the split's colour, cream for
 * freestyle, a pencil loop for a temp split, sized light / regular / heavy, a gold star for a PR
 * on one of the heaviest lifts. Tapping a day lifts its sticker and lands a note; ‹ › flip the page.
 */
@Composable
fun SplitCalendar(
    displayedMonth: YearMonthW,
    flipDirection: Int,
    dayInfo: Map<LocalDate, DayInfo>,
    dayMarks: Map<LocalDate, DayMarks>,
    tempDays: Set<LocalDate>,
    tempNameFor: (LocalDate) -> String?,
    lightThreshold: Long,
    brightThreshold: Long,
    splits: List<SplitType>,
    playIntro: Boolean,
    onIntroPlayed: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        AnimatedContent(
            targetState = displayedMonth,
            transitionSpec = {
                (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished).apply {
                    // The earlier month always lies on top, like pages on a wall calendar: going
                    // forward the old page lifts off above the next one, going back the earlier
                    // page falls back down over the later one.
                    targetContentZIndex = -(targetState.year * 12 + targetState.month).toFloat()
                } using SizeTransform(clip = false)
            },
            label = "calendarPage",
        ) { month ->
            val forward = flipDirection >= 0
            // Only the page that moves animates: forward, the exiting page lifts away; back, the
            // entering page falls in. The other page holds still underneath.
            val rotation by transition.animateFloat(
                transitionSpec = {
                    when {
                        forward && initialState == EnterExitState.Visible && targetState == EnterExitState.PostExit -> keyframes {
                            durationMillis = FLIP_MS
                            0f at 0 using CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
                            86f at 446 using CubicBezierEasing(0.5f, 0f, 1f, 1f)
                            165f at FLIP_MS
                        }
                        !forward && initialState == EnterExitState.PreEnter && targetState == EnterExitState.Visible -> keyframes {
                            // Starts edge-on over the binding and falls, gathering speed, then settles.
                            durationMillis = BACK_FLIP_MS
                            90f at 0 using CubicBezierEasing(0.45f, 0f, 0.8f, 0.5f)
                            -4f at 440 using CubicBezierEasing(0.3f, 0f, 0.4f, 1f)
                            0f at BACK_FLIP_MS
                        }
                        else -> snap()
                    }
                },
                label = "pageRotation",
            ) { state ->
                when (state) {
                    EnterExitState.PreEnter -> if (forward) 0f else 90f
                    EnterExitState.Visible -> 0f
                    EnterExitState.PostExit -> if (forward) 165f else 0f
                }
            }
            val pageAlpha by transition.animateFloat(
                transitionSpec = {
                    when {
                        forward && initialState == EnterExitState.Visible && targetState == EnterExitState.PostExit ->
                            keyframes { durationMillis = FLIP_MS; 1f at 0; 1f at 430; 0f at 520 }
                        !forward && initialState == EnterExitState.PreEnter && targetState == EnterExitState.Visible ->
                            keyframes { durationMillis = BACK_FLIP_MS; 0f at 0; 1f at 50 }
                        else -> snap()
                    }
                },
                label = "pageAlpha",
            ) { state ->
                when (state) {
                    EnterExitState.PreEnter -> if (forward) 1f else 0f
                    EnterExitState.Visible -> 1f
                    EnterExitState.PostExit -> if (forward) 0f else 1f
                }
            }
            val shade by transition.animateFloat(
                transitionSpec = { tween(if (forward) FLIP_MS else 440, easing = LinearEasing) },
                label = "pageShade",
            ) { state ->
                when (state) {
                    EnterExitState.PreEnter -> if (forward) 0.42f else 0f
                    EnterExitState.Visible -> 0f
                    EnterExitState.PostExit -> if (forward) 0f else 0.42f
                }
            }
            val isEntering = transition.targetState == EnterExitState.Visible &&
                transition.currentState == EnterExitState.PreEnter
            // The moving page drops its shadows for the turn (see sheetShadow); the still page keeps them.
            val turning by remember { derivedStateOf { rotation != 0f || pageAlpha < 1f } }
            CompositionLocalProvider(LocalSheetTurning provides turning) {
            CalendarPage(
                month = month,
                dayInfo = dayInfo,
                dayMarks = dayMarks,
                tempDays = tempDays,
                tempNameFor = tempNameFor,
                lightThreshold = lightThreshold,
                brightThreshold = brightThreshold,
                splits = splits,
                introDelayMs = when {
                    isEntering && forward -> FLIP_MS
                    isEntering -> null // a page falling back in already has its stickers on
                    playIntro -> 150
                    else -> null
                },
                onIntroPlayed = onIntroPlayed,
                shade = shade,
                modifier = Modifier.graphicsLayer {
                    rotationX = rotation
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = 30f * density
                    alpha = pageAlpha
                },
            )
            }
        }
        WireBinding(Modifier.align(Alignment.TopCenter).offset(y = (-34).dp).fillMaxWidth().height(50.dp))
    }
}

/**
 * Compiles the calendar's shaders while nothing is moving (see [ShaderWarmup]): a made-up month
 * with every kind of sticker, drawn three times behind the app's opaque background, at rest, in
 * the tilt of a turning page, and tilted through a fade layer. The page at rest covers the Splits
 * tab's first paint; the tilted copies cover the first flip either way, which used to stall.
 */
@Composable
internal fun CalendarShaderWarmup() {
    val reducedMotion = rememberReducedMotion()
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion || ShaderWarmup.isDone(CALENDAR_WARMUP)) return@LaunchedEffect
        delay(CALENDAR_WARMUP_DELAY_MS)
        ShaderWarmup.run(CALENDAR_WARMUP) { showing = it }
    }
    if (!showing) return
    val month = remember { YearMonthW.now() }
    val sample = remember(month) { warmupMonth(month) }
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        for ((tilt, alpha) in WARMUP_POSES) {
            CompositionLocalProvider(LocalSheetTurning provides (tilt != 0f)) {
            CalendarPage(
                month = month,
                dayInfo = sample.days,
                dayMarks = sample.marks,
                tempDays = sample.tempDays,
                tempNameFor = { "Temp split" },
                lightThreshold = 1_000L,
                brightThreshold = 3_000L,
                splits = emptyList(),
                introDelayMs = null,
                onIntroPlayed = {},
                shade = 0.2f,
                modifier = Modifier
                    .padding(top = 64.dp)
                    .graphicsLayer {
                        rotationX = tilt
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        cameraDistance = 30f * density
                        this.alpha = alpha
                    },
            )
            }
        }
    }
}

private const val CALENDAR_WARMUP = "calendar"

/** The boot arrival on the Log tab has played out by then. */
private const val CALENDAR_WARMUP_DELAY_MS = 2_500L

/** Tilt and alpha: at rest; turning on its own (the lift); turning through a fade layer. */
private val WARMUP_POSES = listOf(0f to 1f, 62f to 1f, 62f to 0.5f)

internal class WarmupMonth(val days: Map<LocalDate, DayInfo>, val marks: Map<LocalDate, DayMarks>, val tempDays: Set<LocalDate>)

/**
 * A light, a regular and a heavy split day, a freestyle day, a temp day, and heavy-lift PRs on the
 * latest day, so the note opens on a stamped day with starred PR rows and a "more PRs" line.
 */
internal fun warmupMonth(month: YearMonthW): WarmupMonth {
    val d = { day: Int -> month.atDay(day) }
    return WarmupMonth(
        days = mapOf(
            d(3) to DayInfo(splitId = 1L, visualIndex = 0, volumeLbs = 800L, exerciseCount = 4),
            d(8) to DayInfo(splitId = 2L, visualIndex = 1, volumeLbs = 2_400L, exerciseCount = 5),
            d(12) to DayInfo(splitId = null, visualIndex = null, volumeLbs = 2_000L, exerciseCount = 3),
            d(17) to DayInfo(splitId = null, visualIndex = null, volumeLbs = 2_000L, exerciseCount = 3),
            d(21) to DayInfo(splitId = 3L, visualIndex = 2, volumeLbs = 6_000L, exerciseCount = 6),
        ),
        marks = mapOf(
            d(21) to DayMarks(
                setCount = 18,
                muscles = listOf(MuscleSlice.CHEST, MuscleSlice.BACK, MuscleSlice.QUADS),
                prs = listOf(
                    DayPr("Bench Press", 2_250, 5, 2_200, isBodyweight = false, heavyLift = true),
                    DayPr("Pull Up", 250, 8, 200, isBodyweight = true, heavyLift = false),
                    DayPr("Squat", 3_150, 3, 3_050, isBodyweight = false, heavyLift = true),
                    DayPr("Row", 1_850, 8, 1_800, isBodyweight = false, heavyLift = false),
                ),
            ),
        ),
        tempDays = setOf(d(17)),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarPage(
    month: YearMonthW,
    dayInfo: Map<LocalDate, DayInfo>,
    dayMarks: Map<LocalDate, DayMarks>,
    tempDays: Set<LocalDate>,
    tempNameFor: (LocalDate) -> String?,
    lightThreshold: Long,
    brightThreshold: Long,
    splits: List<SplitType>,
    introDelayMs: Int?,
    onIntroPlayed: () -> Unit,
    shade: Float,
    modifier: Modifier = Modifier,
) {
    val dates = remember(month) { buildPremiumCalendarGrid(month) }
    val today = remember { todayLocalDate() }
    val monthDays = remember(dayInfo, month) { dayInfo.filterKeys { YearMonthW.from(it) == month } }
    // The note opens on the month's latest session until a day is tapped; the data can arrive after
    // the page is first drawn, so the default is derived, not remembered.
    var tapped by remember(month) { mutableStateOf<LocalDate?>(null) }
    val selected = tapped?.takeIf { it in monthDays } ?: monthDays.keys.maxOrNull()
    // Decided once per page: marking the month as played must not cut the stick-on short.
    val introDelay = remember(month) { introDelayMs }
    LaunchedEffect(month) { if (introDelay != null) onIntroPlayed() }

    val kindOf: (LocalDate, DayInfo) -> DayKind = { date, info ->
        when {
            info.splitId != null -> DayKind.SPLIT
            date in tempDays -> DayKind.TEMP
            else -> DayKind.FREESTYLE
        }
    }
    val tierOf: (DayInfo) -> DayTier = { dayTier(it.volumeLbs, lightThreshold, brightThreshold) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 340.dp
        Column(
            Modifier
                .fillMaxWidth()
                .kraftPaper()
                .drawWithContent {
                    drawContent()
                    drawPageCurl(EspressoPalette.bg, 26.dp.toPx())
                    if (shade > 0.001f) drawRect(Color(0xFF0A0807).copy(alpha = shade))
                }
                .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 16.dp),
        ) {
            PageHead(sessions = monthDays.size, monthNumber = month.month)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        label,
                        style = StickerType.print(9.sp, StickerInk.printDim),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            var order = 0
            for (row in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until 7) {
                        val date = dates[row * 7 + column]
                        val inMonth = YearMonthW.from(date) == month
                        val info = if (inMonth) monthDays[date] else null
                        val index = if (info != null || (inMonth && date == today)) order++ else 0
                        Box(
                            Modifier
                                .weight(1f)
                                .height(42.dp)
                                .drawBehind {
                                    drawLine(StickerInk.rule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                !inMonth -> Text(
                                    date.day.toString(),
                                    style = StickerType.print(12.5.sp, Color(0xFF6F655C), 0.sp),
                                    modifier = Modifier.alpha(0.5f),
                                )
                                info == null -> UntrainedDay(
                                    date = date,
                                    isToday = date == today,
                                    introDelayMs = introDelay?.plus(index * 28 + 120),
                                )
                                else -> {
                                    val kind = kindOf(date, info)
                                    val marks = dayMarks[date]
                                    TrainedDay(
                                        date = date,
                                        kind = kind,
                                        tier = tierOf(info),
                                        color = when (kind) {
                                            DayKind.SPLIT -> splitAccentColor(info.visualIndex ?: 0)
                                            DayKind.TEMP -> StickerInk.amber
                                            DayKind.FREESTYLE -> StickerInk.cream
                                        },
                                        starred = marks?.hasHeavyLiftPr == true,
                                        selected = selected == date,
                                        label = dayLabel(date, kind, info, splits, tierOf(info), marks),
                                        introDelayMs = introDelay?.plus(index * 28),
                                        onClick = { tapped = date },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, start = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val counts = remember(monthDays, tempDays, dayMarks) {
                    SheetCounts(
                        bySplit = splits.indices.associateWith { i -> monthDays.values.count { it.visualIndex == splits[i].colorIndex && it.splitId != null } },
                        freestyle = monthDays.count { (d, i) -> i.splitId == null && d !in tempDays },
                        temp = monthDays.count { (d, i) -> i.splitId == null && d in tempDays },
                        starred = monthDays.keys.count { dayMarks[it]?.hasHeavyLiftPr == true },
                    )
                }
                StickerSheet(splits = splits, counts = counts, modifier = Modifier.weight(1f))
                val sel = selected
                val selInfo = sel?.let(monthDays::get)
                if (sel != null && selInfo != null) {
                    key(sel) {
                        val kind = kindOf(sel, selInfo)
                        DayNote(
                            date = sel,
                            kind = kind,
                            info = selInfo,
                            marks = dayMarks[sel],
                            tier = tierOf(selInfo),
                            splitName = when (kind) {
                                DayKind.SPLIT -> selInfo.label ?: "Split workout"
                                DayKind.TEMP -> "Temp split"
                                DayKind.FREESTYLE -> "Freestyle"
                            },
                            tempName = if (kind == DayKind.TEMP) tempNameFor(sel) else null,
                            color = when (kind) {
                                DayKind.SPLIT -> splitAccentColor(selInfo.visualIndex ?: 0)
                                DayKind.TEMP -> StickerInk.amber
                                DayKind.FREESTYLE -> StickerInk.cream
                            },
                            modifier = Modifier.width(if (narrow) 122.dp else 146.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHead(sessions: Int, monthNumber: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text(
                sessions.toString(),
                style = StickerType.display(30.sp, EspressoPalette.ink, (-1).sp),
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (sessions == 1) "session" else "sessions",
                style = StickerType.hand(20.sp, StickerInk.print),
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(
            monthNumber.toString().padStart(2, '0'),
            style = StickerType.display(46.sp, EspressoPalette.ink.copy(alpha = 0.22f), (-2).sp)
                .copy(drawStyle = Stroke(width = 2.4f)),
        )
    }
}

@Composable
private fun UntrainedDay(date: LocalDate, isToday: Boolean, introDelayMs: Int?) {
    Box(contentAlignment = Alignment.Center) {
        if (isToday) {
            val draw = rememberOneShot(date, introDelayMs ?: 0, 550, play = introDelayMs != null)
            HandLoop(LoopStyle.MARKER, StickerInk.moss, 2.4.dp, progress = draw.value)
        }
        Text(
            date.day.toString(),
            style = StickerType.print(12.5.sp, if (isToday) EspressoPalette.ink else StickerInk.print, 0.sp)
                .copy(fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium),
            modifier = if (isToday) Modifier.semantics { contentDescription = "${SPLIT_MONTH_NAMES[date.month.ordinal]} ${date.day} — today" } else Modifier,
        )
    }
}

@Composable
private fun TrainedDay(
    date: LocalDate,
    kind: DayKind,
    tier: DayTier,
    color: Color,
    starred: Boolean,
    selected: Boolean,
    label: String,
    introDelayMs: Int?,
    onClick: () -> Unit,
) {
    val intro = rememberOneShot(date, introDelayMs ?: 0, if (kind == DayKind.TEMP) 550 else 440, play = introDelayMs != null, easing = if (kind == DayKind.TEMP) androidx.compose.animation.core.FastOutSlowInEasing else StickEasing)
    val starIn = rememberOneShot("star$date", (introDelayMs ?: 0) + 240, 420, play = introDelayMs != null && starred, easing = CubicBezierEasing(0.2f, 1.6f, 0.4f, 1f))
    val lift by animateFloatAsState(if (selected) 1f else 0f, tween(240, easing = CubicBezierEasing(0.2f, 1.35f, 0.4f, 1f)), label = "stickerLift")
    val jitter = stickerJitter(date.day)
    Box(
        Modifier
            .size(44.dp, 42.dp)
            .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (kind == DayKind.TEMP) {
            HandLoop(LoopStyle.PENCIL, StickerInk.amber, if (selected) 2.6.dp else 1.6.dp, progress = intro.value)
            Text(
                date.day.toString(),
                style = StickerType.print(12.5.sp, StickerInk.amber, 0.sp).copy(fontWeight = FontWeight.SemiBold),
            )
            if (starred) PrStar(17.dp, Modifier.offset(12.5.dp, (-12.5).dp).graphicsLayer { rotationZ = -14f; scaleX = starIn.value; scaleY = starIn.value })
        } else {
            val size = stickerSize(tier)
            val t = intro.value
            DotSticker(
                color = color,
                diameter = size,
                number = date.day.toString(),
                numberSize = stickerNumberSize(tier),
                lifted = selected,
                modifier = Modifier
                    .offset(jitter.dx.dp, jitter.dy.dp)
                    .graphicsLayer {
                        val introScale = 1.5f - 0.5f * t
                        val liftScale = 1f + 0.14f * lift
                        scaleX = introScale * liftScale
                        scaleY = introScale * liftScale
                        rotationZ = (jitter.tiltDeg - 14f * (1f - t)) * (1f - lift)
                        translationY = (-12f * (1f - t) - 3f * lift) * density
                        alpha = (t / 0.55f).coerceIn(0f, 1f)
                    },
            )
            if (starred) {
                val off = size.value / 2f * 0.72f
                PrStar(
                    17.dp,
                    Modifier
                        .offset((jitter.dx + off).dp, (jitter.dy - off).dp)
                        .graphicsLayer {
                            rotationZ = -14f + 8f * lift - 46f * (1f - starIn.value)
                            val s = (2f - starIn.value) * (1f + 0.14f * lift)
                            scaleX = s
                            scaleY = s
                            translationY = -3f * lift * density
                            alpha = starIn.value.coerceIn(0f, 1f)
                        },
                )
            }
        }
    }
}

private fun dayLabel(date: LocalDate, kind: DayKind, info: DayInfo, splits: List<SplitType>, tier: DayTier, marks: DayMarks?): String {
    val name = when (kind) {
        DayKind.SPLIT -> info.label ?: "Split workout"
        DayKind.TEMP -> "Temp split"
        DayKind.FREESTYLE -> "Freestyle"
    }
    val extras = buildList {
        if (tier == DayTier.HEAVY) add("heavy day")
        if (tier == DayTier.LIGHT) add("light day")
        if (marks?.hasHeavyLiftPr == true) add("heavy-lift PR")
    }
    return "${SPLIT_MONTH_NAMES[date.month.ordinal]} ${date.day} — $name" + extras.joinToString("") { " · $it" }
}

// ---------------------------------------------------------------- key sheet

private data class SheetCounts(
    val bySplit: Map<Int, Int>,
    val freestyle: Int,
    val temp: Int,
    val starred: Int,
)

@Composable
private fun StickerSheet(splits: List<SplitType>, counts: SheetCounts, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .rotate(-1.4f)
            .sheetShadow(8.dp, shape, 0.5f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF41382F), Color(0xFF352E28), Color(0xFF3C342D))))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text("STICKERS USED", style = StickerType.print(7.5.sp, StickerInk.printDim, 1.8.sp).copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        splits.forEachIndexed { i, split ->
            SheetRow(name = split.name, count = counts.bySplit[i] ?: 0) {
                DotSticker(splitAccentColor(split.colorIndex), 18.dp)
            }
        }
        SheetRow(name = "Freestyle", count = counts.freestyle) { DotSticker(StickerInk.cream, 18.dp) }
        Canvas(Modifier.fillMaxWidth().padding(vertical = 5.dp).height(1.dp)) {
            drawLine(
                Color(0xFFF3EDE4).copy(alpha = 0.16f),
                Offset(0f, 0f),
                Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        }
        SheetRow(name = "Heavy-lift PRs", count = counts.starred) { PrStar(19.dp) }
        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("light", style = StickerType.hand(13.5.sp, Color(0xFFCFC5B8)))
            DotSticker(StickerInk.cream, 9.dp)
            DotSticker(StickerInk.cream, 13.dp)
            DotSticker(StickerInk.cream, 17.dp)
            Text("heavy", style = StickerType.hand(13.5.sp, Color(0xFFCFC5B8)))
        }
    }
}

@Composable
private fun SheetRow(name: String, count: Int, icon: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(23.dp).alpha(if (count == 0) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(7.dp))
        Text(
            name,
            style = StickerType.body(12.sp, Color(0xFFDDD4C8)),
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 10.sp, color = StickerInk.printDim, fontFamily = StickerType.body.fontFamily)) { append("×") }
                append(count.toString())
            },
            style = StickerType.display(13.5.sp, EspressoPalette.ink, 0.sp).copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---------------------------------------------------------------- the note

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayNote(
    date: LocalDate,
    kind: DayKind,
    info: DayInfo,
    marks: DayMarks?,
    tier: DayTier,
    splitName: String,
    tempName: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val slap = remember { Animatable(0f) }
    LaunchedEffect(Unit) { slap.animateTo(1f, tween(400, easing = SlapEasing)) }
    val ink = StickerInk.ink
    StickyNote(
        color = color,
        tiltDeg = 2f,
        modifier = modifier.graphicsLayer {
            val p = slap.value
            val s = 1.12f - 0.12f * p
            scaleX = s
            scaleY = s
            rotationZ = -6f * (1f - p)
            translationY = -10f * (1f - p) * density
            alpha = (p * 2f).coerceIn(0f, 1f)
        },
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                "${WEEKDAY_ABBREVIATIONS[date.dayOfWeek.ordinal]}, ${SPLIT_MONTH_ABBREVIATIONS[date.month.ordinal]} ${date.day}",
                style = StickerType.hand(20.sp, ink),
            )
            Text(splitName, style = StickerType.display(15.sp, ink, (-0.2).sp), modifier = Modifier.padding(top = 4.dp))
            if (tempName != null) {
                Text(tempName, style = StickerType.body(10.sp, ink.copy(alpha = 0.72f)), modifier = Modifier.padding(top = 4.dp))
            }
            val muscles = marks?.muscles.orEmpty()
            if (muscles.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    muscles.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(muscleSliceColor(slice))
                                    .drawBehind { drawCircle(ink.copy(alpha = 0.45f), style = Stroke(1.2.dp.toPx())) },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(slice.displayName, style = StickerType.body(10.sp, ink.copy(alpha = 0.85f)), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val data = SpanStyle(fontFamily = StickerType.display.fontFamily, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(
                buildAnnotatedString {
                    withStyle(data) { append(info.exerciseCount.toString()) }
                    append(" ex · ")
                    withStyle(data) { append((marks?.setCount ?: 0).toString()) }
                    append(if (marks?.setCount == 1) " set" else " sets")
                },
                style = StickerType.body(10.5.sp, ink.copy(alpha = 0.92f)),
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (info.volumeLbs > 0) {
                    Text(
                        buildAnnotatedString {
                            withStyle(data) { append(groupThousands(info.volumeLbs)) }
                            append(" lb")
                        },
                        style = StickerType.body(10.5.sp, ink.copy(alpha = 0.92f)),
                    )
                }
                if (tier != DayTier.REGULAR) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (tier == DayTier.HEAVY) "HEAVY" else "LIGHT",
                        style = StickerType.print(7.5.sp, ink.copy(alpha = 0.8f), 1.3.sp).copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .rotate(-4f)
                            .drawBehind {
                                drawRoundRect(ink.copy(alpha = 0.7f), cornerRadius = CornerRadius(5.dp.toPx()), style = Stroke(1.3.dp.toPx()))
                            }
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                    )
                }
            }
            val prs = marks?.prs.orEmpty()
            if (prs.isNotEmpty()) {
                Canvas(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 7.dp).height(1.dp)) {
                    drawLine(
                        ink.copy(alpha = 0.3f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                }
                prs.take(2).forEach { pr ->
                    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                        PrStar(13.dp, Modifier.padding(top = 1.dp), filled = pr.heavyLift, solid = true)
                        Spacer(Modifier.width(5.dp))
                        val plus = if (pr.isBodyweight) "+" else ""
                        Text(
                            buildAnnotatedString {
                                append(pr.exerciseName)
                                append(" ")
                                withStyle(SpanStyle(fontFamily = StickerType.display.fontFamily, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)) {
                                    append("$plus${WeightLbs.formatStored(pr.weightStored)} × ${pr.reps}")
                                }
                                withStyle(SpanStyle(fontSize = 9.sp, color = ink.copy(alpha = 0.7f))) {
                                    append("  was $plus${WeightLbs.formatStored(pr.previousBestStored)}")
                                }
                            },
                            style = StickerType.body(9.5.sp, ink, 12.sp),
                        )
                    }
                }
                if (prs.size > 2) {
                    val more = prs.size - 2
                    Text("+$more more PR${if (more == 1) "" else "s"}", style = StickerType.body(9.sp, ink.copy(alpha = 0.7f)))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- binding

/** Twin-wire binding over punched holes, with the page's hanger on a nail. */
@Composable
private fun WireBinding(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 358f
        val sy = size.height / 50f
        val holes = 13
        for (i in 0 until holes) {
            if (i == 6) continue
            val x = (24f + i * (310f / (holes - 1)) - 5f) * sx
            drawRoundRect(StickerInk.hole, topLeft = Offset(x - 2f * sx, 37f * sy), size = Size(14f * sx, 5f * sy), cornerRadius = CornerRadius(2.5f * sy))
            for (dx in listOf(1.5f, 6.5f)) {
                val a = x + dx * sx
                drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(a + 0.8f * sx, 25f * sy), size = Size(3f * sx, 16f * sy), cornerRadius = CornerRadius(1.5f * sx))
                drawRoundRect(
                    Brush.horizontalGradient(
                        0f to StickerInk.wireDark.copy(red = 0.56f),
                        0.45f to StickerInk.wireLight,
                        1f to StickerInk.wireDark,
                        startX = a,
                        endX = a + 3f * sx,
                    ),
                    topLeft = Offset(a, 22f * sy),
                    size = Size(3f * sx, 19f * sy),
                    cornerRadius = CornerRadius(1.5f * sx),
                )
            }
        }
        val cx = 179f * sx
        val hanger = Path().apply {
            moveTo(cx - 18f * sx, 35f * sy)
            lineTo(cx, 9f * sy)
            lineTo(cx + 18f * sx, 35f * sy)
        }
        drawPath(hanger, Color(0xFFC9BFB1), style = Stroke(1.8f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFE9E2D6), Color(0xFF9E958A), Color(0xFF5E574F)),
                center = Offset(cx - 1.5f * sx, 5.5f * sy),
                radius = 5f * sx,
            ),
            radius = 4.6f * sx,
            center = Offset(cx, 7f * sy),
        )
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = 4.6f * sx, center = Offset(cx + 1f * sx, 9f * sy), style = Stroke(1f))
    }
}

internal fun buildPremiumCalendarGrid(displayedMonth: YearMonthW): List<LocalDate> {
    val firstOfMonth = displayedMonth.atDay(1)
    val firstDayOfWeek = firstOfMonth.dayOfWeek.ordinal
    val grid = mutableListOf<LocalDate>()

    val previousMonth = displayedMonth.minusMonths(1)
    for (i in firstDayOfWeek downTo 1) {
        grid += previousMonth.atEndOfMonth().minusDays((i - 1).toLong())
    }

    for (day in 1..displayedMonth.lengthOfMonth()) {
        grid += displayedMonth.atDay(day)
    }

    val nextMonth = displayedMonth.plusMonths(1)
    var nextDay = 1
    while (grid.size < 42) {
        grid += nextMonth.atDay(nextDay++)
    }

    return grid
}
