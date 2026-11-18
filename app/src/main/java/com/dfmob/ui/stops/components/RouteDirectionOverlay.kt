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
import kotlin.math.hypot

/**
 * Overlay otimizado que renderiza indicadores de fluxo/direcao (circulo branco com seta preta grossa)
 * ao longo da rota de onibus e metro no mapa.
 *
 * Casos de borda tratados:
 * 1. Z-Index: Posicionado abaixo de marcadores de paradas e icones de veiculos.
 * 2. Culling de Zoom: Totalmente oculto em zoom < 13.5 (visao geral do DF sem poluicao).
 * 3. Densidade Dinamica: Espacamento de ~280px em zoom 13.5-15.5 e ~180px em zoom >= 15.5.
 * 4. Orientacao precisa: Seta alinhada ao vetor de deslocamento da via.
 */
class RouteDirectionOverlay(context: Context) : Overlay() {

    private val density = context.resources.displayMetrics.density
    private var points: List<GeoPoint> = emptyList()

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
    private val p1 = Point()
    private val p2 = Point()

    fun setPoints(newPoints: List<GeoPoint>) {
        this.points = newPoints
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        val currentZoom = mapView.zoomLevelDouble
        if (currentZoom < 13.5 || points.size < 2) return

        val spacingPx = if (currentZoom < 15.5) 280f * density else 180f * density
        val projection = mapView.projection
        val width = mapView.width
        val height = mapView.height

        var accumulatedDist = 0.0
        var nextArrowDist = spacingPx * 0.7 // Margem inicial para a primeira seta

        projection.toPixels(points[0], p1)

        for (i in 1 until points.size) {
            projection.toPixels(points[i], p2)

            val dx = (p2.x - p1.x).toDouble()
            val dy = (p2.y - p1.y).toDouble()
            val segLen = hypot(dx, dy)

            if (segLen > 0.1) {
                while (accumulatedDist + segLen >= nextArrowDist) {
                    val progress = (nextArrowDist - accumulatedDist) / segLen
                    val arrowX = (p1.x + progress * dx).toFloat()
                    val arrowY = (p1.y + progress * dy).toFloat()

                    // Desenha apenas setas visiveis dentro dos limites da tela com margem
                    if (arrowX >= -30 && arrowX <= width + 30 && arrowY >= -30 && arrowY <= height + 30) {
                        val angleDeg = Math.toDegrees(atan2(dy, dx)).toFloat()

                        canvas.save()
                        canvas.translate(arrowX, arrowY)

                        // 1. Sombra suave
                        canvas.drawCircle(0f, 1f * density, radius, shadowPaint)

                        // 2. Circulo branco solido
                        canvas.drawCircle(0f, 0f, radius, circleFillPaint)

                        // 3. Borda fina de contorno
                        canvas.drawCircle(0f, 0f, radius, circleBorderPaint)

                        // 4. Seta grossa preta alinhada a via
                        canvas.rotate(angleDeg)
                        canvas.drawPath(arrowPath, arrowPaint)

                        canvas.restore()
                    }

                    nextArrowDist += spacingPx
                }
            }

            accumulatedDist += segLen
            p1.x = p2.x
            p1.y = p2.y
        }
    }
}
