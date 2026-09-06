package com.ogautam.letters.ui.scenes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ogautam.letters.LettersApplication
import com.ogautam.letters.data.dao.SceneSummary
import com.ogautam.letters.data.repository.SceneRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SceneListUiState(
    val loading: Boolean = true,
    val scenes: List<SceneSummary> = emptyList(),
)

class SceneListViewModel(
    private val repo: SceneRepository,
    storyId: String?,
) : ViewModel() {

    val state: StateFlow<SceneListUiState> = (
        if (storyId == null) repo.observeSummaries() else repo.observeSummariesForStory(storyId)
        )
        .map { SceneListUiState(loading = false, scenes = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SceneListUiState())

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.rename(id, trimmed) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    companion object {
        fun factory(storyId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SceneListViewModel((this[APPLICATION_KEY] as LettersApplication).scenes, storyId)
            }
        }
    }
}
