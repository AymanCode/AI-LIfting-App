package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.splits.DayInfo
import com.ayman.ecolift.data.splits.DayMarks
import com.ayman.ecolift.data.splits.EraBand
import com.ayman.ecolift.ui.theme.EspressoEyebrow
import com.ayman.ecolift.ui.theme.EspressoIconButton
import com.ayman.ecolift.ui.theme.EspressoMasthead
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoSectionHeader
import com.ayman.ecolift.ui.theme.EspressoTabPills
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.TAB_ARRIVAL_FRAMES
import com.ayman.ecolift.ui.theme.TabArrival
import com.ayman.ecolift.ui.theme.TabWarmupShell
import com.ayman.ecolift.ui.theme.WarmupSweep
import com.ayman.ecolift.ui.theme.splitAccentColor
import com.ayman.ecolift.ui.theme.warmupSweepsAllowed
import com.ayman.ecolift.ui.viewmodel.ArchiveCardUi
import com.ayman.ecolift.ui.viewmodel.SplitCardMemberUi
import com.ayman.ecolift.ui.viewmodel.SplitGroupCardUi
import com.ayman.ecolift.ui.viewmodel.SplitLiftMetrics
import com.ayman.ecolift.ui.viewmodel.SplitTabMode
import com.ayman.ecolift.ui.viewmodel.formatArchiveDateRange
import com.ayman.ecolift.web.util.YearMonthW
import com.ayman.ecolift.web.util.formatVolumeLbsShort
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate

data class SplitType(
    val id: Long,
    val name: String,
    val exerciseCount: Int = 0,
    val lastRunLabel: String = "",
    /** The split's calendar colour (cycle_slot.color_index). */
    val colorIndex: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleSplitScreen(
    splits: List<SplitType>,
    dayInfo: Map<LocalDate, DayInfo>,
    dayMarks: Map<LocalDate, DayMarks> = emptyMap(),
    lightThreshold: Long = 0L,
    flipDirection: Int = 1,
    displayedMonth: YearMonthW,
    tickerText: List<String>,
    brightThreshold: Long,
    lifetimeWorkoutCount: Int,
    eras: List<EraBand>,
    cards: List<com.ayman.ecolift.ui.viewmodel.SplitGroupCardUi>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddSplit: () -> Unit,
    onEditSplit: (com.ayman.ecolift.ui.viewmodel.SplitGroupCardUi) -> Unit,
    smartSuggestEnabled: Boolean = true,
    onToggleSmartSuggest: (() -> Unit)? = null,
    tabMode: SplitTabMode = SplitTabMode.CURRENT,
    onTabModeChange: (SplitTabMode) -> Unit = {},
    archives: List<ArchiveCardUi> = emptyList(),
    onOpenArchive: (Long) -> Unit = {},
    onArchiveCurrentCycle: () -> Unit = {},
    currentCycleStart: LocalDate? = null,
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    // Stickers stick on the first time a month is shown; scrolling the page away and back doesn't replay it.
    var introPlayed by remember { mutableStateOf(emptySet<YearMonthW>()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Espresso masthead (spec §Split): the month is the title in moss; Current / Archive as pills.
            val monthName = SPLIT_MONTH_NAMES[displayedMonth.month - 1]
            EspressoMasthead(
                // The title and the toggle already say "Archive", so the Archive tab has no kicker.
                kicker = if (tabMode == SplitTabMode.ARCHIVE) "" else "SPLIT · ${displayedMonth.year}",
                titleText = if (tabMode == SplitTabMode.ARCHIVE) "Archive" else monthName,
                titleColor = EspressoPalette.legs,
                line = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EspressoTabPills(
                            labels = listOf("Current", "Archive"),
                            selectedIndex = if (tabMode == SplitTabMode.ARCHIVE) 1 else 0,
                            onSelect = { onTabModeChange(if (it == 1) SplitTabMode.ARCHIVE else SplitTabMode.CURRENT) },
                            modifier = Modifier.weight(1f).offset(x = (-14).dp),
                        )
                        if (tabMode == SplitTabMode.CURRENT) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EspressoIconButton("Previous month", onClick = onPreviousMonth) {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = EspressoPalette.ink, modifier = Modifier.size(18.dp))
                                }
                                EspressoIconButton("Next month", onClick = onNextMonth) {
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = EspressoPalette.ink, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (tabMode) {
                    SplitTabMode.CURRENT -> {
                        // Sticker calendar (spec 2026-09-22): the keys carry the counts, no instruction lines.
                        item {
                            SplitCalendar(
                                displayedMonth = displayedMonth,
                                flipDirection = flipDirection,
                                dayInfo = dayInfo,
                                dayMarks = dayMarks,
                                // No temporary splits any more: every repeating routine is a learned group.
                                tempDays = emptySet(),
                                tempNameFor = { null },
                                lightThreshold = lightThreshold,
                                brightThreshold = brightThreshold,
                                splits = splits,
                                playIntro = displayedMonth !in introPlayed,
                                onIntroPlayed = { introPlayed = introPlayed + displayedMonth },
                            )
                        }
                        item {
                            // The split count is the Splits header's aside; the dial shows the names.
                            EspressoSectionHeader(title = "Rotation", accent = EspressoPalette.legs)
                        }
                        item {
                            // Due order, the due group at UP NEXT; peeking never changes it.
                            SplitDial(
                                splits = splits,
                                cycleEnabled = true,
                                currentIndex = 0,
                                onAddSplit = onAddSplit,
                            )
                        }
                        item {
                            EspressoSectionHeader(title = "Your year", accent = EspressoPalette.legs)
                        }
                        item {
                            YearAtAGlance(
                                dayInfo = dayInfo,
                                lightThreshold = lightThreshold,
                                brightThreshold = brightThreshold,
                                eras = eras,
                                onOpenArchive = onOpenArchive,
                            )
                        }
                        item {
                            EspressoSectionHeader(
                                title = "Splits",
                                accent = EspressoPalette.legs,
                                asideParts = listOf(
                                    com.ayman.ecolift.ui.viewmodel.DataPart(cards.size.toString(), isData = true),
                                    com.ayman.ecolift.ui.viewmodel.DataPart(if (cards.size == 1) " split" else " splits", isData = false),
                                ),
                            )
                        }
                        item {
                            SplitGroupCardList(
                                cards = cards,
                                onEdit = onEditSplit,
                                onAddSplit = onAddSplit,
                                smartSuggestEnabled = smartSuggestEnabled,
                                onToggleSmartSuggest = onToggleSmartSuggest,
                            )
                        }
                    }

                    SplitTabMode.ARCHIVE -> {
                        if (archives.isEmpty()) {
                            // The empty shelf: the album this cycle would become, drawn as an outline
                            // with its numbers so far, and the one way to press it.
                            item(key = "ghostAlbum") {
                                GhostAlbumCard(
                                    soFar = cycleSoFar(dayInfo, currentCycleStart, todayLocalDate()),
                                    onArchive = onArchiveCurrentCycle,
                                )
                            }
                        } else {
                            item {
                                Button(
                                    onClick = onArchiveCurrentCycle,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EspressoPalette.panel2, contentColor = EspressoPalette.ink)
                                ) {
                                    Text(
                                        text = "Archive current cycle",
                                        color = EspressoPalette.ink,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            item(key = "discographyHeader") {
                                EspressoSectionHeader(
                                    title = "Cycles",
                                    accent = EspressoPalette.legs,
                                    asideParts = listOf(
                                        com.ayman.ecolift.ui.viewmodel.DataPart(archives.size.toString(), isData = true),
                                        com.ayman.ecolift.ui.viewmodel.DataPart(if (archives.size == 1) " album" else " albums", isData = false),
                                    ),
                                )
                            }
                            itemsIndexed(
                                items = archives,
                                key = { _, card -> card.id },
                                contentType = { _, _ -> "archiveCard" }
                            ) { index, card ->
                                ArchiveListCard(card = card, accent = splitAccentColor(index), onClick = { onOpenArchive(card.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveListCard(card: ArchiveCardUi, accent: Color, onClick: () -> Unit) {
    // Espresso: every archived cycle is an album. Ridge art in the cycle's colour on the left, the
    // volume as the big number, a tag for new cycles (spec §Split, Archive). Never "N/A".
    val shape = RoundedCornerShape(22.dp)
    val fresh = card.totalVolumeLbs <= 0L
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = EspressoPalette.panel),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            AlbumArt(weekly = card.weeklySessions, accent = accent, scored = !fresh, modifier = Modifier.width(118.dp).fillMaxHeight())
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                EspressoEyebrow(card.dateRangeLabel)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = card.name.ifBlank { "Untitled cycle" },
                    color = EspressoPalette.ink,
                    fontFamily = LogDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.4).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${card.splitCount} " +
                        (if (card.splitCount == 1) "split" else "splits") +
                        " · ${card.sessionCount} " +
                        if (card.sessionCount == 1) "session" else "sessions",
                    color = EspressoPalette.dim,
                    fontFamily = LogUiFontFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    if (fresh) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(EspressoPalette.down)
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "NEW",
                                color = EspressoPalette.onColor,
                                fontFamily = LogUiFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                            )
                        }
                    } else {
                        Text(
                            text = formatVolumeLbsShort(card.totalVolumeLbs),
                            color = accent,
                            fontFamily = LogDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            lineHeight = 30.sp,
                            letterSpacing = (-1.5).sp,
                            maxLines = 1,
                        )
                        Text(
                            text = " lb",
                            color = EspressoPalette.faint,
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The running cycle, counted the way an album counts it: days with checked sets, start to last session. */
internal data class CycleSoFar(
    val start: LocalDate,
    val lastSession: LocalDate?,
    val sessions: Int,
    val splits: Int,
    val volumeLbs: Long,
)

/**
 * [cycleStart] is the date the archive action would start from (the cycle's start, else the first
 * checked set); without it the first worked day stands in, and a new account starts today.
 */
internal fun cycleSoFar(dayInfo: Map<LocalDate, DayInfo>, cycleStart: LocalDate?, today: LocalDate): CycleSoFar {
    val start = cycleStart ?: dayInfo.keys.minOrNull() ?: today
    val days = dayInfo.filterKeys { it >= start && it <= today }
    return CycleSoFar(
        start = start,
        lastSession = days.keys.maxOrNull(),
        sessions = days.size,
        splits = days.values.mapNotNull { it.splitId }.distinct().size,
        volumeLbs = days.values.sumOf { it.volumeLbs },
    )
}

/**
 * The empty archive: the album this cycle would become, as a dashed outline in the album's own
 * layout (ridge art, date eyebrow, name, counts, volume) with the archive action as its foot.
 */
@Composable
private fun GhostAlbumCard(soFar: CycleSoFar, onArchive: () -> Unit) {
    val accent = EspressoPalette.legs
    val shape = RoundedCornerShape(22.dp)
    val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(EspressoPalette.panel.copy(alpha = 0.45f))
            .drawBehind {
                val inset = 1.dp.toPx() / 2
                drawRoundRect(
                    color = EspressoPalette.hair2,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                )
            },
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            AlbumArt(
                // The cycle is still running: its art is the dashed baseline until it is pressed.
                weekly = emptyList(),
                accent = accent,
                scored = false,
                modifier = Modifier.width(118.dp).fillMaxHeight().padding(start = 1.dp, top = 1.dp)
                    .clip(RoundedCornerShape(topStart = 21.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                EspressoEyebrow(
                    if (soFar.lastSession != null) formatArchiveDateRange(soFar.start.toString(), soFar.lastSession.toString())
                    else formatArchiveDateRange(soFar.start.toString(), soFar.start.toString()).substringAfter(" - "),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Current cycle",
                    color = EspressoPalette.dim,
                    fontFamily = LogDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                com.ayman.ecolift.ui.theme.DataText(
                    parts = buildList {
                        if (soFar.splits > 0) {
                            add(com.ayman.ecolift.ui.viewmodel.data(soFar.splits.toString()))
                            add(com.ayman.ecolift.ui.viewmodel.filler(if (soFar.splits == 1) " split · " else " splits · "))
                        }
                        add(com.ayman.ecolift.ui.viewmodel.data(soFar.sessions.toString()))
                        add(com.ayman.ecolift.ui.viewmodel.filler(if (soFar.sessions == 1) " session" else " sessions"))
                    },
                    size = 11.5.sp,
                    lineHeight = 15.sp,
                    filler = EspressoPalette.faint,
                    data = EspressoPalette.ink,
                    maxLines = 1,
                )
                if (soFar.volumeLbs > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatVolumeLbsShort(soFar.volumeLbs),
                            color = accent.copy(alpha = 0.8f),
                            fontFamily = LogDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            lineHeight = 30.sp,
                            letterSpacing = (-1.5).sp,
                            maxLines = 1,
                        )
                        Text(
                            text = " lb",
                            color = EspressoPalette.faint,
                            fontFamily = LogUiFontFamily,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 1.dp).height(1.dp).drawBehind {
            drawLine(EspressoPalette.hair2, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        })
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(EspressoPalette.tint(accent))
                .clickable(role = Role.Button, onClick = onArchive)
                .semantics(mergeDescendants = true) { contentDescription = "Archive current cycle" },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SleeveGlyph(color = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Archive current cycle",
                color = EspressoPalette.ink,
                fontFamily = LogUiFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
            )
        }
    }
}

/** A record sleeve with the disc half out: the archive's album, drawn. */
@Composable
private fun SleeveGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        val sleeve = size.height * 0.78f
        val top = (size.height - sleeve) / 2
        drawCircle(color, radius = sleeve * 0.42f, center = Offset(sleeve * 0.92f, size.height / 2), style = Stroke(stroke))
        drawCircle(color, radius = sleeve * 0.08f, center = Offset(sleeve * 0.92f, size.height / 2))
        drawRoundRect(
            color = EspressoPalette.tint(color),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(sleeve * 0.78f, sleeve),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, top),
            size = androidx.compose.ui.geometry.Size(sleeve * 0.78f - stroke, sleeve),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = Stroke(stroke),
        )
    }
}

/**
 * Album art: a ridge line in the cycle's colour on a tint of it, drawn from the cycle's sessions
 * per week (the album hero's ridge), the flag on the best week (the latest, on a tie), kept whole
 * inside the art. Unscored cycles get a dashed
 * ridge. With no weekly counts (not loaded, failed, or no sessions) it draws only a flat dashed
 * baseline, never a made-up shape.
 */
@Composable
internal fun AlbumArt(weekly: List<Int>, accent: Color, scored: Boolean, modifier: Modifier = Modifier) {
    val ridge = remember(weekly) { albumRidgeHeights(weekly) }
    Box(modifier = modifier.background(EspressoPalette.tint(accent))) {
        Canvas(Modifier.fillMaxSize()) {
            if (ridge.isEmpty()) {
                val y = size.height * 0.7f
                drawLine(
                    color = accent.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
                return@Canvas
            }
            // A one-week cycle still needs two points for a line: it runs flat across.
            val heights = if (ridge.size == 1) ridge + ridge else ridge
            val n = heights.size
            val stepX = size.width / (n - 1)
            fun y(h: Float) = size.height - h * size.height * 0.78f
            val line = Path().apply {
                heights.forEachIndexed { i, h ->
                    if (i == 0) moveTo(0f, y(h)) else lineTo(stepX * i, y(h))
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, accent.copy(alpha = if (scored) 0.85f else 0.35f))
            val back = Path().apply {
                heights.forEachIndexed { i, h ->
                    val hy = size.height - (h * 0.55f) * size.height * 0.78f
                    if (i == 0) moveTo(0f, hy) else lineTo(stepX * i, hy)
                }
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(back, EspressoPalette.bg.copy(alpha = 0.35f))
            if (!scored) {
                drawPath(
                    line,
                    color = accent,
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            val peakIndex = albumFlagIndex(heights)
            val flag = 3.dp.toPx()
            drawCircle(
                EspressoPalette.ink,
                flag,
                Offset(
                    albumFlagCenter(stepX * peakIndex, flag, size.width),
                    albumFlagCenter(y(heights[peakIndex]), flag, size.height),
                ),
            )
        }
    }
}

internal fun buildGymCalendarGrid(displayedMonth: YearMonthW): List<LocalDate> {
    val firstOfMonth = displayedMonth.atDay(1)
    val daysInMonth = displayedMonth.lengthOfMonth()
    val firstDayOfWeek = (firstOfMonth.dayOfWeek.ordinal + 1) % 7
    val grid = mutableListOf<LocalDate>()

    val previousMonth = displayedMonth.minusMonths(1)
    for (i in firstDayOfWeek downTo 1) {
        grid.add(previousMonth.atEndOfMonth().minusDays((i - 1).toLong()))
    }

    for (i in 1..daysInMonth) {
        grid.add(displayedMonth.atDay(i))
    }

    val nextMonth = displayedMonth.plusMonths(1)
    var nextDay = 1
    while (grid.size < 42) {
        grid.add(nextMonth.atDay(nextDay++))
    }

    return grid
}

internal fun isGymCalendarDateWorked(date: LocalDate, gymDays: Set<LocalDate>): Boolean =
    date in gymDays

internal fun countGymDaysInMonth(gymDays: Set<LocalDate>, displayedMonth: YearMonthW): Int =
    gymDays.count { YearMonthW.from(it) == displayedMonth }

// ---------------------------------------------------------------------------------------------
// Shader warm-up (see ShaderWarmup)
// ---------------------------------------------------------------------------------------------

private const val SPLIT_WARMUP = "split-tab"
private const val SPLIT_WARMUP_DELAY_MS = 5_500L

/** The list is about 2000dp under a 250dp masthead. A 12dp step is under the page's stickers and rows, so each is cut at several phases of each edge. */
private const val SPLIT_SWEEP_FRAMES = 120
private const val SPLIT_SWEEP_STEP_DP = 12f

/**
 * Compiles the Split tab's shaders while nothing is moving (see [ShaderWarmup]): the real screen
 * with a made-up month, dial, year and cards, behind the app's opaque background. It is drawn in
 * the tab transition's pose (the first visit, calendar and binding included, which
 * `CalendarShaderWarmup`'s tilted poses do not cover), and where [warmupSweepsAllowed] a second
 * copy scrolls top to bottom so the calendar page, the dial, the year and every card cross the
 * list's edges (the first scroll, which used to compile seventeen programs). Nothing here reads a
 * view model or changes anything: every callback is a no-op.
 */
@Composable
internal fun SplitTabWarmup(bottomReserved: Dp) {
    val sweep = warmupSweepsAllowed
    TabWarmupShell(SPLIT_WARMUP, SPLIT_WARMUP_DELAY_MS, if (sweep) SPLIT_SWEEP_FRAMES + 3 else TAB_ARRIVAL_FRAMES, bottomReserved) {
        val sample = remember { warmupSplitSample() }
        TabArrival { SplitWarmupScreen(sample, rememberLazyListState()) }
        if (sweep) {
            val list = rememberLazyListState()
            WarmupSweep(list, SPLIT_SWEEP_STEP_DP, SPLIT_SWEEP_FRAMES)
            Box(Modifier.fillMaxSize()) { SplitWarmupScreen(sample, list) }
        }
    }
}

@Composable
private fun SplitWarmupScreen(sample: SplitWarmupSample, listState: LazyListState) {
    CycleSplitScreen(
        splits = sample.splits,
        dayInfo = sample.days,
        dayMarks = sample.marks,
        lightThreshold = 1_000L,
        displayedMonth = sample.month,
        tickerText = emptyList(),
        brightThreshold = 3_000L,
        lifetimeWorkoutCount = sample.days.size,
        eras = sample.eras,
        cards = sample.cards,
        onPreviousMonth = {},
        onNextMonth = {},
        onAddSplit = {},
        onEditSplit = {},
        listState = listState,
    )
}

private class SplitWarmupSample(
    val month: YearMonthW,
    val days: Map<LocalDate, DayInfo>,
    val marks: Map<LocalDate, DayMarks>,
    val splits: List<SplitType>,
    val cards: List<SplitGroupCardUi>,
    val eras: List<EraBand>,
)

/**
 * This month and last (every sticker kind, a heavy-lift PR note, a temp loop), four splits on the
 * dial, three boards (tiles with no, one and six bars, rising, falling, flat and no change, other
 * options, and an unsaved group's tag with no number) and a current and an archived cycle on the
 * year's tape.
 */
private fun warmupSplitSample(): SplitWarmupSample {
    val month = YearMonthW.now()
    val thisMonth = warmupMonth(month)
    val lastMonth = warmupMonth(month.minusMonths(1))
    val last = todayLocalDate().minusDays(3).toString()
    fun member(id: Long, name: String, top: String?, volume: List<Float> = emptyList(), delta: Int? = null) =
        SplitCardMemberUi(id, name, SplitLiftMetrics(top, volume, delta))
    val six = listOf(900f, 1100f, 1000f, 1400f, 1300f, 1700f)
    return SplitWarmupSample(
        month = month,
        days = lastMonth.days + thisMonth.days,
        marks = lastMonth.marks + thisMonth.marks,
        splits = listOf(
            SplitType(1, "Lower Strength", 4, "last Sep 24", 0),
            SplitType(2, "Pull + Posterior", 5, "last Sep 26", 1),
            SplitType(3, "Upper Strength", 5, "last Sep 28", 2),
            SplitType(4, "Push + Athletic", 4, "not trained yet", 3),
        ),
        cards = listOf(
            SplitGroupCardUi(
                slotId = 1, name = "Lower Strength", colorIndex = 0, isUnnamed = false, isDue = true,
                members = listOf(member(1, "Back Squat", "270×4", six, 8), member(2, "Leg Press", "445×9", six.reversed(), -6), member(9, "Hip Thrust", "230×8", listOf(800f, 800f), 0)),
                otherOptions = listOf(member(3, "Standing Calf Raise", "185×11"), member(10, "Farmer Carry", "91×9")),
                sessions = 6, lastSessionDate = last, creationNumber = 1,
            ),
            SplitGroupCardUi(
                slotId = 2, name = "Pull + Posterior", colorIndex = 1, isUnnamed = false, isDue = false,
                members = listOf(member(4, "Deadlift", "315×3", listOf(3000f)), member(5, "Lat Pulldown", "140×10", six), member(6, "Pull Up", "BW×8")),
                otherOptions = emptyList(), sessions = 5, lastSessionDate = last, creationNumber = 2,
            ),
            SplitGroupCardUi(
                slotId = -1, name = "Arms", colorIndex = 2, isUnnamed = false, isDue = false,
                members = listOf(member(7, "Bicep Curl", "35×10"), member(8, "Tricep Pushdown", null)),
                otherOptions = emptyList(), sessions = 0, lastSessionDate = null, creationNumber = null,
            ),
        ),
        eras = listOf(
            EraBand(archiveId = 7L, name = "Winter cut", startFrac = 0.08f, endFrac = 0.52f),
            EraBand(archiveId = null, name = "Current", startFrac = 0.52f, endFrac = 1f),
        ),
    )
}
