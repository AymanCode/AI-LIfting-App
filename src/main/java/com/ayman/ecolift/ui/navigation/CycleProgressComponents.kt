package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCore
import com.ayman.ecolift.data.progress.LiftTrend
import com.ayman.ecolift.data.progress.Movement
import com.ayman.ecolift.data.progress.RepBucket
import com.ayman.ecolift.data.progress.ScoreBreakdown
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.ui.theme.HoldAmber
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.MarkerFontFamily
import com.ayman.ecolift.ui.theme.PosterFontFamily
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Poster = PosterFontFamily
private val Marker = MarkerFontFamily
private val Mono = LogUiFontFamily

/** Dark ink used for text sitting on bright poster surfaces (tape, headings). */
private val PosterInk = Color(0xFF08130F)

internal fun movementColor(movement: Movement, palette: GlassPalette): Color = when (movement) {
    Movement.IMPROVED -> palette.complete
    Movement.REGRESSED -> palette.danger
    Movement.HELD -> HoldAmber
}

internal fun formatPct(value: Float?): String =
    if (value == null) "—" else "${if (value >= 0f) "+" else ""}${value.roundToInt()}%"

internal fun verdictWord(score: Int): String = when {
    score >= 80 -> "Excellent cycle"
    score >= 62 -> "Strong cycle"
    score >= 45 -> "Steady progress"
    score >= 30 -> "Maintaining"
    else -> "Down cycle"
}

/** Gauge/verdict tint keyed to the composite score. */
internal fun verdictColor(score: Int, palette: GlassPalette): Color = when {
    score >= 45 -> palette.complete
    score >= 30 -> HoldAmber
    else -> palette.danger
}

internal fun ComparisonWindow.label(): String = when (this) {
    ComparisonWindow.M1 -> "1M"
    ComparisonWindow.M3 -> "3M"
    ComparisonWindow.M6 -> "6M"
}

internal fun ComparisonWindow.headline(): String = when (this) {
    ComparisonWindow.M1 -> "1 month ago"
    ComparisonWindow.M3 -> "3 months ago"
    ComparisonWindow.M6 -> "6 months ago"
}

internal fun formatVolumeLbsShort(lbs: Long): String = when {
    lbs >= 1_000_000 -> "%.1fM".format(Locale.US, lbs / 1_000_000f)
    lbs >= 10_000 -> "%,dk".format(Locale.US, lbs / 1_000)
    else -> "%,d".format(Locale.US, lbs)
}

/** Sessions per week across the cycle span, week 0 anchored at startDate. */
internal fun weeklySessionCounts(core: CycleProgressCore): List<Int> {
    val start = runCatching { LocalDate.parse(core.startDate) }.getOrNull() ?: return emptyList()
    val end = runCatching { LocalDate.parse(core.endDate) }.getOrNull() ?: return emptyList()
    val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
    val weeks = ((days - 1) / 7) + 1
    val counts = IntArray(weeks)
    core.sessionDates.forEach { iso ->
        val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return@forEach
        val index = ChronoUnit.DAYS.between(start, date).toInt() / 7
        if (index in 0 until weeks) counts[index]++
    }
    return counts.toList()
}

/** Slanted ink-block section heading with an offset accent shadow. */
@Composable
internal fun PosterHeading(text: String, modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    val shadow = palette.complete.copy(alpha = 0.45f)
    val fill = palette.ink
    Text(
        text = text.uppercase(Locale.US),
        color = PosterInk,
        fontFamily = Poster,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .rotate(-1f)
            .drawBehind {
                val off = 3.dp.toPx()
                drawRect(shadow, topLeft = Offset(off, off), size = size)
                drawRect(fill)
            }
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
internal fun WindowPills(
    selected: ComparisonWindow,
    onSelect: (ComparisonWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ComparisonWindow.entries.forEach { window ->
            val on = window == selected
            Text(
                text = window.label(),
                color = if (on) PosterInk else palette.inkSubtle,
                fontSize = 11.sp,
                fontFamily = Mono,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (on) palette.complete else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (on) palette.complete else Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(99.dp),
                    )
                    .clickable { onSelect(window) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Improved / held / regressed, read like a season record. */
@Composable
internal fun ArchiveRecordChips(comparison: CycleComparison) {
    val palette = LocalGlassPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        RecordChip(comparison.improvedCount, "improved", palette.complete, Modifier.weight(1f))
        RecordChip(comparison.heldCount, "held", HoldAmber, Modifier.weight(1f))
        RecordChip(comparison.regressedCount, "regressed", palette.danger, Modifier.weight(1f))
    }
}

@Composable
private fun RecordChip(count: Int, label: String, tint: Color, modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassFill)
            .border(1.dp, palette.glassStroke, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count.toString(), color = tint, fontFamily = Poster, fontSize = 20.sp)
        Text(
            label.uppercase(Locale.US),
            color = palette.inkSubtle,
            fontSize = 9.5.sp,
            fontFamily = Mono,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
internal fun ArchiveStatGrid(core: CycleProgressCore) {
    val palette = LocalGlassPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatCell(core.spanDays.toString(), "days", Modifier.weight(1f))
        StatCell(core.lifts.size.toString(), "lifts", Modifier.weight(1f))
        StatCell(core.sessions.toString(), "sessions", Modifier.weight(1f))
        StatCell(core.totalSets.toString(), "sets", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.glassFill)
            .border(1.dp, palette.glassStroke, RoundedCornerShape(12.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = palette.ink, fontFamily = Poster, fontSize = 16.sp)
        Text(
            label.uppercase(Locale.US),
            color = palette.inkSubtle,
            fontSize = 8.5.sp,
            fontFamily = Mono,
            letterSpacing = 1.3.sp,
        )
    }
}

/**
 * Poster hero: ridge art built from weekly session counts, the big composite
 * score, a tape verdict, and a marker-pen annotation. score == null renders
 * the unscored variant (dashed ridge, amber tape, no fake number).
 */
@Composable
internal fun ArchivePosterHero(
    name: String,
    dateRangeLabel: String,
    weeklyCounts: List<Int>,
    score: ScoreBreakdown?,
    window: ComparisonWindow,
    improvedCount: Int,
    comparedCount: Int,
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(18.dp)
    val scored = score != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.complete.copy(alpha = 0.14f),
                        palette.glassFillStrong.copy(alpha = 0.92f),
                    ),
                ),
            )
            .border(1.dp, palette.glassStrokeStrong, shape),
    ) {
        RidgeArt(
            counts = weeklyCounts,
            scored = scored,
            modifier = Modifier
                .matchParentSize()
                .align(Alignment.BottomCenter),
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
            Text(
                text = "CYCLE WRAPPED · ${dateRangeLabel.uppercase(Locale.US)}",
                color = palette.accentStrong,
                fontSize = 10.5.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.6.sp,
            )
            Text(
                text = name.uppercase(Locale.US),
                color = palette.ink,
                fontFamily = Poster,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .rotate(-1.2f),
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.rotate(-2.5f)) {
                Text(
                    text = if (scored) score!!.composite.toString() else "—",
                    color = if (scored) palette.ink else palette.ink.copy(alpha = 0.32f),
                    fontFamily = Poster,
                    fontSize = 72.sp,
                    lineHeight = 72.sp,
                )
                Text(
                    text = "/100",
                    color = if (scored) {
                        palette.accentStrong
                    } else {
                        HoldAmber.copy(alpha = 0.6f)
                    },
                    fontFamily = Poster,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )
            }
            VerdictTape(
                text = if (scored) verdictWord(score!!.composite) else "Unscored · first cycle",
                background = if (scored) verdictColor(score!!.composite, palette) else HoldAmber,
            )
            Text(
                text = if (scored) {
                    "↑ beat your last ${window.headline().removeSuffix(" ago")} " +
                        "on $improvedCount of $comparedCount lifts"
                } else {
                    "no history to race against yet — finish another cycle " +
                        "and the score unlocks"
                },
                color = if (scored) palette.accentStrong else HoldAmber,
                fontFamily = Marker,
                fontSize = 18.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .rotate(-1f),
            )
        }
    }
}

@Composable
private fun VerdictTape(text: String, background: Color) {
    Text(
        text = text.uppercase(Locale.US),
        color = PosterInk,
        fontFamily = Poster,
        fontSize = 13.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .padding(top = 10.dp)
            .rotate(-3f)
            .drawBehind {
                val off = 4.dp.toPx()
                drawRect(Color.Black.copy(alpha = 0.4f), topLeft = Offset(off, off), size = size)
                drawRect(background)
            }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** Layered mountain silhouette whose peaks follow weekly session counts. */
@Composable
private fun RidgeArt(counts: List<Int>, scored: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    Canvas(modifier) {
        val n = counts.size
        if (n == 0 || size.width <= 0f) return@Canvas
        val maxCount = counts.max().coerceAtLeast(1)
        val baseY = size.height
        val ceilingY = size.height * 0.30f
        val stepX = if (n > 1) size.width / (n - 1) else size.width
        fun pointY(count: Int): Float =
            baseY - (count.toFloat() / maxCount) * (baseY - ceilingY) * 0.92f - size.height * 0.05f

        val line = Path()
        counts.forEachIndexed { i, c ->
            val x = if (n > 1) stepX * i else size.width / 2f
            if (i == 0) line.moveTo(x, pointY(c)) else line.lineTo(x, pointY(c))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, baseY)
            lineTo(0f, baseY)
            close()
        }
        // Back ridge: same shape nudged up-left for depth.
        val backFill = Path().apply {
            counts.forEachIndexed { i, c ->
                val x = (if (n > 1) stepX * i else size.width / 2f) - stepX * 0.3f
                val y = pointY(c) - size.height * 0.08f
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            lineTo(size.width, baseY)
            lineTo(0f, baseY)
            close()
        }
        drawPath(
            backFill,
            Brush.verticalGradient(
                listOf(palette.auraBlue.copy(alpha = 0.30f), Color.Transparent),
                startY = ceilingY,
                endY = baseY,
            ),
        )
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(palette.complete.copy(alpha = if (scored) 0.42f else 0.22f), Color.Transparent),
                startY = ceilingY,
                endY = baseY,
            ),
        )
        drawPath(
            line,
            color = palette.accentStrong.copy(alpha = if (scored) 1f else 0.7f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = if (scored) {
                    null
                } else {
                    PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 7.dp.toPx()))
                },
            ),
        )
        if (scored) {
            // Flag planted on the best week.
            val peakIndex = counts.indexOf(counts.max())
            val px = if (n > 1) stepX * peakIndex else size.width / 2f
            val py = pointY(counts[peakIndex])
            val poleTop = py - 22.dp.toPx()
            drawLine(
                color = palette.ink,
                start = Offset(px, py),
                end = Offset(px, poleTop),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val flag = Path().apply {
                moveTo(px, poleTop)
                lineTo(px + 16.dp.toPx(), poleTop + 5.dp.toPx())
                lineTo(px, poleTop + 10.dp.toPx())
                close()
            }
            drawPath(flag, palette.complete)
            drawCircle(palette.accentStrong, radius = 3.dp.toPx(), center = Offset(px, py))
        }
    }
}

internal data class TickerSegment(val text: String, val tint: Color? = null)

internal fun buildTickerSegments(
    core: CycleProgressCore,
    comparison: CycleComparison?,
    totalVolumeLbs: Long?,
    palette: GlassPalette,
): List<TickerSegment> {
    val segments = mutableListOf<TickerSegment>()
    segments += TickerSegment("${core.sessions} SESSIONS")
    segments += TickerSegment("${core.totalSets} SETS")
    if (totalVolumeLbs != null && totalVolumeLbs > 0) {
        segments += TickerSegment("%,d LB MOVED".format(Locale.US, totalVolumeLbs))
    }
    val scored = comparison != null && comparison.comparedCount > 0
    if (scored) {
        segments += TickerSegment("${comparison!!.improvedCount} UP", palette.complete)
        segments += TickerSegment("${comparison.heldCount} HELD", HoldAmber)
        segments += TickerSegment("${comparison.regressedCount} DOWN", palette.danger)
    } else {
        segments += TickerSegment("${core.lifts.size} LIFTS LOGGED", palette.complete)
    }
    segments += TickerSegment("${core.spanDays} DAYS")
    if (scored) {
        val best = comparison!!.lifts
            .filter { !it.isNew && it.vsPct != null }
            .maxByOrNull { it.vsPct!! }
        if (best != null && (best.vsPct ?: 0f) > 0f) {
            segments += TickerSegment(
                "BEST: ${best.name.uppercase(Locale.US)} ${formatPct(best.vsPct)}",
                palette.complete,
            )
        }
    } else {
        segments += TickerSegment("EVERY PR STARTS AT ZERO")
    }
    return segments
}

/** Endless right-to-left marquee between two ink rules. */
@Composable
internal fun ArchiveTicker(segments: List<TickerSegment>, modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    if (segments.isEmpty()) return
    val text = buildAnnotatedString {
        segments.forEach { segment ->
            withStyle(SpanStyle(color = segment.tint ?: palette.inkMuted)) {
                append(segment.text)
            }
            withStyle(SpanStyle(color = palette.inkSubtle)) { append("  ✦  ") }
        }
    }
    var textWidth by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "archiveTicker")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = (textWidth * 14).coerceAtLeast(6_000),
                easing = LinearEasing,
            ),
        ),
        label = "archiveTickerShift",
    )
    val rule = palette.ink
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val w = 2.dp.toPx()
                drawLine(rule, Offset(0f, w / 2), Offset(size.width, w / 2), strokeWidth = w)
                drawLine(
                    rule,
                    Offset(0f, size.height - w / 2),
                    Offset(size.width, size.height - w / 2),
                    strokeWidth = w,
                )
            }
            .clipToBounds()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.graphicsLayer {
                translationX = -progress * textWidth
            },
        ) {
            TickerText(text, Modifier.onSizeChanged { textWidth = it.width })
            TickerText(text)
        }
    }
}

@Composable
private fun TickerText(text: androidx.compose.ui.text.AnnotatedString, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = Poster,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier,
    )
}

/**
 * Center-zero "tug of war" comparison vs the prior window. New lifts are
 * listed with a NEW badge instead of rendering N/A rows; when every lift is
 * new (first cycle) the section collapses to a friendly baseline summary.
 */
@Composable
internal fun VersusSection(
    comparison: CycleComparison?,
    window: ComparisonWindow,
    onWindowChange: (ComparisonWindow) -> Unit,
) {
    val palette = LocalGlassPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PosterHeading("You vs ${window.headline()}")
            Spacer(Modifier.weight(1f))
            WindowPills(selected = window, onSelect = onWindowChange)
        }
        Spacer(Modifier.height(14.dp))
        if (comparison == null) return@Column

        val ranked = comparison.lifts
            .filter { !it.isNew && it.vsPct != null }
            .sortedByDescending { it.vsPct }
        val newLifts = comparison.lifts.filter { it.isNew }
        val maxAbs = ranked.maxOfOrNull { abs(it.vsPct ?: 0f) }?.coerceAtLeast(1f) ?: 1f

        ranked.forEach { lift ->
            TugRow(
                name = lift.name,
                pct = lift.vsPct ?: 0f,
                fraction = (abs(lift.vsPct ?: 0f) / maxAbs).coerceIn(0f, 1f),
                color = movementColor(lift.movement, palette),
            )
        }

        val shownNew = newLifts.take(3)
        shownNew.forEach { lift ->
            NewLiftRow(
                name = lift.name,
                why = if (ranked.isEmpty()) {
                    "first time logged — baseline set ✓"
                } else {
                    "no ${window.label()} history — not scored"
                },
            )
        }

        val foot = when {
            ranked.isEmpty() && newLifts.isNotEmpty() -> {
                val extra = newLifts.size - shownNew.size
                val prefix = if (extra > 0) "+ $extra more — " else ""
                prefix + "all ${newLifts.size} lifts just set their baseline. " +
                    "Next cycle, this section becomes the scoreboard."
            }
            newLifts.isNotEmpty() ->
                "new lifts sit out the scoring — no penalty for trying things"
            else -> null
        }
        if (foot != null) {
            Text(
                text = foot,
                color = palette.inkSubtle,
                fontFamily = Marker,
                fontSize = 17.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .rotate(-1f),
            )
        }
    }
}

@Composable
private fun TugRow(name: String, pct: Float, fraction: Float, color: Color) {
    val palette = LocalGlassPalette.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by animateFloatAsState(
        targetValue = if (started) fraction else 0f,
        animationSpec = tween(650),
        label = "tug-$name",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 9.dp),
    ) {
        Text(
            text = name.uppercase(Locale.US),
            color = palette.ink,
            fontSize = 11.5.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(104.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(2.dp, palette.ink, RoundedCornerShape(9.dp)),
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                    if (pct < 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(animated)
                                .height(10.dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                                .background(color),
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (pct >= 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(animated)
                                .height(10.dp)
                                .clip(RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                                .background(color),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(palette.ink),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatPct(pct),
            color = color,
            fontFamily = Poster,
            fontSize = 12.5.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(46.dp),
        )
    }
}

@Composable
private fun NewLiftRow(name: String, why: String) {
    val palette = LocalGlassPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 9.dp),
    ) {
        Text(
            text = name.uppercase(Locale.US),
            color = palette.inkMuted,
            fontSize = 11.5.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(104.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "NEW",
            color = PosterInk,
            fontSize = 9.5.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .rotate(-2f)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.accentStrong)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = why,
            color = palette.inkSubtle,
            fontSize = 11.sp,
            fontFamily = Mono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class HighlightCard(
    val value: String,
    val valueTint: Color?,
    val tag: String,
    val title: String,
    val sub: String,
)

/**
 * Best / most played / worst as poster callouts. Degrades to always-available
 * facts (most played, total volume) when there is no comparison history.
 */
@Composable
internal fun HighlightReel(
    comparison: CycleComparison?,
    totalVolumeLbs: Long?,
) {
    val palette = LocalGlassPalette.current
    val ranked = comparison?.lifts
        ?.filter { !it.isNew && it.vsPct != null }
        ?.sortedByDescending { it.vsPct }
        .orEmpty()
    val mostPlayed = comparison?.lifts?.maxByOrNull { it.totalSets }

    val cards = buildList {
        val best = ranked.firstOrNull()
        if (best != null && (best.vsPct ?: 0f) > 0f) {
            add(
                HighlightCard(
                    value = formatPct(best.vsPct),
                    valueTint = palette.complete,
                    tag = "Steepest climb",
                    title = best.name,
                    sub = "your fastest-rising lift this cycle",
                ),
            )
        }
        if (mostPlayed != null && mostPlayed.totalSets > 0) {
            add(
                HighlightCard(
                    value = mostPlayed.totalSets.toString(),
                    valueTint = null,
                    tag = "Most played",
                    title = "${mostPlayed.name} · ${mostPlayed.totalSets} sets",
                    sub = "the track you kept on repeat",
                ),
            )
        }
        val worst = ranked.lastOrNull()
        if (worst != null && (worst.vsPct ?: 0f) < 0f) {
            add(
                HighlightCard(
                    value = formatPct(worst.vsPct),
                    valueTint = palette.danger,
                    tag = "Loose scree",
                    title = worst.name,
                    sub = "the one slope that slid back",
                ),
            )
        }
        if (ranked.isEmpty() && totalVolumeLbs != null && totalVolumeLbs > 0) {
            add(
                HighlightCard(
                    value = formatVolumeLbsShort(totalVolumeLbs),
                    valueTint = palette.accentStrong,
                    tag = "Total moved",
                    title = "%,d lb lifted".format(Locale.US, totalVolumeLbs),
                    sub = "every pound is on the record",
                ),
            )
        }
    }
    if (cards.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        PosterHeading("Highlight reel")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.forEach { card ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.glassFill)
                        .border(1.dp, palette.glassStroke, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = card.value,
                        color = card.valueTint ?: palette.ink,
                        fontFamily = Poster,
                        fontSize = 21.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(78.dp)
                            .rotate(-2f),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = card.tag.uppercase(Locale.US),
                            color = palette.inkSubtle,
                            fontSize = 9.5.sp,
                            fontFamily = Mono,
                            letterSpacing = 1.6.sp,
                        )
                        Text(
                            text = card.title,
                            color = palette.ink,
                            fontSize = 14.sp,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = card.sub,
                            color = palette.inkMuted,
                            fontSize = 11.5.sp,
                            fontFamily = Mono,
                        )
                    }
                }
            }
        }
    }
}

/** Weekly bars with the session count printed on top — read it like a score. */
@Composable
internal fun SessionsPerWeek(core: CycleProgressCore) {
    val palette = LocalGlassPalette.current
    val counts = weeklySessionCounts(core)
    if (counts.isEmpty()) return
    val maxCount = counts.max().coerceAtLeast(1)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    Column(modifier = Modifier.fillMaxWidth()) {
        PosterHeading("Sessions per week")
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            counts.forEachIndexed { index, count ->
                val target = if (count == 0) 6f else 14f + (count.toFloat() / maxCount) * 50f
                val animated by animateFloatAsState(
                    targetValue = if (started) target else 0f,
                    animationSpec = tween(600, delayMillis = index * 55),
                    label = "week-$index",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = count.toString(),
                        color = if (count == 0) palette.inkSubtle else palette.accentStrong,
                        fontFamily = Poster,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animated.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (count == 0) {
                                    Color.White.copy(alpha = 0.06f)
                                } else {
                                    palette.accent.copy(alpha = 0.75f)
                                },
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (count == 0) {
                                    Color.White.copy(alpha = 0.14f)
                                } else {
                                    palette.accentStrong.copy(alpha = 0.5f)
                                },
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                            ),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            val showEvery = if (counts.size > 12) 2 else 1
            counts.indices.forEach { index ->
                Text(
                    text = if (index % showEvery == 0) "W${index + 1}" else "",
                    color = palette.inkSubtle,
                    fontSize = 8.5.sp,
                    fontFamily = Mono,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val note = weeklyNote(counts, core.sessions)
        if (note != null) {
            Text(
                text = note,
                color = HoldAmber,
                fontFamily = Marker,
                fontSize = 17.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .rotate(-1.2f),
            )
        }
    }
}

private fun weeklyNote(counts: List<Int>, sessions: Int): String? {
    val weeks = counts.size
    if (weeks == 0) return null
    val zeroWeeks = counts.count { it == 0 }
    return when {
        sessions == 0 -> "nothing logged this cycle — blank pages"
        zeroWeeks > weeks / 2 ->
            "$sessions sessions in $weeks weeks — the next album needs more shows"
        zeroWeeks > 0 ->
            "week ${counts.indexOfFirst { it == 0 } + 1} went quiet — rest, or hiding from leg day?"
        else -> "no zero weeks — relentless"
    }
}

/** The rep-range mix as one stacked bar: the album's tracklist. */
@Composable
internal fun SetlistBar(buckets: List<RepBucket>) {
    val palette = LocalGlassPalette.current
    if (buckets.isEmpty() || buckets.sumOf { it.sets } == 0) return
    val dark = Color(0xFF0B1715)
    fun segColor(index: Int): Color = lerp(palette.accentStrong, dark, index * 0.24f)

    Column(modifier = Modifier.fillMaxWidth()) {
        PosterHeading("Setlist")
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, palette.ink, RoundedCornerShape(10.dp)),
        ) {
            buckets.forEachIndexed { index, bucket ->
                if (bucket.pctOfSets <= 0f) return@forEachIndexed
                Box(
                    modifier = Modifier
                        .weight(bucket.pctOfSets)
                        .fillMaxHeight()
                        .background(segColor(index)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bucket.pctOfSets >= 15f) {
                        Text(
                            text = "${bucket.pctOfSets.roundToInt()}%",
                            color = if (index < 2) PosterInk else palette.ink,
                            fontFamily = Poster,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            buckets.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    pair.forEachIndexed { i, bucket ->
                        val index = buckets.indexOf(bucket)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(segColor(index)),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "${bucket.label} ${repRange(bucket)} · " +
                                    "${bucket.sets} sets · ${bucket.pctOfSets.roundToInt()}%",
                                color = palette.inkMuted,
                                fontSize = 10.5.sp,
                                fontFamily = Mono,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun repRange(bucket: RepBucket): String =
    if (bucket.maxReps == Int.MAX_VALUE) "${bucket.minReps}+" else "${bucket.minReps}–${bucket.maxReps}"

internal enum class TrendSort(val label: String) { VELOCITY("velocity"), GAIN("gain"), AZ("A-Z") }

/** Per-lift index ("Deep cuts"), grouped by split, tap for the detail sheet. */
@Composable
internal fun TrendGrid(
    core: CycleProgressCore,
    comparison: CycleComparison?,
    sort: TrendSort,
    onSortChange: (TrendSort) -> Unit,
    onLiftClick: (LiftTrend) -> Unit,
) {
    val palette = LocalGlassPalette.current
    val vsByExercise = comparison?.lifts?.associate { it.exerciseId to (it.vsPct ?: 0f) }.orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PosterHeading("Deep cuts")
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TrendSort.entries.forEach { option ->
                    val on = option == sort
                    Text(
                        text = option.label,
                        color = if (on) PosterInk else palette.inkSubtle,
                        fontSize = 10.5.sp,
                        fontFamily = Mono,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (on) palette.complete else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (on) {
                                    palette.complete
                                } else {
                                    Color.White.copy(alpha = 0.25f)
                                },
                                shape = RoundedCornerShape(99.dp),
                            )
                            .clickable { onSortChange(option) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val groups = core.lifts.groupBy { it.splitName }
        if (groups.isEmpty()) {
            Text(
                "No lifts in this archived cycle.",
                color = palette.inkMuted,
                fontSize = 13.sp,
                fontFamily = Mono,
            )
        } else {
            groups.forEach { (splitName, lifts) ->
                Text(
                    text = "$splitName · ${lifts.size} ${if (lifts.size == 1) "lift" else "lifts"}",
                    color = palette.inkSubtle,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
                val sorted = when (sort) {
                    TrendSort.VELOCITY ->
                        lifts.sortedByDescending { it.slopePerWeek ?: Float.NEGATIVE_INFINITY }
                    TrendSort.GAIN -> lifts.sortedByDescending {
                        vsByExercise[it.exerciseId] ?: Float.NEGATIVE_INFINITY
                    }
                    TrendSort.AZ -> lifts.sortedBy { it.name.lowercase() }
                }
                sorted.forEach { lift ->
                    TrendRow(lift, onClick = { onLiftClick(lift) })
                }
            }
        }
    }
}

@Composable
private fun TrendRow(lift: LiftTrend, onClick: () -> Unit) {
    val palette = LocalGlassPalette.current
    val slope = lift.slopePerWeek
    val slopeColor = when {
        slope == null -> palette.inkMuted
        slope > 0.05f -> palette.complete
        slope < -0.05f -> palette.danger
        else -> palette.inkMuted
    }
    // Messy-data rule: never print N/A — say what we actually know.
    val slopeText = if (slope == null) {
        "logged ${lift.points.size}× — need more sessions for a trend"
    } else {
        "${if (slope >= 0f) "+" else ""}${"%.1f".format(slope)} ${lift.unitLabel}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    lift.name,
                    color = palette.ink,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (lift.isBodyweight) {
                    Spacer(Modifier.width(4.dp))
                    BwTag()
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(slopeText, color = slopeColor, fontSize = 11.5.sp, fontFamily = Mono)
        }
        Spacer(Modifier.width(12.dp))
        MiniSparkline(
            values = lift.points.map { it.value },
            color = slopeColor,
            modifier = Modifier.width(78.dp).height(30.dp),
        )
        if (lift.r2 != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(40.dp)) {
                Text(
                    "%.2f".format(lift.r2),
                    color = palette.inkMuted,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                )
                Text("fit", color = palette.inkSubtle, fontSize = 10.sp, fontFamily = Mono)
            }
        }
    }
}

@Composable
private fun BwTag() {
    val palette = LocalGlassPalette.current
    Text(
        "BW",
        color = palette.accentStrong,
        fontSize = 8.sp,
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.accent.copy(alpha = 0.16f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
