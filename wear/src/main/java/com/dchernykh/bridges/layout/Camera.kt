package com.dchernykh.bridges.layout

import com.dchernykh.bridges.game.playableCells
import kotlin.math.roundToInt
import kotlin.math.sqrt

// The map camera: which part of the world the round screen is looking at.
//
// The board is dragged the way a map is dragged in a navigator - the finger moves
// the map, not the viewport - and this owns where that is allowed to stop. The
// camera is the world coordinate that sits at the top-left corner of the screen:
// screen = world - camera. One number per axis, no zoom, because pinching on a
// 46mm screen is not a gesture anyone wants to perform.

/**
 * How far past the ordinary limit the map may be dragged, as a fraction of the
 * screen.
 *
 * A square board on a round screen has its corners cut off by the bezel, so
 * stopping the drag exactly at the edge of the board would leave the corner islands
 * permanently half-hidden. This much slack is enough to pull any corner of any of
 * the shipped boards well inside the glass, and little enough that the board never
 * flies off into empty space.
 */
private const val OVERSCROLL_RATIO = 0.18f

/** Where the screen is looking, in world pixels. */
data class Camera(
    val x: Int,
    val y: Int,
)

fun overscrollFor(viewSize: Int): Int = (viewSize * OVERSCROLL_RATIO).roundToInt()

/** How far the camera may travel on one axis, widened by the overscroll. */
fun axisBounds(
    worldSize: Int,
    viewSize: Int,
    overscroll: Int,
): IntRange {
    // A board smaller than the screen has no "keep it covered" limits to widen, so
    // it is centred and given the same slack either way.
    if (worldSize <= viewSize) {
        val centre = (worldSize - viewSize) / 2
        return (centre - overscroll)..(centre + overscroll)
    }
    return -overscroll..(worldSize - viewSize + overscroll)
}

fun clampAxis(
    value: Int,
    worldSize: Int,
    viewSize: Int,
    overscroll: Int,
): Int = value.coerceIn(axisBounds(worldSize, viewSize, overscroll))

fun clampCamera(
    camera: Camera,
    layout: BoardLayout,
    viewSize: Int,
): Camera {
    val overscroll = overscrollFor(viewSize)
    return Camera(
        x = clampAxis(camera.x, layout.width, viewSize, overscroll),
        y = clampAxis(camera.y, layout.height, viewSize, overscroll),
    )
}

/** Where the map starts: the middle of the board in the middle of the screen. */
fun centerCamera(
    layout: BoardLayout,
    viewSize: Int,
): Camera =
    clampCamera(
        Camera((layout.width - viewSize) / 2, (layout.height - viewSize) / 2),
        layout,
        viewSize,
    )

/** Drag the map by a finger movement. The map follows the finger, so the camera moves the other way. */
fun panBy(
    camera: Camera,
    dx: Int,
    dy: Int,
    layout: BoardLayout,
    viewSize: Int,
): Camera = clampCamera(Camera(camera.x - dx, camera.y - dy), layout, viewSize)

/** Whether a disc of this radius, centred at this screen point, is entirely on the glass. */
fun discIsOnScreen(
    viewSize: Int,
    x: Int,
    y: Int,
    radius: Int,
): Boolean {
    val centre = viewSize / 2f
    val dx = x - centre
    val dy = y - centre
    return sqrt(dx * dx + dy * dy) + radius <= centre
}

/**
 * Whether a disc of this radius, centred at this screen point, has any part of
 * itself on the canvas at all.
 *
 * The square canvas, not the round glass: this is what decides whether something is
 * worth drawing, and the corners of the canvas are still drawn even though the
 * bezel covers them. A board larger than the screen has most of its islands off the
 * canvas at any moment, and drawing one of those is not merely wasted work - text
 * drawn past the right-hand edge asks the canvas for a negative width.
 */
fun discIsOnCanvas(
    viewSize: Int,
    x: Int,
    y: Int,
    radius: Int,
): Boolean = x + radius >= 0 && y + radius >= 0 && x - radius <= viewSize && y - radius <= viewSize

/**
 * Whether any of the board is out of sight when it is first shown, and the player
 * will therefore have to drag to see all of it.
 *
 * Only the cells a puzzle may actually use are asked about. The corners of the grid
 * are the first thing a round screen loses, but no island is ever put there, so
 * asking about them would answer for a part of the board that is always empty - and
 * the smallest size would be told to drag when it already fits on the glass whole.
 */
fun needsPanning(
    layout: BoardLayout,
    viewSize: Int,
): Boolean {
    val camera = centerCamera(layout, viewSize)
    return playableCells(layout.cols, layout.rows).any { cell ->
        val x = cellCenterX(layout, cell.col) - camera.x
        val y = cellCenterY(layout, cell.row) - camera.y
        !discIsOnScreen(viewSize, x, y, layout.radius)
    }
}
