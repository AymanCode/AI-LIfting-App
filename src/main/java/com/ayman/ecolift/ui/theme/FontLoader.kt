package com.ayman.ecolift.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.ayman.ecolift.R

/**
 * The families LogTypography.kt does not declare yet. They are vars so [loadAppFonts] can
 * replace the sans-serif stand-in once the res/font files are on the classpath.
 * Log, display, poster, and marker families already load from res/font as vals in that file.
 */
var HighlightsDisplayFontFamily: FontFamily = FontFamily.SansSerif
var HighlightsImpactFontFamily: FontFamily = FontFamily.SansSerif
var HighlightsCondensedImpactFontFamily: FontFamily = FontFamily.SansSerif
var SheetSerifFontFamily: FontFamily = FontFamily.SansSerif
var SheetSansFontFamily: FontFamily = FontFamily.SansSerif

/** Assigns the res/font families. The read is synchronous. The suspend shape matches the web call. */
suspend fun loadAppFonts() {
    QuietUiFontFamily = FontFamily(
        Font(R.font.arimo_regular, FontWeight.Normal),
        Font(R.font.arimo_medium, FontWeight.Medium),
        Font(R.font.arimo_bold, FontWeight.Bold),
    )
    HighlightsDisplayFontFamily = FontFamily(Font(R.font.league_gothic_condensed, FontWeight.Normal))
    HighlightsImpactFontFamily = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))
    HighlightsCondensedImpactFontFamily = FontFamily(Font(R.font.ecolift_impact_condensed, FontWeight.Normal))
    SheetSerifFontFamily = FontFamily(
        Font(R.font.fraunces_light, FontWeight.Light),
        Font(R.font.fraunces_light_italic, FontWeight.Light, FontStyle.Italic),
    )
    SheetSansFontFamily = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    )
}
