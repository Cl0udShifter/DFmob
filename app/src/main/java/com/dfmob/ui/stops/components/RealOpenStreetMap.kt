package com.dfmob.ui.stops.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun RealOpenStreetMap(
    stops: List<TransitStop>,
    selectedStop: TransitStop?,
    focusLocation: DFLocation?,
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
            setTileSource(TileSourceFactory.MAPNIK)
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

    // Gerenciamento de ciclo de vida da MapView nativa
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Adaptacao dinamica de tema Claro / Escuro no mapa real do OpenStreetMap
    LaunchedEffect(isDarkTheme) {
        if (isDarkTheme) {
            // Filtro escuro OLED de alto contraste para tiles OSM
            val darkMatrix = ColorMatrix().apply {
                setSaturation(0.25f)
                val darkTone = floatArrayOf(
                    -0.85f, 0f, 0f, 0f, 220f,
                    0f, -0.85f, 0f, 0f, 220f,
                    0f, 0f, -0.85f, 0f, 220f,
                    0f, 0f, 0f, 1f, 0f
                )
                postConcat(ColorMatrix(darkTone))
            }
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkMatrix))
        } else {
            // Modo Claro: tiles coloridos padrao do OpenStreetMap
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
        mapView.invalidate()
    }

    // Foca suavemente a camera no local selecionado na busca
    LaunchedEffect(focusLocation) {
        if (focusLocation != null) {
            val target = GeoPoint(focusLocation.latitude, focusLocation.longitude)
            mapView.controller.animateTo(target, 16.0, 1000L)
        }
    }

    // Marcadores de paradas e estacoes com Zoom Threshold e Viewport Culling (Moovit pattern)
    LaunchedEffect(stops, selectedStop) {
        fun updateMarkers() {
            val currentZoom = mapView.zoomLevelDouble
            val bounds = mapView.boundingBox

            // Remove marcadores anteriores
            val existingMarkers = mapView.overlays.filterIsInstance<Marker>()
            mapView.overlays.removeAll(existingMarkers)

            for (stop in stops) {
                // Zoom threshold: no zoom afastado (< 14.5), exibe apenas metrô
                if (currentZoom < 14.5 && !stop.isMetro) continue

                // Viewport culling: descarta pontos fora da regiao visivel
                if (bounds != null) {
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
 * Cria pin de alta resolucao para paradas de onibus e estacoes de metro.
 */
private fun createStopPinDrawable(
    context: Context,
    isMetro: Boolean,
    isSelected: Boolean
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = ((if (isSelected) 34 else if (isMetro) 26 else 22) * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    if (isSelected) {
        // Aura externa verde luminosa
        paint.color = android.graphics.Color.parseColor("#4400DC64")
        canvas.drawCircle(center, center, center * 0.98f, paint)

        // Borda branca
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.72f, paint)

        // Circulo central verde Transit
        paint.color = android.graphics.Color.parseColor("#00DC64")
        canvas.drawCircle(center, center, center * 0.52f, paint)
    } else if (isMetro) {
        // Borda dourada
        paint.color = android.graphics.Color.parseColor("#FFB800")
        canvas.drawCircle(center, center, center * 0.95f, paint)

        // Centro verde escuro do Metro-DF
        paint.color = android.graphics.Color.parseColor("#008037")
        canvas.drawCircle(center, center, center * 0.75f, paint)

        // Ponto central branco
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.32f, paint)
    } else {
        // Parada de Onibus Oficial
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, center * 0.95f, paint)

        paint.color = android.graphics.Color.parseColor("#00DC64")
        canvas.drawCircle(center, center, center * 0.75f, paint)
    }

    return BitmapDrawable(context.resources, bitmap)
}
