package com.ayman.ecolift.ui.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ayman.ecolift.ui.theme.GlassPalette
import kotlin.random.Random

/**
 * The living-glass wallpaper: one continuous bubble field that spans every tab. It is mounted
 * once behind the whole NavHost, so the animation state survives navigation and never restarts.
 *
 * Bubbles drift at a single lava-lamp pace and ricochet off the world's outer edges; the world is
 * [tabCount] screens wide and the camera glides to [selectedTabIndex] so orbs carry across tabs.
 * Depth gives near/far parallax-of-scale (size + softness + brightness) and each orb has its own
 * brightness so no two read alike. Theme switches crossfade the colours seamlessly.
 *
 * Performance note: physics runs in a `withFrameNanos` loop that mutates plain (non-snapshot)
 * bubble objects; only a single frame-tick state is read inside the draw lambda, so each frame
 * invalidates the **draw phase only** — there is no per-frame recomposition to stall input.
 */
@Composable
fun GlassBubbleBackground(
    palette: GlassPalette,
    selectedTabIndex: Int,
    tabCount: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Tuning that matched the approved mockup. Expressed in dp so it feels identical across densities.
    val driftSpeedPx = with(density) { 54.dp.toPx() }
    val sizeMaxPx = with(density) { 64.dp.toPx() }
    val depthContrast = 0.7f
    val glowAlpha = 0.62f
    val bubbleCount = 22
    val glideMillis = 620f
    val colorCount = 3

    // Animated palette colours -> seamless crossfade when the Copper/Teal toggle flips.
    val spec = tween<Color>(durationMillis = 600)
    val pageTop by animateColorAsState(palette.pageTop, spec, label = "page_top")
    val pageBottom by animateColorAsState(palette.pageBottom, spec, label = "page_bottom")
    val auraBlue by animateColorAsState(palette.auraBlue, spec, label = "aura_blue")
    val auraGreen by animateColorAsState(palette.auraGreen, spec, label = "aura_green")
    val auraCyan by animateColorAsState(palette.auraCyan, spec, label = "aura_cyan")

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val cameraX = remember { mutableFloatStateOf(0f) }
    val frameTick = remember { mutableLongStateOf(0L) }
    val currentTab by rememberUpdatedState(selectedTabIndex)

    // Field is (re)built only when the screen size or tab count changes. Fixed seed keeps the
    // composition stable across recompositions; sorted far -> near so nearer orbs draw on top.
    val bubbles = remember(canvasSize, tabCount) {
        if (canvasSize == IntSize.Zero) {
            emptyList()
        } else {
            generateBubbles(
                count = bubbleCount,
                worldWidth = worldWidthFor(canvasSize.width.toFloat(), tabCount),
                worldHeight = canvasSize.height.toFloat(),
                speed = driftSpeedPx,
                sizeMax = sizeMaxPx,
                depthContrast = depthContrast,
                colorCount = colorCount,
                random = Random(0x5EEDL),
            ).sortedBy { it.depth }
        }
    }

    LaunchedEffect(canvasSize, bubbles, tabCount) {
        if (canvasSize == IntSize.Zero || bubbles.isEmpty()) return@LaunchedEffect
        val screenW = canvasSize.width.toFloat()
        val worldW = worldWidthFor(screenW, tabCount)
        val worldH = canvasSize.height.toFloat()
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                val target = cameraTargetForTab(currentTab, screenW)
                cameraX.floatValue = easeCamera(cameraX.floatValue, target, dt, glideMillis)
                for (b in bubbles) stepBubble(b, dt, worldW, worldH)
                frameTick.longValue = now
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it },
    ) {
        frameTick.longValue // subscribe the draw phase to the frame clock

        // Base page wash (mirrors the original GlassAmbientBackground, now with animated colours).
        drawRect(
            Brush.linearGradient(
                colors = listOf(pageTop, pageBottom),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, auraGreen.copy(alpha = 0.08f), Color.Transparent),
                start = Offset(size.width * 0.08f, 0f),
                end = Offset(size.width * 0.92f, size.height),
            ),
        )

        // Bubbles: additive soft orbs, drawn through the camera so they span tabs.
        val cam = cameraX.floatValue
        for (b in bubbles) {
            val sx = b.x - cam
            if (sx < -b.radius || sx > size.width + b.radius) continue
            val base = when (b.colorIndex) {
                0 -> auraBlue
                1 -> auraGreen
                else -> auraCyan
            }
            val a = (glowAlpha * b.brightness * (0.35f + 0.65f * b.depth)).coerceIn(0f, 1f)
            val center = Offset(sx, b.y)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to base.copy(alpha = a),
                    0.45f to base.copy(alpha = a * 0.45f),
                    1f to Color.Transparent,
                    center = center,
                    radius = b.radius,
                ),
                radius = b.radius,
                center = center,
                blendMode = BlendMode.Plus,
            )
        }
    }
}
