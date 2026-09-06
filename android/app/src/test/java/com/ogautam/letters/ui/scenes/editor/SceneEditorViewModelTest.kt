package com.ogautam.letters.ui.scenes.editor

import androidx.test.core.app.ApplicationProvider
import com.ogautam.letters.data.DbTest
import com.ogautam.letters.data.avatars.AvatarStore
import com.ogautam.letters.data.entity.CharacterPalette
import com.ogautam.letters.data.repository.SceneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SceneEditorViewModelTest : DbTest() {

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun repo() = SceneRepository(db.sceneDao(), clock)

    private fun viewModel(repo: SceneRepository, sceneId: String? = null) =
        SceneEditorViewModel(
            repo,
            AvatarStore(ApplicationProvider.getApplicationContext()),
            sceneId,
            clock,
        )

    private suspend fun SceneEditorViewModel.awaitLoaded() = state.first { !it.loading }

    /**
     * A save writes through Room's own executor, so it is not finished when [save] returns.
     * Waiting on the state is the same thing the screen does.
     */
    private suspend fun SceneEditorViewModel.awaitSaved() =
        state.first { !it.saving && !it.isDirty }

    /** A two-character scene with one message from each. */
    private suspend fun populated(repo: SceneRepository): SceneEditorViewModel {
        val vm = viewModel(repo)
        vm.awaitLoaded()
        vm.addCharacter("You")
        vm.addCharacter("Meera")
        val you = vm.state.value.characters[0]
        val meera = vm.state.value.characters[1]

        vm.selectCharacter(you.id)
        vm.onDraftChange("are you awake")
        vm.send()
        vm.selectCharacter(meera.id)
        vm.onDraftChange("unfortunately")
        vm.send()
        return vm
    }

    @Test
    fun `the first character added is the outgoing one`() = runTest {
        val vm = viewModel(repo())
        vm.awaitLoaded()

        vm.addCharacter("You")
        vm.addCharacter("Meera")

        val state = vm.state.value
        assertEquals(state.characters.first().id, state.outgoingCharId)
    }

    @Test
    fun `characters take their colour from their position`() = runTest {
        val vm = viewModel(repo())
        vm.awaitLoaded()

        listOf("You", "Meera", "Arjun").forEach(vm::addCharacter)

        vm.state.value.characters.forEachIndexed { index, character ->
            assertEquals(CharacterPalette.colorForIndex(index), character.color)
        }
    }

    @Test
    fun `a message is outgoing only when it comes from the first character`() = runTest {
        val vm = populated(repo())

        val (first, second) = vm.state.value.messages
        assertTrue(first.outgoing)
        assertFalse(second.outgoing)
    }

    @Test
    fun `deleting a character deletes everything they said`() = runTest {
        val vm = populated(repo())
        val meera = vm.state.value.characters[1]

        vm.removeCharacter(meera.id)

        assertEquals(1, vm.state.value.characters.size)
        assertEquals(1, vm.state.value.messages.size)
        assertTrue(vm.state.value.messages.none { it.charId == meera.id })
    }

    /**
     * Colour is assigned by position, so removing someone shifts everyone after them — and
     * the colour is snapshotted onto their messages, which must shift with them.
     */
    @Test
    fun `removing a character recolours the ones after it, messages included`() = runTest {
        val vm = viewModel(repo())
        vm.awaitLoaded()
        listOf("You", "Meera", "Arjun").forEach(vm::addCharacter)
        val arjun = vm.state.value.characters[2]
        vm.selectCharacter(arjun.id)
        vm.onDraftChange("thank you")
        vm.send()

        vm.removeCharacter(vm.state.value.characters[1].id)

        val moved = vm.state.value.characters.single { it.id == arjun.id }
        assertEquals(1, moved.orderIndex)
        assertEquals(CharacterPalette.colorForIndex(1), moved.color)
        assertEquals(moved.color, vm.state.value.messages.single().charColor)
    }

    /** The sender's name is snapshotted onto each message, so a rename has to reach them. */
    @Test
    fun `renaming a character renames what they already said`() = runTest {
        val vm = populated(repo())
        val meera = vm.state.value.characters[1]

        vm.renameCharacter(meera.id, "Meera R")

        assertEquals("Meera R", vm.state.value.characters[1].name)
        assertEquals("Meera R", vm.state.value.messages.single { it.charId == meera.id }.charName)
    }

    @Test
    fun `deleting a message leaves the rest in order`() = runTest {
        val vm = populated(repo())

        vm.deleteMessage(0)

        assertEquals(1, vm.state.value.messages.size)
        assertEquals("unfortunately", vm.state.value.messages.single().text)
    }

    @Test
    fun `an empty draft sends nothing`() = runTest {
        val vm = viewModel(repo())
        vm.awaitLoaded()
        vm.addCharacter("You")

        vm.onDraftChange("   ")
        vm.send()

        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `saving writes the scene, then reloads with everything intact`() = runTest {
        val repo = repo()
        val vm = populated(repo)
        vm.onNameChange("Tuesday night")

        vm.save()
        vm.awaitSaved()

        val summaries = repo.observeSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals("Tuesday night", summaries.single().name)
        assertEquals(2, summaries.single().characterCount)
        assertEquals(2, summaries.single().messageCount)

        val reopened = viewModel(repo, summaries.single().id)
        val state = reopened.awaitLoaded()
        assertEquals("Tuesday night", state.name)
        assertEquals(2, state.characters.size)
        assertEquals(listOf("are you awake", "unfortunately"), state.messages.map { it.text })
        assertFalse(state.isDirty)
    }

    @Test
    fun `saving twice updates the scene rather than making a second one`() = runTest {
        val repo = repo()
        val vm = populated(repo)

        vm.save()
        vm.awaitSaved()
        vm.onDraftChange("go to sleep")
        vm.send()
        vm.save()
        vm.awaitSaved()

        val summaries = repo.observeSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals(3, summaries.single().messageCount)
    }

    @Test
    fun `an id that no longer exists reports missing`() = runTest {
        val vm = viewModel(repo(), "does-not-exist")

        assertTrue(vm.awaitLoaded().missing)
    }

    @Test
    fun `a scene with messages opens at the composer, an empty one at setup`() = runTest {
        val repo = repo()
        val saved = populated(repo)
        saved.save()
        saved.awaitSaved()
        val id = repo.observeSummaries().first().single().id

        assertEquals(EditorStep.COMPOSER, viewModel(repo, id).awaitLoaded().step)
        assertEquals(EditorStep.SETUP, viewModel(repo).awaitLoaded().step)
    }

    @Test
    fun `editing marks the scene dirty and saving clears it`() = runTest {
        val vm = viewModel(repo())
        vm.awaitLoaded()
        assertFalse(vm.state.value.isDirty)

        vm.addCharacter("You")
        assertTrue(vm.state.value.isDirty)

        vm.save()
        vm.awaitSaved()
        assertFalse(vm.state.value.isDirty)
    }
}
