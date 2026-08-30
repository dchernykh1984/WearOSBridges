package com.dchernykh.bridges.game

// A small seeded pseudo-random generator.
//
// The puzzle generator has to be reproducible so a failing board can be replayed
// from its seed in a unit test, and the platform's own source cannot do that.
// mulberry32 is a well-known 32-bit generator: one multiply-xor round per call, no
// state beyond a single integer, and a period long enough for the few thousand
// draws one puzzle needs.
//
// Kotlin has kotlin.random.Random, which is seeded and reproducible too - but the
// boards this generator produces have to be the ones the Zepp OS app produced from
// the same seed, and that means the same arithmetic.

/** The next float in [0, 1). */
class Mulberry32(
    seed: Int,
) {
    private var state: Int = seed

    fun nextFloat(): Float {
        state += 0x6D2B79F5.toInt()
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }

    /** A whole number in [0, bound). Zero for a bound that cannot produce one. */
    fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        return minOf(bound - 1, (nextFloat() * bound).toInt())
    }

    /** A shuffled copy (Fisher-Yates). The input is left alone. */
    fun <T> shuffled(items: List<T>): List<T> {
        val copy = items.toMutableList()
        for (i in copy.indices.reversed()) {
            if (i == 0) break
            val j = nextInt(i + 1)
            val swap = copy[i]
            copy[i] = copy[j]
            copy[j] = swap
        }
        return copy
    }
}
