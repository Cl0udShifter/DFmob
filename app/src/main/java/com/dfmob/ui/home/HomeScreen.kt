package com.dfmob.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.TransitLine
import com.dfmob.data.repository.FavoritesRepository
import com.dfmob.data.repository.RecentSearchesRepository
import com.dfmob.data.repository.StopsRepository
import com.dfmob.ui.tripplanner.MapLocationPicker
import com.dfmob.ui.home.components.AppUpdateModule
import com.dfmob.ui.home.components.FrequentPlacesRow
import com.dfmob.ui.home.components.QuickNavButtons
import com.dfmob.ui.home.components.RecentAndFavoritesSection
import com.dfmob.ui.home.components.SearchPill
import com.dfmob.ui.theme.DFmobTheme
import com.dfmob.ui.theme.TransitGreen
import com.dfmob.updater.AppUpdateManager
import com.dfmob.updater.UpdateState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    favoritesRepository: FavoritesRepository? = null,
    recentSearchesRepository: RecentSearchesRepository? = null,
    onNavigateToLines: () -> Unit = {},
    onNavigateToStops: () -> Unit = {},
    onNavigateToLineRoute: (String, Int?) -> Unit = { _, _ -> },
    onNavigateToTripPlanner: (DFLocation?) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val favoritesRepo = favoritesRepository ?: remember { FavoritesRepository(context) }
    val recentRepo = recentSearchesRepository ?: remember { RecentSearchesRepository(context) }
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    val favoriteLines by favoritesRepo.favoriteLines.collectAsState()
    val favoriteStops by favoritesRepo.favoriteStops.collectAsState()
    val recentSearches by recentRepo.recentSearches.collectAsState()
    val homePlace by recentRepo.homePlace.collectAsState()
    val workPlace by recentRepo.workPlace.collectAsState()

    var pickingPlaceType by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // Verificacao de atualizacao em background, sem bloquear o carregamento imediato dos favoritos
    LaunchedEffect(Unit) {
        val result = updateManager.checkForUpdates()
        updateState = result
    }

    if (pickingPlaceType != null) {
        val placeType = pickingPlaceType!!
        val initialPlace = if (placeType == "HOME") homePlace else workPlace
        MapLocationPicker(
            initialLatE6 = initialPlace?.latE6,
            initialLonE6 = initialPlace?.lonE6,
            title = if (placeType == "HOME") "Definir local de Casa" else "Definir local de Trabalho",
            onCancel = { pickingPlaceType = null },
            onConfirm = { latE6, lonE6, caption ->
                val loc = DFLocation(caption, if (placeType == "HOME") "Casa" else "Trabalho", latE6, lonE6)
                if (placeType == "HOME") {
                    recentRepo.setHomePlace(loc)
                } else {
                    recentRepo.setWorkPlace(loc)
                }
                pickingPlaceType = null
                onNavigateToTripPlanner(loc)
            },
            onReverseGeocode = { latE6, lonE6 ->
                StopsRepository(context).reverseGeocode(latE6, lonE6)
            }
        )
    } else {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding(),
            containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Modulo fixo na parte inferior, visivel exclusivamente quando ha update disponivel
            val isUpdateVisible = updateState is UpdateState.UpdateAvailable ||
                    updateState is UpdateState.Downloading ||
                    updateState is UpdateState.ReadyToInstall

            AnimatedVisibility(
                visible = isUpdateVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    AppUpdateModule(
                        updateState = updateState,
                        onInstallUpdate = {
                            when (val state = updateState) {
                                is UpdateState.ReadyToInstall -> {
                                    downloadedApkFile?.let { apk ->
                                        updateManager.installApk(apk)
                                    }
                                }

                                is UpdateState.UpdateAvailable -> {
                                    scope.launch {
                                        updateState = UpdateState.Downloading(progressPercent = 0)
                                        val apk = updateManager.downloadApk(
                                            downloadUrl = state.releaseInfo.downloadUrl,
                                            targetFileName = state.releaseInfo.apkFileName,
                                            onProgress = { progress ->
                                                updateState = UpdateState.Downloading(progressPercent = progress)
                                            }
                                        )
                                        if (apk != null && apk.exists()) {
                                            downloadedApkFile = apk
                                            updateState = UpdateState.ReadyToInstall(apk.absolutePath)
                                            updateManager.installApk(apk)
                                        } else {
                                            updateState = UpdateState.Error("Falha ao baixar arquivo APK.")
                                        }
                                    }
                                }

                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Cabecalho Transit Pure Blocks com botao de configuracoes alinhado a direita
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DFmob",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = ".",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp
                        ),
                        color = TransitGreen
                    )
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Configurações",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Componente 1: Caixa de busca expansivel (Transit style)
            SearchPill(
                onClick = { onNavigateToTripPlanner(null) }
            )

            // Componente 2: Botoes de modo Linhas e Paradas
            QuickNavButtons(
                onLinesClick = onNavigateToLines,
                onStopsClick = onNavigateToStops
            )

            // Novo Componente: Casa e Trabalho
            FrequentPlacesRow(
                homePlace = homePlace,
                workPlace = workPlace,
                onHomeClick = {
                    if (homePlace != null) {
                        onNavigateToTripPlanner(homePlace)
                    } else {
                        pickingPlaceType = "HOME"
                    }
                },
                onHomeLongClick = {
                    pickingPlaceType = "HOME"
                },
                onWorkClick = {
                    if (workPlace != null) {
                        onNavigateToTripPlanner(workPlace)
                    } else {
                        pickingPlaceType = "WORK"
                    }
                },
                onWorkLongClick = {
                    pickingPlaceType = "WORK"
                }
            )

            // Componente 3: Cards estilo painel de partidas com favoritos dinamicos (linhas e paradas)
            RecentAndFavoritesSection(
                recentSearches = recentSearches,
                favoriteLines = favoriteLines,
                favoriteStops = favoriteStops,
                onRecentSearchClick = { loc -> onNavigateToTripPlanner(loc) },
                onTogglePin = { item -> recentRepo.togglePin(item) },
                onClearRecent = { recentRepo.clearUnpinned() },
                onLineClick = { line -> onNavigateToLineRoute(line.lineNumber, line.routeId.toIntOrNull()) },
                onStopClick = { onNavigateToStops() }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
}

@Preview(name = "Home Screen - Transit Pure Blocks", showBackground = true)
@Composable
fun HomeScreenTransitPreview() {
    DFmobTheme(darkTheme = true) {
        HomeScreen()
    }
}
