package com.dchernykh.bridges

import com.dchernykh.bridges.game.Level
import com.dchernykh.bridges.game.Puzzle
import com.dchernykh.bridges.game.Source
import com.dchernykh.bridges.game.decodeGrid
import com.dchernykh.bridges.game.decodeSeen
import com.dchernykh.bridges.store.BoardSource
import com.dchernykh.bridges.store.ProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** An in-memory stand-in for the watch's storage. */
private class FakeStore : ProgressStore {
    var level = Level.DEFAULT
    var source = Source.DEFAULT
    val bestTimes = mutableMapOf<String, Int>()
    val solved = mutableMapOf<String, Int>()
    val seen = mutableMapOf<Level, String>()

    private fun key(
        level: Level,
        source: Source,
    ) = "${source.name}_${level.name}"

    override suspend fun readLevel(): Level = level

    override suspend fun writeLevel(level: Level) {
        this.level = level
    }

    override suspend fun readSource(): Source = source

    override suspend fun writeSource(source: Source) {
        this.source = source
    }

    override suspend fun readBestTime(
        level: Level,
        source: Source,
    ): Int = bestTimes[key(level, source)] ?: 0

    override suspend fun writeBestTime(
        level: Level,
        source: Source,
        seconds: Int,
    ) {
        bestTimes[key(level, source)] = seconds
    }

    override suspend fun readSolved(
        level: Level,
        source: Source,
    ): Int = solved[key(level, source)] ?: 0

    override suspend fun writeSolved(
        level: Level,
        source: Source,
        count: Int,
    ) {
        solved[key(level, source)] = count
    }

    override suspend fun readSeen(level: Level): String? = seen[level]

    override suspend fun writeSeen(
        level: Level,
        seen: String,
    ) {
        this.seen[level] = seen
    }
}

/** Two boards standing in for the shipped collection of a thousand. */
private class FakeBoards(
    private val grids: List<String> = listOf("1.2.1", "2.2\n...\n2.2"),
) : BoardSource {
    override suspend fun count(level: Level): Int = grids.size

    override suspend fun boardAt(
        level: Level,
        index: Int,
    ): Puzzle? = grids.getOrNull(index)?.let(::decodeGrid)
}

/** A clock the test winds by hand, so a timed game needs no waiting. */
private class FakeClock(
    var now: Long = 1_000L,
) : () -> Long {
    override fun invoke(): Long = now
}

@OptIn(ExperimentalCoroutinesApi::class)
class BridgesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()
    private var seed = 1

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        store: ProgressStore = FakeStore(),
        boards: BoardSource = FakeBoards(),
    ) = BridgesViewModel(store, boards, now = clock, seedOf = { seed }, workDispatcher = dispatcher)

    /** Play the board through to the end: every lane at the count the answer needs. */
    private fun solve(model: BridgesViewModel) {
        val puzzle = model.uiState.value.puzzle!!
        for (edge in puzzle.edges) {
            while (model.uiState.value.bridges[edge.id] < 1) model.tapEdge(edge.id)
        }
    }

    @Test
    fun `opens on the start screen with what was last played`() =
        runTest(dispatcher) {
            val store =
                FakeStore().apply {
                    level = Level.LARGE
                    source = Source.GENERATED
                }
            store.bestTimes["GENERATED_LARGE"] = 42
            store.solved["GENERATED_LARGE"] = 7
            val model = viewModel(store)
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(Screen.START, state.screen)
            assertEquals(Level.LARGE, state.level)
            assertEquals(Source.GENERATED, state.source)
            assertEquals(42, state.bestTime)
            assertEquals(7, state.solvedCount)
            assertNull(state.puzzle)
        }

    @Test
    fun `cycles the size and remembers it`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.cycleLevel()
            advanceUntilIdle()

            assertEquals(Level.MEDIUM, model.uiState.value.level)
            assertEquals(Level.MEDIUM, store.level)
        }

    @Test
    fun `shows the record kept for the size and source now chosen`() =
        runTest(dispatcher) {
            val store = FakeStore()
            store.bestTimes["BUILT_IN_MEDIUM"] = 99
            store.solved["BUILT_IN_MEDIUM"] = 3
            val model = viewModel(store)
            advanceUntilIdle()

            assertEquals(0, model.uiState.value.bestTime)
            model.cycleLevel()
            advanceUntilIdle()

            assertEquals(99, model.uiState.value.bestTime)
            assertEquals(3, model.uiState.value.solvedCount)
        }

    @Test
    fun `cycles the source and remembers it`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.cycleSource()
            advanceUntilIdle()

            assertEquals(Source.GENERATED, model.uiState.value.source)
            assertEquals(Source.GENERATED, store.source)
        }

    @Test
    fun `deals a board from the collection and starts the clock`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(Screen.PLAYING, state.screen)
            assertNotNull(state.puzzle)
            assertTrue("a fresh board carries no bridges", state.bridges.all { it == 0 })
            assertEquals(0, state.seconds)
        }

    @Test
    fun `says it is building while it works`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.startGame()

            assertEquals(Screen.BUILDING, model.uiState.value.screen)
            advanceUntilIdle()
            assertEquals(Screen.PLAYING, model.uiState.value.screen)
        }

    @Test
    fun `puts the finished board away before building the next one`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            solve(model)

            model.startGame()

            val state = model.uiState.value
            assertNull("the solved board is not left up behind Building...", state.puzzle)
            assertTrue(state.bridges.isEmpty())
        }

    @Test
    fun `marks a dealt board as played so it does not come round again`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            assertEquals(1, decodeSeen(store.seen[Level.SMALL], 2).count { it })
        }

    @Test
    fun `works through the collection before repeating a board`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()
            val first =
                model.uiState.value.puzzle!!
                    .islands.size
            model.startGame()
            advanceUntilIdle()

            assertEquals(2, decodeSeen(store.seen[Level.SMALL], 2).count { it })
            assertTrue(
                "the second deal is the other board",
                first !=
                    model.uiState.value.puzzle!!
                        .islands.size,
            )
        }

    @Test
    fun `goes back to the menu when there is nothing to deal`() =
        runTest(dispatcher) {
            val model = viewModel(boards = FakeBoards(emptyList()))
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            assertEquals(Screen.START, model.uiState.value.screen)
            assertNull(model.uiState.value.puzzle)
        }

    @Test
    fun `builds a board on the wrist when asked for a fresh one`() =
        runTest(dispatcher) {
            val store = FakeStore().apply { source = Source.GENERATED }
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            val puzzle = model.uiState.value.puzzle
            assertNotNull("the generator produced a board for this seed", puzzle)
            assertEquals(Screen.PLAYING, model.uiState.value.screen)
            assertTrue(store.seen.isEmpty())
        }

    @Test
    fun `picks an island up and puts it down again`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.tapIsland(1)
            assertEquals(1, model.uiState.value.selected)
            model.tapIsland(1)
            assertNull(model.uiState.value.selected)
        }

    @Test
    fun `builds a bridge between the island held and the one tapped`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.tapIsland(0)
            model.tapIsland(1)

            assertEquals(1, model.uiState.value.bridges[0])
            assertNull("building puts the island down", model.uiState.value.selected)
        }

    @Test
    fun `picks up the new island when the two do not face each other`() =
        runTest(dispatcher) {
            val model = viewModel(boards = FakeBoards(listOf("1.1\n...\n1.1")))
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.tapIsland(0)
            model.tapIsland(3)

            assertEquals(3, model.uiState.value.selected)
            assertTrue(
                model.uiState.value.bridges
                    .all { it == 0 },
            )
        }

    @Test
    fun `cycles a lane tapped directly`() =
        runTest(dispatcher) {
            val model = viewModel(boards = FakeBoards(listOf("4.4")))
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.tapEdge(0)
            assertEquals(1, model.uiState.value.bridges[0])
            model.tapEdge(0)
            assertEquals(2, model.uiState.value.bridges[0])
            model.tapEdge(0)
            assertEquals(0, model.uiState.value.bridges[0])
        }

    @Test
    fun `takes back the last bridge, and stops at the empty board`() =
        runTest(dispatcher) {
            val model = viewModel(boards = FakeBoards(listOf("4.4")))
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.tapEdge(0)
            model.tapEdge(0)
            model.undo()
            assertEquals(1, model.uiState.value.bridges[0])
            model.undo()
            assertEquals(0, model.uiState.value.bridges[0])
            model.undo()
            assertEquals("nothing left to take back", 0, model.uiState.value.bridges[0])
        }

    @Test
    fun `clears the board and restarts the clock`() =
        runTest(dispatcher) {
            val model = viewModel(boards = FakeBoards(listOf("4.4")))
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.tapEdge(0)
            clock.now += 30_000
            model.tick()
            assertEquals(30, model.uiState.value.seconds)

            model.restart()
            model.tick()

            assertEquals(0, model.uiState.value.bridges[0])
            assertEquals(0, model.uiState.value.seconds)
            assertEquals(Screen.PLAYING, model.uiState.value.screen)
        }

    @Test
    fun `pauses the clock with the menu and picks it up again on resume`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            clock.now += 20_000
            model.pauseGame()
            assertEquals(Screen.PAUSED, model.uiState.value.screen)
            assertEquals(20, model.uiState.value.seconds)

            clock.now += 600_000
            model.tick()
            assertEquals("a paused clock does not run", 20, model.uiState.value.seconds)

            model.resumeGame()
            clock.now += 10_000
            model.tick()
            assertEquals(30, model.uiState.value.seconds)
        }

    @Test
    fun `ignores a tap on a screen that is not the board`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            model.pauseGame()

            model.tapIsland(0)
            model.tapEdge(0)
            model.undo()
            model.resumeGame()
            model.resumeGame()

            assertTrue(
                model.uiState.value.bridges
                    .all { it == 0 },
            )
            assertNull(model.uiState.value.selected)
            assertEquals(Screen.PLAYING, model.uiState.value.screen)
        }

    @Test
    fun `has nothing to do before a board is dealt`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.tapIsland(0)
            model.undo()
            model.restart()
            model.pauseGame()
            model.tick()

            assertEquals(Screen.START, model.uiState.value.screen)
        }

    @Test
    fun `shows the solved screen and keeps the record when the board falls`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            clock.now += 45_000
            solve(model)
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(Screen.SOLVED, state.screen)
            assertEquals(45, state.seconds)
            assertEquals(45, state.bestTime)
            assertTrue(state.isRecord)
            assertEquals(1, state.solvedCount)
            assertEquals(45, store.bestTimes["BUILT_IN_SMALL"])
            assertEquals(1, store.solved["BUILT_IN_SMALL"])
        }

    @Test
    fun `keeps the faster time and does not call a slower board a record`() =
        runTest(dispatcher) {
            val store = FakeStore()
            store.bestTimes["BUILT_IN_SMALL"] = 20
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            clock.now += 60_000
            solve(model)
            advanceUntilIdle()

            assertFalse(model.uiState.value.isRecord)
            assertEquals(20, model.uiState.value.bestTime)
            assertEquals("a slower board never overwrites the record", 20, store.bestTimes["BUILT_IN_SMALL"])
        }

    @Test
    fun `stops the clock the moment the last bridge goes in`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            clock.now += 30_000
            solve(model)
            advanceUntilIdle()
            clock.now += 600_000
            model.tick()

            assertEquals(30, model.uiState.value.seconds)
        }

    @Test
    fun `counts a board finished under the size and source it was played on`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            clock.now += 10_000
            solve(model)

            // Back to the menu and on to a different size before storage settles.
            model.showStart()
            model.cycleLevel()
            advanceUntilIdle()

            assertEquals(10, store.bestTimes["BUILT_IN_SMALL"])
            assertNull(store.bestTimes["BUILT_IN_MEDIUM"])
        }

    @Test
    fun `goes back to the menu with nothing held and no record showing`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.tapIsland(1)
            model.showStart()

            assertEquals(Screen.START, model.uiState.value.screen)
            assertNull(model.uiState.value.selected)
            assertFalse(model.uiState.value.isRecord)
        }

    @Test
    fun `remembers where the map has been dragged to`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.moveCamera(120, -30)

            assertEquals(120, model.uiState.value.cameraX)
            assertEquals(-30, model.uiState.value.cameraY)
        }
}
