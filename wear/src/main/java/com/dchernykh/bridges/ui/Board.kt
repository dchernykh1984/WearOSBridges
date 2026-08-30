package com.dchernykh.bridges.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.dchernykh.bridges.game.IslandStatus
import com.dchernykh.bridges.game.Puzzle
import com.dchernykh.bridges.layout.BoardLayout
import com.dchernykh.bridges.layout.Camera
import com.dchernykh.bridges.layout.bridgeRects
import com.dchernykh.bridges.layout.cellCenterX
import com.dchernykh.bridges.layout.cellCenterY
import com.dchernykh.bridges.layout.edgeLine

/**
 * The whole board on one canvas: the bridges, the lanes the selected island may
 * still build along, and the islands themselves with their numbers.
 *
 * The Zepp OS original moved a widget per island and per bridge on every drag
 * frame, and had to hold the repaint rate down to keep up. A canvas is one draw
 * pass either way, so the board is simply painted where the camera says it is.
 */
@Composable
fun BoardCanvas(
    puzzle: Puzzle,
    layout: BoardLayout,
    bridges: List<Int>,
    selected: Int?,
    camera: Camera,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val state = remember(bridges) { bridges.toIntArray() }

    Canvas(modifier = modifier) {
        // The lanes the selected island could still build along, under everything
        // else: a hint, not a bridge.
        selected?.let { islandId ->
            for (move in puzzle.movesFrom(state, islandId)) {
                if (!move.buildable) continue
                val line = edgeLine(layout, puzzle, move.edgeId)
                drawLine(
                    color = ColorGhost,
                    start = Offset((line.x1 - camera.x).toFloat(), (line.y1 - camera.y).toFloat()),
                    end = Offset((line.x2 - camera.x).toFloat(), (line.y2 - camera.y).toFloat()),
                    strokeWidth = layout.bridge.toFloat(),
                )
            }
        }

        for (edge in puzzle.edges) {
            for (rect in bridgeRects(layout, puzzle, edge.id, state[edge.id])) {
                drawRect(
                    color = ColorBridge,
                    topLeft = Offset((rect.x - camera.x).toFloat(), (rect.y - camera.y).toFloat()),
                    size = Size(rect.w.toFloat(), rect.h.toFloat()),
                )
            }
        }

        for (island in puzzle.islands) {
            drawIsland(
                puzzle = puzzle,
                layout = layout,
                state = state,
                islandId = island.id,
                selected = selected,
                camera = camera,
                measurer = measurer,
            )
        }
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawIsland(
    puzzle: Puzzle,
    layout: BoardLayout,
    state: IntArray,
    islandId: Int,
    selected: Int?,
    camera: Camera,
    measurer: TextMeasurer,
) {
    val island = puzzle.islands[islandId]
    val x = (cellCenterX(layout, island.col) - camera.x).toFloat()
    val y = (cellCenterY(layout, island.row) - camera.y).toFloat()
    val done = puzzle.statusOf(state, islandId) == IslandStatus.DONE

    val fill =
        when {
            islandId == selected -> ColorIslandSelected
            done -> ColorIslandDone
            else -> ColorIsland
        }
    drawCircle(color = fill, radius = layout.radius.toFloat(), center = Offset(x, y))

    // A ring around the island being built from, so it reads as picked up even
    // where the orange and the green are hard to tell apart in bright sun.
    if (islandId == selected) {
        drawCircle(
            color = ColorIslandRing,
            radius = layout.radius.toFloat(),
            center = Offset(x, y),
            style = Stroke(width = maxOf(2f, layout.radius * 0.16f)),
        )
    }

    val text = island.required.toString()
    val style =
        TextStyle(
            color = if (done) ColorNumberDone else ColorNumber,
            // The layout is worked out in screen pixels, so the size is converted
            // from pixels rather than declared in sp - which the watch's own font
            // scale would then multiply, and the number would outgrow its island.
            fontSize = layout.numberSize.toSp(),
            fontWeight = FontWeight.Medium,
        )
    val measured = measurer.measure(text, style)
    drawText(
        textMeasurer = measurer,
        text = text,
        style = style,
        topLeft = Offset(x - measured.size.width / 2f, y - measured.size.height / 2f),
    )
}
