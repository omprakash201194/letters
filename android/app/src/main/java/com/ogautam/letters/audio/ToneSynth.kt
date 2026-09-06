package com.ogautam.letters.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * The two message tones, synthesized to PCM.
 *
 * They are generated rather than bundled as WAVs for a reason that only shows up in the
 * last phase: the MP4 encoder has to write these samples onto an audio track at exact
 * frame timestamps, so it needs the samples themselves, not a media player. Playback on
 * screen and playback in the file come from this one function.
 *
 * 16-bit mono at 44.1 kHz, matching the web app's oscillator: a sine with a linear
 * frequency ramp, and a gain that falls exponentially from 0.18 to 0.001.
 */
object ToneSynth {

    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 1

    private const val GAIN_START = 0.18
    private const val GAIN_END = 0.001

    /** Outgoing: 880 Hz → 1100 Hz over 120 ms. */
    fun send(): ShortArray = tone(880.0, 1100.0, durationMs = 120)

    /**
     * Incoming: 1100 Hz → 880 Hz, then 880 Hz → 750 Hz starting 95 ms in — the two
     * overlap by 85 ms, which is what gives it its two-note shape.
     */
    fun receive(): ShortArray = mix(
        Layer(tone(1100.0, 880.0, durationMs = 90), offsetMs = 0),
        Layer(tone(880.0, 750.0, durationMs = 90), offsetMs = 95),
    )

    private class Layer(val samples: ShortArray, val offsetMs: Int)

    private fun tone(startHz: Double, endHz: Double, durationMs: Int): ShortArray {
        val count = samplesFor(durationMs)
        val out = ShortArray(count)
        val durationSeconds = durationMs / 1_000.0
        var phase = 0.0

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = (t / durationSeconds).coerceIn(0.0, 1.0)

            // reason: the phase is integrated rather than computed from an instantaneous
            // frequency — sin(2π·f(t)·t) on a ramp produces an audible chirp artefact
            val frequency = startHz + (endHz - startHz) * progress
            phase += 2 * PI * frequency / SAMPLE_RATE

            val gain = GAIN_START * Math.pow(GAIN_END / GAIN_START, progress)
            out[i] = (sin(phase) * gain * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun mix(vararg layers: Layer): ShortArray {
        val length = layers.maxOf { samplesFor(it.offsetMs) + it.samples.size }
        val accumulator = IntArray(length)

        for (layer in layers) {
            val offset = samplesFor(layer.offsetMs)
            for (i in layer.samples.indices) {
                accumulator[offset + i] += layer.samples[i].toInt()
            }
        }

        return ShortArray(length) { i ->
            accumulator[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun samplesFor(millis: Int): Int = millis * SAMPLE_RATE / 1_000
}
