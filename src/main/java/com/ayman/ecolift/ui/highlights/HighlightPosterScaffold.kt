package com.ayman.ecolift.ui.highlights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.HighlightsDisplayFontFamily
import com.ayman.ecolift.ui.theme.HighlightsImpactFontFamily

internal const val HighlightPosterWidth = 390f
internal const val HighlightPosterHeight = 844f

internal object HighlightColors {
    val Orange = Color(0xFFF84702)
    val Cobalt = Color(0xFF0139DE)
    val Magenta = Color(0xFFE03773)
    val Ink = Color(0xFF0C0C0C)
    val WarmWhite = Color(0xFFFFF8F2)
    val White = Color(0xFFFFFFFF)
    val BeforeGray = Color(0xFFB4B4B5)
    val Chartreuse = Color(0xFFD7F502)
    val CobaltSupport = Color(0xFFBDD7FF)
    val IceBlue = Color(0xFFBAE7FB)
}

@Composable
internal fun HighlightPosterViewport(
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            content = content,
            modifier = Modifier
                .requiredSize(HighlightPosterWidth.dp, HighlightPosterHeight.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
                .clipToBounds(),
        )
    }
}

@Composable
internal fun HighlightReelChrome(
    pageCount: Int,
    currentPage: Int,
    onBack: () -> Unit,
    calibration: HighlightChromeCalibration = HighlightChromeCalibration.MatchCut,
    card: com.ayman.ecolift.data.progress.highlights.HighlightCard? = null,
    onOpenReport: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isBackFocused by remember { mutableStateOf(false) }
    val foreground = if (card?.kind == com.ayman.ecolift.data.progress.highlights.HighlightKind.REPEATABLE_BEST ||
        card?.kind == com.ayman.ecolift.data.progress.highlights.HighlightKind.CADENCE_BANDS) HighlightColors.Chartreuse else HighlightColors.White
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(HighlightColors.Ink, topLeft = Offset(16.dp.toPx(), 20.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(87.dp.toPx(), 40.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()))
            if (isBackFocused) drawRoundRect(HighlightColors.White,
                topLeft = Offset(16.dp.toPx(), 20.dp.toPx()), size = androidx.compose.ui.geometry.Size(87.dp.toPx(), 40.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()), style = Stroke(2.dp.toPx()))
            val arrowY = 40.dp.toPx()
            drawLine(HighlightColors.White, Offset(27.dp.toPx(), arrowY), Offset(43.dp.toPx(), arrowY), 2.2.dp.toPx())
            drawLine(HighlightColors.White, Offset(27.dp.toPx(), arrowY), Offset(33.dp.toPx(), arrowY - 6.dp.toPx()), 2.2.dp.toPx())
            drawLine(HighlightColors.White, Offset(27.dp.toPx(), arrowY), Offset(33.dp.toPx(), arrowY + 6.dp.toPx()), 2.2.dp.toPx())
            repeat(pageCount) { index ->
                val center = Offset((123f + index * 13f).dp.toPx(), 40.dp.toPx())
                if (index == currentPage) drawCircle(HighlightColors.White, 3.2.dp.toPx(), center)
                else drawCircle(HighlightColors.White, 3.05.dp.toPx(), center, style = Stroke(1.05.dp.toPx()))
            }
        }
        PosterText("BACK", 51f, 28f, 46f, HighlightColors.White, 21.sp)
        Box(Modifier.offset(x = 12.dp, y = 16.dp).size(95.dp, 48.dp)
            .onFocusChanged { isBackFocused = it.isFocused }
            .semantics { contentDescription = "Back to Highlights" }
            .clickable(role = Role.Button, onClick = onBack))
        PosterText("HIGHLIGHTS", calibration.labelX, 70f, 180f, HighlightColors.White,
            calibration.labelFontSize.sp, fontFamily = HighlightsImpactFontFamily,
            horizontalScale = calibration.labelHorizontalScale)
        if (card != null) {
            PosterText(card.tag, 205f, 31f, 167f, foreground, 13.sp, maxLines = 2)
            if (card.why.isNotBlank()) {
                PosterText("WHY THIS ONE", 22f, 710f, 346f, foreground, 17.sp, letterSpacing = 1.sp)
                PosterText(card.why, 22f, 735f, 346f, foreground, 20.sp, lineHeight = 22.sp, maxLines = 2)
            }
            HighlightOpenInProgressPill(card.reportExerciseName) { onOpenReport(card.reportExerciseId) }
        }
    }
}

/**
 * The pill is the button: one 350×48 box at (20, 780) draws the ink shape and centres its content,
 * a micro-kicker naming the report over the action, so nothing hugs an edge or splits baselines.
 */
@Composable
private fun HighlightOpenInProgressPill(reportName: String, onOpen: () -> Unit) {
    val poster = TextStyle(fontFamily = HighlightsImpactFontFamily, fontWeight = FontWeight.Normal)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.offset(x = 20.dp, y = 780.dp).size(350.dp, 48.dp)
            .clip(RoundedCornerShape(24.dp)).background(HighlightColors.Ink)
            .clickable(role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Open in Progress"
                onClick("Open in Progress") { onOpen(); true }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("${reportName.uppercase()} REPORT", color = HighlightColors.White.copy(alpha = .8f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = poster.copy(fontSize = 11.sp, lineHeight = 12.sp, letterSpacing = .6.sp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("OPEN IN PROGRESS", color = HighlightColors.White, maxLines = 1,
                    style = poster.copy(fontSize = 19.sp, lineHeight = 21.sp))
                Text("›", color = HighlightColors.White, maxLines = 1, style = poster.copy(fontSize = 19.sp, lineHeight = 21.sp))
            }
        }
    }
}

internal data class HighlightChromeCalibration(
    val chromeYOffset: Float,
    val labelX: Float,
    val labelY: Float,
    val labelFontSize: Float,
    val labelLetterSpacing: Float,
    val labelHorizontalScale: Float,
) {
    companion object {
        val MatchCut = HighlightChromeCalibration(
            chromeYOffset = 0f,
            labelX = 22f,
            labelY = 69.3f,
            labelFontSize = 21f,
            labelLetterSpacing = 0f,
            labelHorizontalScale = 0.966f,
        )
        val Cadence = HighlightChromeCalibration(
            chromeYOffset = 0f,
            labelX = 23f,
            labelY = 67.4f,
            labelFontSize = 27f,
            labelLetterSpacing = 0f,
            labelHorizontalScale = 0.844f,
        )
        val RunningTotal = HighlightChromeCalibration(
            chromeYOffset = -7f,
            labelX = 21f,
            labelY = 62f,
            labelFontSize = 22f,
            labelLetterSpacing = 0f,
            labelHorizontalScale = 0.903f,
        )
    }
}

@Composable
internal fun PosterText(
    text: String,
    x: Float,
    y: Float,
    width: Float,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit = fontSize,
    letterSpacing: TextUnit = 0.sp,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    fontFamily: FontFamily = HighlightsDisplayFontFamily,
    textShadow: Shadow? = null,
    horizontalScale: Float = 1f,
    verticalScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        textAlign = textAlign,
        style = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            shadow = textShadow,
        ),
        modifier = modifier
            .offset(x = x.dp, y = y.dp)
            .width(width.dp)
            .graphicsLayer {
                scaleX = horizontalScale
                scaleY = verticalScale
                transformOrigin = TransformOrigin(0f, 0f)
            },
    )
}

@Composable
internal fun HighlightEmptyPoster(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(HighlightColors.Ink),
    ) {
        PosterText(
            text = "YOUR NEXT HIGHLIGHT\nIS STILL IN THE WORKS",
            x = 28f,
            y = 342f,
            width = 334f,
            color = HighlightColors.White,
            fontSize = 39.sp,
            lineHeight = 43.sp,
            letterSpacing = -0.6.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        PosterText(
            text = "KEEP LOGGING.",
            x = 48f,
            y = 449f,
            width = 294f,
            color = HighlightColors.BeforeGray,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.25.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
