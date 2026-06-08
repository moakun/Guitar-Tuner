package com.tuner.gtuner.pitch

import kotlin.math.abs

/**
 * Stabilises a stream of raw frequency estimates so the on-screen readout sits still
 * even on harmonically rich plucked strings.
 *
 *  - a **median window** rejects single-frame outliers,
 *  - **octave folding** collapses the classic YIN error of latching onto the 2nd harmonic
 *    (or sub-octave) back onto the running estimate,
 *  - within a note the value is **EMA-smoothed** to kill sub-Hz jitter, and once the reading
 *    is genuinely steady the averaging **tightens** (smaller alpha) so the displayed pitch
 *    settles to a precise mean instead of trembling on the last digit,
 *  - a genuine note/string change only **snaps** once the last few frames agree
 *    (a brief harmonic flip-flop is not coherent, so it is ignored instead of chased),
 *  - a short **hold** keeps the last value through brief dropouts.
 *
 * The audio loop runs at ~21 frames/sec (overlapping windows @ 44.1 kHz).
 */
class PitchSmoother(
    private val windowSize: Int = 5,
    private val confirmFrames: Int = 3,
    /** Averaging weight while the pitch is moving (responsive). */
    private val emaAlpha: Double = 0.25,
    /** Averaging weight once the pitch is steady (precise, heavily averaged). */
    private val emaAlphaStable: Double = 0.06,
    private val holdFrames: Int = 6,
    /** Relative change treated as "same note" and smoothed rather than snapped (~1 semitone). */
    private val snapRatio: Double = 0.06,
    /** The confirm window must be tighter than this to accept a jump (~half a semitone). */
    private val coherenceRatio: Double = 0.03,
    /** Below this spread the reading is considered steady, triggering tighter averaging (~4 cents). */
    private val steadyRatio: Double = 0.0025,
) {
    private val window = ArrayDeque<Double>()
    private var ema = -1.0
    private var framesSinceValid = Int.MAX_VALUE

    /**
     * Feed one raw reading (<= 0 means "no pitch this frame"). Returns the stabilised Hz, or -1f.
     *
     * @param referenceHz when > 0 (a known target, e.g. a locked string), octave errors are
     *   folded toward it instead of the running estimate — the cure for low strings read an
     *   octave high.
     */
    fun process(rawHz: Float, referenceHz: Float = -1f): Float {
        if (rawHz > 0f) {
            framesSinceValid = 0
            window.addLast(rawHz.toDouble())
            while (window.size > windowSize) window.removeFirst()

            val median = window.sorted()[window.size / 2]
            val anchor = if (referenceHz > 0f) referenceHz.toDouble() else ema
            val folded = foldToOctaveOf(median, anchor)
            ema = when {
                ema <= 0.0 -> folded
                abs(folded - ema) / ema <= snapRatio -> {
                    // Same note: average. Tighten the averaging once the reading is steady.
                    val alpha = if (recentFramesAgree(steadyRatio)) emaAlphaStable else emaAlpha
                    ema + alpha * (folded - ema)
                }
                recentFramesAgree(coherenceRatio) -> folded   // confirmed new note: snap
                else -> ema                                    // transient/glitch: hold
            }
            return ema.toFloat()
        }

        // No reading this frame: hold the last value briefly, then give up.
        framesSinceValid++
        if (framesSinceValid <= holdFrames && ema > 0.0) return ema.toFloat()
        reset()
        return -1f
    }

    fun reset() {
        window.clear()
        ema = -1.0
        framesSinceValid = Int.MAX_VALUE
    }

    /** Halve/double [freq] until it lands within ~an octave of [reference]; kills octave errors. */
    private fun foldToOctaveOf(freq: Double, reference: Double): Double {
        if (reference <= 0.0) return freq
        var f = freq
        while (f / reference > 1.5) f /= 2.0
        while (reference / f > 1.5) f *= 2.0
        return f
    }

    /** True when the most recent [confirmFrames] readings cluster within [ratio]. */
    private fun recentFramesAgree(ratio: Double): Boolean {
        if (window.size < confirmFrames) return false
        val recent = window.toList().takeLast(confirmFrames)
        val min = recent.min()
        val max = recent.max()
        val mid = recent.sorted()[recent.size / 2]
        return mid > 0.0 && (max - min) / mid <= ratio
    }
}
