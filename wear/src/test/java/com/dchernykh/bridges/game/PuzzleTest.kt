package com.dchernykh.bridges.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Three islands in a row: 1 - 2 - 1, which has exactly one answer. */
private fun row() = decodeGrid("1.2.1")!!

/**
 * A diamond of four islands: the lane across the middle and the lane down it pass
 * over the same empty cell, so one rules the other out.
 */
private fun cross() = decodeGrid(".1.\n1.1\n.1.")!!

class EdgesTest {
    @Test
    fun `joins only islands that share a row or a column with nothing between`() {
        val puzzle = row()

        assertEquals(2, puzzle.edges.size)
        assertEquals(setOf(0 to 1, 1 to 2), puzzle.edges.map { it.a to it.b }.toSet())
    }

    @Test
    fun `never joins two islands across a third`() {
        // The middle island splits the row into two edges rather than leaving a
        // long one that runs over it.
        assertNull(row().edgeBetween(0, 2))
    }

    @Test
    fun `numbers the islands in reading order`() {
        val puzzle = decodeGrid(".2.\n1.1")!!

        assertEquals(listOf(0, 1, 2), puzzle.islands.map { it.id })
        assertEquals(listOf(1 to 0, 0 to 1, 2 to 1), puzzle.islands.map { it.col to it.row })
    }

    @Test
    fun `knows which lanes would cross`() {
        val puzzle = cross()
        val horizontal = puzzle.edges.first { it.horizontal }

        // Every crossing is mutual, and a lane never crosses one of its own kind.
        for (edgeId in puzzle.crossings.indices) {
            for (other in puzzle.crossings[edgeId]) {
                assertTrue(edgeId in puzzle.crossings[other].toList())
                assertTrue(puzzle.edges[edgeId].horizontal != puzzle.edges[other].horizontal)
            }
        }
        assertTrue(horizontal.id in puzzle.edges.map { it.id })
    }
}

class PlacementTest {
    @Test
    fun `builds a bridge and counts it on both islands`() {
        val puzzle = row()
        val state = puzzle.withBridge(puzzle.emptyState(), 0, 1)!!

        assertEquals(1, puzzle.degree(state, 0))
        assertEquals(1, puzzle.degree(state, 1))
        assertEquals(0, puzzle.remaining(state, 0))
        assertEquals(IslandStatus.DONE, puzzle.statusOf(state, 0))
        assertEquals(IslandStatus.UNDER, puzzle.statusOf(state, 1))
    }

    @Test
    fun `refuses to overshoot an island's number`() {
        val puzzle = row()

        // Island 0 asks for one bridge, so a double would overshoot it.
        assertFalse(puzzle.canPlace(puzzle.emptyState(), 0, 2))
        assertNull(puzzle.withBridge(puzzle.emptyState(), 0, 2))
    }

    @Test
    fun `refuses more than two bridges, or fewer than none`() {
        val puzzle = decodeGrid("4.4")!!

        assertFalse(puzzle.canPlace(puzzle.emptyState(), 0, 3))
        assertFalse(puzzle.canPlace(puzzle.emptyState(), 0, -1))
        assertTrue(puzzle.canPlace(puzzle.emptyState(), 0, MAX_BRIDGES))
    }

    @Test
    fun `refuses a lane a bridge already crosses`() {
        val puzzle = cross()
        val vertical = puzzle.edges.first { !it.horizontal }
        val horizontal = puzzle.edges.first { it.horizontal && puzzle.crossings[it.id].isNotEmpty() }

        val state = puzzle.withBridge(puzzle.emptyState(), vertical.id, 1)!!

        assertTrue(puzzle.isBlocked(state, horizontal.id))
        assertFalse(puzzle.canPlace(state, horizontal.id, 1))
    }

    @Test
    fun `always allows taking a bridge away`() {
        val puzzle = cross()
        val vertical = puzzle.edges.first { !it.horizontal }
        val state = puzzle.withBridge(puzzle.emptyState(), vertical.id, 1)!!

        assertTrue(puzzle.canPlace(state, vertical.id, 0))
    }
}

class TapTest {
    @Test
    fun `cycles none, one, two and back to none`() {
        val puzzle = decodeGrid("4.4")!!
        var state = puzzle.emptyState()

        state = puzzle.withBridge(state, 0, puzzle.nextCount(state, 0))!!
        assertEquals(1, state[0])
        state = puzzle.withBridge(state, 0, puzzle.nextCount(state, 0))!!
        assertEquals(2, state[0])
        state = puzzle.withBridge(state, 0, puzzle.nextCount(state, 0))!!
        assertEquals(0, state[0])
    }

    @Test
    fun `skips a step the rules forbid`() {
        // Both islands want one bridge, so two is never on the way round.
        val puzzle = decodeGrid("1.1")!!
        var state = puzzle.emptyState()

        state = puzzle.withBridge(state, 0, puzzle.nextCount(state, 0))!!
        assertEquals(1, state[0])
        assertEquals(0, puzzle.nextCount(state, 0))
    }

    @Test
    fun `leaves a pair alone when no change at all is legal`() {
        val puzzle = cross()
        val vertical = puzzle.edges.first { !it.horizontal }
        val horizontal = puzzle.edges.first { it.horizontal && vertical.id in puzzle.crossings[it.id].toList() }
        val state = puzzle.withBridge(puzzle.emptyState(), vertical.id, 1)!!

        assertEquals(state[horizontal.id], puzzle.nextCount(state, horizontal.id))
    }

    @Test
    fun `lists where the selected island may still go`() {
        val puzzle = row()
        val moves = puzzle.movesFrom(puzzle.emptyState(), 1)

        assertEquals(2, moves.size)
        assertTrue(moves.all { it.buildable })
        assertEquals(setOf(0, 2), moves.map { it.island }.toSet())
    }

    @Test
    fun `stops offering a lane once it is full`() {
        val puzzle = row()
        val state = puzzle.withBridge(puzzle.emptyState(), 0, 1)!!
        val moves = puzzle.movesFrom(state, 0)

        // Island 0 is finished, so the only move left takes its bridge away again.
        assertTrue(moves.none { it.buildable })
    }
}

class SolvedTest {
    @Test
    fun `is solved when every island has its number and all are joined`() {
        val puzzle = row()
        var state = puzzle.withBridge(puzzle.emptyState(), 0, 1)!!
        state = puzzle.withBridge(state, 1, 1)!!

        assertTrue(puzzle.isSolved(state))
    }

    @Test
    fun `is not solved while an island still wants a bridge`() {
        val puzzle = row()
        val state = puzzle.withBridge(puzzle.emptyState(), 0, 1)!!

        assertFalse(puzzle.isSolved(state))
    }

    @Test
    fun `is not solved when the bridges leave two separate groups`() {
        // Two pairs, each finished on its own, with no bridge between them.
        val puzzle = decodeGrid("1.1\n...\n1.1")!!
        var state = puzzle.withBridge(puzzle.emptyState(), 0, 1)!!
        val second = puzzle.edges.first { it.horizontal && it.id != 0 }
        state = puzzle.withBridge(state, second.id, 1)!!

        assertFalse("two groups is not one board", puzzle.isConnected(state))
        assertFalse(puzzle.isSolved(state))
    }

    @Test
    fun `calls a board of one island connected`() {
        assertTrue(decodeGrid("1")!!.isConnected(IntArray(0)))
    }
}

class TouchingIslandsTest {
    @Test
    fun `gives two islands in touching cells no edge at all`() {
        // Nowhere between them for a bridge to be drawn, so the rules do not join
        // them - and the board is not left with an edge that cannot be seen.
        assertEquals(emptyList<Edge>(), decodeGrid("11")!!.edges)
        assertEquals(emptyList<Edge>(), decodeGrid("1\n1")!!.edges)
    }

    @Test
    fun `still joins the next island along`() {
        val puzzle = decodeGrid("1.1")!!

        assertEquals(1, puzzle.edges.size)
        assertEquals(MIN_SPAN, puzzle.edges[0].to - puzzle.edges[0].from)
    }
}
