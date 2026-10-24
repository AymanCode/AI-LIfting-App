package com.ayman.ecolift.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class GlassPalette(
    val pageTop: Color,
    val pageBottom: Color,
    val auraBlue: Color,
    val auraGreen: Color,
    val auraCyan: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassStroke: Color,
    val glassStrokeStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkSubtle: Color,
    val accent: Color,
    val accentStrong: Color,
    val complete: Color,
    val load: Color,
    val loadStrong: Color,
    val loadInk: Color,
    val rep: Color,
    val repStrong: Color,
    val repInk: Color,
    val prLight: Color,
    val prMid: Color,
    val prDeep: Color,
    val prInk: Color,
    val danger: Color,
    val scrim: Color,
)

enum class GlassPaletteChoice(val label: String, val storageKey: String) {
    Sage("Sage", "sage"),
    Ember("Ember", "ember"),
    Boreal("Boreal", "boreal"),
    Meadow("Meadow", "meadow");

    companion object {
        fun fromStorageKey(value: String?): GlassPaletteChoice =
            entries.firstOrNull { it.storageKey == value } ?: Sage
    }
}

/** Every saved choice resolves to Espresso; the enum and its storage key stay so old settings load. */
@Stable
fun GlassPaletteChoice.palette(): GlassPalette = EspressoGlassPalette

val LocalGlassPalette = staticCompositionLocalOf { EspressoGlassPalette }

@Composable
fun GlassTheme(
    palette: GlassPalette,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGlassPalette provides palette) {
        MaterialTheme(typography = LogMaterialTypography) {
            content()
        }
    }
}

@Composable
fun GlassTheme(
    paletteChoice: GlassPaletteChoice,
    content: @Composable () -> Unit,
) {
    GlassTheme(palette = paletteChoice.palette(), content = content)
}

@Composable
fun GlassAmbientBackground(
    palette: GlassPalette,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(EspressoPalette.bg))
}

/**
 * The four material tiers of the glass system. Higher tiers read brighter and more present, and a
 * warm outer glow is reserved for the top two so a single glance tells you what is live:
 *
 * - [Passive] — completed / inactive surfaces. Quiet: low-opacity fill, soft border, no glow.
 * - [Standard] — default surfaces (normal cards, search, date controls, bottom nav bar). Readable,
 *   polished translucency, no glow.
 * - [Active] — the focused panel (the in-progress exercise card). Stronger fill and border, a clearer
 *   top highlight, and a soft warm outer glow for depth.
 * - [Interaction] — touched / selected / mid-gesture surfaces. The brightest tier: accent-tinted
 *   border and the strongest glow, because the user is acting on it right now.
 */
enum class GlassLevel { Passive, Standard, Active, Interaction }

private fun resolveGlassLevel(strong: Boolean, completed: Boolean, interactive: Boolean): GlassLevel =
    when {
        completed -> GlassLevel.Passive
        interactive -> GlassLevel.Interaction
        strong -> GlassLevel.Active
        else -> GlassLevel.Standard
    }

/**
 * Back-compatible entry point. Existing call sites pass [strong] / [completed]; [interactive] opts a
 * surface into the brightest tier. The booleans map to a [GlassLevel] via [resolveGlassLevel]
 * (completed wins over strong so a finished card always reads quiet).
 */
fun Modifier.glassPanel(
    palette: GlassPalette,
    shape: Shape,
    strong: Boolean = false,
    completed: Boolean = false,
    interactive: Boolean = false,
): Modifier = glassPanel(palette, shape, resolveGlassLevel(strong, completed, interactive))

/**
 * Espresso: tone makes structure. Every tier is an opaque panel, no stroke, no highlight, no glow.
 * [GlassLevel.Interaction] adds a 1.5dp ring in the accent so a surface being acted on still reads.
 */
fun Modifier.glassPanel(
    palette: GlassPalette,
    shape: Shape,
    level: GlassLevel,
): Modifier {
    val fill = when (level) {
        GlassLevel.Passive -> EspressoPalette.panel.copy(alpha = 0.72f)
        GlassLevel.Standard, GlassLevel.Active, GlassLevel.Interaction -> EspressoPalette.panel
    }
    val m = clip(shape).background(fill, shape)
    return if (level == GlassLevel.Interaction) m.border(1.5.dp, palette.accent, shape) else m
}

/** How deep a [carvedPanel] sits. [Shallow] is the quiet state, not a different treatment. */
enum class CarveDepth { Full, Shallow }

/**
 * The counterpart to [glassPanel]: **controls are carved into the page, content floats above it.**
 *
 * Every one of glassPanel's four tiers is about lift, so when the search field and the nav buttons
 * used it too, nothing on the screen had depth — an empty input read as an exercise card that
 * happened to have no content. A recess is dark at the top where the light is blocked and catches a
 * bright lip along the bottom where the surface turns back up. Inputs and nav buttons get this;
 * cards, pills and sheets keep floating.
 *
 * Compose has no inset shadow, so the recess is drawn: a short vertical black gradient at the top
 * plus a 1dp light line along the bottom edge.
 *
 * @param accentSpill draws a wash of the palette accent in from the leading edge. The carve itself
 *   is deliberately neutral in every palette — it has to read as a material, not as a colour — so
 *   this is the layer that carries the theme. Used by the search field, not by the nav buttons.
 */
fun Modifier.carvedPanel(
    palette: GlassPalette,
    shape: Shape,
    depth: CarveDepth = CarveDepth.Full,
    accentSpill: Boolean = false,
): Modifier = clip(shape).background(EspressoPalette.panel2, shape)

typealias LogGlassPalette = GlassPalette
typealias LogPaletteChoice = GlassPaletteChoice

val DefaultLogGlassPalette: GlassPalette = EspressoGlassPalette
