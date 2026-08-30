package com.dchernykh.bridges.layout

import com.dchernykh.bridges.game.Puzzle
import kotlin.math.abs
import kotlin.math.roundToInt

// Where the board lives in pixels, and what the player hit when they tapped.
//
// Everything here is in "world" coordinates - the whole map, not the part of it
// currently on screen - so it is free of the camera and unit tested on its own.
// Camera.kt converts world to screen.

// Cell and island sizes as fractions of the screen's diameter. A cell a little
// under an eighth of the screen puts roughly eight columns across a round watch
// and leaves the numbers big enough to read without leaning in.
private const val CELL_RATIO = 0.117f
private const val ISLAND_RATIO = 0.36f
private const val BRIDGE_RATIO = 0.075f
private const val BRIDGE_GAP_RATIO = 0.12f
private const val NUMBER_RATIO = 0.46f

/**
 * How far past an island's edge a tap still counts as hitting it. Fingers are wider
 * than islands, and missing a tap on a watch is far more annoying than occasionally
 * hitting the wrong thing.
 */
private const val TAP_SLACK_RATIO = 0.22f

/** How far from the centre line of a bridge a tap still counts as hitting it. */
private const val BRIDGE_TAP_RATIO = 0.3f

/** A pixel box in world space. */
data class WorldBox(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

/** The line a bridge is drawn along. */
data class Line(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
)

/** What a tap landed on. */
sealed interface Hit {
    data class OnIsland(
        val id: Int,
    ) : Hit

    data class OnEdge(
        val id: Int,
    ) : Hit
}

/** Every pixel size the board is drawn with, derived from the screen's diameter. */
class BoardLayout(
    screenSize: Int,
    cols: Int,
    rows: Int,
) {
    val cell = maxOf(8, (screenSize * CELL_RATIO).roundToInt())
    val cols = maxOf(1, cols)
    val rows = maxOf(1, rows)
    val radius = (cell * ISLAND_RATIO).roundToInt()
    val bridge = maxOf(2, (cell * BRIDGE_RATIO).roundToInt())
    val bridgeGap = maxOf(2, (cell * BRIDGE_GAP_RATIO).roundToInt())
    val numberSize = (cell * NUMBER_RATIO).roundToInt()
    val tapSlack = (cell * TAP_SLACK_RATIO).roundToInt()
    val bridgeTap = (cell * BRIDGE_TAP_RATIO).roundToInt()
    val width = this.cols * cell
    val height = this.rows * cell
}

/**
 * The world-pixel centre of a grid cell. Islands sit in the middle of their cell,
 * which is what leaves room for a bridge to run between two of them.
 */
fun cellCenterX(
    layout: BoardLayout,
    col: Int,
): Int = ((col + 0.5f) * layout.cell).roundToInt()

fun cellCenterY(
    layout: BoardLayout,
    row: Int,
): Int = ((row + 0.5f) * layout.cell).roundToInt()

/**
 * The line a bridge is drawn along, from the edge of one island to the edge of the
 * other, so the bridge does not disappear under the discs.
 */
fun edgeLine(
    layout: BoardLayout,
    puzzle: Puzzle,
    edgeId: Int,
): Line {
    val edge = puzzle.edges[edgeId]
    val a = puzzle.islands[edge.a]
    val b = puzzle.islands[edge.b]
    val ax = cellCenterX(layout, a.col)
    val ay = cellCenterY(layout, a.row)
    val bx = cellCenterX(layout, b.col)
    val by = cellCenterY(layout, b.row)
    return if (edge.horizontal) {
        Line(ax + layout.radius, ay, bx - layout.radius, by)
    } else {
        Line(ax, ay + layout.radius, bx, by - layout.radius)
    }
}

/**
 * The one or two rectangles that draw a bridge.
 *
 * A single bridge runs down the middle of the lane; a double one is two lines
 * either side of it, which is how a player counts them at a glance without reading
 * a number.
 */
fun bridgeRects(
    layout: BoardLayout,
    puzzle: Puzzle,
    edgeId: Int,
    count: Int,
): List<WorldBox> {
    if (count <= 0) return emptyList()
    val edge = puzzle.edges[edgeId]
    val line = edgeLine(layout, puzzle, edgeId)
    val thickness = layout.bridge
    val offsets = if (count == 1) listOf(0) else listOf(-layout.bridgeGap, layout.bridgeGap)

    return offsets.map { offset ->
        if (edge.horizontal) {
            WorldBox(
                x = minOf(line.x1, line.x2),
                y = (line.y1 + offset - thickness / 2f).roundToInt(),
                w = maxOf(1, abs(line.x2 - line.x1)),
                h = thickness,
            )
        } else {
            WorldBox(
                x = (line.x1 + offset - thickness / 2f).roundToInt(),
                y = minOf(line.y1, line.y2),
                w = thickness,
                h = maxOf(1, abs(line.y2 - line.y1)),
            )
        }
    }
}

/**
 * The island under a world-space point, or null. The nearest one wins, so two
 * overlapping tap areas resolve to whichever the player was closer to.
 */
fun islandAt(
    puzzle: Puzzle,
    layout: BoardLayout,
    x: Int,
    y: Int,
): Int? {
    val reach = layout.radius + layout.tapSlack
    var best: Int? = null
    var bestDistance = reach * reach

    for (island in puzzle.islands) {
        val dx = x - cellCenterX(layout, island.col)
        val dy = y - cellCenterY(layout, island.row)
        val distance = dx * dx + dy * dy
        if (distance <= bestDistance) {
            best = island.id
            bestDistance = distance
        }
    }
    return best
}

/**
 * The bridge lane under a world-space point, or null.
 *
 * Only the stretch between the two islands counts, so a tap beyond the end of a
 * lane is not caught by a bridge that merely lines up with it.
 */
fun edgeAt(
    puzzle: Puzzle,
    layout: BoardLayout,
    x: Int,
    y: Int,
): Int? {
    var best: Int? = null
    var bestDistance = layout.bridgeTap + 1

    for (edge in puzzle.edges) {
        val line = edgeLine(layout, puzzle, edge.id)
        val along = if (edge.horizontal) x else y
        val across = if (edge.horizontal) abs(y - line.y1) else abs(x - line.x1)
        val low = if (edge.horizontal) minOf(line.x1, line.x2) else minOf(line.y1, line.y2)
        val high = if (edge.horizontal) maxOf(line.x1, line.x2) else maxOf(line.y1, line.y2)

        if (along < low || along > high || across > layout.bridgeTap) continue
        if (across < bestDistance) {
            best = edge.id
            bestDistance = across
        }
    }
    return best
}

/**
 * What a tap at this world point means.
 *
 * Islands win over bridges: an island is the smaller target and the one the player
 * aimed at, and every bridge ends at one.
 */
fun hitTest(
    puzzle: Puzzle,
    layout: BoardLayout,
    x: Int,
    y: Int,
): Hit? {
    islandAt(puzzle, layout, x, y)?.let { return Hit.OnIsland(it) }
    edgeAt(puzzle, layout, x, y)?.let { return Hit.OnEdge(it) }
    return null
}
