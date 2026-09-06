package com.ogautam.letters.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** The two sounds playback makes. An interface so a player can be driven without audio. */
interface SceneTones {
    fun playSend()
    fun playReceive()
    fun release()

    /** For tests and for silent rendering. */
    object Silent : SceneTones {
        override fun playSend() = Unit
        override fun playReceive() = Unit
        override fun release() = Unit
    }
}

/**
 * Plays the synthesized tones. The buffers are built once and handed to static
 * [AudioTrack]s, so replaying one is a rewind rather than another round of synthesis —
 * these fire every few hundred milliseconds during playback.
 */
class TonePlayer : SceneTones {

    private val send by lazy { track(ToneSynth.send()) }
    private val receive by lazy { track(ToneSynth.receive()) }

    override fun playSend() = play(send)

    override fun playReceive() = play(receive)

    override fun release() {
        runCatching { send.release() }
        runCatching { receive.release() }
    }

    private fun play(track: AudioTrack) {
        runCatching {
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private fun track(samples: ShortArray): AudioTrack {
        val bytes = samples.size * Short.SIZE_BYTES
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ToneSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .apply { write(samples, 0, samples.size) }
    }
}
