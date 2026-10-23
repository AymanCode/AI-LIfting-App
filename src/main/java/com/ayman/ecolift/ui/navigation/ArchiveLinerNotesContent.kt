package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.MarkerFontFamily
import com.ayman.ecolift.ui.viewmodel.ArchiveTrack
import com.ayman.ecolift.ui.viewmodel.ArchiveWin

@Composable
internal fun ArchiveNotesHeading(title: String, aside: String? = null) {
    val palette = LocalGlassPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = palette.ink, fontFamily = MarkerFontFamily, fontSize = 28.sp,
            modifier = Modifier.weight(1f).semantics { contentDescription = title })
        if (aside != null) Text(aside, color = palette.inkMuted, fontSize = 11.sp)
    }
}

@Composable
internal fun ArchiveVersusHeading(window: ComparisonWindow, onWindowChange: (ComparisonWindow) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("You vs ${window.headline()}", color = LocalGlassPalette.current.ink,
            fontFamily = MarkerFontFamily, fontSize = 25.sp, modifier = Modifier.weight(1f))
        WindowPills(selected = window, onSelect = onWindowChange)
    }
}

/** Two lines for repeated tracks; one line for early hints. Both use the same visual vocabulary. */
@Composable
internal fun ArchiveTrackRow(track: ArchiveTrack, onOpen: (Long) -> Unit, compact: Boolean = false) {
    val palette = LocalGlassPalette.current
    Column(Modifier.fillMaxWidth().heightIn(min = 44.dp)
        .semantics(mergeDescendants = true) { contentDescription = track.description }
        .clickable(role = Role.Button, onClickLabel = "Open lift report") { onOpen(track.exerciseId) }
        .padding(vertical = if (compact) 8.dp else 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (track.indexLabel.isNotEmpty()) Text(track.indexLabel, color = palette.inkMuted,
                fontFamily = LogUiFontFamily, fontSize = 11.sp, modifier = Modifier.width(22.dp))
            Text(track.name, color = palette.ink, fontSize = if (compact) 11.sp else 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (track.isNew && track.indexLabel.isNotEmpty()) Text("NEW", color = palette.inkMuted,
                fontFamily = LogUiFontFamily, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            if (compact) {
                SessionBar(track.sessions, Modifier.width(24.dp).height(7.dp))
                Text("${track.sessions}", color = palette.inkMuted, fontSize = 10.sp)
                TrackWaveform(track.waveform, Modifier.width(22.dp).height(18.dp))
                if (track.topSetsLabel.isNotEmpty()) Text(track.topSetsLabel, color = palette.ink,
                    fontFamily = LogUiFontFamily, fontSize = 10.sp, maxLines = 1)
            }
            track.win?.let { ArchiveWinChip(it, compact) }
        }
        if (!compact) {
            Row(Modifier.padding(start = if (track.indexLabel.isNotEmpty()) 28.dp else 0.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SessionBar(track.sessions, Modifier.width(48.dp).height(8.dp))
                Text(track.sessionLabel, color = palette.inkMuted, fontSize = 10.sp)
                TrackWaveform(track.waveform, Modifier.width(36.dp).height(16.dp))
                Spacer(Modifier.weight(1f))
                if (track.topSetsLabel.isNotEmpty()) Text(track.topSetsLabel, color = palette.ink,
                    fontFamily = LogUiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1)
            }
            track.comparisonLabel?.let {
                Text(it, color = palette.inkMuted, fontSize = 10.sp,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = palette.glassStroke)
    }
}

/** Credits and unfolded deep cuts retain dates and raw sets without a trend verdict. */
@Composable
internal fun ArchiveCreditRow(track: ArchiveTrack, onOpen: (Long) -> Unit, credit: Boolean) {
    val palette = LocalGlassPalette.current
    val metadata = if (credit) "${track.firstLoggedLabel} · ${track.sessionLabel}"
        else "${track.sessionLabel} · ${track.dateLabel}"
    Column(Modifier.fillMaxWidth()
        .semantics(mergeDescendants = true) {
            contentDescription = "${track.name} · $metadata" +
                if (track.topSetsLabel.isEmpty()) "" else " · ${track.topSetsLabel}"
        }
        .clickable(role = Role.Button, onClickLabel = "Open lift report") { onOpen(track.exerciseId) }
        .padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(5.dp).background(palette.complete))
            Text(track.name, color = palette.ink, fontSize = 14.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (track.topSetsLabel.isNotEmpty()) Text(track.topSetsLabel, color = palette.ink,
                fontFamily = LogUiFontFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(metadata, color = palette.inkMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            track.win?.let { ArchiveWinChip(it) }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = palette.glassStroke)
    }
}

@Composable
private fun ArchiveWinChip(win: ArchiveWin, compact: Boolean = false) {
    // Board C's cream load chip and green reps chip, both with dark, legible type.
    Text(win.label, color = Color(0xFF17140F), fontWeight = FontWeight.Bold,
        fontSize = if (compact) 9.sp else 11.sp,
        modifier = Modifier.background(if (win.isReps) Color(0xFF91C565) else Color(0xFFEDE6D8),
            androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
            .padding(horizontal = if (compact) 4.dp else 7.dp, vertical = 4.dp))
}

@Composable
private fun SessionBar(sessions: Int, modifier: Modifier) {
    val color = LocalGlassPalette.current.complete
    Canvas(modifier) {
        val step = size.width / sessions.coerceAtLeast(1)
        repeat(sessions) { index ->
            drawRect(color, Offset(index * step, 0f), Size((step * .7f).coerceAtLeast(.5f), size.height))
        }
    }
}

@Composable
private fun TrackWaveform(heights: List<Float>, modifier: Modifier) {
    val color = LocalGlassPalette.current.inkMuted
    Canvas(modifier) {
        val step = size.width / heights.size.coerceAtLeast(1)
        heights.forEachIndexed { index, height ->
            if (height > 0f) drawRect(color, Offset(index * step, size.height * (1f - height)),
                Size((step * .65f).coerceAtLeast(.5f), size.height * height))
        }
    }
}
