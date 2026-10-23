package com.ayman.ecolift.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.viewmodel.DataPart

/**
 * Data is never plain (owner rule, 2026-09-12): every number, set, date and count is display-font
 * semibold ink; the words between them are body font in a quiet colour. Build captions from
 * [DataPart]s so the rule cannot be skipped.
 */
fun dataLine(
    parts: List<DataPart>,
    size: TextUnit = 12.sp,
    dataSize: TextUnit = size,
    filler: Color = EspressoPalette.faint,
    data: Color = EspressoPalette.ink,
): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        val style = if (part.isData) {
            SpanStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.SemiBold, color = data, fontSize = dataSize, letterSpacing = (-0.2).sp)
        } else {
            SpanStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Normal, color = filler, fontSize = size)
        }
        withStyle(style) { append(part.text) }
    }
}

@Composable
fun DataText(
    parts: List<DataPart>,
    modifier: Modifier = Modifier,
    size: TextUnit = 12.sp,
    dataSize: TextUnit = size,
    lineHeight: TextUnit = (size.value + 4).sp,
    filler: Color = EspressoPalette.faint,
    data: Color = EspressoPalette.ink,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    Text(
        text = dataLine(parts, size, dataSize, filler, data),
        modifier = modifier,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}
