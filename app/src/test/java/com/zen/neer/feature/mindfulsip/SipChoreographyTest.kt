package com.zen.neer.feature.mindfulsip

import com.zen.neer.design.theme.Motion
import com.zen.neer.domain.SipTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SipChoreographyTest {

    private val timing = SipTiming()
    private val c = SipChoreography(timing)
    private val beat = timing.beatMs

    @Test
    fun `the beat leaves room for the droplet to form and fall`() {
        assertTrue(Motion.dropletFormMs + Motion.dropletFallMs + Motion.meniscusMs <= beat)
        assertTrue("ripple finishes before the next landing", Motion.rippleMs < beat)
        assertTrue("the Enso finishes advancing before Well done", Motion.ensoAdvanceMs <= timing.settleMs)
    }

    @Test
    fun `enso moves only after a landing and reaches 1 after the last`() {
        assertEquals(0f, c.ensoProgress(0, false), 0f)
        assertEquals(0f, c.ensoProgress(beat - 1, false), 0f)
        assertEquals(1f / 3f, c.ensoProgress(beat + Motion.ensoAdvanceMs, false), 1e-4f)
        assertEquals(1f, c.ensoProgress(timing.ritualMs + Motion.ensoAdvanceMs, false), 1e-4f)
    }

    @Test
    fun `enso never runs backwards`() {
        var last = 0f
        for (t in 0..timing.ritualMs + timing.settleMs step 10) {
            val p = c.ensoProgress(t, false)
            assertTrue("t=$t", p >= last - 1e-6f)
            last = p
        }
    }

    @Test
    fun `reduced motion jumps straight to each landing's value`() {
        assertEquals(1f / 3f, c.ensoProgress(beat, true), 1e-4f)
    }

    @Test
    fun `droplet rests, swells, falls and lands exactly at the beat`() {
        val rest = c.droplet(100, started = true)!!
        assertEquals(SipChoreography.MENISCUS, rest.size, 0f)
        assertEquals(0f, rest.fall, 0f)

        val swelling = c.droplet(beat - Motion.dropletFallMs - 1, started = true)!!
        assertTrue(swelling.size > 0.9f)
        assertEquals(0f, swelling.fall, 0f)

        val falling = c.droplet(beat - Motion.dropletFallMs / 2, started = true)!!
        assertTrue(falling.fall > 0f && falling.fall < 1f)

        val landing = c.droplet(beat - 1, started = true)!!
        assertTrue(landing.fall > 0.99f)

        val next = c.droplet(beat, started = true)!!
        assertEquals("a new meniscus forms after a landing", 0f, next.fall, 0f)
        assertEquals(0f, next.alpha, 1e-4f)
    }

    @Test
    fun `no droplet after the last landing`() {
        assertNull(c.droplet(timing.ritualMs, started = true))
        assertNotNull(c.droplet(0, started = false))
    }

    @Test
    fun `ripple belongs to the latest landing only`() {
        assertNull(c.ripple(beat - 1))
        assertEquals(0f, c.ripple(beat)!!, 0f)
        assertNull(c.ripple(beat + Motion.rippleMs))
        assertEquals(0f, c.ripple(2 * beat)!!, 0f)
    }
}
