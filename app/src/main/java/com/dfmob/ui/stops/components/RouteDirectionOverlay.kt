package com.dfmob.ui.stops.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import androidx.core.graphics.ColorUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Overlay otimizado que renderiza indicadores de fluxo/direcao estaticos
 * (circulo na cor oficial da linha com seta grossa preenchida) ao longo da rota.
 *
 * Utiliza a arquitetura e padrao do Moovit:
 * 1. Pontos estaticos calculados em coordenadas geograficas reais (a cada ~350m ao longo do percurso).
 * 2. Pontos ficam 100% ancorados na coordenada geografica da rua, sem deslizar ou mover ao dar zoom in/out.
 * 3. Estados de exibicao por nivel de zoom (Moovit DrawArrowsState):
 *    - zoom < 14.0: NO_DRAW (0 setas para manter a visao geral do DF limpa)
 *    - 14.0 <= zoom < 16.5: DRAW_HALF (apenas indices pares para evitar poluicao visual)
 *    - zoom >= 16.5: DRAW_ALL (todas as setas exibidas)
 * 4. Circulo na cor da linha com seta grossa branca (ou preta para tons muito claros como amarelo).
 */
class RouteDirectionOverlay(context: Context) : Overlay() {

    private val density = context.resources.displayMetrics.density

    data class ColoredRouteSection(
        val points: List<GeoPoint>,
        val color: Int
    )

    private data class StaticArrow(
        val geoPoint: GeoPoint,
        val angleDeg: Float,
        val color: Int
    )

    @Volatile
    private var staticArrows: List<StaticArrow> = emptyList()

    private var circleColor: Int = Color.parseColor("#1A73E8")

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = circleColor
        style = Paint.Style.FILL
    }

    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Seta grossa normal (corpo retangular e ponta triangular preenchida)
    private val arrowPath = Path().apply {
        moveTo(4.6f * density, 0f)
        lineTo(0.5f * density, -3.8f * density)
        lineTo(0.5f * density, -1.6f * density)
        lineTo(-4.2f * density, -1.6f * density)
        lineTo(-4.2f * density, 1.6f * density)
        lineTo(0.5f * density, 1.6f * density)
        lineTo(0.5f * density, 3.8f * density)
        close()
    }

    private val radius = 9.5f * density
    private val screenPt = Point()

    fun setColor(color: Int) {
        if (this.circleColor != color) {
            this.circleColor = color
            circleFillPaint.color = color
            val isBright = ColorUtils.calculateLuminance(color) > 0.65
            arrowPaint.color = if (isBright) Color.BLACK else Color.WHITE
            circleBorderPaint.color = if (isBright) Color.parseColor("#40000000") else Color.WHITE
        }
    }

    fun setSections(sections: List<ColoredRouteSection>) {
        if (sections.isEmpty()) {
            staticArrows = emptyList()
            return
        }

        val stepMeters = 350.0
        val arrows = ArrayList<StaticArrow>()

        for (section in sections) {
            val points = section.points
            if (points.size < 2) continue

            val sectionColor = section.color
            var accumulated = 0.0
            var nextTarget = 150.0
            val arrowsBefore = arrows.size

            for (i in 0 until points.size - 1) {
                val pA = points[i]
                val pB = points[i + 1]
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
                    arrows.add(StaticArrow(GeoPoint(arrowLat, arrowLon), angleDeg, sectionColor))
                    nextTarget += stepMeters
                }

                accumulated += segDist
            }

            // Se o trecho tiver pelo menos 80m e nenhuma seta coube no target de 150m, adiciona seta no meio
            if (arrows.size == arrowsBefore && accumulated >= 80.0) {
                var acc = 0.0
                val targetMid = accumulated / 2.0
                for (i in 0 until points.size - 1) {
                    val pA = points[i]
                    val pB = points[i + 1]
                    val segDist = pA.distanceToAsDouble(pB)
                    if (segDist <= 0.001) continue
                    if (acc + segDist >= targetMid) {
                        val fraction = (targetMid - acc) / segDist
                        val dLat = pB.latitude - pA.latitude
                        val dLon = pB.longitude - pA.longitude
                        val cosLat = cos(Math.toRadians(pA.latitude))
                        val angleDeg = Math.toDegrees(atan2(-dLat, dLon * cosLat)).toFloat()
                        val arrowLat = pA.latitude + fraction * dLat
                        val arrowLon = pA.longitude + fraction * dLon
                        arrows.add(StaticArrow(GeoPoint(arrowLat, arrowLon), angleDeg, sectionColor))
                        break
                    }
                    acc += segDist
                }
            }
        }

        this.staticArrows = arrows
    }

    fun setPoints(newPoints: List<GeoPoint>, color: Int? = null) {
        val targetColor = color ?: this.circleColor
        if (color != null) {
            setColor(color)
        }
        if (newPoints.size < 2) {
            staticArrows = emptyList()
            return
        }
        setSections(listOf(ColoredRouteSection(newPoints, targetColor)))
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

        var lastColor: Int? = null

        for (i in arrows.indices) {
            if (isHalf && (i % 2 != 0)) continue

            val arrow = arrows[i]
            projection.toPixels(arrow.geoPoint, screenPt)

            val px = screenPt.x
            val py = screenPt.y

            if (px < -30 || px > width + 30 || py < -30 || py > height + 30) continue

            if (lastColor == null || arrow.color != lastColor) {
                lastColor = arrow.color
                circleFillPaint.color = arrow.color
                val isBright = ColorUtils.calculateLuminance(arrow.color) > 0.65
                arrowPaint.color = if (isBright) Color.BLACK else Color.WHITE
                circleBorderPaint.color = if (isBright) Color.parseColor("#40000000") else Color.WHITE
            }

            canvas.save()
            canvas.translate(px.toFloat(), py.toFloat())

            // 1. Sombra suave
            canvas.drawCircle(0f, 1.2f * density, radius, shadowPaint)

            // 2. Circulo com a cor oficial da linha
            canvas.drawCircle(0f, 0f, radius, circleFillPaint)

            // 3. Borda de destaque
            canvas.drawCircle(0f, 0f, radius, circleBorderPaint)

            // 4. Seta grossa normal preenchida apontando no sentido do fluxo
            canvas.rotate(arrow.angleDeg - mapOrientation)
            canvas.drawPath(arrowPath, arrowPaint)

            canvas.restore()
        }
    }
}
