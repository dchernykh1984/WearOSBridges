package com.dchernykh.bridges.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped collection, read from the same files the app reads.
 *
 * The four files are the Zepp OS app's own, byte for byte, so a board played on an
 * Amazfit watch is the same board here - and these tests are what keeps that
 * true: they read every one of the 1,671 boards, decode it, and check it is a
 * board the rules can actually be played on.
 */
private fun boardsFile(level: Level): String = File("src/main/assets/${level.assetName}").readText()

class BoardFileTest {
    @Test
    fun `ships the boards the collection promises`() {
        // The counts the Zepp OS files were generated with. A file that lost or
        // gained a board would mean the collection had drifted from the original.
        val expected = mapOf(Level.SMALL to 171, Level.MEDIUM to 700, Level.LARGE to 500, Level.HUGE to 300)

        for ((level, count) in expected) {
            assertEquals("$level", count, splitBoards(boardsFile(level)).size)
        }
    }

    @Test
    fun `decodes every board it ships`() {
        for (level in Level.entries) {
            for ((index, block) in splitBoards(boardsFile(level)).withIndex()) {
                val puzzle = decodeGrid(block)
                assertNotNull("$level board $index will not decode", puzzle)
                assertEquals("$level board $index", level.cols, puzzle!!.cols)
                assertEquals("$level board $index", level.rows, puzzle.rows)
            }
        }
    }

    @Test
    fun `writes every board back exactly as it was written down`() {
        // Decoding and re-encoding is what proves the reader understood the file
        // rather than merely survived it.
        for (level in Level.entries) {
            for ((index, block) in splitBoards(boardsFile(level)).withIndex()) {
                assertEquals("$level board $index", block, encodeGrid(decodeGrid(block)!!))
            }
        }
    }

    @Test
    fun `ships only boards the rules can be played on`() {
        for (level in Level.entries) {
            for ((index, block) in splitBoards(boardsFile(level)).withIndex()) {
                val puzzle = decodeGrid(block)!!
                val where = "$level board $index"
                assertTrue("$where has no islands", puzzle.islands.isNotEmpty())
                // Every island's number has to be reachable: four directions, two
                // bridges each, and never more than its neighbours can take.
                for (island in puzzle.islands) {
                    assertTrue("$where island ${island.id}", island.required in 1..MAX_REQUIRED)
                    val reach = puzzle.edgesByIsland[island.id].size * MAX_BRIDGES
                    assertTrue("$where island ${island.id} cannot be satisfied", reach >= island.required)
                }
                // And the island numbers have to add up to a whole number of
                // bridges, or no arrangement of them could ever be right.
                assertEquals("$where has an odd number of bridge ends", 0, puzzle.islands.sumOf { it.required } % 2)
            }
        }
    }
}

class DecodeGridTest {
    @Test
    fun `reads a grid into islands in reading order`() {
        val puzzle = decodeGrid(".2.\n...\n3.1")!!

        assertEquals(3, puzzle.cols)
        assertEquals(3, puzzle.rows)
        assertEquals(listOf(2, 3, 1), puzzle.islands.map { it.required })
        assertEquals(listOf(0, 1, 2), puzzle.islands.map { it.id })
    }

    @Test
    fun `refuses a grid that is not one`() {
        assertNull(decodeGrid(""))
        assertNull("ragged", decodeGrid("..\n..."))
        assertNull("not a digit", decodeGrid(".x."))
        assertNull("nine is past what an island can hold", decodeGrid(".9."))
        assertNull("zero is not an island", decodeGrid(".0."))
    }

    @Test
    fun `writes a grid back as it was read`() {
        val text = "...3...\n.2...4.\n.......\n4...5.."

        assertEquals(text, encodeGrid(decodeGrid(text)!!))
    }
}

class SplitBoardsTest {
    @Test
    fun `drops the header and splits on the blank lines`() {
        val text = "; a header\n; and another\n\n.2.\n...\n.2.\n\n.1.\n...\n.1.\n"

        val blocks = splitBoards(text)

        assertEquals(2, blocks.size)
        assertEquals(".2.\n...\n.2.", blocks[0])
        assertEquals(".1.\n...\n.1.", blocks[1])
    }

    @Test
    fun `finds nothing in a file of nothing but comments`() {
        assertEquals(emptyList<String>(), splitBoards("; only a header\n; and more of it\n"))
    }
}
