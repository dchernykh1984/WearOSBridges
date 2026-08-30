package com.dchernykh.bridges.game

// What the watch remembers between games: how many boards have been solved at each
// size, and the fastest time. Kept apart from the storage that holds it so the rule
// is unit tested.
//
// A record is kept per size AND per source. Beating your time on a 13x13 says
// something very different from beating it on a 7x7, and a board dealt from the
// collection is a different proposition from one the watch rolled on the spot - the
// collection never repeats itself, so working through it is a run rather than a
// series of unrelated attempts.

/**
 * Seconds, clamped to what the clock can show. A board left open overnight should
 * read as a very slow game, not as a number that overflows the box it is drawn in.
 */
const val MAX_TIME = 99 * 60 + 59

/** A best time and whether the game just finished is the one that set it. */
data class RecordOutcome(
    val best: Int,
    val isRecord: Boolean,
)

/** A stored count, coerced to something usable. Anything unusable reads as zero. */
fun normalizeCount(value: Int?): Int = (value ?: 0).coerceAtLeast(0)

fun normalizeTime(value: Int?): Int {
    val seconds = value ?: 0
    if (seconds <= 0) return 0
    return minOf(MAX_TIME, seconds)
}

/**
 * The best time after a finished board, and whether it is a new record.
 *
 * Zero means "never finished one", so the first solve is always a record and an
 * unfinished board never sets one.
 */
fun updateBestTime(
    previousBest: Int?,
    seconds: Int?,
): RecordOutcome {
    val best = normalizeTime(previousBest)
    val final = normalizeTime(seconds)
    return if (final > 0 && (best == 0 || final < best)) {
        RecordOutcome(final, isRecord = true)
    } else {
        RecordOutcome(best, isRecord = false)
    }
}

/**
 * mm:ss, with the minutes never shrinking below two digits so the readout does not
 * jump about as the clock passes ten minutes.
 */
fun formatTime(seconds: Int?): String {
    val total = normalizeTime(seconds)
    val minutes = total / 60
    val rest = total % 60
    return "${if (minutes < 10) "0" else ""}$minutes:${if (rest < 10) "0" else ""}$rest"
}

/**
 * The elapsed seconds between two millisecond readings, floored at zero so a watch
 * that adjusts its clock mid-game cannot produce a negative time.
 */
fun elapsedSeconds(
    startedAt: Long,
    now: Long,
): Int {
    if (now <= startedAt) return 0
    return minOf(MAX_TIME.toLong(), (now - startedAt) / 1000).toInt()
}

/**
 * A stopwatch that survives being paused.
 *
 * Thinking time counts, but time spent staring at the pause menu does not, so the
 * clock banks what has run so far and starts a fresh segment on each resume.
 */
data class Clock(
    val banked: Int = 0,
    val since: Long? = null,
) {
    fun started(now: Long): Clock = if (since == null) copy(since = now) else this

    fun paused(now: Long): Clock = if (since == null) this else Clock(banked = seconds(now), since = null)

    fun seconds(now: Long): Int {
        val running = since?.let { elapsedSeconds(it, now) } ?: 0
        return minOf(MAX_TIME, banked + running)
    }
}
