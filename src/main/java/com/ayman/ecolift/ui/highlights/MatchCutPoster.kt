package com.ayman.ecolift.ui.highlights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.R
import com.ayman.ecolift.data.progress.highlights.ProgressHighlightFrame
import com.ayman.ecolift.ui.theme.HighlightsCondensedImpactFontFamily
import com.ayman.ecolift.ui.theme.HighlightsImpactFontFamily

@Composable
internal fun MatchCutPoster(
    frame: ProgressHighlightFrame.MatchCut,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HighlightColors.Orange),
    ) {
        Image(
            painter = painterResource(R.drawable.highlight_texture_orange),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(),
        )
        PosterText(
            text = frame.exerciseName.uppercase(),
            x = 22f,
            y = 103.3f,
            width = 354f,
            color = HighlightColors.Ink,
            fontSize = 52.9.sp,
            lineHeight = 52.9.sp,
            fontFamily = HighlightsCondensedImpactFontFamily,
        )
        PosterText(
            text = frame.dateLabel.uppercase(),
            x = 22f,
            y = 178f,
            width = 240f,
            color = HighlightColors.WarmWhite,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            fontFamily = HighlightsImpactFontFamily,
        )

        PosterText(
            text = "+",
            x = 14f,
            y = 243.3f,
            width = 76f,
            color = HighlightColors.Ink,
            fontSize = 184.5.sp,
            lineHeight = 184.5.sp,
            horizontalScale = 1.33f,
            textAlign = TextAlign.Center,
        )
        PosterText(
            text = frame.deltaLoadLabel.uppercase().removePrefix("+").trim(),
            x = 105f,
            y = 171.4f,
            width = 270f,
            color = HighlightColors.Ink,
            fontSize = 239.5.sp,
            lineHeight = 239.5.sp,
            letterSpacing = 1.sp,
            fontFamily = HighlightsCondensedImpactFontFamily,
            horizontalScale = 1.083f,
        )
        PosterText(
            text = "SAME ${frame.reps} ${if (frame.reps == 1) "REP" else "REPS"}",
            x = 25f,
            y = 429.6f,
            width = 365f,
            color = HighlightColors.Ink,
            fontSize = 121.8.sp,
            lineHeight = 121.8.sp,
            letterSpacing = 0.5.sp,
            fontFamily = HighlightsCondensedImpactFontFamily,
            horizontalScale = 0.957f,
        )

        PosterText(
            text = "BEFORE",
            x = 29f,
            y = 603.2f,
            width = 130f,
            color = HighlightColors.BeforeGray,
            fontSize = 30.5.sp,
            lineHeight = 30.5.sp,
            letterSpacing = 1.4.sp,
            horizontalScale = 1.02f,
        )
        PosterText(
            text = "NOW",
            x = 232f,
            y = 602.8f,
            width = 130f,
            color = HighlightColors.White,
            fontSize = 30.5.sp,
            lineHeight = 30.5.sp,
            letterSpacing = 7.7.sp,
            horizontalScale = 1.02f,
        )
        PosterText(
            text = "${frame.beforeLoadLabel.uppercase()} × ${frame.reps}",
            x = 28f,
            y = 622.8f,
            width = 146f,
            color = HighlightColors.BeforeGray,
            fontSize = 57.5.sp,
            lineHeight = 57.5.sp,
            letterSpacing = 2.2.sp,
            horizontalScale = 1.02f,
        )
        PosterText(
            text = "${frame.afterLoadLabel.uppercase()} × ${frame.reps}",
            x = 231f,
            y = 622.8f,
            width = 150f,
            color = HighlightColors.White,
            fontSize = 57.5.sp,
            lineHeight = 57.5.sp,
            letterSpacing = 2.2.sp,
            horizontalScale = 1.02f,
        )

        Canvas(
            modifier = Modifier
                .offset(x = 177.dp, y = 634.dp)
                .size(width = 40.dp, height = 34.dp),
        ) {
            val stroke = 2.3.dp.toPx()
            val y = size.height / 2f
            val start = Offset(4.dp.toPx(), y)
            val end = Offset(35.dp.toPx(), y)
            drawLine(
                color = HighlightColors.White,
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = HighlightColors.White,
                start = end,
                end = Offset(27.dp.toPx(), y - 8.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = HighlightColors.White,
                start = end,
                end = Offset(27.dp.toPx(), y + 8.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
        }
    }
}
