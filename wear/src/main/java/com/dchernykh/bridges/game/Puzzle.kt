package com.dchernykh.bridges.game

// The Hashiwokakero rule set, with nothing Android in it, so every rule is
// exercised by a unit test rather than by squinting at a watch.
//
// The rules: islands carry a number; bridges run only horizontally or vertically
// between two islands with nothing in between; at most two bridges join the same
// pair; bridges never cross each other; every island ends up with exactly as many
// bridge ends as its number; and the finished bridges join every island into one
// connected group.
//
// A puzzle is immutable - the islands, the pairs they *could* be joined by, and
// which of those pairs would cross. The bridges themselves live in a separate
// state of one count (0, 1 or 2) per edge, so undo is a matter of keeping the old
// state rather than replaying moves.

const val MAX_BRIDGES = 2

/** The largest number an island may carry: four directions, two bridges each. */
const val MAX_REQUIRED = 8

/** An island: where it sits and how many bridge ends it needs. */
data class Island(
    val id: Int,
    val col: Int,
    val row: Int,
    val required: Int,
)

/**
 * A pair of islands the rules allow a bridge between: they share a row or a
 * column with no island in between.
 */
data class Edge(
    val id: Int,
    val a: Int,
    val b: Int,
    val horizontal: Boolean,
    /** The row a horizontal edge runs along, or the column a vertical one runs down. */
    val line: Int,
    val from: Int,
    val to: Int,
)

/** Whether an island still needs bridges, or has exactly as many as it asked for. */
enum class IslandStatus { UNDER, DONE }

/** The bridges laid so far: one count per edge. */
typealias BridgeState = IntArray

/**
 * A horizontal edge and a vertical one cross when the vertical one's column falls
 * strictly inside the horizontal one's span and vice versa.
 *
 * Strictly, because a shared endpoint is an island, and an island is never in the
 * middle of an edge: an island between two others splits them into two edges.
 */
fun edgesCross(
    first: Edge,
    second: Edge,
): Boolean {
    if (first.horizontal == second.horizontal) return false
    val horizontal = if (first.horizontal) first else second
    val vertical = if (first.horizontal) second else first
    return horizontal.from < vertical.line &&
        vertical.line < horizontal.to &&
        vertical.from < horizontal.line &&
        horizontal.line < vertical.to
}

/**
 * A board, compiled once: the islands, the edges between them, and the crossing
 * table. Nothing on a hot path recomputes any of it.
 */
class Puzzle(
    val cols: Int,
    val rows: Int,
    rawIslands: List<Island>,
) {
    val islands: List<Island>
    val edges: List<Edge>
    val edgesByIsland: List<IntArray>
    val crossings: List<IntArray>

    /** How many bridges a finished board carries, which is half the island numbers. */
    val total: Int

    init {
        // Islands sort top to bottom then left to right, so ids are stable for a
        // given set of positions however the generator produced them - which is
        // what makes a generated board reproducible from its seed.
        islands =
            rawIslands
                .sortedWith(compareBy({ it.row }, { it.col }))
                .mapIndexed { id, island -> island.copy(id = id) }
        edges = findEdges(islands)

        val incident = List(islands.size) { mutableListOf<Int>() }
        for (edge in edges) {
            incident[edge.a].add(edge.id)
            incident[edge.b].add(edge.id)
        }
        edgesByIsland = incident.map { it.toIntArray() }

        val crossing = List(edges.size) { mutableListOf<Int>() }
        for (i in edges.indices) {
            for (j in i + 1 until edges.size) {
                if (edgesCross(edges[i], edges[j])) {
                    crossing[i].add(j)
                    crossing[j].add(i)
                }
            }
        }
        crossings = crossing.map { it.toIntArray() }
        total = islands.sumOf { it.required } / 2
    }

    fun emptyState(): BridgeState = IntArray(edges.size)

    /** How many bridge ends the island currently has. */
    fun degree(
        state: BridgeState,
        islandId: Int,
    ): Int = edgesByIsland[islandId].sumOf { state[it] }

    /** How many it still needs. Never negative: no move that would overshoot is accepted. */
    fun remaining(
        state: BridgeState,
        islandId: Int,
    ): Int = islands[islandId].required - degree(state, islandId)

    fun statusOf(
        state: BridgeState,
        islandId: Int,
    ): IslandStatus = if (remaining(state, islandId) == 0) IslandStatus.DONE else IslandStatus.UNDER

    /**
     * Whether a bridge already laid across this one rules it out. Checked before
     * every placement, which is how "bridges must not cross" is enforced.
     */
    fun isBlocked(
        state: BridgeState,
        edgeId: Int,
    ): Boolean = crossings[edgeId].any { state[it] > 0 }

    /**
     * Whether the edge may hold exactly [count] bridges given everything else on
     * the board. Removing is always allowed; adding must fit under both islands'
     * numbers and must not cross a bridge that is already there.
     */
    fun canPlace(
        state: BridgeState,
        edgeId: Int,
        count: Int,
    ): Boolean {
        if (count !in 0..MAX_BRIDGES) return false
        val edge = edges.getOrNull(edgeId) ?: return false
        val delta = count - state[edgeId]
        if (delta > 0 && (remaining(state, edge.a) < delta || remaining(state, edge.b) < delta)) {
            return false
        }
        return !(count > 0 && isBlocked(state, edgeId))
    }

    /**
     * A new state with the edge set to [count], or null when that is not allowed.
     *
     * States are copied rather than mutated, so the undo stack can just keep the
     * old one; a board has a few dozen edges, so the copy is cheap.
     */
    fun withBridge(
        state: BridgeState,
        edgeId: Int,
        count: Int,
    ): BridgeState? {
        if (!canPlace(state, edgeId, count)) return null
        return state.copyOf().also { it[edgeId] = count }
    }

    /**
     * The count one tap gives: none, one, two and back to none, skipping any step
     * the rules forbid. Tapping a pair with no legal change at all leaves it alone
     * rather than quietly doing something surprising elsewhere.
     */
    fun nextCount(
        state: BridgeState,
        edgeId: Int,
    ): Int {
        val current = state[edgeId]
        for (step in 1..MAX_BRIDGES) {
            val candidate = (current + step) % (MAX_BRIDGES + 1)
            if (canPlace(state, edgeId, candidate)) return candidate
        }
        return current
    }

    /** The edge joining two islands, or null when they are not a legal pair. */
    fun edgeBetween(
        first: Int,
        second: Int,
    ): Int? {
        val incident = edgesByIsland.getOrNull(first) ?: return null
        for (edgeId in incident) {
            val edge = edges[edgeId]
            if (edge.a == second || edge.b == second) return edge.id
        }
        return null
    }

    /**
     * The islands this one can still be joined to, with the count a tap would set.
     * The screen uses it to light up where the selected island may go next.
     */
    fun movesFrom(
        state: BridgeState,
        islandId: Int,
    ): List<Move> {
        val moves = mutableListOf<Move>()
        for (edgeId in edgesByIsland[islandId]) {
            val next = nextCount(state, edgeId)
            if (next == state[edgeId]) continue
            val edge = edges[edgeId]
            moves.add(
                Move(
                    edgeId = edgeId,
                    island = if (edge.a == islandId) edge.b else edge.a,
                    count = state[edgeId],
                    next = next,
                    // A tap that adds a bridge is worth hinting at on screen; one
                    // that only takes the pair back round to nothing is not.
                    buildable = next > state[edgeId],
                ),
            )
        }
        return moves
    }

    /**
     * Whether the bridges laid so far join every island into one group.
     *
     * Islands with no bridge at all count as their own group, so a half-built board
     * is correctly "not connected" - only the final check cares, but the screen
     * also uses it to tell a finished-but-split board from a finished one.
     */
    fun isConnected(state: BridgeState): Boolean {
        if (islands.size <= 1) return true
        val seen = BooleanArray(islands.size)
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        seen[0] = true
        var visited = 1

        while (stack.isNotEmpty()) {
            val islandId = stack.removeLast()
            for (edgeId in edgesByIsland[islandId]) {
                if (state[edgeId] == 0) continue
                val edge = edges[edgeId]
                val other = if (edge.a == islandId) edge.b else edge.a
                if (!seen[other]) {
                    seen[other] = true
                    visited += 1
                    stack.addLast(other)
                }
            }
        }
        return visited == islands.size
    }

    /** Whether every island has exactly its number of bridge ends, and all are joined. */
    fun isSolved(state: BridgeState): Boolean = islands.indices.all { remaining(state, it) == 0 } && isConnected(state)
}

/** One tap's worth of change, as the screen needs to describe it. */
data class Move(
    val edgeId: Int,
    val island: Int,
    val count: Int,
    val next: Int,
    val buildable: Boolean,
)

/**
 * Every pair of islands that share a row or a column with no island between them.
 *
 * Walking each row and each column in sorted order yields exactly the consecutive
 * pairs - which is exactly the adjacency the rules allow - and yields each once.
 */
private fun findEdges(islands: List<Island>): List<Edge> {
    val edges = mutableListOf<Edge>()

    for (line in islands.groupBy { it.row }.values) {
        val sorted = line.sortedBy { it.col }
        for (i in 1 until sorted.size) {
            edges.add(
                Edge(
                    id = edges.size,
                    a = sorted[i - 1].id,
                    b = sorted[i].id,
                    horizontal = true,
                    line = sorted[i].row,
                    from = sorted[i - 1].col,
                    to = sorted[i].col,
                ),
            )
        }
    }

    for (line in islands.groupBy { it.col }.values) {
        val sorted = line.sortedBy { it.row }
        for (i in 1 until sorted.size) {
            edges.add(
                Edge(
                    id = edges.size,
                    a = sorted[i - 1].id,
                    b = sorted[i].id,
                    horizontal = false,
                    line = sorted[i].col,
                    from = sorted[i - 1].row,
                    to = sorted[i].row,
                ),
            )
        }
    }

    return edges
}
