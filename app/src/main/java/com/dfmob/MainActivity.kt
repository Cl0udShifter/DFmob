package com.dfmob

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.osmdroid.config.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dfmob.data.model.DFLocation
import com.dfmob.data.preferences.AppThemeMode
import com.dfmob.data.preferences.NotificationPreferences
import com.dfmob.data.preferences.ThemePreferences
import com.dfmob.data.repository.FavoritesRepository
import com.dfmob.data.repository.RecentSearchesRepository
import com.dfmob.ui.alerts.AlertsScreen
import com.dfmob.ui.home.HomeScreen
import com.dfmob.ui.lines.LinesScreen
import com.dfmob.ui.settings.SettingsScreen
import com.dfmob.ui.stops.StopsScreen
import com.dfmob.ui.tripplanner.TripPlannerScreen
import com.dfmob.ui.theme.DFmobTheme
import com.dfmob.worker.SemobAlertsWorker

enum class AppScreen {
    HOME,
    LINES,
    STOPS,
    TRIP_PLANNER,
    SETTINGS,
    ALERTS
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO = "EXTRA_NAVIGATE_TO"
        const val EXTRA_STOP_ID = "EXTRA_STOP_ID"
        const val EXTRA_LINE_NUMBER = "EXTRA_LINE_NUMBER"
        const val EXTRA_DESTINATION = "EXTRA_DESTINATION"
    }

    private var pendingScreen by mutableStateOf<AppScreen?>(null)
    private var pendingStopId by mutableStateOf<Int?>(null)
    private var pendingLineNumber by mutableStateOf<String?>(null)
    private var pendingDestination by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.getStringExtra(EXTRA_NAVIGATE_TO)) {
            "ALERTS" -> {
                pendingScreen = AppScreen.ALERTS
            }
            "STOPS" -> {
                val sId = intent.getIntExtra(EXTRA_STOP_ID, -1)
                if (sId > 0) {
                    pendingStopId = sId
                    pendingLineNumber = intent.getStringExtra(EXTRA_LINE_NUMBER)
                    pendingDestination = intent.getStringExtra(EXTRA_DESTINATION)
                    pendingScreen = AppScreen.STOPS
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        // Solicita permissao de notificacoes no Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Limpa servico de rastreamento continuo residual e sua notificacao fixa
        try {
            com.dfmob.service.StopArrivalTrackingService.stop(applicationContext)
            androidx.core.app.NotificationManagerCompat.from(applicationContext).cancel(29999)
        } catch (_: Exception) {}

        // Agenda o worker de escuta periódica dos comunicados operacionais da SEMOB
        SemobAlertsWorker.schedule(applicationContext)

        // Configuracao global de download de tiles com persistencia vitalicia no osmdroid
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
            userAgentValue = "DFmob/1.4.10 (Android)"
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
            val notificationPreferences = remember { NotificationPreferences(context) }
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
                val initialScreen = when (intent.getStringExtra(EXTRA_NAVIGATE_TO)) {
                    "ALERTS" -> AppScreen.ALERTS
                    "STOPS" -> AppScreen.STOPS
                    else -> AppScreen.HOME
                }
                var currentScreen by rememberSaveable { mutableStateOf(initialScreen) }
                var targetStopIdForStops by rememberSaveable {
                    mutableStateOf(intent.getIntExtra(EXTRA_STOP_ID, -1).takeIf { it > 0 })
                }
                var targetLineNumberForStops by rememberSaveable {
                    mutableStateOf<String?>(intent.getStringExtra(EXTRA_LINE_NUMBER))
                }
                var targetDestinationForStops by rememberSaveable {
                    mutableStateOf<String?>(intent.getStringExtra(EXTRA_DESTINATION))
                }
                var targetLineIdForStops by rememberSaveable { mutableStateOf<Int?>(null) }
                var targetDestinationForTripPlanner by remember { mutableStateOf<DFLocation?>(null) }
                var targetOriginLocationForTripPlanner by remember { mutableStateOf<com.dfmob.data.model.TripPlanLocation?>(null) }
                var targetDestinationLocationForTripPlanner by remember { mutableStateOf<com.dfmob.data.model.TripPlanLocation?>(null) }
                var stopsOpenedFromLines by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(pendingScreen) {
                    pendingScreen?.let {
                        currentScreen = it
                        if (it == AppScreen.STOPS) {
                            pendingStopId?.let { sId ->
                                targetStopIdForStops = sId
                                targetLineNumberForStops = pendingLineNumber
                                targetDestinationForStops = pendingDestination
                            }
                        }
                        pendingScreen = null
                        pendingStopId = null
                        pendingLineNumber = null
                        pendingDestination = null
                    }
                }

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
                        targetOriginLocationForTripPlanner = null
                        targetDestinationLocationForTripPlanner = null
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
                                targetOriginLocationForTripPlanner = null
                                targetDestinationLocationForTripPlanner = null
                                currentScreen = AppScreen.TRIP_PLANNER
                            },
                            onNavigateToTripPlannerWithTrip = { trip ->
                                targetOriginLocationForTripPlanner = trip.origin
                                targetDestinationLocationForTripPlanner = trip.destination
                                targetDestinationForTripPlanner = null
                                currentScreen = AppScreen.TRIP_PLANNER
                            },
                            onNavigateToAlerts = {
                                currentScreen = AppScreen.ALERTS
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
                            initialStopId = targetStopIdForStops,
                            initialDestination = targetDestinationForStops,
                            fromLinesScreen = stopsOpenedFromLines,
                            onBack = {
                                val returnToLines = stopsOpenedFromLines
                                targetLineNumberForStops = null
                                targetLineIdForStops = null
                                targetStopIdForStops = null
                                targetDestinationForStops = null
                                stopsOpenedFromLines = false
                                currentScreen = if (returnToLines) AppScreen.LINES else AppScreen.HOME
                            }
                        )
                        AppScreen.TRIP_PLANNER -> TripPlannerScreen(
                            initialDestination = targetDestinationForTripPlanner,
                            initialOriginLocation = targetOriginLocationForTripPlanner,
                            initialDestinationLocation = targetDestinationLocationForTripPlanner,
                            favoritesRepository = favoritesRepository,
                            recentSearchesRepository = recentSearchesRepository,
                            onBack = {
                                targetDestinationForTripPlanner = null
                                targetOriginLocationForTripPlanner = null
                                targetDestinationLocationForTripPlanner = null
                                currentScreen = AppScreen.HOME
                            }
                        )
                        AppScreen.ALERTS -> AlertsScreen(
                            onBack = { currentScreen = AppScreen.HOME },
                            onLineClick = { lineNum, lineId ->
                                targetLineNumberForStops = lineNum
                                targetLineIdForStops = lineId
                                stopsOpenedFromLines = false
                                currentScreen = AppScreen.STOPS
                            }
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            themePreferences = themePreferences,
                            currentThemeMode = themeMode,
                            notificationPreferences = notificationPreferences,
                            onBack = { currentScreen = AppScreen.HOME }
                        )
                    }
                }
            }
        }
    }
}
