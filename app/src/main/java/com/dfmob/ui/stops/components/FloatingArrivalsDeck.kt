package com.dfmob.ui.stops.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.StopArrival
import com.dfmob.data.model.TransitStop
import com.dfmob.ui.theme.TransitGreen

@Composable
fun FloatingArrivalsDeck(
    stop: TransitStop,
    arrivals: List<StopArrival>,
    isLoading: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val deckShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp)

    val deckBackground = if (isDarkTheme) Color(0xF2141519) else Color(0xF8FFFFFF)
    val deckBorder = if (isDarkTheme) Color(0xFF282A33) else Color(0xFFE2E4EB)
    val closeBtnBg = if (isDarkTheme) Color(0xFF23252C) else Color(0xFFEAECEF)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(deckShape)
            .border(1.dp, deckBorder, deckShape),
        color = deckBackground,
        shape = deckShape,
        shadowElevation = if (isDarkTheme) 18.dp else 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cabecalho da Parada (Padrao Oficial Moovit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val codeText = if (!stop.stopCode.isNullOrBlank()) {
                            "Código ${stop.stopCode}"
                        } else {
                            "Código ${stop.stopId}"
                        }
                        Text(
                            text = codeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Accessible,
                            contentDescription = "Acessivel para cadeirantes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
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

            // Subtitulo de secao Moovit: Proximas chegadas
            Column(modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)) {
                Text(
                    text = "Próximas chegadas",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Próximas chegadas de linhas para esta estação",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Lista de Linhas ou Indicador de Carga
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
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
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma linha em operação no momento",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 2.dp)
                ) {
                    items(
                        items = arrivals,
                        key = { "${it.lineNumber}_${it.destination}_${it.isMetro}" }
                    ) { arrival ->
                        StopArrivalLineCard(
                            arrival = arrival,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }

            // Barra inferior com botoes de acao Moovit: Localizacao em tempo real e Favorito
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botao azul "Localizacao em tempo real"
                val blueBg = Color(0xFF1976D2)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .clickable { /* Abre rastreamento ao vivo */ },
                    color = blueBg,
                    shape = RoundedCornerShape(19.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsBus,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Localização em tempo real",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                // Botao Favorito
                var isFavoriteStop by remember(stop.stopId) { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .border(1.dp, deckBorder, RoundedCornerShape(19.dp))
                        .clickable { isFavoriteStop = !isFavoriteStop },
                    color = if (isDarkTheme) Color(0xFF1E2028) else Color(0xFFF0F2F5),
                    shape = RoundedCornerShape(19.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isFavoriteStop) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavoriteStop) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Favorito",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
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
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(12.dp)
    val cardBg = if (isDarkTheme) Color(0xFF181920) else Color(0xFFFAFAFB)
    val cardBorder = if (isDarkTheme) Color(0xFF272932) else Color(0xFFE5E7EB)

    val liveGreen = if (isDarkTheme) Color(0xFF10B981) else Color(0xFF059669)
    val liveGreenBg = if (isDarkTheme) Color(0x1A10B981) else Color(0x14059669)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, cardBorder, cardShape)
            .clickable { /* Acao silenciosa */ },
        color = cardBg,
        shape = cardShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Badge do número da linha com ícone de ônibus ou metrô (Padrao Moovit)
            val badgeBorder = if (isDarkTheme) Color(0xFF3F424E) else Color(0xFFD1D5DB)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isDarkTheme) Color(0xFF22242D) else Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, badgeBorder, RoundedCornerShape(6.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (arrival.isMetro) Icons.Rounded.Subway else Icons.Rounded.DirectionsBus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = arrival.lineNumber,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. Destino e Subtexto (Moovit: "A hora de chegada e pontual" ou "Baseado em chegadas anteriores")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = arrival.destination,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                val subtext = if (arrival.isRealtime) {
                    "A hora de chegada é pontual"
                } else {
                    "Baseado em chegadas anteriores"
                }
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (arrival.isRealtime) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (arrival.isRealtime) liveGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Coluna de ETA e Próximos horários
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (arrival.isRealtime) {
                    // Badge Verde com Ícone de Sinal Wi-Fi / GPS
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = liveGreenBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(0.8.dp, liveGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = "GPS ao vivo",
                                tint = liveGreen,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${arrival.minutesAway} min",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = liveGreen
                            )
                        }
                    }
                } else {
                    // Badge com Ícone de Relógio e Horário Programado
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Horário agendado",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        val etaDisplay = if (arrival.minutesAway <= 60) {
                            "${arrival.minutesAway} min"
                        } else {
                            arrival.scheduledTimeFormatted
                        }
                        Text(
                            text = etaDisplay,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Proximas partidas (Seguintes)
                if (arrival.nextDepartures.isNotEmpty()) {
                    Text(
                        text = arrival.nextDepartures.take(2).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
