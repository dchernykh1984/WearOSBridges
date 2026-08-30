package com.dchernykh.bridges.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dchernykh.bridges.BridgesUiState
import com.dchernykh.bridges.BridgesViewModel
import com.dchernykh.bridges.R
import com.dchernykh.bridges.Screen
import com.dchernykh.bridges.game.Source
import com.dchernykh.bridges.game.formatTime

// The four menus. They live one file away from the shell that hosts them because
// they are what changes when the game gains a screen, and the shell is what does
// not.

/** Whichever menu is in front, or none at all while a board is being played. */
@Composable
fun Screens(
    screenSize: Int,
    metrics: MenuMetrics,
    state: BridgesUiState,
    viewModel: BridgesViewModel,
) {
    when (state.screen) {
        Screen.PLAYING -> Unit
        Screen.START -> StartMenu(screenSize, metrics, state, viewModel)
        Screen.BUILDING -> BuildingScreen(screenSize, metrics)
        Screen.PAUSED -> PausedMenu(screenSize, metrics, viewModel)
        Screen.SOLVED -> SolvedMenu(screenSize, metrics, state, viewModel)
    }
}

@Composable
private fun StartMenu(
    screenSize: Int,
    metrics: MenuMetrics,
    state: BridgesUiState,
    viewModel: BridgesViewModel,
) {
    MenuOverlay(
        screenSize = screenSize,
        metrics = metrics,
        items =
            listOf(
                MenuItem.Line(metrics.big, ColorText, stringResource(R.string.app_name)),
                MenuItem.Gap(metrics.gap),
                MenuItem.Line(metrics.small, ColorMuted, bestLine(state)),
                MenuItem.Line(metrics.small, ColorMuted, stringResource(R.string.solved_value, state.solvedCount)),
                MenuItem.Gap(metrics.gap),
                // The size, which is digits only and so needs no translating.
                MenuItem.Action(metrics.button, state.level.label, viewModel::cycleLevel),
                MenuItem.Action(metrics.button, sourceLabel(state.source), viewModel::cycleSource),
                MenuItem.Gap(metrics.gap),
                MenuItem.Action(metrics.button, stringResource(R.string.play), viewModel::startGame),
                MenuItem.Line(metrics.small, ColorMuted, stringResource(R.string.hint_tap)),
                MenuItem.Line(metrics.small, ColorMuted, stringResource(R.string.hint_drag)),
            ),
    )
}

/**
 * Shown while the watch builds a board.
 *
 * Generating one runs the solver twice over up to fourteen candidates, which is a
 * visible pause on the largest sizes - so the screen says what is happening rather
 * than appearing to have stopped.
 */
@Composable
private fun BuildingScreen(
    screenSize: Int,
    metrics: MenuMetrics,
) {
    MenuOverlay(
        screenSize = screenSize,
        metrics = metrics,
        items = listOf(MenuItem.Line(metrics.row, ColorMuted, stringResource(R.string.generating))),
    )
}

@Composable
private fun PausedMenu(
    screenSize: Int,
    metrics: MenuMetrics,
    viewModel: BridgesViewModel,
) {
    MenuOverlay(
        screenSize = screenSize,
        metrics = metrics,
        items =
            listOf(
                MenuItem.Line(metrics.big, ColorText, stringResource(R.string.paused)),
                MenuItem.Gap(metrics.gap),
                MenuItem.Action(metrics.button, stringResource(R.string.resume), viewModel::resumeGame),
                MenuItem.Action(metrics.button, stringResource(R.string.restart), viewModel::restart),
                MenuItem.Action(metrics.button, stringResource(R.string.quit), viewModel::showStart),
            ),
    )
}

@Composable
private fun SolvedMenu(
    screenSize: Int,
    metrics: MenuMetrics,
    state: BridgesUiState,
    viewModel: BridgesViewModel,
) {
    MenuOverlay(
        screenSize = screenSize,
        metrics = metrics,
        items =
            listOf(
                MenuItem.Line(metrics.big, ColorAccent, stringResource(R.string.well_done)),
                MenuItem.Gap(metrics.gap),
                MenuItem.Line(metrics.row, ColorText, stringResource(R.string.time_value, formatTime(state.seconds))),
                MenuItem.Line(
                    metrics.row,
                    if (state.isRecord) ColorAccent else ColorMuted,
                    if (state.isRecord) stringResource(R.string.new_best) else bestLine(state),
                ),
                MenuItem.Gap(metrics.gap),
                MenuItem.Action(metrics.button, stringResource(R.string.again), viewModel::startGame),
                MenuItem.Action(metrics.button, stringResource(R.string.quit), viewModel::showStart),
            ),
    )
}

/** The best time for the size and source on show, or a dash when there is none. */
@Composable
private fun bestLine(state: BridgesUiState): String {
    val time = if (state.bestTime > 0) formatTime(state.bestTime) else stringResource(R.string.no_time)
    return stringResource(R.string.best_value, time)
}

@Composable
private fun sourceLabel(source: Source): String =
    stringResource(if (source == Source.BUILT_IN) R.string.source_builtin else R.string.source_random)
