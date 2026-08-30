package com.dchernykh.bridges.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How many islands the round playfield could hold at the very most: islands may not
 * sit in touching cells, so no more than one of every two on a chequerboard.
 */
private fun islandCapacity(
    cols: Int,
    rows: Int,
): Int = playableCells(cols, rows).count { (it.col + it.row) % 2 == 0 }

class PlayfieldTest {
    @Test
    fun `keeps a puzzle off the corners of its own grid`() {
        // The grid is a square and the watch screen is a circle, so the corners
        // land where the glass is not.
        for (level in Level.entries) {
            assertFalse("$level", isPlayable(level.cols, level.rows, 0, 0))
            assertFalse("$level", isPlayable(level.cols, level.rows, level.cols - 1, level.rows - 1))
            assertTrue("$level", isPlayable(level.cols, level.rows, level.cols / 2, level.rows / 2))
        }
    }

    @Test
    fun `reports nothing outside the grid at all`() {
        assertFalse(isPlayable(7, 7, -1, 3))
        assertFalse(isPlayable(7, 7, 3, 7))
    }

    @Test
    fun `keeps the disc it promises, and nothing outside it`() {
        for (level in Level.entries) {
            val radius = playfieldRadius(level.cols, level.rows)
            for (cell in playableCells(level.cols, level.rows)) {
                val dx = cell.col + 0.5f - level.cols / 2f
                val dy = cell.row + 0.5f - level.rows / 2f
                assertTrue("$level $cell", dx * dx + dy * dy <= radius * radius + 0.001f)
            }
        }
    }

    @Test
    fun `has room for the islands every size asks for`() {
        // A size asking for more islands than the disc can hold could never be
        // generated at all.
        for (level in Level.entries) {
            assertTrue(
                "$level wants ${level.islands} of ${islandCapacity(level.cols, level.rows)}",
                level.islands <= islandCapacity(level.cols, level.rows),
            )
        }
    }

    @Test
    fun `keeps the twenty-nine cells the smallest board is documented to have`() {
        assertEquals(29, playableCells(7, 7).size)
    }
}

class RandomTest {
    @Test
    fun `gives the same run from the same seed`() {
        val first = List(20) { Mulberry32(1234).nextFloat() }
        val second = List(20) { Mulberry32(1234).nextFloat() }

        assertEquals(first, second)
    }

    @Test
    fun `gives a different run from a different seed`() {
        assertTrue(Mulberry32(1).nextFloat() != Mulberry32(2).nextFloat())
    }

    @Test
    fun `stays inside the unit interval`() {
        val random = Mulberry32(99)
        repeat(5_000) {
            val value = random.nextFloat()
            assertTrue("$value", value >= 0f && value < 1f)
        }
    }

    @Test
    fun `stays inside the bound it was given`() {
        val random = Mulberry32(7)
        repeat(5_000) { assertTrue(random.nextInt(10) in 0..9) }
        assertEquals(0, Mulberry32(7).nextInt(0))
        assertEquals(0, Mulberry32(7).nextInt(-3))
    }

    @Test
    fun `shuffles without losing or inventing anything`() {
        val items = (1..30).toList()
        val shuffled = Mulberry32(3).shuffled(items)

        assertEquals(items.sorted(), shuffled.sorted())
        assertTrue("a shuffle that changed nothing is a suspicious shuffle", shuffled != items)
        assertEquals(items, items)
    }
}

class GenerateTest {
    @Test
    fun `builds a board that solves itself, on every size`() {
        // The answer is known by construction, which is the whole point of building
        // a board rather than searching for one. A single seed is allowed to come
        // to nothing - a layout that grew too small is the documented way of saying
        // "try another one" - so this asks for a board the way the game does.
        for (level in Level.entries) {
            val config = configFor(level)
            val generated = generatePuzzle(config, seed = 20260808)
            assertNotNull("$level built nothing", generated)
            assertTrue("$level does not solve itself", generated!!.puzzle.isSolved(generated.solution))
            assertTrue("$level came out too small", generated.puzzle.islands.size >= config.minIslands)
        }
    }

    @Test
    fun `comes to nothing on some seeds and to a board on most`() {
        // Growing a layout can paint itself into a corner, and the answer to that is
        // another seed rather than a worse board. It has to be the exception: a
        // generator that failed most of the time would spend the retry budget
        // getting nowhere.
        val config = configFor(Level.SMALL)
        val built = (0 until 40).count { generateCandidate(config, it) != null }

        assertTrue("only $built of 40 seeds produced a layout", built >= 20)
    }

    @Test
    fun `keeps every island inside the round playfield`() {
        for (level in Level.entries) {
            val puzzle = generatePuzzle(configFor(level), seed = 7)!!.puzzle
            for (island in puzzle.islands) {
                assertTrue(
                    "$level island at ${island.col},${island.row} is off the glass",
                    isPlayable(level.cols, level.rows, island.col, island.row),
                )
            }
        }
    }

    @Test
    fun `never asks an island for more than it can hold`() {
        for (level in Level.entries) {
            val puzzle = generatePuzzle(configFor(level), seed = 11)!!.puzzle
            for (island in puzzle.islands) {
                assertTrue("$level", island.required in 1..MAX_REQUIRED)
            }
        }
    }

    @Test
    fun `builds the same board from the same seed`() {
        // Reproducibility is what lets a board that turned out badly be replayed in
        // a test from nothing but its seed.
        val config = configFor(Level.SMALL)
        val first = generatePuzzle(config, seed = 42)!!
        val second = generatePuzzle(config, seed = 42)!!

        assertEquals(first.seed, second.seed)
        assertEquals(encodeGrid(first.puzzle), encodeGrid(second.puzzle))
    }

    @Test
    fun `builds a different board from a different seed`() {
        val config = configFor(Level.SMALL)
        val boards = (0 until 8).map { encodeGrid(generatePuzzle(config, seed = it)!!.puzzle) }

        assertTrue("the seed made no difference", boards.toSet().size > 1)
    }

    @Test
    fun `passes its own quality gates on the small board`() {
        // The two properties that make a board worth playing: one answer, and one
        // reachable without guessing. This is the gate the generator applies to
        // itself, run here so a change to either half is caught.
        for (seed in listOf(1, 2, 3, 5, 8)) {
            val generated = generatePuzzle(configFor(Level.SMALL), seed)
            assertNotNull("seed $seed produced nothing at all", generated)
            assertTrue("seed $seed is not fair", generated!!.unique && generated.fair)
            assertTrue(generated.puzzle.isSolved(generated.solution))
        }
    }

    @Test
    fun `plays a merely-solvable board rather than showing an error`() {
        // With no attempts allowed the gates can never be met, and the generator is
        // required to hand back a playable board anyway: a worse puzzle is a far
        // better watch face than an error message.
        // A seed that does produce a layout, so the only thing standing between it
        // and the gates is the budget.
        val config = configFor(Level.SMALL).copy(attempts = 1, maxNodes = 1)

        val generated = generatePuzzle(config, seed = 2)

        assertNotNull(generated)
        assertFalse("nothing could be proved with a budget of one node", generated!!.unique)
        assertTrue("but it still has to be a board", generated.puzzle.isSolved(generated.solution))
    }
}
