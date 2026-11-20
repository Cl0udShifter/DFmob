package com.dfmob.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dfmob.ui.theme.TransitGreen

/**
 * Ícone animado de sinal Wi-Fi em ondas progressivas (radar sweep) para partidas com GPS em tempo real.
 * Varre em cascata contínua (~900ms): ponto central -> arco médio -> arco externo.
 * Os arcos inativos mantêm silhueta translúcida a 30% de opacidade para legibilidade imediata.
 */
@Composable
fun LiveWifiIcon(
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    tint: Color = TransitGreen
) {
    val transition = rememberInfiniteTransition(label = "wifi_radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Origem comum dos arcos na base do ponto
        val origin = Offset(w / 2f, h * 0.86f)
        val strokeW = h * 0.16f
        val dotRadius = h * 0.11f
        val midRadius = h * 0.44f
        val outerRadius = h * 0.76f

        fun getArcAlpha(prog: Float, peak: Float): Float {
            val diff = (prog - peak + 1f) % 1f
            return when {
                diff in 0f..0.25f -> 1.0f - (diff / 0.25f) * 0.70f
                diff in 0.85f..1f -> 0.30f + ((diff - 0.85f) / 0.15f) * 0.70f
                else -> 0.30f
            }
        }

        val dotAlpha = getArcAlpha(progress, 0.0f)
        val midAlpha = getArcAlpha(progress, 0.33f)
        val outerAlpha = getArcAlpha(progress, 0.66f)

        // 1. Ponto inferior (dot)
        drawCircle(
            color = tint.copy(alpha = dotAlpha),
            radius = dotRadius,
            center = origin
        )

        // 2. Arco intermediário (mid arc)
        val midRect = Rect(
            center = origin,
            radius = midRadius
        )
        drawArc(
            color = tint.copy(alpha = midAlpha),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = midRect.topLeft,
            size = midRect.size,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // 3. Arco superior externo (outer arc)
        val outerRect = Rect(
            center = origin,
            radius = outerRadius
        )
        drawArc(
            color = tint.copy(alpha = outerAlpha),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = outerRect.topLeft,
            size = outerRect.size,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}
