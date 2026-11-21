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
 * Desenha o traçado contínuo e elegante da Variante 2 (estilo Transit App).
 * O loop do D e o elo S do F se preenchem em sincronia fluida com pontas arredondadas,
 * seguidos pela barra média e a cauda inferior do F.
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

        // Proporção fiel do ícone oficial Variante 2: largura 360, altura 245
        val scale = (w * 0.88f) / 360f
        val strokeW = 38f * scale
        val strokeStyle = Stroke(
            width = strokeW,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Centralização exata no Canvas
        val offsetX = (w - 360f * scale) / 2f
        val offsetY = (h - 245f * scale) / 2f

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

        // 1. Loop contínuo do D (haste vertical, curvas suaves e arco direito fechando o D)
        val pathDLoop = Path().apply {
            moveTo(offsetX + 20f * scale, offsetY + 55f * scale)
            cubicTo(
                offsetX + 20f * scale, offsetY + 20f * scale,
                offsetX + 25f * scale, offsetY + 20f * scale,
                offsetX + 55f * scale, offsetY + 20f * scale
            )
            lineTo(offsetX + 130f * scale, offsetY + 20f * scale)
            cubicTo(
                offsetX + 180f * scale, offsetY + 20f * scale,
                offsetX + 180f * scale, offsetY + 225f * scale,
                offsetX + 130f * scale, offsetY + 225f * scale
            )
            lineTo(offsetX + 55f * scale, offsetY + 225f * scale)
            cubicTo(
                offsetX + 25f * scale, offsetY + 225f * scale,
                offsetX + 20f * scale, offsetY + 225f * scale,
                offsetX + 20f * scale, offsetY + 190f * scale
            )
            close()
        }

        // 2. Elo S contínuo: sobe da base do D, cruza o centro e desdobra na barra superior do F
        val pathSCurveRibbon = Path().apply {
            moveTo(offsetX + 130f * scale, offsetY + 225f * scale)
            cubicTo(
                offsetX + 175f * scale, offsetY + 210f * scale,
                offsetX + 185f * scale, offsetY + 130f * scale,
                offsetX + 205f * scale, offsetY + 70f * scale
            )
            cubicTo(
                offsetX + 220f * scale, offsetY + 20f * scale,
                offsetX + 260f * scale, offsetY + 20f * scale,
                offsetX + 345f * scale, offsetY + 20f * scale
            )
        }

        // 3. F - Barra média horizontal com ponta arredondada
        val pathFMiddle = Path().apply {
            moveTo(offsetX + 240f * scale, offsetY + 122f * scale)
            lineTo(offsetX + 325f * scale, offsetY + 122f * scale)
        }

        // 4. F - Cauda inferior curva descendo suavemente com ponta arredondada
        val pathFTail = Path().apply {
            moveTo(offsetX + 195f * scale, offsetY + 160f * scale)
            cubicTo(
                offsetX + 195f * scale, offsetY + 185f * scale,
                offsetX + 205f * scale, offsetY + 205f * scale,
                offsetX + 205f * scale, offsetY + 235f * scale
            )
        }

        // Animação sincronizada das linhas
        drawTrimmedPath(pathDLoop, progress)
        drawTrimmedPath(pathSCurveRibbon, progress)
        val branchProgress = (progress * 1.35f - 0.35f).coerceIn(0f, 1f)
        drawTrimmedPath(pathFMiddle, branchProgress)
        drawTrimmedPath(pathFTail, branchProgress)
    }
}
