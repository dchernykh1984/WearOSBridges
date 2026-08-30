package com.dchernykh.bridges.game

/**
 * The boards on offer, by size.
 *
 * They are named by size rather than by difficulty because size is what they
 * honestly differ in. An audit of the shipped collection found that a 13x13
 * needed exactly the same reasoning as a 7x7 - only more of it - so a label
 * promising harder *thinking* would promise something the boards do not deliver.
 * Bigger boards do turn out to be richer, but that falls out of the space being
 * larger rather than out of a difficulty knob.
 *
 * The name is the storage key, so a level must never be renamed: a record and a
 * played-board history are kept under it.
 */
enum class Level(
    val cols: Int,
    val rows: Int,
    val islands: Int,
) {
    SMALL(cols = 7, rows = 7, islands = 8),
    MEDIUM(cols = 9, rows = 9, islands = 13),
    LARGE(cols = 11, rows = 11, islands = 18),
    HUGE(cols = 13, rows = 13, islands = 24),
    ;

    /** The label, which is digits only and so needs no translating. */
    val label: String get() = "${cols}x$rows"

    /** The file in assets that holds this size's boards. */
    val assetName: String get() = "boards/$label.txt"

    val next: Level get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** A 7x7 board fits the round screen whole, which is where to start. */
        val DEFAULT = SMALL

        fun fromStoredName(name: String?): Level = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Where a board comes from.
 *
 * The app ships a collection generated on a computer and put through a solver
 * twice, which is how it can promise every one of them has a single answer
 * reachable without guessing. It can also build one on the wrist, which nobody
 * needs for quality - but a board nobody has ever seen is worth something on its
 * own, so the choice stays with the player.
 *
 * The two are kept apart for records: a board from the collection is dealt from a
 * pool that never repeats, while a generated one is a fresh roll every time, and
 * one best time covering both would mean neither.
 */
enum class Source {
    /** Built-in first: it is the better experience, so a fresh install gets it. */
    BUILT_IN,
    GENERATED,
    ;

    val next: Source get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = BUILT_IN

        fun fromStoredName(name: String?): Source = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
