package com.zen.neer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SipSessionTest {

    private val timing = SipTiming()

    /** Runs the session in 16 ms frames for [ms] and collects every event. */
    private fun SipSession.run(ms: Long, frame: Long = 16L): List<SipEvent> {
        val events = mutableListOf<SipEvent>()
        var left = ms
        while (left > 0) {
            val step = minOf(frame, left)
            events += advance(step)
            left -= step
        }
        return events
    }

    @Test
    fun `starts in Preparing and only once`() {
        val s = SipSession(timing)
        assertTrue(s.start())
        assertEquals(SipPhase.Preparing, s.phase)
        assertFalse("a second start while running is ignored", s.start())
        assertEquals(12, s.secondsLeft)
        assertEquals(3, s.prepareSecondsLeft)
    }

    @Test
    fun `phases follow the timeline`() {
        val s = SipSession(timing)
        s.start()
        s.run(timing.prepareMs - 1)
        assertEquals(SipPhase.Preparing, s.phase)
        s.run(1)
        assertEquals(SipPhase.Inhale, s.phase)
        s.run(4_000)
        assertEquals(SipPhase.Sip, s.phase)
        s.run(4_000)
        assertEquals(SipPhase.Exhale, s.phase)
        s.run(4_000)
        assertEquals(SipPhase.Settling, s.phase)
        s.run(timing.settleMs)
        assertEquals(SipPhase.Completed, s.phase)
        assertFalse(s.isActive)
    }

    @Test
    fun `a full session records exactly once at twelve seconds`() {
        val s = SipSession(timing)
        s.start()
        val before = s.run(timing.prepareMs + timing.ritualMs - 1)
        assertFalse(before.contains(SipEvent.SipRecorded))
        val at = s.run(1)
        assertTrue(at.contains(SipEvent.SipRecorded))
        val after = s.run(10_000)
        assertFalse(after.contains(SipEvent.SipRecorded))
        assertEquals(SipPhase.Completed, s.phase)
    }

    @Test
    fun `droplets land once per beat in order`() {
        val s = SipSession(timing)
        s.start()
        val events = s.run(timing.totalMs)
        val landed = events.filterIsInstance<SipEvent.DropLanded>().map { it.index }
        assertEquals(listOf(1, 2, 3), landed)
        assertEquals(1, events.count { it == SipEvent.SipRecorded })
    }

    @Test
    fun `one huge step still reports every event once and in order`() {
        val s = SipSession(timing)
        s.start()
        val events = s.advance(60_000)
        val expected = listOf(
            SipEvent.PhaseChanged(SipPhase.Inhale),
            SipEvent.DropLanded(1),
            SipEvent.PhaseChanged(SipPhase.Sip),
            SipEvent.DropLanded(2),
            SipEvent.PhaseChanged(SipPhase.Exhale),
            SipEvent.DropLanded(3),
            SipEvent.SipRecorded,
            SipEvent.PhaseChanged(SipPhase.Settling),
            SipEvent.PhaseChanged(SipPhase.Completed)
        )
        assertEquals(expected, events)
    }

    @Test
    fun `cancel before twelve seconds records nothing and returns to Ready`() {
        val s = SipSession(timing)
        s.start()
        val events = s.run(timing.prepareMs + 11_000)
        assertFalse(events.contains(SipEvent.SipRecorded))
        assertEquals(SipEvent.Cancelled, s.cancel())
        assertEquals(SipPhase.Ready, s.phase)
        assertEquals(0L, s.clockMs)
        assertTrue("time no longer moves", s.advance(5_000).isEmpty())
        assertTrue("a new session can start", s.start())
    }

    @Test
    fun `cannot cancel after the sip is recorded`() {
        val s = SipSession(timing)
        s.start()
        s.run(timing.prepareMs + timing.ritualMs)
        assertEquals(SipPhase.Settling, s.phase)
        assertNull(s.cancel())
        assertEquals(SipPhase.Settling, s.phase)
    }

    @Test
    fun `pause freezes time and resume continues where it left off`() {
        val s = SipSession(timing)
        s.start()
        s.run(5_000)
        val clock = s.clockMs
        assertTrue(s.pause())
        assertTrue(s.advance(10_000).isEmpty())
        assertEquals(clock, s.clockMs)
        assertTrue(s.isActive)
        assertTrue(s.resume())
        s.run(100)
        assertEquals(clock + 100, s.clockMs)
    }

    @Test
    fun `paused session can be ended without recording`() {
        val s = SipSession(timing)
        s.start()
        s.run(8_000)
        s.pause()
        assertEquals(SipEvent.Cancelled, s.cancel())
        assertFalse(s.isPaused)
        assertEquals(SipPhase.Ready, s.phase)
    }

    @Test
    fun `acknowledge returns to Ready only from Completed`() {
        val s = SipSession(timing)
        assertFalse(s.acknowledge())
        s.start()
        assertFalse(s.acknowledge())
        s.run(timing.totalMs)
        assertTrue(s.acknowledge())
        assertEquals(SipPhase.Ready, s.phase)
        assertFalse(s.hasRecorded)
    }

    @Test
    fun `countdown shows whole seconds`() {
        val s = SipSession(timing)
        s.start()
        s.run(timing.prepareMs)
        assertEquals(12, s.secondsLeft)
        s.run(1)
        assertEquals(12, s.secondsLeft)
        s.run(999)
        assertEquals(11, s.secondsLeft)
        s.run(11_000)
        assertEquals(0, s.secondsLeft)
    }

    @Test
    fun `timing is configurable`() {
        val quick = SipTiming(prepareMs = 0, ritualMs = 6_000, drops = 2, settleMs = 0)
        val s = SipSession(quick)
        s.start()
        assertEquals(SipPhase.Inhale, s.phase)
        val events = s.advance(6_000)
        assertEquals(listOf(1, 2), events.filterIsInstance<SipEvent.DropLanded>().map { it.index })
        assertEquals(SipPhase.Completed, s.phase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ritual must divide evenly into droplets`() {
        SipTiming(ritualMs = 10_000, drops = 3)
    }
}
