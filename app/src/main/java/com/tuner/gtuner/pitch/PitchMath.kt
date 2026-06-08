package com.tuner.gtuner.pitch

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure equal-temperament pitch math. No Android dependencies, so it is fully unit-testable.
 *
 * Reference pitch is A4 = 440 Hz (configurable for 432 Hz tuning later). The MIDI note
 * number convention is used internally: MIDI 69 == A4, MIDI 60 == C4 (middle C).
 */
object PitchMath {
    const val A4_FREQ = 440.0
    const val A4_MIDI = 69

    private val NOTE_NAMES =
        arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** Frequency (Hz) of a MIDI note number under equal temperament. */
    fun midiToFrequency(midi: Int, a4: Double = A4_FREQ): Double =
        a4 * 2.0.pow((midi - A4_MIDI) / 12.0)

    /** Continuous MIDI position for a frequency (e.g. 69.5 == half a semitone above A4). */
    fun frequencyToMidi(frequency: Double, a4: Double = A4_FREQ): Double =
        A4_MIDI + 12.0 * log2(frequency / a4)

    /** Scientific-pitch note name for a MIDI number, e.g. 40 -> "E2", 69 -> "A4". */
    fun noteName(midi: Int): String {
        val index = ((midi % 12) + 12) % 12
        val octave = Math.floorDiv(midi, 12) - 1
        return "${NOTE_NAMES[index]}$octave"
    }

    /** Parse a note name like "E2", "C#3" or "Eb2" into a MIDI number. */
    fun noteNameToMidi(name: String): Int {
        val match = Regex("^([A-Ga-g])([#b]?)(-?\\d+)$").find(name.trim())
            ?: throw IllegalArgumentException("Bad note name: $name")
        val (letter, accidental, octaveStr) = match.destructured
        val base = when (letter.uppercase()) {
            "C" -> 0; "D" -> 2; "E" -> 4; "F" -> 5; "G" -> 7; "A" -> 9; "B" -> 11
            else -> throw IllegalArgumentException("Bad note letter: $letter")
        }
        val accidentalShift = when (accidental) {
            "#" -> 1; "b" -> -1; else -> 0
        }
        val octave = octaveStr.toInt()
        return (octave + 1) * 12 + base + accidentalShift
    }

    /** Frequency of a note name, e.g. "E2" -> 82.41 Hz. */
    fun noteNameToFrequency(name: String, a4: Double = A4_FREQ): Double =
        midiToFrequency(noteNameToMidi(name), a4)

    /** Cents from [reference] to [frequency]: negative = flat (tune up), positive = sharp (tune down). */
    fun centsBetween(frequency: Double, reference: Double): Double =
        1200.0 * log2(frequency / reference)

    /**
     * Halve/double [frequency] until it lands within ~an octave of [reference]. Resolves the
     * common detector "octave error" on low strings, where a harmonic is reported instead of
     * the fundamental. The 1.5 ratio means true neighbours (4ths/5ths) are never folded.
     */
    fun foldToOctave(frequency: Double, reference: Double): Double {
        if (reference <= 0.0 || frequency <= 0.0) return frequency
        var f = frequency
        while (f / reference > 1.5) f /= 2.0
        while (reference / f > 1.5) f *= 2.0
        return f
    }

    /** Analyse a detected frequency into the nearest note plus a cents deviation in [-50, 50]. */
    fun analyze(frequency: Double, a4: Double = A4_FREQ): NoteReading {
        val exactMidi = frequencyToMidi(frequency, a4)
        val nearest = exactMidi.roundToInt()
        val cents = (exactMidi - nearest) * 100.0
        return NoteReading(frequency, nearest, noteName(nearest), cents)
    }

    private fun log2(x: Double) = ln(x) / ln(2.0)
}

/**
 * The result of mapping a frequency to a note.
 *
 * @param cents deviation from [noteName]: negative = flat (tune up), positive = sharp (tune down).
 */
data class NoteReading(
    val frequency: Double,
    val midi: Int,
    val noteName: String,
    val cents: Double,
)
