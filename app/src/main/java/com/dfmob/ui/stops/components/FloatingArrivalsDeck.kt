package com.dfmob.ui.stops.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.StopArrival
import com.dfmob.data.model.TransitStop
import com.dfmob.ui.theme.TransitGreen

import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun FloatingArrivalsDeck(
    stop: TransitStop,
    arrivals: List<StopArrival>,
    isLoading: Boolean,
    onClose: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    maxScreenHeight: Dp = 600.dp,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()

    // Estado expandido (75% da tela sem margens de floating pill) vs colapsado (floating pill padrao)
    var isExpanded by remember { mutableStateOf(false) }

    // Reseta para colapsado ao alternar a parada selecionada
    LaunchedEffect(stop.stopId) {
        isExpanded = false
    }

    // Ticker a cada 1 segundo para atualizar minutos e texto 'Já' dinamicamente contra o relogio do sistema
    var currentClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)
            currentClockMs = System.currentTimeMillis()
        }
    }

    // Animacao simples e rapida (250ms com FastOutSlowInEasing)
    val animSpec = tween<Dp>(durationMillis = 250, easing = FastOutSlowInEasing)

    val targetHeight = if (isExpanded) {
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
        targetValue = if (isExpanded) 0.dp else 14.dp,
        animationSpec = animSpec,
        label = "deckPaddingH"
    )

    val animatedBottomPadding by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 12.dp,
        animationSpec = animSpec,
        label = "deckPaddingB"
    )

    val animatedBottomRadius by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 24.dp,
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
                    isExpanded = false
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
                width = if (isExpanded) 0.dp else 1.dp,
                color = if (isExpanded) Color.Transparent else deckBorder,
                shape = deckShape
            )
            .then(if (!isExpanded) dragModifier else Modifier),
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
                    bottom = if (isExpanded) 16.dp else 10.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag Handle Bar indicador no topo (arrastavel e clicavel)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
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
            } else if (arrivals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma linha em operacao no momento",
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
                        items = arrivals,
                        key = { index, it -> "${it.lineNumber}_${it.destination}_${it.minutesAway}_${it.scheduledTimeFormatted}_$index" }
                    ) { _, arrival ->
                        StopArrivalLineCard(
                            arrival = arrival,
                            currentClockMs = currentClockMs,
                            isDarkTheme = isDarkTheme
                        )
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
            .clickable { /* Acao silenciosa conforme especificacao */ },
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
                    Text(
                        text = arrival.lineName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

            // Proximos horarios da linha (com suporte a GPS ao vivo e "Já" nas partidas seguintes)
            if (arrival.nextDepartures.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
            }
        }
    }
}
