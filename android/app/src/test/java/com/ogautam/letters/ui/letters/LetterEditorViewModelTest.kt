package com.ogautam.letters.ui.letters

import androidx.test.core.app.ApplicationProvider
import com.ogautam.letters.data.DbTest
import com.ogautam.letters.data.entity.LetterEntity
import com.ogautam.letters.data.entity.Mood
import com.ogautam.letters.data.prefs.UserPreferences
import com.ogautam.letters.data.repository.LetterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LetterEditorViewModelTest : DbTest() {

    /**
     * Unconfined rather than a virtual-time dispatcher: the pieces this view model waits on —
     * DataStore and Room — do their work on real executors, so advancing virtual time would
     * return before they had finished. The tests wait on the state itself instead.
     */
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun repo() = LetterRepository(db.letterDao(), clock)

    private fun prefs() = UserPreferences(ApplicationProvider.getApplicationContext())

    private fun viewModel(repo: LetterRepository, letterId: String? = null) =
        LetterEditorViewModel(repo, prefs(), letterId)

    private suspend fun LetterEditorViewModel.awaitLoaded(): LetterEditorUiState =
        state.first { !it.loading }

    private suspend fun LetterEditorViewModel.awaitSaved(): LetterEditorUiState =
        state.first { !it.saving && !it.isDirty }

    /** Today, per the fixed test clock: 2026-09-06. */
    private val today = LocalDate.of(2026, 9, 6)

    @Test
    fun `a new letter opens clean and dated today`() = runTest {
        val vm = viewModel(repo())

        val state = vm.awaitLoaded()
        assertTrue(state.isNew)
        assertFalse(state.isDirty)
        assertEquals(today, state.letterDate)
        assertEquals(UserPreferences.DEFAULT_PEN_NAME, state.penName)
    }

    @Test
    fun `typing makes it dirty and saving makes it clean again`() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.awaitLoaded()

        vm.onRecipientChange("Dad")
        assertTrue(vm.state.value.isDirty)

        vm.save()
        vm.awaitSaved()

        assertFalse(vm.state.value.isNew)
        val stored = repo.observeAll().first()
        assertEquals(1, stored.size)
        assertEquals("Dad", stored.single().recipient)
    }

    @Test
    fun `saving twice updates the same row rather than creating a second`() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.awaitLoaded()

        vm.onRecipientChange("Dad")
        vm.save()
        vm.awaitSaved()

        vm.onContentChange("I understand now.")
        vm.save()
        vm.awaitSaved()

        val stored = repo.observeAll().first()
        assertEquals(1, stored.size)
        assertEquals("I understand now.", repo.getById(stored.single().id)?.content)
    }

    @Test
    fun `a blank recipient is refused with a message and writes nothing`() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.awaitLoaded()

        vm.onContentChange("A letter to nobody.")
        vm.save()

        assertNotNull(vm.state.value.error)
        assertTrue(repo.observeAll().first().isEmpty())

        vm.dismissError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `whitespace alone does not leave the letter looking unsaved`() = runTest {
        val vm = viewModel(repo())
        vm.awaitLoaded()

        vm.onRecipientChange("Dad")
        vm.save()
        vm.awaitSaved()

        vm.onRecipientChange("Dad ")
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `tapping the chosen mood again clears it`() = runTest {
        val vm = viewModel(repo())
        vm.awaitLoaded()

        vm.onMoodToggle(Mood.GRATEFUL.slug)
        assertEquals(Mood.GRATEFUL.slug, vm.state.value.mood)

        vm.onMoodToggle(Mood.GRATEFUL.slug)
        assertNull(vm.state.value.mood)
    }

    @Test
    fun `the capsule proposes a year out and only seals once saved`() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.awaitLoaded()

        vm.onRecipientChange("Me at 40")
        vm.onSealToggle()

        assertEquals(today.plusYears(1), vm.state.value.sealedUntil)
        assertTrue(vm.state.value.sealed)

        vm.save()
        vm.awaitSaved()

        val stored = repo.observeAll().first().single()
        assertEquals(today.plusYears(1), stored.sealedUntil)
        assertTrue(stored.isSealed)
        // A sealed letter must not leak its body through the list preview.
        assertNull(stored.contentPreview)
    }

    @Test
    fun `switching the capsule back off unseals the stored letter`() = runTest {
        val repo = repo()
        val existing = repo.create(
            LetterEntity(
                recipient = "Me at 40",
                content = "Hope you got there.",
                sealedUntil = today.plusYears(1),
            ),
        )
        val vm = viewModel(repo, existing.id)
        vm.awaitLoaded()

        assertTrue(vm.state.value.sealToggle)
        assertTrue(vm.state.value.sealed)

        vm.onSealToggle()
        assertTrue(vm.state.value.isDirty)
        assertFalse(vm.state.value.sealed)

        vm.save()
        vm.awaitSaved()

        assertNull(repo.getById(existing.id)?.sealedUntil)
    }

    @Test
    fun `an id that no longer exists reports missing instead of opening blank`() = runTest {
        val vm = viewModel(repo(), "does-not-exist")

        assertTrue(vm.awaitLoaded().missing)
    }

    @Test
    fun `an existing letter loads its fields and stays clean`() = runTest {
        val repo = repo()
        val existing = repo.create(
            LetterEntity(
                recipient = "Dad",
                subject = "The thing I never said",
                content = "I understand now.",
                mood = Mood.GRATEFUL.slug,
                letterDate = LocalDate.of(2026, 5, 22),
            ),
        )
        val vm = viewModel(repo, existing.id)

        val state = vm.awaitLoaded()
        assertFalse(state.isNew)
        assertFalse(state.isDirty)
        assertEquals("Dad", state.recipient)
        assertEquals("The thing I never said", state.subject)
        assertEquals("I understand now.", state.content)
        assertEquals(Mood.GRATEFUL.slug, state.mood)
        assertEquals(LocalDate.of(2026, 5, 22), state.letterDate)
        assertFalse(state.sealToggle)
    }
}
