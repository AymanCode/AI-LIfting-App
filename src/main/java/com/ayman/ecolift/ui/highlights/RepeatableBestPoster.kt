package com.ayman.ecolift.ui.highlights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.R
import com.ayman.ecolift.data.progress.highlights.ProgressHighlightFrame
import com.ayman.ecolift.ui.theme.HighlightsCondensedImpactFontFamily
import com.ayman.ecolift.web.util.noun

@Composable
internal fun RepeatableBestPoster(frame: ProgressHighlightFrame.RepeatableBest) {
    Box(Modifier.fillMaxSize().background(HighlightColors.Cobalt)) {
        Image(painterResource(R.drawable.highlight_texture_cobalt), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        PosterText(frame.exerciseName.uppercase(), 22f, 110f, 346f, HighlightColors.Chartreuse, 45.sp, maxLines = 2,
            fontFamily = HighlightsCondensedImpactFontFamily)
        PosterText("${frame.best.loadLabel} × ${frame.best.reps}", 22f, 211f, 346f, HighlightColors.Chartreuse, 85.sp,
            fontFamily = HighlightsCondensedImpactFontFamily)
        PosterText("YOUR BEST ON ${frame.best.dateLabel}", 22f, 305f, 346f, HighlightColors.CobaltSupport, 22.sp)
        RepeatableBestChart(frame, Modifier.offset(x = 26.dp, y = 360.dp).size(338.dp, 170.dp))
        PosterText(frame.sessions.first().dateLabel, 22f, 538f, 150f, HighlightColors.CobaltSupport, 17.sp)
        PosterText(frame.sessions.last().dateLabel, 255f, 538f, 112f, HighlightColors.CobaltSupport, 17.sp)
        PosterText("DOT: HEAVIEST SET OF ${frame.best.reps}+ REPS", 22f, 568f, 346f, HighlightColors.CobaltSupport, 17.sp)
        PosterText(frame.sessionsAtOrAbove.toString(), 22f, 593f, 85f, HighlightColors.Chartreuse, 92.sp)
        PosterText(noun(frame.sessionsAtOrAbove, "SESSION") + "\nAT OR ABOVE", 117f, 615f, 247f, HighlightColors.Chartreuse, 33.sp, maxLines = 2)
    }
}

/** Coordinates are part of the projection; drawing never queries or reduces session history. */
@Composable
internal fun RepeatableBestChart(frame: ProgressHighlightFrame.RepeatableBest, modifier: Modifier) {
    Canvas(modifier) {
        val inset = 5.dp.toPx()
        val width = size.width - 2 * inset
        val height = size.height - 2 * inset
        val ceiling = inset + frame.ceilingY * height
        drawLine(HighlightColors.Chartreuse, Offset(0f, ceiling), Offset(size.width, ceiling), 2.dp.toPx())
        frame.chartPoints.forEachIndexed { index, point ->
            val center = Offset(inset + point.x * width, inset + point.y * height)
            if (index > 0) {
                val before = frame.chartPoints[index - 1]
                drawLine(HighlightColors.CobaltSupport, Offset(inset + before.x * width, inset + before.y * height), center, 1.dp.toPx())
            }
            if (point.atOrAbove) drawCircle(HighlightColors.Chartreuse, 4.dp.toPx(), center)
            else drawCircle(HighlightColors.CobaltSupport, 4.dp.toPx(), center, style = Stroke(1.4.dp.toPx()))
        }
    }
}
