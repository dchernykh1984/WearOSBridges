package com.dchernykh.bridges

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dchernykh.bridges.game.Level
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * What no JVM test can check: that the game actually runs on a watch.
 *
 * Launching the activity exercises the manifest, the theme, the launcher icon, the
 * board canvas, the whole Compose tree, the DataStore-backed progress store and the
 * reader that pulls the shipped collection out of the APK's assets - the parts
 * excused from the coverage floor precisely because they need a device. The rules,
 * the solver and the generator are covered far more cheaply by the unit tests, so
 * this walks the menu and deals a board rather than solving one.
 *
 * Every test starts from whatever is on screen rather than from what it would like,
 * because the size and the source are stored: a test that assumed one of them would
 * pass or fail depending on what had run before it.
 *
 * Every label is read from the resources, so the test says the same thing on a
 * watch set to any of the eleven languages.
 */
class GameScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    private fun onScreen(label: String) = rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun opensOnTheMenu() {
        rule.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.play)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.hint_tap)).assertIsDisplayed()
    }

    @Test
    fun walksTheSizes() {
        val labels = Level.entries.map { it.label }
        rule.waitUntil { labels.any(::onScreen) }
        val before = labels.first(::onScreen)

        rule.onNodeWithText(before).performClick()
        rule.waitUntil { !onScreen(before) }

        assertNotEquals(before, labels.first(::onScreen))
    }

    @Test
    fun walksTheSources() {
        val builtIn = text(R.string.source_builtin)
        val random = text(R.string.source_random)
        rule.waitUntil { onScreen(builtIn) || onScreen(random) }
        val before = if (onScreen(builtIn)) builtIn else random

        rule.onNodeWithText(before).performClick()
        rule.waitUntil { !onScreen(before) }

        assertNotEquals(before, if (onScreen(builtIn)) builtIn else random)
    }

    @Test
    fun dealsABoardAndComesBack() {
        // The size on screen is whatever was last played, so this deals whichever
        // board that is - the point is that one arrives at all, which needs the
        // assets to be readable and the collection to decode on the device.
        walkToBuiltIn()

        rule.onNodeWithText(text(R.string.play)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) { onScreen(text(R.string.undo)) }

        // The board screen carries the way out in its lower cap.
        rule.onNodeWithText(text(R.string.menu)).performClick()
        rule.waitUntil { onScreen(text(R.string.paused)) }

        rule.onNodeWithText(text(R.string.quit)).performClick()
        rule.waitUntil { onScreen(text(R.string.play)) }
    }

    @Test
    fun pausesAndResumesABoard() {
        walkToBuiltIn()

        rule.onNodeWithText(text(R.string.play)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) { onScreen(text(R.string.undo)) }

        rule.onNodeWithText(text(R.string.menu)).performClick()
        rule.waitUntil { onScreen(text(R.string.resume)) }
        rule.onNodeWithText(text(R.string.resume)).performClick()

        rule.waitUntil { !onScreen(text(R.string.paused)) }
        rule.onNodeWithText(text(R.string.undo)).assertIsDisplayed()
    }

    @Test
    fun dealsTheLargestBoardTheCollectionHas() {
        // The size that used to bring the app down: a board larger than the screen
        // has most of its islands off the canvas, and one drawn past the right-hand
        // edge took the whole activity with it. A 7x7 fits the glass whole, so only
        // this walk ever went near it.
        walkToBuiltIn()
        walkToLevel(Level.HUGE)

        rule.onNodeWithText(text(R.string.play)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) { onScreen(text(R.string.undo)) }

        rule.onNodeWithText(text(R.string.menu)).performClick()
        rule.waitUntil { onScreen(text(R.string.paused)) }
        rule.onNodeWithText(text(R.string.quit)).performClick()
        rule.waitUntil { onScreen(text(R.string.play)) }
    }

    /** Tap the size button until the one wanted is showing. */
    private fun walkToLevel(wanted: Level) {
        val labels = Level.entries.map { it.label }
        repeat(Level.entries.size) {
            if (onScreen(wanted.label)) return
            val showing = labels.first(::onScreen)
            rule.onNodeWithText(showing).performClick()
            rule.waitUntil { !onScreen(showing) }
        }
        rule.onNodeWithText(wanted.label).assertIsDisplayed()
    }

    /** Tap the source button until the collection is the one selected. */
    private fun walkToBuiltIn() {
        val builtIn = text(R.string.source_builtin)
        rule.waitUntil { onScreen(builtIn) || onScreen(text(R.string.source_random)) }
        if (onScreen(builtIn)) return

        rule.onNodeWithText(text(R.string.source_random)).performClick()
        rule.waitUntil { onScreen(builtIn) }
    }
}
