package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.theme.espressoButtonShadow
import com.ayman.ecolift.web.util.groupThousands
import com.ayman.ecolift.web.util.noun
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * The finish of a completed workout (spec docs/superpowers/specs/2026-09-22-summary-reopen-design.md):
 * the tally at the head of the summary, the Reopen latch, and the two motions that leave the summary —
 * the card lifting off its latch onto the reopened deck, and a row growing into its exercise card.
 */

// ───────────────────────────── Tally ─────────────────────────────

/**
 * One lift on the tally: its checked pounds (already rounded to add up) and reps. [bodyweight] is
 * true when every counted set was bodyweight, so a line with no pounds can say BW rather than 0.
 */
@Immutable
internal data class RotationReceiptLine(
    val cardKey: String,
    val region: BodyPartRegion,
    val pounds: Long,
    val reps: Int = 0,
    val bodyweight: Boolean = false,
)

@Immutable
internal data class RotationReceipt(
    val lines: List<RotationReceiptLine>,
    val totalPounds: Long,
    val sets: Int,
    val reps: Int,
)

/**
 * A lift's pounds are its checked sets' weight × reps, warm-ups included and pending deletions left
 * out: the same rule as the day's total, on the numbers each row prints. Stored weights are tenths
 * of a pound, so the sums are exact; the total rounds once and the lines take largest-remainder
 * rounding, so the printed lines always add up to the printed total.
 */
internal fun rotationSummaryReceipt(exercises: List<ExerciseLog>, pendingDeletedSetIds: Set<Long>): RotationReceipt {
    var sets = 0
    val lineReps = IntArray(exercises.size)
    val lineBodyweight = BooleanArray(exercises.size)
    val tenths = exercises.mapIndexed { i, exercise ->
        val counted = exercise.sets.filter { it.isCompleted && it.id !in pendingDeletedSetIds }
        lineBodyweight[i] = counted.isNotEmpty() && counted.all { it.isBodyweight }
        counted.sumOf { set ->
            val setReps = rotationDisplayedRepsOf(set) ?: 0
            sets += 1
            lineReps[i] += setReps
            (rotationDisplayedWeightOf(set) ?: 0).toLong() * setReps
        }
    }
    val total = (tenths.sum() + 5L).floorDiv(10L)
    val pounds = tenths.map { it.floorDiv(10L) }.toMutableList()
    var short = total - pounds.sum()
    tenths.indices
        .sortedWith(compareByDescending<Int> { tenths[it].mod(10L) }.thenBy { it })
        .forEach { i ->
            if (short > 0) {
                pounds[i] += 1
                short -= 1
            }
        }
    return RotationReceipt(
        lines = exercises.mapIndexed { i, exercise ->
            RotationReceiptLine(
                cardKey = rotationCardKey(exercise),
                region = rotationPrimaryRegion(exercise.muscleGroups),
                pounds = pounds[i],
                reps = lineReps[i],
                bodyweight = lineBodyweight[i],
            )
        },
        totalPounds = total,
        sets = sets,
        reps = lineReps.sum(),
    )
}

/** "chest", "back"… A lift with no known region prints no word rather than "unknown". */
internal fun rotationReceiptRegionLabel(region: BodyPartRegion): String? =
    region.takeIf { it != BodyPartRegion.UNKNOWN }?.displayName?.lowercase()

internal fun rotationReceiptDescription(receipt: RotationReceipt): String = buildString {
    append("Tally: ${receipt.sets} ${if (receipt.sets == 1) "set" else "sets"}, ")
    append("${receipt.reps} ${if (receipt.reps == 1) "rep" else "reps"}, ${groupThousands(receipt.totalPounds)} pounds total")
    receipt.lines.forEachIndexed { index, line ->
        append(". ")
        append((index + 1).toString().padStart(2, '0'))
        rotationReceiptRegionLabel(line.region)?.let { append(" ").append(it) }
        append(" ${groupThousands(line.pounds)} pounds")
    }
}

/** A day that moved no weight (all bodyweight) splits its bar by reps instead of pounds. */
internal fun rotationTallyByReps(receipt: RotationReceipt): Boolean = receipt.totalPounds == 0L

/** What the bar splits for one lift: its pounds, or its reps on a [byReps] day. */
internal fun rotationTallyShare(line: RotationReceiptLine, byReps: Boolean): Long =
    if (byReps) line.reps.toLong() else line.pounds

/** A lift's legend value: its pounds, its reps on a [byReps] day, BW for a bodyweight lift with no added load. */
internal fun rotationTallyLegendValue(line: RotationReceiptLine, byReps: Boolean): String = when {
    byReps -> groupThousands(line.reps)
    line.pounds == 0L && line.bodyweight -> "BW"
    else -> groupThousands(line.pounds)
}

/** Two legend columns hold five-digit pounds comfortably; past six lifts a third keeps the head short. */
internal fun rotationTallyLegendColumns(lines: Int): Int = if (lines > 6) 3 else 2

/**
 * The bar's segments left to right, as (line index, width). Lifts with no share get none. Widths
 * fill [width] less a [gap] between neighbours, in proportion to the shares, except that every
 * segment is at least [minWidth] (or an even split, if that many minimums would not fit) so a small
 * lift still shows; the larger segments give up the difference in proportion.
 */
internal fun rotationTallyBarSegments(shares: List<Long>, width: Float, gap: Float, minWidth: Float): List<Pair<Int, Float>> {
    val shown = shares.indices.filter { shares[it] > 0L }
    if (shown.isEmpty() || width <= 0f) return emptyList()
    val available = (width - gap * (shown.size - 1)).coerceAtLeast(0f)
    val floor = minOf(minWidth, available / shown.size)
    val widths = FloatArray(shown.size)
    val pinned = BooleanArray(shown.size)
    // Pin anything that would fall under the floor, then share what is left among the rest; pinning
    // one can push another under, so repeat until nothing new is pinned.
    while (true) {
        val free = available - floor * pinned.count { it }
        val freeShare = shown.indices.filter { !pinned[it] }.sumOf { shares[shown[it]] }.toFloat()
        var changed = false
        shown.indices.forEach { k ->
            if (pinned[k]) {
                widths[k] = floor
            } else {
                widths[k] = if (freeShare > 0f) free * shares[shown[k]] / freeShare else 0f
                if (widths[k] < floor) {
                    pinned[k] = true
                    changed = true
                }
            }
        }
        if (!changed) break
    }
    return shown.mapIndexed { k, line -> line to widths[k] }
}

private val TallyBarHeight = 7.dp
private val TallyBarGap = 2.dp
private val TallyBarMin = 3.dp

/**
 * The day's tally, set at the right of the summary's head: total pounds and reps on one line, a bar
 * in lift colours showing where the work went, and each lift's share under it keyed by its row
 * number. Screen readers hear the same receipt the old footer read out.
 */
@Composable
internal fun CompletedWorkoutTally(receipt: RotationReceipt, modifier: Modifier = Modifier) {
    val description = remember(receipt) { rotationReceiptDescription(receipt) }
    val byReps = rotationTallyByReps(receipt)
    val shares = remember(receipt, byReps) { receipt.lines.map { rotationTallyShare(it, byReps) } }
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TallyFigure(
                value = groupThousands(if (byReps) receipt.reps.toLong() else receipt.totalPounds),
                unit = if (byReps) noun(receipt.reps, "rep") else "lb",
                size = 18.sp,
            )
            if (!byReps) TallyFigure(value = groupThousands(receipt.reps), unit = "reps", size = 12.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 7.dp)
                .height(TallyBarHeight),
        ) {
            val radius = CornerRadius(2.dp.toPx())
            var x = 0f
            rotationTallyBarSegments(shares, size.width, TallyBarGap.toPx(), TallyBarMin.toPx()).forEach { (line, w) ->
                drawRoundRect(receipt.lines[line].region.color, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = radius)
                x += w + TallyBarGap.toPx()
            }
        }
        val columns = rotationTallyLegendColumns(receipt.lines.size)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            receipt.lines.withIndex().chunked(columns).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { (index, line) ->
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f).height(12.dp)) {
                            TallyLegendEntry(index = index, line = line, value = rotationTallyLegendValue(line, byReps))
                        }
                    }
                    repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TallyFigure(value: String, unit: String, size: TextUnit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            fontFamily = LogDisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = size,
            lineHeight = size,
            letterSpacing = if (size > 14.sp) (-0.3).sp else 0.sp,
            color = EspressoPalette.ink,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = unit,
            fontFamily = LogUiFontFamily,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            color = EspressoPalette.dim,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 3.dp).alignByBaseline(),
        )
    }
}

@Composable
private fun TallyLegendEntry(index: Int, line: RotationReceiptLine, value: String) {
    // A wide value (a five-digit lift in a three-column legend) scales down rather than clipping.
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.shrinkToFitStart()) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            fontFamily = LogDisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            color = line.region.color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alignByBaseline(),
        )
        if (value == "BW") {
            Text(
                text = value,
                fontFamily = SheetSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                letterSpacing = 1.sp,
                color = EspressoPalette.dim,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 4.dp).alignByBaseline(),
            )
        } else {
            Text(
                text = value,
                fontFamily = LogDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                color = EspressoPalette.ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 4.dp).alignByBaseline(),
            )
        }
    }
}

/** Lays its content out at natural width and, only if it would not fit, scales it down from the start edge. */
private fun Modifier.shrinkToFitStart(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val available = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    val scale = min(1f, available.toFloat() / placeable.width.coerceAtLeast(1))
    layout(min(placeable.width, available), placeable.height) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
    }
}

// ───────────────────────────── Motion math ─────────────────────────────

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun reopenSeg(ms: Float, from: Float, to: Float): Float = ((ms - from) / (to - from)).coerceIn(0f, 1f)
internal fun reopenEaseOutQuart(t: Float): Float = 1f - (1f - t).pow(4)
internal fun reopenEaseOutCubic(t: Float): Float = 1f - (1f - t).pow(3)
internal fun reopenEaseInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Back easing: overshoots by `overshoot` and settles at 1. */
internal fun reopenBack(t: Float, overshoot: Float): Float =
    1f + (overshoot + 1f) * (t - 1f).pow(3) + overshoot * (t - 1f).pow(2)

/** The latch is pressed from 0 to this; releasing plays on from here. */
internal const val REOPEN_PRESS_MS = 80f
internal const val REOPEN_LID_START_MS = 110f
internal const val REOPEN_END_MS = 820f

@Immutable
internal data class ReopenLatchPose(
    /** 0 resting keycap, 1 pushed all the way in; below 0 is the little lift on release. */
    val depth: Float,
    val iconDegrees: Float,
    /** 0..1 life of the ring that leaves the button on release; 0 when there is none. */
    val pulse: Float,
    val alpha: Float,
    val ringAlpha: Float,
    val captionAlpha: Float,
)

internal fun reopenLatchPose(ms: Float): ReopenLatchPose {
    val press = reopenSeg(ms, 0f, REOPEN_PRESS_MS)
    val release = reopenSeg(ms, REOPEN_PRESS_MS, 240f)
    val depth = if (ms < REOPEN_PRESS_MS) reopenEaseOutCubic(press) else 1f - reopenBack(release, 2.2f)
    // The ↺ winds back a little under the finger, then turns the whole way round as it lets go.
    val icon = if (ms < REOPEN_PRESS_MS) -28f * reopenEaseOutCubic(press)
    else -28f + (-360f + 28f) * reopenEaseOutQuart(reopenSeg(ms, REOPEN_PRESS_MS, 620f))
    val pulse = reopenSeg(ms, REOPEN_PRESS_MS, 520f)
    return ReopenLatchPose(
        depth = depth,
        iconDegrees = icon,
        pulse = if (pulse > 0f && pulse < 1f) pulse else 0f,
        alpha = 1f - reopenEaseOutCubic(reopenSeg(ms, 240f, 460f)),
        ringAlpha = 1f - reopenEaseOutCubic(reopenSeg(ms, REOPEN_LID_START_MS, 260f)),
        captionAlpha = 1f - reopenSeg(ms, 60f, 200f),
    )
}

@Immutable
internal data class ReopenLidPose(
    val rotationDegrees: Float,
    val liftDp: Float,
    val alpha: Float,
    val shade: Float,
    val deckOffsetDp: Float,
    val deckScrim: Float,
    /** The reopened deck is not shown around the latch until the card starts to lift. */
    val deckAlpha: Float,
)

/** The summary card leaves fast and settles slow; the reopened deck settles in under it. */
internal fun reopenLidPose(ms: Float): ReopenLidPose {
    val t = reopenSeg(ms, REOPEN_LID_START_MS, REOPEN_END_MS)
    val o = reopenEaseOutQuart(t)
    return ReopenLidPose(
        rotationDegrees = 80f * o,
        liftDp = -10f * sin((PI * (t * 1.4f).coerceIn(0f, 1f)).toFloat()),
        alpha = 1f - reopenEaseInOutCubic(((t - 0.12f) / 0.6f).coerceIn(0f, 1f)),
        shade = (o * 0.45f).coerceIn(0f, 0.45f),
        deckOffsetDp = 8f * (1f - reopenEaseOutCubic(t)),
        deckScrim = if (ms > REOPEN_LID_START_MS) 0.55f * (1f - o) else 0f,
        deckAlpha = (o / 0.35f).coerceIn(0f, 1f),
    )
}

internal const val ROW_FLIGHT_PRESS_MS = 90f
internal const val ROW_FLIGHT_OPEN_MS = 730f
internal const val ROW_FLIGHT_CLOSE_MS = 560f

/** Progress (0 row, 1 card) and the row's press tint at `ms` into an opening or a closing. */
internal fun rowFlightProgress(ms: Float, opening: Boolean): Pair<Float, Float> =
    if (opening) {
        reopenEaseOutQuart(reopenSeg(ms, ROW_FLIGHT_PRESS_MS, ROW_FLIGHT_OPEN_MS)) to
            reopenEaseOutCubic(reopenSeg(ms, 0f, ROW_FLIGHT_PRESS_MS))
    } else {
        val t = reopenSeg(ms, 0f, ROW_FLIGHT_CLOSE_MS)
        (1f - reopenEaseOutQuart(t)) to (1f - reopenEaseOutCubic(((t - 0.6f) / 0.4f).coerceIn(0f, 1f)))
    }

// ───────────────────────────── The latch ─────────────────────────────

private val LatchLip = Color(0xFFB8431F)
private val LatchCapLight = Color(0xFFF7875F)
private val LatchCapDark = Color(0xFFD9542E)
private val LatchSize = 64.dp
private val LatchRing = 6.dp

/** How far the latch hangs below the card edge: half the ringed button, a gap, the caption. */
internal val REOPEN_LATCH_BELOW: Dp = LatchSize / 2 + LatchRing + 4.dp + 16.dp

/**
 * The Reopen button, sitting on the card's bottom edge like a compact's latch. It clicks like a
 * key: the cap sinks onto its lip under the finger, springs back when released, and the arrow
 * winds round. What it does is unchanged.
 */
@Composable
internal fun ReopenLatch(
    ms: Float,
    interactive: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pose = reopenLatchPose(ms)
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> onPressedChange(true)
                is PressInteraction.Cancel -> onPressedChange(false)
            }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { alpha = pose.alpha }
            .then(
                if (interactive) {
                    Modifier
                        .semantics {
                            role = Role.Button
                            contentDescription = "Reopen completed workout"
                        }
                        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    // The copy that leaves with the card is only a picture of the button.
                    Modifier.clearAndSetSemantics { }
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(LatchSize + LatchRing * 2)) {
            // The ring in the page colour makes the button read as cut into the card's edge.
            Box(
                Modifier
                    .matchParentSizeCompat()
                    .graphicsLayer { alpha = pose.ringAlpha }
                    .drawBehind { drawCircle(EspressoPalette.bg) },
            )
            if (pose.pulse > 0f) {
                Box(
                    Modifier
                        .size(LatchSize)
                        .graphicsLayer {
                            scaleX = 1f + 0.9f * reopenEaseOutCubic(pose.pulse)
                            scaleY = scaleX
                            alpha = 0.75f * (1f - reopenEaseOutCubic(pose.pulse))
                        }
                        .drawBehind { drawCircle(EspressoPalette.chest, style = Stroke(2.dp.toPx())) },
                )
            }
            val depth = pose.depth
            // The lip stays put; the cap travels down onto it.
            Box(Modifier.size(LatchSize).graphicsLayer { translationY = 4.dp.toPx() }.drawBehind { drawCircle(LatchLip) })
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(LatchSize)
                    .graphicsLayer { translationY = 3.dp.toPx() * depth }
                    .espressoButtonShadow(EspressoPalette.chest.copy(alpha = (1f - 0.35f * depth.coerceIn(0f, 1f))), CircleShape)
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                0f to LatchCapLight,
                                0.55f to EspressoPalette.chest,
                                1f to LatchCapDark,
                                center = Offset(size.width * 0.38f, size.height * 0.28f),
                                radius = size.width * 0.85f,
                            ),
                        )
                        val pressed = depth.coerceIn(0f, 1f)
                        if (pressed > 0f) {
                            drawCircle(Color.Black.copy(alpha = 0.08f * pressed))
                            drawCircle(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.28f * pressed),
                                    0.35f to Color.Transparent,
                                ),
                            )
                        }
                    },
            ) {
                ReopenArrow(degrees = pose.iconDegrees, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Reopen",
            fontFamily = LogUiFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = EspressoPalette.dim,
            modifier = Modifier.graphicsLayer { alpha = pose.captionAlpha }.clearAndSetSemantics { },
        )
    }
}

/** ↺, drawn like the other canvas icons on the log card. */
@Composable
private fun ReopenArrow(degrees: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.width / 24f
        val stroke = Stroke(width = 2.4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        rotate(degrees) {
            val arc = Path().apply {
                // M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8
                addArc(Rect(Offset(3f * s, 3f * s), Size(18f * s, 18f * s)), 180f, -270f)
                moveTo(12f * s, 3f * s)
                cubicTo(9.5f * s, 3f * s, 7.1f * s, 4f * s, 5.26f * s, 5.74f * s)
                lineTo(3f * s, 8f * s)
            }
            drawPath(arc, EspressoPalette.onColor, style = stroke)
            val head = Path().apply {
                moveTo(3f * s, 3f * s)
                lineTo(3f * s, 8f * s)
                lineTo(8f * s, 8f * s)
            }
            drawPath(head, EspressoPalette.onColor, style = stroke)
        }
    }
}

/** A Box that fills its parent Box without needing the BoxScope receiver. */
private fun Modifier.matchParentSizeCompat(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

// ───────────────────────────── Leaving the summary ─────────────────────────────

/**
 * Draws a copy of the summary where the summary was, without taking any room, so the next screen
 * can lay out underneath it while it leaves.
 */
internal fun Modifier.summaryGhost(offset: Offset, z: Float): Modifier = this
    .zIndex(z)
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        layout(placeable.width, 0) { placeable.place(offset.x.toInt(), offset.y.toInt()) }
    }

/**
 * The summary card swinging up off its latch. It hinges at the top of what is on screen
 * (`hingeFromTop` px below the card's own top), not at a top edge scrolled far out of view.
 */
internal fun Modifier.reopenLid(pose: ReopenLidPose, hingeFromTop: Float): Modifier = this
    .graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, if (size.height > 0f) (hingeFromTop / size.height).coerceIn(0f, 1f) else 0f)
        rotationX = pose.rotationDegrees
        translationY = pose.liftDp.dp.toPx()
        cameraDistance = 48f * density
        alpha = pose.alpha
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        if (pose.shade > 0f) drawRect(Color.Black.copy(alpha = pose.shade), blendMode = BlendMode.SrcAtop)
    }

/** The reopened deck settling in under the leaving card. */
internal fun Modifier.reopenDeckSettle(pose: ReopenLidPose?): Modifier = if (pose == null) this else this
    .graphicsLayer {
        translationY = pose.deckOffsetDp.dp.toPx()
        alpha = pose.deckAlpha
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        if (pose.deckScrim > 0f) drawRect(Color.Black.copy(alpha = pose.deckScrim), blendMode = BlendMode.SrcAtop)
    }

/** A row on its way into its exercise card (opening) or back out of it (closing). */
@Stable
internal class RowFlight(
    val cardKey: String,
    val exercise: ExerciseLog,
    val opening: Boolean,
    /** Where the pager sat in the root when the summary was last on screen. */
    val summaryOrigin: Offset,
) {
    val clock = Animatable(0f)
}

/** Where a summary row's pieces sit, in root coordinates, measured every layout. */
@Immutable
internal data class SummaryRowPieces(val row: Rect, val number: Rect, val name: Rect, val position: Int)

/** Where the exercise card and its coloured head sit, in root coordinates. */
@Immutable
internal data class ExerciseCardPlacement(val card: Rect, val head: Rect)

/** One frame of a row growing into (or shrinking back from) its card, in the pager's own coordinates. */
@Immutable
internal data class RowFlightFrame(
    val p: Float,
    /** Geometry progress: reaches 1 (landed) at p = 0.96, before the band hands over to the head. */
    val geo: Float,
    val tint: Float,
    val sheet: Rect,
    val radiusPx: Float,
    val bandHeightPx: Float,
    val bandColor: Color,
    val bandMix: Float,
    val deckAlpha: Float,
    val coverAlpha: Float,
    val overlayAlpha: Float,
)

internal fun rowFlightFrame(
    p: Float,
    tint: Float,
    row: SummaryRowPieces,
    card: ExerciseCardPlacement,
    accent: Color,
    rowInsetPx: Float,
    radiusPx: Float,
): RowFlightFrame {
    val g = (p / 0.96f).coerceIn(0f, 1f)
    val startLeft = row.row.left - rowInsetPx
    val startRight = row.row.right + rowInsetPx
    val sheet = Rect(
        left = mix(startLeft, card.card.left, g),
        top = mix(row.row.top, card.card.top, g),
        right = mix(startRight, card.card.right, g),
        bottom = mix(row.row.bottom, card.card.bottom, g),
    )
    val bandMix = (g * 1.8f).coerceIn(0f, 1f)
    return RowFlightFrame(
        p = p,
        geo = g,
        tint = tint,
        sheet = sheet,
        radiusPx = radiusPx * g,
        bandHeightPx = mix(row.row.height, card.head.height, g),
        bandColor = lerp(EspressoPalette.panel, accent, mix(0.16f, 1f, bandMix)),
        bandMix = bandMix,
        deckAlpha = (p / 0.15f).coerceIn(0f, 1f),
        coverAlpha = 1f - ((p - 0.45f) / 0.45f).coerceIn(0f, 1f),
        // The band and the travelling copies hold until the copies have landed on the head's own
        // text, then hand over; p creeps through this last stretch, so the crossfade is unhurried.
        overlayAlpha = 1f - ((p - 0.96f) / 0.04f).coerceIn(0f, 1f),
    )
}

/**
 * The real exercise card, seen only through the growing sheet: the body stays covered until the
 * head is nearly in place, and the coloured band hands over to the card's own head at the end.
 */
internal fun Modifier.rowFlightDeck(frame: RowFlightFrame?, origin: Offset, hidden: Boolean): Modifier = when {
    hidden -> this.graphicsLayer { alpha = 0f }
    frame == null -> this
    else -> rowFlightDeckFrame(frame, origin)
}

private fun Modifier.rowFlightDeckFrame(frame: RowFlightFrame, origin: Offset): Modifier = this
    .graphicsLayer { alpha = frame.deckAlpha }
    .drawWithContent {
        val sheet = frame.sheet.translate(-origin.x, -origin.y)
        val clip = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = sheet,
                    topLeft = CornerRadius(frame.radiusPx),
                    topRight = CornerRadius(frame.radiusPx),
                    bottomRight = CornerRadius.Zero,
                    bottomLeft = CornerRadius.Zero,
                ),
            )
        }
        clipPath(clip) {
            this@drawWithContent.drawContent()
            drawRect(
                EspressoPalette.panel,
                topLeft = Offset(sheet.left, sheet.top + frame.bandHeightPx),
                size = Size(sheet.width, (sheet.height - frame.bandHeightPx).coerceAtLeast(0f)),
                alpha = frame.coverAlpha,
            )
            drawRect(
                frame.bandColor,
                topLeft = sheet.topLeft,
                size = Size(sheet.width, frame.bandHeightPx),
                alpha = frame.overlayAlpha,
            )
        }
    }

/**
 * The number and the name travelling between the row and the card's head. Each end is drawn in
 * that end's own type, so the hand-off to the row and to the real head lands on the same pixels.
 */
@Composable
internal fun RowFlightCopies(
    frame: RowFlightFrame,
    row: SummaryRowPieces,
    card: ExerciseCardPlacement,
    origin: Offset,
    accent: Color,
    eyebrow: String,
    name: String,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val eyebrowStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = SheetSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 2.sp,
    )
    val prefixWidth = remember(measurer, eyebrowStyle) { measurer.measure("EX ", eyebrowStyle).size.width.toFloat() }
    val p = frame.geo
    val a = frame.overlayAlpha
    with(density) {
        // The card's head: padding 20 start, 18 top; eyebrow 12 tall, 8 gap, then the name.
        val headX = card.head.left + 20.dp.toPx() - origin.x
        val eyebrowY = card.head.top + 18.dp.toPx() - origin.y
        val nameY = eyebrowY + 20.dp.toPx()
        val nameWidth = (card.head.width - (20 + 20 + 8 + 36 + 6 + 36).dp.toPx()).coerceAtLeast(1f)
        val numStart = Offset(row.number.left - origin.x, row.number.top - origin.y)

        val numberColor = lerp(accent, EspressoPalette.onColor, frame.bandMix)
        FlightText(
            x = mix(numStart.x, headX + prefixWidth, p),
            y = mix(numStart.y, eyebrowY - 2.dp.toPx(), p),
            scale = mix(1f, 10f / 13f, p),
            alpha = a * (1f - ((p - 0.15f) / 0.35f).coerceIn(0f, 1f)),
        ) {
            Text(
                text = row.position.toString().padStart(2, '0'),
                fontFamily = LogDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = numberColor,
            )
        }
        FlightText(
            x = mix(numStart.x - prefixWidth * 1.3f, headX, p),
            y = mix(numStart.y + 1.dp.toPx(), eyebrowY, p),
            scale = mix(1.3f, 1f, p),
            alpha = a * ((p - 0.3f) / 0.3f).coerceIn(0f, 1f),
            width = nameWidth,
        ) {
            Text(
                text = eyebrow,
                style = eyebrowStyle,
                color = EspressoPalette.onColor.copy(alpha = 0.70f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The row sets its name at 15/18, the head at 22/24; same face, so one scaled copy meets
        // both ends. Where the head wraps to two lines, the one-line copy hands over mid-flight.
        val s0 = 15f / 22f
        val nameStartY = row.name.top - origin.y + (18.dp.toPx() - 24.dp.toPx() * s0) / 2f
        val nameX = mix(row.name.left - origin.x, headX, p)
        val nameYNow = mix(nameStartY, nameY, p)
        val nameScale = mix(s0, 1f, p)
        val nameColor = lerp(EspressoPalette.ink, EspressoPalette.onColor, frame.bandMix)
        val wrapped = ((p - 0.35f) / 0.25f).coerceIn(0f, 1f)
        FlightText(x = nameX, y = nameYNow, scale = nameScale, alpha = a * (1f - wrapped)) {
            Text(text = name, style = rotationExerciseNameStyle(), color = nameColor, maxLines = 1, softWrap = false)
        }
        FlightText(x = nameX, y = nameYNow, scale = nameScale, alpha = a * wrapped, width = nameWidth) {
            Text(
                text = name,
                style = rotationExerciseNameStyle(),
                color = nameColor,
                maxLines = ROTATION_NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One travelling text, drawn at a fractional position and scale from its top-left corner. */
@Composable
private fun FlightText(
    x: Float,
    y: Float,
    scale: Float,
    alpha: Float,
    width: Float? = null,
    content: @Composable () -> Unit,
) {
    if (alpha <= 0f) return
    Box(
        Modifier
            .zIndex(2f)
            .layout { measurable, _ ->
                val c = if (width != null) Constraints.fixedWidth(width.toInt()) else Constraints()
                val placeable = measurable.measure(c)
                layout(0, 0) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = x
                translationY = y
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clearAndSetSemantics { },
    ) { content() }
}
