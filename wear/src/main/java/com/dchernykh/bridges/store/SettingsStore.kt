package com.dchernykh.bridges.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dchernykh.bridges.game.Level
import com.dchernykh.bridges.game.Source
import com.dchernykh.bridges.game.normalizeCount
import com.dchernykh.bridges.game.normalizeTime
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * What survives closing the app: the size last played, the best time and the number
 * solved at each size and source, and which boards of the collection have been
 * dealt.
 *
 * An interface, because everything interesting happens above it: a JVM test drives
 * the view model against an in-memory implementation instead of an emulator.
 */
interface ProgressStore {
    suspend fun readLevel(): Level

    suspend fun writeLevel(level: Level)

    suspend fun readSource(): Source

    suspend fun writeSource(source: Source)

    suspend fun readBestTime(
        level: Level,
        source: Source,
    ): Int

    suspend fun writeBestTime(
        level: Level,
        source: Source,
        seconds: Int,
    )

    suspend fun readSolved(
        level: Level,
        source: Source,
    ): Int

    suspend fun writeSolved(
        level: Level,
        source: Source,
        count: Int,
    )

    /** The played-board record for a size, as the hex string the collection encodes. */
    suspend fun readSeen(level: Level): String?

    suspend fun writeSeen(
        level: Level,
        seen: String,
    )
}

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

private val LEVEL_KEY = stringPreferencesKey("level")
private val SOURCE_KEY = stringPreferencesKey("source")

private fun bestTimeKey(
    level: Level,
    source: Source,
) = intPreferencesKey("best_${source.name}_${level.name}")

private fun solvedKey(
    level: Level,
    source: Source,
) = intPreferencesKey("solved_${source.name}_${level.name}")

private fun seenKey(level: Level) = stringPreferencesKey("seen_${level.name}")

/**
 * The real store, on top of Preferences DataStore.
 *
 * Storage that has gone wrong must not stop anyone playing: a failed read reads as
 * nothing stored and a failed write is dropped, so a corrupt preferences file costs
 * a record rather than the app.
 */
class DataStoreProgressStore(
    context: Context,
) : ProgressStore {
    // The application context, not the activity's: a DataStore outlives any one
    // screen, and holding the activity here would leak it for the life of the app.
    private val dataStore = context.applicationContext.progressDataStore

    private suspend fun read(): Preferences =
        dataStore.data
            .catch { cause ->
                // Only I/O. Anything else is a bug in this file rather than a broken
                // disk, and swallowing it would hide it.
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }.first()

    private suspend fun write(change: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(change)
        } catch (_: IOException) {
            // Nothing to do and nothing worth saying: the game carries on.
        }
    }

    override suspend fun readLevel(): Level = Level.fromStoredName(read()[LEVEL_KEY])

    override suspend fun writeLevel(level: Level) = write { it[LEVEL_KEY] = level.name }

    override suspend fun readSource(): Source = Source.fromStoredName(read()[SOURCE_KEY])

    override suspend fun writeSource(source: Source) = write { it[SOURCE_KEY] = source.name }

    override suspend fun readBestTime(
        level: Level,
        source: Source,
    ): Int = normalizeTime(read()[bestTimeKey(level, source)])

    override suspend fun writeBestTime(
        level: Level,
        source: Source,
        seconds: Int,
    ) = write { it[bestTimeKey(level, source)] = normalizeTime(seconds) }

    override suspend fun readSolved(
        level: Level,
        source: Source,
    ): Int = normalizeCount(read()[solvedKey(level, source)])

    override suspend fun writeSolved(
        level: Level,
        source: Source,
        count: Int,
    ) = write { it[solvedKey(level, source)] = normalizeCount(count) }

    override suspend fun readSeen(level: Level): String? = read()[seenKey(level)]

    override suspend fun writeSeen(
        level: Level,
        seen: String,
    ) = write { it[seenKey(level)] = seen }
}
