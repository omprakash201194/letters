package com.ogautam.letters.export

/**
 * What an exported scene looks like. Fixed rather than taken from the device, so the same
 * scene exports identically from a tablet and a phone.
 */
object VideoSpec {
    const val WIDTH = 720
    const val HEIGHT = 1280
    const val FPS = 30
    const val BIT_RATE = 4_000_000
    const val I_FRAME_INTERVAL_SECONDS = 1

    /** The renderer works in dp; this is the density the exported frame is drawn at. */
    const val DENSITY = 2f

    /** A beat of stillness at the end so the last message is readable before it cuts. */
    const val TAIL_MS = 1_500L

    const val VIDEO_MIME = "video/avc"
    const val AUDIO_MIME = "audio/mp4a-latm"
    const val AUDIO_BIT_RATE = 128_000
}
