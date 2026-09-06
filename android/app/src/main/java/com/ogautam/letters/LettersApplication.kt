package com.ogautam.letters

import android.app.Application
import com.ogautam.letters.data.LettersDatabase
import com.ogautam.letters.data.avatars.AvatarStore
import com.ogautam.letters.data.prefs.UserPreferences
import com.ogautam.letters.data.repository.LetterRepository
import com.ogautam.letters.data.repository.SceneRepository

class LettersApplication : Application() {

    /**
     * A plain service locator, not a DI framework. Four dependencies do not justify
     * Hilt; revisit if the graph grows past a handful.
     */
    lateinit var letters: LetterRepository
        private set

    lateinit var scenes: SceneRepository
        private set

    lateinit var prefs: UserPreferences
        private set

    lateinit var avatars: AvatarStore
        private set

    override fun onCreate() {
        super.onCreate()
        val db = LettersDatabase.get(this)
        letters = LetterRepository(db.letterDao())
        scenes = SceneRepository(db.sceneDao())
        prefs = UserPreferences(this)
        avatars = AvatarStore(this)
    }
}
