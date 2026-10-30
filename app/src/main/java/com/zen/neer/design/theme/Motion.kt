package com.zen.neer.design.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/*
 * Motion system. Every duration and curve in the app comes from here; screens never
 * write raw millisecond values.
 *
 * Principles (meditation apps such as Calm, Headspace and Apple Mindfulness pace motion to
 * breath, not to taps):
 *  - Breath-paced: content changes in 0.6–1 s, ritual beats in 4 s. Nothing snaps.
 *  - Ease in and out: sine-like curves for continuous motion; a long, soft deceleration
 *    for things arriving; only falling water accelerates (gravity).
 *  - Outgoing content leaves faster than incoming content arrives, so two states never
 *    compete on screen.
 *  - Direct feedback (a press) stays quick: the hand must feel answered.
 *  - Reduced motion: transitions collapse to short fades and the ritual draws its end
 *    states without travel (see LocalReducedMotion).
 */
internal object Motion {
    // ---- durations (ms)
    /** Press feedback on controls. */
    const val pressMs = 140
    /** Outgoing content fades. */
    const val exitMs = 320
    /** Incoming content and cross-fades between phases. */
    const val calmMs = 700
    /** Larger state changes: entering a session, the completion screen, overlays. */
    const val gentleMs = 1000
    /** Values that follow a running timer, such as the Focus ring. */
    const val followMs = 1000
    /** Reduced-motion fades. */
    const val reducedMs = 120

    // ---- Mindful Sip choreography (ms). One droplet per 4-second beat.
    /** The droplet swells at the spout before it lets go. */
    const val dropletFormMs = 1_700L
    /** It falls straight down into the basin. */
    const val dropletFallMs = 680L
    /** The ring of water spreads and fades. */
    const val rippleMs = 2_600L
    /** The Enso brush advances after a landing. */
    const val ensoAdvanceMs = 1_500L
    /** After a landing the spout's meniscus takes this long to reappear. */
    const val meniscusMs = 500L

    // ---- easing
    /** Continuous, breath-like motion (sine in-out). */
    val breath: Easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
    /** Things arriving or settling: long, soft deceleration (Material emphasized-decelerate). */
    val arrive: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Things leaving: gentle acceleration (Material emphasized-accelerate). */
    val depart: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** Standard in-out for size and position changes. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // ---- ready-made specs
    fun <T> calm(delayMs: Int = 0): FiniteAnimationSpec<T> = tween(calmMs, delayMs, breath)
    fun <T> gentle(delayMs: Int = 0): FiniteAnimationSpec<T> = tween(gentleMs, delayMs, breath)
    fun <T> follow(): FiniteAnimationSpec<T> = tween(followMs, 0, androidx.compose.animation.core.LinearEasing)
    fun <T> press(): FiniteAnimationSpec<T> = tween(pressMs, 0, standard)

    fun enter(reduced: Boolean = false, durationMs: Int = calmMs): EnterTransition =
        if (reduced) fadeIn(tween(reducedMs)) else fadeIn(tween(durationMs, delayMillis = exitMs / 2, easing = arrive))

    fun exit(reduced: Boolean = false): ExitTransition =
        if (reduced) fadeOut(tween(reducedMs)) else fadeOut(tween(exitMs, easing = depart))

    /** Cross-fade for content that replaces content in place (phase labels, instructions). */
    fun crossfade(reduced: Boolean = false, durationMs: Int = calmMs): ContentTransform =
        enter(reduced, durationMs) togetherWith exit(reduced)
}
