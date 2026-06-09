package com.ayman.ecolift.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    val danger: Color,
    val scrim: Color,
)

val SageGlassPalette = GlassPalette(
    pageTop = Color(0xFF0B1715),
    pageBottom = Color(0xFF111B23),
    auraBlue = Color(0xFF6B97B6),
    auraGreen = Color(0xFF6EC59A),
    auraCyan = Color(0xFF6FAEB7),
    glassFill = Color(0xFF14231F).copy(alpha = 0.56f),
    glassFillStrong = Color(0xFF1A2D28).copy(alpha = 0.70f),
    glassStroke = Color.White.copy(alpha = 0.17f),
    glassStrokeStrong = Color(0xFF9BDCBD).copy(alpha = 0.36f),
    ink = Color(0xFFF4FFF9),
    inkMuted = Color(0xFFC0D9CF),
    inkSubtle = Color(0xFF8EA9A0),
    accent = Color(0xFF78CEA0),
    accentStrong = Color(0xFFA7E6C6),
    complete = Color(0xFF76D69D),
    danger = Color(0xFFFF8D82),
    scrim = Color(0xFF06100F).copy(alpha = 0.58f),
)

val EmberNoirGlassPalette = GlassPalette(
    pageTop = Color(0xFF21120E),
    pageBottom = Color(0xFF0B0E15),
    auraBlue = Color(0xFF5F6D83),
    auraGreen = Color(0xFFC17838),
    auraCyan = Color(0xFF944538),
    glassFill = Color(0xFF211611).copy(alpha = 0.58f),
    glassFillStrong = Color(0xFF2A1A14).copy(alpha = 0.74f),
    glassStroke = Color.White.copy(alpha = 0.16f),
    glassStrokeStrong = Color(0xFFE0A15E).copy(alpha = 0.38f),
    ink = Color(0xFFFFF4E7),
    inkMuted = Color(0xFFE2C29B),
    inkSubtle = Color(0xFFA98A70),
    accent = Color(0xFFD88743),
    accentStrong = Color(0xFFF0B46B),
    complete = Color(0xFFC9783F),
    danger = Color(0xFFFF8D82),
    scrim = Color(0xFF080605).copy(alpha = 0.62f),
)

enum class GlassPaletteChoice(val label: String, val storageKey: String) {
    Sage("Sage", "sage"),
    Ember("Ember", "ember");

    companion object {
        fun fromStorageKey(value: String?): GlassPaletteChoice =
            entries.firstOrNull { it.storageKey == value } ?: Sage
    }
}

@Stable
fun GlassPaletteChoice.palette(): GlassPalette =
    when (this) {
        GlassPaletteChoice.Sage -> SageGlassPalette
        GlassPaletteChoice.Ember -> EmberNoirGlassPalette
    }

val LocalGlassPalette = staticCompositionLocalOf { SageGlassPalette }

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
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(palette.pageTop, palette.pageBottom),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.pageBottom.copy(alpha = 0.56f),
                            Color.Transparent,
                            palette.pageBottom.copy(alpha = 0.28f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                    ),
                )
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.auraGreen.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        start = Offset(size.width * 0.08f, 0f),
                        end = Offset(size.width * 0.92f, size.height),
                    ),
                )
            },
    )
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
 * The material itself. Each tier owns a distinct fill, border weight/colour, top specular highlight
 * and glow so no two tiers read alike — this is what keeps the UI from looking like one reused card.
 */
fun Modifier.glassPanel(
    palette: GlassPalette,
    shape: Shape,
    level: GlassLevel,
): Modifier {
    // Every tier stays translucent so the living background reads THROUGH the glass — the card
    // transparency is deliberate. Hierarchy is carried by border, highlight and glow, never by
    // making a higher tier more opaque. All tiers share the same translucent base (glassFill);
    // only Passive drops lower to read quieter, and warmth is added as a tint, not as opacity.
    val fill = when (level) {
        GlassLevel.Passive -> Brush.linearGradient(
            listOf(
                palette.complete.copy(alpha = 0.10f),
                palette.glassFill.copy(alpha = 0.46f),
                palette.glassFillStrong.copy(alpha = 0.26f),
            ),
        )
        GlassLevel.Standard -> Brush.linearGradient(
            listOf(
                palette.glassFill,
                palette.glassFillStrong.copy(alpha = 0.42f),
                palette.auraBlue.copy(alpha = 0.10f),
            ),
        )
        GlassLevel.Active -> Brush.linearGradient(
            listOf(
                palette.glassFill,
                palette.glassFillStrong.copy(alpha = 0.46f),
                palette.accent.copy(alpha = 0.12f),
            ),
        )
        GlassLevel.Interaction -> Brush.linearGradient(
            listOf(
                palette.glassFill,
                palette.accent.copy(alpha = 0.16f),
                palette.glassFillStrong.copy(alpha = 0.40f),
            ),
        )
    }
    val strokeColor = when (level) {
        GlassLevel.Passive -> palette.glassStroke.copy(alpha = 0.50f)
        GlassLevel.Standard -> palette.glassStroke
        GlassLevel.Active -> palette.glassStrokeStrong
        GlassLevel.Interaction -> palette.accentStrong.copy(alpha = 0.60f)
    }
    val strokeWidth = when (level) {
        GlassLevel.Interaction -> 1.4.dp
        GlassLevel.Active -> 1.1.dp
        else -> 1.dp
    }
    // The thin band of light along the top edge that sells "glass". Brighter on the live tiers,
    // off entirely on the quiet one.
    val highlightAlpha = when (level) {
        GlassLevel.Passive -> 0f
        GlassLevel.Standard -> 0.05f
        GlassLevel.Active -> 0.08f
        GlassLevel.Interaction -> 0.10f
    }

    var m: Modifier = this
    // Outer glow + depth, warm from the palette accent, reserved for the live tiers.
    when (level) {
        GlassLevel.Active -> m = m.shadow(
            elevation = 10.dp,
            shape = shape,
            clip = false,
            ambientColor = palette.accent.copy(alpha = 0.22f),
            spotColor = palette.accent.copy(alpha = 0.22f),
        )
        GlassLevel.Interaction -> m = m.shadow(
            elevation = 14.dp,
            shape = shape,
            clip = false,
            ambientColor = palette.accentStrong.copy(alpha = 0.30f),
            spotColor = palette.accentStrong.copy(alpha = 0.30f),
        )
        else -> {}
    }
    m = m.clip(shape).background(fill, shape)
    if (highlightAlpha > 0f) {
        m = m.background(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = highlightAlpha),
                0.42f to Color.Transparent,
            ),
            shape,
        )
    }
    return m.border(width = strokeWidth, color = strokeColor, shape = shape)
}

@Composable
fun GlassPaletteSwitch(
    selected: GlassPaletteChoice,
    onSelect: (GlassPaletteChoice) -> Unit,
    modifier: Modifier = Modifier,
    palette: GlassPalette = LocalGlassPalette.current,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.glassFillStrong.copy(alpha = 0.74f),
            border = BorderStroke(1.dp, palette.glassStrokeStrong),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPaletteChoice.entries.forEach { choice ->
                    val isSelected = choice == selected
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 76.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) palette.accentStrong.copy(alpha = 0.24f) else Color.Transparent,
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) palette.glassStrokeStrong else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(choice) }
                            .semantics {
                                stateDescription = if (isSelected) "Selected" else "Not selected"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = choice.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) palette.ink else palette.inkMuted,
                        )
                    }
                }
            }
        }
    }
}

typealias LogGlassPalette = GlassPalette
typealias LogPaletteChoice = GlassPaletteChoice

val DefaultLogGlassPalette: GlassPalette = SageGlassPalette
val EmberNoirLogGlassPalette: GlassPalette = EmberNoirGlassPalette
