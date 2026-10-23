package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.splits.DayInfo
import com.ayman.ecolift.data.splits.DayTier
import com.ayman.ecolift.data.splits.EraBand
import com.ayman.ecolift.data.splits.dayTier
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.plusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate

private val MONTH_INITIALS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
private val MONTH_SHORT_CAPS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

/**
 * The last 52 weeks as a dot-grid habit tracker (spec §03): a marker dot for every day you
 * trained, in the colour of the cycle it belongs to and sized light / regular / heavy, with each
 * cycle as a strip of washi tape carrying its name and session count. Archived tapes open the cycle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearAtAGlance(
    dayInfo: Map<LocalDate, DayInfo>,
    lightThreshold: Long,
    brightThreshold: Long,
    eras: List<EraBand>,
    onOpenArchive: (Long) -> Unit,
) {
    val today = remember { todayLocalDate() }
    val currentMonday = remember(today) { today.minusDays(today.dayOfWeek.ordinal.toLong()) }
    val windowStart = remember(currentMonday) { currentMonday.minusDays(51L * 7L) }
    val windowEnd = remember(currentMonday) { currentMonday.plusDays(6L) }
    val spanDays = (windowEnd.toEpochDays() - windowStart.toEpochDays()).toFloat()
    fun fraction(date: LocalDate): Float = ((date.toEpochDays() - windowStart.toEpochDays()) / spanDays).coerceIn(0f, 1f)

    val archivedOrder = remember(eras) { eras.filter { it.archiveId != null }.sortedBy { it.startFrac } }
    fun eraColor(era: EraBand): Color =
        if (era.archiveId == null) StickerInk.moss
        else StickerInk.archivedCycles[archivedOrder.indexOf(era).coerceAtLeast(0) % StickerInk.archivedCycles.size]
    fun eraOf(date: LocalDate): EraBand? {
        val f = fraction(date)
        return eras.firstOrNull { f >= it.startFrac - 0.002f && f <= it.endFrac + 0.002f }
    }

    val inWindow = remember(dayInfo, windowStart, windowEnd) { dayInfo.filterKeys { it >= windowStart && it <= windowEnd } }
    val eraCounts = remember(inWindow, eras) {
        eras.associateWith { era -> inWindow.keys.count { d -> val f = fraction(d); f >= era.startFrac - 0.002f && f <= era.endFrac + 0.002f } }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .kraftPaper()
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = StickerType.display.fontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = EspressoPalette.ink, letterSpacing = (-0.5).sp)) {
                        append(inWindow.size.toString())
                    }
                    append(" days you showed up")
                },
                style = StickerType.hand(21.sp, Color(0xFFD8CFC3)),
            )
            Text(
                "${MONTH_SHORT_CAPS[windowStart.month.ordinal]} '${(windowStart.year % 100).toString().padStart(2, '0')} — " +
                    "${MONTH_SHORT_CAPS[today.month.ordinal]} '${(today.year % 100).toString().padStart(2, '0')}",
                style = StickerType.print(9.sp, StickerInk.printDim, 1.8.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth
            // Month initials where each month begins.
            Box(Modifier.fillMaxWidth().height(12.dp)) {
                var d = LocalDate(windowStart.year, windowStart.month, 1)
                while (d <= windowEnd) {
                    if (d >= windowStart) {
                        val week = ((d.toEpochDays() - windowStart.toEpochDays()) / 7f)
                        Text(
                            MONTH_INITIALS[d.month.ordinal],
                            style = StickerType.print(8.sp, StickerInk.printDim, 0.5.sp).copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.offset(x = width * (week / 52f)),
                        )
                    }
                    d = if (d.month.ordinal == 11) LocalDate(d.year + 1, 1, 1) else LocalDate(d.year, d.month.ordinal + 2, 1)
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(66.dp)
                .semantics { contentDescription = "${inWindow.size} training days in the last 52 weeks" },
        ) {
            val colW = size.width / 52f
            val rowH = size.height / 7f
            for (week in 0 until 52) {
                for (weekday in 0 until 7) {
                    val date = windowStart.plusDays(week * 7L + weekday)
                    val center = Offset(colW * (week + 0.5f), rowH * (weekday + 0.5f))
                    val info = dayInfo[date]
                    if (info == null || date > windowEnd) {
                        drawCircle(EspressoPalette.ink.copy(alpha = 0.2f), 1.dp.toPx(), center)
                    } else {
                        val era = eraOf(date)
                        val color = era?.let(::eraColor) ?: StickerInk.preCycle
                        val d = when (dayTier(info.volumeLbs, lightThreshold, brightThreshold)) {
                            DayTier.LIGHT -> 3.4.dp.toPx()
                            DayTier.REGULAR -> 4.6.dp.toPx()
                            DayTier.HEAVY -> 6.2.dp.toPx()
                        }
                        drawOval(color, topLeft = Offset(center.x - d / 2f, center.y - d * 0.49f), size = Size(d, d * 0.97f))
                    }
                }
            }
            drawGrain(0.05f)
        }

        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp)) {
            val width = maxWidth
            eras.sortedBy { it.startFrac }.forEachIndexed { i, era ->
                val span = width * (era.endFrac - era.startFrac).coerceAtLeast(0f)
                val current = era.archiveId == null
                val label = if (current) "Now" else era.name
                val count = eraCounts[era] ?: 0
                val base = Modifier
                    .offset(y = if (i % 2 == 0) 2.dp else 22.dp)
                    .rotate(listOf(-1.5f, 1f, -1f)[i % 3])
                    .widthIn(min = span)
                    .height(24.dp)
                    .clip(WashiShape)
                    .drawBehind { drawRect(eraColor(era).copy(alpha = 0.85f)) }
                    .then(
                        if (era.archiveId != null) {
                            Modifier
                                .semantics { contentDescription = "${era.name}, $count sessions"; role = Role.Button }
                                .clickable { onOpenArchive(era.archiveId) }
                        } else {
                            Modifier.semantics { contentDescription = "Current cycle, $count sessions" }
                        },
                    )
                    .padding(horizontal = 7.dp)
                Box(
                    if (current) Modifier.align(Alignment.TopEnd).offset(x = 3.dp).then(base)
                    else Modifier.offset(x = width * era.startFrac).then(base),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = StickerType.hand(14.sp, StickerInk.ink), maxLines = 1, softWrap = false)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 4.dp))
                        Text(
                            count.toString(),
                            style = StickerType.display(11.5.sp, StickerInk.ink, 0.sp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
