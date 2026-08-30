package com.dchernykh.bridges.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun boardsOf(level: Level): List<Puzzle> =
    splitBoards(File("src/main/assets/${level.assetName}").readText()).map { decodeGrid(it)!! }

/**
 * The promise the collection makes, checked against the collection.
 *
 * The header of every file in assets/boards says the boards it holds have "a
 * single solution reachable by deduction alone". That was checked by the Zepp OS
 * generator on a computer; this checks the same thing again, with this port's own
 * solver, which is what proves both the boards and the solver came across intact.
 *
 * The whole collection is 1,671 boards, and proving forced solvability on the
 * largest of them is not free, so the exhaustive pass runs over a sample of each
 * size and the small boards are checked in full.
 */
class CollectionTest {
    @Test
    fun `every small board has exactly one solution, reachable without guessing`() {
        for ((index, puzzle) in boardsOf(Level.SMALL).withIndex()) {
            assertTrue("7x7 board $index is not unique", hasUniqueSolution(puzzle))
            assertTrue("7x7 board $index needs a guess", isForcedSolvable(puzzle))
        }
    }

    @Test
    fun `a sample of every larger size holds up too`() {
        for (level in listOf(Level.MEDIUM, Level.LARGE, Level.HUGE)) {
            val boards = boardsOf(level)
            // Every twenty-fifth board, which walks the whole file rather than
            // stopping at the front of it.
            for (index in boards.indices step 25) {
                assertTrue("$level board $index is not unique", hasUniqueSolution(boards[index]))
                assertTrue("$level board $index needs a guess", isForcedSolvable(boards[index]))
            }
        }
    }

    @Test
    fun `the solution the solver finds is a solution the rules accept`() {
        for (level in Level.entries) {
            val boards = boardsOf(level)
            for (index in boards.indices step 40) {
                val puzzle = boards[index]
                val solution = solvePuzzle(puzzle, limit = 1).solution
                assertNotNull("$level board $index has no solution at all", solution)
                assertTrue("$level board $index solution is not solved", puzzle.isSolved(solution!!))
            }
        }
    }
}

class SolvePuzzleTest {
    /** Two islands facing each other, each needing one bridge: exactly one answer. */
    private val pair = decodeGrid("1.1")!!

    @Test
    fun `finds the one answer to a board with one`() {
        val result = solvePuzzle(pair)

        assertEquals(1, result.count)
        assertNotNull(result.solution)
        assertTrue(pair.isSolved(result.solution!!))
        assertFalse(result.exhausted)
    }

    @Test
    fun `finds nothing on a board that cannot be finished`() {
        // One island asking for a bridge with nowhere to put it.
        assertEquals(0, solvePuzzle(decodeGrid("1..")!!).count)
    }

    @Test
    fun `counts no further than it was asked to`() {
        assertTrue(solvePuzzle(pair, limit = 1).count <= 1)
    }

    @Test
    fun `gives up rather than hanging when the budget runs out`() {
        val big = decodeGrid(File("src/main/assets/${Level.HUGE.assetName}").readText().let { splitBoards(it)[0] })!!

        val result = solvePuzzle(big, maxNodes = 1)

        assertTrue(result.exhausted)
        // And a board it could not prove is never called unique.
        assertFalse(hasUniqueSolution(big, maxNodes = 1))
    }

    @Test
    fun `has nothing to solve on a board with no islands`() {
        val empty = solvePuzzle(decodeGrid("...\n...")!!)

        assertEquals(0, empty.count)
        assertEquals(0, empty.nodes)
    }
}

class RulesTest {
    @Test
    fun `finds more answers once a rule is switched off`() {
        // Two pairs side by side, each pair needing one bridge, with the two
        // crossing routes available: the no-crossing rule is what makes the answer
        // unique, so switching it off has to change what the solver finds.
        val boards = splitBoards(File("src/main/assets/${Level.MEDIUM.assetName}").readText())
        var foundOne = false
        for (block in boards.take(60)) {
            val puzzle = decodeGrid(block)!!
            val withRules = solvePuzzle(puzzle, limit = 4)
            val without = solvePuzzle(puzzle, limit = 4, rules = Rules(crossings = false, connectivity = false))
            if (without.count > withRules.count) {
                foundOne = true
                break
            }
        }
        assertTrue("no board in the sample needed either rule", foundOne)
    }
}

class ForcedSolvableTest {
    @Test
    fun `calls a board with nothing to deduce unsolvable`() {
        assertFalse(isForcedSolvable(decodeGrid("...\n...")!!))
    }

    @Test
    fun `follows a chain of forced moves to the end`() {
        assertTrue(isForcedSolvable(decodeGrid("1.1")!!))
    }

    @Test
    fun `refuses a board that contradicts itself`() {
        // An island asking for more bridges than its neighbours can take.
        assertFalse(isForcedSolvable(decodeGrid("8.1")!!))
    }
}
