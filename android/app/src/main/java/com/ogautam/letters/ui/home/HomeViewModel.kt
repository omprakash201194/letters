package com.ogautam.letters.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ogautam.letters.LettersApplication
import com.ogautam.letters.data.dao.SceneSummary
import com.ogautam.letters.data.prefs.UserPreferences
import com.ogautam.letters.data.repository.LetterRepository
import com.ogautam.letters.data.repository.LetterSummary
import com.ogautam.letters.data.repository.SceneRepository
import com.ogautam.letters.ui.common.DailyPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class HomeUiState(
    val loading: Boolean = true,
    val query: String = "",
    val prompt: String = "",
    val penName: String = UserPreferences.DEFAULT_PEN_NAME,
    val letterCount: Int = 0,
    val sealedCount: Int = 0,
    val sceneCount: Int = 0,
    val letterResults: List<LetterSummary> = emptyList(),
    val sceneResults: List<SceneSummary> = emptyList(),
) {
    val isSearching: Boolean get() = query.isNotBlank()
    val hasResults: Boolean get() = letterResults.isNotEmpty() || sceneResults.isNotEmpty()
}

class HomeViewModel(
    letters: LetterRepository,
    scenes: SceneRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /**
     * Search filters the lists already in memory rather than going back to the DAO, which
     * is what the web app did and what the counts on this screen need anyway — one
     * subscription feeds both the tiles and the results.
     */
    val state: StateFlow<HomeUiState> = combine(
        letters.observeAll(),
        scenes.observeSummaries(),
        prefs.penName,
        query,
    ) { letterList, sceneList, penName, rawQuery ->
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        HomeUiState(
            loading = false,
            query = rawQuery,
            prompt = DailyPrompt.today(),
            penName = penName,
            letterCount = letterList.size,
            sealedCount = letterList.count(LetterSummary::isSealed),
            sceneCount = sceneList.size,
            letterResults = if (q.isEmpty()) emptyList() else letterList.filter {
                it.recipient.lowercase(Locale.ROOT).contains(q) ||
                    it.subject.orEmpty().lowercase(Locale.ROOT).contains(q)
            },
            sceneResults = if (q.isEmpty()) emptyList() else sceneList.filter {
                it.name.lowercase(Locale.ROOT).contains(q)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setPenName(name: String) {
        viewModelScope.launch { prefs.setPenName(name) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                HomeViewModel(app.letters, app.scenes, app.prefs)
            }
        }
    }
}
