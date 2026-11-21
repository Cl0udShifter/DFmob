package com.dfmob.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TransitGreen = Color(0xFF0BA05B)

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val iconProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(18f) }
    val lineProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Animacao do icone vetorial preenchendo as linhas (stroke draw-in de 0 a 1)
        launch {
            iconProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        }

        // 2. Animacao do nome DFmob e trilho inferior
        launch {
            delay(400)
            launch {
                textAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing)
                )
            }
            launch {
                textOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                )
            }
            launch {
                lineProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                )
            }
        }

        // Aguarda conclusao total da apresentacao (~1200ms) e transiciona suavemente
        delay(1250)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Toque permite pular imediatamente a splash
                onFinished()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icone vetorial animado (Candidato 6 com fundo branco e linhas verdes)
            VectorAnimatedIcon(
                progress = iconProgress.value,
                modifier = Modifier.size(140.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Nome DFmob com tipografia alinhada e surgimento suave
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset { IntOffset(0, textOffsetY.value.toInt()) }
                    .alpha(textAlpha.value)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DF",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = TransitGreen,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "mob",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = ".",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = TransitGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Linha de trilho sob o texto desenhada progressivamente
                Canvas(modifier = Modifier.size(width = 68.dp, height = 3.dp)) {
                    val fullW = size.width
                    val drawW = fullW * lineProgress.value
                    if (drawW > 0f) {
                        drawLine(
                            color = TransitGreen.copy(alpha = 0.85f),
                            start = androidx.compose.ui.geometry.Offset((fullW - drawW) / 2f, size.height / 2f),
                            end = androidx.compose.ui.geometry.Offset((fullW + drawW) / 2f, size.height / 2f),
                            strokeWidth = 2.5f.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

/**
 * Desenha os arcos e trilhos exatos do Candidato 6 com efeito de preenchimento de linha (trim-path).
 * Utiliza pontas retas (StrokeCap.Butt) identicas ao icone oficial.
 */
@Composable
private fun VectorAnimatedIcon(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val pathMeasure = remember { PathMeasure() }
    val subPath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Proporcao normalizada do icone original: largura 365, altura 257
        val scale = (w * 0.82f) / 365f
        val strokeW = 19f * scale
        val strokeStyle = Stroke(
            width = strokeW,
            cap = StrokeCap.Butt,
            join = StrokeJoin.Miter
        )

        // Centralizacao exata no Canvas
        val offsetX = (w - 365f * scale) / 2f
        val offsetY = (h - 257f * scale) / 2f

        fun drawTrimmedPath(path: Path, pathProgress: Float) {
            if (pathProgress <= 0.001f) return
            subPath.reset()
            pathMeasure.setPath(path, false)
            val length = pathMeasure.length
            if (length > 0f) {
                pathMeasure.getSegment(0f, length * pathProgress.coerceIn(0f, 1f), subPath, true)
                drawPath(path = subPath, color = TransitGreen, style = strokeStyle)
            }
        }

        // Construcao dos trilhos do Candidato 6 fiel ao icone original:

        // 1. D - Trilho externo: barra superior reta, arco Niemeyer e barra inferior reta
        val pathDOuter = Path().apply {
            moveTo(offsetX + 0f * scale, offsetY + 10f * scale)
            lineTo(offsetX + 75f * scale, offsetY + 10f * scale)
            cubicTo(
                offsetX + 200f * scale, offsetY + 10f * scale,
                offsetX + 200f * scale, offsetY + 246f * scale,
                offsetX + 75f * scale, offsetY + 246f * scale
            )
            lineTo(offsetX + 0f * scale, offsetY + 246f * scale)
        }

        // 2. D - Trilho interno concentrico
        val pathDInner = Path().apply {
            moveTo(offsetX + 0f * scale, offsetY + 54f * scale)
            lineTo(offsetX + 70f * scale, offsetY + 54f * scale)
            cubicTo(
                offsetX + 155f * scale, offsetY + 54f * scale,
                offsetX + 155f * scale, offsetY + 202f * scale,
                offsetX + 70f * scale, offsetY + 202f * scale
            )
            lineTo(offsetX + 0f * scale, offsetY + 202f * scale)
        }

        // 3. F - Haste externa Niemeyer: sobe da base pelo centro e curva no topo direito
        val pathFOuter = Path().apply {
            moveTo(offsetX + 189.5f * scale, offsetY + 257f * scale)
            cubicTo(
                offsetX + 168f * scale, offsetY + 185f * scale,
                offsetX + 195f * scale, offsetY + 80f * scale,
                offsetX + 260f * scale, offsetY + 10f * scale
            )
            lineTo(offsetX + 365f * scale, offsetY + 10f * scale)
        }

        // 4. F - Barra superior interna que curva para dentro do feixe vertical
        val pathFTopInner = Path().apply {
            moveTo(offsetX + 365f * scale, offsetY + 54f * scale)
            lineTo(offsetX + 270f * scale, offsetY + 54f * scale)
            cubicTo(
                offsetX + 225f * scale, offsetY + 54f * scale,
                offsetX + 215f * scale, offsetY + 110f * scale,
                offsetX + 225f * scale, offsetY + 160f * scale
            )
        }

        // 5. F - Trilhos intermediarios da barra central
        val pathFMiddleOuter = Path().apply {
            moveTo(offsetX + 365f * scale, offsetY + 138f * scale)
            lineTo(offsetX + 275f * scale, offsetY + 138f * scale)
            cubicTo(
                offsetX + 230f * scale, offsetY + 138f * scale,
                offsetX + 220f * scale, offsetY + 175f * scale,
                offsetX + 225f * scale, offsetY + 200f * scale
            )
        }

        val pathFMiddleInner = Path().apply {
            moveTo(offsetX + 365f * scale, offsetY + 182f * scale)
            lineTo(offsetX + 285f * scale, offsetY + 182f * scale)
            cubicTo(
                offsetX + 245f * scale, offsetY + 182f * scale,
                offsetX + 234.5f * scale, offsetY + 215f * scale,
                offsetX + 234.5f * scale, offsetY + 257f * scale
            )
        }

        // Desenha todas as linhas sincronizadas com o preenchimento vetorial
        drawTrimmedPath(pathDOuter, progress)
        drawTrimmedPath(pathDInner, progress)
        drawTrimmedPath(pathFOuter, progress)
        drawTrimmedPath(pathFTopInner, progress)
        drawTrimmedPath(pathFMiddleOuter, progress)
        drawTrimmedPath(pathFMiddleInner, progress)
    }
}
