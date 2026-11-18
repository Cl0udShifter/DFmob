package com.dfmob.ui.stops.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Overlay otimizado que renderiza indicadores de fluxo/direcao estaticos
 * (circulo branco com seta preta grossa) ao longo da rota de onibus e metro no mapa.
 *
 * Utiliza a arquitetura e padrao do Moovit:
 * 1. Pontos estaticos calculados em coordenadas geograficas reais (a cada ~350m ao longo do percurso).
 * 2. Pontos ficam 100% ancorados na coordenada geografica da rua, sem deslizar ou mover ao dar zoom in/out.
 * 3. Estados de exibicao por nivel de zoom (Moovit DrawArrowsState):
 *    - zoom < 14.0: NO_DRAW (0 setas para manter a visao geral do DF limpa)
 *    - 14.0 <= zoom < 16.5: DRAW_HALF (apenas indices pares para evitar poluicao visual)
 *    - zoom >= 16.5: DRAW_ALL (todas as setas exibidas)
 * 4. Orientacao precisa em graus seguindo o azimute da via.
 */
class RouteDirectionOverlay(context: Context) : Overlay() {

    private val density = context.resources.displayMetrics.density

    private data class StaticArrow(
        val geoPoint: GeoPoint,
        val angleDeg: Float
    )

    @Volatile
    private var staticArrows: List<StaticArrow> = emptyList()

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arrowPath = Path().apply {
        moveTo(-3.5f * density, -4.5f * density)
        lineTo(3.2f * density, 0f)
        lineTo(-3.5f * density, 4.5f * density)
    }

    private val radius = 8.5f * density
    private val screenPt = Point()

    fun setPoints(newPoints: List<GeoPoint>) {
        if (newPoints.size < 2) {
            staticArrows = emptyList()
            return
        }

        val stepMeters = 350.0
        val arrows = ArrayList<StaticArrow>()
        var accumulated = 0.0
        var nextTarget = 150.0

        for (i in 0 until newPoints.size - 1) {
            val pA = newPoints[i]
            val pB = newPoints[i + 1]
            val segDist = pA.distanceToAsDouble(pB)
            if (segDist <= 0.001) continue

            val dLat = pB.latitude - pA.latitude
            val dLon = pB.longitude - pA.longitude
            val cosLat = cos(Math.toRadians(pA.latitude))
            val angleDeg = Math.toDegrees(atan2(-dLat, dLon * cosLat)).toFloat()

            while (accumulated + segDist >= nextTarget) {
                val fraction = (nextTarget - accumulated) / segDist
                val arrowLat = pA.latitude + fraction * dLat
                val arrowLon = pA.longitude + fraction * dLon
                arrows.add(StaticArrow(GeoPoint(arrowLat, arrowLon), angleDeg))
                nextTarget += stepMeters
            }

            accumulated += segDist
        }

        this.staticArrows = arrows
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        val currentZoom = mapView.zoomLevelDouble
        val arrows = staticArrows
        if (currentZoom < 14.0 || arrows.isEmpty()) return

        val isHalf = currentZoom < 16.5
        val projection = mapView.projection
        val width = mapView.width
        val height = mapView.height
        val mapOrientation = mapView.mapOrientation

        for (i in arrows.indices) {
            if (isHalf && (i % 2 != 0)) continue

            val arrow = arrows[i]
            projection.toPixels(arrow.geoPoint, screenPt)

            val px = screenPt.x
            val py = screenPt.y

            if (px < -30 || px > width + 30 || py < -30 || py > height + 30) continue

            canvas.save()
            canvas.translate(px.toFloat(), py.toFloat())

            // 1. Sombra suave
            canvas.drawCircle(0f, 1f * density, radius, shadowPaint)

            // 2. Circulo branco solido
            canvas.drawCircle(0f, 0f, radius, circleFillPaint)

            // 3. Borda fina de contorno
            canvas.drawCircle(0f, 0f, radius, circleBorderPaint)

            // 4. Seta grossa preta alinhada ao sentido do percurso
            canvas.rotate(arrow.angleDeg - mapOrientation)
            canvas.drawPath(arrowPath, arrowPaint)

            canvas.restore()
        }
    }
}
