package com.dfmob.ui.stops

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.LineRouteState
import com.dfmob.data.model.StopArrival
import com.dfmob.data.model.TransitLine
import com.dfmob.data.model.TransitStop
import com.dfmob.data.repository.FavoritesRepository
import com.dfmob.data.repository.StopsRepository
import com.dfmob.ui.stops.components.FloatingArrivalsDeck
import com.dfmob.ui.stops.components.LineItineraryDeck
import com.dfmob.ui.stops.components.RealOpenStreetMap
import com.dfmob.ui.theme.TransitGreen

@Composable
fun StopsScreen(
    onBack: () -> Unit,
    favoritesRepository: FavoritesRepository? = null,
    initialLineNumber: String? = null,
    initialLineId: Int? = null,
    initialStopId: Int? = null,
    initialDestination: String? = null,
    fromLinesScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val focusManager = LocalFocusManager.current
    val repository = remember { StopsRepository(context) }
    val favoritesRepo = favoritesRepository ?: remember { FavoritesRepository(context) }
    val favoriteStops by favoritesRepo.favoriteStops.collectAsState()
    val favoriteLines by favoritesRepo.favoriteLines.collectAsState()

    var allStops by remember { mutableStateOf<List<TransitStop>>(emptyList()) }
    var selectedStop by remember { mutableStateOf<TransitStop?>(null) }
    var activeRouteState by remember { mutableStateOf<LineRouteState?>(null) }
    var arrivals by remember { mutableStateOf<List<StopArrival>>(emptyList()) }
    var isLoadingArrivals by remember { mutableStateOf(false) }

    // Interceptador do botao Voltar nativo do Android
    BackHandler {
        if (fromLinesScreen) {
            onBack()
        } else if (activeRouteState != null) {
            activeRouteState = null
        } else {
            onBack()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DFLocation>>(emptyList()) }
    var focusLocation by remember { mutableStateOf<DFLocation?>(null) }
    var isDropdownDismissed by remember { mutableStateOf(false) }
    var recenterTrigger by remember { mutableStateOf(0L) }

    // Gerenciador de permissao de localizacao precisa/aproximada
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            recenterTrigger = System.currentTimeMillis()
        }
    }

    // Carregamento inicial das paradas e verificacao de permissao de localizacao
    LaunchedEffect(Unit) {
        allStops = repository.getAllStops()
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Se permissao ja estiver concedida, centraliza imediatamente na localizacao GPS atual
            recenterTrigger = System.currentTimeMillis()
        }
    }

    // Busca de enderecos e locais do DF em tempo real com debouncing de 400ms e minimo de 3 caracteres
    LaunchedEffect(searchQuery, isDropdownDismissed) {
        if (isDropdownDismissed || searchQuery.isBlank() || searchQuery.trim().length < 3) {
            searchResults = emptyList()
        } else {
            delay(400L)
            searchResults = repository.searchLocations(searchQuery)
        }
    }

    // Observador de Ciclo de Vida: atualiza telemetria imediatamente quando o usuario liga a tela do celular (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTrigger = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(initialStopId, allStops) {
        if (initialStopId != null && initialStopId > 0 && allStops.isNotEmpty()) {
            val stop = allStops.firstOrNull { it.stopId == initialStopId }
            if (stop != null) {
                selectedStop = stop
                arrivals = repository.getStaticArrivalsForStop(stop)
                focusLocation = DFLocation(stop.stopName, "", stop.latE6, stop.lonE6)
            }
        }
    }

    // Carregamento de chegadas (Padrao Moovit: Instantaneo local sem delay + Telemetria assincrona em segundo plano e polling continuo)
    LaunchedEffect(selectedStop, resumeTrigger) {
        val stop = selectedStop
        if (stop != null) {
            if (arrivals.isEmpty()) {
                arrivals = repository.getStaticArrivalsForStop(stop)
            }
            isLoadingArrivals = false
            while (isActive) {
                val updated = repository.fetchLiveTelemetryUpdates(stop, arrivals)
                arrivals = updated
                delay(15_000L) // Atualiza telemetria a cada 15 segundos estilo Moovit
            }
        } else {
            arrivals = emptyList()
            isLoadingArrivals = false
        }
    }

    // Carregamento e inicializacao da Linha Selecionada vinda da Tela de Linhas ou atalho externo
    LaunchedEffect(initialLineNumber, initialStopId, initialDestination) {
        if (!initialLineNumber.isNullOrBlank()) {
            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val lastGps = try { locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
            val lastNet = try { locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
            val best = lastGps ?: lastNet
            val userLatE6 = best?.let { (it.latitude * 1_000_000).toInt() }
            val userLonE6 = best?.let { (it.longitude * 1_000_000).toInt() }

            val resolvedLineId = initialLineId ?: if (initialStopId != null && initialStopId > 0) {
                repository.resolveLineIdForStop(initialStopId, initialLineNumber, initialDestination)
            } else null

            val fastRoute = repository.getLineRouteStateFast(
                lineNumber = initialLineNumber,
                preferredLineId = resolvedLineId,
                preferredStopId = initialStopId,
                userLatE6 = userLatE6,
                userLonE6 = userLonE6
            )
            if (fastRoute != null) {
                activeRouteState = fastRoute
                if (initialStopId != null && initialStopId > 0) {
                    val stopMatch = fastRoute.stops.firstOrNull { it.stopId == initialStopId }
                    if (stopMatch != null) {
                        focusLocation = DFLocation(stopMatch.stopName, "", stopMatch.latE6, stopMatch.lonE6)
                    } else {
                        fastRoute.selectedStop?.let { sel ->
                            focusLocation = DFLocation(sel.stopName, "", sel.latE6, sel.lonE6)
                        }
                    }
                } else if (userLatE6 != null && userLonE6 != null) {
                    fastRoute.selectedStop?.let { sel ->
                        focusLocation = DFLocation(sel.stopName, "", sel.latE6, sel.lonE6)
                    }
                } else {
                    fastRoute.stops.firstOrNull()?.let { firstStop ->
                        focusLocation = DFLocation(firstStop.stopName, "", firstStop.latE6, firstStop.lonE6)
                    }
                }
                coroutineScope.launch {
                    val live = repository.enrichRouteStateWithLiveTelemetry(fastRoute)
                    if (activeRouteState?.activeLineId == fastRoute.activeLineId) {
                        activeRouteState = live
                    }
                }
            }
        }
    }

    // Telemetria continua da Rota da Linha: atualiza posicoes de veiculos e previsao da parada selecionada a cada 15s
    LaunchedEffect(activeRouteState?.activeLineId, activeRouteState?.selectedStop?.stopId, resumeTrigger) {
        val route = activeRouteState ?: return@LaunchedEffect
        val activeLineId = route.activeLineId
        val activeDirId = route.activeDirectionId
        val lineNum = route.lineNumber
        val selStop = route.selectedStop

        while (isActive) {
            val vDeferred = async(Dispatchers.IO) {
                repository.fetchLiveVehiclesForRoute(lineNum, activeDirId, activeLineId, route.stops)
            }
            val aDeferred = async(Dispatchers.IO) {
                if (selStop != null) {
                    repository.fetchArrivalForLineAtStop(activeLineId, selStop)
                } else null
            }
            val vehicles = vDeferred.await()
            val arrival = aDeferred.await()

            activeRouteState = activeRouteState?.copy(
                liveVehicles = vehicles,
                selectedStopArrival = arrival ?: activeRouteState?.selectedStopArrival
            )
            delay(3_500L)
        }
    }

    // Cores adaptativas ao tema Claro / Escuro do celular
    val scaffoldBg = if (isDarkTheme) Color(0xFF090B10) else Color(0xFFF7F8FA)
    val searchBarBg = if (isDarkTheme) Color(0xF215161A) else Color(0xF8FFFFFF)
    val searchBarBorder = if (isDarkTheme) Color(0xFF282A33) else Color(0xFFE2E4EB)
    val backBtnBg = if (isDarkTheme) Color(0xFF22242B) else Color(0xFFF0F2F5)
    val dropdownBg = if (isDarkTheme) Color(0xF8181A20) else Color(0xFAFFFFFF)
    val dropdownBorder = if (isDarkTheme) Color(0xFF2C2E38) else Color(0xFFE2E4EB)
    val itemBg = if (isDarkTheme) Color(0xFF20222A) else Color(0xFFF3F4F7)
    val iconAccent = if (isDarkTheme) TransitGreen else Color(0xFF00883E)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scaffoldBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Mapa Real OpenStreetMap (osmdroid) com Zoom Culling e suporte Claro/Escuro
            RealOpenStreetMap(
                stops = allStops,
                selectedStop = selectedStop,
                activeRoute = activeRouteState,
                focusLocation = focusLocation,
                recenterTrigger = recenterTrigger,
                enableAutoCenterOnGps = initialLineNumber.isNullOrBlank() && activeRouteState == null && focusLocation == null,
                isDarkTheme = isDarkTheme,
                onStopSelected = { stop ->
                    if (activeRouteState != null) {
                        coroutineScope.launch {
                            val arrival = repository.fetchArrivalForLineAtStop(activeRouteState!!.activeLineId, stop)
                            activeRouteState = activeRouteState?.copy(
                                selectedStop = stop,
                                selectedStopArrival = arrival
                            )
                        }
                    } else {
                        selectedStop = stop
                        // Emissao imediata instantanea (< 1ms) sem nenhum loading spinner bloqueante
                        arrivals = repository.getStaticArrivalsForStop(stop)
                        isLoadingArrivals = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Barra de Busca Superior Expansivel com sugestoes de locais
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val searchShape = RoundedCornerShape(22.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(searchShape)
                        .border(1.dp, searchBarBorder, searchShape),
                    color = searchBarBg,
                    shape = searchShape,
                    shadowElevation = if (isDarkTheme) 8.dp else 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botao Voltar com seta perfeitamente centralizada no circulo
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(backBtnBg)
                                .clickable {
                                    if (fromLinesScreen) {
                                        onBack()
                                    } else if (activeRouteState != null) {
                                        activeRouteState = null
                                    } else {
                                        onBack()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Voltar",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Campo de texto de busca
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Buscar endereço ou local no DF...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp
                                    ),
                                    color = if (isDarkTheme) Color(0xFF888B96) else Color(0xFF6B7280)
                                )
                            }

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    isDropdownDismissed = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(iconAccent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    isDropdownDismissed = true
                                })
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        isDropdownDismissed = true
                                        focusManager.clearFocus()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Limpar busca",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Place,
                                contentDescription = "Localizacao",
                                tint = iconAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Lista Dropdown com locais sugeridos do DF (desaparece ao selecionar um local)
                if (searchResults.isNotEmpty() && !isDropdownDismissed) {
                    val dropdownShape = RoundedCornerShape(16.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(dropdownShape)
                            .border(1.dp, dropdownBorder, dropdownShape),
                        color = dropdownBg,
                        shape = dropdownShape,
                        shadowElevation = if (isDarkTheme) 14.dp else 8.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = searchResults,
                                key = { "${it.name}_${it.latE6}_${it.lonE6}" }
                            ) { loc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(itemBg)
                                        .clickable {
                                            focusLocation = loc
                                            searchQuery = loc.name
                                            isDropdownDismissed = true
                                            searchResults = emptyList()
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Place,
                                        contentDescription = null,
                                        tint = iconAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = loc.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = loc.description,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp
                                            ),
                                            color = if (isDarkTheme) Color(0xFF8C90A0) else Color(0xFF6B7280),
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

            // 3. Botao Flutuante de Centralizar na Localizacao Atual
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isDarkTheme) Color(0xE6181A20) else Color(0xF2FFFFFF))
                    .border(1.dp, searchBarBorder, CircleShape)
                    .clickable {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            recenterTrigger = System.currentTimeMillis()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = "Centralizar mapa",
                    tint = iconAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 4. Painel Flutuante: Itinerario da Linha com Rota e Telemetria ou Chegadas da Parada
            AnimatedVisibility(
                visible = activeRouteState != null || selectedStop != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val currentRoute = activeRouteState
                if (currentRoute != null) {
                    LineItineraryDeck(
                        activeRoute = currentRoute,
                        onSelectStop = { stop ->
                            val staticArrival = repository.getStaticArrivalForLineAtStop(currentRoute.activeLineId, stop)
                            activeRouteState = activeRouteState?.copy(
                                selectedStop = stop,
                                selectedStopArrival = staticArrival
                            )
                            coroutineScope.launch {
                                val arrival = repository.fetchArrivalForLineAtStop(currentRoute.activeLineId, stop)
                                if (arrival != null && activeRouteState?.selectedStop?.stopId == stop.stopId) {
                                    activeRouteState = activeRouteState?.copy(
                                        selectedStopArrival = arrival
                                    )
                                }
                            }
                        },
                        onSwitchDirection = {
                            val newIndex = if (currentRoute.currentDirectionIndex == 0) 1 else 0
                            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                            val lastGps = try { locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
                            val lastNet = try { locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
                            val best = lastGps ?: lastNet
                            val uLat = best?.let { (it.latitude * 1_000_000).toInt() }
                            val uLon = best?.let { (it.longitude * 1_000_000).toInt() }
                            val fastSwitched = repository.changeLineRouteDirectionFast(currentRoute, newIndex, uLat, uLon)
                            activeRouteState = fastSwitched
                            coroutineScope.launch {
                                val live = repository.enrichRouteStateWithLiveTelemetry(fastSwitched)
                                if (activeRouteState?.activeLineId == fastSwitched.activeLineId) {
                                    activeRouteState = live
                                }
                            }
                        },
                        onClose = {
                            activeRouteState = null
                        },
                        onLocateBuses = {
                            val firstBus = currentRoute.liveVehicles.firstOrNull()
                            if (firstBus != null) {
                                focusLocation = DFLocation(firstBus.lineNumber, "", firstBus.latE6, firstBus.lonE6)
                            }
                        },
                        isFavorite = favoriteLines.any { fav ->
                            if (currentRoute.circular) {
                                fav.lineNumber == currentRoute.lineNumber
                            } else {
                                fav.lineNumber == currentRoute.lineNumber && (fav.directionId == null || fav.directionId == currentRoute.activeDirectionId)
                            }
                        },
                        onToggleFavorite = {
                            val lineObj = TransitLine(
                                routeId = currentRoute.activeLineId.toString(),
                                agencyId = "",
                                agencyName = currentRoute.agencyName,
                                lineNumber = currentRoute.lineNumber,
                                routeName = if (currentRoute.circular) currentRoute.lineName else currentRoute.activeDestination,
                                transitType = if (currentRoute.isMetro) 1 else 3,
                                colorHex = currentRoute.colorHex,
                                textColorHex = currentRoute.textColorHex,
                                price = null,
                                directionId = if (currentRoute.circular) null else currentRoute.activeDirectionId,
                                destinationName = if (currentRoute.circular) null else currentRoute.activeDestination,
                                isCircular = currentRoute.circular
                            )
                            favoritesRepo.toggleFavorite(lineObj)
                        },
                        isDarkTheme = isDarkTheme,
                        maxScreenHeight = maxHeight
                    )
                } else {
                    selectedStop?.let { stop ->
                        FloatingArrivalsDeck(
                            stop = stop,
                            arrivals = arrivals,
                            isLoading = isLoadingArrivals,
                            isDarkTheme = isDarkTheme,
                            onLineClick = { arrival ->
                                val fastRoute = repository.getLineRouteStateFast(
                                    lineNumber = arrival.lineNumber,
                                    preferredStopId = stop.stopId
                                )
                                if (fastRoute != null) {
                                    activeRouteState = fastRoute
                                    coroutineScope.launch {
                                        val live = repository.enrichRouteStateWithLiveTelemetry(fastRoute)
                                        if (activeRouteState?.activeLineId == fastRoute.activeLineId) {
                                            activeRouteState = live
                                        }
                                    }
                                }
                            },
                            isFavorite = favoriteStops.any { it.stopId == stop.stopId },
                            onToggleFavorite = {
                                favoritesRepo.toggleFavoriteStop(stop)
                            },
                            onClose = {
                                selectedStop = null
                            },
                            maxScreenHeight = maxHeight
                        )
                    }
                }
            }
        }
    }
}
