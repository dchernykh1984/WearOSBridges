package com.dchernykh.bridges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.dchernykh.bridges.game.BridgeState
import com.dchernykh.bridges.game.Clock
import com.dchernykh.bridges.game.Level
import com.dchernykh.bridges.game.Mulberry32
import com.dchernykh.bridges.game.Puzzle
import com.dchernykh.bridges.game.Source
import com.dchernykh.bridges.game.configFor
import com.dchernykh.bridges.game.dealBoard
import com.dchernykh.bridges.game.decodeSeen
import com.dchernykh.bridges.game.encodeSeen
import com.dchernykh.bridges.game.generatePuzzle
import com.dchernykh.bridges.game.markSeen
import com.dchernykh.bridges.game.updateBestTime
import com.dchernykh.bridges.store.BoardSource
import com.dchernykh.bridges.store.ProgressStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which of the four screens is in front. */
enum class Screen { START, BUILDING, PLAYING, PAUSED, SOLVED }

/** Everything the screen draws. */
data class BridgesUiState(
    val screen: Screen = Screen.START,
    val level: Level = Level.DEFAULT,
    val source: Source = Source.DEFAULT,
    val bestTime: Int = 0,
    val solvedCount: Int = 0,
    val puzzle: Puzzle? = null,
    /** The bridges laid so far, one count per edge. */
    val bridges: List<Int> = emptyList(),
    /** The island a tap picked up, whose lanes are lit for the next tap. */
    val selected: Int? = null,
    val seconds: Int = 0,
    val isRecord: Boolean = false,
    val cameraX: Int = 0,
    val cameraY: Int = 0,
)

/**
 * The game as the screen sees it.
 *
 * [workDispatcher] is where a board is built. Generating one runs the solver twice
 * over up to fourteen candidates, which is a visible pause on a watch CPU - and a
 * pause on the main thread is a frozen screen rather than a "Building..." one.
 */
class BridgesViewModel(
    private val store: ProgressStore,
    private val boards: BoardSource,
    private val now: () -> Long = System::currentTimeMillis,
    private val seedOf: () -> Int = { System.currentTimeMillis().toInt() },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BridgesUiState())
    val uiState: StateFlow<BridgesUiState> = _uiState.asStateFlow()

    private var state: BridgeState = IntArray(0)
    private var undoStack = ArrayDeque<BridgeState>()
    private var clock = Clock()

    /** The board of the collection currently in play, so it can be marked as seen. */
    private var dealtIndex = -1

    // Every touch of storage goes through this, each waiting on the one before.
    private var settings: Job = Job().apply { complete() }

    init {
        settings =
            viewModelScope.launch {
                val level = store.readLevel()
                val source = store.readSource()
                _uiState.update {
                    it.copy(
                        level = level,
                        source = source,
                        bestTime = store.readBestTime(level, source),
                        solvedCount = store.readSolved(level, source),
                    )
                }
            }
    }

    fun cycleLevel() = chooseSetup(_uiState.value.level.next, _uiState.value.source)

    fun cycleSource() = chooseSetup(_uiState.value.level, _uiState.value.source.next)

    private fun chooseSetup(
        level: Level,
        source: Source,
    ) {
        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                store.writeLevel(level)
                store.writeSource(source)
                _uiState.update {
                    it.copy(
                        level = level,
                        source = source,
                        bestTime = store.readBestTime(level, source),
                        solvedCount = store.readSolved(level, source),
                    )
                }
            }
    }

    /**
     * Deal or build a board.
     *
     * Both paths go off the main thread: reading a size out of assets is cheap but
     * not free, and building one is a search. The screen says "Building..." while it
     * happens, which is the whole reason it is not done inline.
     */
    fun startGame() {
        val setup = _uiState.value
        // The board goes with the screen: the one just finished is not the one
        // being built, and leaving it up would show it behind "Building..." until
        // its replacement arrived.
        _uiState.update { it.copy(screen = Screen.BUILDING, puzzle = null, bridges = emptyList(), selected = null) }

        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                // Somebody may have left while this was queued - a second tap on
                // Play whose board has already arrived, or a press of back. Dealing
                // one now would spend a board out of the collection that nobody
                // asked for and nobody would see.
                if (_uiState.value.screen != Screen.BUILDING) return@launch
                val dealt =
                    if (setup.source == Source.BUILT_IN) {
                        dealFromCollection(setup.level)
                    } else {
                        buildOne(setup.level)
                    }
                // And the same again, because building one takes a visible moment
                // and back is the obvious thing to press during it.
                if (_uiState.value.screen != Screen.BUILDING) return@launch
                if (dealt == null) {
                    // Nothing to play. Better to go back to the menu than to sit on
                    // a screen that says it is building something it never will.
                    _uiState.update { it.copy(screen = Screen.START) }
                    return@launch
                }
                state = dealt.emptyState()
                undoStack = ArrayDeque()
                clock = Clock().started(now())
                _uiState.update {
                    it.copy(
                        screen = Screen.PLAYING,
                        puzzle = dealt,
                        bridges = state.toList(),
                        selected = null,
                        seconds = 0,
                        isRecord = false,
                    )
                }
            }
    }

    /** One board out of the collection, never repeating until the pool is spent. */
    private suspend fun dealFromCollection(level: Level): Puzzle? {
        val count = boards.count(level)
        if (count == 0) return null
        val seen = decodeSeen(store.readSeen(level), count)
        val deal = dealBoard(seen, Mulberry32(seedOf()), dealtIndex)
        if (deal.index < 0) return null
        dealtIndex = deal.index
        // Marked as dealt rather than as solved: a board abandoned half way is still
        // a board the player has seen, and dealing it again would be worse than
        // losing it from the pool.
        store.writeSeen(level, encodeSeen(markSeen(deal.seen, deal.index)))
        return boards.boardAt(level, deal.index)
    }

    /** One board built on the wrist, off the main thread. */
    private suspend fun buildOne(level: Level): Puzzle? {
        dealtIndex = -1
        val config = configFor(level)
        val seed = seedOf()
        return withContext(workDispatcher) { generatePuzzle(config, seed)?.puzzle }
    }

    /**
     * Tap an island.
     *
     * The first tap picks it up and lights the lanes it can still build along; a
     * second tap on one of its neighbours cycles the bridge between them - one, two,
     * none. Tapping the island itself again puts it down.
     */
    fun tapIsland(islandId: Int) {
        val puzzle = _uiState.value.puzzle ?: return
        if (_uiState.value.screen != Screen.PLAYING) return

        val selected = _uiState.value.selected
        if (selected == null || selected == islandId) {
            _uiState.update { it.copy(selected = if (selected == islandId) null else islandId) }
            return
        }
        val edgeId = puzzle.edgeBetween(selected, islandId)
        if (edgeId == null) {
            // Not a pair the rules allow: the tap picks up the new island instead of
            // doing nothing, which is what a finger meant by it.
            _uiState.update { it.copy(selected = islandId) }
            return
        }
        cycleEdge(edgeId)
    }

    /** Tap a bridge lane directly, which cycles it without picking an island up first. */
    fun tapEdge(edgeId: Int) {
        if (_uiState.value.screen != Screen.PLAYING) return
        cycleEdge(edgeId)
    }

    private fun cycleEdge(edgeId: Int) {
        val puzzle = _uiState.value.puzzle ?: return
        val next = puzzle.withBridge(state, edgeId, puzzle.nextCount(state, edgeId)) ?: return
        undoStack.addLast(state)
        state = next
        _uiState.update { it.copy(bridges = state.toList(), selected = null) }
        if (puzzle.isSolved(state)) finish()
    }

    /** Take back the last bridge. */
    fun undo() {
        if (_uiState.value.screen != Screen.PLAYING) return
        val previous = undoStack.removeLastOrNull() ?: return
        state = previous
        _uiState.update { it.copy(bridges = state.toList(), selected = null) }
    }

    /** Clear the board without dealing a new one, so the same puzzle can be tried again. */
    fun restart() {
        val puzzle = _uiState.value.puzzle ?: return
        if (_uiState.value.screen != Screen.PLAYING && _uiState.value.screen != Screen.PAUSED) return
        state = puzzle.emptyState()
        undoStack = ArrayDeque()
        clock = Clock().started(now())
        _uiState.update { it.copy(screen = Screen.PLAYING, bridges = state.toList(), selected = null) }
    }

    /**
     * The menu pauses over the board.
     *
     * The clock stops with it: thinking time counts, but time spent staring at the
     * pause menu does not.
     */
    fun pauseGame() {
        if (_uiState.value.screen != Screen.PLAYING) return
        clock = clock.paused(now())
        _uiState.update { it.copy(screen = Screen.PAUSED, seconds = clock.seconds(now())) }
    }

    fun resumeGame() {
        if (_uiState.value.screen != Screen.PAUSED) return
        clock = clock.started(now())
        _uiState.update { it.copy(screen = Screen.PLAYING) }
    }

    fun showStart() {
        clock = clock.paused(now())
        _uiState.update { it.copy(screen = Screen.START, selected = null, isRecord = false) }
    }

    /** Drag the map. The caller has already clamped the camera to what the board allows. */
    fun moveCamera(
        x: Int,
        y: Int,
    ) {
        _uiState.update { it.copy(cameraX = x, cameraY = y) }
    }

    /** The clock as the screen shows it, read on a tick rather than kept running here. */
    fun tick() {
        if (_uiState.value.screen != Screen.PLAYING) return
        _uiState.update { it.copy(seconds = clock.seconds(now())) }
    }

    private fun finish() {
        clock = clock.paused(now())
        val seconds = clock.seconds(now())
        val setup = _uiState.value
        val outcome = updateBestTime(setup.bestTime, seconds)
        _uiState.update {
            it.copy(
                screen = Screen.SOLVED,
                seconds = seconds,
                bestTime = outcome.best,
                isRecord = outcome.isRecord,
                solvedCount = it.solvedCount + 1,
                selected = null,
            )
        }

        val solved = setup.solvedCount + 1
        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                // Not cancellable. The app being closed the instant a board falls is
                // exactly when the record is worth keeping.
                withContext(NonCancellable) {
                    store.writeSolved(setup.level, setup.source, solved)
                    if (outcome.isRecord) store.writeBestTime(setup.level, setup.source, outcome.best)
                }
            }
    }

    companion object {
        fun factory(
            store: ProgressStore,
            boards: BoardSource,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    @Suppress("UNCHECKED_CAST")
                    return BridgesViewModel(store, boards) as T
                }
            }
    }
}
