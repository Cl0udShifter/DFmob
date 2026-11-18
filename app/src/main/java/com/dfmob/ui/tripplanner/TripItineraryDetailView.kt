package com.dfmob.ui.tripplanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dfmob.BuildConfig
import com.dfmob.data.database.DatabaseHelper
import com.dfmob.data.model.LiveVehicleMarker
import com.dfmob.data.model.PolylineDecoder
import com.dfmob.data.model.TripDepartureItem
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLeg
import com.dfmob.data.model.TripPlanLocation
import com.dfmob.data.model.TripTransitLineOption
import com.dfmob.data.telemetry.MoovitTransitClient
import com.dfmob.ui.theme.TransitGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private class ItineraryCartoTileSource(
    name: String,
    style: String
) : XYTileSource(
    name,
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/$style/",
        "https://b.basemaps.cartocdn.com/rastertiles/$style/",
        "https://c.basemaps.cartocdn.com/rastertiles/$style/",
        "https://d.basemaps.cartocdn.com/rastertiles/$style/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val base = super.getTileURLString(pMapTileIndex)
        val apiKey = BuildConfig.CARTO_API_KEY
        return if (apiKey.isNotBlank()) "$base?key=$apiKey" else base
    }
}

private val ITIN_CARTO_POSITRON = ItineraryCartoTileSource("ItinPositron", "light_all")
private val ITIN_CARTO_DARK_MATTER = ItineraryCartoTileSource("ItinDarkMatter", "dark_all")

sealed class NavigationStage {
    data class WalkToStop(
        val legIndex: Int,
        val targetName: String,
        val targetPoint: GeoPoint?,
        val walkDistanceMeters: Double,
        val durationMinutes: Int
    ) : NavigationStage()

    data class WaitForTransit(
        val legIndex: Int,
        val stopName: String,
        val stopId: Int?,
        val stopPoint: GeoPoint?,
        val candidateLines: List<TripTransitLineOption>,
        val initialUpcomingDepartures: List<TripDepartureItem>
    ) : NavigationStage()

    data class RideTransit(
        val legIndex: Int,
        val selectedLine: TripTransitLineOption?,
        val candidateLines: List<TripTransitLineOption>,
        val destinationStopName: String,
        val destinationPoint: GeoPoint?,
        val totalStopsCount: Int,
        val intermediateStops: List<GeoPoint>
    ) : NavigationStage()

    data class WalkToDestination(
        val legIndex: Int,
        val destinationName: String,
        val destinationPoint: GeoPoint?,
        val walkDistanceMeters: Double,
        val durationMinutes: Int
    ) : NavigationStage()

    object Completed : NavigationStage()
}

@Composable
fun TripItineraryDetailView(
    itinerary: TripPlanItinerary,
    origin: TripPlanLocation?,
    destination: TripPlanLocation?,
    onBack: () -> Unit,
    onFinishTrip: () -> Unit = onBack
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current
    val dbHelper = remember { DatabaseHelper(context) }

    // Estados de Navegacao Ativa
    var isNavigating by remember { mutableStateOf(false) }
    var currentStageIndex by remember { mutableIntStateOf(0) }
    var isExpandedDeck by remember { mutableStateOf(false) }
    var selectedLineForRide by remember { mutableStateOf<TripTransitLineOption?>(null) }
    var isChoosingLineDialog by remember { mutableStateOf(false) }
    var linesToChooseFrom by remember { mutableStateOf<List<TripTransitLineOption>>(emptyList()) }

    // Rastreamento de Paradas Restantes na perna a bordo
    var remainingStopsCount by remember { mutableIntStateOf(0) }

    // Localizacao do Usuario
    var currentUserLocation by remember { mutableStateOf<Location?>(null) }

    // Construcao dinamica dos passos do itinerario
    val stages = remember(itinerary, destination) {
        buildNavigationStages(itinerary, destination, dbHelper)
    }

    val currentStage = if (isNavigating && currentStageIndex in stages.indices) {
        stages[currentStageIndex]
    } else null

    // Overlays do osmdroid
    val polylinesFolder = remember { FolderOverlay() }
    val markersFolder = remember { FolderOverlay() }
    val intermediateMarkersFolder = remember { FolderOverlay() }
    val vehiclesFolder = remember { FolderOverlay() }

    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        MapView(context).apply {
            val initialSource = if (isDarkTheme) ITIN_CARTO_DARK_MATTER else ITIN_CARTO_POSITRON
            setTileSource(initialSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 9.0
            maxZoomLevel = 20.0
            overlays.add(polylinesFolder)
            overlays.add(intermediateMarkersFolder)
            overlays.add(markersFolder)
            overlays.add(vehiclesFolder)
        }
    }

    // Registra LocationListener para atualizar a posicao e velocidade do usuario continuamente
    DisposableEffect(context) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                currentUserLocation = loc
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, locListener)
            lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, locListener)
            val lastGps = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            currentUserLocation = lastGps ?: lastNet
        } catch (_: SecurityException) {}

        onDispose {
            try {
                lm?.removeUpdates(locListener)
            } catch (_: SecurityException) {}
        }
    }

    // Gerenciamento de ciclo de vida do mapa
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Desenha as rotas na tela com cor Cinza-ardosia (#455A64) para caminhadas
    LaunchedEffect(itinerary) {
        polylinesFolder.items.clear()
        markersFolder.items.clear()

        val allPoints = mutableListOf<GeoPoint>()

        itinerary.legs.forEach { leg ->
            val polylineStr = leg.polylineEncoded
            if (!polylineStr.isNullOrBlank()) {
                val decoded = PolylineDecoder.decode(polylineStr)
                val geoPoints = decoded.map { GeoPoint(it.first, it.second) }
                allPoints.addAll(geoPoints)

                val line = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    if (leg.type == TripLegType.WALK || leg.type == TripLegType.TRANSFER_WALK) {
                        // Cinza-ardosia tracejado conforme solicitado
                        outlinePaint.apply {
                            color = android.graphics.Color.parseColor("#455A64")
                            strokeWidth = 10f
                            strokeCap = Paint.Cap.ROUND
                            pathEffect = DashPathEffect(floatArrayOf(14f, 14f), 0f)
                        }
                    } else {
                        val colorHex = leg.lines.firstOrNull()?.colorHex ?: "#00A859"
                        val parsedColor = try {
                            android.graphics.Color.parseColor(colorHex)
                        } catch (_: Exception) {
                            android.graphics.Color.parseColor("#00A859")
                        }
                        outlinePaint.apply {
                            color = parsedColor
                            strokeWidth = 14f
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                    }
                }
                polylinesFolder.add(line)
            }
        }

        // Marcador de Origem
        if (origin != null) {
            val originPoint = GeoPoint(origin.latitude, origin.longitude)
            allPoints.add(originPoint)
            val originMarker = Marker(mapView).apply {
                position = originPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createCircleMarkerDrawable(context, "#00A859", "A")
                title = origin.caption
            }
            markersFolder.add(originMarker)
        }

        // Marcador de Destino
        if (destination != null) {
            val destPoint = GeoPoint(destination.latitude, destination.longitude)
            allPoints.add(destPoint)
            val destMarker = Marker(mapView).apply {
                position = destPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createCircleMarkerDrawable(context, "#E53935", "B")
                title = destination.caption
            }
            markersFolder.add(destMarker)
        }

        mapView.invalidate()

        // Ajuste inicial com zoom out exibindo todo o trajeto com folga para nao cobrir sob o painel
        if (allPoints.isNotEmpty()) {
            val minLat = allPoints.minOf { it.latitude }
            val maxLat = allPoints.maxOf { it.latitude }
            val minLon = allPoints.minOf { it.longitude }
            val maxLon = allPoints.maxOf { it.longitude }

            val padLat = (maxLat - minLat).coerceAtLeast(0.005) * 0.40
            val padLon = (maxLon - minLon).coerceAtLeast(0.005) * 0.20

            val boundingBox = BoundingBox(
                maxLat + padLat,
                maxLon + padLon,
                minLat - (padLat * 1.5),
                minLon - padLon
            )
            mapView.post {
                mapView.zoomToBoundingBox(boundingBox, true, 80)
            }
        }
    }

    // Comportamento do mapa e painel conforme a etapa ativa de navegacao
    LaunchedEffect(currentStageIndex, isNavigating) {
        if (!isNavigating) return@LaunchedEffect

        when (val stage = currentStage) {
            is NavigationStage.WalkToStop -> {
                isExpandedDeck = false
                intermediateMarkersFolder.items.clear()
                vehiclesFolder.items.clear()
                mapView.invalidate()
                val target = stage.targetPoint
                if (target != null) {
                    mapView.controller.animateTo(target, 17.0, 700L)
                }
            }

            is NavigationStage.WaitForTransit -> {
                // Painel abre automaticamente a 75% da tela para visualizar os onibus e horarios
                isExpandedDeck = true
                intermediateMarkersFolder.items.clear()
                val target = stage.stopPoint
                if (target != null) {
                    mapView.controller.animateTo(target, 16.5, 700L)
                }
            }

            is NavigationStage.RideTransit -> {
                // Painel retrai para o tamanho normal e mapa centraliza no trajeto
                isExpandedDeck = false
                vehiclesFolder.items.clear()
                remainingStopsCount = stage.totalStopsCount

                // Desenha pequenos pontos pretos indicando as paradas em que o usuario nao deve descer
                intermediateMarkersFolder.items.clear()
                for (pt in stage.intermediateStops) {
                    val m = Marker(mapView).apply {
                        position = pt
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createSmallDotDrawable(context)
                    }
                    intermediateMarkersFolder.add(m)
                }
                mapView.invalidate()

                // Zoom out e centraliza no trajeto do onibus
                if (stage.intermediateStops.isNotEmpty()) {
                    val pts = stage.intermediateStops
                    val minLat = pts.minOf { it.latitude }
                    val maxLat = pts.maxOf { it.latitude }
                    val minLon = pts.minOf { it.longitude }
                    val maxLon = pts.maxOf { it.longitude }
                    val bb = BoundingBox(
                        maxLat + 0.008,
                        maxLon + 0.008,
                        minLat - 0.012,
                        minLon - 0.008
                    )
                    mapView.post { mapView.zoomToBoundingBox(bb, true, 80) }
                } else if (stage.destinationPoint != null) {
                    mapView.controller.animateTo(stage.destinationPoint, 15.0, 700L)
                }
            }

            is NavigationStage.WalkToDestination -> {
                isExpandedDeck = false
                intermediateMarkersFolder.items.clear()
                vehiclesFolder.items.clear()
                mapView.invalidate()
                val target = stage.destinationPoint
                if (target != null) {
                    mapView.controller.animateTo(target, 17.0, 700L)
                }
            }

            is NavigationStage.Completed -> {
                isExpandedDeck = false
                intermediateMarkersFolder.items.clear()
                vehiclesFolder.items.clear()
                mapView.invalidate()
            }

            null -> {}
        }
    }

    // Telemetria ao vivo dos onibus das linhas do trajeto durante a etapa WaitForTransit
    LaunchedEffect(currentStage) {
        if (currentStage is NavigationStage.WaitForTransit) {
            val wait = currentStage as NavigationStage.WaitForTransit
            while (isActive) {
                val markers = mutableListOf<LiveVehicleMarker>()
                for (line in wait.candidateLines) {
                    try {
                        val stopIds = if (wait.stopId != null) listOf(wait.stopId) else emptyList()
                        val list = MoovitTransitClient.fetchLiveVehiclesForLine(
                            lineId = line.lineId,
                            stopIds = stopIds,
                            lineNumber = line.lineNumber,
                            directionId = 0
                        )
                        markers.addAll(list)
                    } catch (_: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    vehiclesFolder.items.clear()
                    for (v in markers) {
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(v.latitude, v.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createVehiclePuckDrawable(context, v.bearing)
                            title = "Linha ${v.lineNumber}"
                        }
                        vehiclesFolder.add(marker)
                    }
                    mapView.invalidate()
                }

                delay(12_000L)
            }
        } else {
            vehiclesFolder.items.clear()
            mapView.invalidate()
        }
    }

    // Monitoramento do GPS e avanco automatico de etapas
    LaunchedEffect(currentStage, currentUserLocation) {
        if (!isNavigating) return@LaunchedEffect
        val userLoc = currentUserLocation ?: return@LaunchedEffect

        when (val stage = currentStage) {
            is NavigationStage.WalkToStop -> {
                val target = stage.targetPoint
                if (target != null) {
                    val dist = distanceMeters(userLoc.latitude, userLoc.longitude, target.latitude, target.longitude)
                    if (dist <= 40f) {
                        // Chegou na parada de embarque
                        currentStageIndex = (currentStageIndex + 1).coerceAtMost(stages.size - 1)
                    }
                }
            }

            is NavigationStage.WaitForTransit -> {
                val stopPt = stage.stopPoint
                val speedKmh = userLoc.speed * 3.6f
                if (stopPt != null) {
                    val distFromStop = distanceMeters(userLoc.latitude, userLoc.longitude, stopPt.latitude, stopPt.longitude)
                    // Se estiver em movimento rapido (> 18 km/h) e se afastando da parada
                    if (speedKmh >= 18f && distFromStop > 70f) {
                        if (stage.candidateLines.size > 1) {
                            linesToChooseFrom = stage.candidateLines
                            isChoosingLineDialog = true
                        } else {
                            selectedLineForRide = stage.candidateLines.firstOrNull()
                            currentStageIndex = (currentStageIndex + 1).coerceAtMost(stages.size - 1)
                        }
                    }
                }
            }

            is NavigationStage.RideTransit -> {
                // Atualiza o contador decrescente de paradas restantes
                if (stage.intermediateStops.isNotEmpty()) {
                    var remaining = 0
                    for (pt in stage.intermediateStops) {
                        val dist = distanceMeters(userLoc.latitude, userLoc.longitude, pt.latitude, pt.longitude)
                        if (dist > 35f) {
                            remaining++
                        }
                    }
                    remainingStopsCount = remaining.coerceAtLeast(1)
                }

                val destPt = stage.destinationPoint
                if (destPt != null) {
                    val distToDest = distanceMeters(userLoc.latitude, userLoc.longitude, destPt.latitude, destPt.longitude)
                    if (distToDest <= 60f) {
                        // Chegou na parada destino do onibus
                        currentStageIndex = (currentStageIndex + 1).coerceAtMost(stages.size - 1)
                    }
                }
            }

            is NavigationStage.WalkToDestination -> {
                val destPt = stage.destinationPoint
                if (destPt != null) {
                    val dist = distanceMeters(userLoc.latitude, userLoc.longitude, destPt.latitude, destPt.longitude)
                    if (dist <= 40f) {
                        currentStageIndex = stages.indexOf(NavigationStage.Completed)
                    }
                }
            }

            is NavigationStage.Completed -> {
                delay(1500L)
                onFinishTrip()
            }

            null -> {}
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxScreenHeight = maxHeight

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar com botoes de controle de viagem
        TopNavigationBar(
            isNavigating = isNavigating,
            currentStageIndex = currentStageIndex,
            totalStages = stages.size,
            itinerary = itinerary,
            onBack = onBack,
            onStartNavigation = {
                isNavigating = true
                currentStageIndex = 0
                val userLoc = currentUserLocation
                if (userLoc != null) {
                    mapView.controller.animateTo(GeoPoint(userLoc.latitude, userLoc.longitude), 17.5, 800L)
                }
            },
            onCancelNavigation = {
                isNavigating = false
                currentStageIndex = 0
                isExpandedDeck = false
                intermediateMarkersFolder.items.clear()
                vehiclesFolder.items.clear()
                mapView.invalidate()
            },
            onPreviousStage = {
                if (currentStageIndex > 0) {
                    currentStageIndex--
                }
            },
            onNextStage = {
                if (currentStage is NavigationStage.WaitForTransit && (currentStage as NavigationStage.WaitForTransit).candidateLines.size > 1) {
                    linesToChooseFrom = (currentStage as NavigationStage.WaitForTransit).candidateLines
                    isChoosingLineDialog = true
                } else if (currentStageIndex < stages.size - 1) {
                    currentStageIndex++
                }
            }
        )

        // Painel inferior contextual
        if (!isNavigating) {
            // Painel normal de visao geral do trajeto
            OverviewItineraryDeck(
                itinerary = itinerary,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        } else {
            // Painel dinâmico da etapa de navegacao ativa
            ActiveNavigationDeck(
                stage = currentStage,
                isExpanded = isExpandedDeck,
                maxScreenHeight = maxScreenHeight,
                remainingStops = remainingStopsCount,
                selectedLine = selectedLineForRide,
                onToggleExpand = { isExpandedDeck = !isExpandedDeck },
                onLineSelectedToBoard = { line ->
                    selectedLineForRide = line
                    currentStageIndex = (currentStageIndex + 1).coerceAtMost(stages.size - 1)
                },
                onManualAdvance = {
                    if (currentStage is NavigationStage.WaitForTransit && (currentStage as NavigationStage.WaitForTransit).candidateLines.size > 1) {
                        linesToChooseFrom = (currentStage as NavigationStage.WaitForTransit).candidateLines
                        isChoosingLineDialog = true
                    } else if (currentStageIndex < stages.size - 1) {
                        currentStageIndex++
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }

        // Dialogo para selecao de linha quando ha mais de uma alternativa disponivel no trajeto
        if (isChoosingLineDialog) {
            AlertDialog(
                onDismissRequest = { isChoosingLineDialog = false },
                title = {
                    Text(
                        text = "Em qual ônibus você embarcou?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Selecione a linha para acompanhar as paradas até o destino:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        linesToChooseFrom.forEach { line ->
                            val lineCol = try {
                                android.graphics.Color.parseColor(line.colorHex).let { Color(it) }
                            } catch (_: Exception) {
                                TransitGreen
                            }
                            Surface(
                                onClick = {
                                    selectedLineForRide = line
                                    isChoosingLineDialog = false
                                    currentStageIndex = (currentStageIndex + 1).coerceAtMost(stages.size - 1)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = lineCol,
                                        modifier = Modifier.padding(end = 10.dp)
                                    ) {
                                        Text(
                                            text = line.lineNumber,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = line.lineName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedLineForRide = linesToChooseFrom.firstOrNull()
                        isChoosingLineDialog = false
                        currentStageIndex = (currentStageIndex + 1).coerceAtMost(stages.size - 1)
                    }) {
                        Text("Primeira linha")
                    }
                }
            )
        }

        // Tela de Conclusao da Viagem com tick branco e fundo verde centralizado
        if (currentStage is NavigationStage.Completed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .clip(CircleShape)
                            .background(TransitGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Concluído",
                            tint = Color.White,
                            modifier = Modifier.size(62.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Viagem Concluída!",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Você chegou ao seu destino",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopNavigationBar(
    isNavigating: Boolean,
    currentStageIndex: Int,
    totalStages: Int,
    itinerary: TripPlanItinerary,
    onBack: () -> Unit,
    onStartNavigation: () -> Unit,
    onCancelNavigation: () -> Unit,
    onPreviousStage: () -> Unit,
    onNextStage: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(14.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        if (!isNavigating) {
            // Modo de visualizacao padrao antes de iniciar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Detalhes do Trajeto",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val startHH = formatEpochMsToHHMM(itinerary.startTimeUtc)
                    val endHH = formatEpochMsToHHMM(itinerary.endTimeUtc)
                    Text(
                        text = "$startHH - $endHH (${formatDuration(itinerary.totalDurationMinutes)})",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = TransitGreen
                    )
                }

                // Botao Verde Iniciar Viagem no topo
                Surface(
                    onClick = onStartNavigation,
                    shape = RoundedCornerShape(12.dp),
                    color = TransitGreen,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Iniciar Viagem",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 12.5.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            // Modo de Navegacao Ativa com botoes de avancar, voltar e cancelar viagem
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancelar Viagem
                IconButton(onClick = onCancelNavigation) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancelar viagem",
                        tint = Color(0xFFE53935)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Em viagem",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Etapa ${currentStageIndex + 1} de ${(totalStages - 1).coerceAtLeast(1)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = TransitGreen
                    )
                }

                // Botao Voltar Etapa
                IconButton(
                    onClick = onPreviousStage,
                    enabled = currentStageIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                        contentDescription = "Etapa anterior",
                        tint = if (currentStageIndex > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Botao Avancar Etapa
                IconButton(
                    onClick = onNextStage,
                    enabled = currentStageIndex < totalStages - 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                        contentDescription = "Próxima etapa",
                        tint = if (currentStageIndex < totalStages - 1) TransitGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveNavigationDeck(
    stage: NavigationStage?,
    isExpanded: Boolean,
    maxScreenHeight: Dp,
    remainingStops: Int,
    selectedLine: TripTransitLineOption?,
    onToggleExpand: () -> Unit,
    onLineSelectedToBoard: (TripTransitLineOption) -> Unit,
    onManualAdvance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val animSpec = tween<Dp>(durationMillis = 90, easing = FastOutSlowInEasing)

    val isWaitForTransit = stage is NavigationStage.WaitForTransit

    val targetHeight = if (isWaitForTransit && isExpanded) {
        if (maxScreenHeight > 200.dp) maxScreenHeight * 0.75f else 540.dp
    } else {
        210.dp
    }

    val animatedHeight by animateDpAsState(targetValue = targetHeight, animationSpec = animSpec, label = "deckH")
    val animatedPaddingH by animateDpAsState(targetValue = if (isWaitForTransit && isExpanded) 0.dp else 14.dp, animationSpec = animSpec, label = "deckPadH")
    val animatedPaddingB by animateDpAsState(targetValue = if (isWaitForTransit && isExpanded) 0.dp else 12.dp, animationSpec = animSpec, label = "deckPadB")
    val animatedRadiusBottom by animateDpAsState(targetValue = if (isWaitForTransit && isExpanded) 0.dp else 22.dp, animationSpec = animSpec, label = "deckRadB")

    val deckShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = animatedRadiusBottom,
        bottomEnd = animatedRadiusBottom
    )

    var dragOffsetY by remember { mutableStateOf(0f) }

    Surface(
        modifier = modifier
            .padding(horizontal = animatedPaddingH, vertical = animatedPaddingB)
            .height(animatedHeight)
            .pointerInput(isWaitForTransit) {
                if (isWaitForTransit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY < -50f && !isExpanded) {
                                onToggleExpand()
                            } else if (dragOffsetY > 50f && isExpanded) {
                                onToggleExpand()
                            }
                            dragOffsetY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount
                        }
                    )
                }
            },
        shape = deckShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            // Puxador central superior
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isWaitForTransit) { onToggleExpand() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (stage) {
                is NavigationStage.WalkToStop -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF455A64)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Caminhe até a parada",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF455A64)
                            )
                            Text(
                                text = stage.targetName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatMeters(stage.walkDistanceMeters)} (${stage.durationMinutes} min)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = onManualAdvance,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TransitGreen),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Já cheguei na parada", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                is NavigationStage.WaitForTransit -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Aguarde o transporte",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TransitGreen
                            )
                            Text(
                                text = stage.stopName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = onToggleExpand) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Lista de cartoes exclusivos das linhas deste trajeto com horarios
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stage.candidateLines.forEach { line ->
                            val lineCol = try {
                                android.graphics.Color.parseColor(line.colorHex).let { Color(it) }
                            } catch (_: Exception) {
                                TransitGreen
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = lineCol
                                            ) {
                                                Text(
                                                    text = line.lineNumber,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 13.sp
                                                    ),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = line.lineName,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.width(140.dp)
                                            )
                                        }

                                        // Botao de embarque direto nesta linha
                                        Surface(
                                            onClick = { onLineSelectedToBoard(line) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = TransitGreen
                                        ) {
                                            Text(
                                                text = "Embarcar",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    // Horarios desta linha
                                    val departures = stage.initialUpcomingDepartures.take(5)
                                    if (departures.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.horizontalScroll(rememberScrollState())
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            departures.forEach { dep ->
                                                if (dep.isRealtime) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(TransitGreen.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                                            color = TransitGreen
                                                        )
                                                    }
                                                } else {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                    ) {
                                                        Text(
                                                            text = dep.timeFormatted,
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                }

                is NavigationStage.RideTransit -> {
                    val line = selectedLine ?: stage.candidateLines.firstOrNull()
                    val lineCol = try {
                        android.graphics.Color.parseColor(line?.colorHex ?: "#00A859").let { Color(it) }
                    } catch (_: Exception) {
                        TransitGreen
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = lineCol
                        ) {
                            Text(
                                text = line?.lineNumber ?: "Ônibus",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Desça na parada",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TransitGreen
                            )
                            Text(
                                text = stage.destinationStopName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$remainingStops paradas restantes até a descida",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = onManualAdvance,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TransitGreen),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Cheguei na parada de descida", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                is NavigationStage.WalkToDestination -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF455A64)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Caminhe até o destino",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF455A64)
                            )
                            Text(
                                text = stage.destinationName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatMeters(stage.walkDistanceMeters)} (${stage.durationMinutes} min)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = onManualAdvance,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TransitGreen),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Cheguei no destino", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                is NavigationStage.Completed -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Chegando ao destino final...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TransitGreen
                        )
                    }
                }

                null -> {}
            }
        }
    }
}

@Composable
private fun OverviewItineraryDeck(
    itinerary: TripPlanItinerary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(max = 360.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            Text(
                text = "Passos do Itinerário",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            itinerary.legs.forEachIndexed { index, leg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(28.dp)
                    ) {
                        val iconVector = when (leg.type) {
                            TripLegType.WALK, TripLegType.TRANSFER_WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
                            TripLegType.TRANSIT -> {
                                if (leg.lines.any { it.isMetro }) Icons.Rounded.DirectionsSubway else Icons.Rounded.DirectionsBus
                            }
                            else -> Icons.Rounded.Place
                        }
                        val iconColor = when (leg.type) {
                            TripLegType.WALK, TripLegType.TRANSFER_WALK -> Color(0xFF455A64)
                            TripLegType.TRANSIT -> TransitGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(18.dp)
                        )

                        if (index < itinerary.legs.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(38.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        when (leg.type) {
                            TripLegType.WALK, TripLegType.TRANSFER_WALK -> {
                                Text(
                                    text = "Caminhada (${leg.durationMinutes} min)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Distância: ${formatMeters(leg.distanceMeters)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TripLegType.TRANSIT -> {
                                val linesText = leg.lines.joinToString(" ou ") { it.lineNumber }
                                Text(
                                    text = linesText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    ),
                                    color = TransitGreen
                                )

                                leg.departureStopName?.let { depStop ->
                                    Text(
                                        text = "Embarque: $depStop",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (leg.stopsCount > 0) {
                                    Text(
                                        text = "Permaneça por ${leg.stopsCount} paradas (${leg.durationMinutes} min)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                leg.arrivalStopName?.let { arrStop ->
                                    Text(
                                        text = "Desembarque: $arrStop",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            else -> {}
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

private fun buildNavigationStages(
    itinerary: TripPlanItinerary,
    destination: TripPlanLocation?,
    dbHelper: DatabaseHelper
): List<NavigationStage> {
    val stages = mutableListOf<NavigationStage>()
    val legs = itinerary.legs

    legs.forEachIndexed { idx, leg ->
        when (leg.type) {
            TripLegType.WALK, TripLegType.TRANSFER_WALK -> {
                val nextTransit = legs.subList(idx + 1, legs.size).firstOrNull { it.type == TripLegType.TRANSIT }
                val targetName = nextTransit?.departureStopName ?: destination?.caption ?: "Destino"
                val decodedPoints = leg.polylineEncoded?.let { PolylineDecoder.decode(it) }
                val targetPoint = if (!decodedPoints.isNullOrEmpty()) {
                    GeoPoint(decodedPoints.last().first, decodedPoints.last().second)
                } else if (nextTransit != null) {
                    val p = nextTransit.polylineEncoded?.let { PolylineDecoder.decode(it) }?.firstOrNull()
                    p?.let { GeoPoint(it.first, it.second) }
                } else if (destination != null) {
                    GeoPoint(destination.latitude, destination.longitude)
                } else null

                if (nextTransit != null) {
                    stages.add(
                        NavigationStage.WalkToStop(
                            legIndex = idx,
                            targetName = targetName,
                            targetPoint = targetPoint,
                            walkDistanceMeters = leg.distanceMeters,
                            durationMinutes = leg.durationMinutes
                        )
                    )
                } else {
                    stages.add(
                        NavigationStage.WalkToDestination(
                            legIndex = idx,
                            destinationName = targetName,
                            destinationPoint = targetPoint,
                            walkDistanceMeters = leg.distanceMeters,
                            durationMinutes = leg.durationMinutes
                        )
                    )
                }
            }

            TripLegType.TRANSIT -> {
                val decodedPoints = leg.polylineEncoded?.let { PolylineDecoder.decode(it) } ?: emptyList()
                val stopPoint = if (decodedPoints.isNotEmpty()) {
                    GeoPoint(decodedPoints.first().first, decodedPoints.first().second)
                } else null
                val destPoint = if (decodedPoints.isNotEmpty()) {
                    GeoPoint(decodedPoints.last().first, decodedPoints.last().second)
                } else null

                // Recupera paradas intermediarias do banco GTFS local se lineId estiver presente
                val firstLineId = leg.lines.firstOrNull()?.lineId
                val intermediatePoints = mutableListOf<GeoPoint>()
                if (firstLineId != null && firstLineId > 0) {
                    val allLineStops = dbHelper.getStopsForLine(firstLineId)
                    if (allLineStops.size > 2) {
                        val firstSid = leg.departureStopId
                        val startIdx = if (firstSid != null) allLineStops.indexOfFirst { it.stopId == firstSid }.coerceAtLeast(0) else 0
                        val endIdx = allLineStops.size - 1
                        val slice = allLineStops.subList(
                            (startIdx + 1).coerceAtMost(allLineStops.size),
                            endIdx.coerceAtLeast(0)
                        )
                        intermediatePoints.addAll(slice.map { GeoPoint(it.latitude, it.longitude) })
                    }
                }

                // Fallback se nao houver paradas no banco: amostra pontos ao longo da polyline
                if (intermediatePoints.isEmpty() && leg.stopsCount > 0 && decodedPoints.size > 2) {
                    val count = leg.stopsCount
                    val step = (decodedPoints.size / (count + 1)).coerceAtLeast(1)
                    for (s in 1..count) {
                        val ptIdx = (s * step).coerceAtMost(decodedPoints.size - 2)
                        val pt = decodedPoints[ptIdx]
                        intermediatePoints.add(GeoPoint(pt.first, pt.second))
                    }
                }

                // Etapa 1: Esperar pelo onibus
                stages.add(
                    NavigationStage.WaitForTransit(
                        legIndex = idx,
                        stopName = leg.departureStopName ?: "Parada de Embarque",
                        stopId = leg.departureStopId,
                        stopPoint = stopPoint,
                        candidateLines = leg.lines,
                        initialUpcomingDepartures = leg.upcomingDepartures
                    )
                )

                // Etapa 2: A bordo ate a parada de descida
                stages.add(
                    NavigationStage.RideTransit(
                        legIndex = idx,
                        selectedLine = leg.lines.firstOrNull(),
                        candidateLines = leg.lines,
                        destinationStopName = leg.arrivalStopName ?: "Parada de Desembarque",
                        destinationPoint = destPoint,
                        totalStopsCount = leg.stopsCount.coerceAtLeast(intermediatePoints.size),
                        intermediateStops = intermediatePoints
                    )
                )
            }

            else -> {}
        }
    }

    stages.add(NavigationStage.Completed)
    return stages
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0]
}

private fun createSmallDotDrawable(context: Context): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (10 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val r = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.FILL
    }
    canvas.drawCircle(r, r, r - (1.2f * density), paint)

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    canvas.drawCircle(r, r, r - (1.2f * density), border)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createVehiclePuckDrawable(context: Context, bearing: Float): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (32 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    paint.color = android.graphics.Color.argb(55, 0, 0, 0)
    canvas.drawCircle(center, center + (1.2f * density), 13.5f * density, paint)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, 13.0f * density, paint)

    paint.color = android.graphics.Color.parseColor("#00C853")
    canvas.drawCircle(center, center, 10.5f * density, paint)

    // Icone de onibus centralizado
    val busPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val w = 11f * density
    val h = 11f * density
    val left = center - w / 2f
    val top = center - h / 2f
    val right = center + w / 2f
    val bottom = center + h / 2f
    val rad = 2.5f * density
    canvas.drawRoundRect(left, top, right, bottom, rad, rad, busPaint)

    // Indicador direcional
    if (bearing > 0f) {
        val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#00C853")
            style = Paint.Style.FILL
        }
        val path = Path()
        val tipDistance = 15.0f * density
        val baseDistance = 11.5f * density
        val halfAngle = 20.0
        val bearingRad = Math.toRadians(bearing.toDouble())
        val tipX = center + (tipDistance * Math.sin(bearingRad)).toFloat()
        val tipY = center - (tipDistance * Math.cos(bearingRad)).toFloat()
        val baseAngleLeft = bearingRad - Math.toRadians(halfAngle)
        val leftX = center + (baseDistance * Math.sin(baseAngleLeft)).toFloat()
        val leftY = center - (baseDistance * Math.cos(baseAngleLeft)).toFloat()
        val baseAngleRight = bearingRad + Math.toRadians(halfAngle)
        val rightX = center + (baseDistance * Math.sin(baseAngleRight)).toFloat()
        val rightY = center - (baseDistance * Math.cos(baseAngleRight)).toFloat()

        path.moveTo(tipX, tipY)
        path.lineTo(leftX, leftY)
        path.lineTo(rightX, rightY)
        path.close()
        canvas.drawPath(path, chevronPaint)
    }

    return BitmapDrawable(context.resources, bitmap)
}

private fun createCircleMarkerDrawable(context: Context, colorHex: String, letter: String): Drawable {
    val sizePx = (32 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = try { android.graphics.Color.parseColor(colorHex) } catch (_: Exception) { android.graphics.Color.GREEN }
        style = Paint.Style.FILL
    }
    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius - 2, paint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
    }
    canvas.drawCircle(radius, radius, radius - 2, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 14f * context.resources.displayMetrics.density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    val yPos = (radius - ((textPaint.descent() + textPaint.ascent()) / 2))
    canvas.drawText(letter, radius, yPos, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun formatMeters(distanceMeters: Double): String {
    return if (distanceMeters >= 1000.0) {
        String.format(Locale.ROOT, "%.1f km", distanceMeters / 1000.0)
    } else {
        "${distanceMeters.toInt()} m"
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
