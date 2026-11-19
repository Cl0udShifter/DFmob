package com.dfmob.ui.stops.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.AirportShuttle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.NextDeparture
import com.dfmob.data.model.StopArrival
import com.dfmob.data.model.StopArrivalAlarm
import com.dfmob.data.model.TransitStop
import com.dfmob.data.model.VehicleType
import com.dfmob.data.repository.StopAlarmsRepository
import com.dfmob.data.repository.StopsRepository
import com.dfmob.ui.theme.TransitGreen

import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun FloatingArrivalsDeck(
    stop: TransitStop,
    arrivals: List<StopArrival>,
    isLoading: Boolean,
    onClose: () -> Unit,
    onLineClick: (StopArrival) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    maxScreenHeight: Dp = 600.dp,
    isDarkTheme: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    modifier: Modifier = Modifier
) {
    // Estado expandido (75% da tela sem margens de floating pill) vs colapsado (floating pill padrao)
    var isExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val stopsRepo = remember { StopsRepository(context) }
    val stopAlarmsRepo = remember { StopAlarmsRepository(context) }
    var alarmsRevision by remember { mutableStateOf(0) }
    var selectedScheduleArrival by remember { mutableStateOf<StopArrival?>(null) }
    var dailySchedule by remember { mutableStateOf<List<NextDeparture>>(emptyList()) }
    var isLoadingSchedule by remember { mutableStateOf(false) }

    // Reseta para colapsado ao alternar a parada selecionada
    LaunchedEffect(stop.stopId) {
        isExpanded = false
        selectedScheduleArrival = null
    }

    // Carrega todas as chegadas futuras do dia da linha ao abrir a tabela
    LaunchedEffect(selectedScheduleArrival) {
        val arrival = selectedScheduleArrival
        if (arrival != null) {
            isLoadingSchedule = true
            dailySchedule = stopsRepo.getDailyDeparturesForLineAtStop(arrival, stop.stopId)
            isLoadingSchedule = false
        } else {
            dailySchedule = emptyList()
        }
    }

    // Intercepta o botao de voltar do Android para retornar à lista de linhas da parada
    BackHandler(enabled = selectedScheduleArrival != null) {
        selectedScheduleArrival = null
    }

    // Ticker a cada 1 segundo para atualizar minutos e texto 'Já' dinamicamente contra o relogio do sistema
    var currentClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)
            currentClockMs = System.currentTimeMillis()
        }
    }

    // Auto-cancelamento: desativa o aviso automaticamente após o ônibus passar do "Já"
    LaunchedEffect(currentClockMs) {
        var changed = false
        for (arr in arrivals) {
            if (stopAlarmsRepo.isAlarmActive(stop.stopId, arr.lineNumber, arr.destination)) {
                val effectiveMins = arr.getEffectiveMinutes(currentClockMs)
                if (effectiveMins <= 0 && (currentClockMs - arr.targetEpochMs) > 60_000L) {
                    stopAlarmsRepo.removeAlarm(stop.stopId, arr.lineNumber, arr.destination)
                    changed = true
                }
            }
        }
        if (changed) {
            alarmsRevision++
        }
    }

    // Animacao ultra-responsiva (90ms com FastOutSlowInEasing) conforme solicitado
    val animSpec = tween<Dp>(durationMillis = 90, easing = FastOutSlowInEasing)

    val isDeckExpanded = isExpanded || selectedScheduleArrival != null

    val targetHeight = if (isDeckExpanded) {
        if (maxScreenHeight > 200.dp) maxScreenHeight * 0.75f else 540.dp
    } else {
        320.dp
    }

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = animSpec,
        label = "deckHeight"
    )

    val animatedHorizontalPadding by animateDpAsState(
        targetValue = if (isDeckExpanded) 0.dp else 14.dp,
        animationSpec = animSpec,
        label = "deckPaddingH"
    )

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val animatedBottomPadding by animateDpAsState(
        targetValue = if (isDeckExpanded) 0.dp else 12.dp + navBarBottom,
        animationSpec = animSpec,
        label = "deckPaddingB"
    )

    val animatedBottomRadius by animateDpAsState(
        targetValue = if (isDeckExpanded) 0.dp else 24.dp,
        animationSpec = animSpec,
        label = "deckCornerBottom"
    )

    val deckShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = animatedBottomRadius,
        bottomEnd = animatedBottomRadius
    )

    val deckBackground = if (isDarkTheme) Color(0xF2141519) else Color(0xF8FFFFFF)
    val deckBorder = if (isDarkTheme) Color(0xFF282A33) else Color(0xFFE2E4EB)
    val closeBtnBg = if (isDarkTheme) Color(0xFF23252C) else Color(0xFFEAECEF)
    val handleColor = if (isDarkTheme) Color(0xFF3F424E) else Color(0xFFD1D5DB)

    // Gesto de puxar verticalmente para cima ou para baixo
    var dragOffsetY by remember { mutableStateOf(0f) }
    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { dragOffsetY = 0f },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                dragOffsetY += dragAmount
            },
            onDragEnd = {
                if (dragOffsetY < -25f) {
                    isExpanded = true
                } else if (dragOffsetY > 25f) {
                    if (selectedScheduleArrival != null) {
                        selectedScheduleArrival = null
                    } else {
                        isExpanded = false
                    }
                }
                dragOffsetY = 0f
            },
            onDragCancel = {
                dragOffsetY = 0f
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .padding(
                start = animatedHorizontalPadding,
                end = animatedHorizontalPadding,
                bottom = animatedBottomPadding
            )
            .clip(deckShape)
            .border(
                width = if (isDeckExpanded) 0.dp else 1.dp,
                color = if (isDeckExpanded) Color.Transparent else deckBorder,
                shape = deckShape
            )
            .then(if (!isDeckExpanded) dragModifier else Modifier),
        color = deckBackground,
        shape = deckShape,
        shadowElevation = if (isDarkTheme) 18.dp else 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 6.dp,
                    bottom = if (isDeckExpanded) 14.dp + navBarBottom else 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag Handle Bar indicador no topo (arrastavel e clicavel)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (selectedScheduleArrival != null) {
                            selectedScheduleArrival = null
                        } else {
                            isExpanded = !isExpanded
                        }
                    }
                    .then(dragModifier)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(handleColor)
                )
            }

            if (selectedScheduleArrival != null) {
                LineDailyScheduleView(
                    arrival = selectedScheduleArrival!!,
                    dailySchedule = dailySchedule,
                    isLoading = isLoadingSchedule,
                    currentClockMs = currentClockMs,
                    isDarkTheme = isDarkTheme,
                    onBack = { selectedScheduleArrival = null }
                )
            } else {
                // Cabecalho da Parada (arrastavel para alternar expansao)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dragModifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.stopName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = if (stop.isMetro) {
                        "METRÔ-DF • ${stop.platformCode ?: "PLATAFORMA PRINCIPAL"}"
                    } else {
                        "PARADA DE ÔNIBUS • ${stop.stopCode ?: "PONTO OFICIAL SEMOB"}"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (stop.isMetro) Color(0xFFE6A100) else if (isDarkTheme) TransitGreen else Color(0xFF00883E)
                    )
                }

                // Botoes de Acao: Favoritar Parada (Padrao Moovit) e Fechar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(closeBtnBg)
                            .clickable(onClick = onToggleFavorite),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favoritar parada",
                            tint = if (isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(closeBtnBg)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fechar chegadas",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            var searchQuery by remember { mutableStateOf("") }

            val filteredArrivals = remember(arrivals, searchQuery) {
                if (searchQuery.isBlank()) {
                    arrivals
                } else {
                    val q = searchQuery.trim().lowercase()
                    arrivals.filter { arrival ->
                        arrival.lineNumber.lowercase().contains(q) ||
                        arrival.lineName.lowercase().contains(q) ||
                        arrival.destination.lowercase().contains(q)
                    }
                }
            }

            val sortedArrivals = remember(filteredArrivals, alarmsRevision, currentClockMs) {
                filteredArrivals.sortedWith(
                    compareByDescending<StopArrival> {
                        stopAlarmsRepo.isAlarmActive(stop.stopId, it.lineNumber, it.destination)
                    }.thenBy {
                        it.getEffectiveMinutes(currentClockMs)
                    }
                )
            }

            // Barra de pesquisa minimalista e rapida sem caixa envolvente cinza
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Buscar linha",
                    tint = if (isDarkTheme) TransitGreen else Color(0xFF00883E),
                    modifier = Modifier.size(16.dp)
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar linha nesta parada...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(if (isDarkTheme) TransitGreen else Color(0xFF00883E)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { searchQuery = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Limpar busca",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Lista de Linhas ou Indicador de Carga
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = if (isDarkTheme) TransitGreen else Color(0xFF00883E),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (filteredArrivals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "Nenhuma linha encontrada para \"$searchQuery\"" else "Nenhuma linha em operacao no momento",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 2.dp)
                ) {
                    itemsIndexed(
                        items = sortedArrivals,
                        key = { index, it -> "${it.lineNumber}_${it.destination}_${it.minutesAway}_${it.scheduledTimeFormatted}_$index" }
                    ) { _, arrival ->
                        val isAlarmActive = stopAlarmsRepo.isAlarmActive(stop.stopId, arrival.lineNumber, arrival.destination)
                        StopArrivalLineCard(
                            arrival = arrival,
                            currentClockMs = currentClockMs,
                            isDarkTheme = isDarkTheme,
                            isAlarmActive = isAlarmActive,
                            onClick = { onLineClick(arrival) },
                            onScheduleClick = { selectedScheduleArrival = arrival },
                            onToggleAlarm = {
                                if (isAlarmActive) {
                                    stopAlarmsRepo.removeAlarm(stop.stopId, arrival.lineNumber, arrival.destination)
                                    Toast.makeText(context, "Aviso de aproximação desativado", Toast.LENGTH_SHORT).show()
                                } else {
                                    val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                                    if (!notifEnabled) {
                                        Toast.makeText(context, "Ative as notificações nas configurações do celular para receber os alertas", Toast.LENGTH_LONG).show()
                                    }

                                    val currentMins = arrival.getEffectiveMinutes(currentClockMs)
                                    val targetEpoch = if (arrival.isRealtime && arrival.targetEpochMs > 0L) {
                                        arrival.targetEpochMs
                                    } else if (arrival.targetEpochMs > 0L && Math.abs(arrival.targetEpochMs - (currentClockMs + currentMins * 60_000L)) < 300_000L) {
                                        arrival.targetEpochMs
                                    } else {
                                        currentClockMs + currentMins * 60_000L
                                    }

                                    stopAlarmsRepo.setAlarm(
                                        StopArrivalAlarm(
                                            lineNumber = arrival.lineNumber,
                                            destination = arrival.destination,
                                            stopId = stop.stopId,
                                            stopName = stop.stopName,
                                            targetEpochMs = targetEpoch
                                        )
                                    )
                                    Toast.makeText(context, "Aviso ativado para a linha ${arrival.lineNumber}!", Toast.LENGTH_SHORT).show()
                                }
                                alarmsRevision++
                            }
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
fun StopArrivalLineCard(
    arrival: StopArrival,
    currentClockMs: Long,
    isDarkTheme: Boolean,
    isAlarmActive: Boolean = false,
    onClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onToggleAlarm: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(14.dp)
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(arrival.colorHex))
    } catch (_: Exception) {
        if (arrival.isMetro) Color(0xFF008037) else TransitGreen
    }

    val cardBg = if (isDarkTheme) Color(0xFF16171C) else Color(0xFFF3F4F7)
    val cardBorder = if (isDarkTheme) Color(0xFF262830) else Color(0xFFE2E4EB)
    val liveGreen = if (isDarkTheme) TransitGreen else Color(0xFF00883E)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, cardBorder, cardShape)
            .clickable(onClick = onClick),
        color = cardBg,
        shape = cardShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge com numero da linha
                val badgeShape = RoundedCornerShape(7.dp)
                Surface(
                    shape = badgeShape,
                    color = parsedColor,
                    modifier = Modifier.clip(badgeShape)
                ) {
                    Text(
                        text = arrival.lineNumber,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        ),
                        color = if (arrival.isMetro) Color.White else Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Nome da Linha e Destino
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = arrival.lineName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        val iconTint = if (isDarkTheme) Color(0xFF8E92A0) else Color(0xFF6B7280)
                        when (arrival.vehicleType) {
                            VehicleType.METRO -> Icon(
                                imageVector = Icons.Rounded.DirectionsSubway,
                                contentDescription = "Metrô",
                                tint = iconTint,
                                modifier = Modifier.size(13.dp)
                            )
                            VehicleType.ARTICULADO -> ArticulatedBusIcon(
                                tint = iconTint,
                                modifier = Modifier.size(width = 16.dp, height = 11.dp)
                            )
                            VehicleType.MINI -> Icon(
                                imageVector = Icons.Rounded.AirportShuttle,
                                contentDescription = "Mini / Micro-ônibus",
                                tint = iconTint,
                                modifier = Modifier.size(13.dp)
                            )
                            VehicleType.PADRON -> Icon(
                                imageVector = Icons.Rounded.DirectionsBus,
                                contentDescription = "Ônibus Padron",
                                tint = iconTint,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        if (arrival.hasAccessibility) {
                            Icon(
                                imageVector = Icons.Rounded.Accessible,
                                contentDescription = "Veículo acessível com elevador/rampa",
                                tint = iconTint,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text(
                        text = "Sentido ${arrival.destination}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Minutos ate a chegada recalculados dinamicamente com suporte a "Já"
                val etaText = arrival.getDisplayEta(currentClockMs)

                // Se for GPS ao vivo, cor verde com icone de sinal Wi-Fi; se for programado, cor padrao com icone de relogio
                val etaColor = if (arrival.isRealtime) liveGreen else MaterialTheme.colorScheme.onSurface

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (arrival.isRealtime) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = "GPS ao vivo",
                            tint = liveGreen,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Horário agendado",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = etaText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = etaColor
                    )
                }
            }

            // Proximos horarios da linha e botao discreto para tabela de chegadas do dia
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (arrival.nextDepartures.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = "Seguintes:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = if (isDarkTheme) Color(0xFF757885) else Color(0xFF6B7280)
                        )
                        arrival.nextDepartures.take(3).forEach { dep ->
                            val label = dep.displayLabel(currentClockMs)
                            val chipBg = if (dep.isRealtime) {
                                if (isDarkTheme) Color(0x2400C853) else Color(0x1F00883E)
                            } else {
                                if (isDarkTheme) Color(0xFF22242C) else Color(0xFFE5E7EB)
                            }
                            val textColor = if (dep.isRealtime) {
                                liveGreen
                            } else {
                                if (isDarkTheme) Color(0xFFC0C2CC) else Color(0xFF374151)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(chipBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (dep.isRealtime) {
                                        Icon(
                                            imageVector = Icons.Rounded.Wifi,
                                            contentDescription = "GPS ao vivo",
                                            tint = liveGreen,
                                            modifier = Modifier.size(9.dp)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (dep.isRealtime) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        ),
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val alarmTint = if (isAlarmActive) liveGreen else (if (isDarkTheme) Color(0xFF8E92A0) else Color(0xFF6B7280))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleAlarm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAlarmActive) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                            contentDescription = if (isAlarmActive) "Desativar aviso de aproximação" else "Ativar aviso de aproximação",
                            tint = alarmTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onScheduleClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Ver todas as saídas do dia",
                            tint = if (isDarkTheme) Color(0xFF8E92A0) else Color(0xFF6B7280),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LineDailyScheduleView(
    arrival: StopArrival,
    dailySchedule: List<NextDeparture>,
    isLoading: Boolean,
    currentClockMs: Long,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveGreen = if (isDarkTheme) TransitGreen else Color(0xFF00883E)
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(arrival.colorHex))
    } catch (_: Exception) {
        if (arrival.isMetro) Color(0xFF008037) else TransitGreen
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Cabecalho da visao de horarios do dia
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar para paradas",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                val badgeShape = RoundedCornerShape(7.dp)
                Surface(
                    shape = badgeShape,
                    color = parsedColor,
                    modifier = Modifier.clip(badgeShape)
                ) {
                    Text(
                        text = arrival.lineNumber,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        ),
                        color = if (arrival.isMetro) Color.White else Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chegadas do dia",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Sentido ${arrival.destination}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                    color = liveGreen,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else if (dailySchedule.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhum horário restante encontrado para hoje",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val itemBg = if (isDarkTheme) Color(0xFF16171C) else Color(0xFFF3F4F7)
            val itemBorder = if (isDarkTheme) Color(0xFF262830) else Color(0xFFE2E4EB)
            val shape = RoundedCornerShape(12.dp)
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp + navBarBottom)
            ) {
                itemsIndexed(
                    items = dailySchedule,
                    key = { index, dep -> "${dep.timeFormatted}_${dep.targetEpochMs}_$index" }
                ) { _, dep ->
                    val displayLabel = dep.displayLabel(currentClockMs)
                    val isRealtime = dep.isRealtime

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(1.dp, itemBorder, shape),
                        color = itemBg,
                        shape = shape
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isRealtime) {
                                    Icon(
                                        imageVector = Icons.Rounded.Wifi,
                                        contentDescription = "GPS ao vivo",
                                        tint = liveGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = "Horário agendado",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = dep.timeFormatted,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (isRealtime) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDarkTheme) Color(0x2400C853) else Color(0x1F00883E))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Wifi,
                                        contentDescription = "GPS ao vivo",
                                        tint = liveGreen,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = displayLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = liveGreen
                                    )
                                }
                            } else {
                                val effectiveMins = dep.getEffectiveMinutes(currentClockMs)
                                if (effectiveMins in 0..45) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDarkTheme) Color(0xFF22242C) else Color(0xFFE5E7EB))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = displayLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isDarkTheme) Color(0xFFC0C2CC) else Color(0xFF374151)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArticulatedBusIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val frontW = w * 0.44f
        val busH = h * 0.75f
        val topY = h * 0.12f
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(0f, topY),
            size = androidx.compose.ui.geometry.Size(frontW, busH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        val bellowW = w * 0.12f
        drawRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = androidx.compose.ui.geometry.Offset(frontW, topY + busH * 0.15f),
            size = androidx.compose.ui.geometry.Size(bellowW, busH * 0.7f)
        )
        val rearX = frontW + bellowW
        val rearW = w - rearX
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(rearX, topY),
            size = androidx.compose.ui.geometry.Size(rearW, busH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}
