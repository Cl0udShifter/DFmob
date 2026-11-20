package com.dfmob.ui.tripplanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dfmob.BuildConfig
import com.dfmob.data.model.DFLocation
import com.dfmob.data.repository.StopsRepository
import com.dfmob.ui.theme.TransitGreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import com.dfmob.ui.stops.components.DFLocationOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import androidx.compose.ui.graphics.luminance
import java.util.Locale

private class PickerStadiaTileSource(
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

private val PICKER_STADIA_SMOOTH = PickerStadiaTileSource("PickerStadiaSmoothHD", "alidade_smooth")
private val PICKER_STADIA_DARK = PickerStadiaTileSource("PickerStadiaDarkHD", "alidade_smooth_dark")

@Composable
fun MapLocationPicker(
    initialLatE6: Int?,
    initialLonE6: Int?,
    onCancel: () -> Unit,
    onConfirm: (latE6: Int, lonE6: Int, caption: String) -> Unit,
    onReverseGeocode: suspend (latE6: Int, lonE6: Int) -> String,
    title: String? = null
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val stopsRepository = remember { StopsRepository(context) }

    var centerLatE6 by remember { mutableStateOf(initialLatE6 ?: -15794167) }
    var centerLonE6 by remember { mutableStateOf(initialLonE6 ?: -47882778) }
    var resolvedAddress by remember { mutableStateOf("Carregando endereço...") }
    var isResolving by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DFLocation>>(emptyList()) }
    var isDropdownDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, isDropdownDismissed) {
        if (isDropdownDismissed || searchQuery.isBlank() || searchQuery.trim().length < 3) {
            searchResults = emptyList()
        } else {
            delay(400L)
            searchResults = stopsRepository.searchLocations(searchQuery)
        }
    }

    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        MapView(context).apply {
            val initialSource = if (isDarkTheme) PICKER_STADIA_DARK else PICKER_STADIA_SMOOTH
            setTileSource(initialSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = false
            minZoomLevel = 9.0
            maxZoomLevel = 20.0
            controller.setZoom(16.5)

            val initialLat = (initialLatE6 ?: -15794167) / 1_000_000.0
            val initialLon = (initialLonE6 ?: -47882778) / 1_000_000.0
            controller.setCenter(GeoPoint(initialLat, initialLon))
        }
    }

    // Gerencia ciclo de vida do MapView
    LaunchedEffect(isDarkTheme) {
        mapView.setTileSource(if (isDarkTheme) PICKER_STADIA_DARK else PICKER_STADIA_SMOOTH)
        mapView.postInvalidate()
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

    // Overlay de localizacao GPS do usuario
    DisposableEffect(mapView) {
        val provider = GpsMyLocationProvider(context)
        val overlay = DFLocationOverlay(provider, mapView, context)
        mapView.overlays.add(overlay)
        onDispose {
            overlay.cleanup()
            overlay.disableMyLocation()
            mapView.overlays.remove(overlay)
        }
    }

    // Monitora arrasto do mapa para atualizar o ponto central
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val center = mapView.mapCenter
                val latE6 = (center.latitude * 1_000_000).toInt()
                val lonE6 = (center.longitude * 1_000_000).toInt()
                centerLatE6 = latE6
                centerLonE6 = lonE6

                debounceJob?.cancel()
                debounceJob = scope.launch {
                    isResolving = true
                    delay(300)
                    resolvedAddress = onReverseGeocode(latE6, lonE6)
                    isResolving = false
                }
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean = true
        }
        mapView.addMapListener(listener)
        onDispose {
            mapView.removeMapListener(listener)
            debounceJob?.cancel()
        }
    }

    // Resolucao inicial do endereco
    LaunchedEffect(Unit) {
        isResolving = true
        resolvedAddress = onReverseGeocode(centerLatE6, centerLonE6)
        isResolving = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa base
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Barra Superior de Busca com Autocomplete da Stadia Maps
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            val searchBarShape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(searchBarShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = searchBarShape
                    ),
                shape = searchBarShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = if (isDarkTheme) 8.dp else 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = title ?: "Buscar endereço ou local no DF...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            cursorBrush = SolidColor(TransitGreen),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                isDropdownDismissed = true
                            })
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                searchResults = emptyList()
                                isDropdownDismissed = true
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(32.dp)
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
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Buscar",
                            tint = TransitGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Dropdown de sugestoes com resultados da Stadia Maps
            if (searchResults.isNotEmpty() && !isDropdownDismissed) {
                Spacer(modifier = Modifier.height(4.dp))
                val dropdownShape = RoundedCornerShape(16.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(dropdownShape)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = dropdownShape
                        ),
                    color = MaterialTheme.colorScheme.surface,
                    shape = dropdownShape,
                    shadowElevation = if (isDarkTheme) 14.dp else 8.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(searchResults) { loc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        searchQuery = loc.name
                                        isDropdownDismissed = true
                                        focusManager.clearFocus()
                                        val geo = GeoPoint(loc.latitude, loc.longitude)
                                        mapView.controller.animateTo(geo)
                                        centerLatE6 = loc.latE6
                                        centerLonE6 = loc.lonE6
                                        resolvedAddress = loc.name
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                    if (loc.description.isNotBlank()) {
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
            }
        }

        // Pin central estatico
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 36.dp), // Compensa a ponta inferior do marcador
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Place,
                contentDescription = "Ponto Central",
                tint = TransitGreen,
                modifier = Modifier.size(48.dp)
            )
        }

        // Botao de Minha Localizacao
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 170.dp, end = 16.dp)
        ) {
            Surface(
                onClick = {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    val lastGps = try { lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
                    val lastNet = try { lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
                    val userLoc = lastGps ?: lastNet
                    if (userLoc != null && System.currentTimeMillis() - userLoc.time < 30_000L) {
                        mapView.controller.animateTo(GeoPoint(userLoc.latitude, userLoc.longitude), 16.5, 750L)
                    } else {
                        try {
                            val singleListener = object : android.location.LocationListener {
                                override fun onLocationChanged(loc: android.location.Location) {
                                    mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 16.5, 750L)
                                    lm?.removeUpdates(this)
                                }
                                override fun onProviderEnabled(provider: String) {}
                                override fun onProviderDisabled(provider: String) {}
                                @Deprecated("Deprecated in Java")
                                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            }
                            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, singleListener)
                            lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, singleListener)
                        } catch (_: SecurityException) {}
                        if (userLoc != null) {
                            mapView.controller.animateTo(GeoPoint(userLoc.latitude, userLoc.longitude), 16.5, 750L)
                        }
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Minha Localização",
                        tint = TransitGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Card inferior de confirmacao
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TransitGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Place,
                            contentDescription = null,
                            tint = TransitGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedAddress,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val latFmt = String.format(Locale.ROOT, "%.5f", centerLatE6 / 1_000_000.0)
                        val lonFmt = String.format(Locale.ROOT, "%.5f", centerLonE6 / 1_000_000.0)
                        Text(
                            text = "$latFmt, $lonFmt",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isResolving) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TransitGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onConfirm(centerLatE6, centerLonE6, resolvedAddress)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TransitGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirmar este local",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
