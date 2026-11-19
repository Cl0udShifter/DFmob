package com.dfmob.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.service.NavigationStateHolder
import com.dfmob.ui.theme.TransitGreen

@Composable
fun ActiveTripModule(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNavigating by NavigationStateHolder.isNavigating.collectAsState()
    val activeItin = NavigationStateHolder.activeItinerary

    AnimatedVisibility(
        visible = isNavigating && activeItin != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val stageTitle by NavigationStateHolder.currentStageTitleFlow.collectAsState()
        val lineNumber by NavigationStateHolder.currentLineNumberFlow.collectAsState()
        val lineColorHex by NavigationStateHolder.currentLineColorHexFlow.collectAsState()
        val remainingStops by NavigationStateHolder.currentRemainingStops.collectAsState()
        val targetStopName = NavigationStateHolder.targetStopName
        val isRideTransit = NavigationStateHolder.isRideTransit

        val lineColor = try {
            Color(android.graphics.Color.parseColor(lineColorHex))
        } catch (_: Exception) {
            TransitGreen
        }

        val interactionSource = remember { MutableInteractionSource() }
        val shape = RoundedCornerShape(18.dp)

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .border(
                    width = 1.2.dp,
                    color = TransitGreen.copy(alpha = 0.6f),
                    shape = shape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = true),
                    onClick = onClick
                ),
            color = MaterialTheme.colorScheme.surface,
            shape = shape,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Linha superior: Tag Em Viagem com ponto verde e seta de acao
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TransitGreen.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(TransitGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val headerLabel = if (stageTitle.isNotBlank()) {
                            "EM VIAGEM • ${stageTitle.uppercase()}"
                        } else {
                            "EM VIAGEM"
                        }
                        Text(
                            text = headerLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            ),
                            color = TransitGreen
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Continuar viagem",
                        tint = TransitGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Linha central: Linha / Icone e Destino / Parada
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRideTransit && lineNumber.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = lineColor,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = lineNumber,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (isRideTransit) {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TransitGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsBus,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val destinationText = if (targetStopName.isNotBlank()) {
                            targetStopName
                        } else {
                            NavigationStateHolder.activeDestination?.caption ?: "Em andamento"
                        }
                        Text(
                            text = destinationText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val statusText = if (isRideTransit) {
                            when {
                                remainingStops <= 0 -> "Desembarque agora!"
                                remainingStops == 1 -> "Desça na próxima parada!"
                                else -> "$remainingStops paradas até a descida"
                            }
                        } else {
                            "Toque para abrir os detalhes e mapa da viagem"
                        }
                        val statusColor = if (isRideTransit && remainingStops <= 1) {
                            TransitGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isRideTransit && remainingStops <= 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.5.sp
                            ),
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}
