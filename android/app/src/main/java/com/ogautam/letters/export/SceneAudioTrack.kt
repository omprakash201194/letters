package com.ogautam.letters.export

import com.ogautam.letters.audio.ToneSynth
import com.ogautam.letters.ui.scenes.chat.PlaybackTimeline

/**
 * Lays the message tones onto one PCM buffer at the exact times they play.
 *
 * This is why the tones are synthesized rather than bundled as audio files: the exported
 * video's audio track is built by writing samples at sample-accurate offsets, not by
 * playing sounds and hoping the recording lines up.
 */
object SceneAudioTrack {

    /**
     * @param outgoing which messages, by index, are outgoing — those get the send tone.
     */
    fun build(
        timeline: PlaybackTimeline,
        outgoing: (Int) -> Boolean,
        totalMs: Long,
    ): ShortArray {
        val samples = ShortArray(samplesFor(totalMs))
        val send = ToneSynth.send()
        val receive = ToneSynth.receive()

        timeline.steps.forEach { step ->
            val tone = if (outgoing(step.index)) send else receive
            mixIn(samples, tone, samplesFor(step.bubbleAtMs))
        }
        return samples
    }

    private fun mixIn(destination: ShortArray, tone: ShortArray, offset: Int) {
        // reason: a tone that starts near the end is truncated rather than dropped — the
        // last message's sound should still begin even if the tail is short
        val count = minOf(tone.size, destination.size - offset)
        for (i in 0 until count) {
            val mixed = destination[offset + i] + tone[i]
            destination[offset + i] =
                mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    fun samplesFor(millis: Long): Int =
        (millis * ToneSynth.SAMPLE_RATE / 1_000L).toInt()
}
