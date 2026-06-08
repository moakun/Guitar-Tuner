package com.tuner.gtuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays a short reference sine tone at a target frequency so the user can hear the pitch
 * they are tuning toward. Short fades top and tail avoid clicks.
 */
class TonePlayer(private val sampleRate: Int = 44_100) {
    @Volatile private var track: AudioTrack? = null

    fun play(frequency: Double, durationMs: Int = 1500) {
        stop()
        val numSamples = sampleRate * durationMs / 1000
        val fade = sampleRate / 50 // 20 ms
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val sample = sin(2 * PI * frequency * i / sampleRate)
            val envelope = when {
                i < fade -> i.toDouble() / fade
                i > numSamples - fade -> (numSamples - i).toDouble() / fade
                else -> 1.0
            }
            buffer[i] = (sample * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
        }

        val at = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            buffer.size * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        at.write(buffer, 0, buffer.size)
        track = at
        at.play()
    }

    fun stop() {
        track?.let { t ->
            try {
                t.stop()
            } catch (_: IllegalStateException) {
                // not playing
            }
            t.release()
        }
        track = null
    }
}
