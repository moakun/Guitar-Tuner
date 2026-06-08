package com.tuner.gtuner.data

import com.tuner.gtuner.pitch.PitchMath

/**
 * A named guitar tuning. [notes] are ordered low-to-high (6th string first, 1st string last)
 * in scientific-pitch notation, e.g. Standard = E2 A2 D3 G3 B3 E4.
 */
data class Tuning(
    val name: String,
    val notes: List<String>,
) {
    /** Target frequencies (Hz) for each string, low to high. */
    fun frequencies(a4: Double = PitchMath.A4_FREQ): List<Double> =
        notes.map { PitchMath.noteNameToFrequency(it, a4) }
}

/** Built-in catalog of common 6-string tunings. Guided mode (later) will pick from these. */
object Tunings {
    val all: List<Tuning> = listOf(
        Tuning("Standard", listOf("E2", "A2", "D3", "G3", "B3", "E4")),
        Tuning("Drop D", listOf("D2", "A2", "D3", "G3", "B3", "E4")),
        Tuning("Half-step Down", listOf("D#2", "G#2", "C#3", "F#3", "A#3", "D#4")),
        Tuning("Full-step Down", listOf("D2", "G2", "C3", "F3", "A3", "D4")),
        Tuning("Drop C", listOf("C2", "G2", "C3", "F3", "A3", "D4")),
        Tuning("Drop B", listOf("B1", "F#2", "B2", "E3", "G#3", "C#4")),
        Tuning("DADGAD", listOf("D2", "A2", "D3", "G3", "A3", "D4")),
        Tuning("Open G", listOf("D2", "G2", "D3", "G3", "B3", "D4")),
        Tuning("Open D", listOf("D2", "A2", "D3", "F#3", "A3", "D4")),
        Tuning("Open E", listOf("E2", "B2", "E3", "G#3", "B3", "E4")),
        Tuning("Open C", listOf("C2", "G2", "C3", "G3", "C4", "E4")),
        Tuning("Baritone (B Standard)", listOf("B1", "E2", "A2", "D3", "F#3", "B3")),
        Tuning("7-String Standard", listOf("B1", "E2", "A2", "D3", "G3", "B3", "E4")),
    )

    val default: Tuning = all.first()
}
