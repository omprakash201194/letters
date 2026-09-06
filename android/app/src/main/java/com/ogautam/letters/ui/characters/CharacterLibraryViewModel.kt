package com.ogautam.letters.ui.characters

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
import com.ogautam.letters.data.repository.CharacterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CharacterLibraryUiState(
    val loading: Boolean = true,
    val characters: List<CharacterEntity> = emptyList(),
)

/** How many scenes a character is in, asked before offering to delete them. */
data class DeletePrompt(val character: CharacterEntity, val sceneCount: Int)

class CharacterLibraryViewModel(
    private val repo: CharacterRepository,
    private val avatars: AvatarStore,
) : ViewModel() {

    val state: StateFlow<CharacterLibraryUiState> = repo.observeAll()
        .map { CharacterLibraryUiState(loading = false, characters = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CharacterLibraryUiState())

    private val _deletePrompt = MutableStateFlow<DeletePrompt?>(null)
    val deletePrompt: StateFlow<DeletePrompt?> = _deletePrompt.asStateFlow()

    fun create(name: String, isSelf: Boolean = false) {
        viewModelScope.launch { repo.create(name = name, isSelf = isSelf) }
    }

    fun rename(character: CharacterEntity, name: String) {
        viewModelScope.launch { repo.update(character.copy(name = name.trim())) }
    }

    fun setColor(character: CharacterEntity, color: Int) {
        viewModelScope.launch { repo.update(character.copy(color = color)) }
    }

    fun setSelf(character: CharacterEntity) {
        viewModelScope.launch { repo.update(character.copy(isSelf = true)) }
    }

    fun setAvatar(character: CharacterEntity, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { avatars.import(uri) } ?: return@launch
            repo.update(character.copy(avatarPath = path))
        }
    }

    /** Asks first, because a character can be in scenes the list does not show. */
    fun askToDelete(character: CharacterEntity) {
        viewModelScope.launch {
            _deletePrompt.value = DeletePrompt(character, repo.sceneCountFor(character.id))
        }
    }

    fun dismissDelete() = _deletePrompt.update { null }

    fun confirmDelete() {
        val prompt = _deletePrompt.value ?: return
        viewModelScope.launch {
            repo.delete(prompt.character.id)
            _deletePrompt.value = null
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                CharacterLibraryViewModel(app.characters, app.avatars)
            }
        }
    }
}
