package com.tuner.gtuner

import com.tuner.gtuner.pitch.PitchSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchSmootherTest {

    @Test
    fun rejects_single_octave_glitch() {
        val s = PitchSmoother()
        // Steady 110 Hz with one doubled-frequency glitch frame.
        val inputs = listOf(110f, 110f, 220f, 110f, 110f, 110f)
        var last = -1f
        for (v in inputs) last = s.process(v)
        // The glitch should never drag the output near 220.
        assertEquals(110.0, last.toDouble(), 3.0)
    }

    @Test
    fun settles_on_steady_input() {
        val s = PitchSmoother()
        var last = -1f
        repeat(10) { last = s.process(82.41f) }
        assertEquals(82.41, last.toDouble(), 0.5)
    }

    @Test
    fun snaps_to_new_note_quickly() {
        val s = PitchSmoother()
        repeat(6) { s.process(110f) }          // settle on A2
        // Move to D3 (146.83) and feed enough frames to clear the median window.
        var last = -1f
        repeat(4) { last = s.process(146.83f) }
        assertEquals(146.83, last.toDouble(), 3.0)
    }

    @Test
    fun steady_noisy_input_converges_to_precise_mean() {
        val s = PitchSmoother()
        val samples = listOf(
            110.0f, 110.1f, 109.9f, 110.05f, 110.0f, 109.95f,
            110.1f, 110.0f, 110.0f, 110.05f, 109.98f, 110.02f,
        )
        var last = -1f
        for (v in samples) last = s.process(v)
        // Tight averaging should land essentially on the mean (~110), not bounce with the noise.
        assertEquals(110.0, last.toDouble(), 0.1)
    }

    @Test
    fun reference_anchors_to_target_octave() {
        val s = PitchSmoother()
        // Detector consistently reports E3 (the octave); reference is the low-E target.
        var last = -1f
        repeat(5) { last = s.process(164.81f, referenceHz = 82.41f) }
        assertEquals("should anchor to the low-E fundamental", 82.41, last.toDouble(), 2.0)
    }

    @Test
    fun folds_sustained_octave_error() {
        val s = PitchSmoother()
        repeat(6) { s.process(82.41f) }   // settle on low E
        var last = -1f
        repeat(5) { last = s.process(164.81f) } // detector jumps to the octave and stays
        assertEquals("octave error should fold back to the fundamental", 82.41, last.toDouble(), 2.0)
    }

    @Test
    fun holds_then_drops_on_silence() {
        val s = PitchSmoother(holdFrames = 3)
        repeat(5) { s.process(196f) }
        assertTrue(s.process(-1f) > 0f)   // held
        assertTrue(s.process(-1f) > 0f)   // held
        assertTrue(s.process(-1f) > 0f)   // held (== holdFrames)
        assertTrue(s.process(-1f) < 0f)   // gave up
    }
}
