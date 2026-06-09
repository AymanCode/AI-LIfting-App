package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.data.progress.CycleProgressCore
import com.ayman.ecolift.data.progress.LiftComparison
import com.ayman.ecolift.data.progress.LiftTrend
import com.ayman.ecolift.data.progress.Movement
import com.ayman.ecolift.data.progress.RepBucket
import com.ayman.ecolift.data.progress.ScoreBreakdown
import com.ayman.ecolift.ui.theme.HoldAmber
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.glassPanel
import kotlin.math.abs
import kotlin.math.roundToInt

private val Mono = LogUiFontFamily

internal fun movementColor(movement: Movement, palette: GlassPalette): Color = when (movement) {
    Movement.IMPROVED -> palette.complete
    Movement.REGRESSED -> palette.danger
    Movement.HELD -> HoldAmber
}

internal fun formatPct(value: Float?): String =
    if (value == null) "N/A" else "${if (value >= 0f) "+" else ""}${value.roundToInt()}%"

internal fun verdictWord(score: Int): String = when {
    score >= 80 -> "Excellent cycle"
    score >= 62 -> "Strong cycle"
    score >= 45 -> "Steady progress"
    score >= 30 -> "Maintaining"
    else -> "Down cycle"
}

internal fun verdictBlurb(score: Int, window: ComparisonWindow): String {
    val w = window.label()
    return when {
        score >= 62 -> "You out-paced your previous $w on most split lifts."
        score >= 45 -> "Most split lifts improved versus your previous $w."
        score >= 30 -> "You largely held your ground versus your previous $w."
        else -> "Most lifts slipped versus your previous $w, worth a look."
    }
}

/** Gauge/verdict tint keyed to the composite score, tuned for the dark hero card. */
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

@Composable
internal fun WindowToggle(
    selected: ComparisonWindow,
    onSelect: (ComparisonWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.glassFill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ComparisonWindow.entries.forEach { window ->
            val on = window == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) palette.accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(window) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = window.label(),
                    color = if (on) palette.accentStrong else palette.inkMuted,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ArchiveSectionTitle(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = palette.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = palette.inkSubtle,
                fontSize = 11.sp,
                fontFamily = Mono,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ArchiveRule(modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = palette.glassStroke.copy(alpha = 0.54f),
    )
}

@Composable
internal fun CompositeHeroCard(
    score: ScoreBreakdown,
    window: ComparisonWindow,
    onAdjustWeights: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    val tint = verdictColor(score.composite, palette)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(palette, shape, strong = true),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tint),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Cycle progress",
                    color = palette.inkSubtle,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    letterSpacing = 0.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        verdictWord(score.composite),
                        color = palette.ink,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        verdictBlurb(score.composite, window),
                        color = palette.inkMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                ArcGauge(
                    value = score.composite,
                    color = tint,
                    modifier = Modifier.size(128.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            SubScoreBar("Progression", score.progression, tint)
            Spacer(Modifier.height(10.dp))
            SubScoreBar("Momentum", score.momentum, tint)
            Spacer(Modifier.height(10.dp))
            SubScoreBar("Consistency", score.consistency, tint)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Split exercises only",
                    color = palette.inkSubtle,
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Adjust weights",
                    color = palette.accentStrong,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAdjustWeights)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun NoComparisonHeroCard(
    comparison: CycleComparison,
    window: ComparisonWindow,
) {
    val palette = LocalGlassPalette.current
    val newLifts = comparison.lifts.count { it.isNew }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(palette, shape, strong = true),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Cycle progress",
                color = palette.inkSubtle,
                fontSize = 12.sp,
                fontFamily = Mono,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No baseline yet",
                color = palette.ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your split lifts have no logged history in the ${window.label()} before this cycle, " +
                    "so there's nothing to score against yet. Try a longer window above, " +
                    "or log through another cycle to unlock your progress score.",
                color = palette.inkMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            if (newLifts > 0) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "$newLifts ${if (newLifts == 1) "lift is" else "lifts are"} new vs your previous ${window.label()}",
                    color = palette.accentStrong,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
            }
        }
    }
}

@Composable
private fun ArcGauge(value: Int, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(0, 100) / 100f,
        animationSpec = tween(900),
        label = "gauge",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        // Soft radial glow behind the readout, tinted to the verdict.
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(100))
                .background(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.16f), Color.Transparent),
                    ),
                ),
        )
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Track.
            drawArc(
                color = palette.glassFillStrong,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Quartile tick marks on the track.
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rOuter = (size.width - inset) / 2f
            val rInner = rOuter - stroke * 0.55f
            for (q in 0..3) {
                val ang = Math.toRadians((-90f + q * 90f).toDouble())
                val cos = kotlin.math.cos(ang).toFloat()
                val sin = kotlin.math.sin(ang).toFloat()
                drawLine(
                    color = palette.glassFill,
                    start = Offset(cx + cos * rInner, cy + sin * rInner),
                    end = Offset(cx + cos * rOuter, cy + sin * rOuter),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Progress sweep with a vertical gradient for depth.
            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(color, color.copy(alpha = 0.55f)),
                ),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value.toString(),
                color = palette.ink,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "/ 100",
                color = color,
                fontSize = 10.sp,
                fontFamily = Mono,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun SubScoreBar(label: String, value: Float, tint: Color? = null) {
    val palette = LocalGlassPalette.current
    val barTint = tint ?: palette.accentStrong
    val animated by animateFloatAsState(
        targetValue = (value / 100f).coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "sub-$label",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = palette.inkSubtle,
            fontSize = 11.sp,
            fontFamily = Mono,
            letterSpacing = 0.sp,
            modifier = Modifier.width(108.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.glassFillStrong),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barTint),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value.roundToInt().toString(),
            color = palette.ink,
            fontSize = 15.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun StoryChips(core: CycleProgressCore, comparison: CycleComparison) {
    val palette = LocalGlassPalette.current
    val ranked = comparison.lifts.filter { !it.isNew && it.vsPct != null }
        .sortedByDescending { it.vsPct }
    val biggest = ranked.firstOrNull()
    val smallest = ranked.lastOrNull()
    val frequent = comparison.lifts.maxByOrNull { it.totalSets }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchiveSectionTitle("Cycle snapshot", "vs previous ${comparison.window.label()}")
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            StoryStat(
                "Biggest gain",
                formatPct(biggest?.vsPct),
                biggest?.name ?: "N/A",
                movementColor(biggest?.movement ?: Movement.HELD, palette),
                Modifier.weight(1f),
            )
            StoryStat(
                "Smallest gain",
                formatPct(smallest?.vsPct),
                smallest?.name ?: "N/A",
                movementColor(smallest?.movement ?: Movement.HELD, palette),
                Modifier.weight(1f),
            )
        }
        ArchiveRule()
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            StoryStat(
                "Most frequent",
                "${frequent?.totalSets ?: 0}",
                "sets · ${frequent?.name ?: "N/A"}",
                palette.ink,
                Modifier.weight(1f),
            )
            StoryStat(
                "Lifts improved",
                "${comparison.improvedCount}/${comparison.comparedCount}",
                "vs ${comparison.window.label()} ago",
                palette.complete,
                Modifier.weight(1f),
            )
        }
        ArchiveRule()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StoryStat(
                "Sessions",
                "${core.sessions}",
                "${"%.1f".format(core.sessionsPerWeek)}/wk",
                palette.ink,
                Modifier.weight(1f),
            )
            Text(
                text = "Training density across this archived span",
                color = palette.inkSubtle,
                fontSize = 11.sp,
                fontFamily = Mono,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StoryStat(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Column(modifier = modifier.padding(vertical = 2.dp)) {
        Text(label, color = palette.inkSubtle, fontSize = 10.sp, fontFamily = Mono, letterSpacing = 0.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(sub, color = palette.inkMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun OutcomeBar(comparison: CycleComparison) {
    val palette = LocalGlassPalette.current
    val total = comparison.comparedCount.coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchiveSectionTitle("Movement mix", "${comparison.comparedCount} compared lifts")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(palette.glassFillStrong),
        ) {
            OutcomeSeg(comparison.improvedCount, total, palette.complete)
            OutcomeSeg(comparison.heldCount, total, HoldAmber)
            OutcomeSeg(comparison.regressedCount, total, palette.danger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            OutcomeLegend("Improved", comparison.improvedCount, palette.complete)
            OutcomeLegend("Held", comparison.heldCount, HoldAmber)
            OutcomeLegend("Regressed", comparison.regressedCount, palette.danger)
        }
    }
}

@Composable
private fun RowScope.OutcomeSeg(count: Int, total: Int, color: Color) {
    if (count <= 0) return
    Box(
        modifier = Modifier
            .weight(count.toFloat() / total)
            .fillMaxHeight()
            .background(color),
    )
}

@Composable
private fun OutcomeLegend(label: String, count: Int, color: Color) {
    val palette = LocalGlassPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text("$label · $count", color = palette.inkMuted, fontSize = 11.sp, fontFamily = Mono)
    }
}

@Composable
internal fun GainLadder(comparison: CycleComparison) {
    val palette = LocalGlassPalette.current
    val rows = comparison.lifts.filter { !it.isNew && it.vsPct != null }
        .sortedByDescending { it.vsPct }
    if (rows.isEmpty()) return
    val maxAbs = rows.maxOf { abs(it.vsPct ?: 0f) }.coerceAtLeast(1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchiveSectionTitle("Ranked gains", "center line = 0%")
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Spacer(Modifier.width(116.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("0", color = palette.inkSubtle, fontSize = 9.sp, fontFamily = Mono)
                }
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.width(42.dp))
            }
            rows.forEachIndexed { index, lift ->
                GainLadderRow(lift, maxAbs)
                if (index != rows.lastIndex) {
                    ArchiveRule(Modifier.padding(top = 9.dp, bottom = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun GainLadderRow(lift: LiftComparison, maxAbs: Float) {
    val palette = LocalGlassPalette.current
    val pct = lift.vsPct ?: 0f
    val color = movementColor(lift.movement, palette)
    val positive = pct >= 0f
    val fraction = (abs(pct) / maxAbs).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.width(116.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lift.name,
                color = palette.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (lift.isBodyweight) {
                Spacer(Modifier.width(4.dp))
                BwTag()
            }
        }
        Spacer(Modifier.width(10.dp))
        // Diverging bar: zero is the center; positive grows right, negative grows left.
        Box(Modifier.weight(1f).height(11.dp)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(palette.glassStroke),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                    if (!positive) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(9.dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                                .background(color),
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (positive) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(9.dp)
                                .clip(RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                                .background(color),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatPct(pct),
            color = color,
            fontSize = 11.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun BwTag() {
    val palette = LocalGlassPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.accent.copy(alpha = 0.16f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text("BW", color = palette.accentStrong, fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun RepDistribution(buckets: List<RepBucket>) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchiveSectionTitle("Rep range mix", "${buckets.sumOf { it.sets }} sets")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            buckets.forEachIndexed { index, bucket ->
                RepBucketRow(bucket)
                if (index != buckets.lastIndex) {
                    ArchiveRule()
                }
            }
        }
    }
}

@Composable
private fun RepBucketRow(bucket: RepBucket) {
    val palette = LocalGlassPalette.current
    val range = if (bucket.maxReps == Int.MAX_VALUE) {
        "${bucket.minReps}+ reps"
    } else {
        "${bucket.minReps}–${bucket.maxReps} reps"
    }
    val animated by animateFloatAsState(
        targetValue = (bucket.pctOfSets / 100f).coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "rep-${bucket.label}",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(108.dp)) {
            Text(bucket.label, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(range, color = palette.inkSubtle, fontSize = 11.sp, fontFamily = Mono)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(palette.glassFillStrong),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(palette.accentStrong),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${bucket.sets} · ${bucket.pctOfSets.roundToInt()}%",
            color = palette.inkMuted,
            fontSize = 11.sp,
            fontFamily = Mono,
            modifier = Modifier.width(70.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun ConsistencyHeatmap(core: CycleProgressCore) {
    val palette = LocalGlassPalette.current
    val sessionDates = core.sessionDates.toSet()
    val start = runCatching { java.time.LocalDate.parse(core.startDate) }.getOrNull()
    val end = runCatching { java.time.LocalDate.parse(core.endDate) }.getOrNull()
    if (start == null || end == null) return
    // Align grid to week start (Sunday). Build leading blanks for the first week.
    val leadBlanks = ((start.dayOfWeek.value) % 7)
    val totalDays = (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(0)
    val cells = buildList<java.time.LocalDate?> {
        repeat(leadBlanks) { add(null) }
        for (i in 0 until totalDays) add(start.plusDays(i.toLong()))
    }
    val weeks = cells.chunked(7)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchiveSectionTitle(
            title = "Training rhythm",
            trailing = "${core.sessions} sessions · ${"%.1f".format(core.sessionsPerWeek)}/wk",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(
                    d,
                    color = palette.inkSubtle,
                    fontSize = 9.sp,
                    fontFamily = Mono,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        val on = day != null && day.toString() in sessionDates
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (on) palette.accentStrong else palette.glassFillStrong),
                        )
                    }
                }
            }
        }
    }
}

internal enum class TrendSort(val label: String) { VELOCITY("velocity"), GAIN("gain"), AZ("A-Z") }

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchiveSectionTitle("Lift index", "tap for detail")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Sort", color = palette.inkSubtle, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 0.sp)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(palette.glassFill).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TrendSort.entries.forEach { option ->
                    val on = option == sort
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) palette.accent.copy(alpha = 0.20f) else Color.Transparent)
                            .clickable { onSortChange(option) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            option.label,
                            color = if (on) palette.accentStrong else palette.inkMuted,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        val groups = core.lifts.groupBy { it.splitName }
        if (groups.isEmpty()) {
            Text(
                "No lifts in this archived cycle.",
                color = palette.inkMuted,
                fontSize = 13.sp,
            )
        } else {
            groups.forEach { (splitName, lifts) ->
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "$splitName · ${lifts.size} lifts",
                        color = palette.inkSubtle,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        letterSpacing = 0.sp,
                    )
                    val sorted = when (sort) {
                        TrendSort.VELOCITY -> lifts.sortedByDescending { it.slopePerWeek ?: Float.NEGATIVE_INFINITY }
                        TrendSort.GAIN -> lifts.sortedByDescending {
                            vsByExercise[it.exerciseId] ?: Float.NEGATIVE_INFINITY
                        }
                        TrendSort.AZ -> lifts.sortedBy { it.name.lowercase() }
                    }
                    sorted.forEachIndexed { index, lift ->
                        TrendRow(lift, onClick = { onLiftClick(lift) })
                        if (index != sorted.lastIndex) {
                            ArchiveRule()
                        }
                    }
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
    val slopeText = if (slope == null) {
        "N/A"
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (lift.isBodyweight) {
                    Spacer(Modifier.width(4.dp))
                    BwTag()
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(slopeText, color = slopeColor, fontSize = 12.sp, fontFamily = Mono)
        }
        Spacer(Modifier.width(12.dp))
        MiniSparkline(
            values = lift.points.map { it.value },
            color = slopeColor,
            modifier = Modifier.width(78.dp).height(30.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(44.dp)) {
            Text(
                if (lift.r2 != null) "%.2f".format(lift.r2) else "N/A",
                color = palette.inkMuted,
                fontSize = 11.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "fit",
                color = palette.inkSubtle,
                fontSize = 10.sp,
                fontFamily = Mono,
            )
        }
    }
}
