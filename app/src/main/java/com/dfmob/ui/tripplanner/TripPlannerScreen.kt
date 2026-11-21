package com.dfmob.ui.tripplanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLeg
import com.dfmob.data.model.TripPlanLocation
import com.dfmob.data.model.TripPreference
import com.dfmob.data.model.TripTimeMode
import com.dfmob.data.repository.FavoritesRepository
import com.dfmob.data.repository.RecentSearchesRepository
import com.dfmob.data.repository.StopsRepository
import com.dfmob.ui.components.CrowdingBadge
import com.dfmob.ui.components.LiveWifiIcon
import com.dfmob.ui.components.TripInsightBadge
import com.dfmob.ui.theme.TransitGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlannerScreen(
    onBack: () -> Unit,
    initialDestination: DFLocation? = null,
    initialOriginLocation: TripPlanLocation? = null,
    initialDestinationLocation: TripPlanLocation? = null,
    resumeActiveTrip: Boolean = false,
    onActiveTripResumed: () -> Unit = {},
    favoritesRepository: FavoritesRepository? = null,
    recentSearchesRepository: RecentSearchesRepository? = null,
    repository: StopsRepository = StopsRepository(LocalContext.current)
) {
    val context = LocalContext.current
    val recentRepo = recentSearchesRepository ?: remember { RecentSearchesRepository(context) }
    val favoritesRepo = favoritesRepository ?: remember { FavoritesRepository(context) }
    val viewModel = remember { TripPlannerViewModel(repository, context) }
    val state by viewModel.uiState.collectAsState()
    val isNavigating by com.dfmob.service.NavigationStateHolder.isNavigating.collectAsState()
    val focusManager = LocalFocusManager.current

    val favoriteTrips by favoritesRepo.favoriteTrips.collectAsState()
    val isCurrentTripFavorite = remember(state.origin, state.destination, favoriteTrips) {
        favoritesRepo.isTripFavorite(state.origin, state.destination)
    }
    var showSaveTripDialog by remember { mutableStateOf(false) }
    var tripCustomNameInput by remember { mutableStateOf("") }
    var showComparisonMap by remember { mutableStateOf(false) }
    var isTimePickerSheetOpen by remember { mutableStateOf(false) }
    val timePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPreferencesSheetOpen by remember { mutableStateOf(false) }
    val preferencesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(context) {
        viewModel.refreshUserLocation()
        onDispose {
            viewModel.stopListeningLocation()
        }
    }

    LaunchedEffect(resumeActiveTrip) {
        if (resumeActiveTrip) {
            val active = com.dfmob.service.NavigationStateHolder.activeItinerary
            if (active != null) {
                viewModel.selectItinerary(active)
            }
            onActiveTripResumed()
        }
    }

    LaunchedEffect(initialOriginLocation, initialDestinationLocation, initialDestination) {
        if (initialOriginLocation != null && initialDestinationLocation != null) {
            viewModel.setOriginAndDestination(initialOriginLocation, initialDestinationLocation)
        } else if (initialDestination != null) {
            viewModel.setDestination(initialDestination)
            recentRepo.addSearch(initialDestination)
        }
    }

    // 1. Tela do Seletor de Mapa em tela cheia se estiver ativa
    if (state.isPickingOnMap) {
        val initialLat = if (state.mapPickerTargetField == ActiveField.ORIGIN) state.origin?.latE6 else state.destination?.latE6
        val initialLon = if (state.mapPickerTargetField == ActiveField.ORIGIN) state.origin?.lonE6 else state.destination?.lonE6

        MapLocationPicker(
            initialLatE6 = initialLat,
            initialLonE6 = initialLon,
            onCancel = { viewModel.cancelMapPicker() },
            onConfirm = { latE6, lonE6, caption ->
                viewModel.confirmMapPicker(latE6, lonE6, caption)
                recentRepo.addSearch(DFLocation(caption, "", latE6, lonE6))
            },
            onReverseGeocode = { latE6, lonE6 ->
                viewModel.reverseGeocode(latE6, lonE6)
            }
        )
        return
    }

    // 2.1 Tela de Comparação de Rotas no Mapa se estiver ativa
    if (showComparisonMap && state.itineraries.size >= 2) {
        TripComparisonMapScreen(
            itineraries = state.itineraries.sortedBy { it.totalDurationMinutes },
            origin = state.origin,
            destination = state.destination,
            onBack = { showComparisonMap = false },
            onSelectItinerary = { itin ->
                showComparisonMap = false
                viewModel.selectItinerary(itin)
            }
        )
        return
    }

    // 2. Tela de Detalhes do Trajeto Selecionado se estiver ativa
    val selectedItin = state.selectedItinerary ?: (if (isNavigating) com.dfmob.service.NavigationStateHolder.activeItinerary else null)
    if (selectedItin != null) {
        TripItineraryDetailView(
            itinerary = selectedItin,
            origin = state.origin ?: com.dfmob.service.NavigationStateHolder.activeOrigin,
            destination = state.destination ?: com.dfmob.service.NavigationStateHolder.activeDestination,
            onBack = {
                viewModel.selectItinerary(null)
            },
            onFinishTrip = {
                viewModel.selectItinerary(null)
                com.dfmob.service.NavigationStateHolder.reset(context)
                onBack()
            }
        )
        return
    }

    // 3. Layout Principal Variante 1 - Transit Puro
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header com botao de voltar e titulo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Planejar Viagem",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = ".",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    ),
                    color = TransitGreen
                )

                Spacer(modifier = Modifier.weight(1f))

                // Botao de favoritar trajeto (quando origem e destino definidos)
                if (state.origin != null && state.destination != null) {
                    IconButton(
                        onClick = {
                            if (isCurrentTripFavorite) {
                                val fav = favoritesRepo.getFavoriteTrip(state.origin, state.destination)
                                if (fav != null) {
                                    favoritesRepo.removeFavoriteTrip(fav.id)
                                    Toast.makeText(context, "Trajeto removido dos favoritos", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                tripCustomNameInput = state.destination?.caption ?: "Meu Trajeto"
                                showSaveTripDialog = true
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isCurrentTripFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (isCurrentTripFavorite) "Remover trajeto dos favoritos" else "Salvar trajeto favorito",
                            tint = if (isCurrentTripFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showSaveTripDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveTripDialog = false },
                    title = {
                        Text(
                            text = "Salvar trajeto favorito",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Escolha um nome para identificar este trajeto rapidamente:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Chips de sugestao rapida
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Trabalho", "Faculdade", "Casa", "Academia").forEach { preset ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (tripCustomNameInput == preset) TransitGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { tripCustomNameInput = preset }
                                    ) {
                                        Text(
                                            text = preset,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (tripCustomNameInput == preset) TransitGreen else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = tripCustomNameInput,
                                onValueChange = { tripCustomNameInput = it },
                                label = { Text("Nome do trajeto") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val name = tripCustomNameInput.trim().ifEmpty { state.destination?.caption ?: "Meu Trajeto" }
                                val orig = state.origin
                                val dest = state.destination
                                if (orig != null && dest != null) {
                                    favoritesRepo.addFavoriteTrip(name, orig, dest)
                                    Toast.makeText(context, "$name salvo nos favoritos", Toast.LENGTH_SHORT).show()
                                }
                                showSaveTripDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TransitGreen)
                        ) {
                            Text("Salvar", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveTripDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (isTimePickerSheetOpen) {
                TripTimePickerBottomSheet(
                    sheetState = timePickerSheetState,
                    initialMode = state.timeMode,
                    initialTimeUtc = state.targetTimeUtc,
                    onDismissRequest = { isTimePickerSheetOpen = false },
                    onConfirm = { mode, targetTimeUtc ->
                        isTimePickerSheetOpen = false
                        viewModel.setTimeModeAndDateTime(mode, targetTimeUtc)
                    }
                )
            }

            if (isPreferencesSheetOpen) {
                TripPreferencesBottomSheet(
                    sheetState = preferencesSheetState,
                    currentPreference = state.preference,
                    allowBus = state.allowBus,
                    allowSubway = state.allowSubway,
                    onDismissRequest = { isPreferencesSheetOpen = false },
                    onApply = { pref, bus, sub ->
                        isPreferencesSheetOpen = false
                        viewModel.setPreference(pref)
                        viewModel.setModalFilters(bus, sub)
                    },
                    onReset = {
                        isPreferencesSheetOpen = false
                        viewModel.resetPreferences()
                    }
                )
            }

            // MODULO 1: Origem e Destino com conexao vertical
            OriginDestinationCard(
                originText = state.originQuery,
                destinationText = state.destinationQuery,
                onOriginChange = { viewModel.onOriginQueryChanged(it) },
                onDestinationChange = { viewModel.onDestinationQueryChanged(it) },
                onFieldFocus = { viewModel.onFieldFocused(it) },
                onFieldBlur = { viewModel.onFieldBlurred(it) },
                onSwap = { viewModel.swapOriginAndDestination() },
                onUseCurrentLocation = { viewModel.useCurrentLocationForActiveField() },
                onPickOnMap = {
                    viewModel.startMapPicker(state.activeField)
                }
            )

            // Dropdown de autocomplete com paradas, estacoes e enderecos
            if (state.searchResults.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((state.searchResults.size * 56).coerceAtMost(220).dp)
                    ) {
                        items(state.searchResults) { loc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectLocationResult(loc)
                                        recentRepo.addSearch(loc)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Place,
                                    contentDescription = null,
                                    tint = TransitGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = loc.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = loc.description,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
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

            Spacer(modifier = Modifier.height(12.dp))

            // MODULO 2: Filtros de Controle de Tempo e Preferências
            TimeAndPreferencesRow(
                timeMode = state.timeMode,
                targetTimeUtc = state.targetTimeUtc,
                preference = state.preference,
                allowBus = state.allowBus,
                allowSubway = state.allowSubway,
                onOpenTimePicker = { isTimePickerSheetOpen = true },
                onOpenPreferences = { isPreferencesSheetOpen = true },
                onAddMinutes = { viewModel.addTimeMinutes(15) },
                onSubtractMinutes = { viewModel.addTimeMinutes(-15) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // MODULO 3: Resultados do Planejamento
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    state.isSearchingTrips -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = TransitGreen,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Buscando melhores rotas com Moovit...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    state.tripSearchError != null && state.itineraries.isEmpty() -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = state.tripSearchError ?: "Nenhum trajeto encontrado.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (!state.allowBus || !state.allowSubway) {
                                        "Nenhum trajeto encontrado para os transportes selecionados."
                                    } else {
                                        "Tente ajustar o horário de partida ou os filtros de caminhada."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (!state.allowBus || !state.allowSubway) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.setModalFilters(allowBus = true, allowSubway = true) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TransitGreen)
                                    ) {
                                        Text(
                                            text = "Buscar com todos os transportes",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    state.itineraries.isNotEmpty() -> {
                        val sortedItineraries = state.itineraries
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Melhores rotas encontradas (${state.itineraries.size})",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (state.itineraries.size >= 2) {
                                        Surface(
                                            onClick = { showComparisonMap = true },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.AltRoute,
                                                    contentDescription = "Comparar no mapa",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "Comparar no mapa",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.5.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Ordenado do que leva menos tempo ate o que leva mais tempo (com key e contentType para reciclagem suave)
                            items(
                                items = sortedItineraries,
                                key = { it.guid },
                                contentType = { "itinerary_card" }
                            ) { itinerary ->
                                TripItineraryCard(
                                    itinerary = itinerary,
                                    onClick = { viewModel.selectItinerary(itinerary) }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    else -> {
                        // Estado inicial convidando o usuario a definir os pontos
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.DirectionsBus,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Defina Origem e Destino para traçar a rota",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginDestinationCard(
    originText: String,
    destinationText: String,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onFieldFocus: (ActiveField) -> Unit,
    onFieldBlur: (ActiveField) -> Unit,
    onSwap: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onPickOnMap: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    var isOriginFocused by remember { mutableStateOf(false) }
    var isDestFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coluna da esquerda com os icones e linha conectora
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(TransitGreen)
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Coluna central com os dois inputs de texto
                Column(modifier = Modifier.weight(1f)) {
                    // Campo Origem
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (originText.isEmpty()) {
                                Text(
                                    text = "De onde? (Origem)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = originText,
                                onValueChange = onOriginChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && !isOriginFocused) {
                                            isOriginFocused = true
                                            onFieldFocus(ActiveField.ORIGIN)
                                        } else if (!focusState.isFocused && isOriginFocused) {
                                            isOriginFocused = false
                                            onFieldBlur(ActiveField.ORIGIN)
                                        }
                                    },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(TransitGreen)
                            )
                        }
                        if (originText.isNotEmpty()) {
                            IconButton(
                                onClick = { onOriginChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Limpar origem",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Campo Destino
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (destinationText.isEmpty()) {
                                Text(
                                    text = "Para onde? (Destino)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = destinationText,
                                onValueChange = onDestinationChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && !isDestFocused) {
                                            isDestFocused = true
                                            onFieldFocus(ActiveField.DESTINATION)
                                        } else if (!focusState.isFocused && isDestFocused) {
                                            isDestFocused = false
                                            onFieldBlur(ActiveField.DESTINATION)
                                        }
                                    },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(TransitGreen)
                            )
                        }
                        if (destinationText.isNotEmpty()) {
                            IconButton(
                                onClick = { onDestinationChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Limpar destino",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Botao Inverter
                IconButton(
                    onClick = onSwap,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "Inverter Origem e Destino",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botoes de acao rapida: Minha Localizacao e Escolher no Mapa
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onUseCurrentLocation,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            tint = TransitGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Minha localização",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    onClick = onPickOnMap,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Escolher no mapa",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
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
private fun TimeAndPreferencesRow(
    timeMode: TripTimeMode,
    targetTimeUtc: Long,
    preference: TripPreference,
    allowBus: Boolean,
    allowSubway: Boolean,
    onOpenTimePicker: () -> Unit,
    onOpenPreferences: () -> Unit,
    onAddMinutes: () -> Unit,
    onSubtractMinutes: () -> Unit
) {
    val now = System.currentTimeMillis()
    val canSubtract = targetTimeUtc >= now + (15 * 60 * 1000L)
    val hasCustomFilters = preference != TripPreference.FASTEST || !allowBus || !allowSubway

    val timeLabel = remember(timeMode, targetTimeUtc) {
        if (timeMode == TripTimeMode.DEPART_NOW) {
            "Sair agora"
        } else {
            val calTarget = Calendar.getInstance().apply { timeInMillis = targetTimeUtc }
            val calNow = Calendar.getInstance()
            val sameYear = calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR)
            val dayTarget = calTarget.get(Calendar.DAY_OF_YEAR)
            val dayNow = calNow.get(Calendar.DAY_OF_YEAR)
            val timeStr = formatEpochMsToHHMM(targetTimeUtc)
            val dateSuffix = when {
                sameYear && dayTarget == dayNow -> ""
                sameYear && dayTarget == dayNow + 1 -> " (Amanhã)"
                else -> {
                    val df = SimpleDateFormat("dd/MM", Locale.getDefault())
                    " (${df.format(Date(targetTimeUtc))})"
                }
            }
            when (timeMode) {
                TripTimeMode.DEPART_NOW -> "Sair agora"
                TripTimeMode.DEPART_AT -> "Partida $timeStr$dateSuffix"
                TripTimeMode.ARRIVE_BY -> "Chegada $timeStr$dateSuffix"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Seletor de Modo de Horário (Abre BottomSheet Material 3)
        Surface(
            onClick = onOpenTimePicker,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (timeMode != TripTimeMode.DEPART_NOW) TransitGreen else Color.Transparent
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = if (timeMode != TripTimeMode.DEPART_NOW) TransitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 2. Botão Filtros e Preferências (Abre BottomSheet com modais e critérios)
        Surface(
            onClick = onOpenPreferences,
            shape = RoundedCornerShape(10.dp),
            color = if (hasCustomFilters) TransitGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (hasCustomFilters) TransitGreen else Color.Transparent
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Filtros e preferências",
                    tint = if (hasCustomFilters) TransitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Filtros",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = if (hasCustomFilters) TransitGreen else MaterialTheme.colorScheme.onSurface
                )
                if (hasCustomFilters) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(TransitGreen, CircleShape)
                    )
                }
            }
        }

        // 3. Botão +15min
        Surface(
            onClick = onAddMinutes,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "15m",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 4. Botão -15min (ativo apenas se há 15min ou mais além do agora)
        if (canSubtract) {
            Surface(
                onClick = onSubtractMinutes,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "15m",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripItineraryCard(
    itinerary: TripPlanItinerary,
    onClick: () -> Unit
) {
    val durationText = remember(itinerary.totalDurationMinutes) { formatDuration(itinerary.totalDurationMinutes) }
    val startHH = remember(itinerary.startTimeUtc) { formatEpochMsToHHMM(itinerary.startTimeUtc) }
    val endHH = remember(itinerary.endTimeUtc) { formatEpochMsToHHMM(itinerary.endTimeUtc) }
    val cardInsights = remember(itinerary.insights, itinerary.insight) { itinerary.effectiveInsights }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            if (cardInsights.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cardInsights.take(2).forEach { insight ->
                        TripInsightBadge(
                            insight = insight,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Linha superior: Duracao total e Janela de Horario
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$startHH - $endHH",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tarifa total estimada da viagem (sempre preta dentro da caixa cinza)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            if (itinerary.isFareIntegrated) {
                                Icon(
                                    imageVector = Icons.Rounded.SyncAlt,
                                    contentDescription = "Tarifa integrada Bilhete Unico",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = itinerary.formattedFare,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Caminhada total
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${itinerary.walkDurationMinutes} min",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val visibleLegs = remember(itinerary.legs) {
                itinerary.legs.filter { leg ->
                    when (leg.type) {
                        TripLegType.WALK, TripLegType.TRANSFER_WALK -> leg.durationMinutes > 0 || leg.distanceMeters > 0
                        TripLegType.TRANSIT -> leg.lines.isNotEmpty()
                        else -> false
                    }
                }
            }

            // Linha do trajeto visual em faixa continua com scroll horizontal
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                visibleLegs.forEachIndexed { index, leg ->
                    when (leg.type) {
                        TripLegType.WALK, TripLegType.TRANSFER_WALK -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${leg.durationMinutes}m",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TripLegType.TRANSIT, TripLegType.WAIT -> {
                            // Agrupa linhas alternativas que servem para este mesmo trecho
                            val lines = leg.lines
                            val maxBadges = 4
                            val displayLines = lines.take(maxBadges)
                            val remaining = lines.size - maxBadges

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                displayLines.forEach { line ->
                                    val lineHex = line.colorHex
                                    val badgeColor = remember(lineHex) {
                                        try {
                                            android.graphics.Color.parseColor(lineHex).let { Color(it) }
                                        } catch (_: Exception) {
                                            TransitGreen
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeColor
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            if (line.isMetro) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DirectionsSubway,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                            }
                                            Text(
                                                text = line.lineNumber,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp
                                                ),
                                                color = Color.White,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                if (remaining > 0) {
                                    Text(
                                        text = "+$remaining",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (index < visibleLegs.size - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Linha dedicada aos proximos horarios de onibus da primeira perna
            val firstTransitLeg = itinerary.legs.firstOrNull { it.type == TripLegType.TRANSIT }
            if (firstTransitLeg != null && firstTransitLeg.upcomingDepartures.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Horários:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    firstTransitLeg.upcomingDepartures.take(5).forEach { dep ->
                        if (dep.isRealtime) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TransitGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
                            ) {
                                LiveWifiIcon(
                                    size = 11.dp,
                                    tint = TransitGreen
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = dep.timeFormatted,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = TransitGreen
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = dep.timeFormatted,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Exibicao da parada de embarque principal e nivel de lotacao
            if (firstTransitLeg?.departureStopName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Place,
                            contentDescription = null,
                            tint = TransitGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Em: ${firstTransitLeg.departureStopName}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    firstTransitLeg.lines.firstOrNull()?.crowding?.let { crowding ->
                        CrowdingBadge(
                            crowding = crowding,
                            isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                            useShortText = true
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    return if (minutes >= 60) {
        val hrs = minutes / 60
        val rem = minutes % 60
        if (rem == 0) "$hrs hr" else "$hrs hr $rem min"
    } else {
        "$minutes min"
    }
}

private val brasiliaTimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
private val brasiliaCalendar = ThreadLocal.withInitial { Calendar.getInstance(brasiliaTimeZone) }

private fun formatEpochMsToHHMM(epochMs: Long): String {
    val cal = brasiliaCalendar.get() ?: Calendar.getInstance(brasiliaTimeZone)
    cal.timeInMillis = epochMs
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    val hStr = if (h < 10) "0$h" else "$h"
    val mStr = if (m < 10) "0$m" else "$m"
    return "$hStr:$mStr"
}
