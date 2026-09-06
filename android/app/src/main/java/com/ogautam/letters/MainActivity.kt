package com.ogautam.letters

import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Placeholder. Phase 1 is the data layer only — this exists so the app installs and runs,
 * and so the database is exercised on a real device. Phase 2 replaces it with the Compose
 * home screen.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        setContentView(label)

        val app = application as LettersApplication
        lifecycleScope.launch {
            combine(
                app.scenes.observeCount(),
                app.letters.observeCount(),
                app.letters.observeSealedCount(),
                app.prefs.penName,
            ) { scenes, letters, sealed, penName ->
                "Letters — data layer\n\n$scenes scenes\n$letters letters ($sealed sealed)\n\nSigned: $penName"
            }.collect { label.text = it }
        }
    }
}
