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
import com.dfmob.data.model.TransitLine
import com.dfmob.data.repository.FavoritesRepository
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
    onNavigateToLines: () -> Unit = {}
) {
    val context = LocalContext.current
    val favoritesRepo = favoritesRepository ?: remember { FavoritesRepository(context) }
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    val favoriteLines by favoritesRepo.favoriteLines.collectAsState()

    val scrollState = rememberScrollState()

    // Verificacao de atualizacao em background, sem bloquear o carregamento imediato dos favoritos
    LaunchedEffect(Unit) {
        val result = updateManager.checkForUpdates()
        updateState = result
    }

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
            // Cabecalho Transit Pure Blocks
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            // Componente 1: Caixa de busca expansivel (Transit style)
            SearchPill(
                onClick = {}
            )

            // Componente 2: Botoes de modo Linhas e Paradas
            QuickNavButtons(
                onLinesClick = onNavigateToLines,
                onStopsClick = {}
            )

            // Novo Componente: Casa e Trabalho
            FrequentPlacesRow(
                onHomeClick = {},
                onWorkClick = {}
            )

            // Componente 3: Cards estilo painel de partidas com linhas favoritas dinamicas
            RecentAndFavoritesSection(
                favoriteLines = favoriteLines
            )

            Spacer(modifier = Modifier.height(20.dp))
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
