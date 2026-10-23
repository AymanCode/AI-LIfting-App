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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCore
import com.ayman.ecolift.data.progress.RepBucket
import com.ayman.ecolift.data.progress.ScoreBreakdown
import com.ayman.ecolift.ui.theme.EspressoRangeTrack
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.ui.theme.HoldAmber
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.PosterFontFamily
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.formatVolumeLbsShort
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

private val Poster = PosterFontFamily
private val Mono = LogUiFontFamily

/** Dark ink used for text sitting on bright poster surfaces (tape, headings). */
private val PosterInk = Color(0xFF08130F)

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

/** Sessions per week across the cycle span, week 0 anchored at startDate. */
internal fun weeklySessionCounts(core: CycleProgressCore): List<Int> {
    val start = runCatching { LocalDate.parse(core.startDate) }.getOrNull() ?: return emptyList()
    val end = runCatching { LocalDate.parse(core.endDate) }.getOrNull() ?: return emptyList()
    val days = (end.toEpochDays() - start.toEpochDays() + 1).toInt().coerceAtLeast(1)
    val weeks = ((days - 1) / 7) + 1
    val counts = IntArray(weeks)
    core.sessionDates.forEach { iso ->
        val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return@forEach
        val index = (date.toEpochDays() - start.toEpochDays()).toInt() / 7
        if (index in 0 until weeks) counts[index]++
    }
    return counts.toList()
}

/** First bar always; after that only where the count changes from the week before. */
internal fun weekLabelIndices(counts: List<Int>): Set<Int> =
    counts.indices.filter { it == 0 || counts[it] != counts[it - 1] }.toSet()

internal fun deepCutMinSpan(values: List<Float>, isBodyweightReps: Boolean): Float =
    if (isBodyweightReps) 2f else (values.average().toFloat() * 0.05f).coerceAtLeast(1f)

/** Each week's sessions as a share of the busiest week; nothing to draw when no week had any. */
internal fun albumRidgeHeights(weeklyCounts: List<Int>): List<Float> {
    val max = weeklyCounts.maxOrNull() ?: return emptyList()
    if (max <= 0) return emptyList()
    return weeklyCounts.map { it.toFloat() / max }
}

/** The album art's flag sits on the best week; on a tie, the latest of them (-1 with no heights). */
internal fun albumFlagIndex(heights: List<Float>): Int {
    val best = heights.maxOrNull() ?: return -1
    return heights.indexOfLast { it == best }
}

/** The flag's centre, pulled in from either edge so the whole dot of [radius] stays on the art. */
internal fun albumFlagCenter(position: Float, radius: Float, extent: Float): Float =
    if (extent <= 2 * radius) extent / 2 else position.coerceIn(radius, extent - radius)

/**
 * The 1M/3M/6M pills do nothing while every lift is new (a first cycle), so on the opening 3M
 * window they wait for the comparison to load and show only if some lift was compared, which
 * keeps them from flashing on a first cycle. Once the lifter has moved off 3M they stay, so a
 * window where every lift happens to be new, or whose comparison fails, is never a dead end.
 */
internal fun windowPillsVisible(comparisonLoaded: Boolean, allLiftsAreNew: Boolean, window: ComparisonWindow): Boolean =
    window != ComparisonWindow.M3 || (comparisonLoaded && !allLiftsAreNew)

/** Slanted ink-block section heading with an offset accent shadow. */
@Composable
internal fun PosterHeading(text: String, modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    val shadow = palette.complete.copy(alpha = 0.45f)
    val fill = palette.ink
    Text(
        text = text.uppercase(),
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

/**
 * Whether the range track drops under the You-vs heading: they share a row only while the heading,
 * [gap] and the track all fit in [maxWidth] (all in px), so on a narrow phone the last pill is
 * never cut off.
 */
internal fun versusTrackWraps(headingWidth: Int, trackWidth: Int, gap: Int, maxWidth: Int): Boolean =
    headingWidth + gap + trackWidth > maxWidth

@Composable
internal fun WindowPills(
    selected: ComparisonWindow,
    onSelect: (ComparisonWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    EspressoRangeTrack(ComparisonWindow.entries, selected, { it.label() }, onSelect, modifier)
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
            label.uppercase(),
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
            label.uppercase(),
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
    volumeLbs: Long?,
    linerNotes: Boolean = false,
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
                text = "LINER NOTES" + if (dateRangeLabel.isNotBlank()) " · ${dateRangeLabel.uppercase()}" else "",
                color = palette.accentStrong,
                fontSize = 10.5.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.6.sp,
                modifier = Modifier.semantics {
                    if (linerNotes) contentDescription = "Liner notes" + if (dateRangeLabel.isNotBlank()) ", $dateRangeLabel" else ""
                },
            )
            Text(
                text = name.uppercase(),
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
                    text = if (scored) score!!.composite.toString() else formatVolumeLbsShort(volumeLbs ?: 0L).uppercase(),
                    color = palette.ink,
                    fontFamily = Poster,
                    fontSize = 72.sp,
                    lineHeight = 72.sp,
                )
                Text(
                    text = if (scored) "/100" else "LB MOVED",
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
        }
    }
}

@Composable
private fun VerdictTape(text: String, background: Color) {
    Text(
        text = text.uppercase(),
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

/** Layered mountain silhouette; archive liner notes pass working sets per week. */
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

/**
 * Only what appears nowhere else on the album: the tiles, the hero and the record chips already
 * carry sessions, sets, pounds moved, lifts, days and the up/held/down counts (Ayman, 2026-10-01:
 * the tiles stay). That leaves the best lift on a scored cycle; empty means no ticker.
 */
internal fun buildTickerSegments(
    core: CycleProgressCore,
    comparison: CycleComparison?,
    totalVolumeLbs: Long?,
    palette: GlassPalette,
): List<TickerSegment> {
    if (comparison == null || comparison.comparedCount <= 0) return emptyList()
    val best = comparison.lifts
        .filter { !it.isNew && it.vsPct != null }
        .maxByOrNull { it.vsPct!! }
    if (best == null || (best.vsPct ?: 0f) <= 0f) return emptyList()
    return listOf(
        TickerSegment("BEST: ${best.name.uppercase()} ${formatPct(best.vsPct)}", palette.complete),
    )
}

/** Endless right-to-left marquee between two ink rules. */
@Composable
internal fun ArchiveTicker(segments: List<TickerSegment>, modifier: Modifier = Modifier, scrolling: Boolean = true) {
    val palette = LocalGlassPalette.current
    if (segments.isEmpty()) return
    val text = buildAnnotatedString {
        segments.forEach { segment ->
            withStyle(SpanStyle(color = segment.tint ?: palette.inkMuted)) {
                append(segment.text)
            }
            withStyle(SpanStyle(color = palette.inkSubtle)) { append("  ·  ") }
        }
    }
    if (!scrolling) {
        // Keep the full ticker on a touch-scrollable strip without a permanent frame clock.
        Row(modifier.fillMaxWidth().drawBehind {
            val width = 2.dp.toPx()
            drawLine(palette.ink, Offset(0f, width / 2), Offset(size.width, width / 2), strokeWidth = width)
            drawLine(palette.ink, Offset(0f, size.height - width / 2),
                Offset(size.width, size.height - width / 2), strokeWidth = width)
        }.horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp)) { TickerText(text) }
        return
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
        // Web divergence: on CMP-web a softWrap=false Text inside a Row reports a
        // width clamped to the incoming constraints (Android reports the full line
        // width), so the Android Row-of-two-copies version painted both copies on
        // top of each other. Measure unbounded and place both copies by the true
        // string width instead.
        Layout(
            content = {
                TickerText(text)
                TickerText(text)
            },
            modifier = Modifier.graphicsLayer {
                translationX = -progress * textWidth
            },
        ) { measurables, constraints ->
            val unbounded = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
            val first = measurables[0].measure(unbounded)
            val second = measurables[1].measure(unbounded)
            if (textWidth != first.width) textWidth = first.width
            layout(constraints.maxWidth, first.height) {
                first.placeRelative(0, 0)
                second.placeRelative(first.width, 0)
            }
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

private data class HighlightCard(
    val value: String,
    val valueTint: Color?,
    val tag: String,
    val title: String,
    /** Printed small after [value], like the hero's "LB MOVED". */
    val unit: String? = null,
)

/**
 * Steepest climb and most played as poster callouts, wins only, drawn once the
 * comparison has loaded (both read it); the hero carries the volume.
 */
@Composable
internal fun HighlightReel(comparison: CycleComparison?) {
    val palette = LocalGlassPalette.current
    val ranked = comparison?.lifts
        ?.filter { !it.isNew && !it.isBodyweight && it.vsPct != null }
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
                ),
            )
        }
        if (mostPlayed != null && mostPlayed.totalSets > 0) {
            add(
                HighlightCard(
                    value = mostPlayed.totalSets.toString(),
                    valueTint = null,
                    tag = "Most played",
                    title = mostPlayed.name,
                    unit = "sets",
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
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .width(92.dp)
                            .rotate(-2f),
                    ) {
                        Text(
                            text = card.value,
                            color = card.valueTint ?: palette.ink,
                            fontFamily = Poster,
                            // Triple-digit percentages ("+123%") must never wrap: one line, a wider column.
                            fontSize = if (card.value.length >= 5) 18.sp else 21.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.alignByBaseline(),
                        )
                        if (card.unit != null) {
                            Text(
                                text = card.unit.uppercase(),
                                color = palette.inkMuted,
                                fontFamily = Poster,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .alignByBaseline()
                                    .padding(start = 3.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = card.tag.uppercase(),
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
                    }
                }
            }
        }
    }
}

/**
 * Weekly bars, read like a score: the count is printed on the first bar and wherever it changes
 * from the week before, so a steady run prints its number once.
 */
@Composable
internal fun SessionsPerWeek(core: CycleProgressCore) {
    val palette = LocalGlassPalette.current
    val counts = weeklySessionCounts(core)
    if (counts.isEmpty()) return
    val maxCount = counts.max().coerceAtLeast(1)
    val labelled = remember(counts) { weekLabelIndices(counts) }
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
                    // An unlabelled bar keeps the count's line, drawn clear, so every bar sits on the
                    // same baseline and a screen reader still hears each week's count.
                    Text(
                        text = count.toString(),
                        color = when {
                            index !in labelled -> Color.Transparent
                            count == 0 -> palette.inkSubtle
                            else -> palette.accentStrong
                        },
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
    }
}

/** The rep-range mix as one stacked bar: the album's tracklist. */
@Composable
internal fun SetlistBar(buckets: List<RepBucket>, title: String = "Setlist") {
    val palette = LocalGlassPalette.current
    if (buckets.isEmpty() || buckets.sumOf { it.sets } == 0) return
    val dark = Color(0xFF0B1715)
    fun segColor(index: Int): Color = lerp(palette.accentStrong, dark, index * 0.24f)

    Column(modifier = Modifier.fillMaxWidth()) {
        PosterHeading(title)
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
            buckets.forEachIndexed { index, bucket ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(9.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(segColor(index)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${bucket.label} ${repRange(bucket)} · " +
                            "${countLabel(bucket.sets, "set")} · ${bucket.pctOfSets.roundToInt()}%",
                        color = palette.inkMuted,
                        fontSize = 10.5.sp,
                        fontFamily = Mono,
                        maxLines = 2,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

private fun repRange(bucket: RepBucket): String =
    if (bucket.maxReps == Int.MAX_VALUE) "${bucket.minReps}+" else "${bucket.minReps}–${bucket.maxReps}"
