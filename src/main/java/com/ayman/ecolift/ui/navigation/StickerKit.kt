package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.progress.MuscleSlice
import com.ayman.ecolift.data.splits.DayTier
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.MarkerFontFamily
import com.ayman.ecolift.ui.theme.SheetSansFontFamily

/**
 * The Splits tab's sticker-calendar materials (spec 2026-09-22-splits-sticker-calendar-design.md):
 * kraft paper, dot stickers, pencil and marker loops, the gold PR star, sticky notes, washi tape
 * and a rubber stamp. The four Splits sections draw from these so they read as one desk.
 */
internal object StickerInk {
    val kraft = Color(0xFF2D2621)
    val kraftCard = Color(0xFF3A312B)
    val sheet = Color(0xFF3B332D)
    val print = Color(0xFFB6AA9B)
    val printDim = Color(0xFF8B7F72)
    val rule = Color(0xFFF3EDE4).copy(alpha = 0.055f)
    val cream = Color(0xFFEAE3D7)
    val noteCream = Color(0xFFEFE6D8)
    val amber = EspressoPalette.down
    val moss = EspressoPalette.legs
    val ink = EspressoPalette.onColor
    val preCycle = Color(0xFF8A7F74)
    val archivedCycles = listOf(Color(0xFF7D8CA3), Color(0xFFA8977E))
    val wireLight = Color(0xFFE4DCD0)
    val wireDark = Color(0xFF6F665D)
    val hole = Color(0xFF141110)
    val goldFoil = listOf(Color(0xFFFFF4C2), Color(0xFFEDBE4E), Color(0xFFF8DC85), Color(0xFFB98A2E))
    val goldSolid = Color(0xFFD99C2B)
    val goldEdge = Color(0xFF6B4A12)
}

internal object StickerType {
    val hand by lazy { TextStyle(fontFamily = MarkerFontFamily, fontWeight = FontWeight.Bold) }
    val print by lazy { TextStyle(fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium) }
    val display by lazy { TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold) }
    val body by lazy { TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium) }

    fun hand(size: TextUnit, color: Color) = hand.copy(fontSize = size, lineHeight = size, color = color)
    fun print(size: TextUnit, color: Color, tracking: TextUnit = 1.4.sp) =
        print.copy(fontSize = size, lineHeight = size, letterSpacing = tracking, color = color)
    fun display(size: TextUnit, color: Color, tracking: TextUnit = (-0.3).sp) =
        display.copy(fontSize = size, lineHeight = size, letterSpacing = tracking, color = color)
    fun body(size: TextUnit, color: Color, lineHeight: TextUnit = size * 1.3f) =
        body.copy(fontSize = size, lineHeight = lineHeight, color = color)
}

/** Sticker diameter by day tier (light 23, regular 29, heavy 35). */
internal fun stickerSize(tier: DayTier): Dp = when (tier) {
    DayTier.LIGHT -> 23.dp
    DayTier.REGULAR -> 29.dp
    DayTier.HEAVY -> 35.dp
}

internal fun stickerNumberSize(tier: DayTier): TextUnit = when (tier) {
    DayTier.LIGHT -> 10.sp
    DayTier.REGULAR -> 11.5.sp
    DayTier.HEAVY -> 12.5.sp
}

/** Hand-placed look, stable per day: a tilt in degrees and a nudge in dp. */
internal data class StickerJitter(val tiltDeg: Float, val dx: Float, val dy: Float)

internal fun stickerJitter(dayOfMonth: Int): StickerJitter = StickerJitter(
    tiltDeg = ((dayOfMonth * 37) % 13 - 6).toFloat(),
    dx = ((dayOfMonth * 17) % 5 - 2) * 0.7f,
    dy = ((dayOfMonth * 29) % 5 - 2) * 0.6f,
)

/** The muscle's Espresso colour (legs for the lower body, arms for biceps/triceps/forearms). */
internal fun muscleSliceColor(slice: MuscleSlice): Color = when (slice) {
    MuscleSlice.QUADS, MuscleSlice.HAMSTRINGS, MuscleSlice.GLUTES, MuscleSlice.CALVES -> EspressoPalette.legs
    MuscleSlice.CHEST -> EspressoPalette.chest
    MuscleSlice.BACK -> EspressoPalette.back
    MuscleSlice.SHOULDERS -> EspressoPalette.shoulders
    MuscleSlice.BICEPS, MuscleSlice.TRICEPS, MuscleSlice.FOREARMS -> EspressoPalette.arms
    MuscleSlice.CORE -> EspressoPalette.core
    MuscleSlice.UNKNOWN -> EspressoPalette.dim
}

// ---------------------------------------------------------------- paper

private val grainBrush = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.18f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.14f),
        Color.Transparent,
    ),
)

/** Fine paper grain over whatever was drawn before it. */
internal fun DrawScope.drawGrain(alpha: Float = 0.07f) {
    drawRect(brush = grainBrush, alpha = alpha, blendMode = BlendMode.Overlay)
}

/** True for content drawn under a 3D tilt: a month page mid-turn. */
internal val LocalSheetTurning = compositionLocalOf { false }

/**
 * A drop shadow that steps aside while its sheet is turning. Under a perspective matrix Skia
 * cannot use its analytic shadows and blurs a mask instead, compiling a new shader program for
 * every blur radius the tilt produces; those compiles were the stall before the first page flip
 * and kept coming mid-turn on later flips. A turning page already casts its shade on the page
 * beneath it (the flip's `shade`), so it draws no shadow of its own.
 */
@Composable
internal fun Modifier.sheetShadow(elevation: Dp, shape: Shape, alpha: Float): Modifier =
    if (LocalSheetTurning.current) this
    else shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = alpha), spotColor = Color.Black.copy(alpha = alpha))

/** A kraft sheet: soft shadow, rounded corners, grain. */
@Composable
internal fun Modifier.kraftPaper(
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = StickerInk.kraft,
    elevation: Dp = 14.dp,
): Modifier = this
    .sheetShadow(elevation, shape, 0.55f)
    .clip(shape)
    .background(color)
    .drawBehind { drawGrain() }

/** The lifted bottom-right corner of a sheet: the desk shows through, the flap folds back. */
internal fun DrawScope.drawPageCurl(desk: Color, size: Float) {
    val w = this.size.width
    val h = this.size.height
    val cut = Path().apply {
        moveTo(w, h - size); lineTo(w, h); lineTo(w - size, h); close()
    }
    drawPath(cut, desk)
    val flap = Path().apply {
        moveTo(w - size, h); lineTo(w, h - size); lineTo(w - size, h - size); close()
    }
    drawPath(flap, Color.Black.copy(alpha = 0.28f))
    withTransform({ translate(-1.5f, -1.5f) }) {
        drawPath(
            flap,
            Brush.linearGradient(
                listOf(Color(0xFF4A4038), Color(0xFF3A312B), Color(0xFF2A231F)),
                start = Offset(w - size, h - size),
                end = Offset(w, h),
            ),
        )
    }
}

// ---------------------------------------------------------------- stickers

/** A glossy dot sticker with a printed number. */
@Composable
internal fun DotSticker(
    color: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
    number: String? = null,
    numberSize: TextUnit = 11.5.sp,
    lifted: Boolean = false,
) {
    val shadow = if (lifted) 9.dp else 2.5.dp
    Box(
        modifier = modifier
            .size(diameter)
            .sheetShadow(shadow, CircleShape, 0.6f)
            .clip(CircleShape)
            .background(color)
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.55f),
                        0.26f to Color.White.copy(alpha = 0.10f),
                        0.5f to Color.Transparent,
                        center = Offset(this.size.width * 0.30f, this.size.height * 0.26f),
                        radius = r * 1.1f,
                    ),
                )
                drawCircle(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.14f)),
                )
                drawCircle(Color.Black.copy(alpha = 0.08f), style = Stroke(1f))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (number != null) {
            Text(number, style = StickerType.display(numberSize, StickerInk.ink, 0.sp).copy(fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false)
        }
    }
}

// ---------------------------------------------------------------- hand-drawn loops

internal enum class LoopStyle { PENCIL, MARKER }

private fun loopPath(style: LoopStyle, scale: Float): Path = Path().apply {
    // The prototype's paths, drawn in a 40×40 box.
    fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
    when (style) {
        LoopStyle.PENCIL -> {
            moveTo(p(9f, 25f).x, p(9f, 25f).y)
            cubicTo(p(6f, 15f).x, p(6f, 15f).y, p(13f, 7f).x, p(13f, 7f).y, p(22f, 7f).x, p(22f, 7f).y)
            cubicTo(p(31f, 8f).x, p(31f, 8f).y, p(35f, 15f).x, p(35f, 15f).y, p(33f, 23f).x, p(33f, 23f).y)
            cubicTo(p(31f, 31f).x, p(31f, 31f).y, p(22f, 35f).x, p(22f, 35f).y, p(15f, 33f).x, p(15f, 33f).y)
            cubicTo(p(9f, 31f).x, p(9f, 31f).y, p(6f, 25f).x, p(6f, 25f).y, p(8f, 18f).x, p(8f, 18f).y)
            cubicTo(p(10f, 13f).x, p(10f, 13f).y, p(15f, 10.5f).x, p(15f, 10.5f).y, p(20f, 10f).x, p(20f, 10f).y)
        }
        LoopStyle.MARKER -> {
            moveTo(p(20f, 6f).x, p(20f, 6f).y)
            cubicTo(p(29f, 6f).x, p(29f, 6f).y, p(34f, 12f).x, p(34f, 12f).y, p(34f, 20f).x, p(34f, 20f).y)
            cubicTo(p(34f, 29f).x, p(34f, 29f).y, p(27f, 34f).x, p(27f, 34f).y, p(19f, 34f).x, p(19f, 34f).y)
            cubicTo(p(11f, 34f).x, p(11f, 34f).y, p(6f, 28f).x, p(6f, 28f).y, p(6f, 20f).x, p(6f, 20f).y)
            cubicTo(p(6f, 12f).x, p(6f, 12f).y, p(12f, 7f).x, p(12f, 7f).y, p(21f, 8f).x, p(21f, 8f).y)
            cubicTo(p(24f, 8.5f).x, p(24f, 8.5f).y, p(26f, 9.5f).x, p(26f, 9.5f).y, p(27f, 11f).x, p(27f, 11f).y)
        }
    }
}

/** A pencil (temp split) or marker (today) loop drawn around a date; [progress] draws it on. */
@Composable
internal fun HandLoop(
    style: LoopStyle,
    color: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    progress: Float = 1f,
) {
    Canvas(modifier.size(38.dp)) {
        val path = loopPath(style, this.size.minDimension / 40f)
        val drawn = if (progress >= 1f) {
            path
        } else {
            val measure = PathMeasure().apply { setPath(path, false) }
            Path().also { measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), it, true) }
        }
        drawPath(drawn, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------- star

private fun starPath(size: Float): Path = Path().apply {
    // 5-point star in a 24-unit box (the prototype's path).
    val s = size / 24f
    val pts = listOf(
        12f to 1.6f, 14.95f to 8f, 21.95f to 8.72f, 16.7f to 13.42f, 18.22f to 20.32f,
        12f to 16.8f, 5.78f to 20.32f, 7.3f to 13.42f, 2.05f to 8.72f, 9.05f to 8f,
    )
    pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * s, y * s) else lineTo(x * s, y * s) }
    close()
}

/** Gold foil star for a PR on one of the heaviest lifts; [filled] = false draws an empty star. */
@Composable
internal fun PrStar(size: Dp, modifier: Modifier = Modifier, filled: Boolean = true, solid: Boolean = false) {
    Canvas(modifier.size(size)) {
        val path = starPath(this.size.minDimension)
        if (filled) {
            drawPath(path, Color.Black.copy(alpha = 0.45f), style = Stroke(2.2f))
            if (solid) {
                drawPath(path, StickerInk.goldSolid)
            } else {
                drawPath(path, Brush.linearGradient(StickerInk.goldFoil, start = Offset.Zero, end = Offset(this.size.width, this.size.height)))
            }
            drawPath(path, StickerInk.goldEdge, style = Stroke(this.size.minDimension * 0.04f, join = StrokeJoin.Round))
            val s = this.size.minDimension / 24f
            drawLine(Color(0xFFFFFAE1).copy(alpha = 0.85f), Offset(12f * s, 4.6f * s), Offset(13.7f * s, 8.3f * s), strokeWidth = 1.3f * s, cap = StrokeCap.Round)
        } else {
            drawPath(path, StickerInk.ink.copy(alpha = 0.5f), style = Stroke(this.size.minDimension * 0.05f, join = StrokeJoin.Round))
        }
    }
}

// ---------------------------------------------------------------- notes, tape, stamp

/** A sticky note: asymmetric corners, a strip of tape on top, tilted. */
@Composable
internal fun StickyNote(
    color: Color,
    modifier: Modifier = Modifier,
    tiltDeg: Float = 2f,
    tape: Color = Color(0xFFF3EDE4).copy(alpha = 0.34f),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomEnd = 14.dp, bottomStart = 3.dp)
    Box(modifier.rotate(tiltDeg)) {
        Box(
            Modifier
                .sheetShadow(10.dp, shape, 0.6f)
                .clip(shape)
                .background(color)
                .padding(start = 12.dp, end = 12.dp, top = 15.dp, bottom = 12.dp),
            content = content,
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .rotate(-5f)
                .size(width = 54.dp, height = 17.dp)
                .background(tape),
        )
    }
}

/** Washi tape: straight sides, zig-zag ends. */
internal val WashiShape: Shape = GenericShape { size, _ ->
    val t = 3f * (size.height / 24f).coerceAtLeast(0.5f)
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - t, size.height * 0.25f)
    lineTo(size.width, size.height * 0.5f)
    lineTo(size.width - t, size.height * 0.75f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    lineTo(t, size.height * 0.75f)
    lineTo(0f, size.height * 0.5f)
    lineTo(t, size.height * 0.25f)
    close()
}

/** A rubber stamp impression. */
@Composable
internal fun RubberStamp(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    tiltDeg: Float = -7f,
    size: TextUnit = 10.sp,
) {
    Box(
        modifier
            .rotate(tiltDeg)
            .border(2.dp, color, RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(text, style = StickerType.print(size, color, 1.8.sp).copy(fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false)
    }
}

/** Animates 0 → 1 once after [delayMs] when [play]; sits at 1 otherwise. */
@Composable
internal fun rememberOneShot(key: Any?, delayMs: Int, durationMs: Int, play: Boolean, easing: Easing = androidx.compose.animation.core.FastOutSlowInEasing): Animatable<Float, AnimationVector1D> {
    val anim = remember(key) { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(key, play) {
        if (!play) {
            anim.snapTo(1f)
        } else {
            anim.snapTo(0f)
            kotlinx.coroutines.delay(delayMs.toLong())
            anim.animateTo(1f, tween(durationMs, easing = easing))
        }
    }
    return anim
}
