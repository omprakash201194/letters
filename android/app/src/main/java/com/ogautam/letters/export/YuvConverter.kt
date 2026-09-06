package com.ogautam.letters.export

import android.media.MediaCodecInfo

/**
 * ARGB frames to the YUV the encoder wants.
 *
 * The encoder is fed buffers rather than a Surface on purpose: a Surface input would mean
 * rendering through OpenGL, and the whole point of the Canvas renderer is that the same
 * drawing code produces the preview and the file. This conversion is the cost of that, and
 * it is paid once per frame in a background export rather than during playback.
 */
object YuvConverter {

    /** The colour formats this converter can write, best first. */
    val SUPPORTED = intArrayOf(
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
    )

    /**
     * Converts [argb] (row-major, width × height) into [out].
     *
     * Chroma is sampled from the top-left pixel of each 2×2 block rather than averaged —
     * a chat screenshot is flat colour and hard edges, where averaging softens the edges
     * of text without measurably improving anything else.
     */
    fun convert(
        argb: IntArray,
        width: Int,
        height: Int,
        colorFormat: Int,
        out: ByteArray,
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        val semiPlanar = colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        val vPlaneStart = frameSize + frameSize / 4

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = argb[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // BT.601 studio swing, which is what an AVC encoder expects by default.
                val luma = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                out[yIndex++] = luma.coerceIn(0, 255).toByte()

                if (y % 2 == 0 && x % 2 == 0) {
                    val u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                    val v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                    if (semiPlanar) {
                        out[uvIndex++] = u.coerceIn(0, 255).toByte()
                        out[uvIndex++] = v.coerceIn(0, 255).toByte()
                    } else {
                        val chromaIndex = (y / 2) * (width / 2) + (x / 2)
                        out[frameSize + chromaIndex] = u.coerceIn(0, 255).toByte()
                        out[vPlaneStart + chromaIndex] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
    }

    fun bufferSize(width: Int, height: Int): Int = width * height * 3 / 2
}
