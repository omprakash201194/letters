package com.ogautam.letters.ui.letters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ogautam.letters.LettersApplication
import com.ogautam.letters.data.entity.LetterEntity
import com.ogautam.letters.data.prefs.UserPreferences
import com.ogautam.letters.data.repository.LetterRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LetterEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val recipient: String = "",
    val subject: String = "",
    val content: String = "",
    val mood: String? = null,
    val letterDate: LocalDate = LocalDate.EPOCH,
    val sealedUntil: LocalDate? = null,
    val sealToggle: Boolean = false,
    val today: LocalDate = LocalDate.EPOCH,
    val penName: String = UserPreferences.DEFAULT_PEN_NAME,
    val saving: Boolean = false,
    val justSaved: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null,
    /** The row is gone (deleted from another screen) — the editor should pop. */
    val missing: Boolean = false,
) {
    /** Effective seal: the toggle is what the user means, the date is only its value. */
    val effectiveSealedUntil: LocalDate? get() = if (sealToggle) sealedUntil else null

    val sealed: Boolean get() = effectiveSealedUntil?.isAfter(today) == true
}

/** The subset that decides whether there is anything to save. */
private data class Snapshot(
    val recipient: String,
    val subject: String,
    val content: String,
    val mood: String?,
    val letterDate: LocalDate,
    val sealedUntil: LocalDate?,
)

class LetterEditorViewModel(
    private val repo: LetterRepository,
    private val prefs: UserPreferences,
    private val letterId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(LetterEditorUiState())
    val state: StateFlow<LetterEditorUiState> = _state.asStateFlow()

    /** What was last written to the database — the baseline every dirty check runs against. */
    private var snapshot = Snapshot("", "", "", null, LocalDate.EPOCH, null)

    /** Null until a new letter has been saved once; after that, updates target this row. */
    private var storedId: String? = letterId

    init {
        viewModelScope.launch {
            val today = repo.today()
            val penName = prefs.penName.first()
            val existing = letterId?.let { repo.getById(it) }

            if (letterId != null && existing == null) {
                _state.value = LetterEditorUiState(loading = false, missing = true)
                return@launch
            }

            val date = existing?.letterDate ?: today
            snapshot = Snapshot(
                recipient = existing?.recipient.orEmpty(),
                subject = existing?.subject.orEmpty(),
                content = existing?.content.orEmpty(),
                mood = existing?.mood,
                letterDate = date,
                sealedUntil = existing?.sealedUntil,
            )
            _state.value = LetterEditorUiState(
                loading = false,
                isNew = letterId == null,
                recipient = snapshot.recipient,
                subject = snapshot.subject,
                content = snapshot.content,
                mood = snapshot.mood,
                letterDate = date,
                sealedUntil = snapshot.sealedUntil,
                sealToggle = snapshot.sealedUntil != null,
                today = today,
                penName = penName,
                isDirty = false,
            )
        }
    }

    fun onRecipientChange(value: String) = edit { copy(recipient = value) }

    fun onSubjectChange(value: String) = edit { copy(subject = value) }

    fun onContentChange(value: String) = edit { copy(content = value) }

    /** Tapping the selected mood again clears it, as on the web. */
    fun onMoodToggle(slug: String) = edit { copy(mood = if (mood == slug) null else slug) }

    fun onDateChange(date: LocalDate) = edit { copy(letterDate = date) }

    fun onSealDateChange(date: LocalDate) = edit { copy(sealedUntil = date) }

    /** Turning the capsule on for the first time proposes a year from today. */
    fun onSealToggle() = edit {
        val nextOn = !sealToggle
        copy(
            sealToggle = nextOn,
            sealedUntil = sealedUntil ?: if (nextOn) today.plusYears(1) else null,
        )
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        if (current.recipient.isBlank()) {
            _state.update { it.copy(error = "Who is this letter to?") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val recipient = current.recipient.trim()
            val subject = current.subject.trim()
            val sealedUntil = current.effectiveSealedUntil

            val id = storedId
            if (id == null) {
                val created = repo.create(
                    LetterEntity(
                        recipient = recipient,
                        subject = subject.ifBlank { null },
                        content = current.content.ifBlank { null },
                        mood = current.mood,
                        letterDate = current.letterDate,
                        sealedUntil = sealedUntil,
                    ),
                )
                storedId = created.id
            } else {
                val existing = repo.getById(id)
                if (existing == null) {
                    _state.update { it.copy(saving = false, missing = true) }
                    return@launch
                }
                repo.update(
                    existing.copy(
                        recipient = recipient,
                        subject = subject.ifBlank { null },
                        content = current.content.ifBlank { null },
                        mood = current.mood,
                        letterDate = current.letterDate,
                        sealedUntil = sealedUntil,
                    ),
                )
            }

            snapshot = Snapshot(
                recipient = recipient,
                subject = subject,
                content = current.content,
                mood = current.mood,
                letterDate = current.letterDate,
                sealedUntil = sealedUntil,
            )
            // reason: the fields adopt the trimmed values that were actually stored, so an
            // untouched editor never reads back as dirty over whitespace alone
            _state.update {
                it.copy(
                    recipient = recipient,
                    subject = subject,
                    sealedUntil = sealedUntil,
                    sealToggle = sealedUntil != null,
                    isNew = false,
                    saving = false,
                    justSaved = true,
                    isDirty = false,
                )
            }
            delay(SAVED_BADGE_MILLIS)
            _state.update { it.copy(justSaved = false) }
        }
    }

    private inline fun edit(block: LetterEditorUiState.() -> LetterEditorUiState) {
        _state.update { current ->
            val next = current.block()
            next.copy(isDirty = next.differsFromSnapshot(), justSaved = false)
        }
    }

    private fun LetterEditorUiState.differsFromSnapshot(): Boolean =
        recipient.trim() != snapshot.recipient ||
            subject.trim() != snapshot.subject ||
            content != snapshot.content ||
            mood != snapshot.mood ||
            letterDate != snapshot.letterDate ||
            effectiveSealedUntil != snapshot.sealedUntil

    companion object {
        const val SAVED_BADGE_MILLIS = 2_000L

        fun factory(letterId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LettersApplication
                LetterEditorViewModel(app.letters, app.prefs, letterId)
            }
        }
    }
}
