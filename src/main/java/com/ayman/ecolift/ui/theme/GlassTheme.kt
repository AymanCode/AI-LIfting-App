package com.ayman.ecolift.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
    val motion = rememberInfiniteTransition(label = "glass_ambient")
    val driftA by motion.animateFloat(
        initialValue = -0.18f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambient_a",
    )
    val driftB by motion.animateFloat(
        initialValue = 0.16f,
        targetValue = -0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambient_b",
    )

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
                drawCircle(
                    color = palette.auraBlue.copy(alpha = 0.36f),
                    radius = size.minDimension * 0.52f,
                    center = Offset(size.width * (0.18f + driftA), size.height * 0.12f),
                )
                drawCircle(
                    color = palette.auraGreen.copy(alpha = 0.40f),
                    radius = size.minDimension * 0.58f,
                    center = Offset(size.width * (0.88f + driftB), size.height * 0.38f),
                )
                drawCircle(
                    color = palette.auraCyan.copy(alpha = 0.30f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width * (0.40f - driftB), size.height * 0.86f),
                )
                drawRect(palette.pageBottom.copy(alpha = 0.10f))
            },
    )
}

fun Modifier.glassPanel(
    palette: GlassPalette,
    shape: Shape,
    strong: Boolean = false,
    completed: Boolean = false,
): Modifier {
    val fill = when {
        completed -> Brush.linearGradient(
            listOf(
                palette.complete.copy(alpha = 0.26f),
                palette.glassFillStrong,
                palette.auraCyan.copy(alpha = 0.20f),
            ),
        )
        strong -> Brush.linearGradient(
            listOf(
                palette.glassFillStrong,
                palette.glassFill.copy(alpha = 0.72f),
                palette.auraGreen.copy(alpha = 0.16f),
            ),
        )
        else -> Brush.linearGradient(
            listOf(
                palette.glassFill,
                palette.glassFillStrong.copy(alpha = 0.42f),
                palette.auraBlue.copy(alpha = 0.12f),
            ),
        )
    }
    return this
        .clip(shape)
        .background(fill, shape)
        .border(
            width = 1.dp,
            color = if (completed || strong) palette.glassStrokeStrong else palette.glassStroke,
            shape = shape,
        )
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
