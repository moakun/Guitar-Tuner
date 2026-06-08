package com.tuner.gtuner.pitch

import com.tuner.gtuner.data.Tuning
import kotlin.math.abs

/**
 * Pure display logic shared by the UI. Kept Android-free so it can be unit-tested.
 */
object TuningGuide {

    /** Which string of [targets] (Hz, low-to-high) a frequency is closest to, plus its cents offset. */
    fun nearestString(frequency: Double, targets: List<Double>): NearestString {
        var bestIndex = 0
        var bestAbs = Double.MAX_VALUE
        var bestCents = 0.0
        targets.forEachIndexed { i, target ->
            // Octave-tolerant: a low string read an octave high still matches its real string.
            val folded = PitchMath.foldToOctave(frequency, target)
            val cents = PitchMath.centsBetween(folded, target)
            if (abs(cents) < bestAbs) {
                bestAbs = abs(cents)
                bestIndex = i
                bestCents = cents
            }
        }
        return NearestString(bestIndex, bestCents)
    }

    /**
     * Turn a (possibly absent) frequency into everything the meter needs to draw.
     *
     * @param tuning the selected tuning, or null for free chromatic mode.
     * @param a4 reference pitch (Hz) for calibration, default 440.
     * @param lockedString if >= 0, measure against this string only (manual precision mode)
     *   instead of auto-picking the nearest string.
     */
    fun display(
        hz: Float,
        tuning: Tuning?,
        a4: Double = PitchMath.A4_FREQ,
        lockedString: Int = -1,
    ): TunerDisplay {
        if (hz <= 0f) {
            // With no signal, still surface the locked target so the user knows what to play.
            val label = if (tuning != null && lockedString in tuning.notes.indices) {
                tuning.notes[lockedString]
            } else {
                "—"
            }
            val index = if (tuning != null) lockedString else -1
            return TunerDisplay(label = label, cents = 0.0, active = false, stringIndex = index)
        }

        val f = hz.toDouble()
        if (tuning == null) {
            val r = PitchMath.analyze(f, a4)
            return TunerDisplay(label = r.noteName, cents = r.cents, active = true, stringIndex = -1)
        }

        val targets = tuning.frequencies(a4)
        val index = if (lockedString in targets.indices) {
            lockedString
        } else {
            nearestString(f, targets).index
        }
        val folded = PitchMath.foldToOctave(f, targets[index])
        return TunerDisplay(
            label = tuning.notes[index],
            cents = PitchMath.centsBetween(folded, targets[index]),
            active = true,
            stringIndex = index,
        )
    }
}

data class NearestString(val index: Int, val cents: Double)

/** What [TuningGuide.display] hands the UI for one frame. */
data class TunerDisplay(
    val label: String,
    val cents: Double,
    val active: Boolean,
    /** Index of the guided string being tuned, or -1 in chromatic mode / no signal. */
    val stringIndex: Int,
)
