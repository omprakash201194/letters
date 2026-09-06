package com.ogautam.letters.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.util.Log
import com.ogautam.letters.audio.ToneSynth
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.ui.scenes.chat.ChatRenderer
import com.ogautam.letters.ui.scenes.chat.PlaySpeed
import com.ogautam.letters.ui.scenes.chat.PlaybackTimeline
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders a scene to an MP4.
 *
 * Every frame comes from the same [ChatRenderer] that draws the preview, sampled from the
 * same [PlaybackTimeline] the preview plays — at a fixed frame interval instead of in real
 * time. That is the whole reason playback was built as a function of time rather than a
 * chain of timeouts: what plays on screen is what lands in the file.
 *
 * The audio is laid out the same way. The tones are synthesized to PCM and mixed into one
 * buffer at sample-accurate offsets, so sound and picture cannot drift apart — they are
 * both derived from the same timeline rather than recorded alongside each other.
 */
class SceneExporter(
    private val context: Context,
    private val avatarFor: (String?) -> Bitmap? = { null },
) {

    /** Progress from 0 to 1, reported as frames are encoded. */
    fun interface Progress {
        fun onProgress(fraction: Float)
    }

    /**
     * Writes to a document the user picked, which is where the file's name and location come
     * from. The muxer is given the descriptor rather than a path: a document provider's Uri
     * need not be a file this process can open by name.
     */
    fun export(
        destination: Uri,
        messages: List<SceneMessageEntity>,
        speed: PlaySpeed = PlaySpeed.DEFAULT,
        progress: Progress = Progress {},
    ) {
        require(messages.isNotEmpty()) { "a scene with no messages has nothing to export" }
        val descriptor = context.contentResolver.openFileDescriptor(destination, "rw")
            ?: error("that location could not be opened for writing")
        descriptor.use {
            encode(messages, speed, progress) {
                MediaMuxer(it.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
        }
    }

    /** Exports to a plain file. Used by the instrumented test, which has no document picker. */
    fun export(
        sceneName: String,
        messages: List<SceneMessageEntity>,
        speed: PlaySpeed = PlaySpeed.DEFAULT,
        progress: Progress = Progress {},
    ): File {
        require(messages.isNotEmpty()) { "a scene with no messages has nothing to export" }

        val output = outputFile(sceneName)
        runCatching {
            encode(messages, speed, progress) {
                MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
        }.onFailure {
            output.delete()
            throw it
        }
        return output
    }

    private fun encode(
        messages: List<SceneMessageEntity>,
        speed: PlaySpeed,
        progress: Progress,
        openMuxer: () -> MediaMuxer,
    ) {
        val timeline = PlaybackTimeline(messages, speed)
        val durationMs = timeline.totalMs + VideoSpec.TAIL_MS
        val frameCount = ceil(durationMs * VideoSpec.FPS / 1_000.0).toInt()
        val muxer = openMuxer()

        try {
            // Audio first, buffered whole: the muxer needs every track added before it
            // starts, and the audio is small enough to hold (seconds of AAC, not minutes).
            val pcm = SceneAudioTrack.build(
                timeline = timeline,
                outgoing = { index -> messages[index].outgoing },
                totalMs = durationMs,
            )
            val audio = encodeAudio(pcm)

            encodeVideo(
                muxer = muxer,
                timeline = timeline,
                messages = messages,
                frameCount = frameCount,
                progress = progress,
                audio = audio,
            )
        } catch (error: Throwable) {
            runCatching { muxer.release() }
            throw error
        }
    }

    // ── video ──────────────────────────────────────────────────────────────

    private fun encodeVideo(
        muxer: MediaMuxer,
        timeline: PlaybackTimeline,
        messages: List<SceneMessageEntity>,
        frameCount: Int,
        progress: Progress,
        audio: EncodedAudio,
    ) {
        val codec = MediaCodec.createEncoderByType(VideoSpec.VIDEO_MIME)
        val colorFormat = pickColorFormat(codec)
        val format = MediaFormat.createVideoFormat(
            VideoSpec.VIDEO_MIME,
            VideoSpec.WIDTH,
            VideoSpec.HEIGHT,
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, VideoSpec.BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VideoSpec.FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoSpec.I_FRAME_INTERVAL_SECONDS)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        // reason: the encoder's rows are padded to its own stride, and chroma starts after
        // sliceHeight rows rather than after height. Assuming otherwise shears the picture
        // and puts chroma in the wrong plane — a smeared, green frame.
        val rowStride = codec.inputFormat.intOr(MediaFormat.KEY_STRIDE, VideoSpec.WIDTH)
            .coerceAtLeast(VideoSpec.WIDTH)
        val sliceHeight = codec.inputFormat.intOr(MediaFormat.KEY_SLICE_HEIGHT, VideoSpec.HEIGHT)
            .coerceAtLeast(VideoSpec.HEIGHT)

        // reason: codecs differ here more than anywhere else in this file, and a wrong
        // guess shows up as a smeared green picture rather than an error. If an export ever
        // looks wrong on a device, this line says what that device asked for.
        Log.i(
            TAG,
            "encoding with ${codec.name}: colorFormat=$colorFormat, " +
                "stride=$rowStride (width ${VideoSpec.WIDTH}), " +
                "sliceHeight=$sliceHeight (height ${VideoSpec.HEIGHT})",
        )

        val renderer = ChatRenderer(VideoSpec.WIDTH.toFloat(), VideoSpec.DENSITY) {
            avatarFor(it.charAvatarPath)
        }
        renderer.setMessages(messages)

        val bitmap = Bitmap.createBitmap(VideoSpec.WIDTH, VideoSpec.HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pixels = IntArray(VideoSpec.WIDTH * VideoSpec.HEIGHT)
        val yuv = ByteArray(YuvConverter.bufferSize(rowStride, sliceHeight))

        val info = MediaCodec.BufferInfo()
        var videoTrack = -1
        var muxerStarted = false
        var frame = 0

        try {
            while (true) {
                if (frame <= frameCount) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val presentationUs = frame * 1_000_000L / VideoSpec.FPS
                        if (frame == frameCount) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, presentationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                        } else {
                            drawFrame(renderer, timeline, canvas, frame)
                            bitmap.getPixels(
                                pixels, 0, VideoSpec.WIDTH, 0, 0,
                                VideoSpec.WIDTH, VideoSpec.HEIGHT,
                            )

                            // reason: the codec's own Image says where each plane sits and
                            // how it is interleaved. Ask it when it will answer; only guess
                            // from the declared format when it will not.
                            val image = runCatching { codec.getInputImage(inputIndex) }
                                .getOrNull()
                            val size = if (image != null) {
                                YuvConverter.writeInto(
                                    image, pixels, VideoSpec.WIDTH, VideoSpec.HEIGHT,
                                )
                                FRAME_BYTES
                            } else {
                                YuvConverter.convert(
                                    argb = pixels,
                                    width = VideoSpec.WIDTH,
                                    height = VideoSpec.HEIGHT,
                                    colorFormat = colorFormat,
                                    out = yuv,
                                    rowStride = rowStride,
                                    sliceHeight = sliceHeight,
                                )
                                codec.getInputBuffer(inputIndex)!!.apply { clear(); put(yuv) }
                                yuv.size
                            }
                            codec.queueInputBuffer(inputIndex, 0, size, presentationUs, 0)
                            progress.onProgress(frame.toFloat() / frameCount)
                        }
                        frame++
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrack = muxer.addTrack(codec.outputFormat)
                        val audioTrack = muxer.addTrack(audio.format)
                        muxer.start()
                        muxerStarted = true
                        audio.writeTo(muxer, audioTrack)
                    }

                    else -> {
                        if (outputIndex < 0) continue
                        val encoded = codec.getOutputBuffer(outputIndex)!!
                        // reason: codec config bytes go into the track format, not the stream
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (info.size > 0 && muxerStarted && !isConfig) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrack, encoded, info)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            progress.onProgress(1f)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            bitmap.recycle()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private fun drawFrame(
        renderer: ChatRenderer,
        timeline: PlaybackTimeline,
        canvas: Canvas,
        frame: Int,
    ) {
        val timeMs = frame * 1_000L / VideoSpec.FPS
        val state = timeline.stateAt(timeMs)
        // The exported frame scrolls exactly as the preview does — pinned to the bottom.
        val scrollY = max(0f, renderer.contentHeight(state) - VideoSpec.HEIGHT)
        renderer.draw(
            canvas = canvas,
            state = state,
            elapsedMs = timeMs,
            scrollY = scrollY,
            viewportHeight = VideoSpec.HEIGHT.toFloat(),
        )
    }

    /**
     * The first colour format this codec and the converter both understand.
     *
     * Asked of the codec that will actually encode, not of whichever encoder happens to
     * come first in the system list — `createEncoderByType` need not return that one, and a
     * colour format taken from a different codec is a guess.
     */
    private fun pickColorFormat(codec: MediaCodec): Int {
        val supported = codec.codecInfo
            .getCapabilitiesForType(VideoSpec.VIDEO_MIME)
            .colorFormats
            .toSet()

        return YuvConverter.SUPPORTED.firstOrNull { it in supported }
            ?: error(
                "${codec.name} wants a colour format this build cannot write " +
                    "(offers ${supported.joinToString()})",
            )
    }

    // ── audio ──────────────────────────────────────────────────────────────

    /** AAC packets held until the muxer is started, with the format the muxer needs. */
    private class EncodedAudio(
        val format: MediaFormat,
        private val packets: List<Pair<MediaCodec.BufferInfo, ByteArray>>,
    ) {
        fun writeTo(muxer: MediaMuxer, track: Int) {
            packets.forEach { (info, bytes) ->
                muxer.writeSampleData(track, ByteBuffer.wrap(bytes), info)
            }
        }
    }

    private fun encodeAudio(pcm: ShortArray): EncodedAudio {
        val format = MediaFormat.createAudioFormat(
            VideoSpec.AUDIO_MIME,
            ToneSynth.SAMPLE_RATE,
            ToneSynth.CHANNELS,
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, VideoSpec.AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_AUDIO_INPUT)
        }

        val codec = MediaCodec.createEncoderByType(VideoSpec.AUDIO_MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val packets = mutableListOf<Pair<MediaCodec.BufferInfo, ByteArray>>()
        var outputFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var sampleOffset = 0
        var inputDone = false

        try {
            while (true) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        buffer.clear()
                        val capacitySamples = buffer.remaining() / Short.SIZE_BYTES
                        val count = minOf(capacitySamples, pcm.size - sampleOffset)
                        val presentationUs =
                            sampleOffset.toLong() * 1_000_000L / ToneSynth.SAMPLE_RATE

                        if (count <= 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, presentationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            buffer.asShortBuffer().put(pcm, sampleOffset, count)
                            codec.queueInputBuffer(
                                inputIndex, 0, count * Short.SIZE_BYTES, presentationUs, 0,
                            )
                            sampleOffset += count
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    else -> {
                        if (outputIndex < 0) continue
                        val encoded = codec.getOutputBuffer(outputIndex)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (info.size > 0 && !isConfig) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            encoded.get(bytes)
                            val copy = MediaCodec.BufferInfo().apply {
                                set(0, bytes.size, info.presentationTimeUs, info.flags)
                            }
                            packets += copy to bytes
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }

        return EncodedAudio(
            format = outputFormat ?: error("the audio encoder never reported a format"),
            packets = packets,
        )
    }

    // ── output ─────────────────────────────────────────────────────────────

    private fun outputFile(sceneName: String): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        directory.mkdirs()
        return File(directory, "${slug(sceneName)}-${System.currentTimeMillis()}.mp4")
    }

    /** Optional keys are absent on plenty of codecs; the packed layout is the fallback. */
    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key).takeIf { it > 0 } ?: fallback else fallback

    companion object {
        private const val TAG = "SceneExporter"
        private const val TIMEOUT_US = 10_000L

        /** The picture's own size, independent of whatever padding the buffer carries. */
        private const val FRAME_BYTES = VideoSpec.WIDTH * VideoSpec.HEIGHT * 3 / 2
        private const val MAX_AUDIO_INPUT = 16_384

        fun slug(name: String): String = name.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "scene" }
            .take(40)

        /** How long the exported file will be, for showing before the export starts. */
        fun durationMs(messages: List<SceneMessageEntity>, speed: PlaySpeed): Long =
            PlaybackTimeline(messages, speed).totalMs + VideoSpec.TAIL_MS

        fun estimatedFrames(messages: List<SceneMessageEntity>, speed: PlaySpeed): Int =
            ceil(durationMs(messages, speed) * VideoSpec.FPS / 1_000.0).roundToInt()
    }
}
