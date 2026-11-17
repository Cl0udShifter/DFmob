package com.dfmob.ui.lines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.TransitLine
import com.dfmob.data.model.TransitModeFilter
import com.dfmob.data.repository.FavoritesRepository
import com.dfmob.data.repository.LinesRepository
import com.dfmob.ui.lines.components.LineCardItem
import com.dfmob.ui.lines.components.ModeSelector
import com.dfmob.ui.theme.DFmobTheme
import com.dfmob.ui.theme.TransitGreen

@Composable
fun LinesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    favoritesRepository: FavoritesRepository? = null,
    onLineClick: (TransitLine) -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val favoritesRepo = favoritesRepository ?: remember { FavoritesRepository(context) }
    val focusManager = LocalFocusManager.current
    val repository = remember { LinesRepository(context) }

    var allLines by remember { mutableStateOf<List<TransitLine>>(emptyList()) }
    val favoriteLines by favoritesRepo.favoriteLines.collectAsState()
    val favoriteIds by remember {
        derivedStateOf { favoriteLines.map { it.routeId }.toSet() }
    }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(TransitModeFilter.BUS) }

    LaunchedEffect(Unit) {
        val lines = repository.loadLines()
        allLines = lines
        favoritesRepo.syncWithAllLines(lines)
        isLoading = false
    }

    val filteredLines by remember {
        derivedStateOf {
            repository.searchLines(allLines, selectedMode, searchQuery)
        }
    }

    val busCount by remember {
        derivedStateOf {
            allLines.count { it.isBus }
        }
    }

    val metroCount by remember {
        derivedStateOf {
            allLines.count { it.isMetro }
        }
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
            // Top Bar com botao voltar
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

                Text(
                    text = "Linhas",
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

            // Barra de busca interativa
            val searchShape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(searchShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, searchShape),
                color = MaterialTheme.colorScheme.surface,
                shape = searchShape
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = TransitGreen,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Número ou nome da linha (ex: 0.108)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(TransitGreen),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Limpar busca",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            // Chave seletora Onibus / Metro
            ModeSelector(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
                busCount = busCount,
                metroCount = metroCount
            )

            // Area de resultados
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TransitGreen,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (filteredLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma linha encontrada para sua busca",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = filteredLines,
                        key = { it.routeId }
                    ) { line ->
                        LineCardItem(
                            line = line,
                            isFavorite = favoriteIds.contains(line.routeId),
                            onToggleFavorite = {
                                favoritesRepo.toggleFavorite(line)
                            },
                            onClick = {
                                onLineClick(line)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Lines Screen Preview", showBackground = true)
@Composable
fun LinesScreenPreview() {
    DFmobTheme(darkTheme = true) {
        LinesScreen(onBack = {})
    }
}
