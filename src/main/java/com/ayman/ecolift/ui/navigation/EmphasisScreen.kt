package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.viewmodel.EmphasisHeadline
import com.ayman.ecolift.ui.viewmodel.EmphasisUiState
import com.ayman.ecolift.ui.viewmodel.RegionWeek
import com.ayman.ecolift.ui.viewmodel.emphasisRegionName
import com.ayman.ecolift.ui.viewmodel.emphasisWeekSpan
import com.ayman.ecolift.data.BodyPartRegion

internal const val EMPHASIS_ROUTE = "progress/emphasis"

// The cream of the ▲ win chips on the Progress posters (`Cream` in ProgressGroupWall.kt).
private val WinCream = Color(0xFFFFE9DE)

@Composable
internal fun EmphasisScreen(state: EmphasisUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(EspressoPalette.bg).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        EmphasisMasthead(state, onBack)
        if (state.weeks.any { it.total > 0 }) {
            EmphasisPanel {
                Text("WORKING SETS PER WEEK", style = EspressoType.eyebrow, color = EspressoPalette.dim)
                Text(state.rangeLabel, style = EspressoType.chipBold, color = EspressoPalette.ink, modifier = Modifier.padding(top = 6.dp))
                StackedWeeks(state)
                RegionLegend(state.regions)
            }
        }
        state.headline?.let { headline ->
            val region = headline.region
            EmphasisPanel {
                Text("${emphasisRegionName(region).uppercase()} · EVERY WEEK", style = EspressoType.eyebrow, color = EspressoPalette.muscle(region))
                RegionHistoryStrip(state, headline)
            }
            EmphasisPanel {
                ByLiftHeader(headline)
                headline.week.lifts[region].orEmpty().forEachIndexed { i, lift ->
                    Column(Modifier.fillMaxWidth().padding(top = if (i == 0) 12.dp else 13.dp).clearAndSetSemantics {
                        contentDescription = "${lift.name}, ${lift.sets} working sets"
                    }) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(lift.name, style = EspressoType.body.copy(fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
                                color = EspressoPalette.ink, maxLines = 2, modifier = Modifier.weight(1f).alignByBaseline())
                            Text("${lift.sets}", style = EspressoType.chipBold.copy(fontSize = 15.sp, lineHeight = 17.sp), color = EspressoPalette.ink,
                                modifier = Modifier.padding(start = 10.dp).alignByBaseline())
                            if (i == 0) Text("SETS", style = EspressoType.eyebrow.copy(fontSize = 9.sp, letterSpacing = 1.4.sp), color = EspressoPalette.faint,
                                modifier = Modifier.padding(start = 4.dp).alignByBaseline())
                        }
                        Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp)) {
                            val radius = CornerRadius(size.height / 2)
                            drawRoundRect(EspressoPalette.hair, cornerRadius = radius)
                            drawRoundRect(EspressoPalette.muscle(region), size = Size(size.width * lift.barFraction, size.height), cornerRadius = radius)
                        }
                    }
                }
            }
        }
    }
}

/** Back pill, kicker and the week's number: the region's set count as the hero, its standing as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmphasisMasthead(state: EmphasisUiState, onBack: () -> Unit) {
    val headline = state.headline
    Column(Modifier.fillMaxWidth().background(EspressoPalette.mast, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp)) {
        // An icon chevron, not a "‹" glyph: the glyph sat on the text baseline, below the label's centre.
        Row(Modifier.clip(RoundedCornerShape(50)).background(EspressoPalette.panel2)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Back to Progress" }
            .clickable(role = Role.Button, onClick = onBack)
            .heightIn(min = 44.dp).padding(start = 10.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = EspressoPalette.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Progress", style = EspressoType.chipBold.copy(fontSize = 13.sp, lineHeight = 15.sp), color = EspressoPalette.ink)
        }
        Text("EMPHASIS", style = EspressoType.kicker, color = EspressoPalette.dim, modifier = Modifier.padding(top = 14.dp))
        if (headline != null) {
            val name = emphasisRegionName(headline.region)
            val color = EspressoPalette.muscle(headline.region)
            Row(Modifier.padding(top = 18.dp).background(color.copy(alpha = .16f), RoundedCornerShape(50))
                .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(7.dp)) { drawCircle(color) }
                Text(name.uppercase(), style = EspressoType.kicker, color = color, modifier = Modifier.padding(start = 6.dp))
            }
            HeroCount(headline.count, color, emphasisWeekSpan(headline.week),
                "$name: ${headline.count} sets in the week of ${headline.week.label}", Modifier.padding(top = 2.dp))
            headline.runnerUp?.let { other ->
                FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (headline.record) {
                        RecordChip()
                        StatChip("PREVIOUS BEST", headline.runnerUpCount, other, "Previous best")
                    } else StatChip("BEST", headline.runnerUpCount, other, "Best")
                }
            }
        } else {
            val last = state.weeks.lastOrNull()
            HeroCount(0, EspressoPalette.faint, last?.let(::emphasisWeekSpan) ?: "LAST FULL WEEK",
                "0 sets${last?.let { " in the week of ${it.label}" } ?: ""}", Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun HeroCount(count: Int, color: Color, span: String, description: String, modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) { heading(); contentDescription = description }) {
        Text("$count", style = EspressoType.chipBold.copy(fontSize = 76.sp, lineHeight = 76.sp, letterSpacing = (-1).sp), color = color,
            modifier = Modifier.alignBy(LastBaseline))
        Column(Modifier.padding(start = 10.dp).alignBy(LastBaseline)) {
            Text("SETS", style = EspressoType.chipBold.copy(fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = .5.sp), color = EspressoPalette.ink)
            Text(span, style = EspressoType.chipBold.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                color = EspressoPalette.dim, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun RecordChip() {
    Row(Modifier.height(28.dp).background(WinCream, RoundedCornerShape(9.dp)).padding(horizontal = 11.dp)
        .semantics(mergeDescendants = true) { contentDescription = "Record week" }, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(9.dp, 8.dp)) {
            drawPath(Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, EspressoPalette.onColor)
        }
        Text("RECORD WEEK", style = EspressoType.chipBold.copy(letterSpacing = .6.sp), color = EspressoPalette.onColor,
            modifier = Modifier.padding(start = 6.dp))
    }
}

/** "PREV BEST 21 · MAY 11": the comparison week as data, never a sentence. */
@Composable
private fun StatChip(word: String, count: Int, week: RegionWeek, spoken: String) {
    Row(Modifier.height(28.dp).background(EspressoPalette.panel2, RoundedCornerShape(9.dp)).padding(horizontal = 11.dp)
        .semantics(mergeDescendants = true) { contentDescription = "$spoken $count, week of ${week.label}" },
        verticalAlignment = Alignment.CenterVertically) {
        Text(word, style = EspressoType.eyebrow.copy(fontSize = 9.5.sp, letterSpacing = 1.6.sp), color = EspressoPalette.dim)
        Text("$count", style = EspressoType.chipBold.copy(fontSize = 13.sp, lineHeight = 15.sp), color = EspressoPalette.ink,
            modifier = Modifier.padding(start = 6.dp))
        Text("· ${week.label.uppercase()}", style = EspressoType.chipBold.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = EspressoPalette.dim, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun ByLiftHeader(headline: EmphasisHeadline) {
    val sessions = headline.week.sessions[headline.region] ?: 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${emphasisRegionName(headline.region).uppercase()} · BY LIFT", style = EspressoType.eyebrow,
            color = EspressoPalette.muscle(headline.region), modifier = Modifier.weight(1f).semantics { heading() })
        Row(Modifier.semantics(mergeDescendants = true) {}) {
            Text("$sessions", style = EspressoType.chipBold.copy(fontSize = 15.sp, lineHeight = 17.sp), color = EspressoPalette.ink,
                modifier = Modifier.alignByBaseline())
            Text(if (sessions == 1) "SESSION" else "SESSIONS", style = EspressoType.eyebrow.copy(fontSize = 9.5.sp, letterSpacing = 1.6.sp),
                color = EspressoPalette.dim, modifier = Modifier.padding(start = 5.dp).alignByBaseline())
        }
    }
}

@Composable
private fun EmphasisPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth()
        .background(EspressoPalette.panel, RoundedCornerShape(16.dp)).padding(13.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionLegend(regions: List<BodyPartRegion>) {
    FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        regions.forEach { region ->
            val color = EspressoPalette.muscle(region)
            Row(Modifier.height(24.dp).background(color.copy(alpha = .15f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(6.dp)) { drawCircle(color) }
                Text(emphasisRegionName(region), style = EspressoType.chip, color = EspressoPalette.ink, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

/** Every bar carries its total and its segments their counts, so the chart has no y-axis. */
@Composable
private fun StackedWeeks(state: EmphasisUiState) {
    val measurer = rememberTextMeasurer()
    val totalStyle = EspressoType.chipBold.copy(fontSize = 11.5.sp, color = EspressoPalette.ink)
    val segmentStyle = EspressoType.chipBold.copy(fontSize = 10.5.sp, color = EspressoPalette.onColor)
    val headline = state.headline
    Canvas(Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp).semantics {
        contentDescription = "Working sets per week stacked chart, ${state.rangeLabel}. " + state.weeks.joinToString { "${it.label}: ${it.total} sets" }
    }.drawWithCache {
        val top = 20.dp.toPx(); val bottom = size.height - 1.dp.toPx()
        val plotHeight = bottom - top
        val step = size.width / state.weeks.size
        val barWidth = step * .66f
        val yScale = plotHeight / state.chartMax
        val regions = state.regions.reversed()
        // Build cumulative stack edges and the connecting ribbon geometry once per resize.
        val stacks = state.weeks.map { week ->
            var cumulative = 0
            regions.map { r ->
                val low = cumulative; cumulative += week.sets[r] ?: 0
                r to (low to cumulative)
            }
        }
        val ribbons = regions.indices.flatMap { r ->
            (0 until state.weeks.lastIndex).map { i ->
                val a = stacks[i][r].second; val b = stacks[i + 1][r].second
                val x1 = step * (i + .5f) + barWidth / 2
                val x2 = step * (i + 1.5f) - barWidth / 2
                EspressoPalette.muscle(regions[r]).copy(alpha = .22f) to Path().apply {
                    moveTo(x1, bottom - a.first * yScale); lineTo(x1, bottom - a.second * yScale)
                    lineTo(x2, bottom - b.second * yScale); lineTo(x2, bottom - b.first * yScale); close()
                }
            }
        }
        val totals = state.weeks.map { measurer.measure("${it.total}", totalStyle) }
        val labels = stacks.map { stack -> stack.map { (_, pair) -> measurer.measure("${pair.second - pair.first}", segmentStyle) } }
        // The headline's own segment is outlined so the chart points at the number above it.
        val marked = headline?.let { h -> state.weeks.indexOfFirst { it.start == h.week.start } } ?: -1
        onDrawBehind {
            drawLine(EspressoPalette.hair2, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
            ribbons.forEach { (color, path) -> drawPath(path, color) }
            stacks.forEachIndexed { i, stack ->
                val x = step * (i + .5f) - barWidth / 2
                stack.forEachIndexed { r, (region, pair) ->
                    val height = (pair.second - pair.first) * yScale
                    if (height > 0) {
                        val y = bottom - pair.second * yScale
                        drawRect(EspressoPalette.muscle(region), Offset(x, y), Size(barWidth, height))
                        val label = labels[i][r]
                        if (height >= label.size.height + 2.dp.toPx()) drawText(label,
                            topLeft = Offset(x + (barWidth - label.size.width) / 2, y + (height - label.size.height) / 2))
                        if (i == marked && region == headline?.region) {
                            val inset = 1.dp.toPx()
                            drawRoundRect(EspressoPalette.ink, Offset(x - inset, y - inset), Size(barWidth + inset * 2, height + inset * 2),
                                CornerRadius(2.dp.toPx()), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                val total = totals[i]
                drawText(total, topLeft = Offset(x + (barWidth - total.size.width) / 2, bottom - state.weeks[i].total * yScale - total.size.height - 4.dp.toPx()))
            }
        }
    }) {}
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        state.weeks.forEach { week ->
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = "Week of ${week.label}" },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(week.label.substringBefore(' ').uppercase(), style = EspressoType.eyebrow.copy(fontSize = 9.sp, letterSpacing = 1.4.sp),
                    color = EspressoPalette.dim)
                Text(week.label.substringAfter(' '), style = EspressoType.chipBold.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = EspressoPalette.ink, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * The region's every full week. The headline week is tagged with its count and date, and so is the week it is
 * measured against, so the chips above are visible on the chart. A tag that would collide with another is dropped.
 */
@Composable
private fun RegionHistoryStrip(state: EmphasisUiState, headline: EmphasisHeadline) {
    val region = headline.region
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(122.dp).padding(top = 10.dp).semantics {
        contentDescription = "${emphasisRegionName(region)}, every full week, ${state.historyRangeLabel}"
    }.drawWithCache {
        val color = EspressoPalette.muscle(region)
        val counts = state.history.map { it.sets[region] ?: 0 }
        val maximum = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        val step = size.width / counts.size
        val barWidth = step * .7f
        val countStyle = EspressoType.chipBold.copy(fontSize = 13.sp)
        val tagStyle = EspressoType.chipBold.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        val highlight = state.history.indexOfFirst { it.start == headline.week.start }
        val other = headline.runnerUp?.let { r -> state.history.indexOfFirst { it.start == r.start } } ?: -1
        val tagRow = 18.dp.toPx()
        val baseline = size.height - tagRow
        val hiCount = measurer.measure("${counts.getOrElse(highlight) { 0 }}", countStyle.copy(color = EspressoPalette.ink))
        val plotTop = hiCount.size.height + 4.dp.toPx()
        fun barHeight(i: Int) = (baseline - plotTop) * counts[i] / maximum
        fun barLeft(i: Int) = i * step + (step - barWidth) / 2
        fun centred(layout: TextLayoutResult, i: Int) =
            (barLeft(i) + barWidth / 2 - layout.size.width / 2).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        fun rectOf(layout: TextLayoutResult, x: Float, y: Float) = Rect(x, y, x + layout.size.width, y + layout.size.height)
        val gap = 6.dp.toPx()
        // Placed in priority order: the headline week, the week it is measured against, then the strip's first week.
        val placed = mutableListOf<Pair<TextLayoutResult, Offset>>()
        val taken = mutableListOf<Rect>()
        fun place(layout: TextLayoutResult, x: Float, y: Float, padded: Boolean = true) {
            val rect = rectOf(layout, x, y)
            val probe = if (padded) Rect(rect.left - gap / 2, rect.top, rect.right + gap / 2, rect.bottom) else rect
            if (taken.none { it.overlaps(probe) }) { placed += layout to Offset(x, y); taken += rect }
        }
        if (highlight >= 0) {
            taken += Rect(barLeft(highlight), baseline - barHeight(highlight), barLeft(highlight) + barWidth, baseline)
            place(hiCount, centred(hiCount, highlight), baseline - barHeight(highlight) - hiCount.size.height - 4.dp.toPx(), padded = false)
            val tag = measurer.measure(state.history[highlight].label.uppercase(), tagStyle.copy(color = color))
            place(tag, centred(tag, highlight), baseline + 4.dp.toPx())
        }
        if (other >= 0) {
            val count = measurer.measure("${counts[other]}", countStyle.copy(fontSize = 12.sp, color = EspressoPalette.dim))
            place(count, centred(count, other), baseline - barHeight(other) - count.size.height - 4.dp.toPx(), padded = false)
            val tag = measurer.measure(state.history[other].label.uppercase(), tagStyle.copy(color = EspressoPalette.dim))
            place(tag, centred(tag, other), baseline + 4.dp.toPx())
        }
        val start = measurer.measure(state.history.first().label.uppercase(), tagStyle.copy(color = EspressoPalette.faint))
        place(start, 0f, baseline + 4.dp.toPx())
        onDrawBehind {
            drawLine(EspressoPalette.hair2, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
            counts.indices.forEach { i ->
                val height = barHeight(i)
                if (height > 0) drawRoundRect(color.copy(alpha = when (i) { highlight -> 1f; other -> .62f; else -> .32f }),
                    Offset(barLeft(i), baseline - height), Size(barWidth, height), CornerRadius(1.dp.toPx()))
            }
            placed.forEach { (layout, at) -> drawText(layout, topLeft = at) }
        }
    }) {}
}
