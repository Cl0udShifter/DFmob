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
import androidx.compose.foundation.layout.width
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

private val SplashGreen = Color(0xFF0BA05B)

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val iconProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(20f) }
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
                    animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing)
                )
            }
            launch {
                textOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                )
            }
            launch {
                lineProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
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
            .background(SplashGreen)
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
            // Icone vetorial animado (Candidato 6 invertido)
            VectorAnimatedIcon(
                progress = iconProgress.value,
                modifier = Modifier.size(136.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "mob",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.95f),
                        letterSpacing = (-0.5).sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Linha de trilho sob o texto desenhada progressivamente
                Canvas(modifier = Modifier.size(width = 68.dp, height = 3.dp)) {
                    val fullW = size.width
                    val drawW = fullW * lineProgress.value
                    if (drawW > 0f) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.8f),
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
 * Desenha os arcos e trilhos do Candidato 6 com efeito de preenchimento de linha (trim-path).
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

        // Escala normalizada: largura do glifo 363, altura 255
        // Ajustamos para ocupar cerca de 82% da largura do canvas
        val scale = (w * 0.82f) / 363f
        val strokeW = 18f * scale
        val strokeStyle = Stroke(
            width = strokeW,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Deslocamento para centralizar perfeitamente no Canvas
        val offsetX = (w - 363f * scale) / 2f
        val offsetY = (h - 255f * scale) / 2f

        fun drawTrimmedPath(path: Path, pathProgress: Float) {
            if (pathProgress <= 0.001f) return
            subPath.reset()
            pathMeasure.setPath(path, false)
            val length = pathMeasure.length
            if (length > 0f) {
                pathMeasure.getSegment(0f, length * pathProgress.coerceIn(0f, 1f), subPath, true)
                drawPath(path = subPath, color = Color.White, style = strokeStyle)
            }
        }

        // Construcao dos 5 caminhos vetoriais do Candidato 6:

        // 1. D - Trilho externo (inicia no canto superior esquerdo, curva no arco de Niemeyer e fecha na base)
        val pathDOuter = Path().apply {
            moveTo(offsetX + 0f * scale, offsetY + 9f * scale)
            lineTo(offsetX + 70f * scale, offsetY + 9f * scale)
            cubicTo(
                offsetX + 195f * scale, offsetY + 9f * scale,
                offsetX + 195f * scale, offsetY + 245f * scale,
                offsetX + 70f * scale, offsetY + 245f * scale
            )
            lineTo(offsetX + 0f * scale, offsetY + 245f * scale)
        }

        // 2. D - Trilho interno concentrico
        val pathDInner = Path().apply {
            moveTo(offsetX + 0f * scale, offsetY + 53f * scale)
            lineTo(offsetX + 70f * scale, offsetY + 53f * scale)
            cubicTo(
                offsetX + 150f * scale, offsetY + 53f * scale,
                offsetX + 150f * scale, offsetY + 201f * scale,
                offsetX + 70f * scale, offsetY + 201f * scale
            )
            lineTo(offsetX + 0f * scale, offsetY + 201f * scale)
        }

        // 3. F - Haste vertical externa que sobe da base e curva a direita no topo
        val pathFOuter = Path().apply {
            moveTo(offsetX + 188f * scale, offsetY + 245f * scale)
            lineTo(offsetX + 188f * scale, offsetY + 120f * scale)
            cubicTo(
                offsetX + 188f * scale, offsetY + 9f * scale,
                offsetX + 250f * scale, offsetY + 9f * scale,
                offsetX + 363f * scale, offsetY + 9f * scale
            )
        }

        // 4. F - Haste vertical interna que sobe e curva na barra intermediaria
        val pathFInner = Path().apply {
            moveTo(offsetX + 233f * scale, offsetY + 245f * scale)
            lineTo(offsetX + 233f * scale, offsetY + 175f * scale)
            cubicTo(
                offsetX + 233f * scale, offsetY + 127f * scale,
                offsetX + 275f * scale, offsetY + 127f * scale,
                offsetX + 363f * scale, offsetY + 127f * scale
            )
        }

        // 5. F - Barra superior interna e barra intermediaria inferior
        val pathFBars = Path().apply {
            moveTo(offsetX + 242f * scale, offsetY + 53f * scale)
            lineTo(offsetX + 363f * scale, offsetY + 53f * scale)
            moveTo(offsetX + 268f * scale, offsetY + 171f * scale)
            lineTo(offsetX + 363f * scale, offsetY + 171f * scale)
        }

        // Desenha todas as linhas sincronizadas com o progresso
        drawTrimmedPath(pathDOuter, progress)
        drawTrimmedPath(pathDInner, progress)
        drawTrimmedPath(pathFOuter, progress)
        drawTrimmedPath(pathFInner, progress)
        drawTrimmedPath(pathFBars, progress)
    }
}
