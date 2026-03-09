package com.hualien.taxidriver.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hualien.taxidriver.ui.theme.HappyYellow
import kotlin.math.PI
import kotlin.math.sin

/**
 * 語音波形指示器
 * 用於顯示錄音時的音量波形動畫
 */
@Composable
fun VoiceWaveIndicator(
    amplitude: Int,
    modifier: Modifier = Modifier,
    color: Color = HappyYellow
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier.height(60.dp)) {
        val barCount = 5
        val barWidth = size.width / (barCount * 2)
        val maxHeight = size.height * 0.8f
        val normalizedAmplitude = (amplitude / 100f).coerceIn(0.1f, 1f)

        for (i in 0 until barCount) {
            val phase = wavePhase + i * 0.5f
            val heightMultiplier = (sin(phase.toDouble()).toFloat() + 1) / 2 * normalizedAmplitude
            val barHeight = maxHeight * (0.3f + heightMultiplier * 0.7f)

            val x = size.width / 2 + (i - barCount / 2) * barWidth * 2

            drawRoundRect(
                color = color,
                topLeft = Offset(x - barWidth / 2, (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}
