package com.dchernykh.bridges.game

// A complete Hashiwokakero solver.
//
// Two properties make a board worth playing, and both are checked here: it has
// exactly one solution, and that solution can be reached without ever guessing. A
// board with two answers is not a logic puzzle, and a board that needs a guess is
// not one either - it is a coin flip with extra steps.
//
// The search branches on islands rather than on single bridges. An island's number
// fixes the total of its own bridges, so enumerating the combinations that add up
// to that number prunes far harder than trying 0, 1 and 2 on one edge at a time -
// most islands have only a handful of combinations, and many have exactly one.

/** An unassigned edge, because "no bridge here" and "not decided yet" prune very differently. */
private const val UNASSIGNED = -1

/** Ceilings that keep a pathological board from freezing the watch. */
const val DEFAULT_SOLUTION_LIMIT = 2
const val DEFAULT_NODE_BUDGET = 120_000

/**
 * Which rules are in force.
 *
 * The game always plays with all of them. Switching one off is how the tests find
 * out whether a rule is doing any work on the shipped boards: solve without it, and
 * if the answer stops being unique then that rule was what pinned it down.
 */
data class Rules(
    val crossings: Boolean = true,
    val connectivity: Boolean = true,
)

/** What a search found: solutions up to the limit, the first of them, and what it cost. */
data class SolveResult(
    val count: Int,
    val solution: BridgeState?,
    val nodes: Int,
    val exhausted: Boolean,
)

/** One island's share of a combination: how many bridges go on each of its open edges. */
private class Assignment(
    val edgeIds: IntArray,
    val values: IntArray,
)

/**
 * The partially decided board both searches work on.
 *
 * Bridge ends per island and undecided edges per island are maintained
 * incrementally: recomputing them at every step is what turns a fast solver into a
 * slow one.
 */
private class SolverBoard(
    val puzzle: Puzzle,
    val rules: Rules,
) {
    val islandCount = puzzle.islands.size
    val required = IntArray(islandCount) { puzzle.islands[it].required }
    val values = IntArray(puzzle.edges.size) { UNASSIGNED }
    val sums = IntArray(islandCount)
    val undecided = IntArray(islandCount) { puzzle.edgesByIsland[it].size }

    /**
     * The most bridges an undecided edge could still take: two, unless a bridge
     * already crosses it, or unless one of its islands has less room left.
     */
    fun capOf(edgeId: Int): Int {
        if (values[edgeId] != UNASSIGNED) return values[edgeId]
        if (rules.crossings && puzzle.crossings[edgeId].any { values[it] > 0 }) return 0
        val edge = puzzle.edges[edgeId]
        return minOf(MAX_BRIDGES, required[edge.a] - sums[edge.a], required[edge.b] - sums[edge.b])
    }

    /**
     * An island is still satisfiable when it has not overshot its number and its
     * undecided edges could still make up the difference.
     */
    fun feasible(islandId: Int): Boolean {
        if (sums[islandId] > required[islandId]) return false
        var headroom = 0
        for (edgeId in puzzle.edgesByIsland[islandId]) {
            if (values[edgeId] == UNASSIGNED) headroom += capOf(edgeId)
        }
        return sums[islandId] + headroom >= required[islandId]
    }

    /**
     * Both islands of every edge that crosses this one, plus this edge's own
     * islands, are the only ones a change here can affect.
     */
    fun neighbourhoodFeasible(edgeId: Int): Boolean {
        val edge = puzzle.edges[edgeId]
        if (!feasible(edge.a) || !feasible(edge.b)) return false
        for (other in puzzle.crossings[edgeId]) {
            val crossing = puzzle.edges[other]
            if (!feasible(crossing.a) || !feasible(crossing.b)) return false
        }
        return true
    }

    fun assign(
        edgeId: Int,
        value: Int,
    ) {
        val edge = puzzle.edges[edgeId]
        values[edgeId] = value
        sums[edge.a] += value
        sums[edge.b] += value
        undecided[edge.a] -= 1
        undecided[edge.b] -= 1
    }

    fun unassign(edgeId: Int) {
        val edge = puzzle.edges[edgeId]
        val value = values[edgeId]
        values[edgeId] = UNASSIGNED
        sums[edge.a] -= value
        sums[edge.b] -= value
        undecided[edge.a] += 1
        undecided[edge.b] += 1
    }

    /**
     * The islands reachable from [startId] over bridges that are decided and
     * present. Used both for the final connectivity rule and for spotting a group
     * that has sealed itself off early.
     */
    fun reachableFrom(startId: Int): List<Int> {
        val seen = BooleanArray(islandCount)
        val stack = ArrayDeque<Int>()
        val group = mutableListOf<Int>()
        stack.addLast(startId)
        seen[startId] = true

        while (stack.isNotEmpty()) {
            val islandId = stack.removeLast()
            group.add(islandId)
            for (edgeId in puzzle.edgesByIsland[islandId]) {
                if (values[edgeId] <= 0) continue
                val edge = puzzle.edges[edgeId]
                val other = if (edge.a == islandId) edge.b else edge.a
                if (!seen[other]) {
                    seen[other] = true
                    stack.addLast(other)
                }
            }
        }
        return group
    }

    /**
     * A group of islands that are all finished and have no undecided edges left can
     * never be joined to anything else. If it does not already contain every
     * island, this line of play can only ever produce a split board - the one rule
     * a per-island count check cannot see.
     */
    fun isSealedOffGroup(startId: Int): Boolean {
        // The prune exists to spot a board that can only end up split. With the
        // connectivity rule off, a split board is a legal answer, so there is
        // nothing to prune.
        if (!rules.connectivity) return false
        val group = reachableFrom(startId)
        if (group.size == islandCount) return false
        return group.all { undecided[it] == 0 && sums[it] == required[it] }
    }

    /** Whether every island carries exactly the number it asks for. */
    fun isSatisfied(): Boolean = (0 until islandCount).all { sums[it] == required[it] }

    fun isFullyConnected(): Boolean {
        if (!rules.connectivity) return true
        return islandCount <= 1 || reachableFrom(0).size == islandCount
    }

    /** The legal ways to make up an island's remaining number out of its undecided edges. */
    fun combinationsFor(islandId: Int): List<Assignment> {
        val open = mutableListOf<Int>()
        val caps = mutableListOf<Int>()
        for (edgeId in puzzle.edgesByIsland[islandId]) {
            if (values[edgeId] == UNASSIGNED) {
                open.add(edgeId)
                caps.add(capOf(edgeId))
            }
        }

        val edgeIds = open.toIntArray()
        val found = mutableListOf<Assignment>()
        val buffer = IntArray(edgeIds.size)
        forEachCombination(caps.toIntArray(), 0, required[islandId] - sums[islandId], buffer) {
            found.add(Assignment(edgeIds, buffer.copyOf()))
        }
        return found
    }

    /**
     * Lay a whole combination down, undoing it again if any of its bridges leaves a
     * neighbouring island unsatisfiable. Reports whether it stuck.
     */
    fun applyCombination(combination: Assignment): Boolean {
        var placed = 0
        var ok = true
        var i = 0
        // One exit: the loop runs while the board still holds up, and the two ways
        // it can stop - an edge an earlier choice in this same combination has
        // blocked, and a neighbour this choice has just made unsatisfiable - are
        // both recorded in `ok` rather than jumped out of.
        while (ok && i < combination.edgeIds.size) {
            val edgeId = combination.edgeIds[i]
            val value = combination.values[i]
            if (value > 0 && capOf(edgeId) < value) {
                ok = false
            } else {
                assign(edgeId, value)
                placed += 1
                ok = neighbourhoodFeasible(edgeId)
            }
            i += 1
        }
        if (!ok) {
            for (i in placed - 1 downTo 0) unassign(combination.edgeIds[i])
        }
        return ok
    }

    fun undoCombination(combination: Assignment) {
        for (i in combination.edgeIds.indices.reversed()) unassign(combination.edgeIds[i])
    }

    /**
     * The combinations for an island that do not immediately contradict the board.
     *
     * A combination is dropped when laying it down leaves a neighbour unsatisfiable
     * and - this is the connectivity rule working as a deduction rather than as a
     * final check - when it seals a group of islands off from the rest. That is
     * exactly the move a player rules out by saying "no, that would close those
     * three off on their own". Without it, every board whose answer needs that
     * argument looks like it needs a guess.
     */
    fun viableCombinationsFor(islandId: Int): List<Assignment> {
        val viable = mutableListOf<Assignment>()
        for (combination in combinationsFor(islandId)) {
            if (!applyCombination(combination)) continue
            val sealed = isSealedOffGroup(islandId)
            undoCombination(combination)
            if (!sealed) viable.add(combination)
        }
        return viable
    }
}

/**
 * Every way to hand out [need] bridge ends among the edges from [index] on,
 * respecting each edge's ceiling.
 *
 * An explicit recursion over a shared buffer: the combinations are consumed
 * immediately, so building arrays for the branches that get pruned would be wasted
 * work.
 */
private fun forEachCombination(
    caps: IntArray,
    index: Int,
    need: Int,
    buffer: IntArray,
    visit: () -> Unit,
) {
    if (need < 0) return
    if (index == caps.size) {
        if (need == 0) visit()
        return
    }
    // Nothing left can cover what is still needed.
    var headroom = 0
    for (i in index until caps.size) headroom += caps[i]
    if (headroom < need) return

    val cap = minOf(caps[index], need)
    for (value in 0..cap) {
        buffer[index] = value
        forEachCombination(caps, index + 1, need - value, buffer, visit)
    }
    buffer[index] = 0
}

/**
 * The island to branch on: the one with the fewest undecided edges, so the
 * combination count stays small, breaking ties towards the tightest number.
 */
private fun selectIsland(board: SolverBoard): Int {
    var best = -1
    var bestOpen = Int.MAX_VALUE
    var bestNeed = Int.MAX_VALUE
    for (id in 0 until board.islandCount) {
        if (board.undecided[id] == 0) continue
        val need = board.required[id] - board.sums[id]
        if (board.undecided[id] < bestOpen || (board.undecided[id] == bestOpen && need < bestNeed)) {
            best = id
            bestOpen = board.undecided[id]
            bestNeed = need
        }
    }
    return best
}

/**
 * Solve the puzzle, counting solutions up to [limit].
 *
 * [rules] can switch a rule off, which is what the tests use to show that the
 * crossing and connectivity rules earn their keep on the shipped collection. A
 * board where every rule can be switched off without changing anything is
 * arithmetic wearing a puzzle's hat.
 */
fun solvePuzzle(
    puzzle: Puzzle,
    limit: Int = DEFAULT_SOLUTION_LIMIT,
    maxNodes: Int = DEFAULT_NODE_BUDGET,
    rules: Rules = Rules(),
): SolveResult {
    val board = SolverBoard(puzzle, rules)
    if (board.islandCount == 0) return SolveResult(0, null, 0, exhausted = false)

    val search = Search(board, maxOf(1, limit), maxNodes)
    search.run()
    return SolveResult(search.count, search.solution, search.nodes, search.exhausted)
}

/** The depth-first search itself, kept as a small object so its counters are not globals. */
private class Search(
    val board: SolverBoard,
    val limit: Int,
    val budget: Int,
) {
    var count = 0
    var solution: BridgeState? = null
    var nodes = 0
    var exhausted = false

    fun run() {
        step()
    }

    private fun step() {
        if (count >= limit || exhausted) return
        nodes += 1
        if (nodes > budget) {
            exhausted = true
            return
        }

        val islandId = selectIsland(board)
        if (islandId == -1) {
            // Nothing is left undecided. On any board the pruning has walked, every
            // island already carries its exact number - but an island with no edges
            // at all was never pruned, because there was never anything to decide
            // about it, so the count is checked here rather than assumed.
            if (board.isSatisfied() && board.isFullyConnected()) {
                if (solution == null) solution = board.values.copyOf()
                count += 1
            }
            return
        }

        val combinations = board.combinationsFor(islandId)
        var index = 0
        while (index < combinations.size && count < limit && !exhausted) {
            val combination = combinations[index]
            index += 1
            // A combination that will not go down is simply not a branch.
            if (board.applyCombination(combination)) {
                if (!board.isSealedOffGroup(islandId)) step()
                board.undoCombination(combination)
            }
        }
    }
}

/**
 * Whether the puzzle has exactly one solution. A search that ran out of budget
 * answers "no", so an unprovable board is never shipped as a good one.
 */
fun hasUniqueSolution(
    puzzle: Puzzle,
    maxNodes: Int = DEFAULT_NODE_BUDGET,
): Boolean {
    val result = solvePuzzle(puzzle, limit = 2, maxNodes = maxNodes)
    return !result.exhausted && result.count == 1
}

/**
 * Whether the board can be solved by pure deduction - repeatedly finding an island
 * whose remaining bridges can only be arranged one way, counting the "that would
 * seal a group off" argument as a deduction like any other - with no point at which
 * the player has to pick between two possibilities and see what happens.
 *
 * This is the property that separates a fair Hashiwokakero board from one that has
 * a unique answer you can only reach by trial and error, and it is what the
 * generator actually ships on.
 */
fun isForcedSolvable(puzzle: Puzzle): Boolean {
    val board = SolverBoard(puzzle, Rules())
    if (board.islandCount == 0) return false

    while (true) {
        val step = nextDeduction(board) ?: return false
        // Either everything is decided, or the next move needs a guess.
        val forced =
            step.forced
                ?: return selectIsland(board) == -1 && board.isSatisfied() && board.isFullyConnected()
        if (!board.applyCombination(forced)) return false
    }
}

/** What one scan for a forced island found, or null when the board contradicts itself. */
private class Deduction(
    val forced: Assignment?,
)

/**
 * The first island whose remaining bridges can only be arranged one way.
 *
 * Null means the board has contradicted itself - an island that can no longer be
 * satisfied at all - and a [Deduction] carrying no assignment means nothing is
 * forced, which is either a finished board or a board that needs a guess.
 */
private fun nextDeduction(board: SolverBoard): Deduction? {
    for (id in 0 until board.islandCount) {
        if (board.undecided[id] == 0) continue
        val combinations = board.viableCombinationsFor(id)
        if (combinations.isEmpty()) return null
        if (combinations.size == 1) return Deduction(combinations[0])
    }
    return Deduction(null)
}
