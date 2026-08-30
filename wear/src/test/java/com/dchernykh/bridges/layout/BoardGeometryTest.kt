package com.dchernykh.bridges.layout

import com.dchernykh.bridges.game.Level
import com.dchernykh.bridges.game.Puzzle
import com.dchernykh.bridges.game.decodeGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The watch this is built for, and the smallest round screen it has to hold on. */
private val SCREEN_SIZES = listOf(384, 416, 450, 454, 466)

private fun board(size: Int = 466) = BoardLayout(size, Level.SMALL.cols, Level.SMALL.rows)

/** Three islands in a row and one below the first, which gives a lane of each kind. */
private fun puzzle(): Puzzle = decodeGrid("1.2.1\n.....\n1....")!!

private fun centerOf(
    layout: BoardLayout,
    puzzle: Puzzle,
    id: Int,
) = cellCenterX(layout, puzzle.islands[id].col) to cellCenterY(layout, puzzle.islands[id].row)

class BoardLayoutTest {
    @Test
    fun `scales every measurement with the screen`() {
        val small = board(384)
        val large = board(466)

        assertTrue(small.cell < large.cell)
        assertTrue(small.radius < large.radius)
        assertTrue(small.numberSize < large.numberSize)
    }

    @Test
    fun `keeps an island well inside its cell`() {
        for (size in SCREEN_SIZES) {
            val layout = board(size)
            assertTrue("$size: island fits its cell", 2 * layout.radius < layout.cell)
        }
    }

    @Test
    fun `keeps a double bridge inside the width of a lane`() {
        for (size in SCREEN_SIZES) {
            val layout = board(size)
            assertTrue(
                "$size: the pair does not spill out of the lane",
                2 * layout.bridgeGap + layout.bridge < layout.cell,
            )
            assertTrue("$size: the two lines do not touch", 2 * layout.bridgeGap > layout.bridge)
        }
    }

    @Test
    fun `never draws anything away to nothing on a small screen`() {
        val layout = BoardLayout(1, 7, 7)

        assertTrue(layout.cell >= 8)
        assertTrue(layout.bridge >= 2)
        assertTrue(layout.bridgeGap >= 2)
    }

    @Test
    fun `refuses a board with no cells in it`() {
        val layout = BoardLayout(466, 0, -3)

        assertEquals(1, layout.cols)
        assertEquals(1, layout.rows)
        assertEquals(layout.cell, layout.width)
        assertEquals(layout.cell, layout.height)
    }

    @Test
    fun `measures the world as the grid times the cell`() {
        val layout = board()

        assertEquals(Level.SMALL.cols * layout.cell, layout.width)
        assertEquals(Level.SMALL.rows * layout.cell, layout.height)
    }

    @Test
    fun `puts an island in the middle of its cell`() {
        val layout = board()

        // Half a cell in, and one cell apart from there on.
        assertTrue(cellCenterX(layout, 0) - layout.cell / 2 in 0..1)
        assertEquals(layout.cell, cellCenterX(layout, 1) - cellCenterX(layout, 0))
        assertEquals(cellCenterX(layout, 4), cellCenterY(layout, 4))
    }
}

class EdgeLineTest {
    @Test
    fun `runs from the edge of one island to the edge of the other`() {
        val layout = board()
        val puzzle = puzzle()
        val horizontal = puzzle.edges.first { it.horizontal }
        val (ax, ay) = centerOf(layout, puzzle, horizontal.a)
        val (bx, _) = centerOf(layout, puzzle, horizontal.b)
        val line = edgeLine(layout, puzzle, horizontal.id)

        assertEquals(ax + layout.radius, line.x1)
        assertEquals(bx - layout.radius, line.x2)
        assertEquals("a horizontal bridge stays level", ay, line.y1)
        assertEquals(line.y1, line.y2)
    }

    @Test
    fun `stops short at both ends so no bridge runs under a disc`() {
        val layout = board()
        val puzzle = puzzle()

        for (edge in puzzle.edges) {
            val line = edgeLine(layout, puzzle, edge.id)
            val span = if (edge.horizontal) line.x2 - line.x1 else line.y2 - line.y1
            val gap =
                if (edge.horizontal) {
                    cellCenterX(layout, puzzle.islands[edge.b].col) - cellCenterX(layout, puzzle.islands[edge.a].col)
                } else {
                    cellCenterY(layout, puzzle.islands[edge.b].row) - cellCenterY(layout, puzzle.islands[edge.a].row)
                }
            assertTrue("edge ${edge.id} is drawn", span > 0)
            assertEquals("edge ${edge.id} clears both discs", gap - 2 * layout.radius, span)
        }
    }

    @Test
    fun `runs a vertical bridge straight down`() {
        val layout = board()
        val puzzle = puzzle()
        val vertical = puzzle.edges.first { !it.horizontal }
        val line = edgeLine(layout, puzzle, vertical.id)

        assertEquals(line.x1, line.x2)
        assertTrue(line.y2 > line.y1)
    }
}

class BridgeRectsTest {
    @Test
    fun `draws nothing where no bridge has been built`() {
        assertTrue(bridgeRects(board(), puzzle(), 0, 0).isEmpty())
        assertTrue(bridgeRects(board(), puzzle(), 0, -1).isEmpty())
    }

    @Test
    fun `draws one line down the middle of a single bridge`() {
        val layout = board()
        val puzzle = puzzle()
        val horizontal = puzzle.edges.first { it.horizontal }
        val line = edgeLine(layout, puzzle, horizontal.id)
        val rects = bridgeRects(layout, puzzle, horizontal.id, 1)

        assertEquals(1, rects.size)
        assertEquals(line.y1, rects[0].y + rects[0].h / 2)
        assertEquals(layout.bridge, rects[0].h)
        assertEquals(line.x2 - line.x1, rects[0].w)
    }

    @Test
    fun `draws a double bridge as two lines either side of the middle`() {
        val layout = board()
        val puzzle = puzzle()
        val horizontal = puzzle.edges.first { it.horizontal }
        val line = edgeLine(layout, puzzle, horizontal.id)
        val rects = bridgeRects(layout, puzzle, horizontal.id, 2)

        assertEquals(2, rects.size)
        assertEquals(-layout.bridgeGap, rects[0].y + rects[0].h / 2 - line.y1)
        assertEquals(layout.bridgeGap, rects[1].y + rects[1].h / 2 - line.y1)
        assertTrue("the two lines are apart", rects[1].y > rects[0].y + rects[0].h)
    }

    @Test
    fun `draws a vertical bridge along its own axis`() {
        val layout = board()
        val puzzle = puzzle()
        val vertical = puzzle.edges.first { !it.horizontal }
        val rects = bridgeRects(layout, puzzle, vertical.id, 2)

        assertEquals(2, rects.size)
        for (rect in rects) {
            assertEquals(layout.bridge, rect.w)
            assertTrue(rect.h > rect.w)
        }
        assertTrue(rects[1].x > rects[0].x)
    }
}

class HitTestTest {
    @Test
    fun `catches a tap on an island`() {
        val layout = board()
        val puzzle = puzzle()
        val (x, y) = centerOf(layout, puzzle, 1)

        assertEquals(Hit.OnIsland(1), hitTest(puzzle, layout, x, y))
    }

    @Test
    fun `catches a tap a finger's width off an island`() {
        val layout = board()
        val puzzle = puzzle()
        val (x, y) = centerOf(layout, puzzle, 1)

        assertEquals(1, islandAt(puzzle, layout, x, y - layout.radius - layout.tapSlack + 1))
    }

    @Test
    fun `misses an island the tap was nowhere near`() {
        val layout = board()
        val puzzle = puzzle()
        val (x, y) = centerOf(layout, puzzle, 1)

        assertNull(islandAt(puzzle, layout, x, y - 3 * layout.cell))
    }

    @Test
    fun `gives a tap between two islands to the nearer one`() {
        val layout = board()
        val puzzle = puzzle()
        val (ax, ay) = centerOf(layout, puzzle, 0)
        val (bx, _) = centerOf(layout, puzzle, 1)

        assertEquals(0, islandAt(puzzle, layout, ax + (bx - ax) / 8, ay))
        assertEquals(1, islandAt(puzzle, layout, bx - (bx - ax) / 8, ay))
    }

    @Test
    fun `catches a tap on the lane between two islands`() {
        val layout = board()
        val puzzle = puzzle()
        val horizontal = puzzle.edges.first { it.horizontal }
        val line = edgeLine(layout, puzzle, horizontal.id)

        assertEquals(Hit.OnEdge(horizontal.id), hitTest(puzzle, layout, (line.x1 + line.x2) / 2, line.y1))
    }

    @Test
    fun `misses a lane the tap was too far above`() {
        val layout = board()
        val puzzle = puzzle()
        val horizontal = puzzle.edges.first { it.horizontal }
        val line = edgeLine(layout, puzzle, horizontal.id)
        val middle = (line.x1 + line.x2) / 2

        assertNotNull(edgeAt(puzzle, layout, middle, line.y1 + layout.bridgeTap))
        assertNull(edgeAt(puzzle, layout, middle, line.y1 + layout.bridgeTap + 1))
    }

    @Test
    fun `misses a lane the tap was past the end of`() {
        val layout = board()
        val puzzle = puzzle()
        val horizontal = puzzle.edges.first { it.horizontal }
        val line = edgeLine(layout, puzzle, horizontal.id)

        // Out along the same line, well beyond the island the lane ends at.
        assertNull(edgeAt(puzzle, layout, line.x2 + 3 * layout.cell, line.y1))
    }

    @Test
    fun `gives an island the tap when a lane ends there too`() {
        val layout = board()
        val puzzle = puzzle()
        val (x, y) = centerOf(layout, puzzle, 1)

        // Island 1 sits on two lanes; the smaller target is the one aimed at.
        assertEquals(Hit.OnIsland(1), hitTest(puzzle, layout, x, y))
    }

    @Test
    fun `finds nothing in an empty corner of the board`() {
        val layout = board()
        val puzzle = puzzle()

        assertNull(hitTest(puzzle, layout, layout.width - layout.cell / 2, layout.height - layout.cell / 2))
    }
}
