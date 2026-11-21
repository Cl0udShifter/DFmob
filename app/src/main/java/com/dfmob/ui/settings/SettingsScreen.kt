package com.dfmob.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.dfmob.data.preferences.AppThemeMode
import com.dfmob.data.preferences.DefaultAppTab
import com.dfmob.data.preferences.DefaultTabPreferences
import com.dfmob.data.preferences.NotificationPreferences
import com.dfmob.data.preferences.ThemePreferences
import com.dfmob.data.service.ApiDiagnosticsService
import com.dfmob.data.service.ApiHealthStatus
import com.dfmob.ui.theme.TransitGreen
import com.dfmob.updater.AppUpdateManager
import com.dfmob.updater.UpdateState
import androidx.compose.material3.LinearProgressIndicator
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    themePreferences: ThemePreferences,
    currentThemeMode: AppThemeMode,
    notificationPreferences: NotificationPreferences,
    defaultTabPreferences: DefaultTabPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val diagnosticsService = remember { ApiDiagnosticsService() }
    val updateManager = remember { AppUpdateManager(context) }
    val currentVersion = remember { updateManager.getCurrentVersion() }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    val notifSettings by notificationPreferences.settings.collectAsState()
    val currentDefaultTab by defaultTabPreferences.defaultTab.collectAsState()
    var apiStatuses by remember { mutableStateOf<List<ApiHealthStatus>>(emptyList()) }
    var isCheckingApis by remember { mutableStateOf(false) }

    fun refreshApis() {
        coroutineScope.launch {
            isCheckingApis = true
            apiStatuses = diagnosticsService.checkAllApis()
            isCheckingApis = false
        }
    }

    LaunchedEffect(Unit) {
        refreshApis()
        updateState = updateManager.checkForUpdates()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Configurações",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ==========================================
            // COMPONENTE 1: TEMA DO APLICATIVO
            // ==========================================
            Text(
                text = "TEMA DO APLICATIVO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Seletor plano com 3 opcoes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeOptionButton(
                    text = "Sistema",
                    isSelected = currentThemeMode == AppThemeMode.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { themePreferences.setThemeMode(AppThemeMode.SYSTEM) }
                )
                ThemeOptionButton(
                    text = "Claro",
                    isSelected = currentThemeMode == AppThemeMode.LIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = { themePreferences.setThemeMode(AppThemeMode.LIGHT) }
                )
                ThemeOptionButton(
                    text = "Escuro",
                    isSelected = currentThemeMode == AppThemeMode.DARK,
                    modifier = Modifier.weight(1f),
                    onClick = { themePreferences.setThemeMode(AppThemeMode.DARK) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ==========================================
            // COMPONENTE: TELA INICIAL PADRÃO
            // ==========================================
            Text(
                text = "TELA INICIAL PADRÃO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Escolha qual seção abrir ao iniciar o aplicativo:",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Seletor plano com as 4 opções
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    DefaultAppTab.HOME to "Início",
                    DefaultAppTab.STOPS to "Paradas",
                    DefaultAppTab.LINES to "Linhas",
                    DefaultAppTab.TRIP_PLANNER to "Viagens"
                ).forEach { (tab, label) ->
                    ThemeOptionButton(
                        text = label,
                        isSelected = currentDefaultTab == tab,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        onClick = { defaultTabPreferences.setDefaultTab(tab) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ==========================================
            // COMPONENTE 2: ALERTAS E NOTIFICAÇÕES
            // ==========================================
            Text(
                text = "ALERTAS E NOTIFICAÇÕES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Subseção: Alarme de desembarque
            Text(
                text = "Alarme de desembarque em viagens",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Antecedência para emitir o alarme sonoro e vibrar antes da sua parada:",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Seletor de 1, 2 ou 3 paradas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    1 to "1 parada (250m)",
                    2 to "2 paradas (400m)",
                    3 to "3 paradas (600m)"
                ).forEach { (stops, label) ->
                    ThemeOptionButton(
                        text = label,
                        isSelected = notifSettings.descentAdvanceStops == stops,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.5.sp,
                        onClick = { notificationPreferences.setDescentAdvanceStops(stops) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subseção: Avisos de aproximação na parada
            Text(
                text = "Avisos de aproximação na parada",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Receber notificações quando o ônibus monitorado estiver a caminho:",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Chips com alternância rápida para cada faixa de aviso
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotificationMilestoneChip(
                    text = "15 min",
                    isEnabled = notifSettings.notify15Min,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val ok = notificationPreferences.setNotify15Min(!notifSettings.notify15Min)
                        if (!ok) {
                            Toast.makeText(context, "Ao menos um intervalo deve permanecer ativo.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                NotificationMilestoneChip(
                    text = "10 min",
                    isEnabled = notifSettings.notify10Min,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val ok = notificationPreferences.setNotify10Min(!notifSettings.notify10Min)
                        if (!ok) {
                            Toast.makeText(context, "Ao menos um intervalo deve permanecer ativo.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                NotificationMilestoneChip(
                    text = "5 min",
                    isEnabled = notifSettings.notify5Min,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val ok = notificationPreferences.setNotify5Min(!notifSettings.notify5Min)
                        if (!ok) {
                            Toast.makeText(context, "Ao menos um intervalo deve permanecer ativo.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                NotificationMilestoneChip(
                    text = "2 min",
                    isEnabled = notifSettings.notify2Min,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val ok = notificationPreferences.setNotify2Min(!notifSettings.notify2Min)
                        if (!ok) {
                            Toast.makeText(context, "Ao menos um intervalo deve permanecer ativo.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ==========================================
            // COMPONENTE 3: ESTADO DAS APIS
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ESTADO DAS APIS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isCheckingApis) { refreshApis() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCheckingApis) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = TransitGreen
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Testar",
                            tint = TransitGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = if (isCheckingApis) "Testando..." else "Testar Conexão",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = TransitGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (apiStatuses.isEmpty() && isCheckingApis) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TransitGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verificando conectividade das APIs...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    apiStatuses.forEach { status ->
                        ApiStatusRow(status = status)
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ==========================================
            // COMPONENTE 4: ATUALIZAÇÃO DO APLICATIVO
            // ==========================================
            Text(
                text = "ATUALIZAÇÃO DO APLICATIVO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            AppUpdateRow(
                currentVersion = currentVersion,
                updateState = updateState,
                onUpdateClick = {
                    when (val state = updateState) {
                        is UpdateState.ReadyToInstall -> {
                            downloadedApkFile?.let { apk ->
                                updateManager.installApk(apk)
                            }
                        }
                        is UpdateState.UpdateAvailable -> {
                            coroutineScope.launch {
                                updateState = UpdateState.Downloading(progressPercent = 0)
                                val apk = updateManager.downloadApk(
                                    downloadUrl = state.releaseInfo.downloadUrl,
                                    targetFileName = state.releaseInfo.apkFileName,
                                    onProgress = { p ->
                                        updateState = UpdateState.Downloading(progressPercent = p)
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AppUpdateRow(
    currentVersion: String,
    updateState: UpdateState,
    onUpdateClick: () -> Unit
) {
    val hasUpdate = updateState is UpdateState.UpdateAvailable || updateState is UpdateState.ReadyToInstall
    val isDownloading = updateState is UpdateState.Downloading

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "DFmob v$currentVersion",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when (updateState) {
                        is UpdateState.UpdateAvailable -> "Nova versão v${updateState.releaseInfo.tagName} disponível"
                        is UpdateState.Downloading -> "Baixando atualização: ${updateState.progressPercent}%"
                        is UpdateState.ReadyToInstall -> "Atualização pronta para instalação"
                        is UpdateState.Error -> "Não foi possível verificar atualizações"
                        else -> "O aplicativo está atualizado"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = if (hasUpdate) TransitGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val buttonShape = RoundedCornerShape(8.dp)
            val isButtonEnabled = hasUpdate && !isDownloading

            Box(
                modifier = Modifier
                    .clip(buttonShape)
                    .then(
                        if (hasUpdate) {
                            Modifier.background(TransitGreen)
                        } else {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                buttonShape
                            )
                        }
                    )
                    .clickable(enabled = isButtonEnabled, onClick = onUpdateClick)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (updateState) {
                        is UpdateState.Downloading -> "${updateState.progressPercent}%"
                        is UpdateState.ReadyToInstall -> "Instalar"
                        is UpdateState.UpdateAvailable -> "Atualizar"
                        else -> "Atualizar"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = when {
                        hasUpdate -> Color.White
                        isDownloading -> TransitGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }
        }

        if (isDownloading) {
            val progress = (updateState as UpdateState.Downloading).progressPercent / 100f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = TransitGreen,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun ThemeOptionButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
    val textColor = if (isSelected) TransitGreen else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = fontSize
            ),
            color = textColor,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ApiStatusRow(status: ApiHealthStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = status.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statusColor = if (status.isHealthy) TransitGreen else Color(0xFFFF5252)
            val statusLabel = if (status.isHealthy) "Conforme" else "Instável"

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = statusColor
            )
        }
    }
}

@Composable
private fun NotificationMilestoneChip(
    text: String,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (isEnabled) TransitGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val borderColor = if (isEnabled) TransitGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textColor = if (isEnabled) TransitGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = textColor
        )
    }
}
