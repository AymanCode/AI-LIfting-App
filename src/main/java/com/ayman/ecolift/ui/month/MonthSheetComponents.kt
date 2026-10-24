package com.ayman.ecolift.ui.month

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.progress.MuscleShare
import com.ayman.ecolift.data.progress.MuscleSlice
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.web.util.formatRecordedVolumeLbs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The month sheet's parts, translated from the reference build `month-sheet-v6.html`.
 *
 * Three rules from that build are load-bearing and easy to undo by accident:
 * no gradients, no shadows, no cards -- sections are separated by hairlines and whitespace,
 * and a filled shape always means data (a trained day, a bar, a muscle region).
 * One accent, [SheetAcc], and it is spent only on: the current month cell, the heaviest
 * session bar, the heaviest calendar day, record dots, the selected muscle, the top jump,
 * the aside's left border and the share link.
 */

// ---- tokens (spec section 1) ----
internal val SheetBg = Color(0xFF0E0E0C)
internal val SheetInk = Color(0xFFEDECE6)
internal val SheetDim = Color(0xFFA29D92)
internal val SheetFaint = Color(0xFF8E897F)
internal val SheetHair = Color(0xFF22211D)
internal val SheetRowLine = Color(0xFF1B1A17)
internal val SheetAcc = Color(0xFFE8552F)
internal val SheetUp = Color(0xFF7CB87C)

fun leadRegionColor(region: BodyPartRegion): Color = when (region) {
    BodyPartRegion.LEGS -> SheetAcc
    BodyPartRegion.CHEST -> Color(0xFF6F8FF2)
    BodyPartRegion.ARMS -> Color(0xFFB79AF0)
    BodyPartRegion.BACK -> Color(0xFF56A5EC)
    BodyPartRegion.SHOULDERS -> Color(0xFFCC84E8)
    BodyPartRegion.CORE -> Color(0xFFE6C27A)
    BodyPartRegion.UNKNOWN -> SheetDim
}

@Composable
private fun StarIcon(size: androidx.compose.ui.unit.Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val innerR = r * 0.382f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val starPath = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else innerR
            val angle = (-PI / 2.0 + i * (PI / 5.0)).toFloat()
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        drawPath(starPath, color = color)
    }
}

@Composable
private fun UpTriangleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(7.dp, 6.dp)) {
        val trianglePath = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(trianglePath, color = color)
    }
}

@Composable
fun GhostChip(
    annotatedText: AnnotatedString,
    modifier: Modifier = Modifier,
    isRecord: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1B1A17), RoundedCornerShape(5.dp))
            .border(1.dp, if (isRecord) Color(0xFF4A2418) else Color(0xFF2A2925), RoundedCornerShape(5.dp))
            .let { if (onClick != null) it.sheetTap { onClick() } else it }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isRecord) {
                StarIcon(size = 8.dp, color = SheetAcc)
            }
            Text(
                text = annotatedText,
                style = TextStyle(
                    fontFamily = SheetSansFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    color = if (isRecord) SheetAcc else SheetDim,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun GhostChip(
    text: String,
    modifier: Modifier = Modifier,
    isRecord: Boolean = false,
    onClick: (() -> Unit)? = null,
) = GhostChip(AnnotatedString(text), modifier, isRecord, onClick)

@Composable
fun WinChip(win: com.ayman.ecolift.ui.viewmodel.ChangelogWin, modifier: Modifier = Modifier) {
    val bg = if (win.isReps) SheetUp else SheetInk
    val textColor = if (win.isReps) Color(0xFF0C1A0C) else Color(0xFF111111)
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UpTriangleIcon(color = textColor)
            Text(
                text = win.label.uppercase(),
                style = TextStyle(
                    fontFamily = SheetSansFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = textColor,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private val WEEKDAY_INITIALS = listOf("S", "M", "T", "W", "T", "F", "S")

// ---- type ----

internal fun sheetSans(
    size: Double,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = 0.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = SheetSansFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    color = color,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

internal fun sheetSerif(
    size: Double,
    color: Color,
    italic: Boolean = false,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = 0.sp,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = SheetSerifFontFamily,
    fontWeight = FontWeight.Light,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    fontSize = size.sp,
    color = color,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** A tap target with no ripple and no press tint -- the sheet reacts by changing data, not chrome. */
@Composable
internal fun Modifier.sheetTap(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

// ---- motion ----

/** The reference build's bar easing, shared by the session, rhythm and roster bars. */
private val SheetGrowEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** The mix squares' overshoot. */
private val SheetPopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * Animates from nothing to [target] on first composition, and follows [target] after that.
 *
 * [animateFloatAsState] seeded with its own target never moves, so a bar written that way
 * is drawn already grown. The latch gives the first frame something to grow from.
 */
@Composable
private fun rememberGrowth(
    target: Float,
    durationMillis: Int,
    delayMillis: Int = 0,
    easing: androidx.compose.animation.core.Easing = SheetGrowEasing,
    label: String,
): State<Float> {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    return animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = tween(durationMillis, delayMillis, easing),
        label = label,
    )
}

// ---- reveal ----

/**
 * Fades and lifts its content in once, the first time 15% of it is on screen.
 *
 * This is the Compose counterpart of the reference build's IntersectionObserver: same
 * threshold, same "fire once" rule, same .7s ease. Sections that are already on screen
 * at first layout reveal immediately, which is what the browser does too.
 */
@Composable
fun SheetReveal(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    val viewportHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val alpha by animateFloatAsState(if (revealed) 1f else 0f, tween(700), label = "sheetRevealAlpha")
    val lift by animateFloatAsState(if (revealed) 0f else 12f, tween(700), label = "sheetRevealLift")

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                if (revealed) return@onGloballyPositioned
                val bounds = coordinates.boundsInWindow()
                val onScreen = min(bounds.bottom, viewportHeight) - max(bounds.top, 0f)
                if (onScreen > 0f && onScreen >= bounds.height * 0.15f) revealed = true
            }
            .graphicsLayer {
                this.alpha = alpha
                translationY = lift.dp.toPx()
            },
    ) {
        content()
    }
}

// ---- tabs and hero ----

/**
 * The Progress header's tab row in the sheet's own language: lowercase Space Grotesk labels over a full-width
 * hairline, a short ink rule under the current one. Each cell is as tall as an Espresso pill, so the labels
 * sit where they do under the Espresso header.
 *
 * The screen-reader label stays the title-case name, so the tabs read the same in both themes.
 */
@Composable
fun SheetProgressTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val on = index == selectedIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .semantics { role = Role.Tab; contentDescription = label; selected = on }
                        .sheetTap { onSelect(index) }
                        // 1.5dp, drawn from half a dp above the cell's bottom edge, so it covers the hairline under it.
                        .drawBehind {
                            if (!on) return@drawBehind
                            val inset = size.width * 0.18f
                            drawRect(
                                color = SheetInk,
                                topLeft = Offset(inset, size.height - 0.5.dp.toPx()),
                                size = Size(size.width - inset * 2f, 1.5.dp.toPx()),
                            )
                        },
                ) {
                    Text(
                        text = label.lowercase(),
                        style = sheetSans(12.5, if (on) SheetInk else SheetFaint, lineHeight = 16.sp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        SheetHairline(verticalMargin = 0.dp)
    }
}

// ---- section furniture ----

@Composable
fun SheetHairline(modifier: Modifier = Modifier, verticalMargin: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalMargin)
            .height(1.dp)
            .background(SheetHair),
    )
}

private val SheetDataSpan = SpanStyle(color = SheetInk, fontWeight = FontWeight.Medium)
private val SheetNumber = Regex("""\d[\d,.]*""")

/** "2 splits", "2 of 31 days": the numbers are data, the words stay quiet. */
internal fun inkNumbers(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    SheetNumber.findAll(text).forEach { addStyle(SheetDataSpan, it.range.first, it.range.last + 1) }
}

/**
 * "ELEVEN SESSIONS" after a short accent tick, a quiet annotation on the right. Sections are not
 * steps, so they carry no numbers (owner, 2026-09-25); the sheet's hairlines already divide them.
 */
@Composable
fun SheetSectionLabel(title: String, trailing: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.alignByBaseline(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 9.dp)
                    .size(width = 3.dp, height = 10.dp)
                    .background(SheetAcc, RoundedCornerShape(1.5.dp)),
            )
            Text(
                text = title.uppercase(),
                style = sheetSans(10.0, SheetDim, letterSpacing = 1.5.sp),
                modifier = Modifier.semantics { heading() },
            )
        }
        if (trailing != null) {
            Text(
                text = inkNumbers(trailing),
                style = sheetSans(9.0, SheetFaint, letterSpacing = 0.54.sp),
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

// ---- 01 sessions ----

/** One session, as the chart and the calendar both need it. */
data class SheetSession(
    val dayOfMonth: Int,
    val dateLabel: String,
    val loadLbs: Double,
    val label: String,
    val isHeaviest: Boolean,
    val hasRecord: Boolean,
    val color: Color = SheetInk,
    val fillFraction: Float = 0f,
    val muscleNames: String = "",
)

// ---- 02 the mix ----

/** One mix row: a name, its count, row color and lead region. */
data class SheetMixRow(
    val name: String,
    val count: Int,
    val color: Color = SheetInk,
    val leadRegion: BodyPartRegion = BodyPartRegion.UNKNOWN,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetMix(rows: List<SheetMixRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.name.uppercase(),
                    style = sheetSans(10.0, row.color, weight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
                    maxLines = 1,
                    modifier = Modifier.width(142.dp),
                )
                Spacer(Modifier.width(8.dp))
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(row.count) {
                        val scale by rememberGrowth(
                            target = 1f,
                            durationMillis = 400,
                            easing = SheetPopEasing,
                            label = "sheetMixSquare",
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .background(row.color),
                        )
                    }
                }
                Text(
                    text = "×${row.count}",
                    style = sheetSans(11.0, SheetInk, weight = FontWeight.SemiBold, tabular = true),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

// ---- 03 the figure (fig.js v3 anatomy with ring callouts) ----

private val FigBody = Color(0xFF161513)
private val FigEdge = Color(0xFF34322D)
private val FigBase = Color(0xFF2B2A26)
private val FigGap = Color(0xFF0E0E0C)

private const val FIG_SIL =
    "M100,50 C92,50 91,54 91,58 C90,62 86,65 80,67 C74,69 68,70 62,71 C50,73 43,81 43,94 " +
    "C42,108 41,124 41,138 C41,150 40,158 40,164 C40,182 42,204 45,224 C44,236 46,250 51,252 C56,254 58,246 57,236 " +
    "C57,230 57,226 57,224 C57,208 58,186 59,168 C59,150 60,124 61,104 C63,118 66,134 69,150 C71,160 72,170 71,180 " +
    "C70,192 67,202 66,212 C64,236 64,262 68,284 C70,292 70,298 69,304 C66,318 66,336 70,350 C73,360 76,368 77,374 " +
    "C75,384 78,392 88,392 C94,392 95,388 94,380 C93,372 93,364 94,356 C96,340 96,322 94,306 C93,298 93,292 94,284 " +
    "C96,262 98,236 99,220 C99.5,216 100,214 100,212 Z"

private const val FIG_BICEPS =
    "M44,113 C50,111 55,107 59.5,102 L60.6,102 L60.4,159 C52,161.5 40,161.5 30,160 L30,113 Z"

private const val FIG_FOREARM =
    "M30,162.5 C40,164 52,164 62,162 L62,223 L30,223 Z"

private fun figRr(x: Double, y: Double, w: Double, h: Double, r: Double): String =
    "M${x + r},$y H${x + w - r} Q${x + w},$y ${x + w},${y + r} V${y + h - r} Q${x + w},${y + h} ${x + w - r},${y + h} H${x + r} Q$x,${y + h} $x,${y + h - r} V${y + r} Q$x,$y ${x + r},$y Z"

private val FIG_FRONT_PARTS: List<Pair<MuscleSlice?, String>> = listOf(
    MuscleSlice.BACK to "M91.5,48 L91.5,69 C86,68.6 78,69 68.5,72 C64,72.5 58,71 52,68 L60,56 Z",
    MuscleSlice.SHOULDERS to "M68.5,72 C67.5,81 64,92 59.5,102 C55,107 50,111 44,113 L30,113 L30,64 L52,68 C58,71 64,72.5 68.5,72 Z",
    MuscleSlice.CHEST to "M100,70.5 L100,108.5 C95,112 88,114.5 81,114 C72,113.5 65,109 59.5,102 C64,92 67.5,81 68.5,72 C78,69 88,68.6 100,70.5 Z",
    MuscleSlice.BICEPS to FIG_BICEPS,
    MuscleSlice.FOREARMS to FIG_FOREARM,
    MuscleSlice.CORE to figRr(86.5, 117.5, 12.6, 13.5, 2.5),
    MuscleSlice.CORE to figRr(86.5, 133.5, 12.6, 13.5, 2.5),
    MuscleSlice.CORE to figRr(86.5, 149.5, 12.6, 13.5, 2.5),
    MuscleSlice.CORE to "M86.5,165.5 L99.1,165.5 L99.1,205 C95,207 90.5,203 88.5,197 C87,188 86.5,176 86.5,165.5 Z",
    MuscleSlice.CORE to "M85,118 C80,117 72,115.5 64,108 L62,106 L62,200 L72,204 L85,211 Z",
    MuscleSlice.QUADS to "M61,199 L72,204 C75,230 76.5,258 73,288 L60,288 Z",
    MuscleSlice.QUADS to "M72,204 L90,214 C90.3,232 90.2,246 89.5,257 C88.8,267 87.8,274 86,280 C82,285 77,288 73,288 C76.5,258 75,230 72,204 Z",
    null to "M90,214 L100,216 L100,251 L89.5,257 C90.2,246 90.3,232 90,214 Z",
    MuscleSlice.QUADS to "M89.5,257 L100,251 L100,292 C95,291.5 90,290 86,288 L86,280 C87.8,274 88.8,267 89.5,257 Z",
    MuscleSlice.CALVES to "M50,304 L76,306 C77,320 77,336 74,352 L50,352 Z",
    null to "M75,295.5 C77,290 87,290 89,295.5 C89,300 87,303 82,303.5 C77,303 75,300 75,295.5 Z",
    null to "M76,306 L84,306 C84.5,325 83.5,345 81,364 C79,362 76,358 74,352 C77,336 77,320 76,306 Z",
    MuscleSlice.CALVES to "M84,306 L100,306 L100,352 C95,354 89,357 81,364 C83.5,345 84.5,325 84,306 Z",
)

private val FIG_FRONT_LINES = listOf(
    "M64.5,114 L72,119",
    "M65.5,123 L73,127.5",
    "M67,132 L74,135.5",
)

private val FIG_BACK_PARTS: List<Pair<MuscleSlice?, String>> = listOf(
    MuscleSlice.BACK to "M99.6,44 L99.6,140 C95,128 91,110 86,94 C80,84 72,78 64,74 L52,69 L56,56 L92,46 Z",
    MuscleSlice.SHOULDERS to "M64,74 C63,84 61,95 58,103 C54,108 50,111 44,113 L30,113 L30,60 L52,69 Z",
    MuscleSlice.BACK to "M64,74 C72,78 80,84 86,94 C87.5,98 88.5,102 89,106 C82,106 72,104 62.5,100 C63,92 63.8,82 64,74 Z",
    MuscleSlice.BACK to "M62.5,100 C72,104 82,106 89,106 C91.5,118 94,128 99.6,140 L99.6,146 C90,156 78,170 64,186 L62,188 L62,104 Z",
    MuscleSlice.BACK to "M99.6,146 L99.6,194 L89,193.5 C89,178 90,164 92,152 C94,150 96,148 99.6,146 Z",
    MuscleSlice.CORE to "M92,152 C90,164 89,178 89,193.5 C80,193.5 72,195 66,198 L62,199 L62,188 L64,186 C76,172 86,160 92,152 Z",
    MuscleSlice.TRICEPS to FIG_BICEPS,
    MuscleSlice.FOREARMS to FIG_FOREARM,
    MuscleSlice.GLUTES to "M100,194 C88,193.5 76,194.5 66,198 L61,200 L60.5,220 C63,229 68,235 76,237 C84,239 92,237 100,233 Z",
    MuscleSlice.HAMSTRINGS to "M60.5,220 C63,229 68,235 76,237 C78,237.6 80,237.9 82,238 C83,255 83,272 80,293 L60.5,293 Z",
    MuscleSlice.HAMSTRINGS to "M82,238 C86,238.5 92,237 100,233 L100,293 L80,293 C83,272 83,255 82,238 Z",
    MuscleSlice.CALVES to "M50,298 L84,300 C85,315 85,330 84,346 C80,350 76,348 72,346 L50,340 Z",
    MuscleSlice.CALVES to "M84,300 L100,300 L100,350 C96,352 90,351 84,346 C85,330 85,315 84,300 Z",
    MuscleSlice.CALVES to "M50,340 L72,346 C76,348 80,350 84,346 C90,351 96,352 100,350 L100,366 L50,366 Z",
)

private val FIG_BACK_LINES = listOf(
    "M52,133 C54.5,140 55,148 53,156",
)

private val FIG_FRONT_ANCHORS = mapOf(
    MuscleSlice.CHEST to Offset(83f, 92f),
    MuscleSlice.SHOULDERS to Offset(53f, 90f),
    MuscleSlice.CORE to Offset(82f, 140f),
    MuscleSlice.BICEPS to Offset(51.5f, 134f),
    MuscleSlice.FOREARMS to Offset(46f, 190f),
    MuscleSlice.QUADS to Offset(82.5f, 240f),
    MuscleSlice.CALVES to Offset(71.5f, 328f),
)

private val FIG_BACK_ANCHORS = mapOf(
    MuscleSlice.BACK to Offset(83f, 122f),
    MuscleSlice.SHOULDERS to Offset(53f, 90f),
    MuscleSlice.TRICEPS to Offset(50.5f, 121f),
    MuscleSlice.FOREARMS to Offset(46f, 190f),
    MuscleSlice.GLUTES to Offset(82f, 214f),
    MuscleSlice.HAMSTRINGS to Offset(76f, 262f),
    MuscleSlice.CALVES to Offset(76f, 322f),
)

private val FRONT_CALLOUTS = listOf(
    MuscleSlice.CHEST to 14f,
    MuscleSlice.SHOULDERS to 88f,
    MuscleSlice.BICEPS to 162f,
    MuscleSlice.QUADS to 240f,
    MuscleSlice.CALVES to 318f,
)

private val BACK_CALLOUTS = listOf(
    MuscleSlice.BACK to 50f,
    MuscleSlice.TRICEPS to 124f,
    MuscleSlice.GLUTES to 204f,
    MuscleSlice.HAMSTRINGS to 278f,
)

@Composable
fun CalloutRing(
    setsPct: Int,
    lbPct: Int,
    progress: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(30.dp)) {
        val strokeWidth = 3.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = 11.dp.toPx()
        val innerRadius = 6.dp.toPx()
        val trackColor = Color(0xFF2A2925)

        drawCircle(trackColor, outerRadius, center, style = Stroke(strokeWidth))
        drawCircle(trackColor, innerRadius, center, style = Stroke(strokeWidth))

        val setsAngle = (setsPct.toFloat() * 3.6f * progress).coerceIn(0f, 360f)
        if (setsAngle > 0f) {
            drawArc(
                color = SheetInk,
                startAngle = -90f,
                sweepAngle = setsAngle,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(strokeWidth),
            )
        }

        val lbAngle = (lbPct.toFloat() * 3.6f * progress).coerceIn(0f, 360f)
        if (lbAngle > 0f) {
            drawArc(
                color = SheetAcc,
                startAngle = -90f,
                sweepAngle = lbAngle,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(strokeWidth),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFigureHalf(
    silhouette: Path,
    parts: List<Pair<MuscleSlice?, Path>>,
    lines: List<Path>,
    loadBySlice: Map<MuscleSlice, Double>,
    maxLoad: Double,
    selected: MuscleSlice?,
    clock: Float,
    order: Map<MuscleSlice, Int>,
) {
    drawPath(silhouette, FigBody)
    clipPath(silhouette) {
        parts.forEach { (slice, path) ->
            drawPath(path, FigBase)

            if (slice != null) {
                val load = loadBySlice[slice] ?: 0.0
                if (load > 0.0) {
                    val rank = order[slice] ?: 0
                    val totalDuration = (order.size * 130 + 300).toFloat()
                    val startProgress = (rank * 130).toFloat() / totalDuration
                    val muscleProgress = if (clock > startProgress) {
                        ((clock - startProgress) / (300f / totalDuration)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val heatAlpha = if (slice == selected) 0.98f
                    else (0.2f + 0.8f * (load / maxLoad).toFloat()).coerceIn(0f, 1f)

                    if (muscleProgress > 0f) {
                        drawPath(path, SheetAcc.copy(alpha = heatAlpha * muscleProgress))
                    }
                }
            }

            val b = path.getBounds()
            if (b.width > 0f && b.height > 0f) {
                drawPath(
                    path = path,
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0f)),
                        center = Offset(b.left + b.width * 0.42f, b.top + b.height * 0.32f),
                        radius = max(b.width, b.height) * 0.75f,
                    ),
                )
            }

            drawPath(path, FigGap, style = Stroke(width = 1.5f, join = StrokeJoin.Round))
        }

        lines.forEach { linePath ->
            drawPath(linePath, FigGap, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
        }
    }

    drawPath(silhouette, FigEdge, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
}

@Composable
fun SheetAnatomyWhereItWent(
    muscles: List<MuscleShare>,
    selected: MuscleSlice?,
    onSelect: (MuscleSlice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val setsShareBySlice = remember(muscles) {
        muscles.associate { it.slice to (it.setShare * 100f).roundToInt() }
    }
    val lbShareBySlice = remember(muscles) {
        muscles.associate { it.slice to (it.loadShare * 100f).roundToInt() }
    }
    val setsBySlice = remember(muscles) {
        muscles.associate { it.slice to it.sets }
    }
    val loadBySlice = remember(muscles) {
        muscles.associate { it.slice to it.loadLbs }
    }
    val maxLoad = remember(muscles) {
        muscles.maxOfOrNull { it.loadLbs } ?: 1.0
    }

    val silPath = remember { PathParser().parsePathString(FIG_SIL).toPath() }
    val frontPaths = remember {
        FIG_FRONT_PARTS.map { (slice, d) -> slice to PathParser().parsePathString(d).toPath() }
    }
    val backPaths = remember {
        FIG_BACK_PARTS.map { (slice, d) -> slice to PathParser().parsePathString(d).toPath() }
    }
    val frontLines = remember {
        FIG_FRONT_LINES.map { PathParser().parsePathString(it).toPath() }
    }
    val backLines = remember {
        FIG_BACK_LINES.map { PathParser().parsePathString(it).toPath() }
    }

    val order = remember(loadBySlice) {
        loadBySlice.entries
            .sortedByDescending { it.value }
            .mapIndexed { rank, entry -> entry.key to rank }
            .toMap()
    }
    val timeline = order.size * 130 + 300
    val clock by rememberGrowth(
        target = 1f,
        durationMillis = timeline,
        easing = LinearEasing,
        label = "sheetAnatomyClock",
    )
    val ringProgress by rememberGrowth(
        target = 1f,
        durationMillis = 800,
        easing = SheetGrowEasing,
        label = "sheetRingProgress",
    )

    val S = 0.7f
    val topOffset = 4f

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val availableWidth = maxWidth.value
        val scale = min(1f, availableWidth / 338f)
        val stageWidth = (338f * scale).dp
        val stageHeight = (300f * scale).dp

        Box(
            modifier = Modifier
                .width(stageWidth)
                .height(stageHeight),
        ) {
            // An oversized child is centred by default, which pushed the scaled stage up over the
            // legend on narrow screens; pin it to the top-left so the scale grows from there.
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(338.dp, 300.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fx = 44.dp.toPx()
                    val bx = 138.dp.toPx()
                    val topPx = topOffset.dp.toPx()
                    val figureWidthPx = 140.dp.toPx()
                    val scale = figureWidthPx / 200f

                    // 1. Front figure
                    withTransform({
                        translate(left = fx, top = topPx)
                        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                    }) {
                        drawOval(color = FigBody, topLeft = Offset(85f, 10f), size = Size(30f, 40f))
                        drawOval(color = FigEdge, topLeft = Offset(85f, 10f), size = Size(30f, 40f), style = Stroke(1.2f))
                        drawFigureHalf(silPath, frontPaths, frontLines, loadBySlice, maxLoad, selected, clock, order)
                        withTransform({
                            translate(left = 200f, top = 0f)
                            scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
                        }) {
                            drawFigureHalf(silPath, frontPaths, frontLines, loadBySlice, maxLoad, selected, clock, order)
                        }
                    }

                    // 2. Back figure
                    withTransform({
                        translate(left = bx, top = topPx)
                        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                    }) {
                        drawOval(color = FigBody, topLeft = Offset(85f, 10f), size = Size(30f, 40f))
                        drawOval(color = FigEdge, topLeft = Offset(85f, 10f), size = Size(30f, 40f), style = Stroke(1.2f))
                        drawFigureHalf(silPath, backPaths, backLines, loadBySlice, maxLoad, selected, clock, order)
                        withTransform({
                            translate(left = 200f, top = 0f)
                            scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
                        }) {
                            drawFigureHalf(silPath, backPaths, backLines, loadBySlice, maxLoad, selected, clock, order)
                        }
                    }

                    // Connecting lines:
                    FRONT_CALLOUTS.forEach { (slice, ly) ->
                        val anchor = FIG_FRONT_ANCHORS[slice] ?: Offset(53f, 90f)
                        val px = fx + anchor.x * scale
                        val py = topPx + anchor.y * scale
                        val targetY = topPx + ly * scale
                        val ex = 76.dp.toPx()
                        val kx = min(px - 8.dp.toPx(), fx + 40f * scale + 4.dp.toPx())

                        val elbowPath = Path().apply {
                            moveTo(px, py)
                            lineTo(kx, targetY)
                            lineTo(ex, targetY)
                        }
                        drawPath(elbowPath, color = Color(0xFF77726A), style = Stroke(1.dp.toPx()))
                        drawCircle(color = SheetInk, radius = 2.dp.toPx(), center = Offset(px, py))
                        drawCircle(color = Color(0xFF0E0E0C), radius = 2.dp.toPx(), center = Offset(px, py), style = Stroke(1.dp.toPx()))
                    }

                    BACK_CALLOUTS.forEach { (slice, ly) ->
                        val anchor = FIG_BACK_ANCHORS[slice] ?: Offset(83f, 122f)
                        val px = bx + (200f - anchor.x) * scale
                        val py = topPx + anchor.y * scale
                        val targetY = topPx + ly * scale
                        val ex = 262.dp.toPx()
                        val kx = max(px + 8.dp.toPx(), bx + 160f * scale - 4.dp.toPx())

                        val elbowPath = Path().apply {
                            moveTo(px, py)
                            lineTo(kx, targetY)
                            lineTo(ex, targetY)
                        }
                        drawPath(elbowPath, color = Color(0xFF77726A), style = Stroke(1.dp.toPx()))
                        drawCircle(color = SheetInk, radius = 2.dp.toPx(), center = Offset(px, py))
                        drawCircle(color = Color(0xFF0E0E0C), radius = 2.dp.toPx(), center = Offset(px, py), style = Stroke(1.dp.toPx()))
                    }
                }

                Text(
                    text = "FRONT",
                    style = sheetSans(8.5, SheetFaint, letterSpacing = 1.8.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = 44.dp, y = (topOffset + 400 * S - 2).dp)
                        .width(140.dp),
                )
                Text(
                    text = "BACK",
                    style = sheetSans(8.5, SheetFaint, letterSpacing = 1.8.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = 138.dp, y = (topOffset + 400 * S - 2).dp)
                        .width(140.dp),
                )

                FRONT_CALLOUTS.forEach { (slice, ly) ->
                    val setsPct = setsShareBySlice[slice] ?: 0
                    val lbPct = lbShareBySlice[slice] ?: 0
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = (topOffset + ly * S - 7f).dp)
                            .width(76.dp)
                            .clickable { onSelect(slice) },
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = slice.name.uppercase(),
                                style = sheetSans(8.5, SheetInk, weight = FontWeight.SemiBold, letterSpacing = 1.3.sp),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$setsPct%",
                                        style = sheetSans(10.5, SheetInk, weight = FontWeight.SemiBold, lineHeight = 12.sp),
                                    )
                                    Text(
                                        text = "$lbPct%",
                                        style = sheetSans(10.5, SheetAcc, weight = FontWeight.SemiBold, lineHeight = 12.sp),
                                    )
                                }
                                CalloutRing(setsPct = setsPct, lbPct = lbPct, progress = ringProgress)
                            }
                        }
                    }
                }

                BACK_CALLOUTS.forEach { (slice, ly) ->
                    val setsPct = setsShareBySlice[slice] ?: 0
                    val lbPct = lbShareBySlice[slice] ?: 0
                    Box(
                        modifier = Modifier
                            .offset(x = 264.dp, y = (topOffset + ly * S - 7f).dp)
                            .width(74.dp)
                            .clickable { onSelect(slice) },
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = slice.name.uppercase(),
                                style = sheetSans(8.5, SheetInk, weight = FontWeight.SemiBold, letterSpacing = 1.3.sp),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CalloutRing(setsPct = setsPct, lbPct = lbPct, progress = ringProgress)
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = "$setsPct%",
                                        style = sheetSans(10.5, SheetInk, weight = FontWeight.SemiBold, lineHeight = 12.sp),
                                    )
                                    Text(
                                        text = "$lbPct%",
                                        style = sheetSans(10.5, SheetAcc, weight = FontWeight.SemiBold, lineHeight = 12.sp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One heat row: muscle, sets, and that muscle's share of the month's sets.
 *
 * The share is denominated in sets, the same measure the rows are sorted by, so the column
 * always falls as you read down. A load-denominated share was the first cut and it read as
 * a broken sort: heavy, low-rep work like calf raises jumped above light, high-volume work
 * like lateral raises, and nothing on screen explained why.
 */
data class SheetHeatRow(
    val slice: MuscleSlice,
    val name: String,
    val sets: Int,
    val sharePct: Int,
)

/** One slice of the load donut. [pct] is a whole percent; the slices always total 100. */
data class SheetPieSlice(
    val region: BodyPartRegion,
    val name: String,
    val pct: Int,
    val sets: Int,
    val loadLbs: Double,
)

/** Degrees trimmed from every slice so neighbours read as two shapes, not one gradient. */
private const val SLICE_GAP_DEGREES = 2f

// ---- 04 calendar and rhythm ----

/** One calendar cell's state. [session] is null on a day with nothing logged. */
data class SheetDayCell(val dayOfMonth: Int, val session: SheetSession?)

@Composable
fun SheetCalendar(
    days: List<SheetDayCell>,
    leadingBlanks: Int,
    selected: Int?,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells: List<SheetDayCell?> = List(leadingBlanks) { null } + days
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            WEEKDAY_INITIALS.forEach { initial ->
                Text(
                    text = initial,
                    style = sheetSans(9.0, SheetFaint, letterSpacing = 0.54.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                week.forEach { cell ->
                    if (cell == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        SheetCalendarCell(
                            cell = cell,
                            isSelected = cell.dayOfMonth == selected,
                            onClick = { onSelectDay(cell.dayOfMonth) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun SheetCalendarCell(
    cell: SheetDayCell,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = cell.session
    val trained = session != null
    val cellShape = RoundedCornerShape(4.dp)
    val baseDark = Color(0xFF141311)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(cellShape)
            .background(baseDark)
            .let {
                if (trained) it.background(session.color.copy(alpha = 0.16f)) else it
            }
            .let {
                if (isSelected) it.border(1.5.dp, SheetInk, cellShape) else it
            }
            .sheetTap(enabled = trained) { onClick() },
    ) {
        if (trained && session.fillFraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(session.fillFraction)
                    .background(session.color),
            )
        }
        Text(
            text = cell.dayOfMonth.toString(),
            style = sheetSans(
                size = 10.0,
                color = if (trained) SheetInk else Color(0xFF5F5B54),
                weight = if (trained) FontWeight.SemiBold else FontWeight.Medium,
                tabular = true,
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetCalendarReadout(
    session: SheetSession?,
    modifier: Modifier = Modifier,
) {
    if (session == null) return
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        GhostChip(
            annotatedText = buildAnnotatedString {
                withStyle(SpanStyle(color = SheetInk, fontWeight = FontWeight.SemiBold)) {
                    append(session.dateLabel)
                }
            },
        )
        if (session.muscleNames.isNotBlank()) {
            GhostChip(
                annotatedText = AnnotatedString(session.muscleNames),
            )
        }
        GhostChip(
            annotatedText = buildAnnotatedString {
                withStyle(SpanStyle(color = SheetInk, fontWeight = FontWeight.SemiBold)) {
                    append(formatRecordedVolumeLbs(session.loadLbs))
                }
                append(" LB")
            },
        )
        if (session.hasRecord) {
            GhostChip(
                annotatedText = AnnotatedString("RECORD"),
                isRecord = true,
            )
        }
    }
}

/** One lift's within-month change. [pct] is signed. */
data class SheetLiftDelta(
    val name: String,
    val sessions: Int,
    val pct: Int,
    val thin: Boolean,
)
