package com.dfmob.ui.tripplanner

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dfmob.data.model.TripPreference
import com.dfmob.data.model.TripTimeMode
import com.dfmob.data.repository.RecentSearchesRepository
import com.dfmob.data.repository.StopsRepository
import com.dfmob.ui.theme.TransitGreen
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun TripPlannerScreen(
    onBack: () -> Unit,
    initialDestination: DFLocation? = null,
    recentSearchesRepository: RecentSearchesRepository? = null,
    repository: StopsRepository = StopsRepository(LocalContext.current)
) {
    val context = LocalContext.current
    val recentRepo = recentSearchesRepository ?: remember { RecentSearchesRepository(context) }
    val viewModel = remember { TripPlannerViewModel(repository, context) }
    val state by viewModel.uiState.collectAsState()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialDestination) {
        if (initialDestination != null) {
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

    // 2. Tela de Detalhes do Trajeto Selecionado se estiver ativa
    val selectedItin = state.selectedItinerary
    if (selectedItin != null) {
        TripItineraryDetailView(
            itinerary = selectedItin,
            origin = state.origin,
            destination = state.destination,
            onBack = { viewModel.selectItinerary(null) },
            onFinishTrip = {
                viewModel.selectItinerary(null)
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
            }

            // MODULO 1: Origem e Destino com conexao vertical
            OriginDestinationCard(
                originText = state.originQuery,
                destinationText = state.destinationQuery,
                onOriginChange = { viewModel.onQueryChanged(it) },
                onDestinationChange = { viewModel.onQueryChanged(it) },
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
                                        focusManager.clearFocus()
                                        viewModel.selectLocationResult(loc)
                                        recentRepo.addSearch(loc)
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

            // MODULO 2: Filtros de Controle de Tempo (+15min, -15min, Menos Trocas, etc.)
            TimeAndPreferencesRow(
                timeMode = state.timeMode,
                targetTimeUtc = state.targetTimeUtc,
                preference = state.preference,
                onTimeModeSelected = { mode ->
                    if (mode == TripTimeMode.DEPART_NOW) {
                        viewModel.setTimeMode(mode)
                    } else {
                        // Abre seletor de horario nativo
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = state.targetTimeUtc
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                viewModel.setTimeModeAndCustomTime(mode, hour, minute)
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    }
                },
                onAddMinutes = { viewModel.addTimeMinutes(15) },
                onSubtractMinutes = { viewModel.addTimeMinutes(-15) },
                onTogglePreference = { viewModel.togglePreference(it) }
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
                                    text = "Tente ajustar o horário de partida ou os filtros de caminhada.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    state.itineraries.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = "Melhores rotas encontradas (${state.itineraries.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            // Ordenado do que leva menos tempo ate o que leva mais tempo
                            val sortedItineraries = state.itineraries.sortedBy { it.totalDurationMinutes }
                            items(sortedItineraries) { itinerary ->
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
                        Box(modifier = Modifier.fillMaxWidth()) {
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
                        Box(modifier = Modifier.fillMaxWidth()) {
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
    onTimeModeSelected: (TripTimeMode) -> Unit,
    onAddMinutes: () -> Unit,
    onSubtractMinutes: () -> Unit,
    onTogglePreference: (TripPreference) -> Unit
) {
    var isDropdownOpen by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val canSubtract = targetTimeUtc >= now + (15 * 60 * 1000L)

    val timeLabel = when (timeMode) {
        TripTimeMode.DEPART_NOW -> "Sair agora"
        TripTimeMode.DEPART_AT -> "Partida às ${formatEpochMsToHHMM(targetTimeUtc)}"
        TripTimeMode.ARRIVE_BY -> "Chegada até ${formatEpochMsToHHMM(targetTimeUtc)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dropdown Seletor de Modo de Horario
        Box {
            Surface(
                onClick = { isDropdownOpen = true },
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

            DropdownMenu(
                expanded = isDropdownOpen,
                onDismissRequest = { isDropdownOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Sair agora", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    onClick = {
                        isDropdownOpen = false
                        onTimeModeSelected(TripTimeMode.DEPART_NOW)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Definir horário de saída", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    onClick = {
                        isDropdownOpen = false
                        onTimeModeSelected(TripTimeMode.DEPART_AT)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Definir horário de chegada", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    onClick = {
                        isDropdownOpen = false
                        onTimeModeSelected(TripTimeMode.ARRIVE_BY)
                    }
                )
            }
        }

        // Botao +15min
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

        // Botao -15min (ativo apenas se ha 15min ou mais alem do agora)
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

        // Chip Menos trocas
        val isLeastTransfers = preference == TripPreference.LEAST_TRANSFERS
        Surface(
            onClick = { onTogglePreference(TripPreference.LEAST_TRANSFERS) },
            shape = RoundedCornerShape(10.dp),
            color = if (isLeastTransfers) TransitGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLeastTransfers) TransitGreen else Color.Transparent
            )
        ) {
            Text(
                text = "Menos trocas",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = if (isLeastTransfers) TransitGreen else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }

        // Chip Caminhar menos
        val isLeastWalking = preference == TripPreference.LEAST_WALKING
        Surface(
            onClick = { onTogglePreference(TripPreference.LEAST_WALKING) },
            shape = RoundedCornerShape(10.dp),
            color = if (isLeastWalking) TransitGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLeastWalking) TransitGreen else Color.Transparent
            )
        ) {
            Text(
                text = "Caminhar menos",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = if (isLeastWalking) TransitGreen else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripItineraryCard(
    itinerary: TripPlanItinerary,
    onClick: () -> Unit
) {
    val durationText = formatDuration(itinerary.totalDurationMinutes)
    val startHH = formatEpochMsToHHMM(itinerary.startTimeUtc)
    val endHH = formatEpochMsToHHMM(itinerary.endTimeUtc)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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

                // Caminhada total
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
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

            Spacer(modifier = Modifier.height(10.dp))

            val visibleLegs = itinerary.legs.filter { leg ->
                when (leg.type) {
                    TripLegType.WALK, TripLegType.TRANSFER_WALK -> leg.durationMinutes > 0 || leg.distanceMeters > 0
                    TripLegType.TRANSIT -> leg.lines.isNotEmpty()
                    else -> false
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
                                displayLines.forEachIndexed { lineIdx, line ->
                                    val badgeColor = try {
                                        android.graphics.Color.parseColor(line.colorHex).let { Color(it) }
                                    } catch (_: Exception) {
                                        TransitGreen
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
                                                color = Color.White
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
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    tint = TransitGreen,
                                    modifier = Modifier.size(11.dp)
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

            // Exibicao da parada de embarque principal
            if (firstTransitLeg?.departureStopName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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

private fun formatEpochMsToHHMM(epochMs: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"))
    cal.timeInMillis = epochMs
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    return String.format(Locale.ROOT, "%02d:%02d", h, m)
}
