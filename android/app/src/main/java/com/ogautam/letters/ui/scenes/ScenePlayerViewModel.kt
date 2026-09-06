package com.ogautam.letters.ui.scenes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.net.Uri
import com.ogautam.letters.LettersApplication
import com.ogautam.letters.audio.SceneTones
import com.ogautam.letters.audio.TonePlayer
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.export.SceneExporter
import com.ogautam.letters.ui.scenes.chat.ChatTheme
import com.ogautam.letters.ui.scenes.chat.PlaySpeed
import com.ogautam.letters.ui.scenes.chat.PlaybackState
import com.ogautam.letters.ui.scenes.chat.PlaybackTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where an export has got to. */
sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val fraction: Float) : ExportState
    data class Done(val name: String, val destination: Uri) : ExportState
    data class Failed(val message: String) : ExportState
}

data class ScenePlayerUiState(
    val sceneName: String,
    val messages: List<SceneMessageEntity>,
    val speed: PlaySpeed = PlaySpeed.DEFAULT,
    val isPlaying: Boolean = false,
    val timeMs: Long = 0L,
    val playback: PlaybackState = PlaybackState(visibleCount = 0, typingIndex = null, msSinceLastBubble = 0L),
    val export: ExportState = ExportState.Idle,
) {
    val messageCount: Int get() = messages.size
    val progress: Float
        get() = if (messageCount == 0) 0f else playback.playedCount.toFloat() / messageCount
}

class ScenePlayerViewModel(
    sceneName: String,
    messages: List<SceneMessageEntity>,
    private val tones: SceneTones = TonePlayer(),
    private val exporter: SceneExporter? = null,
) : ViewModel() {

    private var timeline = PlaybackTimeline(messages, PlaySpeed.DEFAULT)

    private val _state = MutableStateFlow(
        ScenePlayerUiState(
            sceneName = sceneName,
            messages = messages,
            playback = timeline.stateAt(0),
        ),
    )
    val state: StateFlow<ScenePlayerUiState> = _state.asStateFlow()

    fun playPause() {
        val current = _state.value
        when {
            current.isPlaying -> _state.update { it.copy(isPlaying = false) }
            // Pressing play at the end restarts from the beginning, as the web app did.
            current.timeMs >= timeline.totalMs -> seekTo(0L, playing = true)
            else -> _state.update { it.copy(isPlaying = true) }
        }
    }

    /** Advances the clock. Called once per frame while playing. */
    fun advance(deltaMs: Long) {
        val current = _state.value
        if (!current.isPlaying) return

        val next = (current.timeMs + deltaMs).coerceAtMost(timeline.totalMs)
        val playback = timeline.stateAt(next)

        // reason: sound follows bubbles actually crossed, so scrubbing stays silent and a
        // frame long enough to cross two messages still plays both
        for (index in current.playback.visibleCount until playback.visibleCount) {
            val message = current.messages.getOrNull(index) ?: continue
            if (message.outgoing) tones.playSend() else tones.playReceive()
        }

        _state.update {
            it.copy(
                timeMs = next,
                playback = playback,
                isPlaying = next < timeline.totalMs,
            )
        }
    }

    /** The progress bar jumps to a message boundary rather than an arbitrary instant. */
    fun seekToIndex(index: Int) {
        val clamped = index.coerceIn(0, timeline.messageCount)
        seekTo(timeline.timeAtIndex(clamped), playing = false)
    }

    /**
     * A seek lands exactly on a bubble's timestamp, which is the first instant of its
     * pop-in — and playback is paused, so it would never finish. Settled state is what the
     * viewer means by "show me message five".
     */
    private fun settled(state: PlaybackState): PlaybackState =
        state.copy(msSinceLastBubble = ChatTheme.POP_DURATION_MS)

    fun setSpeed(speed: PlaySpeed) {
        val current = _state.value
        if (current.speed == speed) return

        // Rebuild at the new speed and land on the message the viewer was already on,
        // rather than at whatever instant the old timeline's clock happened to read.
        val visible = current.playback.visibleCount
        timeline = PlaybackTimeline(current.messages, speed)
        val time = timeline.timeAtIndex(visible)
        val playback = timeline.stateAt(time)
        _state.update {
            it.copy(
                speed = speed,
                timeMs = time,
                playback = if (current.isPlaying) playback else settled(playback),
            )
        }
    }

    private fun seekTo(timeMs: Long, playing: Boolean) {
        val playback = timeline.stateAt(timeMs)
        _state.update {
            it.copy(
                timeMs = timeMs,
                playback = if (playing) playback else settled(playback),
                isPlaying = playing,
            )
        }
    }

    /**
     * Exports on the IO dispatcher: encoding is hundreds of frames of drawing and colour
     * conversion, and it must not be on the frame clock.
     */
    /** The name the picker should suggest; the user changes it there if they want. */
    fun suggestedFileName(): String =
        "${SceneExporter.slug(_state.value.sceneName)}.mp4"

    fun export(destination: Uri, displayName: String) {
        val exporter = exporter ?: return
        val current = _state.value
        if (current.export is ExportState.Running || current.messages.isEmpty()) return

        _state.update { it.copy(isPlaying = false, export = ExportState.Running(0f)) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    exporter.export(
                        destination = destination,
                        messages = current.messages,
                        speed = current.speed,
                    ) { fraction ->
                        _state.update { it.copy(export = ExportState.Running(fraction)) }
                    }
                }
            }
            _state.update {
                it.copy(
                    export = result.fold(
                        onSuccess = { ExportState.Done(displayName, destination) },
                        onFailure = { error ->
                            ExportState.Failed(error.message ?: "the export could not finish")
                        },
                    ),
                )
            }
        }
    }

    fun dismissExport() = _state.update { it.copy(export = ExportState.Idle) }

    override fun onCleared() {
        tones.release()
    }

    companion object {
        fun factory(
            sceneName: String,
            messages: List<SceneMessageEntity>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                ScenePlayerViewModel(
                    sceneName = sceneName,
                    messages = messages,
                    exporter = SceneExporter(app, app.avatars::load),
                )
            }
        }
    }
}
