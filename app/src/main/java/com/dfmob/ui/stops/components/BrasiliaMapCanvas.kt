package com.dfmob.ui.stops.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.TransitStop
import kotlin.math.hypot

@Composable
fun BrasiliaMapCanvas(
    stops: List<TransitStop>,
    selectedStop: TransitStop?,
    focusLocation: DFLocation?,
    onStopSelected: (TransitStop) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Centro geografico de referencia: Rodoviaria do Plano Piloto
    val centerLat = -15.794167
    val centerLon = -47.882778

    // Centraliza o mapa quando o usuario seleciona um local na busca
    LaunchedEffect(focusLocation) {
        if (focusLocation != null && canvasSize.width > 0 && canvasSize.height > 0) {
            val dLat = focusLocation.latitude - centerLat
            val dLon = focusLocation.longitude - centerLon
            val baseScale = canvasSize.width * 14.0f
            val targetX = -(dLon * baseScale * scale).toFloat()
            val targetY = (dLat * baseScale * scale).toFloat()
            offset = Offset(targetX, targetY)
            if (scale < 1.6f) {
                scale = 1.8f
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 6.0f)
                    offset = Offset(
                        x = offset.x + pan.x,
                        y = offset.y + pan.y
                    )
                }
            }
            .pointerInput(stops, scale, offset, canvasSize) {
                detectTapGestures { tapPos ->
                    val w = canvasSize.width.toFloat()
                    val h = canvasSize.height.toFloat()
                    if (w <= 0 || h <= 0) return@detectTapGestures

                    val baseScale = w * 14.0f
                    val hitRadiusPx = 32f * density

                    var closestStop: TransitStop? = null
                    var closestDist = Float.MAX_VALUE

                    for (stop in stops) {
                        // Respeitar filtro de zoom do Moovit: pontos de onibus so sao clicaveis no zoom >= 1.35
                        if (scale < 1.35f && !stop.isMetro) continue

                        val sx = w / 2f + offset.x + ((stop.longitude - centerLon) * baseScale * scale).toFloat()
                        val sy = h / 2f + offset.y - ((stop.latitude - centerLat) * baseScale * scale).toFloat()

                        val dist = hypot(tapPos.x - sx, tapPos.y - sy)
                        if (dist <= hitRadiusPx && dist < closestDist) {
                            closestDist = dist
                            closestStop = stop
                        }
                    }

                    if (closestStop != null) {
                        onStopSelected(closestStop)
                    }
                }
            }
    ) {
        canvasSize = size

        val w = size.width
        val h = size.height
        val baseScale = w * 14.0f

        // 1. Fundo do Mapa (True Dark #090B10)
        drawRect(Color(0xFF090B10))

        // Helper de projecao geografica
        fun geoToScreen(lat: Double, lon: Double): Offset {
            val x = w / 2f + offset.x + ((lon - centerLon) * baseScale * scale).toFloat()
            val y = h / 2f + offset.y - ((lat - centerLat) * baseScale * scale).toFloat()
            return Offset(x, y)
        }

        // 2. Lago Paranoa (Forma Organica Azul Escuro)
        val lakePath = Path().apply {
            val p0 = geoToScreen(-15.710, -47.860)
            val p1 = geoToScreen(-15.740, -47.830)
            val p2 = geoToScreen(-15.790, -47.810)
            val p3 = geoToScreen(-15.840, -47.840)
            val p4 = geoToScreen(-15.890, -47.880)
            moveTo(p0.x, p0.y)
            cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            lineTo(p4.x, p4.y)
        }
        drawPath(
            path = lakePath,
            color = Color(0xFF0D2538),
            style = Stroke(width = 34f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 3. Rodovias Principais (Cinza Escuro #1D212A)
        // Eixo Monumental
        val emStart = geoToScreen(-15.795, -47.940)
        val emEnd = geoToScreen(-15.801, -47.845)
        drawLine(
            color = Color(0xFF222632),
            start = emStart,
            end = emEnd,
            strokeWidth = 10f * scale.coerceIn(0.8f, 2.0f),
            cap = StrokeCap.Round
        )

        // Eixo Rodoviario (Asa Sul e Asa Norte)
        val erStart = geoToScreen(-15.720, -47.888)
        val erCenter = geoToScreen(-15.794, -47.883)
        val erEnd = geoToScreen(-15.860, -47.935)
        val erPath = Path().apply {
            moveTo(erStart.x, erStart.y)
            quadraticBezierTo(erCenter.x, erCenter.y, erEnd.x, erEnd.y)
        }
        drawPath(
            path = erPath,
            color = Color(0xFF1F232D),
            style = Stroke(width = 8f * scale.coerceIn(0.8f, 2.0f), cap = StrokeCap.Round)
        )

        // Corredor EPTG (Ligacao Taguatinga / Plano Piloto)
        val eptgStart = geoToScreen(-15.835, -48.060)
        val eptgEnd = geoToScreen(-15.800, -47.950)
        drawLine(
            color = Color(0xFF1E222C),
            start = eptgStart,
            end = eptgEnd,
            strokeWidth = 9f * scale.coerceIn(0.8f, 2.0f),
            cap = StrokeCap.Round
        )

        // 4. Trilhos do Metro-DF (Linha Verde #00DC64 e Linha Laranja #FF9100)
        val metroGreenPath = Path().apply {
            val central = geoToScreen(-15.794167, -47.882778)
            val asaSul = geoToScreen(-15.830000, -47.930000)
            val guara = geoToScreen(-15.823611, -47.981944)
            val aguasClaras = geoToScreen(-15.838889, -48.028611)
            val taguatinga = geoToScreen(-15.833611, -48.056667)
            val ceilandia = geoToScreen(-15.819722, -48.110556)

            moveTo(central.x, central.y)
            lineTo(asaSul.x, asaSul.y)
            lineTo(guara.x, guara.y)
            lineTo(aguasClaras.x, aguasClaras.y)
            lineTo(taguatinga.x, taguatinga.y)
            lineTo(ceilandia.x, ceilandia.y)
        }
        drawPath(
            path = metroGreenPath,
            color = Color(0xFF00DC64),
            style = Stroke(width = 3.5f * scale.coerceIn(0.8f, 1.8f), cap = StrokeCap.Round)
        )

        val metroOrangePath = Path().apply {
            val aguasClaras = geoToScreen(-15.838889, -48.028611)
            val samambaia = geoToScreen(-15.875000, -48.085000)
            moveTo(aguasClaras.x, aguasClaras.y)
            lineTo(samambaia.x, samambaia.y)
        }
        drawPath(
            path = metroOrangePath,
            color = Color(0xFFFF9100),
            style = Stroke(width = 3.5f * scale.coerceIn(0.8f, 1.8f), cap = StrokeCap.Round)
        )

        // 5. Pins de Paradas e Estacoes (Filtro de Zoom do Moovit)
        for (stop in stops) {
            // Zoom threshold: no zoom afastado mostra apenas metrô
            if (scale < 1.35f && !stop.isMetro) continue

            val pt = geoToScreen(stop.latitude, stop.longitude)

            // Viewport culling (descarta pontos fora da tela visivel)
            if (pt.x < -40 || pt.x > w + 40 || pt.y < -40 || pt.y > h + 40) continue

            val isSelected = selectedStop?.stopId == stop.stopId

            if (isSelected) {
                // Aura luminosa ao redor do ponto selecionado
                drawCircle(
                    color = Color(0x3300DC64),
                    radius = 20f,
                    center = pt
                )
                drawCircle(
                    color = Color.White,
                    radius = 10f,
                    center = pt
                )
                drawCircle(
                    color = Color(0xFF00DC64),
                    radius = 7.5f,
                    center = pt
                )
            } else if (stop.isMetro) {
                // Estacao do Metro-DF
                drawCircle(
                    color = Color(0xFFFFB800),
                    radius = 7f,
                    center = pt
                )
                drawCircle(
                    color = Color(0xFF008037),
                    radius = 5.2f,
                    center = pt
                )
            } else {
                // Parada de Onibus
                drawCircle(
                    color = Color.White,
                    radius = 5.5f,
                    center = pt
                )
                drawCircle(
                    color = Color(0xFF00DC64),
                    radius = 4f,
                    center = pt
                )
            }
        }
    }
}
