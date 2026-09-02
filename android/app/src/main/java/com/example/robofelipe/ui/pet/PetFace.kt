package com.example.robofelipe.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.robofelipe.data.Emotion

// Face do pet desenhada em Canvas — olhos, boca e corpo mudam por mood.
// MVP sem sprites externos (ADR-021): primitivas do DrawScope bastam
// para distinguir as 13 expressões.

@Composable
fun PetFace(
    mood: Emotion,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            drawBody(mood)
            drawEyes(mood)
            drawMouth(mood)
            drawExtras(mood)
        }
    }
}

private fun DrawScope.drawBody(mood: Emotion) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2.8f

    // Corpo torto para dizzy e scared
    val tilt = when (mood) {
        Emotion.dizzy -> -15f
        Emotion.scared -> -8f
        else -> 0f
    }

    rotateDegrees(tilt) {
        drawCircle(
            color = BodyColor,
            radius = radius,
            center = center,
        )
    }
}

private fun DrawScope.drawEyes(mood: Emotion) {
    val eyeY = size.height / 2f - size.minDimension / 8f
    val leftEyeX = size.width / 2f - size.minDimension / 6f
    val rightEyeX = size.width / 2f + size.minDimension / 6f
    val eyeRadius = size.minDimension / 14f

    when (mood) {
        Emotion.happy, Emotion.excited -> {
            drawCircle(EyeColor, eyeRadius, Offset(leftEyeX, eyeY))
            drawCircle(EyeColor, eyeRadius, Offset(rightEyeX, eyeY))
            if (mood == Emotion.excited) {
                // Brilho nos olhos
                drawCircle(Color.White, eyeRadius / 3f, Offset(leftEyeX - eyeRadius / 3f, eyeY - eyeRadius / 3f))
                drawCircle(Color.White, eyeRadius / 3f, Offset(rightEyeX - eyeRadius / 3f, eyeY - eyeRadius / 3f))
            }
        }

        Emotion.hungry -> {
            // Olhos pequenos
            drawCircle(EyeColor, eyeRadius * 0.5f, Offset(leftEyeX, eyeY))
            drawCircle(EyeColor, eyeRadius * 0.5f, Offset(rightEyeX, eyeY))
        }

        Emotion.tired, Emotion.bored -> {
            // Olhos semi-fechados (linhas)
            drawLine(EyeColor, Offset(leftEyeX - eyeRadius, eyeY), Offset(leftEyeX + eyeRadius, eyeY), strokeWidth = 3f)
            drawLine(EyeColor, Offset(rightEyeX - eyeRadius, eyeY), Offset(rightEyeX + eyeRadius, eyeY), strokeWidth = 3f)
        }

        Emotion.sleepy -> {
            // Olhos fechados (curvas)
            drawEyeClosed(leftEyeX, eyeY, eyeRadius)
            drawEyeClosed(rightEyeX, eyeY, eyeRadius)
        }

        Emotion.sad -> {
            // Olhos caídos
            drawCircle(EyeColor, eyeRadius * 0.7f, Offset(leftEyeX, eyeY + eyeRadius * 0.3f))
            drawCircle(EyeColor, eyeRadius * 0.7f, Offset(rightEyeX, eyeY + eyeRadius * 0.3f))
        }

        Emotion.curious -> {
            // Olhos grandes
            drawCircle(EyeColor, eyeRadius * 1.3f, Offset(leftEyeX, eyeY))
            drawCircle(EyeColor, eyeRadius * 1.3f, Offset(rightEyeX, eyeY))
        }

        Emotion.playful -> {
            // Olhos piscando (um aberto, um fechado)
            drawCircle(EyeColor, eyeRadius, Offset(leftEyeX, eyeY))
            drawEyeClosed(rightEyeX, eyeY, eyeRadius * 0.7f)
        }

        Emotion.mischievous -> {
            // Olhos tortos (assimétricos)
            drawCircle(EyeColor, eyeRadius, Offset(leftEyeX - eyeRadius * 0.3f, eyeY - eyeRadius * 0.2f))
            drawCircle(EyeColor, eyeRadius, Offset(rightEyeX + eyeRadius * 0.3f, eyeY + eyeRadius * 0.2f))
        }

        Emotion.dizzy -> {
            // Espirais nos olhos
            drawSpiral(Offset(leftEyeX, eyeY), eyeRadius)
            drawSpiral(Offset(rightEyeX, eyeY), eyeRadius)
        }

        Emotion.scared -> {
            // Olhos arregalados
            drawCircle(EyeColor, eyeRadius * 1.5f, Offset(leftEyeX, eyeY))
            drawCircle(EyeColor, eyeRadius * 1.5f, Offset(rightEyeX, eyeY))
        }

        Emotion.dirty -> {
            drawCircle(EyeColor, eyeRadius, Offset(leftEyeX, eyeY))
            drawCircle(EyeColor, eyeRadius, Offset(rightEyeX, eyeY))
        }
    }
}

private fun DrawScope.drawEyeClosed(x: Float, y: Float, radius: Float) {
    val path = Path().apply {
        moveTo(x - radius, y)
        quadraticTo(x, y + radius * 0.6f, x + radius, y)
    }
    drawPath(path, EyeColor, style = Stroke(width = 3f))
}

internal fun DrawScope.drawSpiral(center: Offset, radius: Float) {
    val path = Path().apply {
        var angle = 0f
        var r = radius
        moveTo(center.x + r, center.y)
        for (i in 0..50) {
            angle += 0.3f
            r -= radius / 60f
            if (r <= 0f) break
            val x = center.x + r * kotlin.math.cos(angle)
            val y = center.y + r * kotlin.math.sin(angle)
            lineTo(x, y)
        }
    }
    drawPath(path, EyeColor, style = Stroke(width = 2f))
}

private fun DrawScope.drawMouth(mood: Emotion) {
    val mouthY = size.height / 2f + size.minDimension / 5f
    val mouthCenterX = size.width / 2f
    val mouthWidth = size.minDimension / 8f

    when (mood) {
        Emotion.happy -> {
            // Boca sorrindo (arco para cima)
            val path = Path().apply {
                moveTo(mouthCenterX - mouthWidth, mouthY - mouthWidth / 3f)
                quadraticTo(mouthCenterX, mouthY + mouthWidth / 2f, mouthCenterX + mouthWidth, mouthY - mouthWidth / 3f)
            }
            drawPath(path, MouthColor, style = Stroke(width = 4f))
        }

        Emotion.excited -> {
            // Boca grande (círculo aberto)
            drawCircle(MouthColor, mouthWidth, Offset(mouthCenterX, mouthY))
        }

        Emotion.hungry -> {
            // Boca aberta (oval)
            drawOval(
                color = MouthColor,
                topLeft = Offset(mouthCenterX - mouthWidth / 2f, mouthY - mouthWidth / 2f),
                size = Size(mouthWidth, mouthWidth * 1.3f),
            )
        }

        Emotion.tired, Emotion.bored -> {
            // Boca reta
            drawLine(MouthColor, Offset(mouthCenterX - mouthWidth, mouthY), Offset(mouthCenterX + mouthWidth, mouthY), strokeWidth = 3f)
        }

        Emotion.sad -> {
            // Boca invertida (arco para baixo)
            val path = Path().apply {
                moveTo(mouthCenterX - mouthWidth, mouthY + mouthWidth / 3f)
                quadraticTo(mouthCenterX, mouthY - mouthWidth / 2f, mouthCenterX + mouthWidth, mouthY + mouthWidth / 3f)
            }
            drawPath(path, MouthColor, style = Stroke(width = 4f))
        }

        Emotion.sleepy -> {
            // Boca levemente aberta
            drawOval(
                color = MouthColor,
                topLeft = Offset(mouthCenterX - mouthWidth / 3f, mouthY - mouthWidth / 4f),
                size = Size(mouthWidth * 0.6f, mouthWidth * 0.4f),
            )
        }

        Emotion.playful -> {
            // Boca aberta (empolgação)
            drawOval(
                color = MouthColor,
                topLeft = Offset(mouthCenterX - mouthWidth / 2f, mouthY - mouthWidth / 3f),
                size = Size(mouthWidth, mouthWidth * 0.7f),
            )
        }

        Emotion.curious -> {
            // Boca pequena "o"
            drawCircle(MouthColor, mouthWidth / 3f, Offset(mouthCenterX, mouthY))
        }

        Emotion.mischievous -> {
            // Boca marota (sorrimso torto)
            val path = Path().apply {
                moveTo(mouthCenterX - mouthWidth, mouthY)
                quadraticTo(mouthCenterX - mouthWidth / 2f, mouthY + mouthWidth / 2f, mouthCenterX, mouthY + mouthWidth / 4f)
                quadraticTo(mouthCenterX + mouthWidth / 2f, mouthY, mouthCenterX + mouthWidth, mouthY + mouthWidth / 3f)
            }
            drawPath(path, MouthColor, style = Stroke(width = 4f))
        }

        Emotion.dizzy -> {
            // Boca ondulada
            val path = Path().apply {
                moveTo(mouthCenterX - mouthWidth, mouthY)
                for (i in 1..4) {
                    val x = mouthCenterX - mouthWidth + (mouthWidth * 2f * i / 4f)
                    val y = if (i % 2 == 0) mouthY - mouthWidth / 3f else mouthY + mouthWidth / 3f
                    lineTo(x, y)
                }
            }
            drawPath(path, MouthColor, style = Stroke(width = 3f))
        }

        Emotion.scared -> {
            // Boca tremendo (linha ondulada)
            val path = Path().apply {
                moveTo(mouthCenterX - mouthWidth, mouthY)
                for (i in 1..6) {
                    val x = mouthCenterX - mouthWidth + (mouthWidth * 2f * i / 6f)
                    val y = if (i % 2 == 0) mouthY - mouthWidth / 4f else mouthY + mouthWidth / 4f
                    lineTo(x, y)
                }
            }
            drawPath(path, MouthColor, style = Stroke(width = 3f))
        }

        Emotion.dirty -> {
            // Boca neutra
            drawLine(MouthColor, Offset(mouthCenterX - mouthWidth, mouthY), Offset(mouthCenterX + mouthWidth, mouthY), strokeWidth = 3f)
        }
    }
}

private fun DrawScope.drawExtras(mood: Emotion) {
    when (mood) {
        Emotion.dirty -> {
            // Nuvens de poeira ao redor
            val positions = listOf(
                Offset(size.width * 0.15f, size.height * 0.2f),
                Offset(size.width * 0.85f, size.height * 0.3f),
                Offset(size.width * 0.2f, size.height * 0.8f),
                Offset(size.width * 0.8f, size.height * 0.75f),
            )
            positions.forEach { pos ->
                drawCircle(DustColor, size.minDimension / 18f, pos)
            }
        }

        Emotion.sleepy -> {
            // Zzz flutuando (desenhado estaticamente; animação em PetAnimations)
            val zStart = Offset(size.width * 0.7f, size.height * 0.25f)
            drawTextZ(zStart, size.minDimension / 14f)
            drawTextZ(Offset(zStart.x + size.minDimension / 20f, zStart.y - size.minDimension / 18f), size.minDimension / 18f)
            drawTextZ(Offset(zStart.x + size.minDimension / 12f, zStart.y - size.minDimension / 12f), size.minDimension / 22f)
        }

        else -> {}
    }
}

private fun DrawScope.drawTextZ(position: Offset, size: Float) {
    val path = Path().apply {
        moveTo(position.x, position.y)
        lineTo(position.x + size, position.y)
        lineTo(position.x, position.y + size)
        lineTo(position.x + size, position.y + size)
    }
    drawPath(path, EyeColor, style = Stroke(width = 2f))
}

// Rotação do corpo para dizzy/scared — DrawScope.rotate desenha num
// sistema de coordenadas inclinado em torno do centro.
private fun DrawScope.rotateDegrees(degrees: Float, block: DrawScope.() -> Unit) {
    if (degrees == 0f) {
        block()
    } else {
        rotate(degrees, Offset(size.width / 2f, size.height / 2f), block)
    }
}

private val BodyColor = Color(0xFF6FB3E8)
private val EyeColor = Color(0xFF1A1A2E)
private val MouthColor = Color(0xFF1A1A2E)
private val DustColor = Color(0xFFB0A890)
