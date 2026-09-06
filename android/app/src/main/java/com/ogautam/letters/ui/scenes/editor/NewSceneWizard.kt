package com.ogautam.letters.ui.scenes.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ogautam.letters.LettersApplication
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.repository.CharacterRepository
import com.ogautam.letters.data.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewSceneUiState(
    val loading: Boolean = true,
    val storyTitle: String? = null,
    val everyone: List<CharacterEntity> = emptyList(),
    /** The people already in this story, offered first. */
    val suggestedIds: Set<String> = emptySet(),
    val selectedIds: List<String> = emptyList(),
    val title: String = "",
) {
    val canStart: Boolean get() = selectedIds.isNotEmpty()

    /** Whoever was picked first speaks as you, until the editor says otherwise. */
    val outgoingId: String? get() = selectedIds.firstOrNull()
}

/**
 * Choosing who a scene is between, and what to call it, before any of it is written. The
 * scene itself is not created here — nothing is saved until the editor saves it.
 */
class NewSceneViewModel(
    private val characters: CharacterRepository,
    private val stories: StoryRepository,
    private val storyId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(NewSceneUiState())
    val state: StateFlow<NewSceneUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val everyone = characters.observeAll().first()
            val suggested = characters.forStory(storyId).map(CharacterEntity::id).toSet()
            _state.value = NewSceneUiState(
                loading = false,
                storyTitle = storyId?.let { stories.getById(it)?.title },
                everyone = everyone,
                suggestedIds = suggested,
                // A story's existing cast is the likeliest answer, so it starts selected.
                selectedIds = everyone.map(CharacterEntity::id).filter { it in suggested },
            )
        }
    }

    fun toggle(characterId: String) = _state.update { current ->
        current.copy(
            selectedIds = if (characterId in current.selectedIds) {
                current.selectedIds - characterId
            } else {
                current.selectedIds + characterId
            },
        )
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }

    fun addCharacter(name: String) {
        viewModelScope.launch {
            val created = characters.create(
                name = name,
                isSelf = _state.value.everyone.isEmpty(),
            )
            _state.update {
                it.copy(
                    everyone = it.everyone + created,
                    selectedIds = it.selectedIds + created.id,
                )
            }
        }
    }

    companion object {
        fun factory(storyId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                NewSceneViewModel(app.characters, app.stories, storyId)
            }
        }
    }
}
