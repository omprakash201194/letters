package com.ogautam.letters.ui.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ogautam.letters.LettersApplication
import com.ogautam.letters.data.dao.SceneSummary
import com.ogautam.letters.data.dao.StorySummary
import com.ogautam.letters.data.repository.SceneRepository
import com.ogautam.letters.data.repository.StoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StoryListUiState(
    val loading: Boolean = true,
    val stories: List<StorySummary> = emptyList(),
    /** Scenes that belong to no story, shown under the folders. */
    val looseScenes: List<SceneSummary> = emptyList(),
)

class StoryListViewModel(
    private val stories: StoryRepository,
    scenes: SceneRepository,
) : ViewModel() {

    val state: StateFlow<StoryListUiState> = combine(
        stories.observeSummaries(),
        scenes.observeSummariesForStory(null),
    ) { storyList, loose ->
        StoryListUiState(loading = false, stories = storyList, looseScenes = loose)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUiState())

    fun create(title: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch { onCreated(stories.create(title).id) }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch { stories.rename(id, title) }
    }

    /** Deleting a story keeps its scenes; they move back out into the loose list. */
    fun delete(id: String) {
        viewModelScope.launch { stories.delete(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                StoryListViewModel(app.stories, app.scenes)
            }
        }
    }
}
