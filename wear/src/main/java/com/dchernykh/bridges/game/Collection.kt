package com.dchernykh.bridges.game

// Dealing boards out of the built-in collection without repeating one until the
// whole pool has been played.
//
// What has been seen is a bit per board, written to storage as hex - a thousand
// boards is two hundred and fifty characters, which a watch can keep without
// noticing. Pure, so the wrap-around and the awkward sizes are unit tested rather
// than discovered a year into playing.

private const val BITS_PER_CHAR = 4

/** What a deal produced: the board, the pool it came from, and whether the slate was wiped. */
data class Deal(
    val index: Int,
    val seen: BooleanArray,
    val wrapped: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Deal && index == other.index && wrapped == other.wrapped && seen.contentEquals(other.seen))

    override fun hashCode(): Int = 31 * (31 * index + wrapped.hashCode()) + seen.contentHashCode()
}

fun emptySeen(count: Int): BooleanArray = BooleanArray(maxOf(0, count))

/**
 * A stored hex string back into one flag per board.
 *
 * Anything unreadable, or a record written when the collection was a different
 * size, reads as "nothing seen yet": losing the history is a far smaller harm than
 * refusing to deal.
 */
fun decodeSeen(
    text: String?,
    count: Int,
): BooleanArray {
    val size = maxOf(0, count)
    val seen = emptySeen(size)
    if (text == null || text.length != (size + BITS_PER_CHAR - 1) / BITS_PER_CHAR) return seen

    for (index in 0 until size) {
        val digit = text[index / BITS_PER_CHAR].digitToIntOrNull(16) ?: return emptySeen(size)
        seen[index] = digit and (1 shl (index % BITS_PER_CHAR)) != 0
    }
    return seen
}

fun encodeSeen(seen: BooleanArray): String {
    val text = StringBuilder()
    var start = 0
    while (start < seen.size) {
        var digit = 0
        for (bit in 0 until BITS_PER_CHAR) {
            if (seen.getOrElse(start + bit) { false }) digit = digit or (1 shl bit)
        }
        text.append(digit.toString(16))
        start += BITS_PER_CHAR
    }
    return text.toString()
}

fun seenCount(seen: BooleanArray): Int = seen.count { it }

fun allSeen(seen: BooleanArray): Boolean = seen.isNotEmpty() && seenCount(seen) == seen.size

/** Mark a board as played, as a new array so the caller can keep the old one. */
fun markSeen(
    seen: BooleanArray,
    index: Int,
): BooleanArray = seen.copyOf().also { if (index in it.indices) it[index] = true }

/**
 * Deal the next board.
 *
 * Rolls a random starting point and walks forward to the first board that has not
 * been played, which spreads the choice over the whole pool rather than favouring
 * the front of it. When every board has been played the slate is wiped and the deal
 * starts again - and the board just finished is skipped in that fresh round, so the
 * reward for completing a collection is never the very same board again.
 *
 * [Deal.index] is -1 only when there is nothing to deal at all.
 */
fun dealBoard(
    seen: BooleanArray,
    random: Mulberry32,
    avoid: Int,
): Deal {
    val count = seen.size
    if (count == 0) return Deal(-1, seen, wrapped = false)

    var pool = seen
    var wrapped = false
    if (allSeen(pool)) {
        pool = emptySeen(count)
        wrapped = true
    }

    val skip = if (wrapped && count > 1) avoid else -1
    val start = random.nextInt(count)

    for (step in 0 until count) {
        val index = (start + step) % count
        if (!pool[index] && index != skip) return Deal(index, pool, wrapped)
    }

    // Only reachable when the one board left is the one we were told to skip.
    for (index in 0 until count) {
        if (!pool[index]) return Deal(index, pool, wrapped)
    }
    return Deal(-1, pool, wrapped)
}
