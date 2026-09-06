package com.ogautam.letters.export

import android.media.MediaCodecInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuvConverterTest {

    private val semiPlanar = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    private val planar = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

    private fun convert(argb: IntArray, width: Int, height: Int, format: Int): ByteArray {
        val out = ByteArray(YuvConverter.bufferSize(width, height))
        YuvConverter.convert(argb, width, height, format, out)
        return out
    }

    private fun ByteArray.u(index: Int) = this[index].toInt() and 0xFF

    @Test
    fun `the buffer is one and a half bytes a padded pixel`() {
        assertEquals(720 * 1280 * 3 / 2, YuvConverter.bufferSize(720, 1280))
        // A codec that pads 720 rows to 768 needs the bigger buffer.
        assertEquals(768 * 1280 * 3 / 2, YuvConverter.bufferSize(768, 1280))
    }

    /**
     * The defect this guards: writing packed rows into an encoder buffer that pads them
     * shears the picture a little further on every row and lands chroma in the wrong plane —
     * a smeared, green frame. Nothing on the emulator caught it, because its encoder happens
     * to want packed rows.
     */
    @Test
    fun `a padded stride leaves the pad bytes alone and keeps rows aligned`() {
        val width = 4
        val height = 4
        val rowStride = 8
        val sliceHeight = 6
        // Distinct rows: white on top, black below.
        val pixels = IntArray(width * height) { index ->
            if (index < width * 2) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }

        val out = ByteArray(YuvConverter.bufferSize(rowStride, sliceHeight))
        YuvConverter.convert(pixels, width, height, semiPlanar, out, rowStride, sliceHeight)

        // Each row starts a whole stride apart, not a whole width apart.
        assertEquals(235, out.u(0 * rowStride))
        assertEquals(235, out.u(1 * rowStride))
        assertEquals(16, out.u(2 * rowStride))
        assertEquals(16, out.u(3 * rowStride))
        // The padding past the picture is never written.
        (width until rowStride).forEach { assertEquals(0, out.u(it)) }
    }

    @Test
    fun `chroma starts after sliceHeight rows, not after height`() {
        val width = 4
        val height = 4
        val rowStride = 8
        val sliceHeight = 6
        val pixels = IntArray(width * height) { 0xFFFF0000.toInt() }

        val out = ByteArray(YuvConverter.bufferSize(rowStride, sliceHeight))
        YuvConverter.convert(pixels, width, height, semiPlanar, out, rowStride, sliceHeight)

        // Red: U below neutral, V well above it — at the padded chroma offset.
        val chromaStart = rowStride * sliceHeight
        assertTrue("U was ${out.u(chromaStart)}", out.u(chromaStart) < 128)
        assertTrue("V was ${out.u(chromaStart + 1)}", out.u(chromaStart + 1) > 200)
        // Nothing was written where an unpadded layout would have put it.
        assertEquals(0, out.u(rowStride * height))
    }

    /**
     * Flexible has to be accepted — many hardware encoders offer nothing else, and refusing
     * it means refusing to export at all on those devices. It comes last because its layout
     * is not promised: a frame in that format is written through the codec's own Image,
     * which says where the planes are, rather than guessed at from the format alone.
     */
    @Test
    fun `flexible is accepted, but only after the formats whose layout is known`() {
        val flexible = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        assertTrue(YuvConverter.SUPPORTED.contains(flexible))
        assertEquals(flexible, YuvConverter.SUPPORTED.last())
        assertEquals(semiPlanar, YuvConverter.SUPPORTED.first())
    }

    @Test
    fun `white and black land on the studio-swing limits`() {
        val white = convert(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2, semiPlanar)
        val black = convert(IntArray(4) { 0xFF000000.toInt() }, 2, 2, semiPlanar)

        // BT.601 studio swing puts luma in 16..235, not 0..255.
        assertEquals(235, white.u(0))
        assertEquals(16, black.u(0))
        // Neither has any colour in it.
        assertEquals(128, white.u(4))
        assertEquals(128, white.u(5))
    }

    @Test
    fun `the WhatsApp bubble colours survive the round trip`() {
        val outgoing = convert(IntArray(4) { 0xFFDCF8C6.toInt() }, 2, 2, semiPlanar)
        val teal = convert(IntArray(4) { 0xFF075E54.toInt() }, 2, 2, semiPlanar)

        // A pale green is bright and slightly green-shifted: U below neutral, V below it too.
        assertTrue("outgoing luma was ${outgoing.u(0)}", outgoing.u(0) > 200)
        assertTrue(outgoing.u(4) < 128)
        // The dark teal header is dark, and blue-green: U above neutral.
        assertTrue("teal luma was ${teal.u(0)}", teal.u(0) < 100)
        assertTrue(teal.u(4) > 128)
    }

    @Test
    fun `planar and semi-planar hold the same chroma in different places`() {
        val red = IntArray(4) { 0xFFFF0000.toInt() }
        val semi = convert(red, 2, 2, semiPlanar)
        val flat = convert(red, 2, 2, planar)

        val frameSize = 4
        // Semi-planar interleaves U and V; planar keeps them in separate runs.
        assertEquals(semi.u(frameSize), flat.u(frameSize))
        assertEquals(semi.u(frameSize + 1), flat.u(frameSize + frameSize / 4))
    }

    @Test
    fun `every luma sample is written`() {
        val width = 8
        val height = 4
        val pixels = IntArray(width * height) { 0xFF808080.toInt() }
        val out = convert(pixels, width, height, semiPlanar)

        (0 until width * height).forEach { assertTrue(out.u(it) in 16..235) }
    }
}
