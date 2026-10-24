package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.splitAccentColor
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.SplitCardMemberUi
import com.ayman.ecolift.ui.viewmodel.SplitGroupCardUi
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import kotlin.math.abs

/** The card's data line: sessions in the memory window, then the last one when there is one. */
internal fun splitCardSummary(card: SplitGroupCardUi): List<DataPart> = buildList {
    add(data("${card.sessions}"))
    add(filler(if (card.sessions == 1) " SESSION" else " SESSIONS"))
    val last = card.lastSessionDate ?: return@buildList
    add(filler(" · LAST "))
    add(data(formatMonthDay(last).uppercase()))
}

/** What a lift's tile or row says to a screen reader: the same facts the numbers and bars show. */
internal fun liftDescription(member: SplitCardMemberUi): String = buildString {
    append(member.name)
    member.metrics.topSetLabel?.let { append(", top set $it") }
    val sessions = member.metrics.workingVolume.size
    if (sessions > 0) append(", working volume over the last $sessions ${if (sessions == 1) "session" else "sessions"}")
    member.metrics.deltaPercent?.let { delta ->
        append(
            when {
                delta > 0 -> ", up $delta%"
                delta < 0 -> ", down ${abs(delta)}%"
                else -> ", unchanged"
            },
        )
    }
}

private val hairline = EspressoPalette.ink.copy(alpha = 0.09f)
private val optionInk = Color(0xFFCFC5BA)

/**
 * The Splits tab's list: one open board per learned group or
 * hand-made split in due order. Its Edit pill opens the split's editor; a group whose slot is not
 * saved yet shows its lifts but has no pill.
 */
@Composable
fun SplitGroupCardList(
    cards: List<SplitGroupCardUi>,
    onEdit: (SplitGroupCardUi) -> Unit,
    onAddSplit: () -> Unit,
    smartSuggestEnabled: Boolean,
    /** Null hides the toggle (the warmup's sample renders one that would do nothing). */
    onToggleSmartSuggest: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        onToggleSmartSuggest?.let { SplitSmartSuggestToggle(smartSuggestEnabled, it) }
        if (cards.isEmpty()) {
            Column(Modifier.padding(vertical = 18.dp)) {
                if (smartSuggestEnabled) {
                    Text("no splits yet", style = StickerType.hand(22.sp, EspressoPalette.ink))
                    DataText(
                        parts = listOf(filler("Same lifts on "), data("2 days"), filler(" → a split")),
                        size = 11.5.sp,
                        dataSize = 12.5.sp,
                        filler = EspressoPalette.dim,
                        data = EspressoPalette.ink,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Text("Smart suggest is off", style = StickerType.hand(22.sp, EspressoPalette.ink))
                    DataText(
                        parts = listOf(
                            filler("Create a split with "),
                            data("+ new split"),
                            filler(", or turn Smart suggest on."),
                        ),
                        size = 11.5.sp,
                        dataSize = 12.5.sp,
                        filler = EspressoPalette.dim,
                        data = EspressoPalette.ink,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
        }
        cards.forEach { card ->
            SplitBoard(card, onEdit)
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
        }
        Box(
            Modifier
                .padding(top = 16.dp)
                .heightIn(min = 44.dp)
                .drawBehind {
                    drawRoundRect(
                        Color(0xFF5A5047),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Add a split"; role = Role.Button }
                .clickable(onClick = onAddSplit)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ new split", style = StickerType.hand(18.sp, EspressoPalette.dim))
        }
    }
}

@Composable
private fun SplitSmartSuggestToggle(enabled: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(EspressoPalette.ink.copy(alpha = 0.05f))
            .border(1.dp, EspressoPalette.ink.copy(alpha = 0.10f), shape)
            .testTag("splits_smart_suggest_toggle")
            .semantics {
                role = Role.Switch
                contentDescription = "Smart suggest"
                stateDescription = if (enabled) "On" else "Off"
            }
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Smart suggest",
                style = StickerType.body(13.sp, EspressoPalette.ink).copy(fontWeight = FontWeight.Bold),
            )
            Text("Auto-learn splits from your workouts", style = StickerType.body(11.5.sp, EspressoPalette.dim))
        }
        Box(
            Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (enabled) EspressoPalette.legs else EspressoPalette.ink.copy(alpha = 0.15f))
                .padding(horizontal = 4.dp),
            contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(Color.White))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitBoard(card: SplitGroupCardUi, onEdit: (SplitGroupCardUi) -> Unit) {
    val accent = splitAccentColor(card.colorIndex)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = if (card.isSaved) "${card.name} split" else "${card.name} split, not saved" }
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SplitNumberTag(card.creationNumber, accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    card.name,
                    style = StickerType.display(25.sp, EspressoPalette.ink, (-0.6).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DataText(
                    parts = splitCardSummary(card),
                    size = 10.sp,
                    dataSize = 11.sp,
                    filler = EspressoPalette.dim,
                    data = EspressoPalette.ink,
                    maxLines = 1,
                )
            }
            if (card.isSaved) EditPill(card.name) { onEdit(card) }
        }
        card.members.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { LiftTile(it, accent, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (card.otherOptions.isNotEmpty()) {
            Text("OTHER OPTIONS", style = StickerType.print(10.sp, EspressoPalette.dim, 1.6.sp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                card.otherOptions.forEach { OptionPill(it, accent) }
            }
        }
    }
}

/** The split's creation number on a small tilted tag in its colour; an unsaved group has no number yet. */
@Composable
internal fun SplitNumberTag(number: Int?, accent: Color, size: Int = 30) {
    Box(
        Modifier
            .size(size.dp)
            .rotate(stickerJitter(number ?: 0).tiltDeg.coerceIn(-6f, 6f))
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(accent)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (number != null) Text("$number", style = StickerType.display((size / 2).sp, EspressoPalette.onColor, 0.sp))
    }
}

@Composable
private fun EditPill(name: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(EspressoPalette.ink.copy(alpha = 0.07f))
            .border(1.dp, EspressoPalette.ink.copy(alpha = 0.13f), shape)
            .semantics { contentDescription = "Edit $name"; role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.Edit, contentDescription = null, tint = EspressoPalette.ink, modifier = Modifier.size(14.dp))
        Text("Edit", style = StickerType.body(13.sp, EspressoPalette.ink).copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun LiftTile(member: SplitCardMemberUi, accent: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(EspressoPalette.ink.copy(alpha = 0.05f))
            .border(1.dp, EspressoPalette.ink.copy(alpha = 0.07f), shape)
            .clearAndSetSemantics { contentDescription = liftDescription(member) }
            .padding(start = 12.dp, end = 11.dp, top = 10.dp, bottom = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(member.name, style = StickerType.body(12.5.sp, EspressoPalette.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                member.metrics.topSetLabel ?: "—",
                style = StickerType.display(24.sp, EspressoPalette.ink, (-0.7).sp),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TrendBars(member.metrics.workingVolume, accent, Modifier.width(34.dp).height(20.dp))
        }
        member.metrics.deltaPercent?.let { DeltaMark(it) }
    }
}

@Composable
private fun OptionPill(member: SplitCardMemberUi, accent: Color) {
    Row(
        Modifier
            .height(32.dp)
            .drawBehind {
                drawRoundRect(
                    accent.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clearAndSetSemantics { contentDescription = liftDescription(member) }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(member.name, style = StickerType.body(12.5.sp, optionInk), maxLines = 1)
        member.metrics.topSetLabel?.let {
            Text(it, style = StickerType.body(12.sp, EspressoPalette.dim).copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

/**
 * A lift's last six sessions of working volume as six bars, newest on the right and brighter. A
 * session the lift has not had yet is an empty stub, never a zero bar. Heights scale within the
 * lift's own range so a small change still reads.
 */
@Composable
internal fun TrendBars(volume: List<Float>, accent: Color, modifier: Modifier) {
    Canvas(modifier) { drawTrendBars(volume, accent) }
}

private fun DrawScope.drawTrendBars(volume: List<Float>, accent: Color) {
    val slots = 6
    val gap = 2.dp.toPx()
    val barWidth = (size.width - gap * (slots - 1)) / slots
    val radius = CornerRadius(1.5.dp.toPx())
    val shown = volume.takeLast(slots)
    val low = shown.minOrNull() ?: 0f
    val high = shown.maxOrNull() ?: 0f
    val stub = 2.dp.toPx()
    val bright = lerp(accent, Color.White, 0.3f)
    for (slot in 0 until slots) {
        val x = slot * (barWidth + gap)
        val index = slot - (slots - shown.size)
        if (index < 0) {
            drawRoundRect(EspressoPalette.ink.copy(alpha = 0.12f), Offset(x, size.height - stub), Size(barWidth, stub), radius)
            continue
        }
        val value = shown[index]
        val fraction = when {
            high <= 0f -> 0f
            high == low -> 0.7f
            else -> 0.35f + 0.65f * (value - low) / (high - low)
        }
        val height = maxOf(stub, size.height * fraction)
        val color = if (index == shown.lastIndex) bright else accent.copy(alpha = 0.5f)
        drawRoundRect(color, Offset(x, size.height - height), Size(barWidth, height), radius)
    }
}

@Composable
internal fun DeltaMark(delta: Int, modifier: Modifier = Modifier) {
    val color = when {
        delta > 0 -> EspressoPalette.up
        delta < 0 -> EspressoPalette.down
        else -> EspressoPalette.dim
    }
    Row(modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) {
            if (delta == 0) {
                drawRect(color, Offset(0f, size.height * 0.4f), Size(size.width, size.height * 0.2f))
            } else {
                val path = Path().apply {
                    if (delta > 0) {
                        moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height)
                    } else {
                        moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height)
                    }
                    close()
                }
                drawPath(path, color)
            }
        }
        Text("${abs(delta)}%", style = StickerType.body(11.5.sp, color).copy(fontWeight = FontWeight.Bold))
    }
}
