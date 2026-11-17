package com.dfmob.ui.stops.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
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
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.TransitStop
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Basemaps minimalistas CartoDB no estilo Google Maps / Uber (apenas ruas e traçados dos prédios)
private val CARTO_POSITRON = XYTileSource(
    "CartoPositron",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/light_all/",
        "https://b.basemaps.cartocdn.com/rastertiles/light_all/",
        "https://c.basemaps.cartocdn.com/rastertiles/light_all/",
        "https://d.basemaps.cartocdn.com/rastertiles/light_all/"
    )
)

private val CARTO_DARK_MATTER = XYTileSource(
    "CartoDarkMatter",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/dark_all/",
        "https://b.basemaps.cartocdn.com/rastertiles/dark_all/",
        "https://c.basemaps.cartocdn.com/rastertiles/dark_all/",
        "https://d.basemaps.cartocdn.com/rastertiles/dark_all/"
    )
)

@Composable
fun RealOpenStreetMap(
    stops: List<TransitStop>,
    selectedStop: TransitStop?,
    focusLocation: DFLocation?,
    recenterTrigger: Long,
    onStopSelected: (TransitStop) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current

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
            minZoomLevel = 10.0
            maxZoomLevel = 20.0
            controller.setZoom(14.2)
            // Centro em Brasilia: Rodoviaria do Plano Piloto
            controller.setCenter(GeoPoint(-15.794167, -47.882778))
        }
    }

    // Overlay de localizacao atual do usuario com precisao e ponto pulsante
    val locationOverlay = remember {
        val provider = GpsMyLocationProvider(context).apply {
            locationUpdateMinDistance = 5.0f
            locationUpdateMinTime = 2000L
        }
        MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
            setDrawAccuracyEnabled(true)
        }
    }

    // Adiciona o overlay de localizacao se ainda nao estiver presente
    LaunchedEffect(mapView) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
            mapView.invalidate()
        }
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
            mapView.controller.animateTo(target, 16.0, 1000L)
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
                val lastGps = try { locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: SecurityException) { null }
                val lastNet = try { locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: SecurityException) { null }
                val best = lastGps ?: lastNet
                if (best != null) {
                    mapView.controller.animateTo(GeoPoint(best.latitude, best.longitude), 16.0, 1000L)
                } else {
                    mapView.controller.animateTo(GeoPoint(-15.794167, -47.882778), 15.0, 1000L)
                }
            }
        }
    }

    // Marcadores de paradas e estacoes com icones visuais claros e Viewport Culling
    LaunchedEffect(stops, selectedStop) {
        fun updateMarkers() {
            val currentZoom = mapView.zoomLevelDouble
            val bounds = mapView.boundingBox

            // Remove marcadores anteriores mantendo o locationOverlay
            val existingMarkers = mapView.overlays.filterIsInstance<Marker>()
            mapView.overlays.removeAll(existingMarkers)

            for (stop in stops) {
                // Zoom threshold: em visualizacao panoramica (< 11.5), exibe apenas metrô para evitar poluicao
                if (currentZoom < 11.5 && !stop.isMetro) continue

                // Viewport culling: descarta pontos fora da regiao visivel caso bounds seja valido
                if (bounds != null && (bounds.latNorth - bounds.latSouth) > 0.0001) {
                    if (stop.latitude < bounds.latSouth || stop.latitude > bounds.latNorth ||
                        stop.longitude < bounds.lonWest || stop.longitude > bounds.lonEast
                    ) {
                        continue
                    }
                }

                val marker = Marker(mapView).apply {
                    position = GeoPoint(stop.latitude, stop.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = stop.stopName

                    val isSelected = selectedStop?.stopId == stop.stopId
                    icon = createStopPinDrawable(
                        context = context,
                        isMetro = stop.isMetro,
                        isSelected = isSelected
                    )

                    setOnMarkerClickListener { _, _ ->
                        onStopSelected(stop)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }
            mapView.invalidate()
        }

        // Listener para recarregar markers quando o usuario da zoom ou move o mapa
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                updateMarkers()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                updateMarkers()
                return false
            }
        }
        mapView.addMapListener(mapListener)
        updateMarkers()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Cria pin em alta resolucao com iconografia distinta para paradas de onibus e estacoes de metro.
 */
private fun createStopPinDrawable(
    context: Context,
    isMetro: Boolean,
    isSelected: Boolean
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = ((if (isSelected) 36 else if (isMetro) 28 else 26) * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    if (isSelected) {
        // Aura externa luminosa
        val auraColor = if (isMetro) android.graphics.Color.parseColor("#55FFB800") else android.graphics.Color.parseColor("#5500DC64")
        paint.color = auraColor
        canvas.drawCircle(center, center, center * 0.98f, paint)

        // Borda branca solida
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.74f, paint)

        // Circulo de preenchimento
        val fillColor = if (isMetro) android.graphics.Color.parseColor("#006837") else android.graphics.Color.parseColor("#00C853")
        paint.color = fillColor
        canvas.drawCircle(center, center, center * 0.60f, paint)
    } else if (isMetro) {
        // Borda dourada caracteristica do Metro-DF
        paint.color = android.graphics.Color.parseColor("#FFB800")
        canvas.drawCircle(center, center, center * 0.95f, paint)

        // Borda branca intermediaria
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.82f, paint)

        // Preenchimento verde escuro oficial do Metro-DF
        paint.color = android.graphics.Color.parseColor("#006837")
        canvas.drawCircle(center, center, center * 0.70f, paint)
    } else {
        // Parada de Onibus Oficial: Borda branca + Verde Transit
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.95f, paint)

        paint.color = android.graphics.Color.parseColor("#00C853")
        canvas.drawCircle(center, center, center * 0.80f, paint)
    }

    // Desenho dos icones internos em vetor
    if (isMetro) {
        drawMetroIcon(canvas, center, density, isSelected)
    } else {
        drawBusIcon(canvas, center, density, isSelected)
    }

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Desenha silhueta de onibus com vidro dianteiro, farois e rodas.
 */
private fun drawBusIcon(canvas: Canvas, center: Float, density: Float, isSelected: Boolean) {
    val scale = if (isSelected) 1.25f else 1.0f
    val iconWidth = 11f * density * scale
    val iconHeight = 11f * density * scale

    val left = center - iconWidth / 2f
    val top = center - iconHeight / 2f
    val right = center + iconWidth / 2f
    val bottom = center + iconHeight / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Corpo do Onibus (Branco)
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.FILL
    val bodyRect = RectF(left, top, right, bottom - (2f * density * scale))
    canvas.drawRoundRect(bodyRect, 2.5f * density * scale, 2.5f * density * scale, paint)

    // Parabrisa dianteiro (recorte verde)
    paint.color = android.graphics.Color.parseColor("#00C853")
    val winLeft = left + (1.2f * density * scale)
    val winTop = top + (1.5f * density * scale)
    val winRight = right - (1.2f * density * scale)
    val winBottom = top + (4.5f * density * scale)
    canvas.drawRect(RectF(winLeft, winTop, winRight, winBottom), paint)

    // Farois dianteiros
    val headlightRadius = 0.85f * density * scale
    val headlightY = bottom - (3.5f * density * scale)
    canvas.drawCircle(left + (2.4f * density * scale), headlightY, headlightRadius, paint)
    canvas.drawCircle(right - (2.4f * density * scale), headlightY, headlightRadius, paint)

    // Rodas (Brancas)
    paint.color = android.graphics.Color.WHITE
    val wheelW = 1.6f * density * scale
    val wheelH = 1.6f * density * scale
    canvas.drawRect(left + (1.8f * density * scale), bottom - wheelH, left + (1.8f * density * scale) + wheelW, bottom, paint)
    canvas.drawRect(right - (1.8f * density * scale) - wheelW, bottom - wheelH, right - (1.8f * density * scale), bottom, paint)
}

/**
 * Desenha silhueta de trem/metro com janela panoramica, farol dourado e trilhos.
 */
private fun drawMetroIcon(canvas: Canvas, center: Float, density: Float, isSelected: Boolean) {
    val scale = if (isSelected) 1.25f else 1.0f
    val iconWidth = 11f * density * scale
    val iconHeight = 11f * density * scale

    val left = center - iconWidth / 2f
    val top = center - iconHeight / 2f
    val right = center + iconWidth / 2f
    val bottom = center + iconHeight / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Cabine do Metro (Branco)
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.FILL
    val cabinRect = RectF(left, top, right, bottom - (2f * density * scale))
    canvas.drawRoundRect(cabinRect, 3.5f * density * scale, 3.5f * density * scale, paint)

    // Vidro panoramico do Metro (recorte verde escuro)
    paint.color = android.graphics.Color.parseColor("#006837")
    val winLeft = left + (1.3f * density * scale)
    val winTop = top + (1.6f * density * scale)
    val winRight = right - (1.3f * density * scale)
    val winBottom = top + (4.8f * density * scale)
    canvas.drawRect(RectF(winLeft, winTop, winRight, winBottom), paint)

    // Luz central dourada do Metro
    paint.color = android.graphics.Color.parseColor("#FFB800")
    val lightRadius = 1.0f * density * scale
    val lightY = top + (6.2f * density * scale)
    canvas.drawCircle(center, lightY, lightRadius, paint)

    // Farois inferiores
    paint.color = android.graphics.Color.parseColor("#006837")
    val headlightRadius = 0.8f * density * scale
    val headlightY = bottom - (3.4f * density * scale)
    canvas.drawCircle(left + (2.2f * density * scale), headlightY, headlightRadius, paint)
    canvas.drawCircle(right - (2.2f * density * scale), headlightY, headlightRadius, paint)

    // Suportes / Trilhos
    paint.color = android.graphics.Color.WHITE
    val trackW = 1.6f * density * scale
    val trackH = 1.6f * density * scale
    canvas.drawRect(left + (1.6f * density * scale), bottom - trackH, left + (1.6f * density * scale) + trackW, bottom, paint)
    canvas.drawRect(right - (1.6f * density * scale) - trackW, bottom - trackH, right - (1.6f * density * scale), bottom, paint)
}
