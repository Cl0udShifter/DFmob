package com.dfmob.ui.tripplanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dfmob.BuildConfig
import com.dfmob.data.model.PolylineDecoder
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLocation
import com.dfmob.ui.components.TripInsightBadge
import com.dfmob.ui.stops.components.DFLocationOverlay
import kotlinx.coroutines.launch
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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val COMPARISON_COLORS = listOf(
    "#1976D2", // 1. Azul
    "#7B1FA2", // 2. Roxo
    "#388E3C", // 3. Verde
    "#E65100", // 4. Laranja
    "#00838F"  // 5. Ciano/Teal
)

private class ComparisonStadiaTileSource(
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

private val COMP_STADIA_SMOOTH = ComparisonStadiaTileSource("CompStadiaSmoothHD", "alidade_smooth")
private val COMP_STADIA_DARK = ComparisonStadiaTileSource("CompStadiaDarkHD", "alidade_smooth_dark")

private data class DecodedPolylineSegment(
    val polyline: Polyline,
    val isWalk: Boolean,
    val baseColor: Int,
    val unselectedColor: Int
)

private data class RoutePolylineGroup(
    val itineraryIndex: Int,
    val segments: List<DecodedPolylineSegment>,
    val boundingBox: BoundingBox?
)

@Composable
fun TripComparisonMapScreen(
    itineraries: List<TripPlanItinerary>,
    origin: TripPlanLocation?,
    destination: TripPlanLocation?,
    onBack: () -> Unit,
    onSelectItinerary: (TripPlanItinerary) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var selectedIndex by remember { mutableIntStateOf(0) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val polylinesFolder = remember { FolderOverlay() }
    val markersFolder = remember { FolderOverlay() }

    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        MapView(context).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val initialSource = if (isDarkTheme) COMP_STADIA_DARK else COMP_STADIA_SMOOTH
            setTileSource(initialSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = false
            minZoomLevel = 9.0
            maxZoomLevel = 20.0
            overlays.add(polylinesFolder)
            overlays.add(markersFolder)
        }
    }

    val locationOverlay = remember(mapView) {
        val provider = GpsMyLocationProvider(context).apply {
            locationUpdateMinDistance = 5.0f
            locationUpdateMinTime = 3000L
        }
        DFLocationOverlay(provider, mapView, context)
    }

    LaunchedEffect(mapView) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
        mapView.invalidate()
    }

    LaunchedEffect(isDarkTheme) {
        mapView.setTileSource(if (isDarkTheme) COMP_STADIA_DARK else COMP_STADIA_SMOOTH)
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

    // 1. Decodifica polylines e marcadores UMA UNICA VEZ quando itineraries mudam
    val routeGroups = remember(itineraries, mapView) {
        polylinesFolder.items.clear()
        markersFolder.items.clear()

        val allRoutePoints = mutableListOf<GeoPoint>()

        val groups = itineraries.mapIndexed { idx, itin ->
            val hexColor = COMPARISON_COLORS[idx % COMPARISON_COLORS.size]
            val baseColor = try {
                android.graphics.Color.parseColor(hexColor)
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#1976D2")
            }
            val unselectedColor = android.graphics.Color.argb(
                90,
                android.graphics.Color.red(baseColor),
                android.graphics.Color.green(baseColor),
                android.graphics.Color.blue(baseColor)
            )

            val segments = mutableListOf<DecodedPolylineSegment>()
            val itinPoints = mutableListOf<GeoPoint>()

            itin.legs.forEach { leg ->
                val pts = leg.polylineEncoded?.let { PolylineDecoder.decode(it) } ?: emptyList()
                if (pts.isNotEmpty()) {
                    val geoPts = pts.map { GeoPoint(it.first, it.second) }
                    itinPoints.addAll(geoPts)
                    allRoutePoints.addAll(geoPts)

                    val isWalk = leg.type == TripLegType.WALK || leg.type == TripLegType.TRANSFER_WALK
                    val polyline = Polyline(mapView).apply {
                        setPoints(geoPts)
                    }
                    segments.add(
                        DecodedPolylineSegment(
                            polyline = polyline,
                            isWalk = isWalk,
                            baseColor = baseColor,
                            unselectedColor = unselectedColor
                        )
                    )
                    polylinesFolder.add(polyline)
                }
            }

            val bb = if (itinPoints.isNotEmpty()) {
                var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
                var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
                for (p in itinPoints) {
                    if (p.latitude < minLat) minLat = p.latitude
                    if (p.latitude > maxLat) maxLat = p.latitude
                    if (p.longitude < minLon) minLon = p.longitude
                    if (p.longitude > maxLon) maxLon = p.longitude
                }
                BoundingBox(maxLat, maxLon, minLat, minLon)
            } else null

            RoutePolylineGroup(
                itineraryIndex = idx,
                segments = segments,
                boundingBox = bb
            )
        }

        // Marcadores estaticos de Origem e Destino
        if (origin != null) {
            val originPoint = GeoPoint(origin.latitude, origin.longitude)
            allRoutePoints.add(originPoint)
            val originMarker = Marker(mapView).apply {
                position = originPoint
                setAnchor(Marker.ANCHOR_CENTER, 39f / 44f)
                icon = createComparisonPinMarker(context, "#00A859", "A")
                title = origin.caption
            }
            markersFolder.add(originMarker)
        }

        if (destination != null) {
            val destPoint = GeoPoint(destination.latitude, destination.longitude)
            allRoutePoints.add(destPoint)
            val destMarker = Marker(mapView).apply {
                position = destPoint
                setAnchor(Marker.ANCHOR_CENTER, 39f / 44f)
                icon = createComparisonPinMarker(context, "#E53935", "B")
                title = destination.caption
            }
            markersFolder.add(destMarker)
        }

        mapView.invalidate()
        groups
    }

    // 2. Atualiza estilos de traco a 120 FPS sem realocacoes quando selectedIndex muda
    LaunchedEffect(selectedIndex, routeGroups) {
        val walkDashEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
        val walkUnselectedDashEffect = DashPathEffect(floatArrayOf(9f, 9f), 0f)

        // As rotas nao-selecionadas sao atenuadas; a rota selecionada e destacada
        for (group in routeGroups) {
            val isSelected = group.itineraryIndex == selectedIndex
            for (segment in group.segments) {
                segment.polyline.outlinePaint.apply {
                    if (isSelected) {
                        if (segment.isWalk) {
                            color = android.graphics.Color.parseColor("#455A64")
                            strokeWidth = 11f
                            strokeCap = Paint.Cap.ROUND
                            pathEffect = walkDashEffect
                        } else {
                            color = segment.baseColor
                            strokeWidth = 16f
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            pathEffect = null
                        }
                    } else {
                        color = segment.unselectedColor
                        strokeWidth = 7.5f
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        pathEffect = if (segment.isWalk) walkUnselectedDashEffect else null
                    }
                }
            }
        }

        mapView.postInvalidate()

        // Reenquadra o mapa suavemente para a rota selecionada
        val targetBb = routeGroups.getOrNull(selectedIndex)?.boundingBox
        if (targetBb != null) {
            mapView.post {
                mapView.zoomToBoundingBox(targetBb, true, 110)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa base em tela cheia com aceleracao por hardware
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Barra Superior: Voltar, Titulo e Reenquadrar
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Comparar Rotas",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${itineraries.size} alternativas no mapa",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Botao para reenquadrar rota selecionada
                IconButton(
                    onClick = {
                        val targetBb = routeGroups.getOrNull(selectedIndex)?.boundingBox
                        if (targetBb != null) {
                            mapView.zoomToBoundingBox(targetBb, true, 100)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = "Reenquadrar rota",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Painel Inferior: Carrossel Horizontal com altura fixa (evita pulos verticais)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(itineraries) { idx, itin ->
                    val isSelected = idx == selectedIndex
                    val hexColor = COMPARISON_COLORS[idx % COMPARISON_COLORS.size]
                    val routeColor = Color(android.graphics.Color.parseColor(hexColor))

                    ComparisonItineraryCard(
                        itinerary = itin,
                        routeIndex = idx + 1,
                        routeColor = routeColor,
                        isSelected = isSelected,
                        isDarkTheme = isDarkTheme,
                        onCardClick = {
                            selectedIndex = idx
                            coroutineScope.launch {
                                lazyListState.animateScrollToItem(idx)
                            }
                        },
                        onOpenDetails = { onSelectItinerary(itin) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonItineraryCard(
    itinerary: TripPlanItinerary,
    routeIndex: Int,
    routeColor: Color,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onCardClick: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val durationText = remember(itinerary.totalDurationMinutes) {
        formatComparisonDuration(itinerary.totalDurationMinutes)
    }
    val startHH = remember(itinerary.startTimeUtc) {
        formatComparisonEpochMs(itinerary.startTimeUtc)
    }
    val endHH = remember(itinerary.endTimeUtc) {
        formatComparisonEpochMs(itinerary.endTimeUtc)
    }
    val cardInsights = remember(itinerary.insights, itinerary.insight) {
        itinerary.effectiveInsights
    }
    val visibleLegs = remember(itinerary.legs) {
        itinerary.legs.filter { leg ->
            when (leg.type) {
                TripLegType.WALK, TripLegType.TRANSFER_WALK -> leg.durationMinutes > 0 || leg.distanceMeters > 0
                TripLegType.TRANSIT -> leg.lines.isNotEmpty()
                else -> false
            }
        }
    }

    // Altura fixa de 178dp garante estabilidade vertical perfeita sem pulos durante o scroll
    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.5.dp else 1.dp,
            color = if (isSelected) routeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        shadowElevation = 3.dp,
        modifier = Modifier
            .width(300.dp)
            .height(178.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Top Row: Tag colorida da Rota + Dicas inteligentes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(routeColor)
                        )
                        Text(
                            text = "Opção $routeIndex",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            ),
                            color = routeColor
                        )
                    }

                    // Dicas inteligentes (ate 2)
                    if (cardInsights.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            cardInsights.take(2).forEach { insight ->
                                TripInsightBadge(
                                    insight = insight,
                                    isDarkTheme = isDarkTheme
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Linha principal: Duracao, Janela e Tarifa
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
                                fontSize = 19.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$startHH - $endHH",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Tarifa
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            if (itinerary.isFareIntegrated) {
                                Icon(
                                    imageVector = Icons.Rounded.SyncAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = itinerary.formattedFare,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Faixa de etapas compacta (sem scroll interno para nao conflitar com o carrossel)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val displayLegs = visibleLegs.take(4)
                    val remainingLegs = visibleLegs.size - displayLegs.size

                    displayLegs.forEachIndexed { index, leg ->
                        when (leg.type) {
                            TripLegType.WALK, TripLegType.TRANSFER_WALK -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${leg.durationMinutes}m",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TripLegType.TRANSIT -> {
                                val firstLine = leg.lines.firstOrNull()
                                val lineParsedColor = firstLine?.colorHex?.let {
                                    try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
                                } ?: Color(0xFF00796B)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(lineParsedColor)
                                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (firstLine?.isMetro == true) Icons.Rounded.DirectionsSubway else Icons.Rounded.DirectionsBus,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = firstLine?.lineNumber ?: "",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                            else -> {}
                        }

                        if (index < displayLegs.lastIndex) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    if (remainingLegs > 0) {
                        Text(
                            text = "+$remainingLegs",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Botao de acao: Ver Detalhes
            Button(
                onClick = onOpenDetails,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) routeColor else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 5.dp, horizontal = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                Text(
                    text = "Ver detalhes do trajeto",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

private fun formatComparisonDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}min"
        h > 0 -> "${h}h"
        else -> "${m} min"
    }
}

private fun formatComparisonEpochMs(epochMs: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"))
    cal.timeInMillis = epochMs
    return String.format(Locale.ROOT, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

private fun createComparisonPinMarker(context: Context, colorHex: String, letter: String): Drawable {
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
        android.graphics.Color.parseColor("#00A859")
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = pinColor
    }

    val path = Path().apply {
        moveTo(tipX, tipY)
        lineTo(cx - r * 0.72f, cy + r * 0.65f)
        arcTo(cx - r, cy - r, cx + r, cy + r, 140f, 260f, false)
        lineTo(tipX, tipY)
        close()
    }
    canvas.drawPath(path, paint)

    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(cx, cy, 7.5f * density, innerPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2)
    canvas.drawText(letter, cx, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
