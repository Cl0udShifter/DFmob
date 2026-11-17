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
import com.dfmob.ui.stops.StopsScreen
import com.dfmob.ui.theme.DFmobTheme

enum class AppScreen {
    HOME,
    LINES,
    STOPS
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
                var targetLineNumberForStops by remember { mutableStateOf<String?>(null) }
                var targetLineIdForStops by remember { mutableStateOf<Int?>(null) }

                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            favoritesRepository = favoritesRepository,
                            onNavigateToLines = { currentScreen = AppScreen.LINES },
                            onNavigateToStops = {
                                targetLineNumberForStops = null
                                targetLineIdForStops = null
                                currentScreen = AppScreen.STOPS
                            },
                            onNavigateToLineRoute = { lineNum ->
                                targetLineNumberForStops = lineNum
                                targetLineIdForStops = null
                                currentScreen = AppScreen.STOPS
                            }
                        )
                        AppScreen.LINES -> LinesScreen(
                            favoritesRepository = favoritesRepository,
                            onBack = { currentScreen = AppScreen.HOME },
                            onLineClick = { line ->
                                targetLineNumberForStops = line.lineNumber
                                targetLineIdForStops = null
                                currentScreen = AppScreen.STOPS
                            }
                        )
                        AppScreen.STOPS -> StopsScreen(
                            favoritesRepository = favoritesRepository,
                            initialLineNumber = targetLineNumberForStops,
                            initialLineId = targetLineIdForStops,
                            onBack = {
                                targetLineNumberForStops = null
                                targetLineIdForStops = null
                                currentScreen = AppScreen.HOME
                            }
                        )
                    }
                }
            }
        }
    }
}
