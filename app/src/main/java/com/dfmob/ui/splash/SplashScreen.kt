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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TransitGreen = Color(0xFF2BC958)
private val BrandTextBlack = Color(0xFF0F172A)

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

        // Aguarda conclusao total da apresentacao (~1500ms) e transiciona suavemente
        delay(1500)
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
                        color = BrandTextBlack,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "mob",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandTextBlack,
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
 * Desenha o novo ícone oficial de rede de trânsito criado pelo usuário (IconKitchen).
 * O ícone representa a malha de estações interligadas com nós e trilhos:
 * 1. Traçado inicial das linhas de rota e contorno das estações (stroke draw-in)
 * 2. Anéis das 3 estações centrais formados em sincronia
 * 3. Preenchimento sólido e elegante com o degradê oficial verde (#2AC857 -> #168A41)
 * sobre o fundo branco imaculado.
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

        // Proporção matemática do glifo oficial (largura 538, altura 596)
        val scale = (h * 0.88f) / 596f
        val strokeW = 4.dp.toPx()
        val strokeStyle = Stroke(
            width = strokeW,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val offsetX = (w - 538f * scale) / 2f
        val offsetY = (h - 596f * scale) / 2f

        val iconBrush = SolidColor(TransitGreen)

        // 1. Contorno geométrico dos 17 vértices da malha de trânsito
        val outerPath = Path().apply {
            moveTo(offsetX + 210f * scale, offsetY + 0f * scale)
            lineTo(offsetX + 210f * scale, offsetY + 144f * scale)
            lineTo(offsetX + 127f * scale, offsetY + 239f * scale)
            lineTo(offsetX + 0f * scale, offsetY + 239f * scale)
            lineTo(offsetX + 0f * scale, offsetY + 416f * scale)
            lineTo(offsetX + 150f * scale, offsetY + 416f * scale)
            lineTo(offsetX + 357f * scale, offsetY + 520f * scale)
            lineTo(offsetX + 359f * scale, offsetY + 595f * scale)
            lineTo(offsetX + 537f * scale, offsetY + 595f * scale)
            lineTo(offsetX + 537f * scale, offsetY + 418f * scale)
            lineTo(offsetX + 359f * scale, offsetY + 418f * scale)
            lineTo(offsetX + 357f * scale, offsetY + 455f * scale)
            lineTo(offsetX + 178f * scale, offsetY + 364f * scale)
            lineTo(offsetX + 178f * scale, offsetY + 270f * scale)
            lineTo(offsetX + 260f * scale, offsetY + 177f * scale)
            lineTo(offsetX + 387f * scale, offsetY + 177f * scale)
            lineTo(offsetX + 387f * scale, offsetY + 0f * scale)
            close()
        }

        // 2. Glifo completo com os 3 furos de estação (EvenOdd para vazamento exato)
        val fullGlyphPath = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(offsetX + 210f * scale, offsetY + 0f * scale)
            lineTo(offsetX + 210f * scale, offsetY + 144f * scale)
            lineTo(offsetX + 127f * scale, offsetY + 239f * scale)
            lineTo(offsetX + 0f * scale, offsetY + 239f * scale)
            lineTo(offsetX + 0f * scale, offsetY + 416f * scale)
            lineTo(offsetX + 150f * scale, offsetY + 416f * scale)
            lineTo(offsetX + 357f * scale, offsetY + 520f * scale)
            lineTo(offsetX + 359f * scale, offsetY + 595f * scale)
            lineTo(offsetX + 537f * scale, offsetY + 595f * scale)
            lineTo(offsetX + 537f * scale, offsetY + 418f * scale)
            lineTo(offsetX + 359f * scale, offsetY + 418f * scale)
            lineTo(offsetX + 357f * scale, offsetY + 455f * scale)
            lineTo(offsetX + 178f * scale, offsetY + 364f * scale)
            lineTo(offsetX + 178f * scale, offsetY + 270f * scale)
            lineTo(offsetX + 260f * scale, offsetY + 177f * scale)
            lineTo(offsetX + 387f * scale, offsetY + 177f * scale)
            lineTo(offsetX + 387f * scale, offsetY + 0f * scale)
            close()

            // Estação superior (quadrado exato 60x60)
            addRect(
                Rect(
                    left = offsetX + 269f * scale,
                    top = offsetY + 59f * scale,
                    right = offsetX + 329f * scale,
                    bottom = offsetY + 119f * scale
                )
            )
            // Estação centro-esquerda (quadrado exato 60x60)
            addRect(
                Rect(
                    left = offsetX + 60f * scale,
                    top = offsetY + 298f * scale,
                    right = offsetX + 120f * scale,
                    bottom = offsetY + 358f * scale
                )
            )
            // Estação inferior-direita (quadrado exato 60x60)
            addRect(
                Rect(
                    left = offsetX + 418f * scale,
                    top = offsetY + 477f * scale,
                    right = offsetX + 478f * scale,
                    bottom = offsetY + 537f * scale
                )
            )
        }

        // Fase 2: Preenchimento sólido fluido da malha com a cor oficial verde
        val fillAlpha = ((progress - 0.35f) / 0.55f).coerceIn(0f, 1f)
        if (fillAlpha > 0f) {
            drawPath(
                path = fullGlyphPath,
                brush = iconBrush,
                alpha = fillAlpha
            )
        }

        // Fase 1: Desenho progressivo do contorno das linhas
        val strokeProgress = (progress * 1.35f).coerceIn(0f, 1f)
        val strokeAlpha = (1f - fillAlpha).coerceIn(0f, 1f)
        if (strokeProgress > 0.001f && strokeAlpha > 0.001f) {
            subPath.reset()
            pathMeasure.setPath(outerPath, false)
            val length = pathMeasure.length
            if (length > 0f) {
                pathMeasure.getSegment(0f, length * strokeProgress, subPath, true)
                drawPath(path = subPath, brush = iconBrush, style = strokeStyle, alpha = strokeAlpha)
            }

            // Quadrados das 3 estações (stroke de 60x60)
            val squareProgress = (progress * 1.5f - 0.2f).coerceIn(0f, 1f)
            if (squareProgress > 0.001f) {
                val sqSize = 60f * scale
                val alpha = squareProgress * strokeAlpha
                drawRect(
                    brush = iconBrush,
                    topLeft = Offset(offsetX + 269f * scale, offsetY + 59f * scale),
                    size = androidx.compose.ui.geometry.Size(sqSize, sqSize),
                    style = strokeStyle,
                    alpha = alpha
                )
                drawRect(
                    brush = iconBrush,
                    topLeft = Offset(offsetX + 60f * scale, offsetY + 298f * scale),
                    size = androidx.compose.ui.geometry.Size(sqSize, sqSize),
                    style = strokeStyle,
                    alpha = alpha
                )
                drawRect(
                    brush = iconBrush,
                    topLeft = Offset(offsetX + 418f * scale, offsetY + 477f * scale),
                    size = androidx.compose.ui.geometry.Size(sqSize, sqSize),
                    style = strokeStyle,
                    alpha = alpha
                )
            }
        }
    }
}
