package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.*
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.GroupWallChartUi
import com.ayman.ecolift.ui.viewmodel.GroupWallPanelUi
import com.ayman.ecolift.ui.viewmodel.GroupWallTailUi
import com.ayman.ecolift.ui.viewmodel.GroupWallTileUi
import com.ayman.ecolift.ui.viewmodel.GroupWallTopSetUi
import com.ayman.ecolift.ui.viewmodel.GroupWallUi
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

/** Reuse the screen's body/display weights without introducing tracked, semibold tile text. */
@Composable
private fun GroupWallText(
    parts: List<DataPart>, modifier: Modifier = Modifier, size: TextUnit = EspressoType.chip.fontSize,
    lineHeight: TextUnit = (size.value + 4).sp, filler: Color = EspressoPalette.faint,
    data: Color = EspressoPalette.ink, maxLines: Int = 1,
) {
    val text = buildAnnotatedString {
        parts.forEach { part ->
            val style = if (part.isData) EspressoType.chipBold.toSpanStyle().copy(fontSize = size, color = data)
                else EspressoType.body.toSpanStyle().copy(fontSize = size, color = filler)
            withStyle(style) { append(part.text) }
        }
    }
    Text(text, modifier, style = EspressoType.body.copy(fontSize = size, lineHeight = lineHeight),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun GroupWallWindowChip(wall: GroupWallUi, expanded: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .clearAndSetSemantics {
            role = Role.Button
            contentDescription = "Progress window, ${wall.windowLabel}"
            stateDescription = if (expanded) "Range and period controls expanded" else "Range and period controls collapsed"
            onClick("Choose Progress window") { onClick(); true }
        }.padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Open, the period row below shows these dates, so the chip keeps only its chevron.
            AnimatedVisibility(!expanded, Modifier.weight(1f, fill = false),
                enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                GroupWallText(wall.windowParts, Modifier.padding(end = 6.dp), size = 12.sp,
                    filler = EspressoPalette.dim, maxLines = 1)
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
                modifier = Modifier.size(14.dp).rotate(if (expanded) 180f else 0f),
                tint = EspressoPalette.dim)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The poster card. One surface drawn by every slice of a group (header, tile rows, tail): each
// slice knows its offset from the card's top, so the gradient, halftone and grain run on across
// the seams. Offsets are deterministic: the header is fixed, each tile row is 145 dp + 8 dp.
// ---------------------------------------------------------------------------------------------

internal val GroupWallHeaderHeight = 84.dp
private val GroupWallFoldedHeight = 64.dp
private val GroupWallTileHeight = 145.dp
private val GroupWallRowGap = 8.dp

/** Distance from the card's top to tile row [row]. */
internal fun groupWallRowOffset(row: Int): Dp = GroupWallHeaderHeight + (GroupWallTileHeight + GroupWallRowGap) * row

/** The tail starts after [rows] tile rows. */
internal fun groupWallTailOffset(rows: Int): Dp = groupWallRowOffset(rows)

private val Cream = Color(0xFFFFE9DE)
private val CreamEdge = Color(0xFFFFE6DC)
private val TileFill = Color(0x8C0A0909)
private val DarkInk = Color(0xFF170F0B)
private val PosterFloor = Color(0xFF141212)
private val RingFill = Color(0xFF1B1210)
private val CenteredLines = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun wallMix(a: Color, b: Color, t: Float): Color =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/**
 * Gives a single-line display text a slot [height] tall with its baseline [fromBottom] above the slot's bottom,
 * measuring the text at its natural line height. A short `lineHeight` on a Text pins the baseline to the ascent
 * from the top instead of centring the glyphs, which sits a headline well below where its box says it is.
 */
private fun Modifier.baselineAt(height: Float, fromBottom: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val slot = height.dp.roundToPx()
    val baseline = slot - fromBottom.dp.roundToPx()
    layout(placeable.width, slot, mapOf(FirstBaseline to baseline)) { placeable.place(0, baseline - placeable[FirstBaseline]) }
}

/** League Gothic. */
private fun gothic(size: TextUnit, line: TextUnit = TextUnit.Unspecified, tracking: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified) = TextStyle(fontFamily = HighlightsDisplayFontFamily, fontWeight = FontWeight.Normal,
    fontSize = size, lineHeight = line, letterSpacing = tracking, color = color, lineHeightStyle = CenteredLines)

/** Space Grotesk SemiBold. */
private fun spaceSemi(size: TextUnit, tracking: TextUnit, color: Color, line: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = SheetSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = line,
        letterSpacing = tracking, color = color, lineHeightStyle = CenteredLines)

/** Clash Grotesk. */
private fun clash(size: TextUnit, line: TextUnit, weight: FontWeight, color: Color) = TextStyle(fontFamily = LogDisplayFontFamily,
    fontWeight = weight, fontSize = size, lineHeight = line, color = color, lineHeightStyle = CenteredLines)

/** The halftone field: dots on an 8 dp grid, 250 × 150 dp from the card's top-right corner. */
private class HalftoneDot(val x: Float, val y: Float, val tenths: Int)

private val halftoneDots: List<HalftoneDot> by lazy {
    buildList {
        var y = 4f
        while (y < 150f) {
            var x = 4f
            while (x < 250f) {
                val d = hypot((250f - x) / 250f, (y / 150f) * 0.9f)
                val r = 2.7f * (1f - d / 1.05f)
                if (r > 0.35f) add(HalftoneDot(x, y, (r * 10f).roundToInt()))
                x += 8f
            }
            y += 8f
        }
    }
}

/** One 128 px white noise tile with random alpha, made once and tiled by a repeating shader. Never per frame. */
private val posterGrain: ImageBitmap by lazy { renderPosterGrain() }

internal fun renderPosterGrain(): ImageBitmap {
    val side = 128
    val levels = 8
    val image = ImageBitmap(side, side)
    val canvas = GraphicsCanvas(image)
    val random = Random(7919)
    val buckets = Array(levels) { ArrayList<Offset>() }
    for (y in 0 until side) for (x in 0 until side) buckets[random.nextInt(levels)] += Offset(x + 0.5f, y + 0.5f)
    val paint = Paint().apply { strokeWidth = 1f; strokeCap = StrokeCap.Square; isAntiAlias = false }
    val skia = canvas.nativeCanvas
    buckets.forEachIndexed { level, points ->
        paint.color = Color.White.copy(alpha = (level + 1) / levels.toFloat() * 0.7f)
        val coords = FloatArray(points.size * 2)
        for (index in points.indices) {
            coords[index * 2] = points[index].x
            coords[index * 2 + 1] = points[index].y
        }
        skia.drawPoints(coords, paint.asFrameworkPaint())
    }
    return image
}
private val posterGrainBrush: ShaderBrush by lazy { ShaderBrush(ImageShader(posterGrain, TileMode.Repeated, TileMode.Repeated)) }

/**
 * The poster surface for one slice of a card whose top is [offsetY] above this slice's top. A 165°
 * gradient measured in dp from the card's top-left (accent, 23% black, 70% black, #141212 at 335 dp),
 * a halftone dot field anchored to the card's top-right and a grain tile, all clipped to the slice
 * (rounded 24 dp on the card's outer ends). Built once per size in `drawWithCache`; plain SrcOver,
 * no blend modes, blur, shadow or offscreen layer.
 */
internal fun Modifier.posterSurface(accent: Color, offsetY: Dp, top: Boolean, bottom: Boolean): Modifier = drawWithCache {
    val d = density
    val width = size.width
    val height = size.height
    val offset = offsetY.toPx()
    val corner = 24.dp.toPx()
    val rounded: Path? = if (top || bottom) Path().apply {
        addRoundRect(RoundRect(Rect(0f, 0f, width, height),
            topLeft = CornerRadius(if (top) corner else 0f), topRight = CornerRadius(if (top) corner else 0f),
            bottomRight = CornerRadius(if (bottom) corner else 0f), bottomLeft = CornerRadius(if (bottom) corner else 0f)))
    } else null
    val length = 335.dp.toPx()
    val start = Offset(0f, -offset)
    val brush = Brush.linearGradient(
        0f to accent,
        100f / 335f to wallMix(accent, Color.Black, 0.23f),
        215f / 335f to wallMix(accent, Color.Black, 0.70f),
        1f to PosterFloor,
        start = start, end = Offset(start.x + 0.2588f * length, start.y + 0.9659f * length),
    )
    // Dots bucketed to 0.1 dp radii; each bucket is one drawPoints call. Only dots touching this slice's band.
    val fieldLeft = width - 250f * d
    val byRadius = HashMap<Int, ArrayList<Offset>>()
    for (dot in halftoneDots) {
        val cy = dot.y * d - offset
        val r = dot.tenths / 10f * d
        if (cy + r >= 0f && cy - r <= height) byRadius.getOrPut(dot.tenths) { ArrayList() } += Offset(fieldLeft + dot.x * d, cy)
    }
    val halftone = byRadius.map { (tenths, points) -> points to 2f * tenths / 10f * d }
    val dotColor = Color(0xFFFFF1E8).copy(alpha = 0.30f)
    onDrawBehind {
        fun DrawScope.paintSlice() {
            drawRect(brush)
            for ((points, stroke) in halftone) drawPoints(points, PointMode.Points, dotColor, stroke, StrokeCap.Round)
            // The tile's origin is the card's top-left, so the grain continues across slices.
            translate(top = -offset) { drawRect(posterGrainBrush, topLeft = Offset(0f, offset), size = Size(width, height), alpha = 0.14f) }
        }
        if (rounded != null) clipPath(rounded) { paintSlice() } else clipRect { paintSlice() }
    }
}

/** Ink on the poster: dark on light accents, the screen's ink on dark ones. */
private fun posterInk(accent: Color): Color = if (accent.luminance() > 0.3f) DarkInk else EspressoPalette.ink

private fun posterNumeral(accent: Color): Color =
    if (accent.luminance() > 0.3f) wallMix(accent, Color.Black, 0.55f) else wallMix(accent, Color.White, 0.5f)

/**
 * The group name at the largest size from [startSp] down to [minSp] in [stepSp] steps that fits one line in
 * [maxWidthPx]; at [minSp] a name that still does not fit is cut to what fits plus an ellipsis here, not by
 * the Text, because a clipping Text would also shave the glyphs that rise above its short line box.
 */
private fun fitName(measurer: TextMeasurer, text: String, startSp: Float, minSp: Float, stepSp: Float, maxWidthPx: Int): Pair<Float, String> {
    fun width(value: String, sp: Float) = measurer.measure(value, gothic(sp.sp, tracking = 1.sp), softWrap = false, maxLines = 1).size.width
    var sp = startSp
    while (sp > minSp) {
        if (width(text, sp) <= maxWidthPx) return sp to text
        sp -= stepSp
    }
    if (width(text, minSp) <= maxWidthPx) return minSp to text
    var cut = text
    while (cut.isNotEmpty() && width("$cut…", minSp) > maxWidthPx) cut = cut.dropLast(1)
    return minSp to (cut.trimEnd() + "…")
}

/** The group name in League Gothic, fitted to one line in the width the Row leaves it. */
@Composable
private fun PosterName(name: String, startSp: Float, minSp: Float, ink: Color, modifier: Modifier, alignment: Alignment) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val maxPx = constraints.maxWidth
        val (sp, shown) = remember(name, startSp, minSp, maxPx) { fitName(measurer, name, startSp, minSp, 2f, maxPx) }
        // A 58 dp slot at 72 sp: caps rest 2.5 dp above its bottom, as the approved board sets them.
        Text(shown, Modifier.baselineAt(sp * 58f / 72f, sp * 0.0353f), style = gothic(sp.sp, tracking = 1.sp, color = ink),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
    }
}

/** Text set vertically, reading bottom to top; it occupies a (height × width) box. */
@Composable
private fun VerticalLabel(text: String, style: TextStyle) {
    Text(text, Modifier.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints())
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer(0, placeable.width) {
                rotationZ = -90f
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }, style = style, maxLines = 1, softWrap = false)
}

/** The session count: a League Gothic numeral with SESSION(S) set vertically beside it. */
@Composable
private fun PosterCount(count: Int, numeralSp: Float, numeralLine: Float, numeralColor: Color, ink: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(count.toString(), Modifier.baselineAt(numeralLine, numeralSp * 0.0355f), style = gothic(numeralSp.sp, color = numeralColor),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
        VerticalLabel(if (count == 1) "SESSION" else "SESSIONS", spaceSemi(8.5.sp, 2.2.sp, ink.copy(alpha = 0.78f)))
    }
}

@Composable
internal fun GroupWallPanelHeader(panel: GroupWallPanelUi, folded: Boolean, compact: Boolean, onToggle: () -> Unit) {
    val accent = panel.colorIndex?.let(::splitAccentColor) ?: EspressoPalette.dim
    val ink = posterInk(accent)
    val title = panel.title.uppercase()
    // The surface carries no button role. Only the folded slice and the empty expanded one own a
    // click; heading semantics would make the web mirror expose a heading instead of a button.
    if (folded) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(GroupWallFoldedHeight)
            .posterSurface(accent, 0.dp, top = true, bottom = true)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = panel.foldedDescription
                stateDescription = "Panel collapsed"
            }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterName(title, if (compact) 34f else 40f, 24f, ink, Modifier.weight(1f), Alignment.CenterStart)
            PosterCount(0, 34f, 30f, posterNumeral(accent), ink, Modifier.padding(start = 8.dp))
            panel.lastLabel?.let { last ->
                Column(Modifier.padding(start = 10.dp).background(TileFill, RoundedCornerShape(8.dp))
                    .border(1.dp, CreamEdge.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LAST", style = spaceSemi(7.sp, 1.6.sp, Cream.copy(alpha = 0.7f), 8.sp), maxLines = 1)
                    Text(last.uppercase(), style = gothic(18.sp, 18.sp, 0.3.sp, EspressoPalette.ink), maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Visible)
                }
            }
        }
    } else {
        val toggle = if (panel.sessions == 0) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier
        val alone = panel.rows.isEmpty() && panel.tail.isEmpty()
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(GroupWallHeaderHeight)
            .posterSurface(accent, 0.dp, top = true, bottom = alone)
            .then(toggle)
            .semantics(mergeDescendants = true) {
                contentDescription = panel.description
                if (panel.sessions == 0) stateDescription = "Panel expanded" else heading()
            }.padding(start = 14.dp, end = 14.dp, top = 14.dp),
            verticalAlignment = Alignment.Top) {
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.Bottom) {
                PosterName(title, if (compact) 56f else 72f, 40f, ink, Modifier.weight(1f), Alignment.BottomStart)
                if (compact) PosterCount(panel.sessions, 48f, 39f, posterNumeral(accent), ink, Modifier.padding(start = 8.dp).offset(y = 1.dp))
                else PosterCount(panel.sessions, 62f, 50f, posterNumeral(accent), ink, Modifier.padding(start = 8.dp).offset(y = 1.dp))
            }
        }
    }
}

@Composable
internal fun GroupWallTileRow(tiles: List<GroupWallTileUi>, colorIndex: Int?, compact: Boolean, onOpen: (Long) -> Unit,
    offsetY: Dp, bottom: Boolean = false) {
    val accent = colorIndex?.let(::splitAccentColor) ?: EspressoPalette.dim
    Row(Modifier.fillMaxWidth().posterSurface(accent, offsetY, top = false, bottom = bottom)
        .padding(start = 14.dp, end = 14.dp, bottom = if (bottom) 14.dp else GroupWallRowGap),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (tile in tiles) GroupWallTile(tile, accent, compact, { onOpen(tile.exerciseId) }, Modifier.weight(1f))
        if (tiles.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun GroupWallTile(tile: GroupWallTileUi, accent: Color, compact: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val tint = wallMix(accent, Color.White, 0.3f)
    val shape = RoundedCornerShape(16.dp)
    val sidePad = if (compact) 8.dp else 11.dp
    Column(modifier.height(GroupWallTileHeight).clip(shape).background(TileFill)
        .border(1.dp, CreamEdge.copy(alpha = 0.08f), shape)
        .clickable(role = Role.Button, onClick = onOpen)
        .clearAndSetSemantics {
            role = Role.Button
            contentDescription = tile.description
            onClick("Open exercise report") { onOpen(); true }
        }.padding(vertical = 10.dp)) {
        // No fixed height: a one-line name hands its second line to the chart.
        Text(tile.name, Modifier.padding(horizontal = sidePad),
            style = clash(12.5.sp, 15.sp, FontWeight.Bold, EspressoPalette.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
        GroupWallChart(tile.chart, tint, Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp, bottom = 3.dp))
        GroupWallValueRow(tile, tint, Modifier.padding(horizontal = sidePad))
    }
}

/** Shifts a child left by [amount] and gives that room back to its siblings, keeping its baseline. */
private fun Modifier.pullLeft(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width - shift, placeable.height, mapOf(FirstBaseline to placeable[FirstBaseline])) {
        placeable.place(-shift, 0)
    }
}

/**
 * A top set in League Gothic: the weight (with the LB superscript unless it is plain BW), a tinted ×
 * pulled in under the superscript, and the reps. The weight ellipsizes before anything else gives way.
 */
@Composable
private fun TopSetText(top: GroupWallTopSetUi, tint: Color, numberSp: Float, ink: Color, modifier: Modifier = Modifier) {
    val number = gothic(numberSp.sp, (numberSp - 1f).sp, 0.3.sp, ink)
    Row(modifier) {
        Text(top.weight, Modifier.weight(1f, fill = false).alignByBaseline(), style = number, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis)
        if (top.showsLb) {
            Text("LB", Modifier.align(Alignment.Top).padding(start = 1.dp, top = 1.dp),
                style = spaceSemi(6.5.sp, 1.1.sp, tint), maxLines = 1, softWrap = false)
            Text("×", Modifier.alignByBaseline().pullLeft(4.dp).padding(end = 1.dp), style = gothic(14.sp, 14.sp, color = tint),
                maxLines = 1, softWrap = false)
        } else {
            Text("×", Modifier.alignByBaseline().padding(horizontal = 1.dp), style = gothic(14.sp, 14.sp, color = tint),
                maxLines = 1, softWrap = false)
        }
        Text(top.reps, Modifier.alignByBaseline(), style = number, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
    }
}

/** Natural width of a top set at [numberSp], in pixels, matching [TopSetText]'s layout. */
private fun topSetWidth(measurer: TextMeasurer, top: GroupWallTopSetUi, numberSp: Float, density: Float): Float {
    val number = gothic(numberSp.sp, tracking = 0.3.sp)
    fun width(text: String, style: TextStyle) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
    var total = (width(top.weight, number) + width(top.reps, number) + width("×", gothic(14.sp))).toFloat()
    total += if (top.showsLb) width("LB", spaceSemi(6.5.sp, 1.1.sp, Color.Unspecified)) + (1f + 1f - 4f) * density else 2f * density
    return total
}

/** Largest top-set number size (21 sp down to 15 sp) whose set still fits beside the pill. */
private fun fitTopSetSp(measurer: TextMeasurer, top: GroupWallTopSetUi, availablePx: Float, density: Float): Float {
    var sp = 21f
    while (sp > 15f) {
        if (topSetWidth(measurer, top, sp, density) <= availablePx) return sp
        sp -= 1f
    }
    return 15f
}

@Composable
private fun WinPill(number: String, unit: String, isReps: Boolean) {
    val ink = if (isReps) Color(0xFF10301A) else Color(0xFF3A160A)
    val fill = if (isReps) Brush.verticalGradient(listOf(Color(0xFFB6E6C1), Color(0xFF8CCB9A)))
        else Brush.verticalGradient(listOf(Color(0xFFFFF3EA), Color(0xFFF6DCCD)))
    Row(Modifier.height(21.dp).background(fill, RoundedCornerShape(7.dp)).padding(start = 6.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Spacer(Modifier.size(7.dp, 6.dp).drawWithCache {
            val triangle = Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            onDrawBehind { drawPath(triangle, ink) }
        })
        Text(number, style = gothic(16.sp, 16.sp, 0.3.sp, ink), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
        Text(unit, Modifier.align(Alignment.Top).padding(top = 4.dp), style = spaceSemi(6.5.sp, 1.1.sp, ink, 8.sp),
            maxLines = 1, softWrap = false)
    }
}

@Composable
private fun GroupWallValueRow(tile: GroupWallTileUi, tint: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxWidth().height(22.dp)) {
        val top = tile.topSet
        val maxPx = constraints.maxWidth
        val numberSp = remember(top, tile.winNumber, tile.winUnit, maxPx, density) {
            if (top == null) {
                21f
            } else {
                val pill = if (tile.win == null) {
                    0f
                } else {
                    fun width(text: String, style: TextStyle) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
                    (6f + 7f + 3f + 3f + 7f) * density + width(tile.winNumber, gothic(16.sp, tracking = 0.3.sp)) +
                        width(tile.winUnit, spaceSemi(6.5.sp, 1.1.sp, Color.Unspecified))
                }
                fitTopSetSp(measurer, top, maxPx - pill - (if (tile.win == null) 0f else 6f * density), density)
            }
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (top == null) Text("—", style = gothic(21.sp, 20.sp, 0.3.sp, EspressoPalette.ink.copy(alpha = 0.5f)), maxLines = 1)
            else TopSetText(top, tint, numberSp, EspressoPalette.ink, Modifier.weight(1f, fill = false))
            tile.win?.let { win ->
                Spacer(Modifier.width(6.dp))
                WinPill(tile.winNumber, tile.winUnit, win.isReps)
            }
        }
    }
}

/** A cream tone at [alpha]; every chart mark is this colour or the tile's tint. */
private fun cream(alpha: Float): Color = Cream.copy(alpha = alpha)

/**
 * The tile chart: a 1RM (or +LB / REPS) scale in the left gutter, gridlines on round values, week,
 * month, quarter or year marks under a baseline, and the line with its fade, dots by available room
 * and a haloed latest dot. Text is measured and every path built in `drawWithCache` (once per size);
 * drawing only replays them.
 */
@Composable
private fun GroupWallChart(chart: GroupWallChartUi, tint: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Spacer(modifier.drawWithCache {
        val d = density
        val w = size.width
        val h = size.height
        val yStyle = spaceSemi(6.8.sp, TextUnit.Unspecified, cream(0.62f))
        val unitStyle = spaceSemi(5.8.sp, 0.6.sp, cream(0.45f))
        val xStyle = spaceSemi(6.2.sp, 0.4.sp, cream(0.60f))
        val yLabels = chart.yTicks.map { measurer.measure(it.label, yStyle, softWrap = false, maxLines = 1) }
        val unit = measurer.measure(chart.unit, unitStyle, softWrap = false, maxLines = 1)
        val widest = max(unit.size.width, yLabels.maxOfOrNull { it.size.width } ?: 0)
        // The widest label starts 11 dp in, on the tile name's left edge, not against the rounded border.
        val gutter = max(23f * d, 15f * d + widest)
        val padTop = 8f * d
        val base = h - 10f * d
        val plotW = (w - gutter - 9f * d).coerceAtLeast(1f)
        val plotH = (base - padTop - 4f * d).coerceAtLeast(1f)
        fun xAt(fraction: Float) = gutter + fraction * plotW
        fun yAt(fraction: Float) = padTop + (1f - fraction) * plotH
        val axisEnd = w - 6f * d

        // x labels, left to right: a label too close to the last one (or the unit) slides inward by up
        // to 9 dp, and is skipped if it still collides or would leave the card.
        class Placed(val text: TextLayoutResult, val centre: Float)
        val labels = ArrayList<Placed>()
        var lastRight = gutter - 1f * d
        val gap = 3f * d
        val slide = 9f * d
        for (tick in chart.xTicks) {
            val label = tick.label ?: continue
            val text = measurer.measure(label, xStyle, softWrap = false, maxLines = 1)
            val half = text.size.width / 2f
            var centre = xAt(tick.fraction)
            if (centre - half <= lastRight + gap && lastRight + gap + half - centre <= slide) centre = lastRight + gap + half + 0.5f * d
            if (centre + half >= w - 2f * d && centre + half - (w - 3f * d) <= slide) centre = w - 3f * d - half
            if (centre - half > lastRight + gap && centre + half < w - 2f * d) {
                labels += Placed(text, centre)
                lastRight = centre + half
            }
        }

        val n = chart.pointCount
        val px = FloatArray(n) { xAt(chart.xs[it]) }
        val py = FloatArray(n) { yAt(chart.ys[it]) }
        val line = Path()
        val fill = Path()
        var fillBrush: Brush? = null
        if (n > 1) {
            line.moveTo(px[0], py[0])
            for (i in 1 until n) line.lineTo(px[i], py[i])
            fill.addPath(line)
            fill.lineTo(px[n - 1], base)
            fill.lineTo(px[0], base)
            fill.close()
            fillBrush = Brush.verticalGradient(0f to tint.copy(alpha = 0.40f), 1f to tint.copy(alpha = 0f), startY = py.min(), endY = base)
        }
        val lineStroke = Stroke((if (n > 40) 1.6f else 2f) * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ringStroke = Stroke(1.5f * d)
        val room = plotW / d / max(1, n)
        val dash = PathEffect.dashPathEffect(floatArrayOf(2f * d, 3f * d))
        val empty = if (n == 0) measurer.measure("NO SESSIONS", spaceSemi(7.5.sp, 1.6.sp, cream(0.5f)), softWrap = false, maxLines = 1) else null

        onDrawBehind {
            chart.yTicks.forEachIndexed { i, tick ->
                val y = yAt(tick.fraction)
                drawLine(cream(0.13f), Offset(gutter, y), Offset(axisEnd, y), 1f * d, pathEffect = dash)
                val text = yLabels[i]
                drawText(text, topLeft = Offset(gutter - 4f * d - text.size.width, y + 2.4f * d - text.firstBaseline))
            }
            drawLine(cream(0.32f), Offset(gutter, base), Offset(axisEnd, base), 1f * d)
            drawText(unit, topLeft = Offset(gutter - 4f * d - unit.size.width, base + 9.5f * d - unit.firstBaseline))
            for (tick in chart.xTicks) {
                val x = xAt(tick.fraction)
                drawLine(cream(if (tick.major) 0.45f else 0.28f), Offset(x, base), Offset(x, base + (if (tick.major) 3f else 1.8f) * d), 1f * d)
            }
            for (placed in labels) {
                drawText(placed.text, topLeft = Offset(placed.centre - placed.text.size.width / 2f, base + 9.5f * d - placed.text.firstBaseline))
            }
            if (empty != null) {
                val chipW = empty.size.width + 12f * d
                val chipH = empty.size.height + 6f * d
                val left = w / 2f - chipW / 2f
                val topY = h * 0.44f - chipH / 2f
                drawRoundRect(cream(0.30f), Offset(left, topY), Size(chipW, chipH), CornerRadius(6f * d),
                    style = Stroke(1f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * d, 2f * d))))
                drawText(empty, topLeft = Offset(left + 6f * d, topY + 3f * d))
            }
            if (n > 1) {
                drawPath(fill, fillBrush!!)
                drawPath(line, tint, style = lineStroke)
            }
            for (i in 0 until n - 1) {
                val at = Offset(px[i], py[i])
                if (room >= 7f) {
                    drawCircle(RingFill, 2.5f * d, at)
                    drawCircle(tint, 2.5f * d, at, style = ringStroke)
                } else if (room >= 3.2f) drawCircle(tint, 1.3f * d, at)
            }
            if (n > 0) {
                val latest = Offset(px[n - 1], py[n - 1])
                drawCircle(tint.copy(alpha = 0.22f), 6.5f * d, latest)
                drawCircle(tint, 3.6f * d, latest)
            }
        }
    })
}

/**
 * Lifts with too few sessions for a tile still read as data: a kicker row (label, count chip and a
 * fading hairline) over a two-column grid of slim chips on the poster, each with the name and top set.
 */
@Composable
internal fun GroupWallPanelTail(panel: GroupWallPanelUi, compact: Boolean, onOpen: (Long) -> Unit, offsetY: Dp) {
    if (panel.tail.isEmpty()) return
    val accent = panel.colorIndex?.let(::splitAccentColor) ?: EspressoPalette.dim
    val tint = wallMix(accent, Color.White, 0.3f)
    Column(Modifier.fillMaxWidth().posterSurface(accent, offsetY, top = false, bottom = true)
        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        // 6 dp here on top of the last tile row's 8 dp gap makes the 14 dp the card is drawn with.
        Row(Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(panel.tailLabel, style = spaceSemi(8.5.sp, 2.2.sp, cream(0.78f)), maxLines = 1)
            // `+N LIFTS` already carries the count; the `ONE SESSION` kicker does not, so only it keeps the chip.
            if (panel.tail.all { it.sessions == 1 }) Text(panel.tail.size.toString(), Modifier.background(TileFill, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
                style = gothic(17.sp, 17.sp, color = wallMix(accent, Color.White, 0.5f)), maxLines = 1, overflow = TextOverflow.Visible)
            Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(
                listOf(CreamEdge.copy(alpha = 0.30f), CreamEdge.copy(alpha = 0f)))))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (pair in panel.tail.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (tail in pair) GroupWallTailChip(tail, tint, { onOpen(tail.exerciseId) }, Modifier.weight(1f))
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GroupWallTailChip(tail: GroupWallTailUi, tint: Color, onOpen: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.height(48.dp).clip(shape).background(TileFill).border(1.dp, CreamEdge.copy(alpha = 0.08f), shape)
        .clickable(role = Role.Button, onClick = onOpen)
        .clearAndSetSemantics {
            role = Role.Button; contentDescription = tail.description
            onClick("Open exercise report") { onOpen(); true }
        }.padding(start = 10.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tail.name, Modifier.weight(1f), style = clash(11.5.sp, 14.sp, FontWeight.SemiBold, EspressoPalette.ink),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        TopSetText(GroupWallTopSetUi(tail.weight, tail.reps, showsLb = false), tint, 22f, EspressoPalette.ink)
    }
}
