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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dfmob.data.model.DFLocation
import com.dfmob.data.preferences.AppThemeMode
import com.dfmob.data.preferences.ThemePreferences
import com.dfmob.data.repository.FavoritesRepository
import com.dfmob.data.repository.RecentSearchesRepository
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

        // Configuracao global de download de tiles com persistencia vitalicia no osmdroid
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
            userAgentValue = "DFmob/1.4.3 (Android)"
            tileDownloadThreads = 8.toShort()
            tileDownloadMaxQueueSize = 60.toShort()
            cacheMapTileCount = 64.toShort()
            // Cache vitalicio em disco: 10 GB de capacidade maxima
            tileFileSystemCacheMaxBytes = 10L * 1024 * 1024 * 1024
            tileFileSystemCacheTrimBytes = 9L * 1024 * 1024 * 1024
            // Extende a duracao dos tiles baixados para 10 anos, impedindo expiracao ou re-download
            expirationExtendedDuration = 1000L * 60 * 60 * 24 * 365 * 10
            expirationOverrideDuration = 1000L * 60 * 60 * 24 * 365 * 10
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
                val recentSearchesRepository = remember { RecentSearchesRepository(context) }
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
                var targetLineNumberForStops by rememberSaveable { mutableStateOf<String?>(null) }
                var targetLineIdForStops by rememberSaveable { mutableStateOf<Int?>(null) }
                var targetDestinationForTripPlanner by remember { mutableStateOf<DFLocation?>(null) }
                var stopsOpenedFromLines by rememberSaveable { mutableStateOf(false) }

                BackHandler(enabled = currentScreen != AppScreen.HOME) {
                    if (currentScreen == AppScreen.STOPS && stopsOpenedFromLines) {
                        targetLineNumberForStops = null
                        targetLineIdForStops = null
                        stopsOpenedFromLines = false
                        currentScreen = AppScreen.LINES
                    } else {
                        targetLineNumberForStops = null
                        targetLineIdForStops = null
                        targetDestinationForTripPlanner = null
                        stopsOpenedFromLines = false
                        currentScreen = AppScreen.HOME
                    }
                }

                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            favoritesRepository = favoritesRepository,
                            recentSearchesRepository = recentSearchesRepository,
                            onNavigateToLines = { currentScreen = AppScreen.LINES },
                            onNavigateToStops = {
                                targetLineNumberForStops = null
                                targetLineIdForStops = null
                                stopsOpenedFromLines = false
                                currentScreen = AppScreen.STOPS
                            },
                            onNavigateToLineRoute = { lineNum, lineId ->
                                targetLineNumberForStops = lineNum
                                targetLineIdForStops = lineId
                                stopsOpenedFromLines = false
                                currentScreen = AppScreen.STOPS
                            },
                            onNavigateToTripPlanner = { dest ->
                                targetDestinationForTripPlanner = dest
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
                                stopsOpenedFromLines = true
                                currentScreen = AppScreen.STOPS
                            }
                        )
                        AppScreen.STOPS -> StopsScreen(
                            favoritesRepository = favoritesRepository,
                            initialLineNumber = targetLineNumberForStops,
                            initialLineId = targetLineIdForStops,
                            fromLinesScreen = stopsOpenedFromLines,
                            onBack = {
                                val returnToLines = stopsOpenedFromLines
                                targetLineNumberForStops = null
                                targetLineIdForStops = null
                                stopsOpenedFromLines = false
                                currentScreen = if (returnToLines) AppScreen.LINES else AppScreen.HOME
                            }
                        )
                        AppScreen.TRIP_PLANNER -> TripPlannerScreen(
                            initialDestination = targetDestinationForTripPlanner,
                            recentSearchesRepository = recentSearchesRepository,
                            onBack = {
                                targetDestinationForTripPlanner = null
                                currentScreen = AppScreen.HOME
                            }
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
