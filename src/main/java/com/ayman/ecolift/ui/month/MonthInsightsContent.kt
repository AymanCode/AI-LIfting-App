package com.ayman.ecolift.ui.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.progress.EquivalenceUnits
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.ui.viewmodel.*
import com.ayman.ecolift.web.util.groupThousands
import kotlin.math.max
import kotlin.math.roundToLong

private val storySerif get() = TextStyle(
    fontFamily = SheetSerifFontFamily,
    fontWeight = FontWeight.Light,
    fontSize = 23.sp,
    lineHeight = 28.sp,
    color = SheetInk,
)

@Composable
internal fun MonthEntranceContent(
    state: MonthSheetUiState,
    onStep: (Int) -> Unit,
    onOpenLift: (Long) -> Unit,
) {
    val entrance = state.entrance ?: return
    val rollup = state.rollup

    // Masthead: month name in Fraunces with year small beside it, ‹ ›
    // no "MONTH YEAR" kicker, no ", done"
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = entrance.titleMonth,
                style = storySerif.copy(fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = entrance.year.toString(),
                style = TextStyle(
                    fontFamily = SheetSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    color = SheetDim,
                ),
                modifier = Modifier
                    .padding(start = 6.dp, top = 4.dp)
                    .semantics { contentDescription = entrance.year.toString() },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Text(
                text = "‹",
                style = TextStyle(
                    fontFamily = SheetSansFontFamily,
                    fontSize = 20.sp,
                    color = if (state.canGoEarlier) SheetInk else SheetFaint,
                ),
                modifier = Modifier
                    .clickable(enabled = state.canGoEarlier) { onStep(-1) }
                    .semantics { contentDescription = "Previous month" },
            )
            Text(
                text = "›",
                style = TextStyle(
                    fontFamily = SheetSansFontFamily,
                    fontSize = 20.sp,
                    color = if (state.canGoLater) SheetInk else SheetFaint,
                ),
                modifier = Modifier
                    .clickable(enabled = state.canGoLater) { onStep(1) }
                    .semantics { contentDescription = "Next month" },
            )
        }
    }

    // Numbers band: SESSIONS / SETS / LB, then chip ≈ 315 VENDING MACHINES under LB
    val sessionsCount = rollup?.sessions ?: entrance.sessions
    val setsCount = rollup?.sets ?: entrance.workingSets
    val totalLoadLbs = rollup?.totalLoadLbs ?: 0.0

    val sessionsText = sessionsCount.toString()
    val setsText = setsCount.toString()
    val lbText = groupThousands(totalLoadLbs.roundToLong())

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
    ) {
        val totalWidthPx = constraints.maxWidth
        val col1WidthPx = with(density) { 72.dp.roundToPx() }
        val div1WidthPx = with(density) { 1.dp.roundToPx() }
        val col2WidthPx = with(density) { 92.dp.roundToPx() }
        val col2PadStartPx = with(density) { 14.dp.roundToPx() }
        val div2WidthPx = with(density) { 1.dp.roundToPx() }
        val col3PadStartPx = with(density) { 14.dp.roundToPx() }

        val col1AvailPx = col1WidthPx
        val col2AvailPx = (col2WidthPx - col2PadStartPx).coerceAtLeast(1)
        val col3AvailPx = (totalWidthPx - col1WidthPx - div1WidthPx - col2WidthPx - div2WidthPx - col3PadStartPx).coerceAtLeast(1)

        val numeralSp = remember(sessionsText, setsText, lbText, totalWidthPx) {
            computeNumbersBandNumeralSp(
                measurer = measurer,
                sessionsText = sessionsText,
                setsText = setsText,
                lbText = lbText,
                col1AvailPx = col1AvailPx,
                col2AvailPx = col2AvailPx,
                col3AvailPx = col3AvailPx,
                density = density,
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.width(72.dp).semantics(mergeDescendants = true) { contentDescription = "$sessionsText SESSIONS" }) {
                Text(
                    text = sessionsText,
                    style = storySerif.copy(fontSize = numeralSp.sp, lineHeight = numeralSp.sp),
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "SESSIONS",
                    style = TextStyle(
                        fontFamily = SheetSansFontFamily,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.8.sp,
                        color = SheetDim,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(top = 7.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Box(modifier = Modifier.width(1.dp).height(55.dp).background(SheetHair))
            Column(modifier = Modifier.width(92.dp).padding(start = 14.dp).semantics(mergeDescendants = true) { contentDescription = "$setsText SETS" }) {
                Text(
                    text = setsText,
                    style = storySerif.copy(fontSize = numeralSp.sp, lineHeight = numeralSp.sp),
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "SETS",
                    style = TextStyle(
                        fontFamily = SheetSansFontFamily,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.8.sp,
                        color = SheetDim,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(top = 7.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Box(modifier = Modifier.width(1.dp).height(55.dp).background(SheetHair))
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp).semantics(mergeDescendants = true) { contentDescription = "$lbText LB" }) {
                Text(
                    text = lbText,
                    style = storySerif.copy(fontSize = numeralSp.sp, lineHeight = numeralSp.sp),
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "LB",
                    style = TextStyle(
                        fontFamily = SheetSansFontFamily,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.8.sp,
                        color = SheetDim,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(top = 7.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    // Equivalence chip under LB
    val eqCount = EquivalenceUnits.countFor(totalLoadLbs, state.unit)
    if (eqCount > 0) {
        val unitName = if (eqCount == 1) state.unit.singular.uppercase() else state.unit.plural.uppercase()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            GhostChip(
                annotatedText = buildAnnotatedString {
                    append("≈ ")
                    withStyle(SpanStyle(color = SheetInk, fontWeight = FontWeight.SemiBold)) {
                        append(groupThousands(eqCount))
                    }
                    append(" $unitName")
                },
            )
        }
    }

    // Story section (only when entrance.lead != null)
    entrance.lead?.let { lead ->
        HorizontalDivider(modifier = Modifier.padding(top = 22.dp, bottom = 18.dp), color = SheetHair)
        MonthStoryLead(lead, onOpenLift)

        if (entrance.also != null || entrance.earlyHint != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                entrance.also?.let { also ->
                    Column(modifier = Modifier.weight(1f)) {
                        MonthStoryMini(also, onOpenLift)
                    }
                }
                if (entrance.also != null && entrance.earlyHint != null) {
                    Box(modifier = Modifier.width(1.dp).height(140.dp).background(SheetHair))
                }
                entrance.earlyHint?.let { mini2 ->
                    Column(modifier = Modifier.weight(1f)) {
                        MonthStoryMini(mini2, onOpenLift)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStoryLead(story: MonthLiftStory, onOpenLift: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenLift(story.exerciseId) }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "${story.name}: ▲ ${story.win.label}. Open ${story.name} report"
                onClick("Open ${story.name} report") { onOpenLift(story.exerciseId); true }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = story.name,
                style = storySerif.copy(fontSize = 27.sp, lineHeight = 29.sp, letterSpacing = (-0.2).sp),
                modifier = Modifier.weight(1f).padding(end = 10.dp),
            )
            WinChip(story.win, modifier = Modifier.padding(top = 4.dp))
        }

        StoryLineChart(
            story = story,
            color = SheetInk,
            height = 128,
            pad = 22f,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )

        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val totalWidthPx = constraints.maxWidth
            val n = story.points.size.coerceAtLeast(1)
            val colWidthPx = totalWidthPx.toFloat() / n

            fun labelWidthPx(text: String, sp: Float): Float {
                val measured = measurer.measure(
                    text = text,
                    style = TextStyle(
                        fontFamily = SheetSansFontFamily,
                        fontSize = sp.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    softWrap = false,
                    maxLines = 1,
                ).size.width.toFloat()
                val estDp = estimateSetLabelWidthDp(text, sp)
                val estPx = with(density) { estDp.dp.toPx() }
                return max(measured, estPx)
            }

            val fits11sp = remember(story.points, totalWidthPx) {
                story.points.all { pt -> labelWidthPx(pt.setLabel, 11f) <= colWidthPx }
            }
            val fits10sp = remember(story.points, totalWidthPx) {
                fits11sp || story.points.all { pt -> labelWidthPx(pt.setLabel, 10f) <= colWidthPx }
            }
            val tickFontSize = if (fits11sp) 11.sp else 10.sp

            // Ticks row: anchor first, then session ticks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                story.points.forEach { point ->
                    val showSetLabel = fits10sp || point.isAnchor || (point == story.points.last())
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = point.dateLabel,
                            style = TextStyle(
                                fontFamily = SheetSansFontFamily,
                                fontSize = 9.sp,
                                letterSpacing = 1.3.sp,
                                color = if (point.isAnchor) Color(0xFF5F5B54) else SheetFaint,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (showSetLabel) {
                            Text(
                                text = point.setLabel,
                                style = TextStyle(
                                    fontFamily = SheetSansFontFamily,
                                    fontSize = tickFontSize,
                                    fontWeight = if (point.isAnchor) FontWeight.Normal else FontWeight.SemiBold,
                                    color = if (point.isAnchor) SheetDim else SheetInk,
                                ),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStoryMini(story: MonthLiftStory, onOpenLift: (Long) -> Unit) {
    val accent = leadRegionColor(story.region)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenLift(story.exerciseId) }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "${story.name}: ▲ ${story.win.label}. Open ${story.name} report"
                onClick("Open ${story.name} report") { onOpenLift(story.exerciseId); true }
            },
    ) {
        Text(
            text = story.name,
            style = storySerif.copy(fontSize = 18.sp, lineHeight = 20.sp),
            minLines = 2,
            maxLines = 2,
        )
        StoryLineChart(
            story = story,
            color = accent,
            height = 62,
            pad = 8f,
            r = 3.5f,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            val measurer = rememberTextMeasurer()
            val availablePx = constraints.maxWidth
            val fromToText = "${story.fromLabel} → ${story.toLabel}"
            val fromToSp = remember(fromToText, availablePx) {
                var sp = 12f
                while (sp > 9f) {
                    val w = measurer.measure(
                        text = fromToText,
                        style = TextStyle(fontFamily = SheetSansFontFamily, fontSize = sp.sp, fontWeight = FontWeight.SemiBold),
                        softWrap = false,
                        maxLines = 1,
                    ).size.width
                    if (w <= availablePx) break
                    sp -= 0.5f
                }
                sp.sp
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = SheetDim, fontWeight = FontWeight.Medium)) {
                        append(story.fromLabel)
                    }
                    withStyle(SpanStyle(color = SheetFaint, fontWeight = FontWeight.Normal)) {
                        append(" → ")
                    }
                    withStyle(SpanStyle(color = SheetInk, fontWeight = FontWeight.SemiBold)) {
                        append(story.toLabel)
                    }
                },
                style = TextStyle(fontFamily = SheetSansFontFamily, fontSize = fromToSp),
                maxLines = 1,
                softWrap = false,
            )
        }
        WinChip(story.win, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun StoryLineChart(
    story: MonthLiftStory,
    color: Color,
    height: Int,
    pad: Float = 8f,
    r: Float = 4.5f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(height.dp).drawWithCache {
        val w = size.width
        val h = size.height
        val scores = story.points.map { topSetScore(it.top) }
        val lo = scores.minOrNull() ?: 0.0
        val hi = scores.maxOrNull() ?: 1.0
        val span = if (hi > lo) hi - lo else 1.0

        val xStep = if (scores.size > 1) (w - pad * 2f) / (scores.size - 1) else 0f
        val points = scores.mapIndexed { idx, sc ->
            val px = pad + idx * xStep
            val py = h - pad - ((sc - lo) / span * (h - pad * 2f - 6f)).toFloat()
            Offset(px, py)
        }

        val anchorY = points.first().y
        val sessionPoints = points.drop(1)

        val fillPath = Path().apply {
            if (sessionPoints.isNotEmpty()) {
                moveTo(sessionPoints.first().x, sessionPoints.first().y)
                sessionPoints.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(sessionPoints.last().x, h)
                lineTo(sessionPoints.first().x, h)
                close()
            }
        }

        val linePath = Path().apply {
            if (sessionPoints.isNotEmpty()) {
                moveTo(sessionPoints.first().x, sessionPoints.first().y)
                sessionPoints.drop(1).forEach { lineTo(it.x, it.y) }
            }
        }

        val anchorDottedPath = Path().apply {
            if (points.size >= 2) {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
            }
        }

        val stroke = Stroke(2.dp.toPx())
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        val guidelineEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))

        onDrawBehind {
            // Horizontal dashed guideline at anchor height
            drawLine(
                color = Color(0xFF3A3833),
                start = Offset(0f, anchorY),
                end = Offset(w, anchorY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = guidelineEffect,
            )

            // Dashed segment from anchor to point 1
            drawPath(anchorDottedPath, color = color.copy(alpha = 0.45f), style = Stroke(1.6.dp.toPx(), pathEffect = dashEffect))

            // Fill gradient below sessions line
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
                    startY = 0f,
                    endY = h,
                ),
            )

            // Sessions line
            drawPath(linePath, color = color, style = stroke)

            // Dots
            points.forEachIndexed { idx, p ->
                val isAnchor = idx == 0
                val isLast = idx == points.lastIndex
                val dotR = r.dp.toPx()
                val dotFill = if (isLast) color else Color(0xFF0E0E0C)
                val dotStroke = if (isAnchor) Color(0xFF6B675F) else color
                drawCircle(color = dotFill, radius = dotR, center = p)
                drawCircle(color = dotStroke, radius = dotR, center = p, style = Stroke(2.dp.toPx()))
            }
        }
    }) {}
}

@Composable
internal fun MonthCompareContent(comparison: MonthComparison) {
    var expandedOnlyBefore by rememberSaveable { mutableStateOf(false) }
    var expandedOnlyAfter by rememberSaveable { mutableStateOf(false) }
    var showAllRows by rememberSaveable { mutableStateOf(false) }

    val fromAbbrev = comparison.fromMonth.take(3).uppercase()
    val toAbbrev = comparison.toMonth.take(3).uppercase()

    // Kicker: VS <FROM_MONTH>  trailing: N LIFTS IN BOTH
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(2.dp).height(12.dp).background(SheetAcc))
            Spacer(Modifier.width(9.dp))
            Text(
                text = "VS ${comparison.fromMonth.uppercase()}",
                style = TextStyle(
                    fontFamily = SheetSansFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 2.2.sp,
                    color = SheetDim,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = SheetInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)) {
                    append("${comparison.allCount} ")
                }
                append(if (comparison.allCount == 1) "LIFT IN BOTH" else "LIFTS IN BOTH")
            },
            style = TextStyle(
                fontFamily = SheetSansFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = SheetDim,
            ),
        )
    }

    // Column heads: AUG and SEP
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(14.dp))
        Text(
            text = fromAbbrev,
            style = TextStyle(
                fontFamily = SheetSansFontFamily,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                color = SheetFaint,
            ),
            modifier = Modifier.width(88.dp),
        )
        Spacer(Modifier.width(24.dp))
        Text(
            text = toAbbrev,
            style = TextStyle(
                fontFamily = SheetSansFontFamily,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                color = SheetFaint,
            ),
            modifier = Modifier.width(88.dp),
        )
    }
    HorizontalDivider(color = SheetRowLine)

    val rowsToShow = if (showAllRows) comparison.rows else comparison.rows.take(6)
    rowsToShow.forEach { row ->
        CompareRow(row)
    }

    // Bottom chips: ALL ›, ONLY <FROM> n ›, ONLY <TO> n ›
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GhostChip(
            annotatedText = buildAnnotatedString { append("ALL ›") },
            onClick = { showAllRows = !showAllRows },
        )
        GhostChip(
            annotatedText = buildAnnotatedString {
                append("ONLY $fromAbbrev ")
                withStyle(SpanStyle(color = SheetInk, fontWeight = FontWeight.SemiBold)) {
                    append("${comparison.onlyBefore.count}")
                }
                append(" ›")
            },
            onClick = { expandedOnlyBefore = !expandedOnlyBefore },
        )
        GhostChip(
            annotatedText = buildAnnotatedString {
                append("ONLY $toAbbrev ")
                withStyle(SpanStyle(color = SheetInk, fontWeight = FontWeight.SemiBold)) {
                    append("${comparison.onlyAfter.count}")
                }
                append(" ›")
            },
            onClick = { expandedOnlyAfter = !expandedOnlyAfter },
        )
    }

    if (expandedOnlyBefore) {
        OnlyInLifts(comparison.onlyBefore, Modifier.padding(top = 10.dp))
    }
    if (expandedOnlyAfter) {
        OnlyInLifts(comparison.onlyAfter, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun CompareRow(row: MonthCompareRow) {
    val muscleColor = leadRegionColor(row.region)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${row.name}: ${row.fromLabel} → ${row.toLabel}, sessions ${row.sessionsLabel}" +
                    (row.win?.let { ", ▲ ${it.label}" } ?: "")
            },
    ) {
        // Line 1: color dot, lift name, ▲ chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(muscleColor, RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = row.name,
                    style = TextStyle(
                        fontFamily = SheetSansFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SheetInk,
                    ),
                )
            }
            row.win?.let { win ->
                WinChip(win)
            }
        }

        // Line 2: from set + dots -> to set + dots
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val colWidthPx = with(density) { 88.dp.roundToPx() }

            val fromSp = remember(row.fromLabel, colWidthPx) {
                fitSingleLabelSp(measurer, row.fromLabel, 13f, 10f, colWidthPx)
            }
            val toSp = remember(row.toLabel, colWidthPx) {
                fitSingleLabelSp(measurer, row.toLabel, 13f, 10f, colWidthPx)
            }

            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.width(88.dp)) {
                Text(
                    text = row.fromLabel,
                    style = TextStyle(
                        fontFamily = SheetSansFontFamily,
                        fontSize = fromSp.sp,
                        fontWeight = FontWeight.Medium,
                        color = SheetDim,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
                SessionDots(count = row.fromSessions, color = SheetFaint)
            }
            Text(
                text = "→",
                style = TextStyle(
                    fontFamily = SheetSansFontFamily,
                    fontSize = 12.sp,
                    color = SheetFaint,
                ),
                modifier = Modifier.width(24.dp),
                maxLines = 1,
                softWrap = false,
            )
            Column(modifier = Modifier.width(88.dp)) {
                Text(
                    text = row.toLabel,
                    style = TextStyle(
                        fontFamily = SheetSansFontFamily,
                        fontSize = toSp.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SheetInk,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
                SessionDots(count = row.toSessions, color = SheetInk)
            }
        }
    }
    HorizontalDivider(color = SheetRowLine)
}

@Composable
private fun SessionDots(count: Int, color: Color) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlyInLifts(only: MonthOnlyLifts, modifier: Modifier = Modifier) {
    if (only.count == 0) return
    val shown = only.names.take(4)
    val description = "Only in ${only.month}: ${only.count} lift${if (only.count == 1) "" else "s"}, " +
        only.names.take(3).joinToString(", ") + if (only.count > 3) ", and ${only.count - 3} more" else ""
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Text("ONLY IN ${only.month.uppercase()}", style = EspressoType.eyebrow, color = SheetDim)
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            shown.forEach { name -> OnlyInChip(name) }
            if (only.count > shown.size) OnlyInChip("+${only.count - shown.size}")
        }
    }
}

@Composable
private fun OnlyInChip(text: String) {
    Text(
        text = text,
        style = EspressoType.chipBold.copy(fontSize = 10.5.sp, lineHeight = 12.sp),
        color = SheetInk,
        maxLines = 1,
        modifier = Modifier.background(SheetHair, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 5.dp),
    )
}

internal fun estimateNumberWidthDp(text: String, sp: Float): Float {
    return text.sumOf { ch ->
        when (ch) {
            ',' -> 5.0
            else -> 16.0
        }
    }.toFloat() * (sp / 40f)
}

internal fun computeNumbersBandNumeralSpEst(
    sessionsText: String,
    setsText: String,
    lbText: String,
    col1AvailDp: Float,
    col2AvailDp: Float,
    col3AvailDp: Float,
): Float {
    fun textFits(text: String, sp: Float, availDp: Float): Boolean {
        return estimateNumberWidthDp(text, sp) <= availDp
    }
    var sp = 40f
    while (sp > 12f) {
        if (textFits(sessionsText, sp, col1AvailDp) &&
            textFits(setsText, sp, col2AvailDp) &&
            textFits(lbText, sp, col3AvailDp)
        ) {
            return sp
        }
        sp -= 0.5f
    }
    return sp
}

internal fun computeNumbersBandNumeralSp(
    measurer: TextMeasurer,
    sessionsText: String,
    setsText: String,
    lbText: String,
    col1AvailPx: Int,
    col2AvailPx: Int,
    col3AvailPx: Int,
    density: Density,
): Float {
    fun textFits(text: String, sp: Float, availPx: Int): Boolean {
        val measuredPx = measurer.measure(
            text = text,
            style = storySerif.copy(fontSize = sp.sp),
            softWrap = false,
            maxLines = 1,
        ).size.width
        val estDp = estimateNumberWidthDp(text, sp)
        val estPx = with(density) { estDp.dp.roundToPx() }
        val widthPx = max(measuredPx, estPx)
        return widthPx <= availPx
    }

    var sp = 40f
    while (sp > 12f) {
        if (textFits(sessionsText, sp, col1AvailPx) &&
            textFits(setsText, sp, col2AvailPx) &&
            textFits(lbText, sp, col3AvailPx)
        ) {
            return sp
        }
        sp -= 0.5f
    }
    return sp
}

internal fun estimateSetLabelWidthDp(text: String, sp: Float): Float {
    val scale = sp / 11f
    return text.sumOf { ch ->
        when (ch) {
            'W', 'M' -> 11.0
            'B', 'R', 'O', 'Q', 'G', 'C', 'D', '#', '%', '+' -> 7.7
            '.', ',', ' ', ':' -> 3.8
            '1', 'I', 'l', 'i' -> 5.0
            else -> 6.8
        }
    }.toFloat() * scale
}

internal fun shouldShowLeadStorySetLabel(
    point: MonthPathPoint,
    allPoints: List<MonthPathPoint>,
    availableWidthDp: Float,
): Boolean {
    val n = allPoints.size.coerceAtLeast(1)
    val colWidthDp = availableWidthDp / n
    val allFit10sp = allPoints.all { pt ->
        estimateSetLabelWidthDp(pt.setLabel, 10f) <= colWidthDp
    }
    return if (allFit10sp) {
        true
    } else {
        point.isAnchor || point == allPoints.lastOrNull()
    }
}

internal fun fitSingleLabelSp(
    measurer: TextMeasurer,
    text: String,
    startSp: Float,
    minSp: Float,
    availablePx: Int,
): Float {
    var sp = startSp
    while (sp > minSp) {
        val w = measurer.measure(
            text = text,
            style = TextStyle(fontFamily = SheetSansFontFamily, fontSize = sp.sp, fontWeight = FontWeight.SemiBold),
            softWrap = false,
            maxLines = 1,
        ).size.width
        if (w <= availablePx) return sp
        sp -= 0.5f
    }
    return minSp
}
