package com.dchernykh.bridges.layout

import com.dchernykh.bridges.game.Level
import com.dchernykh.bridges.game.playableCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WATCH = 466

private fun layoutOf(
    level: Level,
    size: Int = WATCH,
) = BoardLayout(size, level.cols, level.rows)

class AxisBoundsTest {
    @Test
    fun `lets a board larger than the screen scroll from edge to edge`() {
        val bounds = axisBounds(worldSize = 1000, viewSize = 400, overscroll = 0)

        assertEquals(0, bounds.first)
        assertEquals(600, bounds.last)
    }

    @Test
    fun `widens both ends by the overscroll so a corner can be pulled in`() {
        val bounds = axisBounds(worldSize = 1000, viewSize = 400, overscroll = 80)

        assertEquals(-80, bounds.first)
        assertEquals(680, bounds.last)
    }

    @Test
    fun `centres a board smaller than the screen and gives it the same slack`() {
        val bounds = axisBounds(worldSize = 300, viewSize = 400, overscroll = 80)

        assertEquals(-50 - 80, bounds.first)
        assertEquals(-50 + 80, bounds.last)
    }

    @Test
    fun `holds the camera inside those bounds`() {
        assertEquals(680, clampAxis(9999, 1000, 400, 80))
        assertEquals(-80, clampAxis(-9999, 1000, 400, 80))
        assertEquals(123, clampAxis(123, 1000, 400, 80))
    }
}

class CenterCameraTest {
    @Test
    fun `opens with the middle of the board in the middle of the screen`() {
        val layout = layoutOf(Level.HUGE)
        val camera = centerCamera(layout, WATCH)

        assertEquals(layout.width / 2, camera.x + WATCH / 2)
        assertEquals(layout.height / 2, camera.y + WATCH / 2)
    }

    @Test
    fun `centres a small board too, rather than pinning it to a corner`() {
        val layout = layoutOf(Level.SMALL)
        val camera = centerCamera(layout, WATCH)

        assertEquals((layout.width - WATCH) / 2, camera.x)
    }
}

class PanTest {
    @Test
    fun `moves the map with the finger, not against it`() {
        val layout = layoutOf(Level.HUGE)
        val start = centerCamera(layout, WATCH)
        val dragged = panBy(start, dx = 30, dy = -20, layout = layout, viewSize = WATCH)

        assertEquals(start.x - 30, dragged.x)
        assertEquals(start.y + 20, dragged.y)
    }

    @Test
    fun `stops the map running off into empty space`() {
        val layout = layoutOf(Level.HUGE)
        val far = panBy(centerCamera(layout, WATCH), dx = 99999, dy = 99999, layout = layout, viewSize = WATCH)
        val bounds = axisBounds(layout.width, WATCH, overscrollFor(WATCH))

        assertEquals(bounds.first, far.x)
        assertEquals(bounds.first, far.y)
    }

    @Test
    fun `lets every corner island be dragged fully onto the glass`() {
        // The bezel cuts the corners off a square board, so the overscroll has to be
        // enough to bring the outermost island of every shipped size into view.
        for (level in Level.entries) {
            val layout = layoutOf(level)
            for (cell in playableCells(layout.cols, layout.rows)) {
                val wanted =
                    Camera(
                        cellCenterX(layout, cell.col) - WATCH / 2,
                        cellCenterY(layout, cell.row) - WATCH / 2,
                    )
                val camera = clampCamera(wanted, layout, WATCH)
                val x = cellCenterX(layout, cell.col) - camera.x
                val y = cellCenterY(layout, cell.row) - camera.y
                assertTrue(
                    "${level.label} (${cell.col},${cell.row}) can be brought into view",
                    discIsOnScreen(WATCH, x, y, layout.radius),
                )
            }
        }
    }
}

class OnScreenTest {
    @Test
    fun `calls a disc in the middle of the glass visible`() {
        assertTrue(discIsOnScreen(WATCH, WATCH / 2, WATCH / 2, 20))
    }

    @Test
    fun `calls a disc hanging over the bezel hidden`() {
        assertFalse(discIsOnScreen(WATCH, WATCH / 2, 1, 20))
        assertFalse("a square screen's corner is off a round one", discIsOnScreen(WATCH, 10, 10, 1))
    }

    @Test
    fun `stops exactly at the edge of the glass`() {
        // 20px from the top of a 466px face: the glass reaches exactly that far.
        assertTrue(discIsOnScreen(WATCH, WATCH / 2, 20, 20))
        assertFalse(discIsOnScreen(WATCH, WATCH / 2, 20, 21))
    }
}

class NeedsPanningTest {
    @Test
    fun `says a small board needs no dragging on this watch`() {
        assertFalse(needsPanning(layoutOf(Level.SMALL), WATCH))
    }

    @Test
    fun `says the larger boards do`() {
        assertTrue(needsPanning(layoutOf(Level.LARGE), WATCH))
        assertTrue(needsPanning(layoutOf(Level.HUGE), WATCH))
    }

    @Test
    fun `asks only about cells a puzzle may use`() {
        // The grid's corners are always outside the round playfield, so a board that
        // fits within the disc is not called too big because of them.
        val layout = layoutOf(Level.SMALL)
        val corner = cellCenterX(layout, 0) - centerCamera(layout, WATCH).x

        assertFalse(discIsOnScreen(WATCH, corner, corner, layout.radius))
        assertFalse(needsPanning(layout, WATCH))
    }
}
