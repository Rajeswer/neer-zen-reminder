package com.zen.neer.feature.mindfulsip

import com.zen.neer.design.theme.Motion
import com.zen.neer.domain.SipTiming

/**
 * What the fountain looks like at a moment of the ritual. Every value is a pure function
 * of the session's real ritual time, so the droplet, ripple and Enso can never drift from
 * the countdown, and cancelling simply resets time.
 *
 * Within each 4-second beat:
 *
 *   meniscus rests at the spout ─▶ droplet swells (1.7 s) ─▶ lets go and falls (0.68 s)
 *   ─▶ lands at the beat boundary: impact, ripple spreads (2.6 s), Enso advances (1.5 s)
 *
 * The droplet only ever moves straight down from the spout to the centre of the water.
 */
internal class SipChoreography(private val timing: SipTiming) {

    /** Droplets landed by [ritualMs]. */
    fun landed(ritualMs: Long): Int = (ritualMs / timing.beatMs).toInt().coerceIn(0, timing.drops)

    /**
     * Enso progress 0..1. Holds still between landings and eases forward after each one,
     * so it always reflects how many droplets have actually landed.
     */
    fun ensoProgress(ritualMs: Long, reducedMotion: Boolean): Float {
        val k = landed(ritualMs)
        if (k == 0) return 0f
        val since = ritualMs - k * timing.beatMs
        val t = if (reducedMotion) 1f else (since.toFloat() / Motion.ensoAdvanceMs).coerceIn(0f, 1f)
        return ((k - 1) + breathe(t)) / timing.drops
    }

    /** The droplet at the spout or in the air, or null after the last landing. */
    fun droplet(ritualMs: Long, started: Boolean): DropletFrame? {
        if (!started) return DropletFrame.resting
        val k = landed(ritualMs)
        if (k >= timing.drops) return null
        val landAt = (k + 1) * timing.beatMs
        val releaseAt = landAt - Motion.dropletFallMs
        val formAt = releaseAt - Motion.dropletFormMs
        val sinceLanding = ritualMs - k * timing.beatMs
        return when {
            ritualMs >= releaseAt -> {
                val f = ((ritualMs - releaseAt).toFloat() / Motion.dropletFallMs).coerceIn(0f, 1f)
                // Gravity: position grows with the square of time; the drop rounds out as it falls.
                DropletFrame(size = 1f, stretch = 1.12f - 0.12f * f, fall = f * f, alpha = 1f)
            }
            ritualMs >= formAt -> {
                val g = breathe(((ritualMs - formAt).toFloat() / Motion.dropletFormMs).coerceIn(0f, 1f))
                // Swells from the meniscus and lengthens just before it lets go.
                DropletFrame(size = MENISCUS + (1f - MENISCUS) * g, stretch = 1f + 0.12f * g * g, fall = 0f, alpha = 1f)
            }
            else -> {
                // The meniscus reforms after a landing.
                val back = if (k == 0) 1f else (sinceLanding.toFloat() / Motion.meniscusMs).coerceIn(0f, 1f)
                DropletFrame(size = MENISCUS, stretch = 1f, fall = 0f, alpha = back)
            }
        }
    }

    /** Ripple from the latest landing, 0..1, or null once it has faded. */
    fun ripple(ritualMs: Long): Float? {
        val k = landed(ritualMs)
        if (k == 0) return null
        val since = ritualMs - k * timing.beatMs
        return if (since < Motion.rippleMs) since.toFloat() / Motion.rippleMs else null
    }

    companion object {
        const val MENISCUS = 0.38f

        /** Sine in-out on 0..1, matching [Motion.breath]. */
        fun breathe(t: Float): Float = (0.5 - 0.5 * kotlin.math.cos(Math.PI * t.coerceIn(0f, 1f))).toFloat()
    }
}

/**
 * [size] 0..1 of the full droplet, [stretch] its vertical elongation, [fall] 0..1 of the way
 * from the spout to the water, [alpha] its opacity.
 */
internal data class DropletFrame(val size: Float, val stretch: Float, val fall: Float, val alpha: Float) {
    companion object {
        val resting = DropletFrame(size = SipChoreography.MENISCUS, stretch = 1f, fall = 0f, alpha = 1f)
    }
}
