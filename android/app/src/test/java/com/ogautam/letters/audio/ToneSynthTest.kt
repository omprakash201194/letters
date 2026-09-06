package com.ogautam.letters.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneSynthTest {

    private fun ShortArray.peak(): Int = maxOf { abs(it.toInt()) }

    private fun millisToSamples(ms: Int) = ms * ToneSynth.SAMPLE_RATE / 1_000

    @Test
    fun `the send tone is 120ms long`() {
        assertEquals(millisToSamples(120), ToneSynth.send().size)
    }

    @Test
    fun `the receive tone runs until the offset second tone ends`() {
        // 90ms tone, plus a second one starting at 95ms — 185ms in total.
        assertEquals(millisToSamples(185), ToneSynth.receive().size)
    }

    @Test
    fun `the gain falls away to near silence by the end`() {
        val samples = ToneSynth.send()
        val opening = samples.copyOfRange(0, samples.size / 10).peak()
        val closing = samples.copyOfRange(samples.size * 9 / 10, samples.size).peak()

        assertTrue("the tone should start audible", opening > Short.MAX_VALUE / 20)
        assertTrue("the tone should end all but silent", closing < opening / 20)
    }

    @Test
    fun `nothing clips, including where the two receive tones overlap`() {
        assertTrue(ToneSynth.send().peak() < Short.MAX_VALUE)
        assertTrue(ToneSynth.receive().peak() < Short.MAX_VALUE)
    }

    @Test
    fun `the waveform is continuous — no phase jumps to click on`() {
        val samples = ToneSynth.send()
        // A 1100 Hz sine at 44.1 kHz moves at most ~2350 per sample at full scale; a phase
        // discontinuity would show up as a jump far larger than that.
        val biggestStep = (1 until samples.size)
            .maxOf { abs(samples[it] - samples[it - 1]) }
        assertTrue("largest sample step was $biggestStep", biggestStep < 4_000)
    }
}
