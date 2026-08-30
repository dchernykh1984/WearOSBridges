package com.dchernykh.bridges.store

import android.content.Context
import com.dchernykh.bridges.game.Level
import com.dchernykh.bridges.game.Puzzle
import com.dchernykh.bridges.game.decodeGrid
import com.dchernykh.bridges.game.splitBoards

/**
 * The shipped collection, read from the files the Zepp OS app shipped.
 *
 * An interface, so a JVM test can hand the view model a couple of boards instead of
 * an emulator and 190KB of assets.
 */
interface BoardSource {
    /** How many boards this size holds. */
    suspend fun count(level: Level): Int

    /** One board, or null when the index is past the end of the collection. */
    suspend fun boardAt(
        level: Level,
        index: Int,
    ): Puzzle?
}

/**
 * The real one, on top of the APK's assets.
 *
 * A whole size is read and split once and then kept, because the alternative is
 * re-reading 60KB of text every time a board is dealt. Only the size being played is
 * ever held, which is at most a few hundred short strings.
 */
class AssetBoardSource(
    context: Context,
) : BoardSource {
    private val assets = context.applicationContext.assets
    private var cachedLevel: Level? = null
    private var cachedBoards: List<String> = emptyList()

    private fun boardsFor(level: Level): List<String> {
        if (cachedLevel == level) return cachedBoards
        val text = assets.open(level.assetName).bufferedReader().use { it.readText() }
        cachedBoards = splitBoards(text)
        cachedLevel = level
        return cachedBoards
    }

    override suspend fun count(level: Level): Int = boardsFor(level).size

    override suspend fun boardAt(
        level: Level,
        index: Int,
    ): Puzzle? = boardsFor(level).getOrNull(index)?.let(::decodeGrid)
}
