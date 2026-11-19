package com.dfmob.ui.tripplanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import com.dfmob.ui.stops.components.RouteDirectionOverlay
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.dfmob.data.model.TransitStop
import com.dfmob.data.preferences.NotificationPreferences
import com.dfmob.data.telemetry.MoovitTransitClient
import com.dfmob.data.telemetry.SemobTelemetryService
import com.dfmob.service.NavigationStateHolder
import com.dfmob.service.TripDescentAlarmHelper
import com.dfmob.service.TripNavigationTrackingService
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
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Calendar
import androidx.compose.ui.graphics.luminance
import java.util.Locale
import java.util.TimeZone

private class ItineraryStadiaTileSource(
    name: String,
    style: String
) : XYTileSource(
    name,
    0, 20, 512, "@2x.png",
    arrayOf("https://tiles.stadiamaps.com/tiles/$style/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val base = super.getTileURLString(pMapTileIndex)
        val apiKey = BuildConfig.STADIA_API_KEY
        return if (apiKey.isNotBlank()) "$base?api_key=$apiKey" else base
    }
}

private val ITIN_STADIA_SMOOTH = ItineraryStadiaTileSource("ItinStadiaSmoothHD", "alidade_smooth")
private val ITIN_STADIA_DARK = ItineraryStadiaTileSource("ItinStadiaDarkHD", "alidade_smooth_dark")

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
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
    var lastPassedStopIndex by remember { mutableIntStateOf(-1) }

    // Previsoes em tempo real por linha e relogio continuo
    var liveDeparturesByLine by remember { mutableStateOf<Map<Int, List<TripDepartureItem>>>(emptyMap()) }
    var currentTimerTickerMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)
            currentTimerTickerMs = System.currentTimeMillis()
        }
    }

    // Localizacao do Usuario
    var currentUserLocation by remember { mutableStateOf<Location?>(null) }

    // Construcao dinamica dos passos do itinerario
    val stages = remember(itinerary, destination) {
        buildNavigationStages(itinerary, destination, dbHelper)
    }

    val currentStage = if (isNavigating && currentStageIndex in stages.indices) {
        stages[currentStageIndex]
    } else null

    val isDescentAlarmTriggered by NavigationStateHolder.isDescentAlarmTriggered.collectAsState()
    var liveVehicles by remember { mutableStateOf<List<LiveVehicleMarker>>(emptyList()) }
    var isFocusOnVehicle by remember { mutableStateOf(false) }

    val monitoredVehicle = remember(liveVehicles, currentStage, currentUserLocation) {
        if (liveVehicles.isEmpty()) null
        else {
            val targetPoint = when (currentStage) {
                is NavigationStage.WaitForTransit -> currentStage.stopPoint
                is NavigationStage.RideTransit -> currentStage.destinationPoint
                else -> null
            } ?: currentUserLocation?.let { GeoPoint(it.latitude, it.longitude) }

            if (targetPoint != null) {
                liveVehicles.minByOrNull { v ->
                    distanceMeters(v.latitude, v.longitude, targetPoint.latitude, targetPoint.longitude)
                }
            } else {
                liveVehicles.firstOrNull()
            }
        }
    }

    // Overlays do osmdroid
    val polylinesFolder = remember { FolderOverlay() }
    val directionOverlay = remember { RouteDirectionOverlay(context) }
    val markersFolder = remember { FolderOverlay() }
    val intermediateMarkersFolder = remember { FolderOverlay() }
    val vehiclesFolder = remember { FolderOverlay() }

    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        MapView(context).apply {
            val initialSource = if (isDarkTheme) ITIN_STADIA_DARK else ITIN_STADIA_SMOOTH
            setTileSource(initialSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = false
            minZoomLevel = 9.0
            maxZoomLevel = 20.0
            overlays.add(polylinesFolder)
            overlays.add(directionOverlay)
            overlays.add(intermediateMarkersFolder)
            overlays.add(markersFolder)
            overlays.add(vehiclesFolder)
        }
    }

    // Overlay de localizacao atual do usuario (GPS Puck azul com anel branco e sombra)
    val locationOverlay = remember(mapView) {
        val provider = GpsMyLocationProvider(context).apply {
            locationUpdateMinDistance = 2.0f
            locationUpdateMinTime = 1000L
        }
        val overlay = DFLocationOverlay(provider, mapView, context)

        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val lastGps = try { locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
        val lastNet = try { locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
        (lastGps ?: lastNet)?.let { loc ->
            overlay.onLocationChanged(loc, provider)
        }
        overlay
    }

    // Adiciona o overlay de localizacao acima das rotas e paradas
    LaunchedEffect(mapView) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
        mapView.invalidate()
    }

    // Registra LocationListener para atualizar a posicao e velocidade do usuario continuamente
    DisposableEffect(context) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                currentUserLocation = loc
                locationOverlay.onLocationChanged(loc, null)
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
            val best = lastGps ?: lastNet
            currentUserLocation = best
            if (best != null) {
                locationOverlay.onLocationChanged(best, null)
            }
        } catch (_: SecurityException) {}

        onDispose {
            try {
                lm?.removeUpdates(locListener)
            } catch (_: SecurityException) {}
            if (!isNavigating) {
                NavigationStateHolder.reset(context)
                TripNavigationTrackingService.stopTracking(context)
            }
        }
    }

    // Gerenciamento de ciclo de vida do mapa e GPS
    LaunchedEffect(isDarkTheme) {
        mapView.setTileSource(if (isDarkTheme) ITIN_STADIA_DARK else ITIN_STADIA_SMOOTH)
        mapView.postInvalidate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    locationOverlay.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    locationOverlay.disableMyLocation()
                    mapView.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    locationOverlay.disableMyLocation()
                    mapView.onDetach()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationOverlay.disableMyLocation()
            mapView.onDetach()
        }
    }

    // Desenha as rotas na tela com cor Cinza-ardosia (#455A64) para caminhadas
    LaunchedEffect(itinerary) {
        polylinesFolder.items.clear()
        markersFolder.items.clear()

        val allPoints = mutableListOf<GeoPoint>()
        val transitDirectionPoints = mutableListOf<GeoPoint>()

        itinerary.legs.forEach { leg ->
            val polylineStr = leg.polylineEncoded
            if (!polylineStr.isNullOrBlank()) {
                val decoded = PolylineDecoder.decode(polylineStr)
                val geoPoints = decoded.map { GeoPoint(it.first, it.second) }
                allPoints.addAll(geoPoints)
                if (leg.type == TripLegType.TRANSIT) {
                    transitDirectionPoints.addAll(geoPoints)
                }

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
        val firstTransitLeg = itinerary.legs.firstOrNull { it.type == TripLegType.TRANSIT }
        val transitColor = firstTransitLeg?.lines?.firstOrNull()?.colorHex?.let {
            try { android.graphics.Color.parseColor(it) } catch (_: Exception) { null }
        } ?: android.graphics.Color.parseColor("#00A859")
        directionOverlay.setPoints(transitDirectionPoints, transitColor)

        // Marcador de Origem
        if (origin != null) {
            val originPoint = GeoPoint(origin.latitude, origin.longitude)
            allPoints.add(originPoint)
            val originMarker = Marker(mapView).apply {
                position = originPoint
                setAnchor(Marker.ANCHOR_CENTER, 39f / 44f)
                icon = createPinMarkerDrawable(context, "#00A859", "A")
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
                setAnchor(Marker.ANCHOR_CENTER, 39f / 44f)
                icon = createPinMarkerDrawable(context, "#E53935", "B")
                title = destination.caption
            }
            markersFolder.add(destMarker)
        }

        // Desenha paradas pretas e marcadores brancos de subida/descida diretamente ao entrar na tela
        intermediateMarkersFolder.items.clear()
        itinerary.legs.forEach { leg ->
            if (leg.type == TripLegType.TRANSIT) {
                for (pt in leg.intermediateStopPoints) {
                    val m = Marker(mapView).apply {
                        position = GeoPoint(pt.first, pt.second)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createSmallDotDrawable(context)
                    }
                    intermediateMarkersFolder.add(m)
                }

                val decodedPoly = leg.polylineEncoded?.let { PolylineDecoder.decode(it) }
                val startLat = leg.departureStopLat
                    ?: (if (leg.departureStopId != null) dbHelper.getStopById(leg.departureStopId)?.latitude else null)
                    ?: decodedPoly?.firstOrNull()?.first
                val startLon = leg.departureStopLon
                    ?: (if (leg.departureStopId != null) dbHelper.getStopById(leg.departureStopId)?.longitude else null)
                    ?: decodedPoly?.firstOrNull()?.second
                if (startLat != null && startLon != null) {
                    val boardMarker = Marker(mapView).apply {
                        position = GeoPoint(startLat, startLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createWhiteStopMarkerDrawable(context, true)
                        title = leg.departureStopName ?: "Embarque"
                    }
                    markersFolder.add(boardMarker)
                }

                val endLat = leg.arrivalStopLat
                    ?: (if (leg.arrivalStopId != null) dbHelper.getStopById(leg.arrivalStopId)?.latitude else null)
                    ?: decodedPoly?.lastOrNull()?.first
                val endLon = leg.arrivalStopLon
                    ?: (if (leg.arrivalStopId != null) dbHelper.getStopById(leg.arrivalStopId)?.longitude else null)
                    ?: decodedPoly?.lastOrNull()?.second
                if (endLat != null && endLon != null) {
                    val alightMarker = Marker(mapView).apply {
                        position = GeoPoint(endLat, endLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createWhiteStopMarkerDrawable(context, false)
                        title = leg.arrivalStopName ?: "Desembarque"
                    }
                    markersFolder.add(alightMarker)
                }
            }
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
                vehiclesFolder.items.clear()
                liveVehicles = emptyList()
                NavigationStateHolder.isRideTransit = false
                mapView.invalidate()
                val target = stage.targetPoint
                if (target != null) {
                    mapView.controller.animateTo(target, 17.0, 700L)
                }
            }

            is NavigationStage.WaitForTransit -> {
                // Painel abre automaticamente a 75% da tela para visualizar os onibus e horarios
                isExpandedDeck = true
                NavigationStateHolder.isRideTransit = false
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
                lastPassedStopIndex = -1

                // Configura monitoramento e alarme de desembarque
                NavigationStateHolder.isRideTransit = true
                NavigationStateHolder.targetStopName = stage.destinationStopName
                NavigationStateHolder.targetStopLat = stage.destinationPoint?.latitude ?: 0.0
                NavigationStateHolder.targetStopLon = stage.destinationPoint?.longitude ?: 0.0
                NavigationStateHolder.intermediateStops = stage.intermediateStops.map { it.latitude to it.longitude }
                NavigationStateHolder.totalStopsCount = stage.totalStopsCount
                NavigationStateHolder.hasAlertedCurrentLeg = false
                NavigationStateHolder.isDescentAlarmTriggered.value = false
                NavigationStateHolder.lastPassedStopIndex = -1
                NavigationStateHolder.save(context)

                // Assegura que as paradas intermediarias estao no mapa
                if (intermediateMarkersFolder.items.isEmpty()) {
                    for (pt in stage.intermediateStops) {
                        val m = Marker(mapView).apply {
                            position = pt
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createSmallDotDrawable(context)
                        }
                        intermediateMarkersFolder.add(m)
                    }
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
                vehiclesFolder.items.clear()
                liveVehicles = emptyList()
                NavigationStateHolder.isRideTransit = false
                mapView.invalidate()
                val target = stage.destinationPoint
                if (target != null) {
                    mapView.controller.animateTo(target, 17.0, 700L)
                }
            }

            is NavigationStage.Completed -> {
                isExpandedDeck = false
                vehiclesFolder.items.clear()
                liveVehicles = emptyList()
                NavigationStateHolder.reset(context)
                TripNavigationTrackingService.stopTracking(context)
                mapView.invalidate()
            }

            null -> {}
        }
    }

    // Telemetria ao vivo dos onibus das linhas do trajeto durante a etapa WaitForTransit e RideTransit
    LaunchedEffect(currentStage) {
        val linesToTrack = when (currentStage) {
            is NavigationStage.WaitForTransit -> (currentStage as NavigationStage.WaitForTransit).candidateLines
            is NavigationStage.RideTransit -> {
                val ride = currentStage as NavigationStage.RideTransit
                if (ride.candidateLines.isNotEmpty()) ride.candidateLines
                else listOfNotNull(ride.selectedLine)
            }
            else -> emptyList()
        }

        val boardingStopId = (currentStage as? NavigationStage.WaitForTransit)?.stopId
        val targetStopGeoPoint = when (currentStage) {
            is NavigationStage.WaitForTransit -> currentStage.stopPoint
            is NavigationStage.RideTransit -> currentStage.destinationPoint
            else -> null
        }

        if (linesToTrack.isNotEmpty()) {
            val semobService = SemobTelemetryService()
            val dbHelper = DatabaseHelper(context)

            // Pre-carrega contexto de paradas e sentido para cada linha candidata
            val routeContexts = withContext(Dispatchers.IO) {
                linesToTrack.mapNotNull { lineOpt ->
                    val cleanNum = lineOpt.lineNumber.trim()
                    if (cleanNum.isBlank()) return@mapNotNull null
                    val expectedDir = dbHelper.getLineDirectionId(lineOpt.lineId)
                    val stops = dbHelper.getStopsForLine(lineOpt.lineId)
                    var targetIdx = if (boardingStopId != null) stops.indexOfFirst { it.stopId == boardingStopId } else -1
                    if (targetIdx == -1 && targetStopGeoPoint != null && stops.isNotEmpty()) {
                        val closest = stops.indices.minByOrNull { idx ->
                            val s = stops[idx]
                            distanceMeters(targetStopGeoPoint.latitude, targetStopGeoPoint.longitude, s.latitude, s.longitude)
                        }
                        if (closest != null) {
                            val s = stops[closest]
                            val d = distanceMeters(targetStopGeoPoint.latitude, targetStopGeoPoint.longitude, s.latitude, s.longitude)
                            if (d <= 350f) {
                                targetIdx = closest
                            }
                        }
                    }
                    val oppLineId = dbHelper.getOppositeDirectionLineId(lineOpt.lineId)
                    val oppStops = oppLineId?.let { dbHelper.getStopsForLine(it) } ?: emptyList()
                    RouteLineContext(
                        lineId = lineOpt.lineId,
                        lineNumber = cleanNum,
                        expectedDirectionId = expectedDir,
                        stops = stops,
                        targetStopIndex = targetIdx,
                        oppositeStops = oppStops
                    )
                }
            }
            val routeContextById = routeContexts.associateBy { it.lineId }

            fun shouldIncludeVehicle(v: LiveVehicleMarker, routeCtx: RouteLineContext): Boolean {
                val stops = routeCtx.stops
                val targetIdx = routeCtx.targetStopIndex
                // Se a linha nao possuir paradas ou nao passar na parada alvo, descarta imediatamente
                if (stops.isEmpty() || targetIdx < 0) {
                    return false
                }

                var minStopDist = Float.MAX_VALUE
                var closestIdx = -1
                for (i in stops.indices) {
                    val s = stops[i]
                    val d = distanceMeters(v.latitude, v.longitude, s.latitude, s.longitude)
                    if (d < minStopDist) {
                        minStopDist = d
                        closestIdx = i
                    }
                }

                // Se estiver a mais de 650m de qualquer parada da rota, nao faz parte deste trecho
                if (minStopDist > 650f) {
                    return false
                }

                // Pistas duplas e opostas: se estiver mais proximo da rota de volta do que da rota atual
                val oppStops = routeCtx.oppositeStops
                if (oppStops.isNotEmpty()) {
                    var minOppDist = Float.MAX_VALUE
                    for (os in oppStops) {
                        val od = distanceMeters(v.latitude, v.longitude, os.latitude, os.longitude)
                        if (od < minOppDist) {
                            minOppDist = od
                        }
                    }
                    if (minOppDist + 15f < minStopDist) {
                        return false
                    }
                }

                // Filtro Vetorial de Rumo (Bearing):
                // Se o onibus estiver em movimento (> 2 km/h) e possuir rumo de direcao valido (> 5 graus)
                if (v.speedKmh > 2f && v.bearing > 5f) {
                    val nextIdx = minOf(stops.size - 1, closestIdx + 1)
                    val prevIdx = maxOf(0, closestIdx - 1)
                    val routeHeading = if (closestIdx < stops.size - 1) {
                        calculateBearing(stops[closestIdx].latitude, stops[closestIdx].longitude, stops[nextIdx].latitude, stops[nextIdx].longitude)
                    } else {
                        calculateBearing(stops[prevIdx].latitude, stops[prevIdx].longitude, stops[closestIdx].latitude, stops[closestIdx].longitude)
                    }

                    val incomingHeading = if (prevIdx < closestIdx) {
                        calculateBearing(stops[prevIdx].latitude, stops[prevIdx].longitude, stops[closestIdx].latitude, stops[closestIdx].longitude)
                    } else routeHeading

                    val diffRoute = angleDifference(v.bearing, routeHeading)
                    val diffIncoming = angleDifference(v.bearing, incomingHeading)

                    // Se o rumo do onibus divergir mais de 75 graus dos vetores da rota neste trecho, esta no sentido contrario
                    if (diffRoute > 75f && diffIncoming > 75f) {
                        return false
                    }
                }

                // Regra: Se a parada mais proxima for posterior a parada alvo, o onibus ja passou
                if (closestIdx > targetIdx) {
                    return false
                }

                // Se estiver na propria parada de embarque:
                if (closestIdx == targetIdx) {
                    val targetStop = stops[targetIdx]
                    val distToTarget = distanceMeters(v.latitude, v.longitude, targetStop.latitude, targetStop.longitude)
                    if (distToTarget > 80f && targetIdx + 1 < stops.size) {
                        val nextStop = stops[targetIdx + 1]
                        val distToNext = distanceMeters(v.latitude, v.longitude, nextStop.latitude, nextStop.longitude)
                        val distTargetToNext = distanceMeters(targetStop.latitude, targetStop.longitude, nextStop.latitude, nextStop.longitude)
                        if (distToNext < distTargetToNext) {
                            return false
                        }
                    }
                }

                return true
            }

            fun calculateDistanceAlongRoute(
                vLat: Double,
                vLon: Double,
                stops: List<TransitStop>,
                closestIdx: Int,
                targetIdx: Int
            ): Float {
                if (closestIdx >= targetIdx || stops.isEmpty()) {
                    val target = stops.getOrNull(targetIdx) ?: return 0f
                    return distanceMeters(vLat, vLon, target.latitude, target.longitude)
                }
                var totalDist = distanceMeters(vLat, vLon, stops[closestIdx].latitude, stops[closestIdx].longitude)
                for (i in closestIdx until targetIdx) {
                    totalDist += distanceMeters(stops[i].latitude, stops[i].longitude, stops[i + 1].latitude, stops[i + 1].longitude)
                }
                return totalDist
            }

            fun getVehicleDistanceToTarget(v: LiveVehicleMarker): Float {
                val routeCtx = routeContexts.firstOrNull { it.lineNumber == v.lineNumber && it.targetStopIndex >= 0 }
                if (routeCtx != null && routeCtx.stops.isNotEmpty() && routeCtx.targetStopIndex >= 0) {
                    val stops = routeCtx.stops
                    val targetIdx = routeCtx.targetStopIndex
                    var closestIdx = 0
                    var minDist = Float.MAX_VALUE
                    for (i in 0..targetIdx) {
                        val d = distanceMeters(v.latitude, v.longitude, stops[i].latitude, stops[i].longitude)
                        if (d < minDist) {
                            minDist = d
                            closestIdx = i
                        }
                    }
                    return calculateDistanceAlongRoute(v.latitude, v.longitude, stops, closestIdx, targetIdx)
                }
                return Float.MAX_VALUE
            }

            while (isActive) {
                val markers = mutableListOf<LiveVehicleMarker>()

                // 1. Telemetria oficial da SEMOB em tempo real por numero de linha
                val distinctLines = linesToTrack.distinctBy { it.lineNumber.trim() }
                for (line in distinctLines) {
                    val cleanLine = line.lineNumber.trim()
                    val routeCtx = routeContexts.firstOrNull { it.lineNumber == cleanLine && it.targetStopIndex >= 0 } ?: continue
                    try {
                        val semobList = semobService.getLiveVehiclePositions(cleanLine)
                        for (sv in semobList) {
                            val vMarker = LiveVehicleMarker(
                                vehicleId = sv.vehicleId,
                                lineNumber = cleanLine,
                                latE6 = sv.latE6,
                                lonE6 = sv.lonE6,
                                speedKmh = sv.speedKmh,
                                bearing = sv.bearing,
                                directionId = sv.directionId
                            )
                            if (shouldIncludeVehicle(vMarker, routeCtx)) {
                                markers.add(vMarker)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 2. Previsoes e coordenadas de veiculos via Moovit (se houver parada de embarque)
                val newDeparturesMap = mutableMapOf<Int, List<TripDepartureItem>>()
                val nowMs = System.currentTimeMillis()
                if (boardingStopId != null) {
                    try {
                        val lineIdSet = linesToTrack.map { it.lineId }.toSet()
                        val moovitArrivals = MoovitTransitClient.fetchArrivalsForStop(boardingStopId)
                        val matched = moovitArrivals.filter { lineIdSet.contains(it.lineId) }

                        for (lArr in matched) {
                            val lineOpt = linesToTrack.firstOrNull { it.lineId == lArr.lineId }
                            val lineNum = lineOpt?.lineNumber ?: ""
                            val routeCtx = routeContextById[lArr.lineId]
                                ?: routeContexts.firstOrNull { it.lineNumber == lineNum && it.targetStopIndex >= 0 }

                            for (arr in lArr.arrivals) {
                                // Se a previsao ao vivo contem a coordenada GPS do veiculo em aproximacao
                                if (arr.vehicleLatE6 != null && arr.vehicleLonE6 != null) {
                                    val vId = arr.vehicleId ?: "moovit_${lArr.lineId}_${arr.vehicleLatE6}"
                                    val alreadyExists = markers.any {
                                        it.vehicleId == vId || (Math.abs(it.latE6 - arr.vehicleLatE6) < 150 && Math.abs(it.lonE6 - arr.vehicleLonE6) < 150)
                                    }
                                    if (!alreadyExists && routeCtx != null && routeCtx.targetStopIndex >= 0) {
                                        val mMarker = LiveVehicleMarker(
                                            vehicleId = vId,
                                            lineNumber = lineNum,
                                            latE6 = arr.vehicleLatE6,
                                            lonE6 = arr.vehicleLonE6,
                                            speedKmh = 0f,
                                            bearing = 0f,
                                            directionId = routeCtx.expectedDirectionId
                                        )
                                        if (shouldIncludeVehicle(mMarker, routeCtx)) {
                                            markers.add(mMarker)
                                        }
                                    }
                                }
                            }

                            val valid = lArr.arrivals.filter {
                                val target = it.rtEtdUTC ?: it.staticEtdUTC
                                target >= nowMs - 60_000L
                            }
                            val items = valid.map { arr ->
                                val isRt = arr.rtEtdUTC != null
                                val target = arr.rtEtdUTC ?: arr.staticEtdUTC
                                val mins = maxOf(0, Math.round((target - nowMs) / 60000.0).toInt())
                                val formatted = if (isRt) {
                                    if (mins <= 1) "Já" else "$mins min"
                                } else {
                                    formatEpochMsToHHMM(target)
                                }
                                TripDepartureItem(
                                    lineNumber = lineNum,
                                    timeFormatted = formatted,
                                    isRealtime = isRt,
                                    targetEpochMs = target,
                                    lineId = lArr.lineId
                                )
                            }.distinctBy { it.timeFormatted }.sortedBy { it.targetEpochMs }.take(5)
                            if (items.isNotEmpty()) {
                                newDeparturesMap[lArr.lineId] = items
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Sincroniza a posicao real dos onibus com os horarios do painel inferior
                for (line in linesToTrack) {
                    val lineNum = line.lineNumber.trim()
                    val closestVeh = markers
                        .filter { it.lineNumber == lineNum }
                        .minByOrNull { getVehicleDistanceToTarget(it) }

                    if (closestVeh != null) {
                        val dist = getVehicleDistanceToTarget(closestVeh)
                        if (dist < Float.MAX_VALUE) {
                            val mins = if (dist <= 150f) 0 else maxOf(1, Math.round(dist / 360.0).toInt())
                            val formatted = if (mins <= 1) "Já" else "$mins min"
                            val liveTargetEpoch = nowMs + (mins * 60_000L)

                            val liveDepItem = TripDepartureItem(
                                lineNumber = lineNum,
                                timeFormatted = formatted,
                                isRealtime = true,
                                targetEpochMs = liveTargetEpoch,
                                lineId = line.lineId
                            )

                            val existing = newDeparturesMap[line.lineId] ?: emptyList()
                            val remaining = existing.filter { it.targetEpochMs > (liveTargetEpoch + 90_000L) }
                            newDeparturesMap[line.lineId] = (listOf(liveDepItem) + remaining).take(5)
                        }
                    }
                }

                // Limita estritamente aos 5 onibus mais proximos da parada de embarque
                val top5Vehicles = markers
                    .distinctBy { it.vehicleId }
                    .filter { getVehicleDistanceToTarget(it) < Float.MAX_VALUE }
                    .sortedBy { getVehicleDistanceToTarget(it) }
                    .take(5)

                withContext(Dispatchers.Main) {
                    if (newDeparturesMap.isNotEmpty()) {
                        liveDeparturesByLine = newDeparturesMap
                    }
                    liveVehicles = top5Vehicles
                    vehiclesFolder.items.clear()
                    for (v in top5Vehicles) {
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(v.latitude, v.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createVehiclePuckDrawable(context, v.bearing)
                            title = "Linha ${v.lineNumber}"
                            relatedObject = v
                            infoWindow = TransitVehicleInfoWindow(mapView, isDarkTheme)
                            setInfoWindowAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_TOP)
                            setOnMarkerClickListener { m, _ ->
                                InfoWindow.closeAllInfoWindowsOn(mapView)
                                m.showInfoWindow()
                                true
                            }
                        }
                        vehiclesFolder.add(marker)
                    }
                    if (isFocusOnVehicle && monitoredVehicle != null) {
                        mapView.controller.animateTo(
                            GeoPoint(monitoredVehicle.latitude, monitoredVehicle.longitude),
                            17.5,
                            600L
                        )
                    }
                    mapView.invalidate()
                }

                delay(12_000L)
            }
        } else {
            vehiclesFolder.items.clear()
            liveVehicles = emptyList()
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
                // Atualiza o contador decrescente de paradas restantes com suporte a salto de GPS
                val fullStopList = stage.intermediateStops + listOfNotNull(stage.destinationPoint)
                if (fullStopList.isNotEmpty()) {
                    val closestIndex = fullStopList.indices.minByOrNull { i ->
                        distanceMeters(userLoc.latitude, userLoc.longitude, fullStopList[i].latitude, fullStopList[i].longitude)
                    } ?: -1

                    if (closestIndex >= 0) {
                        val distToClosest = distanceMeters(
                            userLoc.latitude, userLoc.longitude,
                            fullStopList[closestIndex].latitude, fullStopList[closestIndex].longitude
                        )
                        // Se o usuario estiver a ate 250m da parada mais proxima a frente
                        if (distToClosest < 250f && closestIndex > lastPassedStopIndex) {
                            lastPassedStopIndex = closestIndex
                            NavigationStateHolder.lastPassedStopIndex = closestIndex
                        }
                    }

                    val totalStopsInRide = fullStopList.size
                    val remaining = if (lastPassedStopIndex >= 0) {
                        (totalStopsInRide - 1 - lastPassedStopIndex).coerceAtLeast(1)
                    } else {
                        stage.totalStopsCount.coerceAtLeast(1)
                    }
                    remainingStopsCount = remaining
                    NavigationStateHolder.currentRemainingStops.value = remaining
                }

                val destPt = stage.destinationPoint
                if (destPt != null) {
                    val distToDest = distanceMeters(userLoc.latitude, userLoc.longitude, destPt.latitude, destPt.longitude)
                    NavigationStateHolder.currentDistToDescentMeters.value = distToDest

                    val notifPrefs = NotificationPreferences(context)
                    val advanceStops = notifPrefs.getSettings().descentAdvanceStops
                    val maxDistMeters = notifPrefs.getDescentDistanceMeters()

                    // Disparo do Alarme de Desembarque
                    if ((remainingStopsCount <= advanceStops || distToDest <= maxDistMeters) && !NavigationStateHolder.hasAlertedCurrentLeg) {
                        NavigationStateHolder.hasAlertedCurrentLeg = true
                        NavigationStateHolder.isDescentAlarmTriggered.value = true
                        TripDescentAlarmHelper.triggerDescentAlarm(
                            context = context,
                            destinationStopName = stage.destinationStopName,
                            remainingStops = remainingStopsCount,
                            distanceMeters = distToDest
                        )
                    }

                    if (distToDest <= 60f || (lastPassedStopIndex >= 0 && lastPassedStopIndex >= fullStopList.size - 1)) {
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
                isFocusOnVehicle = false
                NavigationStateHolder.isNavigating.value = true
                NavigationStateHolder.save(context)
                TripNavigationTrackingService.startTracking(context)
                val myLoc = locationOverlay.myLocation
                val userLoc = currentUserLocation
                val best = myLoc ?: userLoc?.let { GeoPoint(it.latitude, it.longitude) }
                if (best != null) {
                    mapView.controller.animateTo(best, 17.5, 800L)
                }
            },
            onCancelNavigation = {
                isNavigating = false
                currentStageIndex = 0
                isExpandedDeck = false
                isFocusOnVehicle = false
                NavigationStateHolder.reset(context)
                TripNavigationTrackingService.stopTracking(context)
                intermediateMarkersFolder.items.clear()
                vehiclesFolder.items.clear()
                liveVehicles = emptyList()
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

        // Botoes Flutuantes de Camera / Centralizacao (Item 5.2)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isNavigating) {
                // Botao Foco no Onibus em aproximacao
                Surface(
                    onClick = {
                        val veh = monitoredVehicle
                        if (veh != null) {
                            isFocusOnVehicle = true
                            mapView.controller.animateTo(
                                GeoPoint(veh.latitude, veh.longitude),
                                17.5,
                                750L
                            )
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Buscando veículo em aproximação...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = CircleShape,
                    color = if (isFocusOnVehicle) TransitGreen else if (isDarkTheme) Color(0xE61E2028) else Color(0xF2FFFFFF),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isFocusOnVehicle) TransitGreen else if (isDarkTheme) Color(0xFF333644) else Color(0xFFE2E4EB)
                    ),
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsBus,
                            contentDescription = "Focar no ônibus",
                            tint = if (isFocusOnVehicle) Color.White else if (monitoredVehicle != null) TransitGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Botao Minha Localizacao (GPS)
            Surface(
                onClick = {
                    isFocusOnVehicle = false
                    val myLoc = locationOverlay.myLocation
                    if (myLoc != null) {
                        mapView.controller.animateTo(myLoc, 17.0, 750L)
                    } else {
                        val userLoc = currentUserLocation
                        if (userLoc != null) {
                            mapView.controller.animateTo(GeoPoint(userLoc.latitude, userLoc.longitude), 17.0, 750L)
                        } else {
                            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                            val lastGps = try { lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
                            val lastNet = try { lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
                            val best = lastGps ?: lastNet
                            if (best != null) {
                                mapView.controller.animateTo(GeoPoint(best.latitude, best.longitude), 17.0, 750L)
                            }
                        }
                    }
                },
                shape = CircleShape,
                color = if (!isFocusOnVehicle && isNavigating) TransitGreen else if (isDarkTheme) Color(0xE61E2028) else Color(0xF2FFFFFF),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (!isFocusOnVehicle && isNavigating) TransitGreen else if (isDarkTheme) Color(0xFF333644) else Color(0xFFE2E4EB)
                ),
                shadowElevation = 3.dp,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Minha localização",
                        tint = if (!isFocusOnVehicle && isNavigating) Color.White else TransitGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

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
                liveDeparturesByLine = liveDeparturesByLine,
                currentTickerMs = currentTimerTickerMs,
                isDescentAlarmTriggered = isDescentAlarmTriggered,
                dbHelper = dbHelper,
                onToggleExpand = { isExpandedDeck = !isExpandedDeck },
                onLineSelectedToBoard = { line ->
                    selectedLineForRide = line
                    currentStageIndex = (currentStageIndex + 1).coerceAtMost(stages.size - 1)
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
                    val fareDesc = if (itinerary.isFareIntegrated) {
                        "${itinerary.formattedFare} (Integrado)"
                    } else {
                        itinerary.formattedFare
                    }
                    Text(
                        text = "$startHH - $endHH (${formatDuration(itinerary.totalDurationMinutes)}) • $fareDesc",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    liveDeparturesByLine: Map<Int, List<TripDepartureItem>>,
    currentTickerMs: Long,
    isDescentAlarmTriggered: Boolean,
    dbHelper: DatabaseHelper,
    onToggleExpand: () -> Unit,
    onLineSelectedToBoard: (TripTransitLineOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val animSpec = tween<Dp>(durationMillis = 90, easing = FastOutSlowInEasing)

    val isWaitForTransit = stage is NavigationStage.WaitForTransit

    val targetHeight = if (isWaitForTransit && isExpanded) {
        if (maxScreenHeight > 200.dp) maxScreenHeight * 0.75f else 540.dp
    } else if (isWaitForTransit) {
        200.dp
    } else if (stage is NavigationStage.RideTransit && isDescentAlarmTriggered) {
        155.dp
    } else {
        115.dp
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

    val currentIsExpanded by androidx.compose.runtime.rememberUpdatedState(isExpanded)
    val currentOnToggleExpand by androidx.compose.runtime.rememberUpdatedState(onToggleExpand)

    var dragOffsetY by remember { mutableStateOf(0f) }
    val dragModifier = Modifier.pointerInput(isWaitForTransit) {
        if (!isWaitForTransit) return@pointerInput
        detectVerticalDragGestures(
            onDragStart = { dragOffsetY = 0f },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                dragOffsetY += dragAmount
            },
            onDragEnd = {
                if (dragOffsetY < -20f && !currentIsExpanded) {
                    currentOnToggleExpand()
                } else if (dragOffsetY > 20f && currentIsExpanded) {
                    currentOnToggleExpand()
                }
                dragOffsetY = 0f
            },
            onDragCancel = {
                dragOffsetY = 0f
            }
        )
    }

    Surface(
        modifier = modifier
            .padding(horizontal = animatedPaddingH, vertical = animatedPaddingB)
            .height(animatedHeight)
            .then(if (isWaitForTransit && !isExpanded) dragModifier else Modifier),
        shape = deckShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            // Puxador central superior com suporte a gesto vertical e clique
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dragModifier)
                    .clickable(enabled = isWaitForTransit) { onToggleExpand() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(5.dp)
                        .clip(CircleShape)
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
                }

                is NavigationStage.WaitForTransit -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dragModifier)
                            .clickable(enabled = isWaitForTransit) { onToggleExpand() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    val liveGreenColor = if (isDark) TransitGreen else Color(0xFF00883E)

                    val sortedCandidateLines = remember(stage.candidateLines, liveDeparturesByLine, currentTickerMs / 30000L) {
                        stage.candidateLines.sortedWith(
                            compareBy<TripTransitLineOption> { line ->
                                val deps = liveDeparturesByLine[line.lineId]?.ifEmpty { null }
                                    ?: stage.initialUpcomingDepartures.filter { it.lineId == line.lineId }.ifEmpty { null }
                                val first = deps?.firstOrNull()
                                if (first != null) {
                                    if (first.isRealtime) {
                                        first.targetEpochMs
                                    } else {
                                        first.targetEpochMs + 100_000_000L
                                    }
                                } else {
                                    Long.MAX_VALUE
                                }
                            }.thenBy { it.lineNumber }
                        )
                    }

                    // Lista de cartoes exclusivos das linhas deste trajeto com horarios
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedCandidateLines.forEach { line ->
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

                                    // Horarios desta linha especifica
                                    val departures = liveDeparturesByLine[line.lineId]?.ifEmpty { null }
                                        ?: stage.initialUpcomingDepartures.filter { it.lineId == line.lineId }.ifEmpty { null }
                                        ?: remember(line.lineId, stage.stopId, currentTickerMs / 60000L) {
                                            if (stage.stopId != null) {
                                                val tz = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
                                                val cal = java.util.Calendar.getInstance(tz)
                                                val curSec = cal.get(java.util.Calendar.HOUR_OF_DAY) * 3600 + cal.get(java.util.Calendar.MINUTE) * 60 + cal.get(java.util.Calendar.SECOND)
                                                val secList: List<Int> = dbHelper.getNextScheduledDepartures(line.lineId, stage.stopId, curSec, 3)
                                                secList.map { sec: Int ->
                                                    val h = (sec / 3600) % 24
                                                    val m = (sec % 3600) / 60
                                                    TripDepartureItem(
                                                        lineNumber = line.lineNumber,
                                                        timeFormatted = String.format(Locale.ROOT, "%02d:%02d", h, m),
                                                        isRealtime = false,
                                                        targetEpochMs = currentTickerMs + ((sec - curSec) * 1000L),
                                                        lineId = line.lineId
                                                    )
                                                }
                                            } else emptyList()
                                        }

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
                                            departures.take(5).forEach { dep ->
                                                val chipBg = if (dep.isRealtime) {
                                                    if (isDark) Color(0x2400C853) else Color(0x1F00883E)
                                                } else {
                                                    if (isDark) Color(0xFF22242C) else Color(0xFFE5E7EB)
                                                }
                                                val textColor = if (dep.isRealtime) {
                                                    liveGreenColor
                                                } else {
                                                    if (isDark) Color(0xFFC0C2CC) else Color(0xFF374151)
                                                }
                                                val label = if (dep.isRealtime) {
                                                    val mins = maxOf(0, Math.round((dep.targetEpochMs - currentTickerMs) / 60000.0).toInt())
                                                    if (mins <= 1) "Já" else "$mins min"
                                                } else {
                                                    dep.timeFormatted
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(chipBg)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        if (dep.isRealtime) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Wifi,
                                                                contentDescription = "GPS ao vivo",
                                                                tint = liveGreenColor,
                                                                modifier = Modifier.size(9.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = if (dep.isRealtime) FontWeight.Bold else FontWeight.SemiBold,
                                                                fontSize = 10.sp
                                                            ),
                                                            color = textColor
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

                    if (isDescentAlarmTriggered) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFF9800).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Prepare-se para desembarcar!",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    leg.lines.forEach { lineOpt ->
                                        val badgeBg = try {
                                            Color(android.graphics.Color.parseColor(lineOpt.colorHex))
                                        } catch (_: Exception) {
                                            TransitGreen
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeBg)
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            if (lineOpt.isMetro) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DirectionsSubway,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                            }
                                            Text(
                                                text = lineOpt.lineNumber,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 13.sp
                                                ),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

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

                // 1. Usa as paradas intermediarias vindas da API Moovit (precisas para esta perna)
                val intermediatePoints = mutableListOf<GeoPoint>()
                if (leg.intermediateStopPoints.isNotEmpty()) {
                    intermediatePoints.addAll(leg.intermediateStopPoints.map { GeoPoint(it.first, it.second) })
                } else {
                    // Fallback para paradas do banco SQLite limitadas estritamente ao trecho entre embarque e desembarque
                    val firstLineId = leg.lines.firstOrNull()?.lineId
                    if (firstLineId != null && firstLineId > 0) {
                        val allLineStops = dbHelper.getStopsForLine(firstLineId)
                        if (allLineStops.size > 2) {
                            val firstSid = leg.departureStopId
                            val startIdx = if (firstSid != null) {
                                allLineStops.indexOfFirst { it.stopId == firstSid }.coerceAtLeast(0)
                            } else 0
                            val destIdx = if (destPoint != null) {
                                allLineStops.indexOfFirst {
                                    distanceMeters(it.latitude, it.longitude, destPoint.latitude, destPoint.longitude) < 120f
                                }
                            } else -1
                            val safeEndIdx = if (destIdx > startIdx) destIdx else allLineStops.size - 1
                            val slice = allLineStops.subList(
                                (startIdx + 1).coerceAtMost(allLineStops.size),
                                safeEndIdx.coerceAtLeast(0)
                            )
                            intermediatePoints.addAll(slice.map { GeoPoint(it.latitude, it.longitude) })
                        }
                    }
                }

                // 2. Trava geometrica rigorosa: elimina quaisquer pontos fora da rota desta perna (ex: voltas de linhas circulares)
                val validIntermediateStops = if (decodedPoints.isNotEmpty() && intermediatePoints.isNotEmpty()) {
                    intermediatePoints.filter { pt ->
                        val minDistance = decodedPoints.minOfOrNull { p ->
                            distanceMeters(pt.latitude, pt.longitude, p.first, p.second)
                        } ?: Float.MAX_VALUE
                        minDistance <= 120f
                    }
                } else {
                    intermediatePoints
                }

                // 3. Fallback se vazio e houver contagem de paradas: interpola pontos ao longo da propria polyline
                val finalIntermediateStops = if (validIntermediateStops.isEmpty() && leg.stopsCount > 1 && decodedPoints.size > 2) {
                    val count = leg.stopsCount - 1
                    val step = (decodedPoints.size / (count + 1)).coerceAtLeast(1)
                    val sampled = mutableListOf<GeoPoint>()
                    for (s in 1..count) {
                        val ptIdx = (s * step).coerceAtMost(decodedPoints.size - 2)
                        val pt = decodedPoints[ptIdx]
                        sampled.add(GeoPoint(pt.first, pt.second))
                    }
                    sampled
                } else {
                    validIntermediateStops
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
                        totalStopsCount = if (finalIntermediateStops.isNotEmpty()) finalIntermediateStops.size + 1 else leg.stopsCount.coerceAtLeast(1),
                        intermediateStops = finalIntermediateStops
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

private data class RouteLineContext(
    val lineId: Int,
    val lineNumber: String,
    val expectedDirectionId: Int?,
    val stops: List<TransitStop>,
    val targetStopIndex: Int,
    val oppositeStops: List<TransitStop> = emptyList()
)

private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val y = Math.sin(deltaLambda) * Math.cos(phi2)
    val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda)
    val theta = Math.atan2(y, x)
    return ((Math.toDegrees(theta) + 360.0) % 360.0).toFloat()
}

private fun angleDifference(a: Float, b: Float): Float {
    val diff = Math.abs((a - b) % 360f)
    return if (diff > 180f) 360f - diff else diff
}

private fun createVehiclePuckDrawable(
    context: Context,
    bearing: Float
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (32 * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 1. Sombra externa suave
    paint.color = android.graphics.Color.argb(55, 0, 0, 0)
    canvas.drawCircle(center, center + (1.2f * density), 13.5f * density, paint)

    // 2. Anel branco solido de alto contraste
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, 13.0f * density, paint)

    // 3. Preenchimento verde vibrante (#00C853)
    paint.color = android.graphics.Color.parseColor("#00C853")
    canvas.drawCircle(center, center, 10.5f * density, paint)

    // 4. Icone de onibus centralizado em vetor branco com recorte verde, farois e rodas
    drawVehicleBusIcon(canvas, center, density)

    // 5. Indicador direcional (chevron externo) se houver rumo valido
    if (bearing > 0f) {
        drawHeadingChevron(canvas, center, density, bearing)
    }

    return BitmapDrawable(context.resources, bitmap)
}

private fun drawVehicleBusIcon(canvas: Canvas, center: Float, density: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val w = 11f * density
    val h = 11f * density
    val left = center - w / 2f
    val top = center - h / 2f
    val right = center + w / 2f
    val bodyH = h - 2f * density

    // Corpo
    canvas.drawRoundRect(left, top, right, top + bodyH, 2.2f * density, 2.2f * density, paint)

    // Para-brisa (recorte verde #00C853)
    paint.color = android.graphics.Color.parseColor("#00C853")
    canvas.drawRoundRect(left + 1.2f * density, top + 1.4f * density, right - 1.2f * density, top + 4.8f * density, 1f * density, 1f * density, paint)

    // Farois
    val r = 0.8f * density
    canvas.drawCircle(left + 2.2f * density, top + bodyH - 2f * density, r, paint)
    canvas.drawCircle(right - 2.2f * density, top + bodyH - 2f * density, r, paint)

    // Rodas
    paint.color = android.graphics.Color.WHITE
    val bottom = center + h / 2f
    canvas.drawRect(left + 1.2f * density, top + bodyH, left + 2.8f * density, bottom, paint)
    canvas.drawRect(right - 2.8f * density, top + bodyH, right - 1.2f * density, bottom, paint)
}

private fun drawHeadingChevron(canvas: Canvas, center: Float, density: Float, bearing: Float) {
    canvas.save()
    canvas.rotate(bearing, center, center)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        val tipY = center - 14.5f * density
        val baseY = center - 10.5f * density
        val halfW = 3.5f * density
        moveTo(center, tipY)
        lineTo(center + halfW, baseY)
        lineTo(center - halfW, baseY)
        close()
    }
    canvas.drawPath(path, paint)
    canvas.restore()
}

private class TransitVehicleInfoWindow(
    mapView: MapView,
    private val isDarkTheme: Boolean
) : InfoWindow(createLayout(mapView.context, isDarkTheme), mapView) {

    private val badgeView: TextView = mView.findViewWithTag("badge")
    private val titleView: TextView = mView.findViewWithTag("title")
    private val subtitleView: TextView = mView.findViewWithTag("subtitle")

    init {
        mView.setOnClickListener {
            close()
        }
    }

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val v = marker.relatedObject as? LiveVehicleMarker
        if (v != null) {
            badgeView.text = v.lineNumber
            titleView.text = "Linha ${v.lineNumber}"
            val speedText = if (v.speedKmh > 1f) " • ${v.speedKmh.toInt()} km/h" else ""
            subtitleView.text = "Em aproximação$speedText"
        } else {
            badgeView.text = "Ônibus"
            titleView.text = marker.title ?: "Em viagem"
            subtitleView.text = "Em aproximação"
        }
    }

    override fun onClose() {
        // Nada a limpar
    }

    companion object {
        private fun createLayout(context: Context, isDark: Boolean): View {
            val density = context.resources.displayMetrics.density

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (12 * density).toInt()
                val padV = (8 * density).toInt()
                setPadding(padH, padV, padH, padV)

                val bg = GradientDrawable().apply {
                    cornerRadius = 16 * density
                    setColor(if (isDark) android.graphics.Color.parseColor("#1C1C1E") else android.graphics.Color.WHITE)
                    setStroke(
                        (1.2f * density).toInt(),
                        if (isDark) android.graphics.Color.parseColor("#38383A") else android.graphics.Color.parseColor("#E5E5EA")
                    )
                }
                background = bg
                elevation = 6 * density
            }

            val badge = TextView(context).apply {
                tag = "badge"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val bPadH = (8 * density).toInt()
                val bPadV = (4 * density).toInt()
                setPadding(bPadH, bPadV, bPadH, bPadV)

                val badgeBg = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(android.graphics.Color.parseColor("#00C853"))
                }
                background = badgeBg
            }
            root.addView(badge)

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val ml = (10 * density).toInt()
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = ml
                }
                layoutParams = params
            }

            val title = TextView(context).apply {
                tag = "title"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#1C1C1E"))
            }
            textCol.addView(title)

            val subtitle = TextView(context).apply {
                tag = "subtitle"
                textSize = 11f
                setTextColor(if (isDark) android.graphics.Color.parseColor("#A0A0A5") else android.graphics.Color.parseColor("#6C6C70"))
            }
            textCol.addView(subtitle)

            root.addView(textCol)
            return root
        }
    }
}

private fun createPinMarkerDrawable(context: Context, colorHex: String, letter: String): Drawable {
    val density = context.resources.displayMetrics.density
    val widthPx = (32 * density).toInt()
    val heightPx = (44 * density).toInt()
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = widthPx / 2f
    val cy = 14f * density
    val r = 12f * density
    val tipX = widthPx / 2f
    val tipY = 39f * density

    val pinColor = try {
        android.graphics.Color.parseColor(colorHex)
    } catch (_: Exception) {
        android.graphics.Color.parseColor("#00C853")
    }

    // Sombra oval no chão
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(70, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawOval(
        cx - (7f * density),
        tipY - (1.5f * density),
        cx + (7f * density),
        tipY + (3.5f * density),
        shadowPaint
    )

    // Curvatura elegante e contínua do Pin (Gota de localização moderna)
    val pinPath = Path().apply {
        moveTo(tipX, tipY)
        cubicTo(
            tipX - (1.5f * density), tipY - (7f * density),
            cx - r, cy + (5f * density),
            cx - r, cy
        )
        arcTo(
            cx - r, cy - r,
            cx + r, cy + r,
            180f, 180f, false
        )
        cubicTo(
            cx + r, cy + (5f * density),
            tipX + (1.5f * density), tipY - (7f * density),
            tipX, tipY
        )
        close()
    }

    // Preenchimento com cor vibrante do pin
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(pinPath, fillPaint)

    // Contorno branco nítido de alto contraste
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawPath(pinPath, borderPaint)

    // Medalhão circular branco no centro
    val innerDiscRadius = 6.2f * density
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, innerDiscRadius, discPaint)

    // Letra central em negrito destacada com a cor do marcador
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        textSize = 9.5f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(letter, cx, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createWhiteStopMarkerDrawable(context: Context, isBoarding: Boolean): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (18 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    // Sombra do marcador circular
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center, shadow)

    // Fundo branco
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center - (1.5f * density), whitePaint)

    // Borda escura de contorno para contraste absoluto
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E293B")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    canvas.drawCircle(center, center, center - (1.5f * density), borderPaint)

    // Ponto interno (Verde para subida/embarque, Vermelho para descida/desembarque)
    val innerDotColor = if (isBoarding) {
        android.graphics.Color.parseColor("#00C853")
    } else {
        android.graphics.Color.parseColor("#E53935")
    }
    val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = innerDotColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 3.8f * density, innerDotPaint)

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

/**
 * Overlay de localizacao moderno com puck azul e anel branco solido de alta precisao.
 */
private class DFLocationOverlay(
    provider: GpsMyLocationProvider,
    mapView: MapView,
    context: Context
) : MyLocationNewOverlay(provider, mapView) {
    init {
        val puckBitmap = createLocationPuckBitmap(context)
        setPersonIcon(puckBitmap)
        setDirectionIcon(puckBitmap)
        setPersonAnchor(0.5f, 0.5f)
        setDirectionAnchor(0.5f, 0.5f)
        enableMyLocation()
        setDrawAccuracyEnabled(true)
        mCirclePaint.apply {
            color = android.graphics.Color.parseColor("#221A73E8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }
}

/**
 * Cria o puck moderno de localizacao do usuario com disco azul, borda branca e sombra.
 */
private fun createLocationPuckBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (24 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Sombra suave (Drop Shadow)
    paint.color = android.graphics.Color.argb(45, 0, 0, 0)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center + (1f * density), 10.5f * density, paint)

    // 2. Anel branco solido de alto contraste
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, 10f * density, paint)

    // 3. Disco azul vibrante (Google / Transit Blue)
    paint.color = android.graphics.Color.parseColor("#1A73E8")
    canvas.drawCircle(center, center, 7.5f * density, paint)

    // 4. Ponto central branco
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, 2.5f * density, paint)

    return bitmap
}
