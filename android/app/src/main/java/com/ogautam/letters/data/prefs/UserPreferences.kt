package com.ogautam.letters.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/**
 * The pen name signed at the bottom of every letter. The web app kept this in
 * localStorage and defaulted to the Google display name; with no account, it defaults
 * to "You" until the user sets one.
 */
class UserPreferences(private val context: Context) {

    val penName: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[PEN_NAME]?.takeIf(String::isNotBlank) ?: DEFAULT_PEN_NAME }

    suspend fun setPenName(name: String) {
        context.dataStore.edit { prefs ->
            val trimmed = name.trim()
            if (trimmed.isEmpty()) prefs.remove(PEN_NAME) else prefs[PEN_NAME] = trimmed
        }
    }

    companion object {
        const val DEFAULT_PEN_NAME = "You"
        private val PEN_NAME = stringPreferencesKey("pen_name")
    }
}
