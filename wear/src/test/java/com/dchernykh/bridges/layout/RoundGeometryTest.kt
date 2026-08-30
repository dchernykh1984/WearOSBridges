package com.dchernykh.bridges.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** The round sizes the game is built for, and a small one for good measure. */
private val SCREENS = listOf(384, 450, 454, 466, 480)

/** The same inset the screens use; kept here so the test does not reach into the UI. */
private const val PADDING = 8

private fun assertCornersOnScreen(
    screenSize: Int,
    box: Box,
    what: String,
) {
    val radius = screenSize / 2f
    val corners =
        listOf(
            box.x to box.y,
            box.x + box.w to box.y,
            box.x to box.y + box.h,
            box.x + box.w to box.y + box.h,
        )
    for ((x, y) in corners) {
        assertTrue(
            "corner ($x, $y) of $what escapes a $screenSize screen",
            hypot(x - radius, y - radius) <= radius,
        )
    }
}

class SafeWidthTest {
    @Test
    fun `is widest on the centre line and narrows towards the edge`() {
        assertEquals(100f, safeHalfWidth(100f, 0f), 0.001f)
        assertEquals(80f, safeHalfWidth(100f, 60f), 0.001f)
        assertEquals(0f, safeHalfWidth(100f, 100f), 0.001f)
    }

    @Test
    fun `has no width at all past the edge of the circle`() {
        assertEquals(0f, safeHalfWidth(100f, 140f), 0.001f)
    }

    @Test
    fun `measures the same either side of the centre line`() {
        assertEquals(safeHalfWidth(100f, 42f), safeHalfWidth(100f, -42f), 0.001f)
    }

    @Test
    fun `binds a line by whichever of its edges is further out`() {
        val below = safeLineWidth(466, 300f, 40f, PADDING)
        val above = safeLineWidth(466, 166f, 40f, PADDING)

        assertEquals(above, below, 0.001f)
        assertTrue(below < safeLineWidth(466, 233f, 40f, PADDING))
    }

    @Test
    fun `gives nothing to a line pushed off the screen`() {
        assertEquals(0f, safeLineWidth(466, -50f, 40f, PADDING), 0.001f)
        assertEquals(0f, safeLineWidth(466, 520f, 40f, PADDING), 0.001f)
    }
}

class CenteredBoxTest {
    @Test
    fun `keeps every corner of a centred box on the screen`() {
        // The property that matters, and the one the buttons on the board screen
        // were missing before they were placed this way: wherever the box goes, the
        // bezel never slices a corner off it.
        for (screen in SCREENS) {
            var top = 0
            while (top < screen - 30) {
                val box = centeredBox(screen, top, 30, screen.toFloat(), PADDING)
                if (box.w > 0) assertCornersOnScreen(screen, box, "a box at $top")
                top += 7
            }
        }
    }

    @Test
    fun `never exceeds the width it was asked for`() {
        val box = centeredBox(466, 200, 40, 100f, PADDING)

        assertEquals(100, box.w)
        assertEquals(200, box.y)
        assertEquals(40, box.h)
    }

    @Test
    fun `centres what it places`() {
        for (screen in SCREENS) {
            val box = centeredBox(screen, screen / 2, 40, 120f, PADDING)
            assertEquals(screen - box.x - box.w, box.x)
        }
    }

    @Test
    fun `gives no width to a row that has left the glass`() {
        assertEquals(0, centeredBox(466, 600, 40, 200f, PADDING).w)
    }

    @Test
    fun `is narrower near the bezel than across the middle`() {
        val high = centeredBox(466, 40, 40, 466f, PADDING)
        val middle = centeredBox(466, 213, 40, 466f, PADDING)

        assertTrue(high.w < middle.w)
    }
}

class BoxTest {
    @Test
    fun `knows what it contains`() {
        val box = Box(10, 20, 30, 40)

        assertTrue((10 to 20) in box)
        assertTrue((39 to 59) in box)
        assertTrue((9 to 20) !in box)
        assertTrue((40 to 20) !in box)
        assertTrue((10 to 60) !in box)
        assertTrue((10 to 19) !in box)
    }
}
