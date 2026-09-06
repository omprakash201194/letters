package com.ogautam.letters.export

import android.media.MediaCodecInfo

/**
 * ARGB frames to the YUV the encoder wants.
 *
 * The encoder is fed buffers rather than a Surface on purpose: a Surface input would mean
 * rendering through OpenGL, and the whole point of the Canvas renderer is that the same
 * drawing code produces the preview and the file. This conversion is the cost of that, and
 * it is paid once per frame in a background export rather than during playback.
 *
 * **The encoder's buffer is not tightly packed.** Rows are padded to `rowStride`, and the
 * chroma planes begin after `sliceHeight` rows rather than after `height` — both come from
 * the codec's own input format. Writing packed data into a padded buffer shears every row a
 * little further than the last and puts chroma in the wrong place: a smeared, green picture.
 */
object YuvConverter {

    /**
     * The colour formats this converter can write, best first.
     *
     * `COLOR_FormatYUV420Flexible` is deliberately absent. It is opaque — it promises a
     * 4:2:0 layout but not *which* one — so bytes written for it are a guess.
     */
    val SUPPORTED = intArrayOf(
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
    )

    /**
     * Converts [argb] (row-major, [width] × [height]) into [out], laid out for [colorFormat]
     * with the encoder's [rowStride] and [sliceHeight].
     *
     * Chroma is sampled from the top-left pixel of each 2×2 block rather than averaged — a
     * chat screenshot is flat colour and hard edges, where averaging only softens text.
     */
    fun convert(
        argb: IntArray,
        width: Int,
        height: Int,
        colorFormat: Int,
        out: ByteArray,
        rowStride: Int = width,
        sliceHeight: Int = height,
    ) {
        val semiPlanar = colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        val chromaStart = rowStride * sliceHeight
        val chromaRowStride = if (semiPlanar) rowStride else rowStride / 2
        val vPlaneStart = chromaStart + chromaRowStride * (sliceHeight / 2)

        for (y in 0 until height) {
            val rowStart = y * rowStride
            val sourceRow = y * width
            for (x in 0 until width) {
                val pixel = argb[sourceRow + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // BT.601 studio swing, which is what an AVC encoder expects by default.
                val luma = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                out[rowStart + x] = luma.coerceIn(0, 255).toByte()

                if (y and 1 == 0 && x and 1 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val chromaRow = y / 2
                    if (semiPlanar) {
                        val index = chromaStart + chromaRow * chromaRowStride + x
                        out[index] = u.coerceIn(0, 255).toByte()
                        out[index + 1] = v.coerceIn(0, 255).toByte()
                    } else {
                        val offset = chromaRow * chromaRowStride + x / 2
                        out[chromaStart + offset] = u.coerceIn(0, 255).toByte()
                        out[vPlaneStart + offset] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
    }

    fun bufferSize(rowStride: Int, sliceHeight: Int): Int = rowStride * sliceHeight * 3 / 2
}
