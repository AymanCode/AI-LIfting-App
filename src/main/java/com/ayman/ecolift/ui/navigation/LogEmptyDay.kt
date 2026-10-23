package com.ayman.ecolift.ui.navigation


import com.ayman.ecolift.web.util.countLabel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import com.ayman.ecolift.ui.theme.rememberReducedMotion
import com.ayman.ecolift.ui.theme.splitAccentColor
import com.ayman.ecolift.ui.viewmodel.ClipboardProgramUi
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.StakeUi
import com.ayman.ecolift.ui.viewmodel.TicketRowKind
import com.ayman.ecolift.ui.viewmodel.TicketRowUi
import com.ayman.ecolift.ui.viewmodel.TopSetUi
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.ui.viewmodel.stakeParts
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

// ---------------------------------------------------------------------------------------------
// Pure Helpers
// ---------------------------------------------------------------------------------------------

/**
 * Top set in a lift's completed session: highest weight, ties broken by higher reps.
 * Unchecked sets are ignored; warm-ups are ignored if non-warmup completed sets exist.
 */
internal fun topSetOfSession(sets: List<WorkoutSet>): TopSetUi? {
    val completed = sets.filter { it.completed }
    if (completed.isEmpty()) return null
    val eligible = completed.filter { !it.isWarmup }.ifEmpty { completed }
    val top = eligible.maxWithOrNull(
        compareBy<WorkoutSet> { it.weightLbs ?: 0 }
            .thenBy { it.reps ?: 0 }
    ) ?: return null
    val weight = WeightLbs.toLbs(top.weightLbs).roundToInt()
    val reps = top.reps ?: 0
    return TopSetUi(weight = weight, reps = reps, isBodyweight = top.isBodyweight)
}

/** Formats a top set for display: "225×5" or "BW×8" or "BW+25×5". */
internal fun formatTopSet(topSet: TopSetUi): String =
    if (topSet.isBodyweight) {
        if (topSet.weight > 0) "BW+${topSet.weight}×${topSet.reps}" else "BW×${topSet.reps}"
    } else {
        "${topSet.weight}×${topSet.reps}"
    }

/** Sum of completed checked set volume in whole pounds. */
internal fun totalPounds(sets: List<WorkoutSet>): Int =
    sets.filter { it.completed }.sumOf { set ->
        val weight = WeightLbs.toLbs(set.weightLbs)
        val reps = set.reps ?: 0
        weight * reps
    }.roundToInt()

/** "Sep 18" formatted date for sub-lines. */
internal fun formatMonthDay(iso: String): String = runCatching {
    val date = LocalDate.parse(iso)
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    "${months[date.monthNumber - 1]} ${date.dayOfMonth}"
}.getOrDefault(iso)

// ---------------------------------------------------------------------------------------------
// Ticket Shape
// ---------------------------------------------------------------------------------------------

/**
 * 14 dp rounded corners with two 7 dp punch-hole notches at the sides at y = 74 dp.
 */
class TicketShape(
    private val cornerRadius: Dp = 14.dp,
    private val notchRadius: Dp = 7.dp,
    private val notchCenterY: Dp = 74.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { cornerRadius.toPx() }
        val nr = with(density) { notchRadius.toPx() }
        val ny = with(density) { notchCenterY.toPx() }
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(w - corner, 0f)
            arcTo(Rect(w - 2 * corner, 0f, w, 2 * corner), -90f, 90f, false)
            lineTo(w, ny - nr)
            arcTo(Rect(w - nr, ny - nr, w + nr, ny + nr), -90f, -180f, false)
            lineTo(w, h - corner)
            arcTo(Rect(w - 2 * corner, h - 2 * corner, w, h), 0f, 90f, false)
            lineTo(corner, h)
            arcTo(Rect(0f, h - 2 * corner, 2 * corner, h), 90f, 90f, false)
            lineTo(0f, ny + nr)
            arcTo(Rect(-nr, ny - nr, nr, ny + nr), 90f, -180f, false)
            lineTo(0f, corner)
            arcTo(Rect(0f, 0f, 2 * corner, 2 * corner), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------------------------------------------------------------------------------------------
// Arrow Graphic
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TicketArrow(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(width = 14.dp, height = 12.dp)) {
        val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val midY = size.height / 2f
        drawLine(tint, Offset(1.dp.toPx(), midY), Offset(size.width - 1.dp.toPx(), midY), strokeWidth = stroke.width, cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo(size.width - 6.dp.toPx(), 1.dp.toPx())
            lineTo(size.width - 1.dp.toPx(), midY)
            lineTo(size.width - 6.dp.toPx(), size.height - 1.dp.toPx())
        }
        drawPath(head, tint, style = stroke)
    }
}

// ---------------------------------------------------------------------------------------------
// Empty Day Component: one learned ticket (spec 2026-09-28)
// ---------------------------------------------------------------------------------------------

/** "Tue" within the last six days of [shownDate], "Sep 23" before that. */
internal fun lastSessionLabel(lastIso: String, shownDate: String): String = runCatching {
    val last = LocalDate.parse(lastIso)
    val shown = LocalDate.parse(shownDate)
    val days = (shown.toEpochDays() - last.toEpochDays()).toInt()
    if (days in 1..6) last.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } else formatMonthDay(lastIso)
}.getOrDefault(lastIso)

/** A row's second line: its last top set (members and tail), or "last {date}" (faded); the stake sits beside it. */
internal fun ticketRowDetail(row: TicketRowUi): List<DataPart> = buildList {
    when (row.kind) {
        TicketRowKind.FADED -> row.lastDate?.let { add(filler("last ")); add(data(formatMonthDay(it))) }
        else -> row.topSetLabel?.let { add(data(it)) }
    }
}

/** What a screen reader and the QA harness read for one ledger row. */
internal fun ticketRowDescription(row: TicketRowUi, included: Boolean): String = buildList {
    add(row.name)
    row.usualName?.let { add("today only"); add("usual · $it") }
    add(if (row.lastSets.isEmpty()) "last sets —" else "last sets ${row.lastSets.joinToString(" · ") { it.label }}")
    row.lastSetsDateLabel?.let { add(it) }
    add(row.toBeatLabel?.let { "to beat $it" } ?: "first time")
    if (row.kind == TicketRowKind.TAIL) add("last time also")
    add(if (included) "included" else "not included")
}.joinToString(", ")


// ---------------------------------------------------------------------------------------------
// The suggested sheet (board: workshopping ui/suggested-card-paper/board.html, 2026-10-09)
// ---------------------------------------------------------------------------------------------

/** The paper the suggestion is printed on: cream, dark ink, hand-ink blue for last session's sets. */
internal object SheetPaper {
    val paper = Color(0xFFEFE7DA)
    val under1 = Color(0xFFE3DACB)
    val under2 = Color(0xFFD7CDBC)
    val ink = Color(0xFF1C1816)
    val sub = Color(0xFF5E5248)
    val rule = Color(0xFFDACDB8)
    val hand = Color(0xFF27479A)
    val arrow = Color(0xFF8A7C6F)
    val chip = Color(0xFFE2D8C8)
    val kraft = Color(0xFFCDBB9A)
    val kraftInk = Color(0xFF3E3329)
    val scrim = Color(0xFF0C0A09)
}

private val SheetShape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
private val TabShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
private val StubHeight = 54.dp

/** The list's two 16 dp gaps (the empty pager item sits between) plus a 2 dp tuck under the board. */
private val SheetHang = 34.dp
private const val SHEET_REST_TILT = -0.35f
private const val SHEET_LIFT_TILT = -1.4f
private const val SHEET_LIFT_SCALE = 1.025f
private const val SHEET_LIFT_MS = 220
private const val SHEET_FLIGHT_MS = 540
private const val FLIGHT_LAND_AT = 0.82f
private const val PUT_AWAY_MS = 320
private const val BRING_BACK_MS = 440
private const val TAB_IN_MS = 200
private const val TAB_OUT_MS = 140
private const val SCRIM_MS = 200
/** A hold on the sheet lands after this long; Compose Web's own long press is longer and gave no sign it was running. */
internal const val SHEET_LONG_PRESS_MS = 400L
/** How far the sheet rises and the list dims while a press is still charging; the hold's landing finishes both. */
private const val PRESS_LIFT = 0.55f
private const val PRESS_SCRIM = 0.5f
private const val PRESS_SETTLE_MS = 160
/** Picker sheets are the clip-slot sheet drawn at this scale. */
private const val PICKER_SHEET_SCALE = 0.82f
/** `away` below zero is the bring-back overshoot: this value is 6 dp below the clip. */
private const val AWAY_OVERSHOOT = -0.05f

private val SheetEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val PressEase = CubicBezierEasing(0.25f, 0.6f, 0.3f, 1f)

/** The sheet's own long-press timeout; everything else is the platform's. */
internal class SheetViewConfiguration(base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long get() = SHEET_LONG_PRESS_MS
}
private val PutAwayEase = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)
private val BringBackEase = CubicBezierEasing(0.2f, 0.8f, 0.25f, 1f)

/** One 128 px speckle, made at most once per process and tiled by a repeating shader. Never per frame. */
private val sheetGrain: ImageBitmap by lazy {
    val side = 128
    val image = ImageBitmap(side, side)
    val canvas = GraphicsCanvas(image)
    val paint = Paint()
    val random = Random(1009)
    repeat(2600) {
        paint.color = Color(0.22f, 0.16f, 0.10f, 0.03f + random.nextFloat() * 0.09f)
        val x = random.nextInt(side).toFloat()
        val y = random.nextInt(side).toFloat()
        canvas.drawRect(x, y, x + 1f, y + 1f, paint)
    }
    image
}
private val sheetGrainBrush: ShaderBrush by lazy { ShaderBrush(ImageShader(sheetGrain, TileMode.Repeated, TileMode.Repeated)) }

private fun DrawScope.grainedFill(color: Color) {
    drawRect(color)
    drawRect(sheetGrainBrush)
}

/** Clips only above the box's top edge: the line the sheet slides up under the clipboard through. */
private fun Modifier.clipAboveTop(): Modifier = drawWithContent {
    clipRect(left = -size.width, top = 0f, right = size.width * 2f, bottom = size.height * 4f + 4000f) {
        this@drawWithContent.drawContent()
    }
}

/** Lays [sheetWidth] of sheet out and reports it at [scale], scaled from its top centre (a draw-layer scale). */
private fun Modifier.fitSheet(sheetWidth: Dp, scale: Float): Modifier = layout { measurable, _ ->
    val w = sheetWidth.roundToPx()
    val placeable = measurable.measure(Constraints.fixedWidth(w))
    val outW = (w * scale).roundToInt()
    val outH = (placeable.height * scale).roundToInt()
    layout(outW, outH) {
        placeable.placeWithLayer(-(w - outW) / 2, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

/** Two static shadows: resting, and lifted (only its alpha moves). Elevation never animates. */
@Composable
private fun BoxScope.SheetShadows(lifted: () -> Float) {
    Box(Modifier.matchParentSize().shadow(14.dp, SheetShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black))
    Box(
        Modifier.matchParentSize().graphicsLayer {
            shadowElevation = 30.dp.toPx()
            shape = SheetShape
            clip = false
            alpha = lifted()
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    )
}

/** An underneath sheet: a flat rect with its split's stub, one flat contact shadow, no composable. */
private fun DrawScope.drawUnderSheet(paper: Color, accent: Color, dx: Float, dy: Float, degrees: Float) {
    val corner = CornerRadius(5.dp.toPx())
    val stub = StubHeight.toPx()
    translate(dx, dy) {
        rotate(degrees, pivot = Offset(size.width / 2f, 0f)) {
            drawRoundRect(Color.Black.copy(alpha = 0.3f), topLeft = Offset(0f, 5.dp.toPx()), size = size, cornerRadius = corner)
            drawRoundRect(paper, size = size, cornerRadius = corner)
            drawRoundRect(sheetGrainBrush, size = size, cornerRadius = corner)
            drawRoundRect(accent, topLeft = Offset(0f, size.height - stub), size = Size(size.width, stub), cornerRadius = corner)
        }
    }
}

/** The landing outline while a picked sheet flies in: dashed split colour, open at the clip. */
private fun DrawScope.drawLanding(color: Color) {
    val stroke = 2.dp.toPx()
    val r = 6.dp.toPx()
    val half = stroke / 2f
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(half, 0f)
        lineTo(half, h - r)
        quadraticTo(half, h - half, r, h - half)
        lineTo(w - r, h - half)
        quadraticTo(w - half, h - half, w - half, h - r)
        lineTo(w - half, 0f)
    }
    drawPath(path, color.copy(alpha = 0.75f), style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
}

/**
 * The empty day's suggestion: a paper sheet hanging from under the clipboard, the next two
 * splits' sheets underneath. Holding it unclips it into the picker; the X puts it away under the
 * clipboard for this date only, leaving a tab that brings it back. Every motion is a draw-layer
 * read of an animated value: nothing here recomposes or remeasures per frame.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LogEmptyDay(
    program: ClipboardProgramUi,
    allPrograms: List<ClipboardProgramUi>,
    included: Set<Long>,
    shownDate: String,
    pickerOpen: Boolean,
    placingColor: Color?,
    putAway: Boolean,
    onStart: (ClipboardProgramUi) -> Unit,
    onToggleRow: (ClipboardProgramUi, Long) -> Unit,
    onSwapRow: (ClipboardProgramUi, Long) -> Unit,
    onRenameProposal: (ClipboardProgramUi, String) -> Unit,
    onOpenPicker: () -> Unit,
    onPutAway: () -> Unit,
    onBringBack: () -> Unit,
    onSlotPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    hangUnderClipboard: Boolean = true,
) {
    val reduced = rememberReducedMotion()
    fun ms(duration: Int) = if (reduced) 0 else duration
    val lightHaptic = rememberLightHaptic()
    val scope = rememberCoroutineScope()
    val multi = allPrograms.size > 1

    val lift = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    var lifted by remember { mutableStateOf(false) }
    // Put-away belongs to a date: a new date starts from its own state, never mid-slide.
    val away = remember(shownDate) { Animatable(if (putAway) 1f else 0f) }
    val tabIn = remember(shownDate) { Animatable(if (putAway) 1f else 0f) }
    var busy by remember(shownDate) { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val holdable = multi && !putAway
    val baseViewConfiguration = LocalViewConfiguration.current
    val sheetViewConfiguration = remember(baseViewConfiguration) { SheetViewConfiguration(baseViewConfiguration) }

    // The hold shows from touch-down: while the press lasts the sheet rises and the list dims, so a
    // hold still charging looks different from nothing, and a release before it lands settles it back.
    // While the sheet is pressed, lifted or settling back it draws over the list, so its dim reaches the
    // clipboard above it; at rest it tucks back under the board.
    var raised by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, holdable) {
        if (!holdable || lifted) return@LaunchedEffect
        if (pressed) {
            raised = true
            launch { scrim.animateTo(PRESS_SCRIM, tween(ms(SHEET_LONG_PRESS_MS.toInt()), easing = PressEase)) }
            lift.animateTo(PRESS_LIFT, tween(ms(SHEET_LONG_PRESS_MS.toInt()), easing = PressEase))
        } else if (lift.value > 0f || scrim.value > 0f) {
            val dim = launch { scrim.animateTo(0f, tween(ms(PRESS_SETTLE_MS))) }
            lift.animateTo(0f, tween(ms(PRESS_SETTLE_MS)))
            dim.join()
            raised = false
        }
    }

    // A pick is flying in: the sheet underneath is hidden, so it drops its lift at once and the dim goes.
    LaunchedEffect(placingColor != null) {
        if (placingColor == null) return@LaunchedEffect
        lift.snapTo(0f)
        scrim.animateTo(0f, tween(ms(SCRIM_MS)))
    }
    // The picker closed without a pick: the sheet clips back on.
    LaunchedEffect(pickerOpen) {
        if (pickerOpen || !lifted) return@LaunchedEffect
        launch { scrim.animateTo(0f, tween(ms(SCRIM_MS))) }
        lift.animateTo(0f, tween(ms(SHEET_LIFT_MS), easing = SheetEase))
        lifted = false
        raised = false
    }

    val hold: (() -> Unit)? = if (holdable) {
        {
            if (!lifted && !busy) {
                lightHaptic()
                lifted = true
                // The picker opens the moment the hold lands; the rise and dim finish under it.
                onOpenPicker()
                scope.launch {
                    launch { scrim.animateTo(1f, tween(ms(SCRIM_MS))) }
                    lift.animateTo(1f, tween(ms(SHEET_LIFT_MS), easing = SheetEase))
                }
            }
        }
    } else null

    fun putAwayNow() {
        if (busy || lifted) return
        busy = true
        scope.launch {
            away.animateTo(1f, tween(ms(PUT_AWAY_MS), easing = PutAwayEase))
            tabIn.snapTo(0f)
            onPutAway()
            tabIn.animateTo(1f, tween(ms(TAB_IN_MS), easing = EaseOut))
            busy = false
        }
    }

    fun bringBackNow() {
        if (busy) return
        busy = true
        scope.launch {
            tabIn.animateTo(0f, tween(ms(TAB_OUT_MS), easing = EaseIn))
            away.snapTo(1f)
            onBringBack()
            if (reduced) away.snapTo(0f) else away.animateTo(0f, keyframes {
                durationMillis = BRING_BACK_MS
                1f at 0 using BringBackEase
                AWAY_OVERSHOOT at (BRING_BACK_MS * 0.85f).toInt() using LinearOutSlowInEasing
            })
            lightHaptic()
            busy = false
        }
    }

    val rotation = remember(allPrograms) { allPrograms.sortedBy { it.position } }
    val index = rotation.indexOfFirst { it.slotId == program.slotId }
    val under1 = if (multi && index >= 0) splitAccentColor(rotation[(index + 1) % rotation.size].colorIndex) else null
    val under2 = if (rotation.size >= 3 && index >= 0) splitAccentColor(rotation[(index + 2) % rotation.size].colorIndex) else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (lifted || raised) 3f else 0f)
            .then(if (hangUnderClipboard) Modifier.offset(y = -SheetHang) else Modifier)
            // The hold's flat dim, drawn behind the lifted sheet over the rest of the list.
            .drawBehind {
                val a = scrim.value
                if (a > 0f) drawRect(SheetPaper.scrim.copy(alpha = 0.62f * a), topLeft = Offset(-4000f, -4000f), size = Size(size.width + 8000f, size.height + 8000f))
            },
    ) {
        if (putAway) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clipAboveTop()) {
                    PutAwayTab(
                        program = program,
                        onClick = ::bringBackNow,
                        modifier = Modifier.graphicsLayer { translationY = -size.height * (1f - tabIn.value) },
                    )
                }
                LogEmptyFirstLift()
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .onGloballyPositioned { coords ->
                        val root = coords.findRootCoordinates()
                        if (coords.isAttached && root.isAttached) {
                            val pos = coords.positionInRoot()
                            onSlotPositioned(Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height))
                        }
                    }
                    .clipAboveTop()
                    .drawWithContent {
                        drawContent()
                        placingColor?.let { drawLanding(it) }
                    },
            ) {
                Box(
                    Modifier
                        .graphicsLayer {
                            val a = away.value
                            translationY = if (a >= 0f) -(size.height + 40.dp.toPx()) * a else (a / AWAY_OVERSHOOT) * 6.dp.toPx()
                            alpha = if (pickerOpen) 0f else 1f
                        }
                        .drawBehind {
                            under2?.let { drawUnderSheet(SheetPaper.under2, it, -3.dp.toPx(), 9.dp.toPx(), -1.4f) }
                            under1?.let { drawUnderSheet(SheetPaper.under1, it, 3.dp.toPx(), 5.dp.toPx(), 1.7f) }
                        },
                ) {
                    Box(
                        Modifier.graphicsLayer {
                            val l = lift.value
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationY = 10.dp.toPx() * l
                            rotationZ = lerp(SHEET_REST_TILT, SHEET_LIFT_TILT, l)
                            val s = lerp(1f, SHEET_LIFT_SCALE, l)
                            scaleX = s
                            scaleY = s
                        },
                    ) {
                        SheetShadows(lifted = { lift.value })
                        CompositionLocalProvider(LocalViewConfiguration provides sheetViewConfiguration) {
                            SuggestedSheet(
                                program = program,
                                included = included,
                                interactive = true,
                                interactionSource = press,
                                onHold = hold,
                                onCommitName = { name -> onRenameProposal(program, name) },
                                onPutAway = ::putAwayNow,
                                onToggleRow = { onToggleRow(program, it) },
                                onSwapRow = { onSwapRow(program, it) },
                                onStart = { onStart(program) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Start counts the included rows' lifts; a group with no ledger rows counts its saved lifts. */
internal fun sheetStartCount(program: ClipboardProgramUi, included: Set<Long>): Int =
    if (program.rows.isEmpty()) program.lifts.size
    else program.rows.filter { it.usualExerciseId in included }.map { it.exerciseId }.distinct().size

/**
 * The sheet itself. [interactive] false is the picker's and the flight's display-only copy: no X,
 * no swap, no row input and no semantics of its own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedSheet(
    program: ClipboardProgramUi,
    included: Set<Long>,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    onHold: (() -> Unit)? = null,
    onCommitName: (String) -> Unit = {},
    onPutAway: () -> Unit = {},
    onToggleRow: (Long) -> Unit = {},
    onSwapRow: (Long) -> Unit = {},
    onStart: () -> Unit = {},
) {
    val accent = splitAccentColor(program.colorIndex)
    val namePhase = remember { mutableStateOf(SplitNamePhase.Rest) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .drawBehind { grainedFill(SheetPaper.paper) }
            .drawWithContent {
                drawContent()
                // The tuck: the clipboard's shade on the paper's top edge.
                drawRect(
                    Brush.verticalGradient(listOf(Color(0x5726180E), Color(0x0026180E)), startY = 0f, endY = 14.dp.toPx()),
                    size = Size(size.width, 14.dp.toPx()),
                )
            }
            .then(
                if (interactive) Modifier.combinedClickable(
                    interactionSource = interactionSource ?: remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onHold,
                ) else Modifier.clearAndSetSemantics { },
            ),
    ) {
        // The printed band: eyebrow, name (its own rename target), and the X in its own column.
        Box(Modifier.fillMaxWidth().drawBehind { grainedFill(accent) }) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 54.dp, top = 16.dp, bottom = 13.dp)) {
                Text(
                    text = when {
                        interactive && namePhase.value == SplitNamePhase.Editing -> "NAMING THIS SPLIT"
                        program.isExpected -> "SUGGESTED FOR TODAY"
                        else -> "SPLIT FOR TODAY"
                    },
                    fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium,
                    fontSize = 9.5.sp, lineHeight = 11.sp, letterSpacing = 2.sp,
                    color = SheetPaper.ink.copy(alpha = 0.74f), maxLines = 1,
                )
                Spacer(Modifier.height(7.dp))
                if (interactive) {
                    InlineSplitName(
                        name = program.name,
                        isUnnamed = program.isUnnamed,
                        phase = namePhase,
                        onCommitName = onCommitName,
                    )
                } else {
                    Text(
                        text = program.name,
                        fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 30.sp, lineHeight = 31.sp, letterSpacing = (-0.9).sp,
                        color = SheetPaper.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (interactive) {
                SheetXButton(
                    label = "Put away ${program.name} for today",
                    onClick = onPutAway,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
                )
            }
        }

        program.daysSinceLastSession?.let { days ->
            SplitReasonChips(days, program.isExpected, program.activeGroupCount, Modifier.padding(start = 18.dp, end = 18.dp, top = 11.dp))
        }

        // Column header: the last session's date, then today's target column.
        Row(
            Modifier
                .padding(start = 18.dp, end = 18.dp, top = 13.dp)
                .fillMaxWidth()
                .drawBehind {
                    val y = size.height - 0.75.dp.toPx()
                    drawLine(SheetPaper.ink, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                }
                .padding(end = 40.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Each column header is its own mirror node: the web mirror caps merged text at 80 chars.
            val lastLabel = program.ledgerLastDateLabel ?: "—"
            DataText(
                parts = listOf(data(lastLabel)), size = 12.5.sp, dataSize = 12.5.sp, data = SheetPaper.ink, maxLines = 1,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Last session column, $lastLabel" },
            )
            Text(
                "TODAY · TO BEAT", fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium,
                fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 1.8.sp, color = SheetPaper.sub,
                modifier = Modifier.semantics { contentDescription = "TODAY · TO BEAT" },
            )
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            var tailCaptionShown = false
            var optionCaptionShown = false
            if (program.rows.isEmpty()) {
                program.lifts.forEach { lift ->
                    SheetLiftLine(lift.name, lift.primaryGroup, lift.topSetLabel ?: lift.lastTopSet?.let(::formatTopSet))
                }
            }
            program.rows.forEach { row ->
                if (row.kind == TicketRowKind.OPTION && !optionCaptionShown) {
                    optionCaptionShown = true
                    SheetCaption("OTHER OPTIONS")
                }
                if (row.kind == TicketRowKind.TAIL && !tailCaptionShown) {
                    tailCaptionShown = true
                    SheetCaption("LAST TIME ALSO")
                }
                SheetRow(
                    row = row,
                    included = row.usualExerciseId in included,
                    interactive = interactive,
                    interactionSource = interactionSource,
                    onToggle = { onToggleRow(row.usualExerciseId) },
                    onSwap = { onSwapRow(row.usualExerciseId) },
                    onHold = onHold,
                )
            }
        }

        SheetPerforation(Modifier.padding(top = 6.dp))

        // The tear-off stub: Start seeds only the rows left in.
        val count = sheetStartCount(program, included)
        val canStart = count > 0 && program.startable
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(StubHeight)
                .drawBehind { grainedFill(accent) }
                .then(
                    if (interactive) Modifier
                        .clickable(enabled = canStart, role = Role.Button, onClick = onStart)
                        .clearAndSetSemantics { role = Role.Button; contentDescription = "Start ${countLabel(count, "lift")}, ${program.name}" }
                    else Modifier,
                ),
        ) {
            Row(Modifier.alpha(if (canStart) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
                DataText(
                    parts = listOf(filler("Start "), data("$count"), filler(if (count == 1) " lift" else " lifts")),
                    size = 16.sp, dataSize = 16.sp, filler = SheetPaper.ink, data = SheetPaper.ink, maxLines = 1,
                )
                Spacer(Modifier.width(10.dp))
                TicketArrow(tint = SheetPaper.ink)
            }
        }
    }
}

/** The band's X: a ruled square with a drawn cross. The sheet's puts it away; a picker card's retires the split. */
@Composable
private fun SheetXButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, SheetPaper.ink.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(13.dp)) {
            val stroke = 2.2.dp.toPx()
            val i = 2.dp.toPx() * size.width / 14.dp.toPx()
            drawLine(SheetPaper.ink, Offset(i, i), Offset(size.width - i, size.height - i), stroke, cap = StrokeCap.Round)
            drawLine(SheetPaper.ink, Offset(size.width - i, i), Offset(i, size.height - i), stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun SheetCaption(text: String) {
    Text(
        text, fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 11.sp,
        letterSpacing = 1.8.sp, color = SheetPaper.sub, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

/** A saved lift on a display-only sheet for a group with no ledger rows: name and last top set. */
@Composable
private fun SheetLiftLine(name: String, primaryGroup: String?, topSet: String?) {
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(SheetPaper.rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(rotationRegionColor(primaryGroup.orEmpty())))
        Spacer(Modifier.width(7.dp))
        Text(name, fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 17.sp,
            color = SheetPaper.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        topSet?.let { DataText(parts = listOf(data(it)), size = 13.sp, dataSize = 13.sp, data = SheetPaper.hand, maxLines = 1) }
    }
}

/** One lift: name, last session's sets in hand ink (top set underlined) → the dashed number to beat. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SheetRow(
    row: TicketRowUi,
    included: Boolean,
    interactive: Boolean,
    interactionSource: MutableInteractionSource?,
    onToggle: () -> Unit,
    onSwap: () -> Unit,
    onHold: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().drawBehind {
            drawLine(SheetPaper.rule, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
        }.padding(top = 8.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(
                    if (interactive) Modifier
                        // The row's press feeds the sheet's own cue, so a hold on a lift rises the sheet like a hold anywhere else.
                        .combinedClickable(
                            interactionSource = interactionSource ?: remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggle,
                            onLongClick = onHold,
                        )
                        .clearAndSetSemantics { role = Role.Checkbox; contentDescription = ticketRowDescription(row, included) }
                    else Modifier,
                )
                .alpha(if (included) 1f else 0.4f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 18.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(rotationRegionColor(row.primaryGroup.orEmpty())))
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.name, fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp, lineHeight = 17.sp, letterSpacing = (-0.15).sp, color = SheetPaper.ink,
                        textDecoration = if (included) null else TextDecoration.LineThrough,
                    )
                    row.usualName?.let { Text("usual · $it", fontFamily = LogUiFontFamily, fontSize = 11.sp, color = SheetPaper.sub) }
                }
                if (row.usualName != null) {
                    Text(
                        "TODAY ONLY", fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 8.sp,
                        letterSpacing = 1.6.sp, color = SheetPaper.ink,
                        modifier = Modifier.padding(start = 5.dp).sheetDashedOutline(1.dp).padding(horizontal = 5.dp, vertical = 3.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (row.lastSets.isEmpty()) {
                        DataText(parts = listOf(data("—")), size = 13.sp, data = SheetPaper.sub)
                        if (row.toBeatLabel == null) Text("first time", fontFamily = LogUiFontFamily, fontSize = 11.sp, lineHeight = 16.sp, color = SheetPaper.sub)
                    }
                    row.lastSets.forEach { set ->
                        DataText(
                            parts = listOf(data(set.label)), size = 13.sp, dataSize = 13.sp, lineHeight = 15.sp, data = SheetPaper.hand, maxLines = 1,
                            modifier = if (set.isTop) Modifier.drawBehind {
                                val y = size.height - 1.dp.toPx()
                                drawLine(SheetPaper.hand, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                            }.padding(bottom = 3.dp) else Modifier.padding(bottom = 3.dp),
                        )
                    }
                    row.lastSetsDateLabel?.let { DataText(parts = listOf(data(it)), size = 10.5.sp, dataSize = 11.sp, data = SheetPaper.sub) }
                }
                Spacer(Modifier.width(8.dp))
                TicketArrow(SheetPaper.arrow)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.widthIn(min = 74.dp).sheetDashedOutline(1.3.dp).padding(horizontal = 6.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        row.toBeatLabel ?: "—", fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, lineHeight = 17.sp, color = SheetPaper.ink, maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (interactive) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .border(1.dp, SheetPaper.ink.copy(alpha = 0.28f), CircleShape)
                    .clickable(role = Role.Button, onClick = onSwap)
                    .clearAndSetSemantics { role = Role.Button; contentDescription = if (row.alternativeCount > 1) "Swap ${row.name}, ${row.alternativeCount} lifts used" else "Swap ${row.name}" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = SheetPaper.ink, modifier = Modifier.size(16.dp)) }
        } else {
            Spacer(Modifier.width(30.dp))
        }
    }
}

private fun Modifier.sheetDashedOutline(width: Dp): Modifier = drawBehind {
    drawRoundRect(
        SheetPaper.ink, cornerRadius = CornerRadius(5.dp.toPx()),
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))),
    )
}

/** The perforation above the stub: a row of punched holes and a notch each side, in the page colour. */
@Composable
private fun SheetPerforation(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(24.dp)) {
        val hole = EspressoPalette.bg
        val r = 1.6.dp.toPx()
        val step = 9.dp.toPx()
        val y = size.height / 2f
        var x = step / 2f
        while (x < size.width) {
            drawCircle(hole, r, Offset(x, y))
            x += step
        }
        drawCircle(hole, 7.dp.toPx(), Offset(0f, y))
        drawCircle(hole, 7.dp.toPx(), Offset(size.width, y))
    }
}

/** What stays below the clipboard while the sheet is put away: kraft, the split's colour, its name. */
@Composable
private fun PutAwayTab(program: ClipboardProgramUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = splitAccentColor(program.colorIndex)
    Row(
        modifier
            .height(36.dp)
            .shadow(6.dp, TabShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(TabShape)
            .drawBehind { grainedFill(SheetPaper.kraft) }
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { role = Role.Button; contentDescription = "Bring back ${program.name}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(7.dp).fillMaxHeight().background(accent))
        Text(
            program.name.uppercase(), fontFamily = SheetSansFontFamily, fontWeight = FontWeight.SemiBold,
            fontSize = 10.5.sp, lineHeight = 12.sp, letterSpacing = 1.8.sp, color = SheetPaper.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 11.dp, end = 12.dp),
        )
        androidx.compose.foundation.Canvas(Modifier.width(1.dp).fillMaxHeight()) {
            drawLine(
                SheetPaper.ink.copy(alpha = 0.35f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bring back", fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 14.sp, color = SheetPaper.kraftInk, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
                val s = size.width / 12f
                val path = Path().apply {
                    moveTo(2.5f * s, 4.5f * s)
                    lineTo(6f * s, 8f * s)
                    lineTo(9.5f * s, 4.5f * s)
                }
                drawPath(path, SheetPaper.ink, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The picker: display-only sheets in rotation order
// ---------------------------------------------------------------------------------------------

/**
 * One display-only sheet per split in rotation order, opening on the one after the sheet on the
 * clipboard. Tapping the centred sheet places it (it flies to the clip and lands with a small
 * overshoot); tapping a side sheet centres it; tapping outside keeps the current one. The centred
 * sheet's X asks to retire its split ([onRetire]); a side sheet's X centres it first.
 */
@Composable
internal fun LogEmptyDayCardPickerOverlay(
    currentProgram: ClipboardProgramUi?,
    programs: List<ClipboardProgramUi>,
    sheetFor: (ClipboardProgramUi) -> ClipboardProgramUi,
    includedFor: (ClipboardProgramUi) -> Set<Long>,
    slotBounds: () -> Rect?,
    onClose: () -> Unit,
    onPlacingStarted: (ClipboardProgramUi) -> Unit,
    onPlace: (ClipboardProgramUi) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onRetire: ((ClipboardProgramUi) -> Unit)? = null,
) {
    if (programs.isEmpty()) return

    val haptics = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rotation = remember(programs) { programs.sortedBy { it.position } }
    val currentIndex = remember(currentProgram, rotation) {
        rotation.indexOfFirst { it.slotId == currentProgram?.slotId }.coerceAtLeast(0)
    }
    // Opens on the sheet after the one on the clipboard, so the first swipe isn't wasted.
    val pagerState = rememberPagerState(initialPage = (currentIndex + 1) % rotation.size) { rotation.size }

    var placing by remember { mutableStateOf<ClipboardProgramUi?>(null) }
    var flightFrom by remember { mutableStateOf<Rect?>(null) }
    var flightTo by remember { mutableStateOf<Rect?>(null) }
    val flight = remember { Animatable(0f) }
    val chrome = remember { Animatable(0f) }
    // Written by layout callbacks, read once at a tap: never state, so nothing recomposes from them.
    val pageRects = remember { HashMap<Int, Rect>() }
    val origin = remember { FloatArray(2) }

    LaunchedEffect(Unit) { chrome.animateTo(1f, tween(if (reduced) 0 else 180)) }

    fun place(page: Int, program: ClipboardProgramUi) {
        if (placing != null) return
        val to = slotBounds()
        val from = pageRects[page]
        placing = program
        onPlacingStarted(program)
        scope.launch {
            launch { chrome.animateTo(0f, tween(if (reduced) 0 else 180)) }
            if (to != null && from != null && !reduced) {
                flightFrom = from
                flightTo = to
                flight.animateTo(1f, tween(SHEET_FLIGHT_MS, easing = SheetEase))
            }
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            onPlace(program)
            // Two frames for the clip slot to compose the placed sheet before the flyer leaves.
            withFrameNanos { }
            withFrameNanos { }
            onFinished()
        }
    }

    fun tapPage(page: Int, program: ClipboardProgramUi) {
        if (placing != null) return
        if (page == pagerState.currentPage && pagerState.currentPageOffsetFraction.absoluteValue < 0.1f) place(page, program)
        else scope.launch { pagerState.animateScrollToPage(page) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { val p = it.positionInRoot(); origin[0] = p.x; origin[1] = p.y }
            // Taps between the sheets close it; a sheet's own tap is consumed first.
            .pointerInput(Unit) { detectTapGestures(onTap = { if (placing == null) onClose() }) },
    ) {
        val screenWidth = maxWidth
        val sheetWidth = slotBounds()?.let { with(density) { it.width.toDp() } } ?: (screenWidth - 52.dp)
        val cardWidth = sheetWidth * PICKER_SHEET_SCALE

        // A flat dim, no blur.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = chrome.value }
                .background(SheetPaper.scrim.copy(alpha = 0.84f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = placing == null,
                    onClick = onClose,
                )
                .clearAndSetSemantics { role = Role.Button; contentDescription = "Close" },
        )

        Column(Modifier.fillMaxSize().padding(top = 96.dp).graphicsLayer { alpha = chrome.value }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "CHOOSE A SHEET", fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium,
                    fontSize = 10.5.sp, letterSpacing = 2.2.sp, color = EspressoPalette.dim,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "CHOOSE A SHEET" },
                )
                DataText(
                    // A retired sheet can leave the pager past its new end for a frame.
                    parts = listOf(data("${minOf(pagerState.currentPage + 1, rotation.size)}"), filler(" / "), data("${rotation.size}")),
                    size = 11.sp, dataSize = 13.sp, filler = EspressoPalette.faint, data = EspressoPalette.ink, maxLines = 1,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = ((screenWidth - cardWidth) / 2).coerceAtLeast(0.dp)),
                pageSpacing = 14.dp,
                beyondViewportPageCount = 1,
                userScrollEnabled = placing == null,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                // Bounds-checked: retiring a sheet shrinks the list under a composed page.
                val program = rotation.getOrNull(page) ?: return@HorizontalPager
                val sheet = sheetFor(program)
                val flying = placing?.slotId == program.slotId && flightFrom != null
                Column(
                    Modifier.width(cardWidth).graphicsLayer {
                        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        val s = lerp(1f, 0.94f, offset)
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        alpha = if (flying) 0f else lerp(1f, 0.45f, offset)
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            (page + 1).toString().padStart(2, '0'), fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, lineHeight = 18.sp, letterSpacing = (-0.4).sp, color = EspressoPalette.faint,
                        )
                        if (program.slotId == currentProgram?.slotId) {
                            Text("ON THE CLIPBOARD", fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium,
                                fontSize = 9.5.sp, letterSpacing = 2.sp, color = EspressoPalette.dim, maxLines = 1)
                        }
                    }
                    Box {
                        Box(
                            Modifier
                                .onGloballyPositioned { pageRects[page] = it.boundsInRoot() }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    enabled = placing == null,
                                ) { tapPage(page, program) }
                                .clearAndSetSemantics { role = Role.Button; contentDescription = "Place ${program.name}" }
                                .fitSheet(sheetWidth, PICKER_SHEET_SCALE),
                        ) {
                            SuggestedSheet(program = sheet, included = includedFor(sheet), interactive = false)
                        }
                        // The sheet's X at the sheet's scale, over the band where the clip sheet has its own.
                        if (onRetire != null) {
                            SheetXButton(
                                label = "Retire ${program.name}",
                                onClick = {
                                    if (placing == null) {
                                        if (page == pagerState.currentPage) onRetire(program)
                                        else scope.launch { pagerState.animateScrollToPage(page) }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp * PICKER_SHEET_SCALE, end = 10.dp * PICKER_SHEET_SCALE)
                                    .graphicsLayer {
                                        scaleX = PICKER_SHEET_SCALE
                                        scaleY = PICKER_SHEET_SCALE
                                        transformOrigin = TransformOrigin(1f, 0f)
                                    },
                            )
                        }
                    }
                }
            }
        }

        // The picked sheet's flight to the clip: one layer, translated, scaled and tilted in draw.
        val from = flightFrom
        val to = flightTo
        val flyer = placing
        if (from != null && to != null && flyer != null) {
            val sheet = sheetFor(flyer)
            Box(
                Modifier
                    .offset { IntOffset((to.left - origin[0]).roundToInt(), (to.top - origin[1]).roundToInt()) }
                    .width(with(density) { to.width.toDp() })
                    .graphicsLayer {
                        val w = size.width
                        val s0 = from.width / w
                        val tx0 = from.left - to.left - w / 2f * (1f - s0)
                        val ty0 = from.top - to.top
                        val land = 14.dp.toPx()
                        val p = flight.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        if (p <= FLIGHT_LAND_AT) {
                            val k = p / FLIGHT_LAND_AT
                            translationX = lerp(tx0, 0f, k)
                            translationY = lerp(ty0, land, k)
                            rotationZ = lerp(0f, -0.9f, k)
                            val s = lerp(s0, 1f, k)
                            scaleX = s
                            scaleY = s
                        } else {
                            val k = (p - FLIGHT_LAND_AT) / (1f - FLIGHT_LAND_AT)
                            translationX = 0f
                            translationY = lerp(land, 0f, k)
                            rotationZ = lerp(-0.9f, SHEET_REST_TILT, k)
                            scaleX = 1f
                            scaleY = 1f
                        }
                    },
            ) {
                SheetShadows(lifted = { 1f })
                SuggestedSheet(program = sheet, included = includedFor(sheet), interactive = false)
            }
        }
    }
}

/**
 * The split's reason as pills on the paper, not a sentence: `30 days since last`, plus
 * `longest of 4` when it is the suggested split. Narrow sheets fold both into one
 * `30 days · longest of 4` chip. The mirror keeps one node reading the whole reason.
 */
@Composable
private fun SplitReasonChips(days: Int, isExpected: Boolean, groups: Int, modifier: Modifier = Modifier) {
    val sentence = "$days days since last time" + if (isExpected) ", the longest of your $groups groups" else ""
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = sentence }) {
        val single = isExpected && maxWidth < 280.dp
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (single) {
                SplitReasonChip(listOf(data(countLabel(days, "day")), filler(" · longest of "), data("$groups")))
            } else {
                SplitReasonChip(listOf(data(countLabel(days, "day")), filler(" since last")))
                if (isExpected) SplitReasonChip(listOf(filler("longest of "), data("$groups")))
            }
        }
    }
}

@Composable
private fun SplitReasonChip(parts: List<DataPart>) {
    Box(
        Modifier.height(24.dp).background(SheetPaper.chip, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        DataText(parts = parts, size = 11.sp, dataSize = 11.5.sp, lineHeight = 14.sp, filler = SheetPaper.sub, data = SheetPaper.ink, maxLines = 1)
    }
}

internal fun Modifier.dashedLedgerOutline(): Modifier = drawBehind {
    drawRoundRect(EspressoPalette.dim, cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
}

/** A suggestion, not a win: ink on a dashed outline, never the win green. Shared with Progress. */
@Composable
internal fun StakeChip(stake: StakeUi) {
    DataText(
        parts = stakeParts(stake),
        size = 10.5.sp,
        dataSize = 11.5.sp,
        lineHeight = 13.sp,
        filler = EspressoPalette.dim,
        data = EspressoPalette.ink,
        maxLines = 1,
        modifier = Modifier
            .drawBehind {
                val corner = 7.dp.toPx()
                drawRoundRect(
                    color = EspressoPalette.hair2,
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// No proposal yet: the first-lift line and any hand-made splits
// ---------------------------------------------------------------------------------------------

/** No group is due (a new account, one training day): add a lift, or start a hand-made split. */
@Composable
internal fun LogEmptyFallback(
    programs: List<ClipboardProgramUi>,
    onStart: (ClipboardProgramUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LogEmptyFirstLift()
        val withLifts = programs.filter { it.lifts.isNotEmpty() }
        if (withLifts.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                withLifts.forEach { program ->
                    val accent = splitAccentColor(program.colorIndex)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EspressoPalette.panel)
                            .alpha(if (program.startable) 1f else 0.45f)
                            .clickable(enabled = program.startable, role = Role.Button) { onStart(program) }
                            .clearAndSetSemantics {
                                role = Role.Button
                                contentDescription = "Start ${program.name}"
                                if (!program.startable) disabled()
                            }
                            .padding(horizontal = 14.dp),
                    ) {
                        Text(
                            text = program.name,
                            fontFamily = LogDisplayFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = accent,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        DataText(
                            parts = listOf(data("${program.lifts.size}"), filler(if (program.lifts.size == 1) " lift" else " lifts")),
                            size = 11.5.sp,
                            filler = EspressoPalette.dim,
                            data = EspressoPalette.ink,
                        )
                        Spacer(Modifier.width(10.dp))
                        TicketArrow(tint = accent)
                    }
                }
            }
        }
    }
}
