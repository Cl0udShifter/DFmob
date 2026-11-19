package com.dfmob.ui.alerts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.SemobAlert
import com.dfmob.data.model.TransitLine
import com.dfmob.data.repository.AlertsRepository
import com.dfmob.ui.theme.TransitGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onLineClick: (String, Int?) -> Unit = { _, _ -> },
    alertsRepository: AlertsRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = alertsRepository ?: remember { AlertsRepository(context) }

    var alerts by remember { mutableStateOf<List<SemobAlert>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var linesMap by remember { mutableStateOf<Map<String, TransitLine>>(emptyMap()) }

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        isLoading = true
        val list = repository.getAlerts()
        alerts = list
        repository.preferences.markAllAsSeen(list.map { it.id })

        // Pre-carrega detalhes das linhas afetadas para obter cores corretas
        val allLineNums = list.flatMap { it.affectedLineNumbers }.distinct()
        val map = mutableMapOf<String, TransitLine>()
        allLineNums.forEach { num ->
            val detail = repository.getLineDetail(num)
            if (detail != null) {
                map[num] = detail
            }
        }
        linesMap = map
        isLoading = false
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar com botao voltar e titulo Transit Pure Blocks
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backShape = RoundedCornerShape(12.dp)
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(backShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, backShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = rememberRipple(bounded = true),
                            onClick = onBack
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Avisos",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = ".",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        ),
                        color = TransitGreen
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TransitGreen,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (alerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Nenhum aviso operacional no momento",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "A operação das linhas de ônibus e metrô está normal.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            linesMap = linesMap,
                            onLineClick = onLineClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertCard(
    alert: SemobAlert,
    linesMap: Map<String, TransitLine>,
    onLineClick: (String, Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = cardShape
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = cardShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Categoria e Data de vigencia
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val categoryColor = when (alert.category.uppercase()) {
                    "OBRAS" -> Color(0xFFE65100)
                    "DESVIO" -> Color(0xFFD84315)
                    "FERIADO" -> Color(0xFF1565C0)
                    else -> Color(0xFF00883E)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(categoryColor.copy(alpha = 0.15f))
                        .border(1.dp, categoryColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = alert.category.uppercase(),
                        color = categoryColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = alert.dateFormatted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Titulo do alerta
            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Descricao detalhada
            Text(
                text = alert.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Linhas afetadas
            if (alert.affectedLineNumbers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "LINHAS AFETADAS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    alert.affectedLineNumbers.forEach { lineNum ->
                        val lineInfo = linesMap[lineNum]
                        val badgeBg = if (lineInfo != null) {
                            try {
                                Color(android.graphics.Color.parseColor(lineInfo.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF00D26A)
                            }
                        } else {
                            Color(0xFF00D26A)
                        }

                        val badgeText = if (lineInfo != null) {
                            try {
                                Color(android.graphics.Color.parseColor(lineInfo.textColorHex))
                            } catch (_: Exception) {
                                Color.White
                            }
                        } else {
                            Color.White
                        }

                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeBg)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = rememberRipple(bounded = true),
                                    onClick = {
                                        onLineClick(lineNum, lineInfo?.routeId?.toIntOrNull())
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lineNum,
                                color = badgeText,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
