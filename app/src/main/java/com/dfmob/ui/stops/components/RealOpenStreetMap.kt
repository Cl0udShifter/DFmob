package com.dfmob.ui.stops.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dfmob.BuildConfig
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.LineRouteState
import com.dfmob.data.model.TransitStop
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Basemaps minimalistas CartoDB no estilo Google Maps / Uber com injecao segura de API key via BuildConfig
private class CartoTileSource(
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

private val CARTO_POSITRON = CartoTileSource("CartoPositronV2", "light_all")
private val CARTO_DARK_MATTER = CartoTileSource("CartoDarkMatterV2", "dark_all")

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
 * Cache em memoria dos 4 BitmapDrawables reutilizaveis para evitar alocacoes a 60 FPS.
 */
private class StopPinCache(context: Context) {
    val busNormal: BitmapDrawable = createBusStopDrawable(context, isSelected = false)
    val busSelected: BitmapDrawable = createBusStopDrawable(context, isSelected = true)
    val metroNormal: BitmapDrawable = createMetroStopDrawable(context, isSelected = false)
    val metroSelected: BitmapDrawable = createMetroStopDrawable(context, isSelected = true)
}

@Composable
fun RealOpenStreetMap(
    stops: List<TransitStop>,
    selectedStop: TransitStop?,
    focusLocation: DFLocation?,
    recenterTrigger: Long,
    onStopSelected: (TransitStop) -> Unit,
    activeRoute: LineRouteState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current

    val pinCache = remember(context) { StopPinCache(context) }
    val routePolyline = remember { Polyline() }
    val stopsFolder = remember { FolderOverlay() }
    val vehiclesFolder = remember { FolderOverlay() }
    val markerMap = remember { mutableMapOf<Int, Marker>() }

    // Configuracao inicial do osmdroid com persistencia em cache local
    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = "com.dfmob"

        MapView(context).apply {
            val initialSource = if (isDarkTheme) CARTO_DARK_MATTER else CARTO_POSITRON
            setTileSource(initialSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 9.0
            maxZoomLevel = 20.0
            controller.setZoom(15.5)
            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val lastGps = try { locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
            val lastNet = try { locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
            val initialLoc = lastGps ?: lastNet
            if (initialLoc != null) {
                controller.setCenter(GeoPoint(initialLoc.latitude, initialLoc.longitude))
            } else {
                controller.setCenter(GeoPoint(-15.794167, -47.882778))
            }
        }
    }

    // Overlay de localizacao atual do usuario
    val locationOverlay = remember {
        val provider = GpsMyLocationProvider(context).apply {
            locationUpdateMinDistance = 3.0f
            locationUpdateMinTime = 1500L
        }
        val overlay = DFLocationOverlay(provider, mapView, context)

        // Seed imediato do ultimo ponto GPS/Rede conhecido para evitar atraso ou sumico inicial
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val lastGps = try { locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
        val lastNet = try { locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
        (lastGps ?: lastNet)?.let { loc ->
            overlay.onLocationChanged(loc, provider)
        }
        overlay
    }

    // Adiciona os overlays em ordem hierarquica: Polylines -> Paradas -> Veiculos -> Localizacao Usuario
    LaunchedEffect(mapView) {
        if (!mapView.overlays.contains(routePolyline)) {
            mapView.overlays.add(routePolyline)
        }
        if (!mapView.overlays.contains(stopsFolder)) {
            mapView.overlays.add(stopsFolder)
        }
        if (!mapView.overlays.contains(vehiclesFolder)) {
            mapView.overlays.add(vehiclesFolder)
        }
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
        mapView.invalidate()
    }

    // Gerenciamento de ciclo de vida da MapView nativa e GPS
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

    // Adaptacao dinamica de tema Claro / Escuro usando basemaps CartoDB dedicados
    LaunchedEffect(isDarkTheme) {
        val tileSource = if (isDarkTheme) CARTO_DARK_MATTER else CARTO_POSITRON
        mapView.setTileSource(tileSource)
        mapView.overlayManager.tilesOverlay.setColorFilter(null)
        mapView.invalidate()
    }

    // Foca suavemente a camera no local selecionado na barra de busca
    LaunchedEffect(focusLocation) {
        if (focusLocation != null) {
            val target = GeoPoint(focusLocation.latitude, focusLocation.longitude)
            mapView.controller.animateTo(target, 16.5, 750L)
        }
    }

    // Centraliza na localizacao atual do usuario ao acionar o botao
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0L) {
            val myLoc = locationOverlay.myLocation
            if (myLoc != null) {
                mapView.controller.animateTo(myLoc, 16.0, 1000L)
            } else {
                val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val lastGps = try { locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
                val lastNet = try { locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
                val best = lastGps ?: lastNet
                if (best != null) {
                    mapView.controller.animateTo(GeoPoint(best.latitude, best.longitude), 16.0, 1000L)
                } else {
                    mapView.controller.animateTo(GeoPoint(-15.794167, -47.882778), 15.0, 1000L)
                }
            }
        }
    }

    // Atualizacao da Linha da Rota (Polyline) no mapa com a cor oficial da linha
    LaunchedEffect(activeRoute?.activeLineId, activeRoute?.currentDirectionIndex) {
        val route = activeRoute
        if (route != null && route.stops.isNotEmpty()) {
            val geoPoints = route.stops.map { GeoPoint(it.latitude, it.longitude) }
            val strokeColor = try {
                android.graphics.Color.parseColor(route.colorHex)
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#00C853")
            }
            routePolyline.outlinePaint.apply {
                color = strokeColor
                strokeWidth = 6f * context.resources.displayMetrics.density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            routePolyline.setPoints(geoPoints)
            routePolyline.isEnabled = true
        } else {
            routePolyline.setPoints(emptyList())
            routePolyline.isEnabled = false
        }
        mapView.invalidate()
    }

    // Atualizacao dos Veiculos Reais em Tempo Real (Pucks azuis com direcao e sem prefixo)
    LaunchedEffect(activeRoute?.liveVehicles) {
        vehiclesFolder.items.clear()
        val vehicles = activeRoute?.liveVehicles ?: emptyList()
        for (v in vehicles) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(v.latitude, v.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createVehiclePuckDrawable(context, v.bearing)
                infoWindow = null
            }
            vehiclesFolder.add(marker)
        }
        mapView.invalidate()
    }

    // Centraliza na parada selecionada na fita de itinerario da linha
    LaunchedEffect(activeRoute?.selectedStop?.stopId) {
        val s = activeRoute?.selectedStop
        if (s != null) {
            mapView.controller.animateTo(GeoPoint(s.latitude, s.longitude), 16.0, 500L)
        }
    }

    // Gerenciador de marcadores de paradas (filtragem por rota se ativa ou culling geral)
    LaunchedEffect(stops, selectedStop, activeRoute) {
        suspend fun updateMarkers() {
            if (activeRoute != null) {
                // Modo ROTA DA LINHA: todas as outras paradas somem, apenas as da linha sao exibidas
                val routeStops = activeRoute.stops
                val currentSelectedStopId = activeRoute.selectedStop?.stopId

                withContext(Dispatchers.Main) {
                    val targetIds = routeStops.map { it.stopId }.toSet()
                    val iterator = markerMap.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (entry.key !in targetIds) {
                            stopsFolder.remove(entry.value)
                            iterator.remove()
                        }
                    }

                    for (stop in routeStops) {
                        val isSelected = (currentSelectedStopId == stop.stopId)
                        val iconDrawable = when {
                            stop.isMetro && isSelected -> pinCache.metroSelected
                            stop.isMetro -> pinCache.metroNormal
                            isSelected -> pinCache.busSelected
                            else -> pinCache.busNormal
                        }

                        val existingMarker = markerMap[stop.stopId]
                        if (existingMarker != null) {
                            if (existingMarker.icon != iconDrawable) {
                                existingMarker.icon = iconDrawable
                            }
                        } else {
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(stop.latitude, stop.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = stop.stopName
                                icon = iconDrawable
                                setOnMarkerClickListener { _, _ ->
                                    onStopSelected(stop)
                                    true
                                }
                            }
                            stopsFolder.add(marker)
                            markerMap[stop.stopId] = marker
                        }
                    }
                    mapView.invalidate()
                }
                return
            }

            val currentZoom = mapView.zoomLevelDouble
            val bounds = mapView.boundingBox

            // 1. Zoom out total (< 11.5): limpa marcadores
            if (currentZoom < 11.5) {
                if (markerMap.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        stopsFolder.items.clear()
                        markerMap.clear()
                        mapView.invalidate()
                    }
                }
                return
            }

            // 2. Zoom intermediario (11.5 a 13.5): exibe apenas as estacoes de metrô
            val isIntermediateZoom = currentZoom < 13.5

            // Calculo de filtragem dos 1435 pontos executado em thread de background (Dispatchers.Default)
            val visibleStops = withContext(Dispatchers.Default) {
                stops.filter { stop ->
                    if (isIntermediateZoom && !stop.isMetro) {
                        return@filter false
                    }
                    if (bounds != null && (bounds.latNorth - bounds.latSouth) > 0.0001) {
                        if (stop.latitude < bounds.latSouth - 0.008 || stop.latitude > bounds.latNorth + 0.008 ||
                            stop.longitude < bounds.lonWest - 0.008 || stop.longitude > bounds.lonEast + 0.008
                        ) {
                            return@filter false
                        }
                    }
                    true
                }
            }

            // Aplicacao diferencial na Main Thread: reaproveita marcadores existentes sem destruir/recriar objetos
            withContext(Dispatchers.Main) {
                val newIds = visibleStops.map { it.stopId }.toSet()
                var hasChanges = false

                // Remove apenas os marcadores que sairam do campo de visao
                val iterator = markerMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key !in newIds) {
                        stopsFolder.remove(entry.value)
                        iterator.remove()
                        hasChanges = true
                    }
                }

                // Adiciona novos marcadores ou atualiza icones dos que ja estao na tela
                for (stop in visibleStops) {
                    val isSelected = selectedStop?.stopId == stop.stopId
                    val iconDrawable = when {
                        stop.isMetro && isSelected -> pinCache.metroSelected
                        stop.isMetro -> pinCache.metroNormal
                        isSelected -> pinCache.busSelected
                        else -> pinCache.busNormal
                    }

                    val existingMarker = markerMap[stop.stopId]
                    if (existingMarker != null) {
                        if (existingMarker.icon != iconDrawable) {
                            existingMarker.icon = iconDrawable
                            hasChanges = true
                        }
                    } else {
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(stop.latitude, stop.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = stop.stopName
                            icon = iconDrawable
                            setOnMarkerClickListener { _, _ ->
                                onStopSelected(stop)
                                true
                            }
                        }
                        stopsFolder.add(marker)
                        markerMap[stop.stopId] = marker
                        hasChanges = true
                    }
                }

                if (hasChanges) {
                    mapView.invalidate()
                }
            }
        }

        var updateJob: kotlinx.coroutines.Job? = null
        fun scheduleDebouncedUpdate(delayMs: Long) {
            updateJob?.cancel()
            updateJob = launch {
                delay(delayMs)
                updateMarkers()
            }
        }

        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (activeRoute == null) {
                    scheduleDebouncedUpdate(160L)
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                if (activeRoute == null) {
                    scheduleDebouncedUpdate(100L)
                }
                return false
            }
        }

        mapView.addMapListener(mapListener)
        updateMarkers()

        try {
            awaitCancellation()
        } finally {
            mapView.removeMapListener(mapListener)
            updateJob?.cancel()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
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

/**
 * Cria o marcador visual para Paradas de Onibus no padrao Variante A (Frente de Onibus Transit).
 */
private fun createBusStopDrawable(
    context: Context,
    isSelected: Boolean
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = ((if (isSelected) 36 else 26) * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    if (isSelected) {
        // Aura externa luminosa verde
        paint.color = android.graphics.Color.parseColor("#5500DC64")
        canvas.drawCircle(center, center, center * 0.98f, paint)

        // Borda branca solida
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.76f, paint)

        // Preenchimento verde Transit oficial
        paint.color = android.graphics.Color.parseColor("#00C853")
        canvas.drawCircle(center, center, center * 0.62f, paint)
    } else {
        // Borda branca solida
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.95f, paint)

        // Preenchimento verde Transit oficial
        paint.color = android.graphics.Color.parseColor("#00C853")
        canvas.drawCircle(center, center, center * 0.80f, paint)
    }

    drawBusFrontTransitIcon(canvas, center, density, isSelected)
    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Desenha o icone frontal estilizado de onibus Transit (Variante A) em vetor branco com recortes limpos.
 */
private fun drawBusFrontTransitIcon(canvas: Canvas, center: Float, density: Float, isSelected: Boolean) {
    val scale = if (isSelected) 1.25f else 1.0f
    val iconWidth = 12f * density * scale
    val iconHeight = 12f * density * scale

    val left = center - iconWidth / 2f
    val top = center - iconHeight / 2f
    val right = center + iconWidth / 2f
    val bottom = center + iconHeight / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Corpo principal do onibus (Branco solido com cantos arredondados)
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.FILL
    val bodyHeight = iconHeight - (2.2f * density * scale)
    canvas.drawRoundRect(left, top, right, top + bodyHeight, 2.5f * density * scale, 2.5f * density * scale, paint)

    // 2. Para-brisa frontal panoramico (recorte verde Transit)
    paint.color = android.graphics.Color.parseColor("#00C853")
    val winPadH = 1.4f * density * scale
    val winPadTop = 1.6f * density * scale
    val winH = 4.0f * density * scale
    canvas.drawRoundRect(
        left + winPadH,
        top + winPadTop,
        right - winPadH,
        top + winPadTop + winH,
        1.2f * density * scale,
        1.2f * density * scale,
        paint
    )

    // 3. Farois dianteiros circulares (recorte verde)
    val headlightRadius = 0.9f * density * scale
    val headlightY = top + bodyHeight - (2.5f * density * scale)
    canvas.drawCircle(left + (2.5f * density * scale), headlightY, headlightRadius, paint)
    canvas.drawCircle(right - (2.5f * density * scale), headlightY, headlightRadius, paint)

    // 4. Rodas dianteiras (Branco solido)
    paint.color = android.graphics.Color.WHITE
    val wheelW = 1.6f * density * scale
    canvas.drawRect(left + (1.5f * density * scale), top + bodyHeight, left + (1.5f * density * scale) + wheelW, bottom, paint)
    canvas.drawRect(right - (1.5f * density * scale) - wheelW, top + bodyHeight, right - (1.5f * density * scale), bottom, paint)
}

/**
 * Cria o marcador visual para Estacoes de Metro (Totem "M" com anel dourado).
 */
private fun createMetroStopDrawable(
    context: Context,
    isSelected: Boolean
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = ((if (isSelected) 38 else 28) * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    if (isSelected) {
        // Aura externa luminosa dourada
        paint.color = android.graphics.Color.parseColor("#55FFB800")
        canvas.drawCircle(center, center, center * 0.98f, paint)

        // Borda dourada
        paint.color = android.graphics.Color.parseColor("#FFB800")
        canvas.drawCircle(center, center, center * 0.78f, paint)

        // Borda branca intermediaria
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.70f, paint)

        // Preenchimento verde escuro oficial do Metro-DF
        paint.color = android.graphics.Color.parseColor("#006837")
        canvas.drawCircle(center, center, center * 0.60f, paint)
    } else {
        // Borda dourada caracteristica do Metro-DF
        paint.color = android.graphics.Color.parseColor("#FFB800")
        canvas.drawCircle(center, center, center * 0.95f, paint)

        // Borda branca intermediaria
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.82f, paint)

        // Preenchimento verde escuro oficial do Metro-DF
        paint.color = android.graphics.Color.parseColor("#006837")
        canvas.drawCircle(center, center, center * 0.70f, paint)
    }

    drawMetroIcon(canvas, center, density, isSelected)
    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Desenha o icone oficial "M" do Metro-DF em vetor.
 */
private fun drawMetroIcon(canvas: Canvas, center: Float, density: Float, isSelected: Boolean) {
    val scale = if (isSelected) 1.25f else 1.0f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    val halfW = 7.0f * density * scale
    val topY = center - (5.5f * density * scale)
    val bottomY = center + (6.0f * density * scale)
    val midVTop = center - (1.0f * density * scale)
    val midVBottom = center + (2.5f * density * scale)
    val colW = 2.4f * density * scale

    val path = Path().apply {
        moveTo(center - halfW, bottomY)
        lineTo(center - halfW, topY)
        lineTo(center - halfW + colW, topY)
        lineTo(center, midVBottom)
        lineTo(center + halfW - colW, topY)
        lineTo(center + halfW, topY)
        lineTo(center + halfW, bottomY)
        lineTo(center + halfW - colW, bottomY)
        lineTo(center + halfW - colW, midVTop)
        lineTo(center, midVBottom + (2.0f * density * scale))
        lineTo(center - halfW + colW, midVTop)
        lineTo(center - halfW + colW, bottomY)
        close()
    }
    canvas.drawPath(path, paint)
}

/**
 * Cria o puck moderno para Veiculos Reais em Transito (Padrao azul royal vibrante com indicador de direcao).
 * Visivelmente distinto do pino de paradas verde para evitar confusao visual.
 * Nao contem texto de prefixo conforme instrucao.
 */
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

    // 3. Preenchimento azul vibrante (distinto do verde de paradas)
    paint.color = android.graphics.Color.parseColor("#1565C0")
    canvas.drawCircle(center, center, 10.5f * density, paint)

    // 4. Icone de onibus centralizado em vetor branco
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

    // Para-brisa (recorte azul)
    paint.color = android.graphics.Color.parseColor("#1565C0")
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
