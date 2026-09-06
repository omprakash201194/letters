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
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.entity.SceneCastEntity
import com.ogautam.letters.data.entity.SceneEntity
import com.ogautam.letters.data.entity.SceneMessageEntity
import com.ogautam.letters.data.repository.CharacterRepository
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
    val characters: List<CharacterEntity> = emptyList(),
    val outgoingCharId: String? = null,
    val messages: List<SceneMessageEntity> = emptyList(),
    val selectedCharId: String? = null,
    val draft: String = "",
    /** Composing a message that will be typed and taken back rather than sent. */
    val composingUnsent: Boolean = false,
    val saving: Boolean = false,
    val justSaved: Boolean = false,
    val isDirty: Boolean = false,
    val missing: Boolean = false,
) {
    val canCompose: Boolean get() = characters.isNotEmpty()
    val canPreview: Boolean get() = messages.isNotEmpty()

    val outgoing: CharacterEntity?
        get() = characters.firstOrNull { it.id == outgoingCharId } ?: characters.firstOrNull()
}

private data class Snapshot(
    val name: String,
    val characterIds: List<String>,
    val outgoingCharId: String?,
    val messages: List<SceneMessageEntity>,
)

class SceneEditorViewModel(
    private val repo: SceneRepository,
    private val characters: CharacterRepository,
    private val avatars: AvatarStore,
    private val sceneId: String?,
    private val storyId: String? = null,
    initialCastIds: List<String> = emptyList(),
    initialName: String? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _state = MutableStateFlow(SceneEditorUiState())
    val state: StateFlow<SceneEditorUiState> = _state.asStateFlow()

    private var snapshot = Snapshot("", emptyList(), null, emptyList())
    private var storedId: String? = sceneId

    init {
        viewModelScope.launch {
            val existing = sceneId?.let { repo.getWithContent(it) }
            if (sceneId != null && existing == null) {
                _state.value = SceneEditorUiState(loading = false, missing = true)
                return@launch
            }

            val cast = existing?.characters ?: characters.getAllById(initialCastIds)
                .sortedBy { initialCastIds.indexOf(it.id) }
            val outgoingId = existing?.outgoingCharacterId
                ?: cast.firstOrNull { it.isSelf }?.id
                ?: cast.firstOrNull()?.id
            val name = existing?.scene?.name ?: initialName?.trim()?.ifBlank { null } ?: DEFAULT_NAME
            val messages = existing?.messages.orEmpty()

            snapshot = Snapshot(name, cast.map(CharacterEntity::id), outgoingId, messages)
            _state.value = SceneEditorUiState(
                loading = false,
                // An existing scene opens where the work is, not back at the cast list.
                step = if (messages.isNotEmpty()) EditorStep.COMPOSER else EditorStep.SETUP,
                name = name,
                characters = cast,
                outgoingCharId = outgoingId,
                messages = messages,
                selectedCharId = outgoingId ?: cast.firstOrNull()?.id,
            )
        }
    }

    fun goTo(step: EditorStep) = _state.update { it.copy(step = step) }

    fun onNameChange(value: String) = edit { copy(name = value) }

    // ── cast ───────────────────────────────────────────────────────────────

    fun addToCast(character: CharacterEntity) = edit {
        if (characters.any { it.id == character.id }) this
        else copy(
            characters = characters + character,
            outgoingCharId = outgoingCharId ?: character.id,
            selectedCharId = selectedCharId ?: character.id,
        )
    }

    /**
     * Removing someone from the cast leaves what they already said. Their messages hold
     * their own snapshot, so the scene still plays — they simply cannot say anything more.
     */
    fun removeFromCast(characterId: String) = edit {
        val remaining = characters.filterNot { it.id == characterId }
        copy(
            characters = remaining,
            outgoingCharId = if (outgoingCharId == characterId) remaining.firstOrNull()?.id
            else outgoingCharId,
            selectedCharId = if (selectedCharId == characterId) remaining.firstOrNull()?.id
            else selectedCharId,
        )
    }

    /** Which member of the cast is speaking as "you" — a role, not a property of a person. */
    fun setOutgoing(characterId: String) = edit { copy(outgoingCharId = characterId) }

    /**
     * Reflects a library edit into the open scene, and into the messages composed in it so
     * far. Scenes already saved keep the snapshot they were written with.
     */
    fun refreshCharacter(updated: CharacterEntity) = edit {
        copy(
            characters = characters.map { if (it.id == updated.id) updated else it },
            messages = messages.map {
                if (it.charId != updated.id) it
                else it.copy(
                    charName = updated.name,
                    charColor = updated.color,
                    charAvatarPath = updated.avatarPath,
                )
            },
        )
    }

    /**
     * Avatars belong to the library character, so setting one changes them everywhere they
     * appear from now on. Scenes already saved keep the avatar they were written with.
     */
    fun setAvatar(charId: String, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { avatars.import(uri) } ?: return@launch
            val character = characters.getById(charId) ?: return@launch
            refreshCharacter(characters.update(character.copy(avatarPath = path)))
        }
    }

    // ── messages ───────────────────────────────────────────────────────────

    fun selectCharacter(charId: String) = _state.update { it.copy(selectedCharId = charId) }

    fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

    fun setComposingUnsent(unsent: Boolean) =
        _state.update { it.copy(composingUnsent = unsent) }

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
                    outgoing = character.id == (outgoingCharId ?: characters.firstOrNull()?.id),
                    orderIndex = messages.size,
                    unsent = composingUnsent,
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

    /** The per-message playback controls, applied from the customise sheet. */
    fun customiseMessage(
        index: Int,
        revealPerCharMs: Long?,
        typingMs: Long?,
        delayBeforeMs: Long?,
        unsent: Boolean,
    ) = edit {
        if (index !in messages.indices) this
        else copy(
            messages = messages.mapIndexed { i, message ->
                if (i != index) message
                else message.copy(
                    revealPerCharMs = revealPerCharMs,
                    typingMs = typingMs,
                    delayBeforeMs = delayBeforeMs,
                    unsent = unsent,
                )
            },
        )
    }

    fun save() {
        val current = _state.value
        if (current.saving) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val name = current.name.trim().ifBlank { DEFAULT_NAME }
            val id = storedId

            val scene = if (id == null) {
                SceneEntity(name = name, storyId = storyId)
            } else {
                repo.getWithContent(id)?.scene?.copy(name = name)
                    ?: SceneEntity(id = id, name = name, storyId = storyId)
            }

            val outgoingId = current.outgoingCharId ?: current.characters.firstOrNull()?.id
            val cast = current.characters.mapIndexed { index, character ->
                SceneCastEntity(
                    sceneId = scene.id,
                    characterId = character.id,
                    orderIndex = index,
                    outgoing = character.id == outgoingId,
                )
            }
            val messages = current.messages.map { it.copy(sceneId = scene.id) }

            if (id == null) {
                repo.create(scene, cast, messages)
            } else {
                repo.save(scene, cast, messages)
            }
            storedId = scene.id

            snapshot = Snapshot(
                name = name,
                characterIds = current.characters.map(CharacterEntity::id),
                outgoingCharId = outgoingId,
                messages = messages,
            )
            _state.update {
                it.copy(
                    name = name,
                    outgoingCharId = outgoingId,
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
            characters.map(CharacterEntity::id) != snapshot.characterIds ||
            outgoingCharId != snapshot.outgoingCharId ||
            messages != snapshot.messages

    companion object {
        const val DEFAULT_NAME = "Untitled scene"

        /**
         * Children are built before a new scene has an id, and are stamped with the real one
         * on save. Never written to the database.
         */
        private val PENDING_SCENE_ID = UUID(0, 0).toString()

        fun factory(
            sceneId: String?,
            storyId: String? = null,
            castIds: List<String> = emptyList(),
            name: String? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                SceneEditorViewModel(
                    repo = app.scenes,
                    characters = app.characters,
                    avatars = app.avatars,
                    sceneId = sceneId,
                    storyId = storyId,
                    initialCastIds = castIds,
                    initialName = name,
                )
            }
        }
    }
}
