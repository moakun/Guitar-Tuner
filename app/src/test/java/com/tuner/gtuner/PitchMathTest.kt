package com.tuner.gtuner

import com.tuner.gtuner.pitch.PitchMath
import org.junit.Assert.assertEquals
import org.junit.Test

class PitchMathTest {

    @Test
    fun a4_is_440() {
        assertEquals(440.0, PitchMath.midiToFrequency(69), 1e-6)
    }

    @Test
    fun standard_tuning_frequencies() {
        // Known equal-temperament values for the six open strings.
        assertEquals(82.41, PitchMath.noteNameToFrequency("E2"), 0.01)
        assertEquals(110.00, PitchMath.noteNameToFrequency("A2"), 0.01)
        assertEquals(146.83, PitchMath.noteNameToFrequency("D3"), 0.01)
        assertEquals(196.00, PitchMath.noteNameToFrequency("G3"), 0.01)
        assertEquals(246.94, PitchMath.noteNameToFrequency("B3"), 0.01)
        assertEquals(329.63, PitchMath.noteNameToFrequency("E4"), 0.01)
    }

    @Test
    fun note_names_round_trip() {
        for (name in listOf("E2", "A2", "D3", "G3", "B3", "E4", "C#3", "Eb2")) {
            val midi = PitchMath.noteNameToMidi(name)
            // Eb2 and D#2 are enharmonic; compare by MIDI, not string.
            assertEquals(midi, PitchMath.noteNameToMidi(PitchMath.noteName(midi)))
        }
    }

    @Test
    fun analyze_exact_note_is_zero_cents() {
        val r = PitchMath.analyze(PitchMath.noteNameToFrequency("E2"))
        assertEquals("E2", r.noteName)
        assertEquals(0.0, r.cents, 0.01)
    }

    @Test
    fun analyze_sharp_note_is_positive_cents() {
        // A bit above A2 should read as sharp (positive cents) of A2.
        val r = PitchMath.analyze(112.0)
        assertEquals("A2", r.noteName)
        assert(r.cents > 0) { "expected sharp, got ${r.cents}" }
    }

    @Test
    fun analyze_flat_note_is_negative_cents() {
        val r = PitchMath.analyze(108.0)
        assertEquals("A2", r.noteName)
        assert(r.cents < 0) { "expected flat, got ${r.cents}" }
    }

    @Test
    fun fold_to_octave_collapses_octave_errors() {
        val e2 = PitchMath.noteNameToFrequency("E2") // 82.41
        // An octave high (E3) and an octave low both fold back to E2.
        assertEquals(e2, PitchMath.foldToOctave(e2 * 2, e2), 0.01)
        assertEquals(e2, PitchMath.foldToOctave(e2 / 2, e2), 0.01)
        // A true neighbour (A2, a 4th up) is left alone.
        val a2 = PitchMath.noteNameToFrequency("A2")
        assertEquals(a2, PitchMath.foldToOctave(a2, e2), 0.01)
    }
}
