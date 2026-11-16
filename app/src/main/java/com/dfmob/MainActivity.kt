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
import androidx.compose.ui.platform.LocalContext
import com.dfmob.data.repository.FavoritesRepository
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
                val context = LocalContext.current
                val favoritesRepository = remember { FavoritesRepository(context) }
                var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            favoritesRepository = favoritesRepository,
                            onNavigateToLines = { currentScreen = AppScreen.LINES }
                        )
                        AppScreen.LINES -> LinesScreen(
                            favoritesRepository = favoritesRepository,
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    }
                }
            }
        }
    }
}
