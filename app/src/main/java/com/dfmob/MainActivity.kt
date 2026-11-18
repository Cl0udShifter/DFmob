package com.dfmob

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.osmdroid.config.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dfmob.data.preferences.AppThemeMode
import com.dfmob.data.preferences.ThemePreferences
import com.dfmob.data.repository.FavoritesRepository
import com.dfmob.ui.home.HomeScreen
import com.dfmob.ui.lines.LinesScreen
import com.dfmob.ui.settings.SettingsScreen
import com.dfmob.ui.stops.StopsScreen
import com.dfmob.ui.tripplanner.TripPlannerScreen
import com.dfmob.ui.theme.DFmobTheme

enum class AppScreen {
    HOME,
    LINES,
    STOPS,
    TRIP_PLANNER,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Otimizacao global de download de tiles e cache de alta performance no osmdroid
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
            userAgentValue = "DFmob/1.4.0 (Android)"
            tileDownloadThreads = 8.toShort()
            tileDownloadMaxQueueSize = 60.toShort()
            cacheMapTileCount = 48.toShort()
            tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
        }

        setContent {
            val context = LocalContext.current
            val themePreferences = remember { ThemePreferences(context) }
            val themeMode by themePreferences.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            DFmobTheme(darkTheme = isDarkTheme) {
                val favoritesRepository = remember { FavoritesRepository(context) }
                var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
                var targetLineNumberForStops by remember { mutableStateOf<String?>(null) }
                var targetLineIdForStops by remember { mutableStateOf<Int?>(null) }

                BackHandler(enabled = currentScreen != AppScreen.HOME) {
                    targetLineNumberForStops = null
                    targetLineIdForStops = null
                    currentScreen = AppScreen.HOME
                }

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
                            },
                            onNavigateToTripPlanner = {
                                currentScreen = AppScreen.TRIP_PLANNER
                            },
                            onNavigateToSettings = {
                                currentScreen = AppScreen.SETTINGS
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
                        AppScreen.TRIP_PLANNER -> TripPlannerScreen(
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            themePreferences = themePreferences,
                            currentThemeMode = themeMode,
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    }
                }
            }
        }
    }
}
