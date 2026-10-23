package com.ayman.ecolift.ui.hub

import com.ayman.ecolift.ui.navigation.formatMonthDay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.hub.BacklogItem
import com.ayman.ecolift.data.hub.HubBuild
import com.ayman.ecolift.data.hub.HubDay
import com.ayman.ecolift.data.hub.HubDayKind
import com.ayman.ecolift.data.hub.HubItem
import com.ayman.ecolift.data.hub.UncheckedSetsItem
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.SheetSansFontFamily
import com.ayman.ecolift.ui.navigation.rotationRegionColor
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogType
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.web.util.MONTH_ABBREV
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * Swiping mirrors the Log screen's exercise card: right finishes, left removes. Opening an item is
 * a tap, so there is no third direction to learn.
 */
enum class HubSwipe { Confirm, Clear }

fun hubDayQualifier(kind: HubDayKind): String = when (kind) {
    HubDayKind.Trained -> "you trained"
    HubDayKind.NeverVerified -> "never verified"
}

fun hubItemTag(item: HubItem): String = when (item) {
    is UncheckedSetsItem -> {
        val count = item.openSets.size
        if (count == 1) "1 SET OPEN" else "$count SETS OPEN"
    }
    is BacklogItem -> "BACKLOG"
}

fun hubItemHeadline(item: HubItem): String = when (item) {
    is UncheckedSetsItem -> item.exerciseName
    is BacklogItem -> {
        val setWord = if (item.setCount == 1) "set" else "sets"
        val dayWord = if (item.dayCount == 1) "day" else "days"
        "${item.setCount} $setWord across ${item.dayCount} $dayWord"
    }
}

/**
 * States the checked ratio rather than describing the session, because the ratio is the whole point:
 * only checked sets count, so "1 of 3" says exactly how much of that exercise currently exists.
 */
fun hubItemSupportingParts(item: HubItem): List<DataPart> = when (item) {
    is UncheckedSetsItem -> {
        val checked = item.referenceSets.size
        if (checked == 0) {
            listOf(data("0"), filler(" checked"))
        } else {
            listOf(data("$checked"), filler(" of "), data("${checked + item.openSets.size}"), filler(" checked"))
        }
    }
    is BacklogItem -> listOf(data("0"), filler(" checked"))
}

fun hubItemSupporting(item: HubItem): String = hubItemSupportingParts(item).joinToString("") { it.text }

fun hubItemMeta(item: HubItem): String = when (item) {
    is UncheckedSetsItem -> item.muscleGroups.uppercase()
    is BacklogItem -> "OLDEST ${formatMonthDay(item.fromDate).uppercase()}"
}

/** The tint each item type carries, used for its tag and its spine. */
private fun hubItemTint(item: HubItem, palette: GlassPalette): Color = when (item) {
    is UncheckedSetsItem -> EspressoPalette.down
    is BacklogItem -> EspressoPalette.arms
}

/**
 * The notification stack.
 *
 * Horizontal insets deliberately come from the hosting list's contentPadding, not from here — the
 * slips have to line up with everything else in that list.
 */
@Composable
fun HubList(
    build: HubBuild,
    expandedItemId: String?,
    onToggleExpand: (String) -> Unit,
    onSwipe: (item: HubItem, direction: HubSwipe) -> Unit,
    expandedContent: @Composable (HubItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        build.days.forEachIndexed { index, day ->
            DayUmbrella(day = day, isFirst = index == 0)
            day.items.forEach { item ->
                HubSlip(
                    item = item,
                    isExpanded = expandedItemId == item.id,
                    onToggleExpand = { onToggleExpand(item.id) },
                    onSwipe = { direction -> onSwipe(item, direction) },
                    expandedContent = expandedContent,
                )
            }
        }
        if (build.backlog != null) {
            BacklogDayUmbrella(
                backlog = build.backlog,
                isFirst = build.days.isEmpty(),
            )
            HubSlip(
                item = build.backlog,
                isExpanded = expandedItemId == build.backlog.id,
                onToggleExpand = { onToggleExpand(build.backlog.id) },
                onSwipe = { direction -> onSwipe(build.backlog, direction) },
                expandedContent = expandedContent,
            )
        }
    }
}

/** Shared chrome for the two umbrella variants: date, hairline, qualifier. */
@Composable
private fun UmbrellaRow(
    date: String,
    qualifier: String,
    isFirst: Boolean,
    modifier: Modifier = Modifier,
    qualifierColor: Color = EspressoPalette.faint,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = if (isFirst) 6.dp else 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = date,
            color = EspressoPalette.ink,
            fontFamily = LogDisplayFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(EspressoPalette.hair2, Color.Transparent),
                    ),
                ),
        )
        Text(
            text = qualifier,
            color = qualifierColor,
            fontFamily = SheetSansFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp,
        )
    }
}

@Composable
private fun DayUmbrella(day: HubDay, isFirst: Boolean, modifier: Modifier = Modifier) {
    UmbrellaRow(
        date = formatDayHeader(day.date),
        qualifier = hubDayQualifier(day.kind).uppercase(),
        isFirst = isFirst,
        modifier = modifier,
    )
}

@Composable
private fun BacklogDayUmbrella(backlog: BacklogItem, isFirst: Boolean, modifier: Modifier = Modifier) {
    UmbrellaRow(
        date = formatBacklogRange(backlog.fromDate, backlog.toDate),
        qualifier = "TOO OLD TO RECALL",
        isFirst = isFirst,
        modifier = modifier,
    )
}

@Composable
private fun HubSlip(
    item: HubItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSwipe: (HubSwipe) -> Unit,
    expandedContent: @Composable (HubItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetX = remember(item.id) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 96.dp.toPx() }
    val shape = RoundedCornerShape(if (isExpanded) 18.dp else 16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        ActionBed(
            item = item,
            offsetX = offsetX.value,
            thresholdPx = thresholdPx,
            shape = shape,
            modifier = Modifier.matchParentSize(),
        )
        SlipCard(
            item = item,
            isExpanded = isExpanded,
            offsetX = offsetX.value,
            shape = shape,
            onToggleExpand = onToggleExpand,
            expandedContent = expandedContent,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value > thresholdPx) {
                                    offsetX.animateTo(thresholdPx * 4f, tween(200))
                                    onSwipe(HubSwipe.Confirm)
                                } else if (offsetX.value < -thresholdPx) {
                                    offsetX.animateTo(-thresholdPx * 4f, tween(200))
                                    onSwipe(HubSwipe.Clear)
                                } else {
                                    offsetX.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val next = offsetX.value + dragAmount
                                // Adding a split needs a name typed first, so a right swipe on a
                                // smart split can never register as a confirmation: the card simply
                                // cannot travel past centre in that direction.
                                offsetX.snapTo(next)
                            }
                        },
                    )
                },
        )
    }
}

/**
 * The bed behind a slip. It returns before drawing anything while the card is at rest — the slip is
 * translucent, so any resting tint or label would ghost straight through it. Same contract as the
 * Log screen's SwipeActionBackground.
 */
@Composable
private fun ActionBed(
    item: HubItem,
    offsetX: Float,
    thresholdPx: Float,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    if (offsetX == 0f || thresholdPx <= 0f) return
    val swipingRight = offsetX > 0f
    val progress = (abs(offsetX) / thresholdPx).coerceIn(0f, 1f)
    // Espresso: the bed fills with the meaning colour as you swipe; text is the dark ink on it.
    val color = if (swipingRight) EspressoPalette.up else EspressoPalette.down
    val clearLabel = "Clear"
    val tint = EspressoPalette.onColor.copy(alpha = progress)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(color.copy(alpha = 0.92f * progress)),
        contentAlignment = if (swipingRight) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (swipingRight) {
                Icon(Icons.Default.Check, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Text(
                    text = "Check",
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = clearLabel,
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(Icons.Default.Close, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SlipCard(
    item: HubItem,
    isExpanded: Boolean,
    offsetX: Float,
    shape: RoundedCornerShape,
    onToggleExpand: () -> Unit,
    expandedContent: @Composable (HubItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val tint = hubItemTint(item, palette)
    // Unchecked sets open into a workout card. A backlog item is resolved by swiping, so it gets no
    // chevron and no tap target rather than expanding into an empty divider.
    val isExpandable = item is UncheckedSetsItem
    val interactionSource = remember { MutableInteractionSource() }
    // Resting slips sit one tier below the Log card so the hub reads as a quieter surface; opening
    // one promotes it to the same Active tier the Log card uses, because it now IS that card.
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "hub_slip_chevron",
    )

    Column(
        modifier = modifier
            .graphicsLayer { translationX = offsetX }
            .clip(shape)
            .background(EspressoPalette.panel)
            .then(if (isExpanded) Modifier.border(1.5.dp, EspressoPalette.hair2, shape) else Modifier)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(tint, tint.copy(alpha = 0.25f)),
                    ),
                    topLeft = Offset.Zero,
                    size = Size(4.dp.toPx(), size.height),
                )
            }
            .then(
                if (isExpandable) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) {
                        onToggleExpand()
                    }
                } else {
                    Modifier
                }
            )
            .animateContentSize(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HubItemTagView(item = item, tint = tint)
                    when (item) {
                        is UncheckedSetsItem -> {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(rotationRegionColor(item.muscleGroups)),
                            )
                        }
                        else -> Unit
                    }
                    Text(
                        text = hubItemMeta(item),
                        color = EspressoPalette.dim,
                        fontFamily = SheetSansFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hubItemHeadline(item),
                    style = LogType.exerciseTitle.copy(fontSize = 18.sp, lineHeight = 21.sp),
                    color = palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                DataText(
                    parts = hubItemSupportingParts(item),
                    size = 12.5.sp,
                    lineHeight = 17.sp,
                    filler = EspressoPalette.dim,
                    data = EspressoPalette.ink,
                )
            }
            if (isExpandable) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = EspressoPalette.faint,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }

        if (isExpanded) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(EspressoPalette.hair),
            )
            Spacer(Modifier.height(12.dp))
            expandedContent(item)
        }
    }
}

@Composable
private fun HubItemTagView(item: HubItem, tint: Color) {
    val palette = LocalGlassPalette.current
    val tagShape = RoundedCornerShape(7.dp)
    Text(
        text = hubItemTag(item),
        color = tint,
        fontFamily = SheetSansFontFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .clip(tagShape)
            .background(EspressoPalette.tint(tint), tagShape)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

internal fun formatDayHeader(dateStr: String): String {
    val ld = runCatching { LocalDate.parse(dateStr) }.getOrNull()
    return if (ld != null) {
        val monthStr = MONTH_ABBREV.getOrNull(ld.month.ordinal) ?: ld.month.name.take(3)
        "${ld.day} $monthStr"
    } else {
        dateStr
    }
}

private fun formatBacklogRange(fromDate: String, toDate: String): String {
    val fromLd = runCatching { LocalDate.parse(fromDate) }.getOrNull()
    val toLd = runCatching { LocalDate.parse(toDate) }.getOrNull()
    return if (fromLd != null && toLd != null) {
        val fromMonth = MONTH_ABBREV.getOrNull(fromLd.month.ordinal) ?: fromLd.month.name.take(3)
        val toMonth = MONTH_ABBREV.getOrNull(toLd.month.ordinal) ?: toLd.month.name.take(3)
        if (fromMonth == toMonth) fromMonth else "$fromMonth - $toMonth"
    } else {
        "BACKLOG"
    }
}

/** "4 Aug – 12 Sep": a smart split's claimed range, day-then-month at both ends. */
fun formatHubDateRange(fromDate: String, toDate: String): String {
    val fromLd = runCatching { LocalDate.parse(fromDate) }.getOrNull()
    val toLd = runCatching { LocalDate.parse(toDate) }.getOrNull()
    return if (fromLd != null && toLd != null) {
        "${formatDayHeader(fromDate)} – ${formatDayHeader(toDate)}"
    } else {
        "$fromDate – $toDate"
    }
}
