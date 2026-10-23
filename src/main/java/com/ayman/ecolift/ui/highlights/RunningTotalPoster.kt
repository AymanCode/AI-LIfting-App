package com.ayman.ecolift.ui.highlights

import com.ayman.ecolift.web.util.countLabel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.progress.highlights.ProgressHighlightFrame
import com.ayman.ecolift.ui.theme.HighlightsCondensedImpactFontFamily
import com.ayman.ecolift.ui.theme.HighlightsImpactFontFamily
import com.ayman.ecolift.ui.theme.PosterFontFamily
import androidx.compose.ui.res.painterResource
import com.ayman.ecolift.R

@Composable
internal fun RunningTotalPoster(
    frame: ProgressHighlightFrame.RunningTotal,
    modifier: Modifier = Modifier,
) {
    val receiptShadow = Shadow(
        color = HighlightColors.IceBlue,
        offset = Offset(0.85f, 0.85f),
        blurRadius = 0f,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HighlightColors.Magenta),
    ) {
        Image(
            painter = painterResource(R.drawable.highlight_texture_magenta),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = HighlightColors.Ink,
                topLeft = Offset(0f, 598.dp.toPx()),
                size = Size(size.width, size.height - 598.dp.toPx()),
            )
            drawRect(
                color = HighlightColors.Ink,
                topLeft = Offset(46.dp.toPx(), 582.dp.toPx()),
                size = Size(289.dp.toPx(), 7.dp.toPx()),
            )
        }

        PosterText(
            text = "REPS PER SESSION",
            x = 26f,
            y = 89.6f,
            width = 364f,
            color = HighlightColors.Ink,
            fontSize = 67.8.sp,
            lineHeight = 67.8.sp,
            letterSpacing = 0.5.sp,
            fontFamily = HighlightsCondensedImpactFontFamily,
            horizontalScale = 1.177f,
        )
        PosterText(
            text = "SESSION",
            x = 113f,
            y = 185.5f,
            width = 120f,
            color = HighlightColors.IceBlue,
            fontSize = 23.sp,
            lineHeight = 23.sp,
            fontFamily = PosterFontFamily,
            horizontalScale = 0.56f,
        )
        PosterText(
            text = "REPS",
            x = 236f,
            y = 185.5f,
            width = 75f,
            color = HighlightColors.IceBlue,
            fontSize = 23.sp,
            lineHeight = 23.sp,
            fontFamily = PosterFontFamily,
            horizontalScale = 0.56f,
        )

        frame.sessionReps.forEachIndexed { index, reps ->
            val rowY = 207f + index * 44.85f
            PosterText(
                text = "+",
                x = 65f,
                y = rowY + 8.5f,
                width = 22f,
                color = HighlightColors.IceBlue,
                fontSize = 36.8.sp,
                lineHeight = 36.8.sp,
                textAlign = TextAlign.Center,
                textShadow = receiptShadow,
                horizontalScale = 1.35f,
                verticalScale = 0.94f,
            )
            PosterText(
                text = (index + 1).toString().padStart(2, '0'),
                x = 117f,
                y = rowY,
                width = 80f,
                color = HighlightColors.IceBlue,
                fontSize = 55.sp,
                lineHeight = 55.sp,
                fontFamily = PosterFontFamily,
                horizontalScale = 0.50f,
                verticalScale = 0.97f,
            )
            PosterText(
                text = reps.toString(),
                x = 238f,
                y = rowY,
                width = 80f,
                color = HighlightColors.IceBlue,
                fontSize = 55.sp,
                lineHeight = 55.sp,
                fontFamily = PosterFontFamily,
                horizontalScale = 0.50f,
                verticalScale = 0.97f,
            )
        }

        PosterText(
            text = frame.totalReps.toString(),
            x = 58f,
            y = 553.6f,
            width = 240f,
            color = HighlightColors.Magenta,
            fontSize = 173.3.sp,
            lineHeight = 173.3.sp,
            letterSpacing = 1.5.sp,
            fontFamily = HighlightsCondensedImpactFontFamily,
            horizontalScale = 1.338f,
        )
        PosterText(
            text = "REPS",
            x = 258f,
            y = 686.2f,
            width = 115f,
            color = HighlightColors.Magenta,
            fontSize = 59.8.sp,
            lineHeight = 59.8.sp,
            letterSpacing = 1.sp,
            fontFamily = HighlightsCondensedImpactFontFamily,
            horizontalScale = 1.30f,
        )
        PosterText(
            text = "ACROSS ${frame.workoutCount} ${if (frame.workoutCount == 1) "WORKOUT" else "WORKOUTS"}",
            x = 80f,
            y = 770.9f,
            width = 230f,
            color = HighlightColors.White,
            fontSize = 25.sp,
            lineHeight = 25.sp,
            letterSpacing = 5.1.sp,
            textAlign = TextAlign.Center,
        )
        PosterText(
            text = "${countLabel(frame.setCount, "SET")}  ·  ${frame.periodLabel.uppercase()}",
            x = 65f,
            y = 801.1f,
            width = 260f,
            color = HighlightColors.White,
            fontSize = 25.sp,
            lineHeight = 25.sp,
            letterSpacing = 3.6.sp,
            textAlign = TextAlign.Center,
        )
    }
}
