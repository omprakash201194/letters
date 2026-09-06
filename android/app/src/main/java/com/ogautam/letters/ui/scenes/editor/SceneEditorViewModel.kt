package com.ogautam.letters.ui.scenes.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ogautam.letters.LettersApplication
import com.ogautam.letters.data.avatars.AvatarStore
import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.entity.SceneCharacterEntity
import com.ogautam.letters.data.entity.SceneEntity
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.data.repository.SceneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalTime
import java.util.UUID

/** The editor's three steps, in the order the web app walked them. */
enum class EditorStep { SETUP, COMPOSER, PREVIEW }

data class SceneEditorUiState(
    val loading: Boolean = true,
    val step: EditorStep = EditorStep.SETUP,
    val name: String = "",
    val characters: List<SceneCharacterEntity> = emptyList(),
    val messages: List<SceneMessageEntity> = emptyList(),
    val selectedCharId: String? = null,
    val draft: String = "",
    val saving: Boolean = false,
    val justSaved: Boolean = false,
    val isDirty: Boolean = false,
    val missing: Boolean = false,
) {
    val canCompose: Boolean get() = characters.isNotEmpty()
    val canPreview: Boolean get() = messages.isNotEmpty()

    /** The first character is the outgoing one. This defines the scene model. */
    val outgoingCharId: String? get() = characters.firstOrNull()?.id
}

private data class Snapshot(
    val name: String,
    val characters: List<SceneCharacterEntity>,
    val messages: List<SceneMessageEntity>,
)

class SceneEditorViewModel(
    private val repo: SceneRepository,
    private val avatars: AvatarStore,
    private val sceneId: String?,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _state = MutableStateFlow(SceneEditorUiState())
    val state: StateFlow<SceneEditorUiState> = _state.asStateFlow()

    private var snapshot = Snapshot("", emptyList(), emptyList())
    private var storedId: String? = sceneId

    init {
        viewModelScope.launch {
            val existing = sceneId?.let { repo.getWithContent(it) }
            if (sceneId != null && existing == null) {
                _state.value = SceneEditorUiState(loading = false, missing = true)
                return@launch
            }

            val name = existing?.scene?.name ?: DEFAULT_NAME
            val characters = existing?.characters.orEmpty()
            val messages = existing?.messages.orEmpty()
            snapshot = Snapshot(name, characters, messages)
            _state.value = SceneEditorUiState(
                loading = false,
                // An existing scene opens where the work is, not back at the character list.
                step = if (messages.isNotEmpty()) EditorStep.COMPOSER else EditorStep.SETUP,
                name = name,
                characters = characters,
                messages = messages,
                selectedCharId = characters.firstOrNull()?.id,
            )
        }
    }

    fun goTo(step: EditorStep) = _state.update { it.copy(step = step) }

    fun onNameChange(value: String) = edit { copy(name = value) }

    // ── characters ─────────────────────────────────────────────────────────

    fun addCharacter(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        edit {
            val character = SceneCharacterEntity(
                sceneId = storedId ?: PENDING_SCENE_ID,
                name = trimmed,
                color = CharacterPalette.colorForIndex(characters.size),
                orderIndex = characters.size,
            )
            copy(
                characters = characters + character,
                selectedCharId = selectedCharId ?: character.id,
            )
        }
    }

    fun renameCharacter(charId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        edit {
            copy(
                characters = characters.map {
                    if (it.id == charId) it.copy(name = trimmed) else it
                },
                // The sender's name is snapshotted onto each message, so a rename has to
                // reach the messages too or playback keeps showing the old one.
                messages = messages.map {
                    if (it.charId == charId) it.copy(charName = trimmed) else it
                },
            )
        }
    }

    /** Deleting a character deletes everything they said. */
    fun removeCharacter(charId: String) {
        edit {
            val remaining = characters
                .filterNot { it.id == charId }
                // reason: colour is assigned by position, so closing a gap has to recolour
                // everyone after it — including the messages they already sent
                .mapIndexed { index, character ->
                    character.copy(orderIndex = index, color = CharacterPalette.colorForIndex(index))
                }
            val colorById = remaining.associate { it.id to it.color }
            copy(
                characters = remaining,
                messages = messages
                    .filterNot { it.charId == charId }
                    .map { message ->
                        colorById[message.charId]
                            ?.let { message.copy(charColor = it) }
                            ?: message
                    },
                selectedCharId = if (selectedCharId == charId) remaining.firstOrNull()?.id
                else selectedCharId,
            )
        }
    }

    fun setAvatar(charId: String, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { avatars.import(uri) } ?: return@launch
            edit {
                copy(
                    characters = characters.map {
                        if (it.id == charId) it.copy(avatarPath = path) else it
                    },
                    messages = messages.map {
                        if (it.charId == charId) it.copy(charAvatarPath = path) else it
                    },
                )
            }
        }
    }

    // ── messages ───────────────────────────────────────────────────────────

    fun selectCharacter(charId: String) = _state.update { it.copy(selectedCharId = charId) }

    fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

    fun send() {
        val current = _state.value
        val text = current.draft.trim()
        val character = current.characters.firstOrNull { it.id == current.selectedCharId }
        if (text.isEmpty() || character == null) return

        edit {
            copy(
                messages = messages + SceneMessageEntity(
                    sceneId = storedId ?: PENDING_SCENE_ID,
                    charId = character.id,
                    charName = character.name,
                    charColor = character.color,
                    charAvatarPath = character.avatarPath,
                    text = text,
                    time = LocalTime.now(clock).withSecond(0).withNano(0),
                    outgoing = character.id == outgoingCharId,
                    orderIndex = messages.size,
                ),
                draft = "",
            )
        }
    }

    fun deleteMessage(index: Int) {
        edit {
            if (index !in messages.indices) this
            else copy(messages = messages.filterIndexed { i, _ -> i != index })
        }
    }

    // ── saving ─────────────────────────────────────────────────────────────

    fun save() {
        val current = _state.value
        if (current.saving) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val name = current.name.trim().ifBlank { DEFAULT_NAME }
            val id = storedId

            val scene = if (id == null) {
                SceneEntity(name = name)
            } else {
                repo.getWithContent(id)?.scene?.copy(name = name) ?: SceneEntity(id = id, name = name)
            }

            // Children carry the scene's id; a scene created just now did not have one when
            // its characters and messages were built.
            val characters = current.characters.map { it.copy(sceneId = scene.id) }
            val messages = current.messages.map { it.copy(sceneId = scene.id) }

            if (id == null) {
                repo.create(scene, characters, messages)
            } else {
                repo.save(scene, characters, messages)
            }
            storedId = scene.id

            snapshot = Snapshot(name, characters, messages)
            _state.update {
                it.copy(
                    name = name,
                    characters = characters,
                    messages = messages,
                    saving = false,
                    justSaved = true,
                    isDirty = false,
                )
            }
        }
    }

    fun acknowledgeSaved() = _state.update { it.copy(justSaved = false) }

    private inline fun edit(block: SceneEditorUiState.() -> SceneEditorUiState) {
        _state.update { current ->
            val next = current.block()
            next.copy(isDirty = next.differsFromSnapshot(), justSaved = false)
        }
    }

    private fun SceneEditorUiState.differsFromSnapshot(): Boolean =
        name.trim() != snapshot.name ||
            characters != snapshot.characters ||
            messages != snapshot.messages

    companion object {
        const val DEFAULT_NAME = "Untitled scene"

        /**
         * Children are built before a new scene has an id, and are stamped with the real one
         * on save. Never written to the database.
         */
        private val PENDING_SCENE_ID = UUID(0, 0).toString()

        fun factory(sceneId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                SceneEditorViewModel(app.scenes, app.avatars, sceneId)
            }
        }
    }
}
