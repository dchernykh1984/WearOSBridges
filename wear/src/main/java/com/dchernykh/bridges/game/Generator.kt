package com.dchernykh.bridges.game

import kotlin.math.roundToInt

// Puzzle generation.
//
// Boards are built, not searched for: start from one island and repeatedly throw a
// bridge out to a fresh island, which makes the answer known by construction and
// guarantees the finished layout is connected. The numbers on the islands are then
// simply how many bridge ends each one ended up with.
//
// Building a solvable board is the easy half. A *good* board also has exactly one
// solution, and construction alone does not give that, so each candidate is handed
// to the solver and rejected if a second answer exists. Rejections are cheap - a
// new seed and another go - and the retry budget keeps the watch from thinking for
// longer than the "generating" screen is worth looking at.

/** How a size is generated. Everything here was tuned against the watch, not a puzzle book. */
data class GeneratorConfig(
    val cols: Int,
    val rows: Int,
    val islands: Int,
    val minIslands: Int,
    val maxSpan: Int,
    val doubleShare: Float,
    val extraBridges: Int,
    val attempts: Int,
    val maxNodes: Int,
)

/** The settings each size is generated with. */
fun configFor(level: Level): GeneratorConfig =
    when (level) {
        Level.SMALL ->
            GeneratorConfig(
                7,
                7,
                islands = 8,
                minIslands = 7,
                maxSpan = 4,
                doubleShare = 0.35f,
                extraBridges = 4,
                attempts = 14,
                maxNodes = 60_000,
            )
        Level.MEDIUM ->
            GeneratorConfig(
                9,
                9,
                islands = 13,
                minIslands = 11,
                maxSpan = 4,
                doubleShare = 0.35f,
                extraBridges = 9,
                attempts = 14,
                maxNodes = 90_000,
            )
        Level.LARGE ->
            GeneratorConfig(
                11,
                11,
                islands = 18,
                minIslands = 15,
                maxSpan = 5,
                doubleShare = 0.35f,
                extraBridges = 15,
                attempts = 14,
                maxNodes = 120_000,
            )
        Level.HUGE ->
            GeneratorConfig(
                13,
                13,
                islands = 24,
                minIslands = 20,
                maxSpan = 5,
                doubleShare = 0.35f,
                extraBridges = 22,
                attempts = 14,
                maxNodes = 150_000,
            )
    }

/** A generated board and the answer it was built around. */
data class Generated(
    val puzzle: Puzzle,
    val solution: BridgeState,
    val seed: Int,
    /** Whether the solver could prove it has exactly one answer, and that no guess is needed. */
    val unique: Boolean,
    val fair: Boolean,
)

/**
 * Grow the layout one island at a time.
 *
 * Each round picks an island that still has room, a direction and a distance, and -
 * if the way is clear - drops a new island there and bridges the two. Islands are
 * tried least-connected first, in shuffled order within a tie, so the board spreads
 * out instead of clustering around whichever island the loop happens to favour.
 */
private fun growIslands(
    grid: Grid,
    config: GeneratorConfig,
    random: Mulberry32,
) {
    while (grid.points.size < config.islands) {
        // Least-connected first, in shuffled order within a tie, so the board
        // spreads out instead of clustering around whichever island the loop
        // happens to favour.
        val order =
            random
                .shuffled(grid.points.indices.toList())
                .sortedBy { grid.degrees[it] }
                .filter { grid.degrees[it] < MAX_REQUIRED }

        // Nothing fits anywhere: the board is as big as this layout will get.
        val placed = order.any { growFrom(grid, config, random, it) }
        if (!placed) return
    }
}

/** One attempt to throw a bridge out of an island to a fresh one. */
private fun growFrom(
    grid: Grid,
    config: GeneratorConfig,
    random: Mulberry32,
    fromId: Int,
): Boolean {
    val from = grid.points[fromId]
    for (direction in random.shuffled(DIRECTIONS)) {
        for (distance in random.shuffled((MIN_SPAN..config.maxSpan).toList())) {
            val col = from.col + direction.col * distance
            val row = from.row + direction.row * distance
            val room =
                grid.canHostIsland(col, row) &&
                    grid.spanIsClear(from.col, from.row, direction.col, direction.row, distance)
            if (room) {
                val toId = grid.addIsland(col, row)
                grid.markSpan(from.col, from.row, direction.col, direction.row, distance, pairKey(fromId, toId))
                grid.addBridges(fromId, toId, 1)
                return true
            }
        }
    }
    return false
}

/**
 * Extra bridges between islands that are already on the board.
 *
 * Without these the answer is a tree, and a tree is a much softer puzzle than it
 * looks: with no loop anywhere the "one connected group" rule is satisfied for
 * free, so one of the game's three rules never does any work at all. Closing a loop
 * is what forces a player to think about connectivity.
 *
 * New pairs come first and doubling second, deliberately. Doubling an existing pair
 * is far easier to find - the lane is already clear, because it is the pair's own
 * lane - and a generator that takes the easy option ends up with every bridge
 * doubled and not one loop on the board. That was measured on the original: 88 per
 * cent of bridges doubled, and four boards in five a bare tree.
 */
private fun addExtraBridges(
    grid: Grid,
    config: GeneratorConfig,
    random: Mulberry32,
) {
    closeLoops(grid, config, random)
    doubleSomeBridges(grid, config, random)
}

/** Join two islands already placed but not yet linked, which closes a loop. */
private fun closeLoops(
    grid: Grid,
    config: GeneratorConfig,
    random: Mulberry32,
) {
    repeat(config.extraBridges * 4) {
        val fromId = random.nextInt(grid.points.size)
        val from = grid.points[fromId]
        // The first direction that offers a fresh pair with a clear lane; nothing
        // is done at all when none of the four does.
        random
            .shuffled(DIRECTIONS)
            .firstNotNullOfOrNull { direction ->
                val target = grid.scanForIsland(from, direction.col, direction.row, config.maxSpan)
                if (target != null && canLink(grid, fromId, target)) direction to target else null
            }?.let { (direction, target) ->
                val key = pairKey(fromId, target.id)
                grid.markSpan(from.col, from.row, direction.col, direction.row, target.distance, key)
                grid.addBridges(fromId, target.id, 1)
            }
    }
}

/** Whether a scan found a pair worth bridging: long enough, fresh, clear, and with room. */
private fun canLink(
    grid: Grid,
    fromId: Int,
    target: Scan,
): Boolean =
    target.distance >= MIN_SPAN &&
        !grid.links.containsKey(pairKey(fromId, target.id)) &&
        target.blockedBy.isEmpty() &&
        grid.degrees[fromId] < MAX_REQUIRED &&
        grid.degrees[target.id] < MAX_REQUIRED

/**
 * Turn a few of the single bridges into doubles, so the board uses the whole
 * one-or-two vocabulary rather than settling on one of them. Kept to a share of the
 * links rather than a free-for-all: a board of all doubles reads as bundles of tram
 * lines and solves itself.
 */
private fun doubleSomeBridges(
    grid: Grid,
    config: GeneratorConfig,
    random: Mulberry32,
) {
    val wanted = (grid.links.size * config.doubleShare).roundToInt()
    var doubled = 0
    for (key in random.shuffled(grid.links.keys.toList())) {
        if (doubled >= wanted) break
        val a = pairFirst(key)
        val b = pairSecond(key)
        val room =
            grid.links[key] == 1 &&
                grid.degrees[a] < MAX_REQUIRED &&
                grid.degrees[b] < MAX_REQUIRED
        if (room) {
            grid.addBridges(a, b, 1)
            doubled += 1
        }
    }
}

/**
 * Turn the grown layout into a puzzle plus the bridge counts that solve it.
 *
 * [Puzzle] renumbers islands into reading order, so the built bridges are looked up
 * again by position rather than by the ids used while growing.
 */
private fun compile(grid: Grid): Pair<Puzzle, BridgeState>? {
    val puzzle =
        Puzzle(
            cols = grid.cols,
            rows = grid.rows,
            rawIslands = grid.points.mapIndexed { id, point -> Island(0, point.col, point.row, grid.degrees[id]) },
        )

    val idByCell = puzzle.islands.associateBy { grid.cellKey(it.col, it.row) }

    fun idOf(pointId: Int): Int? = idByCell[grid.cellKey(grid.points[pointId].col, grid.points[pointId].row)]?.id

    val solution = puzzle.emptyState()
    for ((key, amount) in grid.links) {
        val a = idOf(pairFirst(key)) ?: return null
        val b = idOf(pairSecond(key)) ?: return null
        val edgeId = puzzle.edgeBetween(a, b) ?: return null
        solution[edgeId] = amount
    }
    return puzzle to solution
}

/**
 * One candidate board from one seed. Null when the layout came out too small or
 * somehow inconsistent, which the caller answers with another seed.
 */
fun generateCandidate(
    config: GeneratorConfig,
    seed: Int,
): Pair<Puzzle, BridgeState>? {
    val random = Mulberry32(seed)
    val grid = Grid(config.cols, config.rows)

    // Start from any usable cell, which is already the round middle of the grid:
    // the layout then grows outwards in every direction rather than hugging
    // whichever edge the first draw landed on.
    val cells = playableCells(config.cols, config.rows)
    if (cells.isEmpty()) return null
    val start = cells[random.nextInt(cells.size)]
    grid.addIsland(start.col, start.row)

    growIslands(grid, config, random)
    // A layout that came out too small is not worth proving anything about.
    if (grid.points.size < config.minIslands) return null
    addExtraBridges(grid, config, random)

    // A board whose own construction does not solve it is one this generator got
    // wrong, which the caller answers with another seed.
    val compiled = compile(grid)
    return compiled?.takeIf { it.first.isSolved(it.second) }
}

/**
 * A playable board for the given size.
 *
 * Candidates are generated from seeds derived from the one given until one passes
 * both quality gates: exactly one solution, and a solution reachable by deduction
 * alone. Building a board is far cheaper than proving it, and most candidates pass,
 * so the retry budget is rarely touched.
 *
 * If nothing passes, the last valid candidate is played anyway. A board that is
 * merely solvable is a worse puzzle than one that is provably fair, but it is a far
 * better watch face than an error message.
 */
fun generatePuzzle(
    config: GeneratorConfig,
    seed: Int,
): Generated? {
    var fallback: Generated? = null

    for (attempt in 0 until config.attempts) {
        val attemptSeed = seed + attempt * 0x9E3779B1.toInt()
        val candidate = generateCandidate(config, attemptSeed) ?: continue
        val (puzzle, solution) = candidate
        if (fallback == null) {
            fallback = Generated(puzzle, solution, attemptSeed, unique = false, fair = false)
        }
        if (hasUniqueSolution(puzzle, config.maxNodes) && isForcedSolvable(puzzle)) {
            return Generated(puzzle, solution, attemptSeed, unique = true, fair = true)
        }
    }

    return fallback
}
