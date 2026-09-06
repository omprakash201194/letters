package com.ogautam.letters.ui.scenes.editor

import com.ogautam.letters.data.DbTest
import com.ogautam.letters.data.entity.CharacterEntity
import com.ogautam.letters.data.repository.CharacterRepository
import com.ogautam.letters.data.repository.SceneRepository
import com.ogautam.letters.data.repository.StoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewSceneViewModelTest : DbTest() {

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun library() = CharacterRepository(db.characterDao(), clock)

    private fun stories() = StoryRepository(db.storyDao(), clock)

    private fun scenes() = SceneRepository(db.sceneDao(), clock)

    private fun viewModel(library: CharacterRepository, storyId: String? = null) =
        NewSceneViewModel(library, stories(), storyId)

    private suspend fun NewSceneViewModel.awaitLoaded() = state.first { !it.loading }

    /** Adding runs through the database, so it is not finished when the call returns. */
    private suspend fun NewSceneViewModel.awaitCast(size: Int) =
        state.first { it.everyone.size == size }

    private suspend fun NewSceneViewModel.awaitNotice() = state.first { it.notice != null }

    @Test
    fun `a new name creates someone`() = runTest {
        val library = library()
        val vm = viewModel(library)
        vm.awaitLoaded()

        vm.addCharacter("Meera")
        vm.awaitCast(1)

        assertEquals(listOf("Meera"), vm.state.value.everyone.map(CharacterEntity::name))
        assertEquals(1, vm.state.value.selectedIds.size)
        assertEquals(1, library.observeAll().first().size)
    }

    /**
     * Typing a name here is a way of reaching someone you already have, not of inventing a
     * second of them. Two people genuinely sharing a name is said on the library screen.
     */
    @Test
    fun `an existing name reaches that person rather than making a second`() = runTest {
        val library = library()
        val meera = library.create("Meera")
        val vm = viewModel(library)
        vm.awaitLoaded()
        assertTrue("nothing is selected without a story", vm.state.value.selectedIds.isEmpty())

        vm.addCharacter("Meera")
        vm.awaitNotice()

        assertEquals(1, library.observeAll().first().size)
        assertEquals(listOf(meera.id), vm.state.value.selectedIds)
        assertNotNull(vm.state.value.notice)
    }

    @Test
    fun `matching ignores case and surrounding space`() = runTest {
        val library = library()
        library.create("Meera")
        val vm = viewModel(library)
        vm.awaitLoaded()

        vm.addCharacter("  meera  ")
        vm.awaitNotice()

        assertEquals(1, library.observeAll().first().size)
        assertEquals(listOf("Meera"), vm.state.value.everyone.map(CharacterEntity::name))
    }

    @Test
    fun `adding someone already in the scene says so and changes nothing`() = runTest {
        val library = library()
        val meera = library.create("Meera")
        val vm = viewModel(library)
        vm.awaitLoaded()
        vm.toggle(meera.id)   // now in the scene

        vm.addCharacter("Meera")
        vm.awaitNotice()

        assertEquals(listOf(meera.id), vm.state.value.selectedIds)
        assertTrue(vm.state.value.notice!!.contains("already in this scene"))
        assertEquals(1, library.observeAll().first().size)
    }

    @Test
    fun `the notice clears once the cast is touched again`() = runTest {
        val library = library()
        val meera = library.create("Meera")
        val vm = viewModel(library)
        vm.awaitLoaded()
        vm.addCharacter("Meera")
        vm.awaitNotice()

        vm.toggle(meera.id)

        assertNull(vm.state.value.notice)
    }

    @Test
    fun `the first person added is marked as you`() = runTest {
        val library = library()
        val vm = viewModel(library)
        vm.awaitLoaded()

        vm.addCharacter("Om")
        vm.awaitCast(1)
        vm.addCharacter("Meera")
        vm.awaitCast(2)

        assertEquals("Om", library.getSelf()!!.name)
        assertEquals(vm.state.value.everyone.first().id, vm.state.value.outgoingId)
    }

    @Test
    fun `a story's existing cast starts selected`() = runTest {
        val library = library()
        val scenes = scenes()
        val story = stories().create("Tuesday nights")
        val you = library.create("You", isSelf = true)
        val meera = library.create("Meera")
        val other = library.create("Someone else")
        scenes.create(
            scene = com.ogautam.letters.data.entity.SceneEntity(
                name = "First",
                storyId = story.id,
            ),
            cast = listOf(
                com.ogautam.letters.data.entity.SceneCastEntity("", you.id, 0, true),
                com.ogautam.letters.data.entity.SceneCastEntity("", meera.id, 1, false),
            ),
            messages = emptyList(),
        )

        val state = viewModel(library, storyId = story.id).awaitLoaded()

        assertEquals(setOf(you.id, meera.id), state.selectedIds.toSet())
        assertTrue(other.id !in state.selectedIds)
        assertEquals("Tuesday nights", state.storyTitle)
    }
}
