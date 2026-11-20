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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Wifi
import com.dfmob.ui.components.LiveWifiIcon
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
import com.dfmob.data.model.LineRouteState
import com.dfmob.data.model.TransitStop
import com.dfmob.ui.theme.TransitGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun LineItineraryDeck(
    activeRoute: LineRouteState,
    onSelectStop: (TransitStop) -> Unit,
    onSwitchDirection: () -> Unit,
    onClose: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onLocateBuses: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    maxScreenHeight: Dp = 600.dp,
    isDarkTheme: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Ticker a cada 1 segundo para recalcular previsao ao vivo contra relogio local
    var currentClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)
            currentClockMs = System.currentTimeMillis()
        }
    }

    val animSpec = tween<Dp>(durationMillis = 90, easing = FastOutSlowInEasing)
    val targetHeight = if (isExpanded) {
        if (maxScreenHeight > 200.dp) maxScreenHeight * 0.75f else 540.dp
    } else {
        340.dp
    }
    val animatedHeight by animateDpAsState(targetValue = targetHeight, animationSpec = animSpec, label = "LineDeckHeight")

    val targetCorner = if (isExpanded) 20.dp else 24.dp
    val animatedCorner by animateDpAsState(targetValue = targetCorner, animationSpec = animSpec, label = "LineDeckCorner")

    val targetPadH = if (isExpanded) 0.dp else 14.dp
    val animatedPadH by animateDpAsState(targetValue = targetPadH, animationSpec = animSpec, label = "LineDeckPadH")

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val targetPadBottom = if (isExpanded) 0.dp else 12.dp + navBarBottom
    val animatedPadBottom by animateDpAsState(targetValue = targetPadBottom, animationSpec = animSpec, label = "LineDeckPadB")

    val surfaceBg = if (isDarkTheme) Color(0xF514151B) else Color(0xF8FFFFFF)
    val borderColor = if (isDarkTheme) Color(0xFF282A33) else Color(0xFFE2E4EB)
    val handleColor = if (isDarkTheme) Color(0xFF454854) else Color(0xFFD2D5DC)
    val actionBtnBg = if (isDarkTheme) Color(0xFF22242B) else Color(0xFFF0F2F5)
    val liveGreen = if (isDarkTheme) TransitGreen else Color(0xFF00883E)
    val ribbonColor = try {
        Color(android.graphics.Color.parseColor(activeRoute.colorHex))
    } catch (_: Exception) {
        TransitGreen
    }

    val deckShape = if (isExpanded) {
        RoundedCornerShape(topStart = animatedCorner, topEnd = animatedCorner, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(animatedCorner)
    }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures { _, dragAmount ->
            if (dragAmount < -18f && !isExpanded) {
                isExpanded = true
            } else if (dragAmount > 18f && isExpanded) {
                isExpanded = false
            }
        }
    }

    val listState = rememberLazyListState()

    // Rola para a parada selecionada na abertura inicial
    LaunchedEffect(activeRoute.selectedStop?.stopId, activeRoute.currentDirectionIndex) {
        val selIndex = activeRoute.stops.indexOfFirst { it.stopId == activeRoute.selectedStop?.stopId }
        if (selIndex >= 0) {
            val scrollTarget = (selIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(scrollTarget)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = animatedPadH)
            .padding(bottom = animatedPadBottom)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .clip(deckShape)
                .border(
                    width = if (isExpanded) 0.dp else 1.dp,
                    color = if (isExpanded) Color.Transparent else borderColor,
                    shape = deckShape
                ),
            color = surfaceBg,
            shape = deckShape,
            shadowElevation = if (isDarkTheme) 16.dp else 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = if (isExpanded) 10.dp + navBarBottom else 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Barra superior para arrastar (Drag Handle)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .then(dragModifier),
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

                // 2. Cabecalho: Badge da Linha + Empresa + Botao Fechar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dragModifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val badgeShape = RoundedCornerShape(7.dp)
                        Surface(
                            shape = badgeShape,
                            color = ribbonColor,
                            modifier = Modifier.clip(badgeShape)
                        ) {
                            Text(
                                text = activeRoute.lineNumber,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp
                                ),
                                color = if (activeRoute.isMetro) Color.White else Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = activeRoute.agencyName.ifBlank { activeRoute.lineName },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Acoes do Topo: Favorito e Fechar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botao Favoritar Linha
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(actionBtnBg)
                                .clickable(onClick = onToggleFavorite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Favoritar linha",
                                tint = if (isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Botao Fechar Itinerario da Linha
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(actionBtnBg)
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Fechar rota",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 3. Destino da Linha e Chave/Botao Trocar Sentido
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dragModifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeRoute.activeDestination,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!activeRoute.circular && activeRoute.directions.size > 1) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onSwitchDirection)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Trocar sentido",
                                tint = Color(0xFF1A73E8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Trocar sentido",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF1A73E8)
                            )
                        }
                    } else if (activeRoute.circular) {
                        Text(
                            text = "Linha Circular",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = liveGreen
                        )
                    }
                }

                // 4. Fita Vertical do Itinerario com Paradas (LazyColumn)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = activeRoute.stops,
                        key = { index, stop -> "${stop.stopId}_${activeRoute.activeLineId}_$index" }
                    ) { index, stop ->
                        val isSelected = activeRoute.selectedStop?.stopId == stop.stopId
                        val isFirst = index == 0
                        val isLast = index == activeRoute.stops.lastIndex

                        ItineraryStopItem(
                            stop = stop,
                            isSelected = isSelected,
                            isFirst = isFirst,
                            isLast = isLast,
                            ribbonColor = ribbonColor,
                            arrival = if (isSelected) activeRoute.selectedStopArrival else null,
                            currentClockMs = currentClockMs,
                            liveGreen = liveGreen,
                            isDarkTheme = isDarkTheme,
                            onClick = { onSelectStop(stop) }
                        )
                    }
                }


            }
        }
    }
}

@Composable
private fun ItineraryStopItem(
    stop: TransitStop,
    isSelected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    ribbonColor: Color,
    arrival: com.dfmob.data.model.StopArrival?,
    currentClockMs: Long,
    liveGreen: Color,
    isDarkTheme: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    onClick: () -> Unit
) {
    val itemBg = if (isDarkTheme) Color(0xFF1E2028) else Color(0xFFF3F5F9)
    val itemBorder = if (isDarkTheme) Color(0xFF2C2F3A) else Color(0xFFE2E5EC)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (isSelected) 4.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Coluna da Fita Vertical com os nós (Linha contínua + nós circulares)
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(if (isSelected) 58.dp else 34.dp),
            contentAlignment = Alignment.Center
        ) {
            // Linha vertical conectora
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxSize()
                    .padding(top = if (isFirst) 14.dp else 0.dp, bottom = if (isLast) 14.dp else 0.dp)
                    .background(ribbonColor)
            )

            // Ponto / Nó da parada
            if (isSelected) {
                // Nó expandido com anel colorido e centro branco
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(ribbonColor),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            } else {
                // Nó discreto na linha
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color(0xFF424552) else Color(0xFFB0B4C0))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Conteudo da Parada: apenas a parada selecionada exibe cartao de previsao
        if (isSelected) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, itemBorder, RoundedCornerShape(12.dp)),
                color = itemBg,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stop.stopName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (arrival != null) {
                            val etaText = arrival.getDisplayEta(currentClockMs)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (arrival.isRealtime) {
                                    LiveWifiIcon(
                                        size = 13.dp,
                                        tint = liveGreen
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = "Programado",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = etaText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    ),
                                    color = if (arrival.isRealtime) liveGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = liveGreen
                            )
                        }
                    }

                    // Linha inferior: Proximas partidas estilizadas com indicador de GPS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (arrival != null && arrival.nextDepartures.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Seguintes:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
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
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            if (dep.isRealtime) {
                                                LiveWifiIcon(
                                                    size = 9.dp,
                                                    tint = liveGreen
                                                )
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (dep.isRealtime) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 10.sp
                                                ),
                                                color = textColor,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val subText = stop.stopCode?.let { "Código $it" } ?: "Ponto oficial SEMOB"
                            Text(
                                text = subText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Accessible,
                            contentDescription = "Acessibilidade",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        } else {
            // Parada nao selecionada: exibe apenas o nome limpo e alinhado
            Text(
                text = stop.stopName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
