package com.ayman.ecolift.ui.theme

import androidx.compose.ui.graphics.Color
import com.ayman.ecolift.data.BodyPartRegion

/**
 * Espresso: the one look for the lifting side of the app (spec
 * docs/superpowers/specs/2026-09-11-espresso-restyle-design.md).
 *
 * Four tonal steps of a warm brown-black make the structure. Six muscle colours do triple duty as
 * muscle, split and tab colours. Three meaning colours never decorate. Text on any colour block is
 * always [onColor]; colour is used as text only at 20sp+ bold.
 */
object EspressoPalette {
    val bg = Color(0xFF1C1816)
    val mast = Color(0xFF151210)
    val panel = Color(0xFF26211E)
    val panel2 = Color(0xFF312B27)
    val ink = Color(0xFFF3EDE4)
    val dim = Color(0xFFA89C90)
    val faint = Color(0xFF6F655C)
    val hair = Color(0xFF2E2925)
    val hair2 = Color(0xFF4A423B)

    val chest = Color(0xFFF0643C)
    val back = Color(0xFF5F80E6)
    val legs = Color(0xFF8CC163)
    val shoulders = Color(0xFFA98BEB)
    val arms = Color(0xFFF07AA8)
    val core = Color(0xFFE9B44C)

    val up = Color(0xFF8CCB9A)
    val down = Color(0xFFF2C270)
    val last = Color(0xFF9EB4E8)
    val danger = Color(0xFFF08A7E)

    /** Ink on every colour block. Cobalt is the tightest pairing at 4.8:1. */
    val onColor = Color(0xFF1C1816)

    /** The six, in the order splits draw from them. */
    val muscles: List<Color> = listOf(chest, back, legs, shoulders, arms, core)

    fun muscle(region: BodyPartRegion): Color = when (region) {
        BodyPartRegion.CHEST -> chest
        BodyPartRegion.BACK -> back
        BodyPartRegion.LEGS -> legs
        BodyPartRegion.SHOULDERS -> shoulders
        BodyPartRegion.ARMS -> arms
        BodyPartRegion.CORE -> core
        BodyPartRegion.UNKNOWN -> dim
    }

    /** A colour's resting fill: 16% over [panel]. */
    fun tint(color: Color): Color = color.copy(alpha = 0.16f).compositeOverPanel()

    private fun Color.compositeOverPanel(): Color {
        val a = alpha
        return Color(
            red = red * a + panel.red * (1 - a),
            green = green * a + panel.green * (1 - a),
            blue = blue * a + panel.blue * (1 - a),
        )
    }
}

/** The tab colour for a route. The Log's masthead uses the day's split colour instead; the nav uses chest. */
fun espressoTabColor(route: String?): Color = when (route?.substringBefore('/')?.substringBefore('?')) {
    "progress" -> EspressoPalette.back
    "ai" -> EspressoPalette.arms
    "split", "cycleArchive" -> EspressoPalette.legs
    else -> EspressoPalette.chest
}

/**
 * Espresso expressed through the legacy [GlassPalette] wiring, so every screen that reads
 * `LocalGlassPalette` lands on the new ground at once. The glass fields lose their meaning:
 * fills are opaque tones, strokes are gone, auras are transparent.
 */
val EspressoGlassPalette = GlassPalette(
    pageTop = EspressoPalette.bg,
    pageBottom = EspressoPalette.bg,
    auraBlue = Color.Transparent,
    auraGreen = Color.Transparent,
    auraCyan = Color.Transparent,
    glassFill = EspressoPalette.panel,
    glassFillStrong = EspressoPalette.panel2,
    glassStroke = Color.Transparent,
    glassStrokeStrong = EspressoPalette.hair2,
    ink = EspressoPalette.ink,
    inkMuted = EspressoPalette.dim,
    inkSubtle = EspressoPalette.faint,
    accent = EspressoPalette.chest,
    accentStrong = EspressoPalette.chest,
    complete = EspressoPalette.up,
    load = EspressoPalette.down,
    loadStrong = EspressoPalette.down,
    loadInk = EspressoPalette.onColor,
    rep = EspressoPalette.last,
    repStrong = EspressoPalette.last,
    repInk = EspressoPalette.onColor,
    prLight = EspressoPalette.core,
    prMid = EspressoPalette.core,
    prDeep = EspressoPalette.core,
    prInk = EspressoPalette.onColor,
    danger = EspressoPalette.danger,
    scrim = EspressoPalette.mast.copy(alpha = 0.70f),
)
