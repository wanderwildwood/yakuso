package com.wanderwildwood.yakuso.timer

import com.wanderwildwood.yakuso.herbs.Herb
import java.util.Locale

/** A water temperature as it is written: both scales stored, neither worked out from the other. */
data class Temp(val f: Int, val c: Int)

/**
 * A timer setting: the four the app comes with, or one of the person's own. A range ("2 to 3
 * minutes") starts at its shorter end.
 */
data class Preset(
    /** The built-in's key, or "own:<uuid>" for the person's own. */
    val key: String,
    val minSeconds: Int,
    val maxSeconds: Int,
    val temp: Temp?,
    /** The person's own name for it; null for a built-in, whose name is in strings.xml. */
    val ownName: String? = null,
    /** The person's own preset's row id, for deleting it. */
    val ownId: Long? = null,
)

/** What is brewing, or about to: the name shown, how long, and where it came from. */
data class Brew(
    val label: String,
    val seconds: Int,
    val temp: Temp? = null,
    /** The herb it was started from, so the journal can name it from the book. */
    val herbId: String? = null,
    /** When it is done, on the wall clock; 0 when not running. */
    val endAt: Long = 0,
    /** When it rang; 0 when it has not. */
    val finishedAt: Long = 0,
) {
    val running: Boolean get() = endAt > 0 && finishedAt == 0L
    val finished: Boolean get() = finishedAt > 0

    fun remainingMs(now: Long): Long = if (running) (endAt - now).coerceAtLeast(0) else seconds * 1000L
}

object BrewMath {
    /** The four the plan names. Green and black carry their water; the herbal two leave it to the herb. */
    const val GREEN = "green"
    const val BLACK = "black"
    const val HERBAL = "herbal"
    const val DECOCTION = "decoction"

    val BUILT_IN: List<Preset> = listOf(
        Preset(GREEN, 2 * 60, 3 * 60, Temp(175, 80)),
        Preset(BLACK, 4 * 60, 4 * 60, Temp(212, 100)),
        Preset(HERBAL, 5 * 60, 10 * 60, null),
        Preset(DECOCTION, 15 * 60, 20 * 60, null),
    )

    /** The shortest and longest a brew may be set to. */
    const val MIN_SECONDS = 30
    const val MAX_SECONDS = 60 * 60

    /** One press of − or +. */
    const val STEP_SECONDS = 30

    /** The last stretch, redrawn every second. */
    const val FINE_MS = 10_000L

    /** Before it, the panel is redrawn every ten seconds. */
    const val COARSE_MS = 10_000L

    fun adjust(seconds: Int, by: Int): Int = (seconds + by).coerceIn(MIN_SECONDS, MAX_SECONDS)

    /**
     * The seconds the clock shows with [remainingMs] to go. An e-ink panel redraws in full, so
     * the clock moves every ten seconds and every second only in the last ten. It is rounded up,
     * so it never says less time is left than is: 3:50 stays up until 3:50 is reached.
     */
    fun shownSeconds(remainingMs: Long): Long = when {
        remainingMs <= 0 -> 0
        remainingMs <= FINE_MS -> ceilDiv(remainingMs, 1000)
        else -> ceilDiv(remainingMs, COARSE_MS) * (COARSE_MS / 1000)
    }

    /** How long until the clock shows something else: when to draw it next. Never under 1 ms. */
    fun nextRedrawMs(remainingMs: Long): Long {
        if (remainingMs <= 0) return Long.MAX_VALUE
        val shown = shownSeconds(remainingMs) * 1000
        val step = if (remainingMs <= FINE_MS) 1000 else COARSE_MS
        return (remainingMs - (shown - step)).coerceAtLeast(1)
    }

    /** "4:00", "12:05", "0:09". */
    fun clock(seconds: Long): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    /** Minutes for a whole number of them, else minutes and seconds: "4", "2:30". */
    fun minutes(seconds: Int): String = if (seconds % 60 == 0) (seconds / 60).toString() else clock(seconds.toLong())

    /** The timer as a herb's page opens it: its name, its shorter steep, and its water. */
    fun fromHerb(herb: Herb): Brew? {
        val p = herb.preparation ?: return null
        if (herb.dontUse) return null
        return Brew(label = herb.name, seconds = p.steepMin * 60, temp = Temp(p.tempF, p.tempC), herbId = herb.id)
    }

    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b
}
