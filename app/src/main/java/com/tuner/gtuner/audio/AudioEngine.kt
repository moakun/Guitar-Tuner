package com.tuner.gtuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.tuner.gtuner.pitch.PitchSmoother
import com.tuner.gtuner.pitch.YinPitchDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Captures microphone PCM on a background thread, runs [YinPitchDetector] over a long,
 * overlapping analysis window, and publishes the latest frequency through [pitch].
 *
 * A long [analysisSize] window lowers the variance of each estimate (more wave cycles per
 * read), while a half-size [hopSize] slides it so updates stay frequent — accuracy without
 * the latency a long non-overlapping window would cost.
 *
 * The caller is responsible for holding RECORD_AUDIO permission before [start].
 */
class AudioEngine(
    private val sampleRate: Int = 44_100,
    /** Samples fed to the detector each estimate; larger = more precise, slightly more latency. */
    private val analysisSize: Int = 4096,
    /** Samples advanced between estimates. analysisSize/2 gives 50% overlap (~21 updates/sec). */
    private val hopSize: Int = 2048,
    /** Frames quieter than this RMS are treated as silence (no pitch). */
    private val silenceRms: Float = 0.007f,
    /** Reject pitch frames below this YIN clarity (0..1) to avoid noisy/ambiguous reads. */
    private val minClarity: Float = 0.85f,
    /** One-pole low-pass cutoff (Hz) applied before detection to tame string harmonics. */
    private val lowPassHz: Double = 800.0,
) {
    /** Latest detected frequency in Hz, or -1f when silent / no confident pitch. */
    private val _pitch = MutableStateFlow(-1f)
    val pitch: StateFlow<Float> = _pitch

    @Volatile private var running = false
    @Volatile private var muted = false
    @Volatile private var referenceHz = -1f
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    /** Mute detection (e.g. while a reference tone plays) so the mic doesn't hear our own tone. */
    fun setMuted(value: Boolean) {
        muted = value
    }

    /** Anchor octave correction to a known target (e.g. a locked string), or -1f to disable. */
    fun setReference(hz: Float) {
        referenceHz = hz
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val recordBufferBytes = maxOf(minBuf, analysisSize * 4)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recordBufferBytes,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        recorder = record

        val detector = YinPitchDetector(sampleRate, analysisSize)
        val smoother = PitchSmoother()
        val lpAlpha = (1.0 - exp(-2.0 * PI * lowPassHz / sampleRate)).toFloat()
        running = true
        record.startRecording()

        worker = thread(name = "gtuner-audio") {
            val window = FloatArray(analysisSize) // sliding analysis buffer
            val hop = ShortArray(hopSize)
            var lp = 0f
            while (running) {
                val read = record.read(hop, 0, hopSize)
                if (read != hopSize) continue

                if (muted) {
                    window.fill(0f)
                    lp = 0f
                    smoother.reset()
                    _pitch.value = -1f
                    continue
                }

                // Slide the window left by one hop and append the new, low-passed samples.
                System.arraycopy(window, hopSize, window, 0, analysisSize - hopSize)
                val base = analysisSize - hopSize
                for (i in 0 until hopSize) {
                    val sample = hop[i] / 32768f
                    lp += lpAlpha * (sample - lp)
                    window[base + i] = lp
                }

                val raw = if (rms(window) < silenceRms) {
                    -1f
                } else {
                    val estimate = detector.detect(window)
                    if (estimate.clarity >= minClarity) estimate.frequency else -1f
                }
                _pitch.value = smoother.process(raw, referenceHz)
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(250)
        worker = null
        recorder?.let { rec ->
            try {
                rec.stop()
            } catch (_: IllegalStateException) {
                // already stopped
            }
            rec.release()
        }
        recorder = null
        _pitch.value = -1f
    }

    private fun rms(buf: FloatArray): Float {
        var sum = 0.0
        for (v in buf) sum += v * v
        return sqrt(sum / buf.size).toFloat()
    }
}
