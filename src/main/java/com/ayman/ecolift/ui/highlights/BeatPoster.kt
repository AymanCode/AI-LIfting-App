package com.ayman.ecolift.ui.highlights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.R
import com.ayman.ecolift.data.progress.highlights.ProgressHighlightFrame
import com.ayman.ecolift.ui.theme.HighlightsCondensedImpactFontFamily
import com.ayman.ecolift.web.util.countLabel

@Composable
internal fun BeatPoster(frame: ProgressHighlightFrame.Beat) {
    Box(Modifier.fillMaxSize().background(HighlightColors.Orange)) {
        Image(painterResource(R.drawable.highlight_texture_orange), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        PosterText(frame.exerciseName.uppercase(), 22f, 112f, 346f, HighlightColors.Ink, 47.sp, maxLines = 2,
            fontFamily = HighlightsCondensedImpactFontFamily)
        PosterText("${countLabel(frame.sessions.size, "SESSION")}", 22f, 209f, 346f, HighlightColors.White, 22.sp)
        PosterText(frame.deltaLoadLabel, 16f, 234f, 362f, HighlightColors.Ink, 143.sp,
            fontFamily = HighlightsCondensedImpactFontFamily)
        PosterText("SAME ${countLabel(frame.now.reps, "REP")}", 22f, 385f, 346f, HighlightColors.Ink, 66.sp,
            fontFamily = HighlightsCondensedImpactFontFamily)
        Canvas(Modifier.offset(x = 26.dp, y = 484.dp).size(338.dp, 40.dp)) {
            val y = size.height / 2
            drawLine(HighlightColors.Ink, Offset(0f, y), Offset(size.width, y), 1.4.dp.toPx())
            frame.sessionTickPositions.forEachIndexed { index, tick ->
                val endpoint = index == 0 || index == frame.sessionTickPositions.lastIndex
                drawLine(if (index == 0) HighlightColors.Ink else HighlightColors.White,
                    Offset(tick * size.width, y - if (endpoint) 16.dp.toPx() else 9.dp.toPx()),
                    Offset(tick * size.width, y + if (endpoint) 16.dp.toPx() else 9.dp.toPx()),
                    if (endpoint) 3.dp.toPx() else 2.dp.toPx())
            }
        }
        listOf(frame.then, frame.now).forEachIndexed { index, set ->
            val x = if (index == 0) 22f else 224f
            val color = if (index == 0) HighlightColors.Ink else HighlightColors.White
            PosterText(set.dateLabel, x, 530f, 150f, color, 22.sp)
            PosterText(if (index == 0) "THEN" else "NOW", x, 564f, 150f, color, 23.sp)
            PosterText(set.loadLabel, x, 591f, 150f, color, 45.sp, fontFamily = HighlightsCondensedImpactFontFamily)
            PosterText("× ${countLabel(set.reps, "REP")}", x, 639f, 150f, color, 27.sp)
        }
        PosterText("→", 178f, 596f, 44f, HighlightColors.White, 30.sp)
    }
}
