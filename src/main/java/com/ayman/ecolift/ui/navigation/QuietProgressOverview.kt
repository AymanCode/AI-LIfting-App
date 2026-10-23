package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.month.SheetBg
import com.ayman.ecolift.ui.month.SheetInk
import com.ayman.ecolift.ui.month.SheetProgressTabs
import com.ayman.ecolift.ui.month.sheetSerif
import com.ayman.ecolift.ui.theme.*
import com.ayman.ecolift.ui.viewmodel.HeldLiftRowUi
import com.ayman.ecolift.ui.viewmodel.HeldSectionKind
import com.ayman.ecolift.ui.viewmodel.HeldSectionUi
import com.ayman.ecolift.ui.viewmodel.GroupWallUi
import com.ayman.ecolift.ui.viewmodel.GroupWallSource
import com.ayman.ecolift.ui.viewmodel.buildGroupWall
import com.ayman.ecolift.ui.viewmodel.QuietProgressOverview
import com.ayman.ecolift.ui.viewmodel.QuietProgressPoint
import com.ayman.ecolift.ui.viewmodel.StakeUi
import com.ayman.ecolift.ui.viewmodel.TimeframeFilter
import com.ayman.ecolift.ui.viewmodel.stakeText
import com.ayman.ecolift.ui.viewmodel.ChangelogKind
import com.ayman.ecolift.ui.viewmodel.otherSets
import com.ayman.ecolift.ui.viewmodel.supportingSetLabel
import com.ayman.ecolift.ui.viewmodel.formatTopSet
import com.ayman.ecolift.ui.viewmodel.formatLoad
import com.ayman.ecolift.ui.viewmodel.shortDate
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.ui.viewmodel.progressWindowStart
import com.ayman.ecolift.web.util.YearMonthW
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
private fun QuietText(text: String, size: Int = 12, color: Color = QuietDepth.ink, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null) {
    Text(text, modifier, color, fontFamily = LogUiFontFamily, fontSize = size.sp,
        lineHeight = (size + if (size <= 12) 2 else 4).sp, letterSpacing = 0.sp, fontWeight = weight, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, textAlign = align)
}

private fun quietDate(date: LocalDate): String = "${MONTH_ABBREV[date.month.ordinal]} ${date.day}"
private fun quietDate(iso: String): String = quietDate(LocalDate.parse(iso))
private fun quietSortLabel(sort: ProgressSortModeV2): String = when (sort) {
    ProgressSortModeV2.TREND -> "Trend"
    ProgressSortModeV2.RECENT -> "Recent"
    ProgressSortModeV2.NAME -> "Name"
    ProgressSortModeV2.SESSIONS -> "Sessions"
    ProgressSortModeV2.BODY_PART -> "Muscle"
}

@Composable
fun QuietProgressOverviewScreen(
    exercises: List<ProgressExercise>,
    isPending: Boolean = false,
    selectedRange: TimeRangeV2,
    onRangeChange: (TimeRangeV2) -> Unit,
    windowStart: LocalDate,
    windowEnd: LocalDate,
    onWindowEndChange: (LocalDate) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    sortMode: ProgressSortModeV2,
    onSortModeChange: (ProgressSortModeV2) -> Unit,
    onExerciseClick: (ProgressExercise) -> Unit,
    onOrganizationModeChange: (ProgressOrganizationModeV2) -> Unit,
    onOpenHighlights: () -> Unit,
    modifier: Modifier = Modifier,
    groupWall: GroupWallUi = GroupWallUi(),
    heldAvailable: Boolean = false,
    showAllLifts: Boolean = false,
    onShowAllLiftsChange: (Boolean) -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    betweenPanels: (@Composable (index: Int) -> Unit)? = null,
    onOpenEmphasis: () -> Unit = {},
) {
    // The landing is the held lifts; "All lifts" is the library (spec 2026-09-28).
    val showHeld = heldAvailable && !showAllLifts
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // The overview's SaveableStateProvider retains these keys while a report is pushed.
    var expandedEmptyPanels by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var controlsOpen by rememberSaveable { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var sortAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    val reducedMotion = rememberReducedMotion()
    fun stepPeriod(direction: Int) {
        if (selectedRange == TimeRangeV2.TWO_WEEKS) {
            onWindowEndChange(windowEnd.minusDays(-14L * direction))
            return
        }
        val months = when (selectedRange) {
            TimeRangeV2.TWO_WEEKS -> 0
            TimeRangeV2.ONE_MONTH -> 1
            TimeRangeV2.THREE_MONTHS -> 3
            TimeRangeV2.SIX_MONTHS -> 6
            TimeRangeV2.ONE_YEAR, TimeRangeV2.ALL -> 12
        }
        onWindowEndChange(windowEnd.plus(DatePeriod(months = months * direction)))
    }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { boxOrigin = it.positionInRoot() }) {
    val compact = maxWidth < 359.dp
    Column(Modifier.fillMaxSize()) {
    ProgressMasthead(
        selectedIndex = 1,
        onSelect = { index ->
            when (index) {
                0 -> onOpenHighlights()
                2 -> onOrganizationModeChange(ProgressOrganizationModeV2.MONTH)
            }
        },
        compact = compact,
        modifier = Modifier.semantics { if (calendarOpen || sortOpen) hideFromAccessibility() },
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().semantics {
            contentDescription = "Progress overview"
            if (calendarOpen || sortOpen) hideFromAccessibility()
        },
        contentPadding = PaddingValues(start = if (compact) 17.dp else 22.dp, end = if (compact) 17.dp else 22.dp, bottom = 12.dp),
    ) {
        item(key = "header") {
            if (showHeld) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupWallWindowChip(groupWall, controlsOpen, { controlsOpen = !controlsOpen }, Modifier.weight(1f))
                    // Below 359dp the arrows give their width to the window dates so those stay on one line.
                    QuietScopePill("All lifts", "Show all lifts", trailingArrow = !compact, compact = compact) { onShowAllLiftsChange(true) }
                    QuietScopePill("Emphasis", "Open emphasis", trailingArrow = !compact, compact = compact, onClick = onOpenEmphasis)
                }
            }
            if (!showHeld || controlsOpen) QuietRangeControl(
                selectedRange = selectedRange, onRangeChange = onRangeChange,
                windowStart = windowStart, windowEnd = windowEnd,
                onPrevious = { stepPeriod(-1) }, onNext = { stepPeriod(1) },
                onChooseDate = { calendarOpen = true }, reducedMotion = reducedMotion,
                modifier = Modifier.padding(top = if (showHeld) 4.dp else 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietExerciseSearch(searchQuery, onSearchChange, reducedMotion, Modifier.weight(1f))
                if (!showHeld) {
                    QuietSortButton(sortMode, sortOpen, Modifier.onGloballyPositioned { sortAnchor = it.boundsInRoot() }) { sortOpen = !sortOpen }
                    if (heldAvailable) QuietScopePill("Held", "Show held lifts") { onShowAllLiftsChange(false) }
                }
            }
            if (!showHeld) HorizontalDivider(color = QuietDepth.line, thickness = 1.dp)
        }
        if (showHeld) {
            if (!isPending && groupWall.panels.isEmpty()) item(key = "held-empty") {
                QuietText("No matching held lifts. Try All lifts.", 13, QuietDepth.muted, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), align = TextAlign.Center)
            }
            groupWall.panels.forEachIndexed { index, panel ->
                val folded = panel.sessions == 0 && panel.key !in expandedEmptyPanels
                item(key = "wall-${panel.key}-header", contentType = "wall-header") {
                    GroupWallPanelHeader(panel, folded, compact) {
                        expandedEmptyPanels = if (panel.key in expandedEmptyPanels) expandedEmptyPanels - panel.key
                            else expandedEmptyPanels + panel.key
                    }
                }
                // Each two-tile row is a lazy item, including when one group holds many lifts.
                if (!folded) {
                    items(panel.rows.size, key = { "wall-${panel.key}-row-$it" }, contentType = { "wall-row" }) { row ->
                        GroupWallTileRow(panel.rows[row], panel.colorIndex, compact, onOpenReport,
                            offsetY = groupWallRowOffset(row), bottom = panel.tail.isEmpty() && row == panel.rows.lastIndex)
                    }
                    if (panel.tail.isNotEmpty()) item(key = "wall-${panel.key}-tail", contentType = "wall-tail") {
                        GroupWallPanelTail(panel, compact, onOpenReport, groupWallTailOffset(panel.rows.size))
                    }
                }
                // The insight deck owns this seam. An absent callback draws and spaces nothing.
                if (index == 0 && groupWall.panels.size > 1 && betweenPanels != null) {
                    item(key = "between-panels-0") { betweenPanels(0) }
                }
            }
        } else {
            if (!isPending && exercises.isEmpty()) item(key = "empty") {
                QuietText(if (searchQuery.isNotBlank()) "No matching exercises. Try a different name." else "Log a completed working set to start seeing your progress.", 13, QuietDepth.muted, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), align = TextAlign.Center)
            }
            items(exercises, key = { "exercise-${it.id ?: it.name}" }) { exercise ->
                val key = (exercise.id?.toString() ?: exercise.name)
                QuietExerciseRow(exercise, key in expanded, windowStart, windowEnd, reducedMotion, compact,
                    onToggle = { expanded = if (key in expanded) expanded - key else expanded + key },
                    onOpenHistory = { onExerciseClick(exercise) })
            }
        }
        item(key = "emphasis-link") {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpenEmphasis)
                .semantics(mergeDescendants = true) { contentDescription = "Emphasis" }
                .padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Emphasis", style = EspressoType.name, color = EspressoPalette.ink)
                Text("›", style = EspressoType.name, color = EspressoPalette.dim)
            }
        }
    }
    }
    // Sort: a menu dropped from the pill, no scrim; the period: a sheet from the bottom edge.
    QuietSortMenu(
        open = sortOpen,
        anchor = sortAnchor?.translate(-boxOrigin),
        containerWidth = constraints.maxWidth.toFloat(),
        selected = sortMode,
        reducedMotion = reducedMotion,
        onSelect = { onSortModeChange(it); sortOpen = false },
        onDismiss = { sortOpen = false },
    )
    QuietPeriodSheet(
        open = calendarOpen,
        initialDate = windowEnd,
        range = selectedRange,
        reducedMotion = reducedMotion,
        onDismiss = { calendarOpen = false },
        onSelect = {
            onWindowEndChange(it)
            calendarOpen = false
        },
    )
    }
}

/** Where the sort menu's left edge goes: right-aligned under the pill, kept [margin] inside the screen. */
internal fun quietMenuLeft(anchorRight: Float, menuWidth: Float, containerWidth: Float, margin: Float): Float =
    (anchorRight - menuWidth).coerceAtMost(containerWidth - margin - menuWidth).coerceAtLeast(margin)

/** The first day a period ending on [end] would show, or null for All (it starts at the first session). */
internal fun quietWindowStartFor(range: TimeRangeV2, end: LocalDate): LocalDate? = when (range) {
    TimeRangeV2.TWO_WEEKS -> progressWindowStart(TimeframeFilter.TWO_WEEKS, end)
    TimeRangeV2.ONE_MONTH -> progressWindowStart(com.ayman.ecolift.ui.viewmodel.TimeframeFilter.ONE_MONTH, end)
    TimeRangeV2.THREE_MONTHS -> progressWindowStart(com.ayman.ecolift.ui.viewmodel.TimeframeFilter.THREE_MONTHS, end)
    TimeRangeV2.SIX_MONTHS -> progressWindowStart(com.ayman.ecolift.ui.viewmodel.TimeframeFilter.SIX_MONTHS, end)
    TimeRangeV2.ONE_YEAR -> progressWindowStart(com.ayman.ecolift.ui.viewmodel.TimeframeFilter.ONE_YEAR, end)
    TimeRangeV2.ALL -> null
}

/**
 * The sort options as a menu hanging from the pill: it grows from the pill's corner, marks the
 * current order with a drawn check, and closes on any tap outside it. Kept in the main Compose
 * scene (no Popup) so Wasm restores the accessibility tree when it closes.
 */
@Composable
private fun BoxScope.QuietSortMenu(
    open: Boolean,
    anchor: androidx.compose.ui.geometry.Rect?,
    containerWidth: Float,
    selected: ProgressSortModeV2,
    reducedMotion: Boolean,
    onSelect: (ProgressSortModeV2) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    if (open) {
        Box(Modifier.matchParentSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .onPreviewKeyEvent { if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) { onDismiss(); true } else false }
            .focusRequester(focus).focusable())
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
    if (anchor == null) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val menuWidth = 212.dp
    val left = with(density) { quietMenuLeft(anchor.right, menuWidth.toPx(), containerWidth, 12.dp.toPx()).toDp() }
    val top = with(density) { (anchor.bottom).toDp() } + 6.dp
    // Scale from the corner the pill sits in: its right edge, unless the menu had to shift.
    val originX = with(density) { ((anchor.right - left.toPx()) / menuWidth.toPx()).coerceIn(0f, 1f) }
    val origin = androidx.compose.ui.graphics.TransformOrigin(originX, 0f)
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.offset(x = left, y = top),
        enter = if (reducedMotion) fadeIn(tween(0)) else
            scaleIn(spring(dampingRatio = 0.82f, stiffness = 900f), initialScale = 0.86f, transformOrigin = origin) + fadeIn(tween(110)),
        exit = if (reducedMotion) fadeOut(tween(0)) else
            scaleOut(tween(110), targetScale = 0.92f, transformOrigin = origin) + fadeOut(tween(110)),
    ) {
        Column(
            Modifier.width(menuWidth).clip(RoundedCornerShape(12.dp)).background(EspressoPalette.panel2)
                .border(1.dp, EspressoPalette.hair2, RoundedCornerShape(12.dp))
                .padding(vertical = 5.dp)
                .selectableGroup(),
        ) {
            ProgressSortModeV2.entries.forEachIndexed { index, mode ->
                val active = mode == selected
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = EspressoPalette.hair, thickness = 1.dp)
                Row(
                    Modifier.fillMaxWidth().height(44.dp)
                        .semantics { this.selected = active }
                        .clickable(role = Role.RadioButton) { onSelect(mode) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(quietSortLabel(mode), Modifier.weight(1f), color = if (active) EspressoPalette.ink else EspressoPalette.dim,
                        fontFamily = LogUiFontFamily, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1)
                    if (active) QuietCheckGlyph(EspressoPalette.last, Modifier.size(14.dp))
                }
            }
        }
    }
}

/** A hand-set check: short stroke down, long stroke up. */
@Composable
private fun QuietCheckGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.54f)
            lineTo(size.width * 0.40f, size.height * 0.80f)
            lineTo(size.width * 0.90f, size.height * 0.20f)
        }
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * The period picker as an Espresso sheet: it rises from the bottom edge over a light scrim, has a
 * grabber you can pull down to close, and shades the days the chosen period would cover.
 */
@Composable
private fun BoxScope.QuietPeriodSheet(
    open: Boolean,
    initialDate: LocalDate,
    range: TimeRangeV2,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val focus = remember { FocusRequester() }
    AnimatedVisibility(open, Modifier.matchParentSize(), enter = fadeIn(tween(if (reducedMotion) 0 else 180)), exit = fadeOut(tween(if (reducedMotion) 0 else 160))) {
        Box(Modifier.fillMaxSize().background(EspressoPalette.mast.copy(alpha = .62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
    }
    AnimatedVisibility(
        open,
        Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(if (reducedMotion) tween(0) else spring(dampingRatio = 0.9f, stiffness = 520f)) { it },
        exit = slideOutVertically(tween(if (reducedMotion) 0 else 200)) { it },
    ) {
        var drag by remember { mutableStateOf(0f) }
        val settle by animateFloatAsState(drag, if (drag == 0f) spring(stiffness = 700f) else snap(), label = "Period sheet drag")
        val density = androidx.compose.ui.platform.LocalDensity.current
        val dismissAt = with(density) { 90.dp.toPx() }
        val dragState = androidx.compose.foundation.gestures.rememberDraggableState { drag = (drag + it).coerceAtLeast(0f) }
        Column(
            Modifier.fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(0, settle.toInt()) }
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(EspressoPalette.panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .onPreviewKeyEvent { if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) { onDismiss(); true } else false }
                .focusRequester(focus).focusable()
                .draggable(dragState, androidx.compose.foundation.gestures.Orientation.Vertical,
                    onDragStopped = { if (drag > dismissAt) onDismiss() else drag = 0f }),
        ) {
            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(EspressoPalette.hair2))
            }
            QuietPeriodPicker(initialDate, range, onDismiss = onDismiss, onSelect = onSelect)
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/** Date-only calendar: no UTC-to-local conversion in the Wasm platform date formatter. */
@Composable
private fun QuietPeriodPicker(initialDate: LocalDate, range: TimeRangeV2, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    var selectedIso by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var monthIso by rememberSaveable { mutableStateOf(YearMonthW.from(initialDate).atDay(1).toString()) }
    val selected = LocalDate.parse(selectedIso)
    val month = YearMonthW.from(LocalDate.parse(monthIso))
    val today = todayLocalDate()
    val windowStart = quietWindowStartFor(range, selected)
    val accent = EspressoPalette.back
    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                QuietText("Ends", 12, EspressoPalette.dim)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(quietDate(selected), fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold,
                        fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp, color = EspressoPalette.ink,
                        modifier = Modifier.alignByBaseline())
                    Text(" ${selected.year}", fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, lineHeight = 18.sp, color = EspressoPalette.dim, modifier = Modifier.alignByBaseline())
                }
            }
            if (windowStart != null) {
                DataText(
                    listOf(data(range.label), filler(" from "), data(quietDate(windowStart) + if (windowStart.year != selected.year) ", ${windowStart.year}" else "")),
                    size = 12.sp, filler = EspressoPalette.faint, data = EspressoPalette.dim, maxLines = 1,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 14.dp), color = EspressoPalette.hair, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(MONTH_NAMES_FULL[month.month - 1], fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, lineHeight = 20.sp, color = EspressoPalette.ink, modifier = Modifier.alignByBaseline())
                Text(" ${month.year}", fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, lineHeight = 16.sp, color = EspressoPalette.faint, modifier = Modifier.alignByBaseline())
            }
            QuietDateButton(QuietIconName.Left, "Previous calendar month", month.atDay(1) > LocalDate(2000, 1, 1)) { monthIso = month.minusMonths(1).atDay(1).toString() }
            QuietDateButton(QuietIconName.Right, "Next calendar month", month.atEndOfMonth() < today) { monthIso = month.plusMonths(1).atDay(1).toString() }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), style = EspressoType.eyebrow, color = EspressoPalette.faint, textAlign = TextAlign.Center)
            }
        }
        val offset = month.atDay(1).dayOfWeek.ordinal
        // Always six weeks, so the sheet keeps its height while you page months.
        repeat(6) { week -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            repeat(7) { weekday ->
                val day = week * 7 + weekday - offset + 1
                if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(40.dp)) else {
                    val date = month.atDay(day)
                    val active = date == selected
                    val inWindow = windowStart != null && date >= windowStart && date < selected
                    val future = date > today
                    // The period as a rule under its days: it starts under the first day and runs
                    // on into the chosen end, like a measured span.
                    val ruleColor = accent.copy(alpha = .7f)
                    val runStart = date == windowStart
                    Box(Modifier.weight(1f).height(40.dp).semantics {
                        contentDescription = "Choose period end $date"
                        this.selected = active
                    }.drawBehind {
                        if (inWindow) {
                            val y = size.height - 3.dp.toPx()
                            val x0 = if (runStart) size.width / 2 else 0f
                            drawLine(ruleColor, Offset(x0, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Butt)
                            if (runStart) drawCircle(ruleColor, 2.5.dp.toPx(), Offset(x0, y))
                        }
                    }
                        .clickable(enabled = !future, role = Role.Button) { selectedIso = date.toString() }, contentAlignment = Alignment.Center) {
                        if (active) Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent))
                        else if (date == today) Box(Modifier.size(36.dp).border(1.dp, EspressoPalette.hair2, RoundedCornerShape(10.dp)))
                        Text(day.toString(), fontFamily = LogDisplayFontFamily,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 16.sp,
                            color = when { active -> EspressoPalette.onColor; future -> EspressoPalette.faint.copy(alpha = .45f); inWindow -> EspressoPalette.ink; else -> EspressoPalette.dim })
                    }
                }
            }
        } }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(0.42f).height(48.dp).clip(RoundedCornerShape(14.dp))
                .border(1.dp, EspressoPalette.hair2, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                QuietText("Cancel", 14, EspressoPalette.dim, FontWeight.Medium)
            }
            Box(Modifier.weight(0.58f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(accent)
                .clickable(role = Role.Button) { onSelect(selected) }, contentAlignment = Alignment.Center) {
                QuietText("Show period", 14, EspressoPalette.onColor, FontWeight.SemiBold)
            }
        }
    }
}

private val MONTH_NAMES_FULL = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

@Composable
private fun QuietDateButton(icon: QuietIconName, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp, 44.dp).semantics { contentDescription = label }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(34.dp, 36.dp).border(1.dp, QuietDepth.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            QuietIcon(icon, null, Modifier.size(17.dp), if (enabled) QuietDepth.muted else QuietDepth.subtle.copy(alpha = .6f))
        }
    }
}

/** A compact preset rail and a separate, readable calendar window. */
@Composable
private fun QuietRangeControl(
    selectedRange: TimeRangeV2,
    onRangeChange: (TimeRangeV2) -> Unit,
    windowStart: LocalDate,
    windowEnd: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChooseDate: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val start = quietDate(windowStart) + if (windowStart.year != windowEnd.year) ", ${windowStart.year}" else ""
    val period = "$start – ${quietDate(windowEnd)}, ${windowEnd.year}"
    Column(modifier.fillMaxWidth()) {
        EspressoRangeTrack(
            TimeRangeV2.entries, selectedRange, { it.label }, onRangeChange,
            description = { "Progress range ${it.label}" },
            fill = true,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietPeriodArrow(QuietIconName.Left, "Previous Progress period", windowEnd > LocalDate(2000, 1, 1), onPrevious)
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(5.dp))
                .clickable(role = Role.Button, onClick = onChooseDate)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = "Choose Progress period end date"
                    text = AnnotatedString(period)
                    onClick("Choose period end date") { onChooseDate(); true }
                }, contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = windowStart to windowEnd,
                    transitionSpec = {
                        val duration = if (reducedMotion) 0 else 180
                        val direction = if (targetState.second >= initialState.second) 1 else -1
                        (fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 5 * direction })
                            .togetherWith(fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { -it / 5 * direction })
                    }, contentAlignment = Alignment.Center, label = "Progress period dates",
                ) { (from, to) -> QuietPeriodReadout(from, to) }
            }
            QuietPeriodArrow(QuietIconName.Right, "Next Progress period", windowEnd < todayLocalDate(), onNext)
        }
    }
}

@Composable
private fun QuietPeriodReadout(start: LocalDate, end: LocalDate) {
    val crossYear = start.year != end.year
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 230.dp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
                QuietPeriodDate(start, compact)
                HorizontalDivider(Modifier.width(if (compact) 10.dp else 14.dp), color = EspressoPalette.dim)
                QuietPeriodDate(end, compact)
                if (!crossYear) {
                    Row(Modifier.padding(start = if (compact) 2.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        QuietIcon(QuietIconName.Calendar, null, Modifier.size(12.dp), EspressoPalette.dim)
                        QuietText(end.year.toString(), if (compact) 10 else 11, EspressoPalette.dim, maxLines = 1)
                    }
                }
            }
            // Keep both years legible for long windows instead of compressing the day readout.
            if (crossYear) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuietIcon(QuietIconName.Calendar, null, Modifier.size(10.dp), EspressoPalette.dim)
                QuietText("${start.year} – ${end.year}", 10, EspressoPalette.dim, maxLines = 1)
            }
        }
    }
}

@Composable
private fun QuietPeriodDate(date: LocalDate, compact: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(MONTH_ABBREV[date.month.ordinal], fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium,
            fontSize = if (compact) 11.sp else 12.sp, lineHeight = 16.sp,
            color = EspressoPalette.dim, modifier = Modifier.alignByBaseline())
        Text(date.day.toString(), fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 20.sp else 22.sp, lineHeight = 24.sp, letterSpacing = (-.4).sp,
            color = EspressoPalette.ink, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun QuietPeriodArrow(icon: QuietIconName, label: String, enabled: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Box(Modifier.size(44.dp).semantics { contentDescription = label }
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
            .background(if (pressed) EspressoPalette.panel2 else EspressoPalette.mast.copy(alpha = .45f))
            .border(1.dp, when { focused -> EspressoPalette.last; enabled -> EspressoPalette.hair2; else -> EspressoPalette.hair }, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center) {
            QuietIcon(icon, null, Modifier.size(14.dp), if (enabled) EspressoPalette.ink else EspressoPalette.faint)
        }
    }
}

@Composable
private fun QuietExerciseSearch(query: String, onQueryChange: (String) -> Unit, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val frame by animateColorAsState(
        when { focused -> EspressoPalette.back; hovered -> EspressoPalette.dim; else -> EspressoPalette.hair2 },
        tween(if (reducedMotion) 0 else 160), label = "Progress search frame",
    )
    BasicTextField(
        value = query, onValueChange = onQueryChange, singleLine = true, interactionSource = interactions,
        modifier = modifier.height(48.dp).hoverable(interactions).semantics { contentDescription = "Search exercises" },
        textStyle = EspressoType.body.copy(fontSize = 13.sp, color = EspressoPalette.ink),
        cursorBrush = SolidColor(EspressoPalette.last),
        decorationBox = { input ->
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(EspressoPalette.mast.copy(alpha = .35f))
                .border(1.dp, frame, RoundedCornerShape(6.dp))
                .padding(start = 8.dp, end = if (query.isEmpty()) 12.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    QuietIcon(QuietIconName.Search, null, Modifier.size(18.dp), if (focused) EspressoPalette.last else EspressoPalette.dim)
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Find exercise", fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium,
                        fontSize = 13.sp, lineHeight = 18.sp, color = EspressoPalette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    input()
                }
                if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Close, "Clear exercise search", Modifier.size(15.dp), tint = EspressoPalette.ink)
                }
            }
        },
    )
}

@Composable
private fun QuietSortButton(selected: ProgressSortModeV2, open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(160), label = "Sort chevron")
    Row(modifier.height(48.dp).clip(RoundedCornerShape(6.dp)).background(EspressoPalette.panel2)
        .border(1.dp, if (open) EspressoPalette.last.copy(alpha = .8f) else EspressoPalette.hair2, RoundedCornerShape(6.dp))
        .semantics(mergeDescendants = true) { contentDescription = "Sort exercises"; stateDescription = quietSortLabel(selected) }
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(Icons.Default.Sort, null, Modifier.size(16.dp), tint = EspressoPalette.dim)
        QuietText(quietSortLabel(selected), 12, EspressoPalette.ink, FontWeight.Medium)
        QuietIcon(QuietIconName.Down, null, Modifier.size(12.dp).rotate(chevron), EspressoPalette.dim)
    }
}

@Composable
private fun QuietExerciseRow(exercise: ProgressExercise, expanded: Boolean, start: LocalDate, end: LocalDate, reducedMotion: Boolean, compact: Boolean, onToggle: () -> Unit, onOpenHistory: () -> Unit) {
    val overview = exercise.overview
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Espresso: the sparkline takes the muscle colour; the delta is a filled chip.
    val muscleColor = rotationRegionColor(exercise.muscleGroups)
    val trendColor = muscleColor
    val tone = when {
        overview.deltaSign > 0 -> EspressoTone.Up
        overview.deltaSign < 0 -> EspressoTone.Down
        else -> EspressoTone.Flat
    }
    Column {
        Row(Modifier.fillMaxWidth().background(if (pressed) QuietDepth.accent.copy(alpha = .025f) else Color.Transparent)
            .semantics(mergeDescendants = true) {
                contentDescription = "${exercise.name}, ${overview.deltaLabel}, ${overview.metricLabel}"
                stateDescription = if (expanded) "Comparison expanded" else "Comparison collapsed"
            }.clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = "Show comparison", onClick = onToggle)
            .padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            EspressoMuscleDot(muscleColor)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 15.sp, lineHeight = 18.sp, letterSpacing = (-0.2).sp, color = EspressoPalette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    QuietText(progressMuscleGroupLabel(exercise.muscleGroups) + " · " + (overview.latest?.evidence ?: "No sets"), 11, EspressoPalette.dim, maxLines = 2)
                    if (overview.isRecord) QuietText("· ${overview.recordLabel}", 11, EspressoPalette.up)
                }
            }
            QuietTrend(overview.points, start, end, Modifier.width(if (compact) 52.dp else 60.dp).height(26.dp), trendColor)
            Column(Modifier.widthIn(min = if (compact) 60.dp else 70.dp), horizontalAlignment = Alignment.End) {
                if (overview.points.size < 2) {
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(EspressoPalette.panel2).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        QuietText(overview.deltaLabel, 11, EspressoPalette.dim, FontWeight.Medium, maxLines = 1)
                    }
                } else {
                    EspressoDeltaChip(overview.deltaLabel, tone)
                }
                QuietText(overview.metricLabel, 10, EspressoPalette.faint, modifier = Modifier.padding(top = 5.dp), align = TextAlign.End, maxLines = 1)
            }
            QuietIcon(QuietIconName.Right, null, Modifier.size(10.dp).rotate(if (expanded) 90f else 0f), EspressoPalette.faint)
        }
        val duration = if (reducedMotion) 0 else 230
        AnimatedVisibility(expanded, enter = expandVertically(tween(duration)) + fadeIn(tween(duration)), exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                HorizontalDivider(color = QuietDepth.line.copy(alpha = .5f), thickness = 1.dp)
                val report = exercise.report
                val isBodyweight = exercise.isBodyweight
                if (report.events.isEmpty()) {
                    EspressoEyebrow("No sets in range", modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                } else {
                    Column(Modifier.padding(top = 6.dp)) {
                        report.events.take(4).forEachIndexed { i, event ->
                            val isPr = event.kind == ChangelogKind.REP_PR || event.kind == ChangelogKind.WEIGHT_AT_REPS
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(shortDate(event.date).uppercase(), style = EspressoType.eyebrow.copy(letterSpacing = 1.2.sp), color = EspressoPalette.faint, modifier = Modifier.width(52.dp))
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    val headline = when (event.kind) {
                                        ChangelogKind.MOVED_UP, ChangelogKind.MOVED_DOWN -> "${formatTopSet(event.from!!, isBodyweight)} → ${formatTopSet(event.to, isBodyweight)}"
                                        else -> formatTopSet(event.to, isBodyweight)
                                    }
                                    Text(headline, fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, color = if (isPr) muscleColor else EspressoPalette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val rest = event.otherSets()
                                    if (event.kind != ChangelogKind.MOVED_UP && event.kind != ChangelogKind.MOVED_DOWN && rest.isNotEmpty()) DataText(rest.flatMap { listOf(filler(" · "), data(supportingSetLabel(it, event.to, isBodyweight))) }, size = 11.sp, filler = EspressoPalette.faint, data = EspressoPalette.dim, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
                                }
                                when {
                                    event.deltaLabel.isNotEmpty() -> Text(
                                        text = event.deltaLabel + if (isPr) " · PR" else "",
                                        fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, lineHeight = 13.sp,
                                        color = when { event.kind == ChangelogKind.MOVED_DOWN || event.deltaLabel.startsWith("−") -> EspressoPalette.down; event.kind == ChangelogKind.MOVED_UP -> muscleColor; event.kind == ChangelogKind.HELD -> EspressoPalette.dim; else -> EspressoPalette.up },
                                    )
                                    event.kind == ChangelogKind.HELD -> QuietText("Held", 11, EspressoPalette.dim)
                                    else -> DataText(event.aside, size = 11.sp, filler = EspressoPalette.dim, data = EspressoPalette.ink)
                                }
                            }
                            if (i < minOf(3, report.events.lastIndex)) HorizontalDivider(color = QuietDepth.line.copy(alpha = .5f), thickness = 1.dp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clearAndSetSemantics { contentDescription = "Full report"; role = Role.Button }.clickable(onClick = onOpenHistory), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuietText("Full report", 12, muscleColor, FontWeight.Medium)
                        QuietIcon(QuietIconName.Arrow, null, Modifier.size(16.dp), muscleColor)
                    }
                    val next = report.nextStep
                    val aside = when {
                        next != null -> listOf(data("${formatLoad(next.targetLoadStored, isBodyweight)}${if (isBodyweight) "" else " lb"}"), filler(" at "), data("${formatLoad(next.currentLoadStored, isBodyweight)} × ${next.triggerReps}"))
                        report.stepsInWindow > 0 -> listOf(filler("moved up "), data("${report.stepsInWindow}×"), filler(" · last "), data(shortDate(report.events.first { it.kind == ChangelogKind.MOVED_UP }.date)))
                        else -> emptyList()
                    }
                    if (aside.isNotEmpty()) DataText(aside, size = 11.5.sp, filler = EspressoPalette.faint, data = EspressoPalette.dim, maxLines = 1)
                }
            }
        }
        HorizontalDivider(color = QuietDepth.line, thickness = 1.dp)
    }
}

@Composable
private fun QuietTrend(points: List<QuietProgressPoint>, start: LocalDate, end: LocalDate, modifier: Modifier, color: Color) {
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val pad = 5.dp.toPx()
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        val span = max - min
        val locations = points.map { point ->
            Offset(pad + (size.width - pad * 2) * progressDateFraction(LocalDate.parse(point.date), start, end),
                if (span <= .0001f) size.height / 2 else size.height - pad - (point.value - min) / span * (size.height - pad * 2))
        }
        if (locations.size > 1) {
            val path = Path().apply { locations.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }
        locations.forEachIndexed { index, point ->
            val latest = index == locations.lastIndex
            if (latest) drawCircle(color.copy(alpha = .20f), 4.5.dp.toPx(), point)
            drawCircle(color.copy(alpha = if (latest) 1f else .85f), if (latest) 2.8.dp.toPx() else 1.35.dp.toPx(), point)
        }
    }
}

/** What a screen reader and the QA harness read for a held row. */
internal fun heldRowDescription(row: HeldLiftRowUi): String = buildList {
    add("Open ${row.name} report")
    row.topSetLabel?.let(::add)
    row.stake?.let { add(stakeText(it)) }
    if (row.faded) add("faded")
}.joinToString(", ")

@Composable
private fun QuietScopePill(label: String, description: String, trailingArrow: Boolean = false, compact: Boolean = false,
    onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(EspressoPalette.panel2)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = if (compact) 10.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QuietText(label, 12, EspressoPalette.ink, FontWeight.Medium)
            if (trailingArrow) QuietPillChevron(EspressoPalette.ink)
        }
    }
}

/**
 * The pill's arrow, drawn rather than loaded: the `quiet_right` resource never painted inside these
 * pills, so each label sat left of centre beside an empty 14dp slot. The box hugs the stroke
 * (`quiet_right.svg`'s 3.5×7dp chevron at 14dp) so the pill's padding is the only space on either side.
 */
@Composable
private fun QuietPillChevron(color: Color) {
    Canvas(Modifier.size(width = 5.dp, height = 14.dp)) {
        val stroke = 1.6.dp.toPx()
        val left = stroke / 2
        val right = size.width - stroke / 2
        val chevron = Path().apply {
            moveTo(left, size.height / 2 - (right - left)); lineTo(right, size.height / 2); lineTo(left, size.height / 2 + (right - left))
        }
        drawPath(chevron, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------------------------
// Shader warm-up (see ShaderWarmup)
// ---------------------------------------------------------------------------------------------

private const val PROGRESS_WARMUP = "progress-tab"
private const val PROGRESS_WARMUP_DELAY_MS = 4_000L

/** 768dp reaches the second header; its four tile rows leave enough content to pass the top edge. */
private const val PROGRESS_SWEEP_FRAMES = 64
private const val PROGRESS_SWEEP_STEP_DP = 12f

/**
 * Compiles the Progress tab's shaders while nothing is moving (see [ShaderWarmup]): the real
 * screen with made-up lifts, behind the app's opaque background. It is drawn in the tab
 * transition's pose (the first visit), both the held-lifts landing and the library behind "All
 * lifts", and where [warmupSweepsAllowed] the landing also scrolls top to bottom so the header's
 * rounded pieces and the tiles cross the list's edges (the first scroll). Nothing here reads a view
 * model or changes anything: every callback is a no-op.
 */
@Composable
internal fun ProgressTabWarmup(bottomReserved: Dp) {
    val sweep = warmupSweepsAllowed
    TabWarmupShell(PROGRESS_WARMUP, PROGRESS_WARMUP_DELAY_MS, if (sweep) PROGRESS_SWEEP_FRAMES + 3 else TAB_ARRIVAL_FRAMES, bottomReserved) {
        val sample = remember { warmupProgressSample() }
        TabArrival { ProgressWarmupScreen(sample, showAllLifts = false, listState = rememberLazyListState()) }
        TabArrival { ProgressWarmupScreen(sample, showAllLifts = true, listState = rememberLazyListState()) }
        if (sweep) {
            val heldList = rememberLazyListState()
            WarmupSweep(heldList, PROGRESS_SWEEP_STEP_DP, PROGRESS_SWEEP_FRAMES)
            Box(Modifier.fillMaxSize()) { ProgressWarmupScreen(sample, showAllLifts = false, listState = heldList) }
        }
    }
}

@Composable
private fun ProgressWarmupScreen(sample: ProgressWarmupSample, showAllLifts: Boolean, listState: LazyListState) {
    QuietProgressOverviewScreen(
        exercises = sample.exercises,
        selectedRange = TimeRangeV2.ONE_MONTH,
        onRangeChange = {},
        windowStart = sample.windowStart,
        windowEnd = sample.windowEnd,
        onWindowEndChange = {},
        searchQuery = "",
        onSearchChange = {},
        sortMode = ProgressSortModeV2.TREND,
        onSortModeChange = {},
        onExerciseClick = {},
        onOrganizationModeChange = {},
        onOpenHighlights = {},
        groupWall = sample.wall,
        heldAvailable = true,
        showAllLifts = showAllLifts,
        onShowAllLiftsChange = {},
        onOpenReport = {},
        listState = listState,
    )
}

private class ProgressWarmupSample(
    val wall: GroupWallUi,
    val exercises: List<ProgressExercise>,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
)

/**
 * Demo-shaped panels: Push + Athletic leads, Upper Strength stays expanded, Pull + Posterior
 * folds. Both win colours, BW, a single session and an active tail use the real projection.
 * The second panel's four rows let its header pass the top edge without clamping the sweep early.
 * Fourteen library lifts cover the library's existing draws.
 */
private fun warmupProgressSample(): ProgressWarmupSample {
    val end = todayLocalDate()
    val start = progressWindowStart(TimeframeFilter.ONE_MONTH, end)
    fun held(id: Long, name: String, group: String, top: String?, stake: StakeUi?, faded: Boolean = false) =
        HeldLiftRowUi(id, name, group, top, stake, faded)
    val heldSections = listOf(
        HeldSectionUi("Push + Athletic", HeldSectionKind.GROUP, 3, listOf(
            held(1, "Overhead Press", "Shoulders", "135×8", StakeUi.Beat(8)),
            held(4, "Close-Grip Bench", "Triceps", "185×8", StakeUi.Beat(8)),
            held(6, "Hanging Knee Raise", "Abs", "BW×8", StakeUi.RepsToRecord(1)),
            held(14, "Farmer Carry", "Forearms", "50×8", null),
            held(3, "Bulgarian Split Squat", "Quads", "40×8", null, faded = true),
            held(19, "Hip Thrust", "Glutes", "315×8", null, faded = true),
        )),
        HeldSectionUi("Upper Strength", HeldSectionKind.GROUP, 0, listOf(
            held(7, "Barbell Bench Press", "Chest", "225×8", StakeUi.Beat(8)),
            held(8, "Incline Dumbbell Press", "Chest", "55×8", StakeUi.Beat(8)),
            held(9, "Pull Up", "Back", "BW+25×8", null),
            held(2, "Barbell Row", "Back", "135×8", null),
            held(15, "Seated Dumbbell Shoulder Press", "Shoulders", "45×8", null),
            held(16, "Cable Triceps Pressdown", "Triceps", "60×8", null),
            held(17, "Dip", "Triceps", "BW×8", null),
            held(18, "Lat Pulldown", "Back", "140×8", null),
        )),
        HeldSectionUi("Pull + Posterior", HeldSectionKind.GROUP, 1, listOf(
            held(10, "Deadlift", "Back", "315×8", null),
        )),
    )
    fun lift(id: Long, name: String, group: String, values: List<Float>, delta: String, sign: Int, record: Boolean = false): ProgressExercise {
        val points = values.mapIndexed { i, value ->
            QuietProgressPoint(end.minusDays((values.size - 1 - i) * 6L).toString(), value, "${value.toInt()} lb × 5")
        }
        return ProgressExercise(
            name = name, muscleGroups = group, lastSetLabel = "", trendPercent = 0f, sessions = values.size, id = id,
            overview = QuietProgressOverview(
                points = points, deltaLabel = delta, deltaSign = sign, latest = points.lastOrNull(), isRecord = record,
            ),
        )
    }
    val exercises = listOf(
        lift(1, "Back Squat", "Quads", listOf(285f, 290f, 296f, 301f, 308f), "+8%", 1, record = true),
        lift(2, "Bench Press", "Chest", listOf(245f, 244f, 250f, 249f, 252f), "+3%", 1),
        lift(3, "Deadlift", "Back", listOf(340f, 345f, 341f, 338f), "−2%", -1),
        lift(4, "Overhead Press", "Shoulders", listOf(150f, 150f, 150f), "Steady", 0),
        lift(5, "Romanian Deadlift", "Hamstrings", listOf(260f, 268f, 275f, 281f), "+8%", 1),
        lift(6, "Lat Pulldown", "Back", listOf(165f, 170f, 172f, 178f, 181f), "+10%", 1),
        lift(7, "Leg Press", "Quads", listOf(520f, 540f, 525f, 560f), "+8%", 1),
        lift(8, "Seated Row", "Back", listOf(150f, 148f, 152f), "+1%", 1),
        lift(9, "Incline Dumbbell Press", "Chest", listOf(95f, 92f, 90f), "−5%", -1),
        lift(10, "Bicep Curl", "Biceps", listOf(48f, 50f, 52f), "+8%", 1),
        lift(11, "Tricep Pushdown", "Triceps", listOf(88f, 90f, 90f, 95f), "+8%", 1),
        lift(12, "Hip Thrust", "Glutes", listOf(360f), "1 session", 0),
        lift(13, "Face Pull", "Shoulders", listOf(62f, 64f, 66f), "+6%", 1),
        lift(14, "Cable Crunch", "Abs", listOf(110f, 110f, 110f), "Steady", 0),
    )
    // Run the real tile projection once, outside the draw/composition hot loops. This covers
    // rising/flat/falling paths, hollow and solid dots, BW, both wins, no win and a tail.
    val wallCatalog = heldSections.flatMap { it.rows }.map { row ->
        com.ayman.ecolift.data.Exercise(row.exerciseId, row.name, row.primaryGroup.orEmpty(), row.exerciseId in listOf(6L, 9L, 17L))
    }
    val wallSets = wallCatalog.flatMap { exercise ->
        val historical = exercise.id == 10L || exercise.id == 19L
        val sessions = if (exercise.id == 14L || historical) 1 else if (exercise.id == 7L) 3 else 4
        (0 until sessions).map { i ->
            val weight = when (exercise.id) {
                1L -> 1350 + i * 50 // Load win (ink chip).
                4L -> 1850 // Rep win (green chip), beside the load win.
                6L -> 0 // Bodyweight and a rep win.
                14L -> 500 // Single session, one solid dot, no chip.
                7L -> 2250 - i * 50 // Falling path.
                8L -> 550 // Flat path with a rep win.
                9L -> 250 // Added-load bodyweight tile.
                2L -> 1350 // Flat path with no win.
                3L -> 400 // A faded lift with sessions in the tail.
                10L, 19L -> 3150 // Folded group / omitted zero-session tail.
                15L -> 450
                16L -> 600
                17L -> 0
                18L -> 1400
                else -> 500
            }
            com.ayman.ecolift.data.WorkoutSet(exerciseId = exercise.id,
                date = (if (historical) start.minusDays(4) else
                    end.minusDays((sessions - 1 - i) * 6L + if (exercise.id in listOf(1L, 4L, 6L, 14L, 3L)) 0 else 2)).toString(),
                setNumber = 1,
                weightLbs = weight, reps = if (exercise.id % 4 == 0L || exercise.id == 6L) 8 + i else 8,
                completed = true, isBodyweight = exercise.isBodyweight)
        }
    }
    val wall = buildGroupWall(GroupWallSource(sections = heldSections, exercises = wallCatalog, sets = wallSets),
        start, end, { it })
    return ProgressWarmupSample(wall, exercises, start, end)
}

private val ProgressTabLabels = listOf("Highlights", "Splits", "Month")

/**
 * The Progress tab's one header: Highlights, Splits and Month all draw it, so the title never changes size
 * between views. [sheet] swaps in the Month sheet's theme; the geometry is identical in both, so opening
 * Month changes the look without moving the title or the tabs.
 */
@Composable
internal fun ProgressMasthead(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    sheet: Boolean = false,
) {
    val side = if (compact) 17.dp else 22.dp
    // The sheet paints under the status bar too; the Espresso panel starts below it.
    val panel = if (sheet) Modifier.background(SheetBg).statusBarsPadding()
        else Modifier.statusBarsPadding().clip(EspressoShape.masthead).background(EspressoPalette.mast)
    Column(
        modifier.fillMaxWidth().then(panel)
            .padding(start = side, end = side, top = 20.dp, bottom = if (sheet) 0.dp else 12.dp),
    ) {
        // Clash at 42/44 lays out 48 dp tall and Fraunces at 44/44 lays out 55, so both titles sit in one 48 dp slot
        // (Fraunces' descender hangs into the gap) and the tabs land on the same line in either theme.
        val title = Modifier.height(48.dp).wrapContentHeight(Alignment.Top, unbounded = true).semantics { heading() }
        if (sheet) {
            Text("Progress", style = sheetSerif(44.0, SheetInk, lineHeight = 44.sp, letterSpacing = (-0.8).sp),
                modifier = title)
            SheetProgressTabs(ProgressTabLabels, selectedIndex, onSelect, Modifier.padding(top = 12.dp))
        } else {
            Text("Progress", style = EspressoType.mastTitle.copy(fontSize = 42.sp, lineHeight = 44.sp),
                color = EspressoPalette.back, modifier = title)
            EspressoTabPills(
                labels = ProgressTabLabels,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                modifier = Modifier.padding(top = 12.dp),
                fill = true,
            )
        }
    }
}
