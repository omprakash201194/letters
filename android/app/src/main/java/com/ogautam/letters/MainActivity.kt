package com.ogautam.letters

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ogautam.letters.ui.LettersNavHost
import com.ogautam.letters.ui.theme.LettersTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LettersTheme {
                LettersNavHost()
            }
        }
    }
}
