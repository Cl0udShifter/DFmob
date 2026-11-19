package com.dfmob.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.FavoriteLineArrivalPrediction
import com.dfmob.data.model.TransitLine
import com.dfmob.data.model.TransitStop
import com.dfmob.data.repository.FavoritesRepository
import com.dfmob.data.repository.RecentSearchItem
import com.dfmob.ui.theme.TransitGreen

@Composable
fun RecentAndFavoritesSection(
    recentSearches: List<RecentSearchItem> = emptyList(),
    favoriteLines: List<TransitLine> = emptyList(),
    favoriteStops: List<TransitStop> = emptyList(),
    favoritePredictions: Map<String, FavoriteLineArrivalPrediction> = emptyMap(),
    onRecentSearchClick: (DFLocation) -> Unit = {},
    onTogglePin: (RecentSearchItem) -> Unit = {},
    onClearRecent: () -> Unit = {},
    onLineClick: (TransitLine) -> Unit = {},
    onStopClick: (TransitStop) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Modulo de Ultimas Pesquisas com suporte a fixacao e limpar
        RecentSearchesCard(
            recentSearches = recentSearches,
            onRecentSearchClick = onRecentSearchClick,
            onTogglePin = onTogglePin,
            onClearRecent = onClearRecent
        )

        // Modulo de Favoritos (linhas e paradas com diferenciacao visual)
        FavoritesCard(
            favoriteLines = favoriteLines,
            favoriteStops = favoriteStops,
            favoritePredictions = favoritePredictions,
            onLineClick = onLineClick,
            onStopClick = onStopClick
        )
    }
}

@Composable
private fun FavoritesCard(
    favoriteLines: List<TransitLine>,
    favoriteStops: List<TransitStop>,
    favoritePredictions: Map<String, FavoriteLineArrivalPrediction>,
    onLineClick: (TransitLine) -> Unit,
    onStopClick: (TransitStop) -> Unit,
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
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "FAVORITOS",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (favoriteLines.isEmpty() && favoriteStops.isEmpty()) {
                Text(
                    text = "Nenhum favorito salvo ainda",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    favoriteStops.forEach { stop ->
                        FavoriteStopMiniRow(
                            stop = stop,
                            onClick = { onStopClick(stop) }
                        )
                    }
                    favoriteLines.forEach { line ->
                        val key = FavoritesRepository.getLineKey(line)
                        FavoriteLineMiniRow(
                            line = line,
                            prediction = favoritePredictions[key],
                            onClick = { onLineClick(line) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteStopMiniRow(
    stop: TransitStop,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(12.dp)
    val badgeShape = RoundedCornerShape(8.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val isMetro = stop.isMetro
    val badgeBg = if (isMetro) Color(0xFF008037) else Color(0xFF00883E)
    val badgeText = Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = rowShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = rowShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 54.dp, minHeight = 32.dp)
                    .clip(badgeShape)
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isMetro) "METRÔ" else (stop.stopCode ?: "PONTO"),
                    color = badgeText,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stop.stopName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val sub = if (isMetro) {
                    "Metrô-DF • Estação Oficial"
                } else {
                    "Parada de Ônibus • ${stop.stopCode ?: "Ponto Oficial SEMOB"}"
                }

                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "Favorito",
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FavoriteLineMiniRow(
    line: TransitLine,
    prediction: FavoriteLineArrivalPrediction? = null,
    onClick: () -> Unit = {}
) {
    val rowShape = RoundedCornerShape(12.dp)
    val badgeShape = RoundedCornerShape(8.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val badgeBg = remember(line.colorHex) {
        parseColorSafely(line.colorHex, Color(0xFF00D26A))
    }
    val badgeText = remember(line.textColorHex) {
        parseColorSafely(line.textColorHex, Color.White)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = rowShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = rowShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 54.dp, minHeight = 32.dp)
                    .clip(badgeShape)
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = line.lineNumber,
                    color = badgeText,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = line.routeName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subText = if (!line.destinationName.isNullOrBlank()) {
                    "${line.agencyName} • Sentido ${line.destinationName}"
                } else if (line.directionId != null && !line.isCircular) {
                    val dir = if (line.directionId == 0) "Ida" else "Volta"
                    "${line.agencyName} • Sentido $dir"
                } else {
                    line.agencyName
                }

                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (prediction != null) {
                Spacer(modifier = Modifier.width(8.dp))
                val isRealtime = prediction.arrival.isRealtime
                val chipBg = if (isRealtime) Color(0x2400D26A) else MaterialTheme.colorScheme.surface
                val chipContentColor = if (isRealtime) TransitGreen else MaterialTheme.colorScheme.onSurfaceVariant
                val chipBorderColor = if (isRealtime) TransitGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipBg)
                        .border(1.dp, chipBorderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isRealtime) Icons.Rounded.Wifi else Icons.Rounded.Schedule,
                        contentDescription = if (isRealtime) "GPS ao vivo" else "Programado",
                        tint = chipContentColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = prediction.arrival.getDisplayEta(),
                        color = chipContentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "Favorita",
                tint = Color(0xFFEAB308),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RecentSearchesCard(
    recentSearches: List<RecentSearchItem>,
    onRecentSearchClick: (DFLocation) -> Unit,
    onTogglePin: (RecentSearchItem) -> Unit,
    onClearRecent: () -> Unit,
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
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ÚLTIMAS PESQUISAS",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (recentSearches.any { !it.isPinned }) {
                    Text(
                        text = "Limpar",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = TransitGreen,
                        modifier = Modifier
                            .clickable(onClick = onClearRecent)
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    )
                }
            }

            if (recentSearches.isEmpty()) {
                Text(
                    text = "Nenhuma busca recente encontrada no dispositivo",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    recentSearches.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRecentSearchClick(item.toDFLocation()) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onTogglePin(item) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = if (item.isPinned) "Desafixar" else "Fixar no topo",
                                    tint = if (item.isPinned) TransitGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (item.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

private fun parseColorSafely(hex: String, defaultColor: Color): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> ("FF$clean").toLong(16).toInt()
            8 -> clean.toLong(16).toInt()
            3 -> {
                val r = clean[0]
                val g = clean[1]
                val b = clean[2]
                ("FF$r$r$g$g$b$b").toLong(16).toInt()
            }
            else -> return defaultColor
        }
        Color(colorInt)
    } catch (_: Exception) {
        defaultColor
    }
}
