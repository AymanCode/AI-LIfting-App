package com.ayman.ecolift.ui.highlights

import com.ayman.ecolift.web.util.countLabel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.progress.highlights.*
import com.ayman.ecolift.ui.theme.HighlightsCondensedImpactFontFamily

@Composable
fun ProgressHighlightsStack(snapshot: HighlightReelSnapshot, onOpenCard: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (snapshot.cards.isEmpty()) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            HighlightPosterViewport(kotlin.math.min(maxWidth.value / HighlightPosterWidth, maxHeight.value / HighlightPosterHeight)) {
                HighlightEmptyPoster()
            }
        }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "kicker") {
            Text(snapshot.kicker, color = HighlightColors.BeforeGray, fontSize = 11.sp, letterSpacing = 1.3.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp).clearAndSetSemantics { contentDescription = snapshot.kicker })
        }
        itemsIndexed(snapshot.cards, key = { _, card -> card.key }, contentType = { _, card -> card.kind }) { index, card ->
            HighlightCompactCard(card, onClick = { onOpenCard(index) })
        }
    }
}

internal fun HighlightKind.label() = when (this) {
    HighlightKind.MATCH_CUT -> "Match Cut"
    HighlightKind.BEAT -> "Beat"
    HighlightKind.REPEATABLE_BEST -> "Repeatable Best"
    HighlightKind.CADENCE_BANDS -> "Cadence Bands"
    HighlightKind.RUNNING_TOTAL -> "Running Total"
}

@Composable
private fun HighlightCompactCard(card: HighlightCard, onClick: () -> Unit) {
    val background = card.frame.chapterColor()
    val foreground = when (card.kind) {
        HighlightKind.CADENCE_BANDS, HighlightKind.REPEATABLE_BEST -> HighlightColors.Chartreuse
        else -> HighlightColors.Ink
    }
    val height = when (card.kind) {
        HighlightKind.REPEATABLE_BEST -> 184f
        HighlightKind.CADENCE_BANDS -> 206f
        HighlightKind.RUNNING_TOTAL -> 208f
        else -> 166f
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = maxWidth.value / 358f
        Box(Modifier.fillMaxWidth().height((height * scale).dp)
            .background(background, RoundedCornerShape(18.dp))
            .semantics { contentDescription = "Open ${card.kind.label()} highlight · ${card.tag} · ${card.title}" }
            .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(358.dp, height.dp).graphicsLayer {
                scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin.Center
            }) {
                PosterText(card.title, 14f, 10f, 330f, foreground, 24.sp, maxLines = 2,
                    fontFamily = HighlightsCondensedImpactFontFamily)
                // The pill says why the card was selected.
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(foreground.copy(alpha = 0.16f), Offset(12.dp.toPx(), 42.dp.toPx()),
                        Size((card.tag.length * 6.1f + 12f).coerceAtMost(334f).dp.toPx(), 19.dp.toPx()), CornerRadius(9.5.dp.toPx()))
                }
                PosterText(card.tag, 14f, 43f, 330f, foreground, 13.sp,
                    modifier = Modifier.semantics { contentDescription = "Highlight tag: ${card.tag}" })
                when (val frame = card.frame) {
                    is ProgressHighlightFrame.MatchCut -> {
                        PosterText(frame.deltaLoadLabel, 14f, 55f, 196f, foreground, 69.sp)
                        PosterText("SAME ${countLabel(frame.reps, "REP")}", 205f, 72f, 140f, foreground, 29.sp)
                        PosterText("${frame.beforeLoadLabel} → ${frame.afterLoadLabel}", 14f, 120f, 330f, HighlightColors.White, 21.sp)
                    }
                    is ProgressHighlightFrame.Beat -> {
                        PosterText(frame.deltaLoadLabel, 14f, 55f, 196f, foreground, 69.sp)
                        PosterText("SAME ${countLabel(frame.now.reps, "REP")}", 205f, 72f, 140f, foreground, 29.sp)
                        PosterText("${frame.then.loadLabel} → ${frame.now.loadLabel}", 14f, 120f, 330f, HighlightColors.White, 21.sp)
                        CompactTicks(frame.sessionTickPositions, foreground)
                    }
                    is ProgressHighlightFrame.RepeatableBest -> {
                        PosterText("${frame.best.loadLabel} × ${frame.best.reps}", 14f, 56f, 330f, foreground, 49.sp)
                        PosterText("${countLabel(frame.sessionsAtOrAbove, "SESSION")} AT OR ABOVE", 14f, 112f, 330f, HighlightColors.CobaltSupport, 21.sp)
                        RepeatableBestChart(frame, Modifier.offset(x = 175.dp, y = 139.dp).size(165.dp, 23.dp))
                    }
                    is ProgressHighlightFrame.CadenceBands -> {
                        PosterText(frame.straightWeekCount.toString(), 14f, 52f, 76f, foreground, 95.sp)
                        PosterText("STRAIGHT\nACTIVE WEEKS", 100f, 72f, 244f, foreground, 34.sp, maxLines = 2)
                        Canvas(Modifier.offset(x = 16.dp, y = 153.dp).size(326.dp, 23.dp)) {
                            frame.weeks.forEachIndexed { weekIndex, week ->
                                val left = weekIndex * size.width / 4f
                                drawLine(HighlightColors.Ink, Offset(left, size.height / 2f), Offset(left + size.width / 4f - 8.dp.toPx(), size.height / 2f), 1.dp.toPx())
                                week.workoutDays.forEach { day ->
                                    drawCircle(foreground, 2.4.dp.toPx(), Offset(left + day * (size.width / 4f - 10.dp.toPx()) / 6f, size.height / 2f))
                                }
                            }
                        }
                    }
                    is ProgressHighlightFrame.RunningTotal -> {
                        PosterText(frame.totalReps.toString(), 14f, 52f, 188f, foreground, 85.sp)
                        PosterText("REPS", 205f, 86f, 136f, foreground, 40.sp)
                        // Receipts are an equation, not unlabeled chart bars or a strength score.
                        PosterText(frame.sessionReps.joinToString(" + "), 14f, 139f, 330f, HighlightColors.IceBlue, 19.sp)
                        PosterText("ACROSS ${countLabel(frame.workoutCount, "WORKOUT")}", 14f, 159f, 330f, foreground, 16.sp)
                    }
                }
                PosterText(card.dateLabel, 14f, height - 24f, 330f, foreground, 14.sp)
            }
        }
    }
}

@Composable
private fun CompactTicks(positions: List<Float>, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.offset(x = 208.dp, y = 112.dp).size(130.dp, 10.dp)) {
        positions.forEach { x -> drawRect(color, Offset(x * (size.width - 2.dp.toPx()), 0f), Size(2.dp.toPx(), size.height)) }
    }
}
