package com.dfmob.ui.tripplanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

private class PickerCartoTileSource(
    name: String,
    style: String
) : XYTileSource(
    name,
    0, 20, 512, "@2x.png",
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

private val PICKER_CARTO_POSITRON = PickerCartoTileSource("PickerCartoPositronHD", "light_all")
private val PICKER_CARTO_DARK_MATTER = PickerCartoTileSource("PickerCartoDarkMatterHD", "dark_all")

@Composable
fun MapLocationPicker(
    initialLatE6: Int?,
    initialLonE6: Int?,
    onCancel: () -> Unit,
    onConfirm: (latE6: Int, lonE6: Int, caption: String) -> Unit,
    onReverseGeocode: suspend (latE6: Int, lonE6: Int) -> String
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var centerLatE6 by remember { mutableStateOf(initialLatE6 ?: -15794167) }
    var centerLonE6 by remember { mutableStateOf(initialLonE6 ?: -47882778) }
    var resolvedAddress by remember { mutableStateOf("Carregando endereço...") }
    var isResolving by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        MapView(context).apply {
            val initialSource = if (isDarkTheme) PICKER_CARTO_DARK_MATTER else PICKER_CARTO_POSITRON
            setTileSource(initialSource)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 9.0
            maxZoomLevel = 20.0
            controller.setZoom(16.5)

            val initialLat = (initialLatE6 ?: -15794167) / 1_000_000.0
            val initialLon = (initialLonE6 ?: -47882778) / 1_000_000.0
            controller.setCenter(GeoPoint(initialLat, initialLon))
        }
    }

    // Gerencia ciclo de vida do MapView
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
        val overlay = MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
            setDrawAccuracyEnabled(true)
        }
        mapView.overlays.add(overlay)
        onDispose {
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

        // Top bar com botao de voltar e orientacao
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
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Arraste o mapa até o local desejado",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
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
                    if (userLoc != null) {
                        mapView.controller.animateTo(GeoPoint(userLoc.latitude, userLoc.longitude))
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
