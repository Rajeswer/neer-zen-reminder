package com.zen.neer.domain

/*
 * The Mindful Sip session as a plain state machine. It has no Android or Compose
 * dependencies, so it is unit-tested directly (see SipSessionTest).
 *
 * Phases, in order:
 *
 *   Ready ─begin─▶ Preparing ─▶ Inhale ─▶ Sip ─▶ Exhale ─▶ Settling ─▶ Completed ─done─▶ Ready
 *                  (3 s)        (4 s)     (4 s)  (4 s)      (2 s)
 *                  └──────────── cancel at any point before 12 s ─────────────▶ Ready
 *
 * - The 12-second sip (Inhale + Sip + Exhale) is the product's ritual and is unchanged.
 *   Preparing is a short lead-in; Settling lets the last ripple and the Enso finish.
 * - Holding the breath is not a separate phase: "Inhale" is the hold-your-glass beat.
 * - Paused is a flag rather than a phase, so resuming returns to exactly where it was.
 * - Cancelled is an event, not a resting state: the session returns to Ready at once.
 * - A sip is recorded exactly once, at the moment the 12 seconds complete. Cancelling
 *   before then records nothing; cancelling after then is not possible.
 *
 * Time only moves through [advance], so the countdown is independent of any animation:
 * the UI reads [ritualMs] and draws from it.
 */

enum class SipPhase { Ready, Preparing, Inhale, Sip, Exhale, Settling, Completed }

/** Session timing. Visual choreography (droplet, ripple, Enso easing) lives in the UI layer. */
data class SipTiming(
    val prepareMs: Long = 3_000L,
    val ritualMs: Long = 12_000L,
    /** Droplets that fall during the ritual; one lands at the end of each beat. */
    val drops: Int = 3,
    val settleMs: Long = 2_000L
) {
    init {
        require(prepareMs >= 0 && ritualMs > 0 && settleMs >= 0) { "durations must be positive" }
        require(drops in 1..12 && ritualMs % drops == 0L) { "ritual must divide evenly into drops" }
    }

    val beatMs: Long get() = ritualMs / drops
    val totalMs: Long get() = prepareMs + ritualMs + settleMs
}

sealed interface SipEvent {
    data class PhaseChanged(val phase: SipPhase) : SipEvent
    /** Droplet [index] (1-based) reached the water. */
    data class DropLanded(val index: Int) : SipEvent
    /** The 12 seconds completed: record one mindful sip. Emitted once per session. */
    data object SipRecorded : SipEvent
    data object Cancelled : SipEvent
}

class SipSession(val timing: SipTiming = SipTiming()) {

    var phase: SipPhase = SipPhase.Ready
        private set

    var isPaused: Boolean = false
        private set

    /** Time since Begin, including preparation. */
    var clockMs: Long = 0L
        private set

    var hasRecorded: Boolean = false
        private set

    /** Time inside the 12-second ritual, 0 during preparation. Continues through Settling. */
    val ritualMs: Long get() = (clockMs - timing.prepareMs).coerceAtLeast(0L)

    /** A session is under way: the nav bar is hidden and Back cancels. */
    val isActive: Boolean get() = phase != SipPhase.Ready && phase != SipPhase.Completed

    /** Cancel is offered until the sip is recorded. */
    val canCancel: Boolean get() = isActive && !hasRecorded

    /** Whole seconds left in the ritual; shows the full 12 during preparation. */
    val secondsLeft: Int
        get() = (((timing.ritualMs - ritualMs).coerceAtLeast(0L) + 999L) / 1000L).toInt()

    /** Seconds left in the lead-in, for "Get ready". */
    val prepareSecondsLeft: Int
        get() = (((timing.prepareMs - clockMs).coerceAtLeast(0L) + 999L) / 1000L).toInt()

    /** Droplets that have landed so far. */
    val landed: Int get() = landedAt(ritualMs)

    fun start(): Boolean {
        if (phase != SipPhase.Ready) return false
        clockMs = 0L
        hasRecorded = false
        isPaused = false
        phase = if (timing.prepareMs > 0) SipPhase.Preparing else SipPhase.Inhale
        return true
    }

    fun pause(): Boolean {
        if (!isActive || isPaused) return false
        isPaused = true
        return true
    }

    fun resume(): Boolean {
        if (!isPaused) return false
        isPaused = false
        return true
    }

    /** Ends the session without recording. Returns false when there is nothing to cancel. */
    fun cancel(): SipEvent.Cancelled? {
        if (!canCancel) return null
        reset()
        return SipEvent.Cancelled
    }

    /** "Done" on the completion screen. */
    fun acknowledge(): Boolean {
        if (phase != SipPhase.Completed) return false
        reset()
        return true
    }

    /**
     * Moves session time forward by [deltaMs] and returns what happened, in order.
     * Does nothing while paused or not active. A large step (for example after a stall)
     * still reports every landing, the recording and every phase exactly once.
     */
    fun advance(deltaMs: Long): List<SipEvent> {
        if (!isActive || isPaused || deltaMs <= 0L) return emptyList()
        val events = ArrayList<SipEvent>(2)
        val beforeRitual = ritualMs
        clockMs = (clockMs + deltaMs).coerceAtMost(timing.totalMs)
        val afterRitual = ritualMs

        val landedBefore = landedAt(beforeRitual)
        val landedAfter = landedAt(afterRitual)
        // Walk the timeline so events interleave in the order they happened.
        val beat = timing.beatMs
        for (k in landedBefore + 1..landedAfter) {
            val at = k * beat
            emitPhasesUpTo(timing.prepareMs + at - 1, events)
            events += SipEvent.DropLanded(k)
            if (k == timing.drops && !hasRecorded) {
                hasRecorded = true
                events += SipEvent.SipRecorded
            }
        }
        emitPhasesUpTo(clockMs, events)
        return events
    }

    private fun emitPhasesUpTo(clock: Long, into: MutableList<SipEvent>) {
        val target = phaseAt(clock)
        while (phase.ordinal < target.ordinal) {
            phase = SipPhase.entries[phase.ordinal + 1]
            into += SipEvent.PhaseChanged(phase)
        }
    }

    private fun phaseAt(clock: Long): SipPhase {
        val r = clock - timing.prepareMs
        val third = timing.ritualMs / 3
        return when {
            r < 0 -> SipPhase.Preparing
            r < third -> SipPhase.Inhale
            r < third * 2 -> SipPhase.Sip
            r < timing.ritualMs -> SipPhase.Exhale
            r < timing.ritualMs + timing.settleMs -> SipPhase.Settling
            else -> SipPhase.Completed
        }
    }

    private fun landedAt(ritual: Long): Int = (ritual / timing.beatMs).toInt().coerceIn(0, timing.drops)

    private fun reset() {
        phase = SipPhase.Ready
        clockMs = 0L
        isPaused = false
        hasRecorded = false
    }
}
