package com.dfmob.ui.tripplanner

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.dfmob.data.model.TripPlanLeg
import com.dfmob.data.model.TripPlanLocation
import com.dfmob.ui.theme.TransitGreen
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
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

@Composable
fun TripItineraryDetailView(
    itinerary: TripPlanItinerary,
    origin: TripPlanLocation?,
    destination: TripPlanLocation?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current

    val polylinesFolder = remember { FolderOverlay() }
    val markersFolder = remember { FolderOverlay() }

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
            overlays.add(markersFolder)
        }
    }

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

    // Desenha os trajetos (polylines e marcadores de embarque/desembarque)
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
                        outlinePaint.apply {
                            color = android.graphics.Color.parseColor("#4A90E2")
                            strokeWidth = 10f
                            strokeCap = Paint.Cap.ROUND
                            pathEffect = DashPathEffect(floatArrayOf(16f, 16f), 0f)
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

        if (allPoints.isNotEmpty()) {
            val minLat = allPoints.minOf { it.latitude }
            val maxLat = allPoints.maxOf { it.latitude }
            val minLon = allPoints.minOf { it.longitude }
            val maxLon = allPoints.maxOf { it.longitude }

            val padLat = (maxLat - minLat).coerceAtLeast(0.005) * 0.25
            val padLon = (maxLon - minLon).coerceAtLeast(0.005) * 0.25

            val boundingBox = BoundingBox(
                maxLat + padLat,
                maxLon + padLon,
                minLat - padLat,
                minLon - padLon
            )
            mapView.post {
                mapView.zoomToBoundingBox(boundingBox, true, 80)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar de navegacao
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(14.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 4.dp
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

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Text(
                        text = "Detalhes do Trajeto",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val startHH = formatEpochMsToHHMM(itinerary.startTimeUtc)
                    val endHH = formatEpochMsToHHMM(itinerary.endTimeUtc)
                    Text(
                        text = "$startHH - $endHH (${formatDuration(itinerary.totalDurationMinutes)})",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TransitGreen
                    )
                }
            }
        }

        // Painel inferior deslizavel com passos detalhados
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .heightIn(max = 380.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header do resumo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Passo a passo",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${itinerary.walkDurationMinutes} min (${itinerary.walkDistanceMeters.toInt()}m)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // Lista de passos
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itinerary.legs.forEachIndexed { index, leg ->
                        ItineraryStepItem(
                            stepNumber = index + 1,
                            leg = leg,
                            isLast = index == itinerary.legs.size - 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItineraryStepItem(
    stepNumber: Int,
    leg: TripPlanLeg,
    isLast: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Indicador de icone da etapa
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (leg.type == TripLegType.WALK || leg.type == TripLegType.TRANSFER_WALK)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        TransitGreen.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (leg.type == TripLegType.WALK || leg.type == TripLegType.TRANSFER_WALK) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                val isMetro = leg.lines.firstOrNull()?.isMetro == true
                Icon(
                    imageVector = if (isMetro) Icons.Rounded.DirectionsSubway else Icons.Rounded.DirectionsBus,
                    contentDescription = null,
                    tint = TransitGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            when (leg.type) {
                TripLegType.WALK -> {
                    Text(
                        text = "Caminhe ${leg.durationMinutes} min (${leg.distanceMeters.toInt()}m)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    leg.arrivalStopName?.let { stop ->
                        Text(
                            text = "Até a parada: $stop",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TripLegType.TRANSFER_WALK -> {
                    Text(
                        text = "Transferência / Conexão (${leg.durationMinutes} min)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TripLegType.TRANSIT, TripLegType.WAIT -> {
                    val line = leg.lines.firstOrNull()
                    val lineNum = line?.lineNumber ?: "Ônibus"
                    val lineName = line?.lineName ?: ""

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = try {
                                android.graphics.Color.parseColor(line?.colorHex ?: "#00A859").let { Color(it) }
                            } catch (_: Exception) {
                                TransitGreen
                            }
                        ) {
                            Text(
                                text = lineNum,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (line?.hasLiveGps == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TransitGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    tint = TransitGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                val etaText = when (line.liveEtaMinutes) {
                                    null, 0 -> "Já"
                                    else -> "${line.liveEtaMinutes} min"
                                }
                                Text(
                                    text = etaText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = TransitGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    leg.departureStopName?.let { depStop ->
                        Text(
                            text = "Embarque: $depStop",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (leg.stopsCount > 0) {
                        Text(
                            text = "Permaneça por ${leg.stopsCount} paradas (${leg.durationMinutes} min)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    leg.arrivalStopName?.let { arrStop ->
                        Text(
                            text = "Desembarque: $arrStop",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun createCircleMarkerDrawable(context: Context, colorHex: String, letter: String): android.graphics.drawable.Drawable {
    val sizePx = (32 * context.resources.displayMetrics.density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

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

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
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
