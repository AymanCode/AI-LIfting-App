package com.ayman.ecolift.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Local visual system for rotation rows.
 *
 * The six Espresso muscle colours, so a split's colour is drawn from the same set as the
 * muscles (spec §Colour): chest, back, legs, shoulders, arms, core.
 */
object SplitAccentPalette {
    val colors: List<Color> = listOf(
        EspressoPalette.chest,
        EspressoPalette.back,
        EspressoPalette.legs,
        EspressoPalette.shoulders,
        EspressoPalette.arms,
        EspressoPalette.core,
    )
}

const val SplitSigilCount: Int = 7

/**
 * Returns a deterministic row accent by rotation position.
 * The seven sigils cycle first; when a sigil repeats, the color advances.
 */
fun splitAccentColor(visualIndex: Int): Color {
    val index = visualIndex.coerceAtLeast(0)
    val sigilIndex = index % SplitSigilCount
    val repeatCycle = index / SplitSigilCount
    val colorIndex = (sigilIndex + repeatCycle) % SplitAccentPalette.colors.size
    return SplitAccentPalette.colors[colorIndex]
}

fun splitSigilIndex(visualIndex: Int): Int =
    visualIndex.coerceAtLeast(0) % SplitSigilCount
