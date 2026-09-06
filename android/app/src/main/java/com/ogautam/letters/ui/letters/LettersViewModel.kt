package com.ogautam.letters.ui.letters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ogautam.letters.LettersApplication
import com.ogautam.letters.data.prefs.UserPreferences
import com.ogautam.letters.data.repository.LetterRepository
import com.ogautam.letters.data.repository.LetterSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LettersUiState(
    val loading: Boolean = true,
    val letters: List<LetterSummary> = emptyList(),
    val penName: String = UserPreferences.DEFAULT_PEN_NAME,
)

class LettersViewModel(
    private val repo: LetterRepository,
    prefs: UserPreferences,
) : ViewModel() {

    val state: StateFlow<LettersUiState> =
        combine(repo.observeAll(), prefs.penName) { letters, penName ->
            LettersUiState(loading = false, letters = letters, penName = penName)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LettersUiState())

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                LettersViewModel(app.letters, app.prefs)
            }
        }
    }
}
