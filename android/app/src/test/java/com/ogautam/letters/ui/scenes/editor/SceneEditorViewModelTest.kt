package com.ogautam.letters.ui.scenes.editor

import androidx.test.core.app.ApplicationProvider
import com.ogautam.letters.data.DbTest
import com.ogautam.letters.data.avatars.AvatarStore
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.repository.CharacterRepository
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
import org.junit.Assert.assertNull
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

    private fun scenes() = SceneRepository(db.sceneDao(), clock)

    private fun library() = CharacterRepository(db.characterDao(), clock)

    private fun viewModel(
        scenes: SceneRepository,
        library: CharacterRepository,
        sceneId: String? = null,
        castIds: List<String> = emptyList(),
        storyId: String? = null,
    ) = SceneEditorViewModel(
        repo = scenes,
        characters = library,
        avatars = AvatarStore(ApplicationProvider.getApplicationContext()),
        sceneId = sceneId,
        storyId = storyId,
        initialCastIds = castIds,
        clock = clock,
    )

    private suspend fun SceneEditorViewModel.awaitLoaded() = state.first { !it.loading }

    private suspend fun SceneEditorViewModel.awaitSaved() =
        state.first { !it.saving && !it.isDirty }

    private suspend fun castOf(library: CharacterRepository): Pair<CharacterEntity, CharacterEntity> {
        val you = library.create("You", isSelf = true)
        val meera = library.create("Meera")
        return you to meera
    }

    /** A scene with one message from each of two people. */
    private suspend fun populated(
        scenes: SceneRepository,
        library: CharacterRepository,
    ): SceneEditorViewModel {
        val (you, meera) = castOf(library)
        val vm = viewModel(scenes, library, castIds = listOf(you.id, meera.id))
        vm.awaitLoaded()

        vm.selectCharacter(you.id)
        vm.onDraftChange("are you awake")
        vm.send()
        vm.selectCharacter(meera.id)
        vm.onDraftChange("unfortunately")
        vm.send()
        return vm
    }

    @Test
    fun `the cast is taken from the library the wizard chose`() = runTest {
        val library = library()
        val (you, meera) = castOf(library)

        val state = viewModel(scenes(), library, castIds = listOf(you.id, meera.id)).awaitLoaded()

        assertEquals(listOf("You", "Meera"), state.characters.map { it.name })
        assertEquals(you.id, state.outgoing?.id)
    }

    @Test
    fun `a message is outgoing only when it comes from whoever speaks as you`() = runTest {
        val vm = populated(scenes(), library())

        val (first, second) = vm.state.value.messages
        assertTrue(first.outgoing)
        assertFalse(second.outgoing)
    }

    @Test
    fun `the outgoing role can be handed to someone else`() = runTest {
        val library = library()
        val (you, meera) = castOf(library)
        val vm = viewModel(scenes(), library, castIds = listOf(you.id, meera.id))
        vm.awaitLoaded()

        vm.setOutgoing(meera.id)
        vm.selectCharacter(meera.id)
        vm.onDraftChange("my turn")
        vm.send()

        assertTrue(vm.state.value.messages.single().outgoing)
    }

    /** Removing someone from a scene leaves what they already said in it. */
    @Test
    fun `removing someone from the cast keeps their messages`() = runTest {
        val vm = populated(scenes(), library())
        val meera = vm.state.value.characters[1]

        vm.removeFromCast(meera.id)

        assertEquals(1, vm.state.value.characters.size)
        assertEquals(2, vm.state.value.messages.size)
        assertEquals("Meera", vm.state.value.messages[1].charName)
    }

    @Test
    fun `a library edit reaches the scene being written`() = runTest {
        val library = library()
        val vm = populated(scenes(), library)
        val meera = vm.state.value.characters[1]

        vm.refreshCharacter(meera.copy(name = "Meera R"))

        assertEquals("Meera R", vm.state.value.characters[1].name)
        assertEquals("Meera R", vm.state.value.messages[1].charName)
    }

    @Test
    fun `an unsent message is composed but never sent`() = runTest {
        val library = library()
        val (you, _) = castOf(library)
        val vm = viewModel(scenes(), library, castIds = listOf(you.id))
        vm.awaitLoaded()

        vm.setComposingUnsent(true)
        vm.onDraftChange("i miss you")
        vm.send()

        val message = vm.state.value.messages.single()
        assertTrue(message.unsent)
        assertEquals("i miss you", message.text)
    }

    @Test
    fun `customising a message sets only that message's playback`() = runTest {
        val vm = populated(scenes(), library())

        vm.customiseMessage(
            index = 1,
            revealPerCharMs = 40L,
            typingMs = 2_000L,
            delayBeforeMs = 800L,
            unsent = false,
        )

        val (first, second) = vm.state.value.messages
        assertNull(first.revealPerCharMs)
        assertEquals(40L, second.revealPerCharMs)
        assertEquals(2_000L, second.typingMs)
        assertEquals(800L, second.delayBeforeMs)
    }

    @Test
    fun `an empty draft sends nothing`() = runTest {
        val library = library()
        val (you, _) = castOf(library)
        val vm = viewModel(scenes(), library, castIds = listOf(you.id))
        vm.awaitLoaded()

        vm.onDraftChange("   ")
        vm.send()

        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `saving writes the scene, then reloads with everything intact`() = runTest {
        val scenes = scenes()
        val library = library()
        val vm = populated(scenes, library)
        vm.onNameChange("Tuesday night")

        vm.save()
        vm.awaitSaved()

        val summary = scenes.observeSummaries().first().single()
        assertEquals("Tuesday night", summary.name)
        assertEquals(2, summary.characterCount)
        assertEquals(2, summary.messageCount)

        val state = viewModel(scenes, library, sceneId = summary.id).awaitLoaded()
        assertEquals("Tuesday night", state.name)
        assertEquals(listOf("You", "Meera"), state.characters.map { it.name })
        assertEquals(listOf("are you awake", "unfortunately"), state.messages.map { it.text })
        assertFalse(state.isDirty)
    }

    @Test
    fun `a scene saved into a story stays in it`() = runTest {
        val scenes = scenes()
        val library = library()
        val story = com.ogautam.letters.data.repository.StoryRepository(db.storyDao(), clock)
            .create("The group chat")
        val (you, meera) = castOf(library)
        val vm = viewModel(scenes, library, castIds = listOf(you.id, meera.id), storyId = story.id)
        vm.awaitLoaded()
        vm.onDraftChange("hi")
        vm.send()

        vm.save()
        vm.awaitSaved()

        assertEquals(1, scenes.observeSummariesForStory(story.id).first().size)
        assertEquals(0, scenes.observeSummariesForStory(null).first().size)
    }

    @Test
    fun `saving twice updates the scene rather than making a second one`() = runTest {
        val scenes = scenes()
        val vm = populated(scenes, library())

        vm.save()
        vm.awaitSaved()
        vm.onDraftChange("go to sleep")
        vm.send()
        vm.save()
        vm.awaitSaved()

        val summaries = scenes.observeSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals(3, summaries.single().messageCount)
    }

    @Test
    fun `an id that no longer exists reports missing`() = runTest {
        assertTrue(viewModel(scenes(), library(), sceneId = "nope").awaitLoaded().missing)
    }

    @Test
    fun `a scene with messages opens at the composer, an empty one at setup`() = runTest {
        val scenes = scenes()
        val library = library()
        val saved = populated(scenes, library)
        saved.save()
        saved.awaitSaved()
        val id = scenes.observeSummaries().first().single().id

        assertEquals(EditorStep.COMPOSER, viewModel(scenes, library, sceneId = id).awaitLoaded().step)
        assertEquals(EditorStep.SETUP, viewModel(scenes, library).awaitLoaded().step)
    }
}
