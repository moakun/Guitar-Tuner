package com.tuner.gtuner.pitch

/**
 * One frame's pitch estimate.
 *
 * @param frequency Hz, or -1f when no pitch was found.
 * @param clarity 0..1 confidence; higher means a cleaner periodic signal.
 */
data class PitchEstimate(val frequency: Float, val clarity: Float) {
    val hasPitch: Boolean get() = frequency > 0f

    companion object {
        val NONE = PitchEstimate(-1f, 0f)
    }
}

/**
 * Monophonic fundamental-frequency estimator using the YIN algorithm
 * (de Cheveigné & Kawahara, 2002). Operates on a mono float buffer in [-1, 1].
 *
 * A [bufferSize] of 2048 at 44.1 kHz resolves pitches down to ~43 Hz, comfortably
 * covering a guitar's low E (82 Hz) and dropped tunings.
 *
 * @param threshold YIN absolute threshold; lower is stricter. 0.10–0.15 works well for guitar.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    bufferSize: Int,
    private val threshold: Double = 0.15,
) {
    private val halfBuffer = bufferSize / 2
    private val yin = FloatArray(halfBuffer)

    /** Estimate the fundamental, returning frequency in Hz plus a 0..1 clarity score. */
    fun detect(audio: FloatArray): PitchEstimate {
        difference(audio)
        cumulativeMeanNormalizedDifference()
        val tau = absoluteThreshold()
        if (tau == -1) return PitchEstimate.NONE
        val refinedTau = parabolicInterpolation(tau)
        if (refinedTau <= 0f) return PitchEstimate.NONE
        val clarity = (1f - yin[tau]).coerceIn(0f, 1f)
        return PitchEstimate(sampleRate / refinedTau, clarity)
    }

    /** Squared-difference function over each lag tau. */
    private fun difference(audio: FloatArray) {
        for (tau in 0 until halfBuffer) {
            var sum = 0.0
            for (i in 0 until halfBuffer) {
                val delta = audio[i] - audio[i + tau]
                sum += delta * delta
            }
            yin[tau] = sum.toFloat()
        }
    }

    /** Normalise so the function tends to 1, sharpening the dips at true periods. */
    private fun cumulativeMeanNormalizedDifference() {
        yin[0] = 1f
        var runningSum = 0.0
        for (tau in 1 until halfBuffer) {
            runningSum += yin[tau]
            yin[tau] = if (runningSum == 0.0) 1f else (yin[tau] * tau / runningSum).toFloat()
        }
    }

    /** First lag dipping below [threshold] (then nudged to its local minimum), else -1. */
    private fun absoluteThreshold(): Int {
        var tau = 2
        while (tau < halfBuffer) {
            if (yin[tau] < threshold) {
                while (tau + 1 < halfBuffer && yin[tau + 1] < yin[tau]) tau++
                return tau
            }
            tau++
        }
        return -1
    }

    /** Sub-sample refinement of the period by fitting a parabola to the dip. */
    private fun parabolicInterpolation(tau: Int): Float {
        val x0 = if (tau < 1) tau else tau - 1
        val x2 = if (tau + 1 < halfBuffer) tau + 1 else tau
        if (x0 == tau) return if (yin[tau] <= yin[x2]) tau.toFloat() else x2.toFloat()
        if (x2 == tau) return if (yin[tau] <= yin[x0]) tau.toFloat() else x0.toFloat()
        val s0 = yin[x0]
        val s1 = yin[tau]
        val s2 = yin[x2]
        val denom = 2f * (2f * s1 - s2 - s0)
        return if (denom == 0f) tau.toFloat() else tau + (s2 - s0) / denom
    }
}
