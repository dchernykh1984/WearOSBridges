package com.dchernykh.bridges.ui

import androidx.compose.ui.graphics.Color

// The colours, carried over unchanged from the Zepp OS original so the two versions
// of the game look like the same game.
//
// The board is read at arm's length in daylight, so the islands are solid blocks of
// colour that say what state they are in without the player having to compare
// numbers: dark blue still needs bridges, green is finished, orange is the one you
// are building from.

/** Pixels kept between anything centred and the edge of the circle. */
const val SCREEN_PADDING = 8

val ColorBackground = Color(0xFF000000)
val ColorIsland = Color(0xFF1D3557)
val ColorIslandDone = Color(0xFF2A9D5C)
val ColorIslandSelected = Color(0xFFE07B39)
val ColorIslandRing = Color(0xFFFFB066)
val ColorNumber = Color(0xFFFFFFFF)
val ColorNumberDone = Color(0xFFEAFFF2)
val ColorBridge = Color(0xFFA8C6DF)

/** The lanes the selected island may still build along, shown before they are built. */
val ColorGhost = Color(0xFF33414F)

val ColorText = Color(0xFFFFFFFF)
val ColorMuted = Color(0xFF93A1AD)
val ColorAccent = Color(0xFFF0A202)
val ColorButton = Color(0xFF1A2027)
val ColorButtonPressed = Color(0xFF2F3D46)

/**
 * How opaque the panel behind a stacked menu is. Not fully, so the board underneath
 * still shows through and the menu reads as something laid over the game rather
 * than a different screen.
 */
const val PANEL_ALPHA = 225f / 255f

/**
 * How far a finger may travel and still count as a tap rather than a drag. A
 * fingertip never lands and lifts on exactly one pixel, and without some slack a
 * board that can be dragged would swallow half the taps meant to build a bridge.
 */
const val DRAG_SLOP = 8f
