package com.dchernykh.bridges.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeTest {
    @Test
    fun `reads nothing stored as zero`() {
        assertEquals(0, normalizeCount(null))
        assertEquals(0, normalizeTime(null))
    }

    @Test
    fun `refuses a negative count or time`() {
        assertEquals(0, normalizeCount(-3))
        assertEquals(0, normalizeTime(-3))
    }

    @Test
    fun `caps a time at what the clock can show`() {
        assertEquals(MAX_TIME, normalizeTime(MAX_TIME + 1))
        assertEquals(MAX_TIME, normalizeTime(Int.MAX_VALUE))
    }
}

class BestTimeTest {
    @Test
    fun `makes the first finished board a record`() {
        val outcome = updateBestTime(previousBest = 0, seconds = 90)

        assertEquals(90, outcome.best)
        assertTrue(outcome.isRecord)
    }

    @Test
    fun `keeps the faster of the two`() {
        assertEquals(RecordOutcome(60, isRecord = true), updateBestTime(90, 60))
        assertEquals(RecordOutcome(60, isRecord = false), updateBestTime(60, 90))
    }

    @Test
    fun `does not call an equal time a record`() {
        assertEquals(RecordOutcome(60, isRecord = false), updateBestTime(60, 60))
    }

    @Test
    fun `never sets a record from a board that took no time at all`() {
        assertEquals(RecordOutcome(60, isRecord = false), updateBestTime(60, 0))
        assertEquals(RecordOutcome(0, isRecord = false), updateBestTime(null, null))
    }
}

class FormatTimeTest {
    @Test
    fun `writes minutes and seconds with two digits each`() {
        assertEquals("00:00", formatTime(0))
        assertEquals("00:07", formatTime(7))
        assertEquals("01:05", formatTime(65))
        assertEquals("10:00", formatTime(600))
    }

    @Test
    fun `shows nothing stored and a broken value as no time`() {
        assertEquals("00:00", formatTime(null))
        assertEquals("00:00", formatTime(-5))
    }

    @Test
    fun `stops at the largest time it can write`() {
        assertEquals("99:59", formatTime(MAX_TIME + 1000))
    }
}

class ElapsedTest {
    @Test
    fun `counts whole seconds only`() {
        assertEquals(0, elapsedSeconds(1_000, 1_999))
        assertEquals(1, elapsedSeconds(1_000, 2_000))
        assertEquals(1, elapsedSeconds(1_000, 2_999))
    }

    @Test
    fun `never goes backwards when the watch adjusts its clock`() {
        assertEquals(0, elapsedSeconds(5_000, 1_000))
        assertEquals(0, elapsedSeconds(5_000, 5_000))
    }

    @Test
    fun `caps a board left open overnight`() {
        assertEquals(MAX_TIME, elapsedSeconds(0, 24L * 60 * 60 * 1000))
    }
}

class ClockTest {
    @Test
    fun `counts from the moment it starts`() {
        val clock = Clock().started(1_000)

        assertEquals(0, clock.seconds(1_000))
        assertEquals(30, clock.seconds(31_000))
    }

    @Test
    fun `does not restart a clock already running`() {
        val clock = Clock().started(1_000).started(20_000)

        assertEquals(30, clock.seconds(31_000))
    }

    @Test
    fun `stops counting while paused`() {
        val paused = Clock().started(1_000).paused(31_000)

        assertEquals(30, paused.seconds(31_000))
        assertEquals("time in the menu is not thinking time", 30, paused.seconds(999_000))
    }

    @Test
    fun `banks what has run and carries on from there`() {
        val resumed = Clock().started(1_000).paused(31_000).started(500_000)

        assertEquals(30, resumed.seconds(500_000))
        assertEquals(40, resumed.seconds(510_000))
    }

    @Test
    fun `pausing a stopped clock changes nothing`() {
        val once = Clock().started(1_000).paused(31_000)

        assertEquals(once, once.paused(99_000))
    }

    @Test
    fun `caps the total the same way a single stretch is capped`() {
        val clock = Clock(banked = MAX_TIME - 1).started(1_000)

        assertEquals(MAX_TIME, clock.seconds(60_000))
    }

    @Test
    fun `reads a fresh clock as no time at all`() {
        assertEquals(0, Clock().seconds(99_000))
        assertFalse(Clock().started(1_000).since == null)
    }
}
