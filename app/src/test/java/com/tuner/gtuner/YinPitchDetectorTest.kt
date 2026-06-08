package com.tuner.gtuner

import com.tuner.gtuner.pitch.YinPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class YinPitchDetectorTest {

    private val sampleRate = 44_100
    private val bufferSize = 2048

    private fun sine(frequency: Double, harmonics: Boolean = false): FloatArray {
        val buf = FloatArray(bufferSize)
        for (i in buf.indices) {
            val t = i.toDouble() / sampleRate
            var v = sin(2 * PI * frequency * t)
            if (harmonics) {
                // Add weaker overtones to mimic a plucked string.
                v += 0.5 * sin(2 * PI * 2 * frequency * t)
                v += 0.3 * sin(2 * PI * 3 * frequency * t)
            }
            buf[i] = (v / 1.8).toFloat()
        }
        return buf
    }

    @Test
    fun detects_low_e_82hz() {
        val est = YinPitchDetector(sampleRate, bufferSize).detect(sine(82.41))
        assertEquals(82.41, est.frequency.toDouble(), 1.0)
        assertTrue("expected high clarity", est.clarity > 0.9f)
    }

    @Test
    fun detects_a440() {
        val est = YinPitchDetector(sampleRate, bufferSize).detect(sine(440.0))
        assertEquals(440.0, est.frequency.toDouble(), 1.0)
    }

    @Test
    fun detects_fundamental_with_overtones() {
        // The fundamental should win even when overtones are present.
        val est = YinPitchDetector(sampleRate, bufferSize).detect(sine(146.83, harmonics = true))
        assertEquals(146.83, est.frequency.toDouble(), 2.0)
    }

    @Test
    fun silence_returns_no_pitch() {
        val est = YinPitchDetector(sampleRate, bufferSize).detect(FloatArray(bufferSize))
        assertFalse("expected no pitch on silence, got ${est.frequency}", est.hasPitch)
    }
}
