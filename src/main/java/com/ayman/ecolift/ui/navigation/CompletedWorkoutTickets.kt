package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.viewmodel.GhostSetUi
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * The sets under each lift on the completed-workout summary, as tickets
 * (spec docs/superpowers/specs/2026-09-22-summary-set-tickets-design.md). The stub holds last
 * session's matching set and the body today's; colour lands only on the number that changed. Each
 * time a day's summary comes into view, each ticket prints with last time on its body, then last
 * time lifts into the stub while today's numbers roll in.
 */

// ───────────────────────────── What a ticket says ─────────────────────────────

internal enum class TicketMark { NONE, UP, DOWN }

@Immutable
internal data class SetTicket(
    /** Stored tenths of a pound, as logged. */
    val weight: Int?,
    val reps: Int,
    val isBodyweight: Boolean,
    val isWarmup: Boolean,
    /** Last session's matching set, by the Log card's rule; null prints NEW. */
    val last: GhostSetUi?,
    val weightMark: TicketMark,
    val repsMark: TicketMark,
)

/** One ticket per set, each measured against the set it would meet on the Log card. */
internal fun rotationSetTickets(sets: List<LoggedSet>, ghosts: List<GhostSetUi>): List<SetTicket> {
    val kinds = sets.map { it.isWarmup }
    return sets.mapIndexed { index, set ->
        val weight = rotationDisplayedWeightOf(set)
        val reps = rotationDisplayedRepsOf(set) ?: 0
        val last = rotationMatchedGhost(ghosts, kinds, index)
        val (weightMark, repsMark) = rotationTicketMarks(weight, reps, last)
        SetTicket(weight, reps, set.isBodyweight, set.isWarmup, last, weightMark, repsMark)
    }
}

/**
 * Heavier than last time is a step up: the weight is marked and the reps are not held against it,
 * because fewer reps at a new weight is progress, not a drop. At the same weight the reps decide.
 * Lighter marks the weight. Weights compare in stored tenths, so the comparison is exact.
 */
internal fun rotationTicketMarks(weight: Int?, reps: Int, last: GhostSetUi?): Pair<TicketMark, TicketMark> {
    if (last == null) return TicketMark.NONE to TicketMark.NONE
    val now = weight ?: 0
    val then = last.weightLbs ?: 0
    if (now != then) return (if (now > then) TicketMark.UP else TicketMark.DOWN) to TicketMark.NONE
    val lastReps = last.reps ?: 0
    return TicketMark.NONE to when {
        reps > lastReps -> TicketMark.UP
        reps < lastReps -> TicketMark.DOWN
        else -> TicketMark.NONE
    }
}

/** "150", "62.5", "BW", "BW+25": the load without spaces, so three tickets fit a line. */
internal fun rotationTicketLoad(weight: Int?, isBodyweight: Boolean): String =
    rotationLoadLabel(weight, isBodyweight).replace(" ", "")

internal fun rotationTicketLastLabel(last: GhostSetUi): String =
    "${rotationTicketLoad(last.weightLbs, last.isBodyweight)}×${last.reps ?: 0}"

/** One ticket, read aloud: "150×10 last 150×9", "warm-up 95×10 last 95×8", "150×6 new". */
internal fun rotationTicketDescription(ticket: SetTicket): String = buildString {
    if (ticket.isWarmup) append("warm-up ")
    append(rotationTicketLoad(ticket.weight, ticket.isBodyweight)).append('×').append(ticket.reps)
    append(ticket.last?.let { " last ${rotationTicketLastLabel(it)}" } ?: " new")
}

// ───────────────────────────── Motion ─────────────────────────────

internal const val TICKET_PRINT_MS = 300f
internal const val TICKET_LIFT_START_MS = 520f
internal const val TICKET_LIFT_MS = 400f
internal const val TICKET_ROLL_START_MS = 580f
internal const val TICKET_ROLL_MS = 440f
internal const val TICKET_ROLL_GAP_MS = 70f
internal const val TICKET_POP_START_MS = TICKET_LIFT_START_MS + TICKET_LIFT_MS
internal const val TICKET_POP_MS = 300f
internal const val TICKET_LOCAL_END_MS = TICKET_POP_START_MS + TICKET_POP_MS

/** Any clock at or past this draws every ticket at rest. */
internal const val TICKETS_AT_REST_MS = 100_000f

/**
 * Rows print one after another and the sets in a row a beat apart. A long day squeezes its row gap
 * so the whole print still lands in about half a second.
 */
internal fun ticketDelayMs(row: Int, set: Int, rows: Int): Float {
    val rowStep = if (rows <= 1) 0f else min(115f, 520f / (rows - 1))
    return row * rowStep + set * 55f
}

internal fun ticketsEndMs(rows: Int, maxSets: Int): Float =
    ticketDelayMs((rows - 1).coerceAtLeast(0), (maxSets - 1).coerceAtLeast(0), rows) + TICKET_LOCAL_END_MS

/** 0 nothing printed yet … 1 the whole ticket out. */
internal fun ticketPrint(ms: Float): Float = reopenEaseOutCubic(reopenSeg(ms, 0f, TICKET_PRINT_MS))

/** Last time's set on its way from the body into the stub: 0 still on the body, 1 landed. */
internal fun ticketLift(ms: Float): Float =
    reopenEaseOutCubic(reopenSeg(ms, TICKET_LIFT_START_MS, TICKET_POP_START_MS))

/** The travelling copy fades up as it leaves, so it never sits doubled on the body's digits. */
internal fun ticketLiftAlpha(ms: Float): Float =
    if (ms >= TICKET_POP_START_MS) 0f
    else reopenSeg(ms, TICKET_LIFT_START_MS, TICKET_LIFT_START_MS + TICKET_LIFT_MS * 0.18f)

/**
 * A changed number rolling from last time to today, overshooting a touch before it settles.
 * [order] is 0 for the first number that rolls and 1 for the second.
 */
internal fun ticketRoll(ms: Float, order: Int): Float {
    val start = TICKET_ROLL_START_MS + order * TICKET_ROLL_GAP_MS
    val t = reopenSeg(ms, start, start + TICKET_ROLL_MS)
    return if (t <= 0f || t >= 1f) t else reopenBack(t, 1.2f)
}

/** The stub stays empty while the body still shows last time. */
internal fun ticketStubShown(ms: Float): Boolean = ms >= TICKET_POP_START_MS

/** Last time lands in the stub with a small bump. */
internal fun ticketPopScale(ms: Float): Float {
    val t = reopenSeg(ms, TICKET_POP_START_MS, TICKET_LOCAL_END_MS)
    if (t <= 0f || t >= 1f) return 1f
    val bump = if (t < 0.38f) reopenEaseOutCubic(t / 0.38f) else 1f - reopenEaseInOutCubic((t - 0.38f) / 0.62f)
    return 1f + 0.22f * bump
}

/** LAST (with W, or NEW on its own) slides in as last time lands. */
internal fun ticketLabelIn(ms: Float): Float =
    reopenEaseOutCubic(reopenSeg(ms, TICKET_POP_START_MS, TICKET_POP_START_MS + 240f))

/** NEW has nothing landing beside it, so it pops in by itself. */
internal fun ticketNewScale(ms: Float): Float {
    val t = reopenSeg(ms, TICKET_POP_START_MS, TICKET_LOCAL_END_MS)
    return if (t < 0.6f) 0.6f + 0.55f * reopenEaseOutCubic(t / 0.6f)
    else 1.15f - 0.15f * reopenEaseInOutCubic((t - 0.6f) / 0.4f)
}

// ───────────────────────────── Paper ─────────────────────────────

private val TicketCream = Color(0xFFEFE7DA)
private val TicketCreamLow = Color(0xFFE1D6C5)
private val TicketInk = EspressoPalette.onColor
private val TicketUp = Color(0xFF2C7A47)
private val TicketDown = Color(0xFFA5621A)
private val TICKET_HEIGHT = 44.dp
private val TICKET_STUB = 16.dp
private val TICKET_CORNER = 4.5.dp
private val TICKET_NOTCH = 4.5.dp
private val TICKET_GAP = 6.dp
private val STUB_GAP = 4.dp
private const val TICKETS_PER_LINE = 4

private fun TicketMark.ink(): Color = when (this) {
    TicketMark.UP -> TicketUp
    TicketMark.DOWN -> TicketDown
    TicketMark.NONE -> TicketInk
}

// Lazy: the app's font families load at startup, after this file's properties first initialise.
private val ticketKeyStyle by lazy {
    TextStyle(
        fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 7.5.sp, lineHeight = 9.sp, letterSpacing = 1.2.sp,
    )
}
private val ticketWarmStyle by lazy {
    TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 11.sp)
}
private val ticketLastStyle by lazy {
    TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 12.sp)
}
private val ticketBodyStyle by lazy {
    TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, lineHeight = 18.sp)
}
private val ticketTimesStyle by lazy {
    TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
}

/** A small fixed speckle, tiled over the paper so it reads as paper rather than a flat fill. */
private val paperGrain: ImageBitmap by lazy {
    val side = 64
    val image = ImageBitmap(side, side)
    val canvas = Canvas(image)
    val paint = Paint()
    val random = Random(922)
    repeat(900) {
        paint.color = Color(0.11f, 0.09f, 0.07f, 0.05f + random.nextFloat() * 0.13f)
        val x = random.nextInt(side).toFloat()
        val y = random.nextInt(side).toFloat()
        canvas.drawRect(x, y, x + 1f, y + 1f, paint)
    }
    image
}

/** Concave corners, and a notch each side where the stub tears off. */
internal fun ticketOutline(width: Float, height: Float, stub: Float, corner: Float, notch: Float): Path = Path().apply {
    moveTo(corner, 0f)
    lineTo(width - corner, 0f)
    arcTo(Rect(Offset(width, 0f), corner), 180f, -90f, false)
    lineTo(width, stub - notch)
    arcTo(Rect(Offset(width, stub), notch), 270f, -180f, false)
    lineTo(width, height - corner)
    arcTo(Rect(Offset(width, height), corner), 270f, -90f, false)
    lineTo(corner, height)
    arcTo(Rect(Offset(0f, height), corner), 0f, -90f, false)
    lineTo(0f, stub + notch)
    arcTo(Rect(Offset(0f, stub), notch), 90f, -180f, false)
    lineTo(0f, corner)
    arcTo(Rect(Offset.Zero, corner), 90f, -90f, false)
    close()
}

/** Lays its content out at natural width and scales it down, centred, only if it would not fit. */
private fun Modifier.shrinkToFit(sidePadding: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val available = if (constraints.hasBoundedWidth) {
        (constraints.maxWidth - 2 * sidePadding.roundToPx()).coerceAtLeast(1)
    } else {
        placeable.width
    }
    val scale = min(1f, available.toFloat() / placeable.width.coerceAtLeast(1))
    val width = min(placeable.width, available)
    layout(width, placeable.height) {
        placeable.placeWithLayer((width - placeable.width) / 2, 0) {
            scaleX = scale
            scaleY = scale
        }
    }
}

/**
 * A lift's tickets, four to a line, so a usual three- or four-set lift takes one line and five lifts
 * fit on a phone without scrolling. [ms] is the summary's ticket clock; [row] of [rows] places this
 * lift in the print order.
 */
@Composable
internal fun CompletedWorkoutTickets(
    tickets: List<SetTicket>,
    lift: Color,
    row: Int,
    rows: Int,
    ms: () -> Float,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TICKET_GAP),
        // The row's own label reads every ticket out; the tickets add nothing of their own.
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
    ) {
        tickets.chunked(TICKETS_PER_LINE).forEachIndexed { line, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(TICKET_GAP), modifier = Modifier.fillMaxWidth()) {
                chunk.forEachIndexed { i, ticket ->
                    val delay = ticketDelayMs(row, line * TICKETS_PER_LINE + i, rows)
                    SetTicketView(ticket = ticket, lift = lift, ms = { ms() - delay }, modifier = Modifier.weight(1f))
                }
                repeat(TICKETS_PER_LINE - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SetTicketView(ticket: SetTicket, lift: Color, ms: () -> Float, modifier: Modifier = Modifier) {
    val last = ticket.last
    val load = rotationTicketLoad(ticket.weight, ticket.isBodyweight)
    val reps = ticket.reps.toString()
    val lastLoad = last?.let { rotationTicketLoad(it.weightLbs, it.isBodyweight) }
    val lastReps = last?.let { (it.reps ?: 0).toString() }
    val weightRolls = lastLoad != null && lastLoad != load
    val repsRolls = lastReps != null && lastReps != reps
    // Where last time lands: the stub's value, beside LAST.
    var labelWidth by remember { mutableIntStateOf(0) }
    var valueWidth by remember { mutableIntStateOf(0) }
    var liftWidth by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .height(TICKET_HEIGHT)
            .graphicsLayer {
                translationY = -6.dp.toPx() * (1f - ticketPrint(ms()))
                alpha = if (ticket.isWarmup) 0.62f else 1f
            }
            // Printing: the ticket feeds out top first.
            .drawWithContent {
                val printed = ticketPrint(ms())
                if (printed > 0f) clipRect(bottom = size.height * printed) { this@drawWithContent.drawContent() }
            }
            .drawWithCache {
                val stub = TICKET_STUB.toPx()
                val notch = TICKET_NOTCH.toPx()
                val outline = ticketOutline(size.width, size.height, stub, TICKET_CORNER.toPx(), notch)
                val paper = Brush.verticalGradient(listOf(lerp(TicketCream, lift, 0.22f), lerp(TicketCreamLow, lift, 0.27f)))
                val grain = ShaderBrush(ImageShader(paperGrain, TileMode.Repeated, TileMode.Repeated))
                val from = notch + 6.dp.toPx()
                val to = size.width - notch - 6.dp.toPx()
                val holes = max(2, ((to - from) / 4.2.dp.toPx()).toInt() + 1)
                val holeRadius = 1.05.dp.toPx()
                onDrawBehind {
                    clipPath(outline) {
                        drawRect(paper)
                        drawRect(grain)
                        drawRect(lift, size = Size(size.width, stub))
                    }
                    // Punched: the card shows through the tear line.
                    repeat(holes) { k ->
                        drawCircle(EspressoPalette.panel, holeRadius, Offset(from + (to - from) * k / (holes - 1), stub))
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(TICKET_STUB)) {
            if (last == null) {
                Text(
                    text = "NEW",
                    style = ticketKeyStyle,
                    color = TicketInk.copy(alpha = 0.9f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        val t = ms()
                        alpha = ticketLabelIn(t)
                        scaleX = ticketNewScale(t)
                        scaleY = scaleX
                    },
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.shrinkToFit(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .onSizeChanged { labelWidth = it.width }
                            .graphicsLayer {
                                val a = ticketLabelIn(ms())
                                alpha = a
                                translationX = -6.dp.toPx() * (1f - a)
                            },
                    ) {
                        if (ticket.isWarmup) {
                            Text("W", style = ticketWarmStyle, color = TicketInk, maxLines = 1, softWrap = false)
                            Spacer(Modifier.width(STUB_GAP))
                        }
                        Text("LAST", style = ticketKeyStyle, color = TicketInk.copy(alpha = 0.9f), maxLines = 1, softWrap = false)
                    }
                    Spacer(Modifier.width(STUB_GAP))
                    Text(
                        text = buildAnnotatedString {
                            append(lastLoad.orEmpty())
                            withStyle(SpanStyle(color = TicketInk.copy(alpha = 0.45f))) { append("×") }
                            append(lastReps.orEmpty())
                        },
                        style = ticketLastStyle,
                        color = TicketInk,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .onSizeChanged { valueWidth = it.width }
                            .graphicsLayer {
                                val t = ms()
                                alpha = if (ticketStubShown(t)) 1f else 0f
                                scaleX = ticketPopScale(t)
                                scaleY = scaleX
                            },
                    )
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(top = TICKET_STUB).height(TICKET_HEIGHT - TICKET_STUB),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.shrinkToFit(6.dp),
            ) {
                RollingNumber(
                    from = if (weightRolls) lastLoad else null,
                    to = load,
                    up = (ticket.weight ?: 0) >= (last?.weightLbs ?: 0),
                    progress = { ticketRoll(ms(), 0) },
                    color = ticket.weightMark.ink(),
                )
                Text("×", style = ticketTimesStyle, color = TicketInk.copy(alpha = 0.38f), maxLines = 1, softWrap = false)
                RollingNumber(
                    from = if (repsRolls) lastReps else null,
                    to = reps,
                    up = ticket.reps >= (last?.reps ?: 0),
                    progress = { ticketRoll(ms(), if (weightRolls) 1 else 0) },
                    color = ticket.repsMark.ink(),
                )
            }
            if (last != null) {
                // Last time on its way up: from the body's centre to the stub value's centre.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .onSizeChanged { liftWidth = it.width }
                        .graphicsLayer {
                            val t = ms()
                            val p = ticketLift(t)
                            alpha = ticketLiftAlpha(t)
                            val stub = TICKET_STUB.toPx()
                            val bodyCentre = stub + (TICKET_HEIGHT.toPx() - stub) / 2f
                            translationY = (stub / 2f - bodyCentre) * p
                            translationX = ((labelWidth + STUB_GAP.toPx()) / 2f) * p
                            val target = if (liftWidth > 0 && valueWidth > 0) valueWidth.toFloat() / liftWidth else 1f
                            scaleX = 1f + (target - 1f) * p
                            scaleY = scaleX
                        },
                ) {
                    Text(lastLoad.orEmpty(), style = ticketBodyStyle, color = TicketInk, maxLines = 1, softWrap = false)
                    Text("×", style = ticketTimesStyle, color = TicketInk.copy(alpha = 0.38f), maxLines = 1, softWrap = false)
                    Text(lastReps.orEmpty(), style = ticketBodyStyle, color = TicketInk, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * A number that changed: last time's value rolls out and today's rolls in, up when it went up and
 * down when it went down. With nothing to roll from it is plain text.
 */
@Composable
private fun RollingNumber(from: String?, to: String, up: Boolean, progress: () -> Float, color: Color) {
    if (from == null) {
        Text(to, style = ticketBodyStyle, color = color, maxLines = 1, softWrap = false)
        return
    }
    val measurer = rememberTextMeasurer()
    val fromWidth = remember(from) { measurer.measure(from, ticketBodyStyle).size.width }
    val toWidth = remember(to) { measurer.measure(to, ticketBodyStyle).size.width }
    val direction = if (up) 1f else -1f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .layout { measurable, _ ->
                val p = progress().coerceIn(0f, 1f)
                val width = (fromWidth + (toWidth - fromWidth) * p).roundToInt()
                val placeable = measurable.measure(Constraints.fixedWidth(width))
                layout(width, placeable.height) { placeable.place(0, 0) }
            }
            .clipToBounds(),
    ) {
        Text(
            text = from,
            style = ticketBodyStyle,
            color = TicketInk,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .graphicsLayer { translationY = -direction * size.height * progress() },
        )
        Text(
            text = to,
            style = ticketBodyStyle,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .graphicsLayer { translationY = direction * size.height * (1f - progress()) },
        )
    }
}
