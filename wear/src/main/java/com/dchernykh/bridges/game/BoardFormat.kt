package com.dchernykh.bridges.game

// How a board is written down.
//
// The source of truth is an ASCII grid, one character per cell, because a board
// you can read in a diff is a board you can argue about:
//
//     ...3...
//     .2...4.
//     .......
//     4...5..
//
// The four files in assets/boards are the Zepp OS app's own, byte for byte, so a
// board that was played on an Amazfit watch is the same board here.
//
// The Zepp OS build additionally packed each board to three characters per island,
// because the grids were parsed at import time and the packed form was less than
// half the size. Android reads them from assets at runtime, where 190KB of text
// costs nothing worth a second representation to keep in step with the first.

const val EMPTY_CELL = '.'
const val COMMENT_PREFIX = ';'

/**
 * One ASCII grid back into islands, or null when the text is not a grid at all.
 *
 * Returning null rather than throwing keeps a corrupt line in a data file from
 * taking the whole collection down with it; the caller decides what to do.
 */
fun decodeGrid(text: String): Puzzle? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null

    val cols = lines[0].length
    val islands = mutableListOf<Island>()
    for (row in lines.indices) {
        if (lines[row].length != cols) return null
        for (col in 0 until cols) {
            val character = lines[row][col]
            if (character == EMPTY_CELL) continue
            if (character !in '1'..'8') return null
            islands.add(Island(id = 0, col = col, row = row, required = character - '0'))
        }
    }
    return Puzzle(cols = cols, rows = lines.size, rawIslands = islands)
}

/** The islands of one board back out as an ASCII grid, which is what a test compares. */
fun encodeGrid(puzzle: Puzzle): String =
    (0 until puzzle.rows).joinToString("\n") { row ->
        (0 until puzzle.cols)
            .map { col ->
                puzzle.islands
                    .firstOrNull { it.col == col && it.row == row }
                    ?.required
                    ?.digitToChar()
                    ?: EMPTY_CELL
            }.joinToString("")
    }

/**
 * Split a boards file into the blocks that each hold one grid.
 *
 * The files carry a header of comment lines and separate boards with a blank one,
 * which is the shape a person reading them expects and the shape a diff shows.
 */
fun splitBoards(text: String): List<String> =
    text
        .lines()
        .filterNot { it.trimStart().startsWith(COMMENT_PREFIX) }
        .joinToString("\n")
        .split(Regex("\n\\s*\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
