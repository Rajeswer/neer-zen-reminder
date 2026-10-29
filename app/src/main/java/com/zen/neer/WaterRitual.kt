package com.zen.neer

/**
 * Timing model for the Mindful Sip ritual.
 *
 * The ring records the 12-second sip sequence itself (not the daily count). The sequence
 * is divided into [BEATS] equal beats; one droplet lands at the end of each beat. Every
 * visual below is a pure function of the real elapsed session time, so the droplet,
 * the ripple and the Enso can never drift apart, and cancelling simply resets time.
 *
 *   droplet forms → falls → lands at beat k → ripple spreads → Enso advances to k / BEATS
 */
internal object WaterRitual {
    const val DURATION_MS = 12_000L
    const val BEATS = 6
    const val BEAT_MS = DURATION_MS / BEATS

    /** Droplets that have reached the water by [elapsedMs]. */
    fun landed(elapsedMs: Long): Int = (elapsedMs / BEAT_MS).toInt().coerceIn(0, BEATS)

    /** Remaining whole seconds, for the countdown. */
    fun secondsLeft(elapsedMs: Long): Int =
        (((DURATION_MS - elapsedMs).coerceAtLeast(0L) + 999L) / 1000L).toInt()

    fun phase(elapsedMs: Long): RitualPhase = when {
        elapsedMs < DURATION_MS / 3 -> RitualPhase.Hold
        elapsedMs < DURATION_MS * 2 / 3 -> RitualPhase.Sip
        elapsedMs < DURATION_MS -> RitualPhase.Still
        else -> RitualPhase.Settling
    }

    /**
     * Enso progress 0..1. It moves only when a droplet lands, easing over
     * [Motion.ensoAdvanceMs] from the previous landing's value.
     */
    fun ensoProgress(elapsedMs: Long, reducedMotion: Boolean): Float {
        val k = landed(elapsedMs)
        if (k == 0) return 0f
        val since = elapsedMs - k * BEAT_MS
        val t = if (reducedMotion) 1f else (since.toFloat() / Motion.ensoAdvanceMs).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        return ((k - 1) + eased) / BEATS
    }

    /** The droplet on its way to the next landing, or null when none is visible. */
    fun droplet(elapsedMs: Long): DropletFrame? {
        val next = (elapsedMs / BEAT_MS).toInt() + 1
        if (next > BEATS) return null
        val landAt = next * BEAT_MS
        val appearAt = landAt - Motion.dropletFallMs - Motion.dropletFormMs
        if (elapsedMs < appearAt) return null
        val t = elapsedMs - appearAt
        return if (t < Motion.dropletFormMs) {
            DropletFrame(formation = t.toFloat() / Motion.dropletFormMs, fall = 0f)
        } else {
            DropletFrame(formation = 1f, fall = ((t - Motion.dropletFormMs).toFloat() / Motion.dropletFallMs).coerceIn(0f, 1f))
        }
    }

    /** Progress 0..1 of the ripple from the latest landing, or null once it has settled. */
    fun ripple(elapsedMs: Long): Float? {
        val k = landed(elapsedMs)
        if (k == 0) return null
        val since = elapsedMs - k * BEAT_MS
        return if (since < Motion.rippleMs) since.toFloat() / Motion.rippleMs else null
    }
}

/** [formation] 0..1 grows the droplet in place; [fall] 0..1 carries it to the water. */
internal class DropletFrame(val formation: Float, val fall: Float)
