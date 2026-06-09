package com.ayman.ecolift.ui.background

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * One drifting orb in the living-glass wallpaper, expressed in **world** coordinates.
 *
 * The world is `tabCount` screens wide and one screen tall. Bubbles ricochet off the world's
 * outer edges only; each tab is a viewport onto the same world, so an orb that drifts off the
 * right of one tab reappears entering the next. Depth (0 = far, 1 = near) drives size, softness
 * and brightness; [brightness] additionally carries a per-orb random factor so no two read alike.
 */
class Bubble(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val depth: Float = 0.5f,
    val brightness: Float = 1f,
    val colorIndex: Int = 0,
)

/** Total wallpaper width: one screen per tab. */
fun worldWidthFor(screenWidth: Float, tabCount: Int): Float = screenWidth * tabCount

/** Where the camera should sit to centre [tabIndex] in the viewport. */
fun cameraTargetForTab(tabIndex: Int, screenWidth: Float): Float = tabIndex * screenWidth

/**
 * Advance one bubble by [dt] seconds and ricochet it off the world edges like an old DVD
 * screensaver: clamp back inside and invert the offending velocity component.
 */
fun stepBubble(b: Bubble, dt: Float, worldWidth: Float, worldHeight: Float) {
    b.x += b.vx * dt
    b.y += b.vy * dt
    val r = b.radius
    if (b.x < r) {
        b.x = r
        b.vx = -b.vx
    } else if (b.x > worldWidth - r) {
        b.x = worldWidth - r
        b.vx = -b.vx
    }
    if (b.y < r) {
        b.y = r
        b.vy = -b.vy
    } else if (b.y > worldHeight - r) {
        b.y = worldHeight - r
        b.vy = -b.vy
    }
}

/**
 * Frame-rate-independent exponential glide of the camera toward [target]. [glideMillis] is the
 * rough time-to-settle; smaller is snappier. Never overshoots.
 */
fun easeCamera(current: Float, target: Float, dt: Float, glideMillis: Float): Float {
    val k = 1f - 0.001f.pow(dt * (1400f / glideMillis))
    return current + (target - current) * k
}

/**
 * Build a fresh field of [count] bubbles scattered across the world. All share the same drift
 * [speed] (uniform "lava-lamp" pace); [depthContrast] (0..1) controls how much nearer orbs grow
 * relative to far ones, and each orb gets its own random brightness.
 */
fun generateBubbles(
    count: Int,
    worldWidth: Float,
    worldHeight: Float,
    speed: Float,
    sizeMax: Float,
    depthContrast: Float,
    colorCount: Int,
    random: Random,
): List<Bubble> = List(count) { i ->
    val depth = random.nextFloat()
    val depthScale = 1f - depthContrast * (1f - depth)
    val radius = sizeMax * (0.4f + 0.6f * depthScale)
    val angle = random.nextFloat() * (2f * PI.toFloat())
    val brightness = 0.5f + random.nextFloat() * 0.5f
    val x = radius + random.nextFloat() * (worldWidth - 2f * radius)
    val y = radius + random.nextFloat() * (worldHeight - 2f * radius)
    Bubble(
        x = x,
        y = y,
        vx = cos(angle) * speed,
        vy = sin(angle) * speed,
        radius = radius,
        depth = depth,
        brightness = brightness,
        colorIndex = i % colorCount,
    )
}
