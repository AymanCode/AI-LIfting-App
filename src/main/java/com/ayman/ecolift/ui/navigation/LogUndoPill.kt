package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.ShaderWarmup
import com.ayman.ecolift.ui.theme.rememberLightHaptic
import com.ayman.ecolift.ui.theme.rememberReducedMotion
import com.ayman.ecolift.ui.theme.splitAccentColor
import com.ayman.ecolift.ui.viewmodel.DayUndoCopy
import com.ayman.ecolift.ui.viewmodel.DayUndoTint
import com.ayman.ecolift.ui.viewmodel.startedCopy
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** How long the ring takes to drain from full to empty. */
internal const val LOG_UNDO_COUNTDOWN_MS = 6_000L

/** The pill's height, and the resting circle's size. */
internal val LogUndoPillHeight = 58.dp

/** Keyed by screen because the log tab and the report-workout route overlap during a transition. */
internal class NoticeClearance {
    private val owners = mutableStateMapOf<Any, Dp>()
    val value: Dp get() = owners.values.maxOrNull() ?: 0.dp
    fun set(owner: Any, clearance: Dp) {
        if (clearance > 0.dp) owners[owner] = clearance else owners.remove(owner)
    }
}

internal val LocalNoticeClearance = compositionLocalOf<NoticeClearance?> { null }

/** How long the "Undone" beat shows before the caller clears the offer. */
internal const val LOG_UNDO_UNDONE_MS = 700L

private const val LOG_UNDO_WARMUP_KEY = "log-undo"
private const val LOG_UNDO_WARMUP_DELAY_MS = 3_000L

internal enum class LogUndoPhase { Full, Resting, Undone }

internal fun logUndoPhase(elapsedMs: Long, undone: Boolean): LogUndoPhase = when {
    undone -> LogUndoPhase.Undone
    elapsedMs >= LOG_UNDO_COUNTDOWN_MS -> LogUndoPhase.Resting
    else -> LogUndoPhase.Full
}

/** Fraction of the ring still drawn: drains 1 → 0 over the countdown, full again at rest. */
internal fun logUndoRingFraction(elapsedMs: Long): Float =
    if (elapsedMs >= LOG_UNDO_COUNTDOWN_MS) 1f else 1f - elapsedMs.toFloat() / LOG_UNDO_COUNTDOWN_MS

internal fun logUndoLabel(copy: DayUndoCopy): String =
    "Undo: " + copy.headline.joinToString("") { it.text } + " " + copy.verb + " " + copy.detail.joinToString("") { it.text }

/** What the pill shows; [key] changes for every new offer so the countdown restarts. */
@Immutable
internal data class LogUndoPillModel(val key: String, val copy: DayUndoCopy, val ringColor: Color)

@Composable
internal fun LogUndoPill(
    model: LogUndoPillModel?,
    undone: Boolean,
    visible: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hold the last non-null model so the exit animation still has something to draw while it
    // fades and sinks — by the time it plays, the caller has usually already nulled the model.
    var heldModel by remember { mutableStateOf(model) }
    if (model != null) heldModel = model
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = model != null && visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { with(density) { 24.dp.roundToPx() } },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 2 },
        modifier = modifier,
    ) {
        heldModel?.let { shown ->
            LogUndoPillLive(model = shown, undone = undone, onUndo = onUndo)
        }
    }
}

/** The real, ticking pill: owns the countdown and the tap handler. */
@Composable
private fun LogUndoPillLive(model: LogUndoPillModel, undone: Boolean, onUndo: () -> Unit) {
    val lightHaptic = rememberLightHaptic()
    // Two clocks read from the same mark: [elapsed] ticks every frame but is only ever read from
    // draw scopes (the ring), so draining never recomposes this composable; [resting] flips once,
    // at the 6 s mark, and is the only thing that changes the pill's layout (Full -> Resting).
    val elapsed = remember(model.key) { mutableLongStateOf(0L) }
    var resting by remember(model.key) { mutableStateOf(false) }
    LaunchedEffect(model.key) {
        val start = TimeSource.Monotonic.markNow()
        resting = false
        while (elapsed.longValue < LOG_UNDO_COUNTDOWN_MS) {
            withFrameMillis { }
            elapsed.longValue = start.elapsedNow().inWholeMilliseconds
        }
        resting = true
    }
    // Latched rather than read straight from [undone]: the caller clears its own "undone" flag
    // right when it nulls the model, at the same moment this pill starts its 160 ms exit fade. If
    // Undone were read live, that fade would revert to Full/Resting — and the ring would visibly
    // spin an extra turn back to 0° — for the one frame before the model change unmounts it.
    var everUndone by remember(model.key) { mutableStateOf(false) }
    if (undone) everUndone = true
    val phase = logUndoPhase(if (resting) LOG_UNDO_COUNTDOWN_MS else 0L, everUndone)
    // Read inside graphicsLayer (a draw/layout-phase scope) rather than hoisted with `by`, so the
    // 450 ms spin animates without recomposing the row every frame — same trick as chromeReveal.
    val ringRotation = animateFloatAsState(
        targetValue = if (everUndone) -360f else 0f,
        animationSpec = tween(450),
        label = "log_undo_ring_rotation",
    )
    LogUndoPillChrome(
        phase = phase,
        copy = model.copy,
        ringColor = model.ringColor,
        elapsed = elapsed,
        draining = phase == LogUndoPhase.Full,
        rotation = ringRotation,
        interactive = true,
        contentDescriptionText = if (everUndone) "Undone" else logUndoLabel(model.copy),
        onTap = { if (!everUndone) { lightHaptic(); onUndo() } },
    )
}

/**
 * The pill's surface, layout and ring, with no opinion on where its numbers come from. Shared by
 * the live pill and [LogUndoPillWarmup] so the warm-up compiles the exact shaders the real pill
 * will need, drawing a frozen [elapsed] instead of a ticking one.
 */
@Composable
private fun LogUndoPillChrome(
    phase: LogUndoPhase,
    copy: DayUndoCopy,
    ringColor: Color,
    elapsed: MutableLongState,
    draining: Boolean,
    rotation: State<Float>,
    interactive: Boolean,
    contentDescriptionText: String,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val resting = phase == LogUndoPhase.Resting
    // Padding is the LAST (innermost) modifier here, after clip/background/border/clickable, so
    // the rounded surface is sized to include it (a proper inset, per the board) instead of the
    // surface hugging the Row's content tightly and the padding sitting outside it as blank,
    // uncoloured margin — which is what put the 20/9 dp on the wrong side of the clip and let the
    // name line spill past the surface's own left edge.
    val surfaceModifier = Modifier
        .animateContentSize(tween(260, easing = FastOutSlowInEasing))
        .then(if (resting) Modifier.size(LogUndoPillHeight) else Modifier.height(LogUndoPillHeight))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xF73E3732), Color(0xF7282320))))
        .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
        .drawBehind {
            // Top highlight, inset by the pill's own corner radius so it stops before the rounded
            // ends (on the resting circle this collapses to nothing, which is correct there).
            val corner = size.height / 2f
            val y = 0.5.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(corner, y),
                end = Offset(size.width - corner, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        .let { base ->
            if (!interactive) {
                base.clearAndSetSemantics { }
            } else {
                base.clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = contentDescriptionText
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                )
            }
        }
        .then(if (resting) Modifier else Modifier.padding(start = 20.dp, end = 9.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (resting) Arrangement.Center else Arrangement.spacedBy(14.dp),
        modifier = surfaceModifier,
    ) {
        if (!resting) {
            val undone = phase == LogUndoPhase.Undone
            // Always the same three children (Text, Spacer, DataText) regardless of phase — only
            // their content/size differs, collapsing the detail line to zero height for the
            // "Undone" single-line look. Bisecting an unrelated crash (an uncaught
            // IndexOutOfBoundsException that turned out to come from the exercise pager, not from
            // here) briefly suspected this Column swapping in a single lone Text under the same
            // animateContentSize-wrapped Row; keeping the child count constant regardless of phase
            // ruled that out and is kept as the simpler, defensively safer shape either way.
            Column(modifier = Modifier.widthIn(max = 200.dp)) {
                Text(
                    text = if (undone) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, color = EspressoPalette.ink, fontSize = 15.sp)) {
                                append("Undone")
                            }
                        }
                    } else {
                        logUndoNameLine(copy, ringColor)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(if (undone) 0.dp else 5.dp))
                DataText(
                    parts = if (undone) emptyList() else copy.detail,
                    modifier = if (undone) Modifier.height(0.dp) else Modifier,
                    size = 11.5.sp,
                    dataSize = 12.5.sp,
                    filler = EspressoPalette.dim,
                    data = EspressoPalette.ink,
                    maxLines = 1,
                )
            }
        }
        LogUndoRing(
            elapsed = elapsed,
            ringColor = ringColor,
            diameter = if (resting) 46.dp else 42.dp,
            draining = draining,
            rotation = rotation,
        )
    }
}

private fun logUndoNameLine(copy: DayUndoCopy, ringColor: Color): AnnotatedString = buildAnnotatedString {
    val nameColor = if (copy.tint is DayUndoTint.Split) ringColor else EspressoPalette.ink
    withStyle(SpanStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, color = nameColor, fontSize = 15.sp)) {
        append(copy.headline.joinToString("") { it.text })
    }
    withStyle(SpanStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Normal, color = EspressoPalette.ink, fontSize = 12.5.sp)) {
        append(" " + copy.verb)
    }
}

/**
 * The countdown ring. [elapsed] is read only inside the [Canvas] draw phase (never hoisted into
 * composition), so a draining countdown redraws this one node 60 times a second instead of
 * recomposing the pill.
 */
@Composable
private fun LogUndoRing(
    elapsed: MutableLongState,
    ringColor: Color,
    diameter: Dp,
    draining: Boolean,
    rotation: State<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(diameter)
            .graphicsLayer { rotationZ = rotation.value }
    ) {
        val fraction = if (draining) logUndoRingFraction(elapsed.longValue) else 1f
        val strokeWidthPx = 3.5.dp.toPx()
        val insetPx = 5.dp.toPx()
        val radius = size.minDimension / 2f - strokeWidthPx / 2f

        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = radius,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = size.minDimension / 2f - insetPx,
        )
        if (fraction > 0f) {
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
        }
        drawRewindGlyph(color = ringColor, boxSizePx = 17.dp.toPx(), strokeWidthPx = 2.2.dp.toPx())
    }
}

/**
 * A hooked rewind arrow on a 14-unit box, centred in whatever this [DrawScope] currently is.
 * Coordinates mirror the board's SVG paths verbatim: `M4.5 3 L1.5 6 L4.5 9` for the chevron and
 * `M1.8 6 H9 A3.5 3.5 0 0 1 9 13 H6` for the hook.
 */
private fun DrawScope.drawRewindGlyph(color: Color, boxSizePx: Float, strokeWidthPx: Float) {
    val scale = boxSizePx / 14f
    fun point(x: Float, y: Float) = Offset(center.x + (x - 7f) * scale, center.y + (y - 7f) * scale)

    val chevron = Path().apply {
        val tip = point(4.5f, 3f)
        moveTo(tip.x, tip.y)
        val back = point(1.5f, 6f)
        lineTo(back.x, back.y)
        val tail = point(4.5f, 9f)
        lineTo(tail.x, tail.y)
    }
    val hook = Path().apply {
        val start = point(1.8f, 6f)
        moveTo(start.x, start.y)
        val corner = point(9f, 6f)
        lineTo(corner.x, corner.y)
        val arcCenter = point(9f, 9.5f)
        val r = 3.5f * scale
        arcTo(
            rect = Rect(arcCenter.x - r, arcCenter.y - r, arcCenter.x + r, arcCenter.y + r),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        val end = point(6f, 13f)
        lineTo(end.x, end.y)
    }
    val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(chevron, color = color, style = stroke)
    drawPath(hook, color = color, style = stroke)
}

/**
 * Draws the pill off-screen, once, while the app is idle — the same fix as the calendar's tilt
 * warm-up (`CalendarShaderWarmup`), for the pill's own draw calls (arc sweep, rounded gradient
 * clip). Placed under the opaque background in `AppNavigation.kt` right after
 * `CalendarShaderWarmup()`.
 */
@Composable
internal fun LogUndoPillWarmup() {
    val reducedMotion = rememberReducedMotion()
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion || ShaderWarmup.isDone(LOG_UNDO_WARMUP_KEY)) return@LaunchedEffect
        delay(LOG_UNDO_WARMUP_DELAY_MS)
        ShaderWarmup.run(LOG_UNDO_WARMUP_KEY) { showing = it }
    }
    if (!showing) return
    val accent = splitAccentColor(0)
    val sampleCopy = remember { startedCopy("Upper Strength", 1, 6, 18) }
    val frozenElapsed = remember { mutableLongStateOf(2_000L) }
    val noRotation = remember { mutableFloatStateOf(0f) }
    val halfRotation = remember { mutableFloatStateOf(-180f) }
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogUndoPillChrome(
                phase = LogUndoPhase.Full,
                copy = sampleCopy,
                ringColor = accent,
                elapsed = frozenElapsed,
                draining = true,
                rotation = noRotation,
                interactive = false,
                contentDescriptionText = "",
                onTap = {},
            )
            LogUndoPillChrome(
                phase = LogUndoPhase.Resting,
                copy = sampleCopy,
                ringColor = accent,
                elapsed = frozenElapsed,
                draining = false,
                rotation = noRotation,
                interactive = false,
                contentDescriptionText = "",
                onTap = {},
            )
            LogUndoPillChrome(
                phase = LogUndoPhase.Undone,
                copy = sampleCopy,
                ringColor = accent,
                elapsed = frozenElapsed,
                draining = false,
                rotation = halfRotation,
                interactive = false,
                contentDescriptionText = "",
                onTap = {},
            )
            Box(Modifier.graphicsLayer { alpha = 0.5f; scaleX = 0.9f; scaleY = 0.9f }) {
                LogUndoPillChrome(
                    phase = LogUndoPhase.Full,
                    copy = sampleCopy,
                    ringColor = accent,
                    elapsed = frozenElapsed,
                    draining = true,
                    rotation = noRotation,
                    interactive = false,
                    contentDescriptionText = "",
                    onTap = {},
                )
            }
        }
    }
}
