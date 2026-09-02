package com.example.robofelipe.ui.pet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robofelipe.data.Emotion
import kotlinx.coroutines.delay

// Animações das 5 Action types do Plano: dance, express_emotion,
// get_dizzy, sleep, speak. Cada uma recebe um callback onComplete para
// o Plano executor saber quando a action terminou.

// --- dance ---------------------------------------------------------------
@Composable
fun DanceAnimation(
    durationMs: Long,
    mood: Emotion,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "dance")
    val rotation by transition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dance_rotation",
    )
    val sway by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dance_sway",
    )

    LaunchedEffect(durationMs) {
        delay(durationMs)
        onComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        PetFace(mood = mood, modifier = Modifier.size(200.dp))
    }
}

// --- express_emotion -----------------------------------------------------
@Composable
fun ExpressEmotionAnimation(
    emotion: Emotion,
    previousMood: Emotion,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Troca de face via AnimatedContent — fade entre mood anterior e novo
    LaunchedEffect(emotion) {
        delay(600)
        onComplete()
    }

    AnimatedContent(
        targetState = emotion,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        },
        modifier = modifier.fillMaxSize(),
        label = "express_emotion",
    ) { target ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            PetFace(mood = target, modifier = Modifier.size(200.dp))
        }
    }
}

// --- get_dizzy -----------------------------------------------------------
@Composable
fun GetDizzyAnimation(
    intensity: Double,
    mood: Emotion,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "dizzy")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f * intensity.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
        ),
        label = "dizzy_rotation",
    )

    LaunchedEffect(intensity) {
        delay(2000)
        onComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .rotate(rotation * 0.15f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            // Espirais nos olhos + corpo tombando
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF6FB3E8), size.minDimension / 2.8f, center)
            // Espirais
            val eyeY = size.height / 2f - size.minDimension / 8f
            val leftEyeX = size.width / 2f - size.minDimension / 6f
            val rightEyeX = size.width / 2f + size.minDimension / 6f
            drawSpiral(Offset(leftEyeX, eyeY), size.minDimension / 14f)
            drawSpiral(Offset(rightEyeX, eyeY), size.minDimension / 14f)
        }
    }
}

// --- sleep ---------------------------------------------------------------
@Composable
fun SleepAnimation(
    durationMs: Long,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "sleep")
    val zOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sleep_z",
    )

    LaunchedEffect(durationMs) {
        delay(durationMs)
        onComplete()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        PetFace(mood = Emotion.sleepy, modifier = Modifier.size(200.dp))
        // Zzz flutuando
        Text(
            text = "Z",
            fontSize = (12 + zOffset / 2f).sp,
            color = Color(0xFF6FB3E8),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = (40 + zOffset).dp, top = (30 + zOffset).dp),
        )
        Text(
            text = "z",
            fontSize = (10 + zOffset / 3f).sp,
            color = Color(0xFF6FB3E8),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = (25 + zOffset * 0.5f).dp, top = (50 + zOffset * 0.7f).dp),
        )
    }
}

// --- speak ---------------------------------------------------------------
@Composable
fun SpeakAnimation(
    text: String,
    mood: Emotion,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Boca se move (abre/fecha) + subtítulo do texto
    val transition = rememberInfiniteTransition(label = "speak")
    val mouthOpen by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "speak_mouth",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF6FB3E8), size.minDimension / 2.8f, center)
            // Olhos
            val eyeY = size.height / 2f - size.minDimension / 8f
            val leftEyeX = size.width / 2f - size.minDimension / 6f
            val rightEyeX = size.width / 2f + size.minDimension / 6f
            drawCircle(Color(0xFF1A1A2E), size.minDimension / 14f, Offset(leftEyeX, eyeY))
            drawCircle(Color(0xFF1A1A2E), size.minDimension / 14f, Offset(rightEyeX, eyeY))
            // Boca animada
            val mouthY = size.height / 2f + size.minDimension / 5f
            val mouthCenterX = size.width / 2f
            val mouthWidth = size.minDimension / 8f
            val mouthHeight = mouthWidth * (0.3f + mouthOpen * 0.5f)
            drawOval(
                color = Color(0xFF1A1A2E),
                topLeft = Offset(mouthCenterX - mouthWidth / 2f, mouthY - mouthHeight / 2f),
                size = androidx.compose.ui.geometry.Size(mouthWidth, mouthHeight),
            )
        }
        // Subtítulo
        Text(
            text = text,
            fontSize = 18.sp,
            color = Color(0xFF1A1A2E),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
