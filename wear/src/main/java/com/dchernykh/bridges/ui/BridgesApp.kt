package com.dchernykh.bridges.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import com.dchernykh.bridges.BridgesUiState
import com.dchernykh.bridges.BridgesViewModel
import com.dchernykh.bridges.R
import com.dchernykh.bridges.Screen
import com.dchernykh.bridges.game.Puzzle
import com.dchernykh.bridges.game.formatTime
import com.dchernykh.bridges.layout.BoardLayout
import com.dchernykh.bridges.layout.Camera
import com.dchernykh.bridges.layout.Hit
import com.dchernykh.bridges.layout.centerCamera
import com.dchernykh.bridges.layout.centeredBox
import com.dchernykh.bridges.layout.hitTest
import com.dchernykh.bridges.layout.panBy
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import com.dchernykh.bridges.layout.Box as LayoutBox

/**
 * The whole screen: the board, the two controls beside it, and whichever menu is in
 * front.
 *
 * The board is bigger than the screen on every size but the smallest, so it is
 * dragged the way a map is dragged in a navigator - the finger moves the map, not
 * the viewport.
 */
@Composable
fun BridgesApp(viewModel: BridgesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val container = LocalWindowInfo.current.containerSize
    val screenSize = minOf(container.width, container.height)
    if (screenSize <= 0) return

    val menu = remember(screenSize) { MenuMetrics(screenSize) }
    val puzzle = state.puzzle
    val layout =
        remember(screenSize, puzzle?.cols, puzzle?.rows) {
            puzzle?.let { BoardLayout(screenSize, it.cols, it.rows) }
        }

    // The map starts with the middle of the board in the middle of the screen.
    // Keyed on the puzzle and not only on the layout: two boards of the same size
    // share a layout, so a new deal would otherwise open wherever the last one was
    // dragged to.
    LaunchedEffect(layout, puzzle) {
        if (layout != null) {
            val centre = centerCamera(layout, screenSize)
            viewModel.moveCamera(centre.x, centre.y)
        }
    }

    // A puzzle is thought about rather than tapped at, and a ten-second display
    // timeout would black out mid-deduction.
    KeepScreenOnWhile(state.screen == Screen.PLAYING)

    // The clock is read on a tick rather than kept running in the view model, so
    // nothing counts while the app is off screen.
    LaunchedEffect(state.screen) {
        while (state.screen == Screen.PLAYING) {
            delay(1000)
            viewModel.tick()
        }
    }

    BackHandler(enabled = state.screen != Screen.START) {
        when (state.screen) {
            Screen.PLAYING -> viewModel.pauseGame()
            else -> viewModel.showStart()
        }
    }

    MaterialTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(ColorBackground)
                    .boardGestures(state, puzzle, layout, screenSize, viewModel),
        ) {
            if (puzzle != null && layout != null && state.screen != Screen.START) {
                BoardCanvas(
                    puzzle = puzzle,
                    layout = layout,
                    bridges = state.bridges,
                    selected = state.selected,
                    camera = Camera(state.cameraX, state.cameraY),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (state.screen == Screen.PLAYING) PlayControls(screenSize, menu, state, viewModel)

            Screens(screenSize, menu, state, viewModel)
        }
    }
}

@Composable
private fun KeepScreenOnWhile(playing: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Dragging moves the map; tapping builds a bridge.
 *
 * The two never collide because a drag has to travel further than the slop before
 * it counts as one - a fingertip never lands and lifts on exactly one pixel.
 */
private fun Modifier.boardGestures(
    state: BridgesUiState,
    puzzle: Puzzle?,
    layout: BoardLayout?,
    screenSize: Int,
    viewModel: BridgesViewModel,
): Modifier =
    if (state.screen != Screen.PLAYING || puzzle == null || layout == null) {
        this
    } else {
        this
            .pointerInput(layout) {
                var camera = Camera(0, 0)
                var travelled = 0f
                detectDragGestures(
                    onDragStart = {
                        val current = viewModel.uiState.value
                        camera = Camera(current.cameraX, current.cameraY)
                        travelled = 0f
                    },
                ) { change, drag ->
                    travelled += abs(drag.x) + abs(drag.y)
                    if (travelled > DRAG_SLOP) {
                        change.consume()
                        camera = panBy(camera, drag.x.roundToInt(), drag.y.roundToInt(), layout, screenSize)
                        viewModel.moveCamera(camera.x, camera.y)
                    }
                }
            }.pointerInput(layout, puzzle) {
                detectTapGestures { offset ->
                    val current = viewModel.uiState.value
                    val x = offset.x.roundToInt() + current.cameraX
                    val y = offset.y.roundToInt() + current.cameraY
                    when (val hit = hitTest(puzzle, layout, x, y)) {
                        is Hit.OnIsland -> viewModel.tapIsland(hit.id)
                        is Hit.OnEdge -> viewModel.tapEdge(hit.id)
                        null -> Unit
                    }
                }
            }
    }

/** Undo and the menu, in the caps the round screen leaves above and below the board. */
@Composable
private fun PlayControls(
    screenSize: Int,
    metrics: MenuMetrics,
    state: BridgesUiState,
    viewModel: BridgesViewModel,
) {
    TopLine(screenSize, metrics, state)

    // Through centeredBox, so the row is only as wide as the circle allows where it
    // sits: placed at a fixed width it hung off the bottom of the glass and both
    // labels were cut in half.
    val height = metrics.button
    val row =
        centeredBox(
            screenSize,
            (screenSize * 0.78f).roundToInt(),
            height,
            metrics.maxWidth,
            SCREEN_PADDING,
        )
    val gap = metrics.gap
    val width = (row.w - gap) / 2

    PillButton(
        box = LayoutBox(row.x, row.y, width, row.h),
        text = stringResource(R.string.undo),
        onClick = viewModel::undo,
    )
    PillButton(
        box = LayoutBox(row.x + width + gap, row.y, width, row.h),
        text = stringResource(R.string.menu),
        onClick = viewModel::pauseGame,
    )
}

/** The clock, in the cap above the board. */
@Composable
private fun TopLine(
    screenSize: Int,
    metrics: MenuMetrics,
    state: BridgesUiState,
) {
    val box =
        centeredBox(
            screenSize,
            (screenSize * 0.045f).roundToInt(),
            metrics.small,
            metrics.maxWidth,
            SCREEN_PADDING,
        )
    MenuLine(box, ColorMuted, formatTime(state.seconds), fraction = 0.9f)
}
