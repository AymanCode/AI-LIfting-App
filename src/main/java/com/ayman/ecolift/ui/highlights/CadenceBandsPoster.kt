package com.ayman.ecolift.ui.highlights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

private val bandTops = listOf(420f, 499f, 577f, 655f)
private val weekdayCenters = listOf(40f, 91f, 143f, 194f, 245f, 297f, 348f)
private val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
internal fun CadenceBandsPoster(
    frame: ProgressHighlightFrame.CadenceBands,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HighlightColors.Cobalt),
    ) {
        Image(
            painter = painterResource(R.drawable.highlight_texture_cobalt),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(),
        )
        PosterText(
            text = frame.straightWeekCount.toString(),
            x = 26f,
            y = 43.6f,
            width = 130f,
            color = HighlightColors.Chartreuse,
            fontSize = 241.9.sp,
            lineHeight = 241.9.sp,
            fontFamily = HighlightsImpactFontFamily,
            horizontalScale = 1.345f,
        )
        PosterText(
            text = "STRAIGHT ACTIVE WEEKS",
            x = 23f,
            y = 320f,
            width = 367f,
            color = HighlightColors.Chartreuse,
            fontSize = 66.7.sp,
            lineHeight = 66.7.sp,
            fontFamily = HighlightsCondensedImpactFontFamily,
            horizontalScale = 0.985f,
        )

        CadenceRulesAndDots(frame)

        frame.weeks.forEachIndexed { index, week ->
            val top = bandTops.getOrNull(index) ?: return@forEachIndexed
            PosterText(
                text = "WEEK ${index + 1}  ·  ${week.dateRangeLabel.uppercase()}",
                x = 23f,
                y = top + 7f,
                width = 340f,
                color = HighlightColors.Chartreuse,
                fontSize = 20.7.sp,
                lineHeight = 20.7.sp,
                letterSpacing = 1.9.sp,
            )
            weekdayLabels.forEachIndexed { day, label ->
                PosterText(
                    text = label,
                    x = weekdayCenters[day] - 10f,
                    y = top + 33f,
                    width = 20f,
                    color = HighlightColors.CobaltSupport,
                    fontSize = 18.5.sp,
                    lineHeight = 18.5.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        PosterText(
            text = frame.workoutCount.toString(),
            // Four calendar weeks can contain up to 28 workout days. Keep the word
            // anchored while giving a two-digit receipt room to grow to the left.
            x = if (frame.workoutCount >= 10) 17f else 73f,
            y = 710.7f,
            width = if (frame.workoutCount >= 10) 90f else 45f,
            color = HighlightColors.Chartreuse,
            fontSize = 77.sp,
            lineHeight = 77.sp,
            fontFamily = HighlightsImpactFontFamily,
            horizontalScale = 1.26f,
        )
        PosterText(
            text = if (frame.workoutCount == 1) "WORKOUT" else "WORKOUTS",
            x = 134f,
            y = 737f,
            width = 225f,
            color = HighlightColors.Chartreuse,
            fontSize = 54.sp,
            lineHeight = 54.sp,
            letterSpacing = 0.5.sp,
            fontFamily = HighlightsCondensedImpactFontFamily,
            horizontalScale = 1.397f,
        )
    }
}

@Composable
private fun CadenceRulesAndDots(frame: ProgressHighlightFrame.CadenceBands) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val fullRule = 1.35.dp.toPx()
        val railRule = 1.1.dp.toPx()
        val tickHalfHeight = 6.5.dp.toPx()

        bandTops.forEachIndexed { weekIndex, top ->
            val topY = top.dp.toPx()
            drawLine(
                color = HighlightColors.Ink,
                start = Offset(0f, topY),
                end = Offset(size.width, topY),
                strokeWidth = fullRule,
                cap = StrokeCap.Square,
            )

            val railY = (top + 62f).dp.toPx()
            drawLine(
                color = HighlightColors.Ink,
                start = Offset(22.dp.toPx(), railY),
                end = Offset(370.dp.toPx(), railY),
                strokeWidth = railRule,
                cap = StrokeCap.Square,
            )
            weekdayCenters.forEach { x ->
                drawLine(
                    color = HighlightColors.Ink,
                    start = Offset(x.dp.toPx(), railY - tickHalfHeight),
                    end = Offset(x.dp.toPx(), railY + tickHalfHeight),
                    strokeWidth = railRule,
                    cap = StrokeCap.Square,
                )
            }

            frame.weeks.getOrNull(weekIndex)?.workoutDays?.forEach { day ->
                weekdayCenters.getOrNull(day)?.let { x ->
                    drawCircle(
                        color = HighlightColors.Chartreuse,
                        radius = 8.8.dp.toPx(),
                        center = Offset(x.dp.toPx(), railY),
                    )
                }
            }
        }
    }
}
