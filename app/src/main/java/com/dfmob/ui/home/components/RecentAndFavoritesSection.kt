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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
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
import com.dfmob.data.model.TransitLine
import com.dfmob.data.model.TransitStop

@Composable
fun RecentAndFavoritesSection(
    favoriteLines: List<TransitLine> = emptyList(),
    favoriteStops: List<TransitStop> = emptyList(),
    onLineClick: (TransitLine) -> Unit = {},
    onStopClick: (TransitStop) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Modulo de Ultimas Pesquisas (texto limpo)
        CleanTextBlock(
            header = "Últimas pesquisas",
            message = "Nenhuma busca recente encontrada no dispositivo"
        )

        // Modulo de Favoritos (linhas e paradas com diferenciacao visual)
        FavoritesCard(
            favoriteLines = favoriteLines,
            favoriteStops = favoriteStops,
            onLineClick = onLineClick,
            onStopClick = onStopClick
        )
    }
}

@Composable
private fun FavoritesCard(
    favoriteLines: List<TransitLine>,
    favoriteStops: List<TransitStop>,
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
                        FavoriteLineMiniRow(
                            line = line,
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

                Text(
                    text = line.agencyName,
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
                contentDescription = "Favorita",
                tint = Color(0xFFEAB308),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CleanTextBlock(
    header: String,
    message: String,
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
                text = header.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
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
