package com.ayman.ecolift.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Pure, Compose-free physics for the living-glass wallpaper. The bubble field is one
 * continuous world that is `tabCount` screens wide; each tab is a viewport onto it and the
 * camera slides between them. Bubbles ricochet off the world's outer edges only.
 */
class BubbleFieldTest {

    private fun bubble(x: Float, y: Float, vx: Float, vy: Float, radius: Float) =
        Bubble(x = x, y = y, vx = vx, vy = vy, radius = radius)

    @Test
    fun `world width spans one screen per tab`() {
        assertEquals(1800f, worldWidthFor(screenWidth = 360f, tabCount = 5), 0.001f)
    }

    @Test
    fun `camera target is the tab index times screen width`() {
        assertEquals(0f, cameraTargetForTab(tabIndex = 0, screenWidth = 360f), 0.001f)
        assertEquals(720f, cameraTargetForTab(tabIndex = 2, screenWidth = 360f), 0.001f)
    }

    @Test
    fun `bubble drifts by velocity times dt in open space`() {
        val b = bubble(x = 500f, y = 400f, vx = 100f, vy = -50f, radius = 30f)
        stepBubble(b, dt = 0.5f, worldWidth = 1800f, worldHeight = 800f)
        assertEquals(550f, b.x, 0.001f)
        assertEquals(375f, b.y, 0.001f)
        assertEquals(100f, b.vx, 0.001f) // unchanged in open space
        assertEquals(-50f, b.vy, 0.001f)
    }

    @Test
    fun `bubble ricochets off the left world edge`() {
        // would travel to x = -10; must clamp and reverse
        val b = bubble(x = 40f, y = 400f, vx = -100f, vy = 0f, radius = 30f)
        stepBubble(b, dt = 0.5f, worldWidth = 1800f, worldHeight = 800f)
        assertEquals(30f, b.x, 0.001f) // clamped to the radius margin
        assertTrue("vx reversed to positive", b.vx > 0f)
    }

    @Test
    fun `bubble ricochets off the right world edge`() {
        // would travel to x = 1820; world is 1800 wide
        val b = bubble(x = 1770f, y = 400f, vx = 100f, vy = 0f, radius = 30f)
        stepBubble(b, dt = 0.5f, worldWidth = 1800f, worldHeight = 800f)
        assertEquals(1770f, b.x, 0.001f) // worldWidth - radius
        assertTrue("vx reversed to negative", b.vx < 0f)
    }

    @Test
    fun `bubble ricochets off top and bottom edges`() {
        val top = bubble(x = 500f, y = 20f, vx = 0f, vy = -100f, radius = 30f)
        stepBubble(top, dt = 0.5f, worldWidth = 1800f, worldHeight = 800f)
        assertEquals(30f, top.y, 0.001f)
        assertTrue("vy reversed downward", top.vy > 0f)

        val bottom = bubble(x = 500f, y = 780f, vx = 0f, vy = 100f, radius = 30f)
        stepBubble(bottom, dt = 0.5f, worldWidth = 1800f, worldHeight = 800f)
        assertEquals(770f, bottom.y, 0.001f)
        assertTrue("vy reversed upward", bottom.vy < 0f)
    }

    @Test
    fun `camera eases toward the target without overshooting`() {
        var cam = 0f
        val target = 720f
        repeat(600) { cam = easeCamera(cam, target, dt = 1f / 60f, glideMillis = 620f) }
        assertEquals(720f, cam, 1f) // converged
        assertTrue("never overshoots the target", cam <= target + 0.001f)
    }

    @Test
    fun `generated bubbles vary in brightness and stay within the world`() {
        val bubbles = generateBubbles(
            count = 24,
            worldWidth = 1800f,
            worldHeight = 800f,
            speed = 60f,
            sizeMax = 120f,
            depthContrast = 0.7f,
            colorCount = 3,
            random = Random(42),
        )
        assertEquals(24, bubbles.size)
        bubbles.forEach { b ->
            assertTrue("x in world", b.x in 0f..1800f)
            assertTrue("y in world", b.y in 0f..800f)
            assertTrue("depth normalized", b.depth in 0f..1f)
            assertTrue("brightness in range", b.brightness in 0.5f..1f)
            assertTrue("color index valid", b.colorIndex in 0 until 3)
            assertEquals("uniform drift pace", 60f, hypot(b.vx, b.vy), 0.5f)
        }
        // Every bubble should have its own brightness, not a single shared value.
        assertNotEquals(bubbles[0].brightness, bubbles[1].brightness)
        assertTrue("brightness genuinely varied", bubbles.map { it.brightness }.distinct().size > 5)
    }

    @Test
    fun `depth contrast of zero makes every bubble the same size`() {
        val bubbles = generateBubbles(
            count = 12,
            worldWidth = 1800f,
            worldHeight = 800f,
            speed = 60f,
            sizeMax = 100f,
            depthContrast = 0f,
            colorCount = 3,
            random = Random(7),
        )
        val radii = bubbles.map { it.radius }.distinct()
        assertEquals("no depth contrast -> one radius", 1, radii.size)
    }
}
