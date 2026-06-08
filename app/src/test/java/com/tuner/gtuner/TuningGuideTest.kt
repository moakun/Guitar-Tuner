package com.tuner.gtuner

import com.tuner.gtuner.data.Tunings
import com.tuner.gtuner.pitch.PitchMath
import com.tuner.gtuner.pitch.TuningGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningGuideTest {

    private val standard = Tunings.all.first { it.name == "Standard" }

    @Test
    fun picks_nearest_string_in_standard_tuning() {
        // Slightly flat A2 should map to the 2nd string (index 1) and read flat.
        val a2 = PitchMath.noteNameToFrequency("A2") * 0.99
        val nearest = TuningGuide.nearestString(a2, standard.frequencies())
        assertEquals(1, nearest.index)
        assertTrue("expected flat", nearest.cents < 0)
    }

    @Test
    fun guided_display_labels_the_target_string() {
        val d3 = PitchMath.noteNameToFrequency("D3").toFloat()
        val display = TuningGuide.display(d3, standard)
        assertEquals("D3", display.label)
        assertEquals(2, display.stringIndex)
        assertEquals(0.0, display.cents, 1.0)
    }

    @Test
    fun chromatic_display_has_no_string_index() {
        val display = TuningGuide.display(440f, tuning = null)
        assertEquals("A4", display.label)
        assertEquals(-1, display.stringIndex)
    }

    @Test
    fun no_pitch_is_inactive() {
        val display = TuningGuide.display(-1f, standard)
        assertFalse(display.active)
        assertEquals("—", display.label)
    }

    @Test
    fun locked_string_overrides_nearest() {
        // Play an A2 but lock onto the low-E string: it should read against E2, not A2.
        val a2 = PitchMath.noteNameToFrequency("A2").toFloat()
        val display = TuningGuide.display(a2, standard, lockedString = 0)
        assertEquals("E2", display.label)
        assertEquals(0, display.stringIndex)
        // A2 is well above E2, so a large positive (sharp) reading.
        assertTrue(display.cents > 400)
    }

    @Test
    fun locked_string_shows_target_label_without_signal() {
        val display = TuningGuide.display(-1f, standard, lockedString = 5)
        assertFalse(display.active)
        assertEquals("E4", display.label)
        assertEquals(5, display.stringIndex)
    }

    @Test
    fun octave_high_low_e_still_reads_in_tune() {
        // The detector latches onto E3 (the octave) but the player is tuning the low-E string.
        val e3 = PitchMath.noteNameToFrequency("E2").toFloat() * 2f
        val locked = TuningGuide.display(e3, standard, lockedString = 0)
        assertEquals("E2", locked.label)
        assertEquals("octave error should still read in tune", 0.0, locked.cents, 1.0)

        // And auto mode should map it to the low-E string, not some neighbour.
        val auto = TuningGuide.display(e3, standard)
        assertEquals(0, auto.stringIndex)
        assertEquals(0.0, auto.cents, 1.0)
    }

    @Test
    fun calibration_shifts_targets() {
        // At A4 = 432, the nearest "A2" target is lower, so a true-440 A2 reads sharp.
        val a2At440 = PitchMath.noteNameToFrequency("A2", a4 = 440.0).toFloat()
        val display = TuningGuide.display(a2At440, standard, a4 = 432.0, lockedString = 1)
        assertEquals("A2", display.label)
        assertTrue("expected sharp against 432 reference", display.cents > 10)
    }
}
