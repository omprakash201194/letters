package com.ogautam.letters.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ogautam.letters.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UserPreferencesTest {

    private fun prefs() = UserPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun `pen name defaults to You and survives a write`() = runTest {
        val prefs = prefs()
        assertEquals(UserPreferences.DEFAULT_PEN_NAME, prefs.penName.first())

        prefs.setPenName("  Om  ")
        assertEquals("Om", prefs.penName.first())
    }

    @Test
    fun `blank pen name falls back to the default`() = runTest {
        val prefs = prefs()
        prefs.setPenName("Om")
        prefs.setPenName("   ")
        assertEquals(UserPreferences.DEFAULT_PEN_NAME, prefs.penName.first())
    }
}
