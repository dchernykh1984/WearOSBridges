package com.dchernykh.bridges.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun seenOf(vararg flags: Boolean) = booleanArrayOf(*flags)

class SeenCodecTest {
    @Test
    fun `survives a round trip`() {
        val seen = seenOf(true, false, false, true, true, false, true)

        assertArrayEquals(seen, decodeSeen(encodeSeen(seen), seen.size))
    }

    @Test
    fun `packs four boards into one hex digit`() {
        assertEquals("1", encodeSeen(seenOf(true, false, false, false)))
        assertEquals("8", encodeSeen(seenOf(false, false, false, true)))
        assertEquals("f", encodeSeen(seenOf(true, true, true, true)))
    }

    @Test
    fun `pads a pool that does not divide by four`() {
        // Five boards need two digits, the second of which holds only one of them.
        assertEquals(2, encodeSeen(emptySeen(5)).length)
        assertArrayEquals(
            seenOf(false, false, false, false, true),
            decodeSeen(encodeSeen(seenOf(false, false, false, false, true)), 5),
        )
    }

    @Test
    fun `reads nothing stored as nothing played`() {
        assertArrayEquals(emptySeen(9), decodeSeen(null, 9))
    }

    @Test
    fun `throws away a record written for a different sized collection`() {
        // Shipping more boards must not make the old record mean something else.
        assertArrayEquals(emptySeen(9), decodeSeen(encodeSeen(emptySeen(5)), 9))
    }

    @Test
    fun `throws away a record that is not hex`() {
        assertArrayEquals(emptySeen(4), decodeSeen("z", 4))
    }

    @Test
    fun `copes with an empty pool`() {
        assertEquals("", encodeSeen(emptySeen(0)))
        assertEquals(0, decodeSeen("", 0).size)
        assertEquals(0, emptySeen(-4).size)
    }
}

class MarkSeenTest {
    @Test
    fun `marks one board and leaves the original alone`() {
        val before = emptySeen(4)
        val after = markSeen(before, 2)

        assertTrue(after[2])
        assertFalse(before[2])
        assertEquals(1, seenCount(after))
    }

    @Test
    fun `ignores an index outside the pool`() {
        assertEquals(0, seenCount(markSeen(emptySeen(4), 9)))
        assertEquals(0, seenCount(markSeen(emptySeen(4), -1)))
    }

    @Test
    fun `knows when the whole collection has been played`() {
        assertFalse(allSeen(emptySeen(0)))
        assertFalse(allSeen(seenOf(true, false)))
        assertTrue(allSeen(seenOf(true, true)))
    }
}

class DealTest {
    @Test
    fun `deals a board nobody has played`() {
        val seen = seenOf(true, true, false, true)
        val deal = dealBoard(seen, Mulberry32(1), avoid = -1)

        assertEquals(2, deal.index)
        assertFalse(deal.wrapped)
    }

    @Test
    fun `works through the whole pool before repeating one`() {
        var seen = emptySeen(20)
        val random = Mulberry32(7)
        val dealt = mutableListOf<Int>()

        repeat(20) {
            val deal = dealBoard(seen, random, avoid = -1)
            assertFalse("no wrap until the pool is spent", deal.wrapped)
            dealt.add(deal.index)
            seen = markSeen(deal.seen, deal.index)
        }

        assertEquals(20, dealt.toSet().size)
    }

    @Test
    fun `wipes the slate when every board has been played`() {
        val deal = dealBoard(seenOf(true, true, true, true), Mulberry32(3), avoid = -1)

        assertTrue(deal.wrapped)
        assertEquals("the fresh pool starts empty", 0, seenCount(deal.seen))
        assertTrue(deal.index in 0..3)
    }

    @Test
    fun `does not open a fresh round with the board just finished`() {
        val finished = 2

        repeat(20) { seed ->
            val deal = dealBoard(seenOf(true, true, true, true), Mulberry32(seed), avoid = finished)
            assertNotEquals(finished, deal.index)
        }
    }

    @Test
    fun `deals the only board there is even when told to avoid it`() {
        val deal = dealBoard(seenOf(true), Mulberry32(1), avoid = 0)

        assertEquals(0, deal.index)
        assertTrue(deal.wrapped)
    }

    @Test
    fun `has nothing to deal from an empty collection`() {
        assertEquals(-1, dealBoard(emptySeen(0), Mulberry32(1), avoid = -1).index)
    }

    @Test
    fun `spreads its choice over the pool rather than starting at the front`() {
        val first = (0 until 40).map { dealBoard(emptySeen(30), Mulberry32(it), avoid = -1).index }

        assertTrue("a walk from a random start visits more than a couple of boards", first.toSet().size > 5)
    }
}

class DealEqualityTest {
    @Test
    fun `compares by the pool it carries, not by array identity`() {
        val one = Deal(1, seenOf(true, false), wrapped = false)
        val same = Deal(1, seenOf(true, false), wrapped = false)

        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertEquals(one, one)
        assertNotEquals(one, Deal(1, seenOf(false, false), wrapped = false))
        assertNotEquals(one, Deal(2, seenOf(true, false), wrapped = false))
        assertNotEquals(one, Deal(1, seenOf(true, false), wrapped = true))
        // Anything that is not a deal at all, which is the branch a data class
        // with a hand-written equals is easiest to get wrong.
        val other: Any = "not a deal"
        assertFalse(one.equals(other))
    }
}

class LevelTest {
    @Test
    fun `labels a size by its grid`() {
        assertEquals("7x7", Level.SMALL.label)
        assertEquals("boards/13x13.txt", Level.HUGE.assetName)
    }

    @Test
    fun `cycles round the sizes and the sources`() {
        assertEquals(Level.MEDIUM, Level.SMALL.next)
        assertEquals(Level.SMALL, Level.HUGE.next)
        assertEquals(Source.GENERATED, Source.BUILT_IN.next)
        assertEquals(Source.BUILT_IN, Source.GENERATED.next)
    }

    @Test
    fun `falls back to the default when nothing usable is stored`() {
        assertEquals(Level.DEFAULT, Level.fromStoredName(null))
        assertEquals(Level.DEFAULT, Level.fromStoredName("GIGANTIC"))
        assertEquals(Level.LARGE, Level.fromStoredName("LARGE"))
        assertEquals(Source.DEFAULT, Source.fromStoredName(null))
        assertEquals(Source.DEFAULT, Source.fromStoredName("MADE_UP"))
        assertEquals(Source.GENERATED, Source.fromStoredName("GENERATED"))
    }
}
