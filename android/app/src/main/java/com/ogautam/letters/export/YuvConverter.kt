package com.ogautam.letters.export

import android.media.Image
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
     * `COLOR_FormatYUV420Flexible` is last because it says only that the buffer is 4:2:0,
     * not which layout — its planes have to be asked, per frame, via
     * [MediaCodec.getInputImage]. Many hardware encoders offer nothing else, so refusing it
     * means refusing to export at all on those devices.
     */
    val SUPPORTED = intArrayOf(
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
    )

    /**
     * Writes a frame through the codec's own [Image], which describes where each plane
     * actually sits: `rowStride` gives the row padding and `pixelStride` says whether chroma
     * is interleaved (NV12/NV21) or in separate planes (I420). Asking beats assuming, and it
     * is the only correct way to fill a buffer whose format is merely "4:2:0".
     */
    fun writeInto(image: Image, argb: IntArray, width: Int, height: Int) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val lumaRow = ByteArray(width)
        val uRow = ByteArray(width / 2)
        val vRow = ByteArray(width / 2)

        for (y in 0 until height) {
            val sourceRow = y * width
            val chromaRow = y / 2
            var chromaIndex = 0

            for (x in 0 until width) {
                val pixel = argb[sourceRow + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                lumaRow[x] = luma(r, g, b).toByte()

                if (y and 1 == 0 && x and 1 == 0) {
                    uRow[chromaIndex] = chromaU(r, g, b).toByte()
                    vRow[chromaIndex] = chromaV(r, g, b).toByte()
                    chromaIndex++
                }
            }

            writeRow(yPlane, lumaRow, row = y, count = width)
            if (y and 1 == 0) {
                writeRow(uPlane, uRow, row = chromaRow, count = width / 2)
                writeRow(vPlane, vRow, row = chromaRow, count = width / 2)
            }
        }
    }

    private fun writeRow(plane: Image.Plane, source: ByteArray, row: Int, count: Int) {
        val buffer = plane.buffer
        val start = row * plane.rowStride
        if (plane.pixelStride == 1) {
            // Packed: one bulk copy per row.
            buffer.position(start)
            buffer.put(source, 0, count)
        } else {
            // Interleaved (NV12/NV21): every pixelStride-th byte belongs to this plane.
            for (i in 0 until count) {
                buffer.put(start + i * plane.pixelStride, source[i])
            }
        }
    }

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

                out[rowStart + x] = luma(r, g, b).toByte()

                if (y and 1 == 0 && x and 1 == 0) {
                    val chromaRow = y / 2
                    if (semiPlanar) {
                        val index = chromaStart + chromaRow * chromaRowStride + x
                        out[index] = chromaU(r, g, b).toByte()
                        out[index + 1] = chromaV(r, g, b).toByte()
                    } else {
                        val offset = chromaRow * chromaRowStride + x / 2
                        out[chromaStart + offset] = chromaU(r, g, b).toByte()
                        out[vPlaneStart + offset] = chromaV(r, g, b).toByte()
                    }
                }
            }
        }
    }

    fun bufferSize(rowStride: Int, sliceHeight: Int): Int = rowStride * sliceHeight * 3 / 2

    // BT.601 studio swing, which is what an AVC encoder expects by default. Shared by both
    // paths so the picture cannot differ depending on how the buffer was filled.

    fun luma(r: Int, g: Int, b: Int): Int =
        (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)

    fun chromaU(r: Int, g: Int, b: Int): Int =
        (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)

    fun chromaV(r: Int, g: Int, b: Int): Int =
        (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
}
