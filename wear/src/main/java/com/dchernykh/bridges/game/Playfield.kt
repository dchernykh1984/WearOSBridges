package com.dchernykh.bridges.game

import kotlin.math.sqrt

// Which cells of the square grid a puzzle is allowed to use.
//
// The grid is a square and the watch screen is a circle, so the corners of the
// grid land where the glass is not. An island put there is drawn into the bezel:
// on the smallest board the corner island was sliced in half before the player had
// touched anything, and on the largest one it sat 254 pixels outside the screen.
// Restricting a puzzle to the disc inscribed in its grid makes the shape of the
// board match the shape of the thing it is drawn on.
//
// Bridges need no rule of their own: a disc is convex, so a straight line between
// two cells inside it never leaves it.

/**
 * How far inside the circle a cell's centre has to be.
 *
 * Half a cell, so the whole of the island drawn in that cell is inside too - an
 * island disc is a little over a third of a cell across, so this is the strict
 * reading and still leaves room to spare.
 */
const val CELL_MARGIN = 0.5f

/** A cell of the grid. */
data class Cell(
    val col: Int,
    val row: Int,
)

/** The radius, in cells, of the usable disc. */
fun playfieldRadius(
    cols: Int,
    rows: Int,
): Float = minOf(cols, rows) / 2f - CELL_MARGIN

/** Whether a cell may hold an island. Cells are addressed by their centre. */
fun isPlayable(
    cols: Int,
    rows: Int,
    col: Int,
    row: Int,
): Boolean {
    if (col !in 0 until cols || row !in 0 until rows) return false
    val dx = col + 0.5f - cols / 2f
    val dy = row + 0.5f - rows / 2f
    return sqrt(dx * dx + dy * dy) <= playfieldRadius(cols, rows)
}

/** Every usable cell, in reading order. */
fun playableCells(
    cols: Int,
    rows: Int,
): List<Cell> {
    val cells = mutableListOf<Cell>()
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            if (isPlayable(cols, rows, col, row)) cells.add(Cell(col, row))
        }
    }
    return cells
}
