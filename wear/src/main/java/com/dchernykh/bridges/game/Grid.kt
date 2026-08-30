package com.dchernykh.bridges.game

// The layout while it is being grown, before it becomes a puzzle.
//
// Islands, the lanes their bridges run along and who owns each lane, all kept by
// cell so the generator can ask "is this free?" without walking anything.

internal val DIRECTIONS = listOf(Cell(1, 0), Cell(-1, 0), Cell(0, 1), Cell(0, -1))

/** Two island ids as one key, order-independent. */
internal fun pairKey(
    a: Int,
    b: Int,
): Long = if (a < b) a.toLong() shl 32 or b.toLong() else b.toLong() shl 32 or a.toLong()

internal fun pairFirst(key: Long): Int = (key ushr 32).toInt()

internal fun pairSecond(key: Long): Int = (key and 0xFFFFFFFFL).toInt()

/** What a scan along one direction found. */
internal class Scan(
    val id: Int,
    val distance: Int,
    val blockedBy: Set<Long>,
)

/** The layout while it is being grown, before it becomes a puzzle. */
internal class Grid(
    val cols: Int,
    val rows: Int,
) {
    /** cell -> island id. */
    val islands = HashMap<Int, Int>()

    /**
     * cell -> the pair whose bridge runs over it. Ownership matters: a second bridge
     * may share a lane with the first one of the same pair, but never with anybody
     * else's.
     */
    val bridges = HashMap<Int, Long>()

    val points = mutableListOf<Cell>()
    val degrees = mutableListOf<Int>()
    val links = LinkedHashMap<Long, Int>()

    fun cellKey(
        col: Int,
        row: Int,
    ) = row * cols + col

    fun addIsland(
        col: Int,
        row: Int,
    ): Int {
        val id = points.size
        points.add(Cell(col, row))
        degrees.add(0)
        islands[cellKey(col, row)] = id
        return id
    }

    fun addBridges(
        from: Int,
        to: Int,
        amount: Int,
    ) {
        val key = pairKey(from, to)
        links[key] = (links[key] ?: 0) + amount
        degrees[from] += amount
        degrees[to] += amount
    }

    fun markSpan(
        col: Int,
        row: Int,
        dc: Int,
        dr: Int,
        distance: Int,
        owner: Long,
    ) {
        for (step in 1 until distance) bridges[cellKey(col + dc * step, row + dr * step)] = owner
    }

    /**
     * A cell can host a new island when it is inside the round playfield, empty, not
     * already spanned by a bridge, and not touching another island.
     */
    fun canHostIsland(
        col: Int,
        row: Int,
    ): Boolean {
        if (!isPlayable(cols, rows, col, row)) return false
        val key = cellKey(col, row)
        if (islands.containsKey(key) || bridges.containsKey(key)) return false
        return DIRECTIONS.none { islands.containsKey(cellKey(col + it.col, row + it.row)) }
    }

    /**
     * Whether the cells strictly between two points on a line are free of islands and
     * of other bridges. Bridges laid here later cannot break this one, because its own
     * cells are marked as taken the moment it is built.
     */
    fun spanIsClear(
        col: Int,
        row: Int,
        dc: Int,
        dr: Int,
        distance: Int,
    ): Boolean =
        (1 until distance).none {
            val key = cellKey(col + dc * it, row + dr * it)
            islands.containsKey(key) || bridges.containsKey(key)
        }

    /**
     * The first island in a direction, how far away it is, and which pairs' bridges lie
     * in the way. Stops at the board edge or once the reach is used up.
     */
    fun scanForIsland(
        from: Cell,
        dc: Int,
        dr: Int,
        maxSpan: Int,
    ): Scan? {
        val blockedBy = HashSet<Long>()
        for (step in 1..maxSpan) {
            val col = from.col + dc * step
            val row = from.row + dr * step
            if (col !in 0 until cols || row !in 0 until rows) return null
            val key = cellKey(col, row)
            islands[key]?.let { return Scan(it, step, blockedBy) }
            bridges[key]?.let { blockedBy.add(it) }
        }
        return null
    }
}
