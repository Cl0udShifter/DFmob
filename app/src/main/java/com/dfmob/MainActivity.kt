package com.dfmob

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dfmob.ui.home.HomeScreen
import com.dfmob.ui.lines.LinesScreen
import com.dfmob.ui.theme.DFmobTheme

enum class AppScreen {
    HOME,
    LINES
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DFmobTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            onNavigateToLines = { currentScreen = AppScreen.LINES }
                        )
                        AppScreen.LINES -> LinesScreen(
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    }
                }
            }
        }
    }
}
