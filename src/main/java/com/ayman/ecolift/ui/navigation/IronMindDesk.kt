package com.ayman.ecolift.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.*
import com.ayman.ecolift.ui.viewmodel.AskedUi
import com.ayman.ecolift.ui.viewmodel.DeskDecisionUi
import com.ayman.ecolift.ui.viewmodel.NoticedKind
import com.ayman.ecolift.ui.viewmodel.NoticedUi

@Composable
internal fun DeskSection(label: String, accent: Boolean = false, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (accent) Box(Modifier.size(6.dp).clip(CircleShape).background(EspressoPalette.arms))
        Text(label, style = EspressoType.eyebrow, color = if (accent) EspressoPalette.arms else EspressoPalette.dim,
            modifier = Modifier.semantics { contentDescription = if (trailing != null) "$label, $trailing" else label })
        Box(Modifier.weight(1f).height(1.dp).background(EspressoPalette.hair))
        if (trailing != null) {
            Text(trailing, style = EspressoType.eyebrow, color = EspressoPalette.dim, maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Same filled/hollow 8dp dots and 20dp session cells as the slot swap sheet. */
@Composable
internal fun SessionUsageDots(dots: List<Boolean>, color: Color) {
    Row {
        dots.forEach { used ->
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(8.dp).clip(CircleShape)
                    .then(if (used) Modifier.background(color) else Modifier.border(1.5.dp, EspressoPalette.hair2, CircleShape)))
            }
        }
    }
}

@Composable
internal fun DeskDecisionCard(ui: DeskDecisionUi, onKeep: () -> Unit, onRetire: () -> Unit, onOpen: () -> Unit) {
    val overlap = ui.overlapsName != null
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EspressoPalette.panel)
        .drawBehind { drawRect(EspressoPalette.arms, Offset.Zero, Size(3.dp.toPx(), size.height)) }
        .then(if (overlap) Modifier.clickable(enabled = ui.slotId > 0, role = Role.Button, onClick = onOpen) else Modifier)
        .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (overlap) "OVERLAPS ${ui.overlapsName}" else "KEEP AS A SPLIT?", style = EspressoType.eyebrow,
                color = EspressoPalette.dim, modifier = Modifier.weight(1f))
            if (!overlap) Text("○ DETECTED", style = EspressoType.eyebrow.copy(fontSize = 9.sp), color = EspressoPalette.dim,
                modifier = Modifier.semantics { contentDescription = "DETECTED" }
                    .border(1.dp, EspressoPalette.hair2, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
            else Text("›", color = EspressoPalette.dim, fontSize = 24.sp)
        }
        Text(ui.title, style = EspressoType.name.copy(fontSize = 22.sp, lineHeight = 26.sp), color = EspressoPalette.ink)
        Text(ui.evidence, style = EspressoType.body, color = EspressoPalette.dim)
        if (!overlap) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(EspressoPalette.hair))
            // Scroll the whole table so long histories keep their labels and dots aligned.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tableWidth = maxOf(maxWidth, (140 + ui.sessions.size * 20).dp)
                Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.width((ui.sessions.size * 20).dp)
                            .semantics { contentDescription = "Split sessions, ${ui.sessions.joinToString { "${it.month} ${it.day}" }}" }) {
                            ui.sessions.forEachIndexed { index, session ->
                                Column(Modifier.width(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (index == 0 || ui.sessions[index - 1].month != session.month) session.month else "",
                                        color = EspressoPalette.dim, fontSize = 8.sp)
                                    Text(session.day, color = EspressoPalette.dim, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                    ui.lifts.forEach { lift ->
                        Row(Modifier.fillMaxWidth().semantics {
                            contentDescription = "${lift.name}, ${lift.sessionDots.count { it }} of ${ui.sessions.size} sessions"
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Text(lift.name, fontFamily = LogUiFontFamily, fontSize = 12.sp, lineHeight = 15.sp,
                                color = EspressoPalette.dim, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            SessionUsageDots(lift.sessionDots, rotationRegionColor(lift.muscleGroups))
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeskAction("Keep and name it", true, ui.slotId > 0, onKeep)
                DeskAction("Retire", false, ui.slotId > 0, onRetire)
            }
            if (ui.slotId <= 0) EspressoEyebrow("Syncing")
        }
    }
}

@Composable
internal fun DeskAction(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
        .background(if (primary) EspressoPalette.arms else EspressoPalette.panel2)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            color = if (primary) EspressoPalette.onColor else EspressoPalette.ink)
    }
}

@Composable
internal fun DeskNoticedRow(ui: NoticedUi, onOpen: () -> Unit, onAsk: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen)
        .semantics { contentDescription = "Open ${ui.name} report, ${ui.loadLabel}, ${ui.evidence}" }
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val color = rotationRegionColor(ui.muscleGroups)
        Canvas(Modifier.size(48.dp, 28.dp)) {
            fun point(index: Int) = Offset(3.dp.toPx() + ui.trend[index].x * (size.width - 6.dp.toPx()),
                size.height - 3.dp.toPx() - ui.trend[index].y * (size.height - 6.dp.toPx()))
            val path = Path()
            ui.trend.indices.forEach { index -> val p = point(index); if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (ui.trend.isNotEmpty()) drawCircle(color, 3.dp.toPx(), point(ui.trend.lastIndex))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(ui.name, style = EspressoType.name.copy(fontSize = 16.sp, lineHeight = 19.sp), color = EspressoPalette.ink)
            Text(if (ui.kind == NoticedKind.FlatLoad) "${ui.loadLabel} · ${ui.evidence}" else ui.evidence,
                fontFamily = LogUiFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = EspressoPalette.dim)
            ui.question?.let { question ->
                Text("Ask why", fontFamily = LogUiFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = EspressoPalette.ink,
                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, EspressoPalette.arms, RoundedCornerShape(50))
                        .clickable(role = Role.Button) { onAsk(question) }.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
        Text("›", fontSize = 24.sp, color = EspressoPalette.dim)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(EspressoPalette.hair))
}

@Composable
internal fun DeskAskedRow(ui: AskedUi, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EspressoPalette.arms, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ui.question, fontFamily = LogUiFontFamily, fontSize = 16.sp, lineHeight = 20.sp, color = EspressoPalette.ink)
            Text(ui.dateLabel, fontFamily = LogUiFontFamily, fontSize = 12.sp, color = EspressoPalette.dim)
        }
        Text("›", fontSize = 24.sp, color = EspressoPalette.dim)
    }
}

@Composable
internal fun DeskCleanupLine(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onToggle)
        .semantics { contentDescription = "Unchecked sets, $count, Clean up, ${if (expanded) "expanded" else "collapsed"}" }
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Unchecked sets · $count", fontFamily = LogUiFontFamily, fontSize = 13.sp, color = EspressoPalette.dim, modifier = Modifier.weight(1f))
        Text("Clean up ${if (expanded) "⌄" else "›"}", fontFamily = LogUiFontFamily, fontSize = 13.sp, color = EspressoPalette.ink)
    }
}
