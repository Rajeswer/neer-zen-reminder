package com.zen.neer.feature.mindfulsip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.zen.neer.domain.SipEvent
import com.zen.neer.domain.SipPhase
import com.zen.neer.domain.SipSession
import com.zen.neer.domain.SipTiming

/**
 * Holds one [SipSession] for the whole app (it lives above the tabs, so it is never
 * dropped by navigation) and mirrors it into Compose state for the UI.
 */
@Stable
internal class SipSessionController(timing: SipTiming = SipTiming()) {
    private val session = SipSession(timing)
    val timing: SipTiming get() = session.timing
    val choreography = SipChoreography(timing)

    var phase by mutableStateOf(SipPhase.Ready)
        private set
    var isPaused by mutableStateOf(false)
        private set
    private val clock = mutableLongStateOf(0L)

    /** Read in draw lambdas only, so per-frame changes redraw without recomposing. */
    val ritualMs: Long get() = (clock.longValue - timing.prepareMs).coerceAtLeast(0L)
    val clockMs: Long get() = clock.longValue

    val isActive: Boolean get() = phase != SipPhase.Ready && phase != SipPhase.Completed
    /** Matches [SipSession.canCancel]: the sip is recorded as Settling begins. */
    val canCancel: Boolean get() = isActive && phase != SipPhase.Settling

    /** Whole seconds left in the ritual. Reads frame state: wrap in derivedStateOf in composition. */
    val secondsLeft: Int
        get() = (((timing.ritualMs - ritualMs).coerceAtLeast(0L) + 999L) / 1000L).toInt()

    /** Whole seconds left in the lead-in. Reads frame state: wrap in derivedStateOf in composition. */
    val prepareSecondsLeft: Int
        get() = (((timing.prepareMs - clock.longValue).coerceAtLeast(0L) + 999L) / 1000L).toInt()

    fun start(): Boolean = session.start().also { if (it) sync() }
    fun pause(): Boolean = session.pause().also { if (it) sync() }
    fun resume(): Boolean = session.resume().also { if (it) sync() }
    fun cancel(): Boolean = (session.cancel() != null).also { if (it) sync() }
    fun acknowledge(): Boolean = session.acknowledge().also { if (it) sync() }

    fun advance(deltaMs: Long): List<SipEvent> = session.advance(deltaMs).also { sync() }

    private fun sync() {
        phase = session.phase
        isPaused = session.isPaused
        clock.longValue = session.clockMs
    }
}

/**
 * Drives the session from display frames while it is running. Keyed on running state, so
 * recomposition never starts a second loop.
 */
@Composable
internal fun RunSipSession(controller: SipSessionController, onEvent: (SipEvent) -> Unit) {
    val latestOnEvent by rememberUpdatedState(onEvent)
    val running = controller.isActive && !controller.isPaused
    LaunchedEffect(controller, running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (controller.isActive && !controller.isPaused) {
            val now = withFrameNanos { it }
            val step = ((now - last) / 1_000_000L).coerceIn(0L, MAX_FRAME_STEP_MS)
            last = now
            controller.advance(step).forEach { latestOnEvent(it) }
        }
    }
}

/**
 * Leaving the app pauses the session explicitly (ON_STOP), so this cap only guards against
 * a long stall. It is generous so slow devices keep the countdown in step with real time.
 */
private const val MAX_FRAME_STEP_MS = 250L
