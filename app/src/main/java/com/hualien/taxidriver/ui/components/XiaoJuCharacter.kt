package com.hualien.taxidriver.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualien.taxidriver.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * 小橘 - 語音助手動畫角色
 *
 * 一個友善的圓形橘色角色，有大眼睛和溫暖笑容
 * 根據不同狀態展現不同的表情和動畫
 */

// ==================== 角色狀態定義 ====================

enum class CharacterState {
    IDLE,           // 待機：微笑、輕微呼吸動畫
    LISTENING,      // 聆聽：耳朵豎起、眼睛放大
    THINKING,       // 思考：眨眼、頭部微微轉動
    SPEAKING,       // 說話：嘴巴開合動畫
    HAPPY,          // 開心：彈跳、發光效果
    SAD,            // 難過：眼睛下垂、嘴巴下彎
    WAITING         // 等待中：緩慢脈動
}

// ==================== 主要組件 ====================

@Composable
fun XiaoJuCharacter(
    state: CharacterState,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    message: String? = null,        // 角色說的話
    amplitude: Int = 0              // 語音振幅（用於說話動畫）
) {
    // 狀態動畫
    val infiniteTransition = rememberInfiniteTransition(label = "character")

    // 呼吸動畫（縮放）
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // 彈跳動畫（開心狀態）
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (state == CharacterState.HAPPY) -20f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // 思考動畫（旋轉）
    val thinkRotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "think"
    )

    // 聆聽動畫（耳朵動）
    val listenScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listen"
    )

    // 嘴巴開合動畫（說話狀態）
    val mouthOpenAmount = remember(amplitude) {
        (amplitude / 100f).coerceIn(0.2f, 1f)
    }

    // 眨眼動畫
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0
                0f at 3700
                1f at 3850
                0f at 4000
            }
        ),
        label = "blink"
    )

    // 脈動動畫（等待狀態）
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 背景光暈顏色
    val glowColor by animateColorAsState(
        targetValue = when (state) {
            CharacterState.IDLE -> WarmCoral.copy(alpha = 0.2f)
            CharacterState.LISTENING -> HappyYellow.copy(alpha = 0.4f)
            CharacterState.THINKING -> Color(0xFF64B5F6).copy(alpha = 0.3f)
            CharacterState.SPEAKING -> SoftGreen.copy(alpha = 0.3f)
            CharacterState.HAPPY -> HappyYellow.copy(alpha = 0.5f)
            CharacterState.SAD -> Color.Gray.copy(alpha = 0.2f)
            CharacterState.WAITING -> WarmCoral.copy(alpha = 0.3f * pulseAlpha)
        },
        animationSpec = tween(500),
        label = "glow"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 角色主體
        Box(
            modifier = Modifier
                .size(size)
                .offset(y = bounceOffset.dp)
                .scale(
                    when (state) {
                        CharacterState.LISTENING -> listenScale
                        CharacterState.HAPPY -> breathScale * 1.1f
                        else -> breathScale
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // 背景光暈
            Box(
                modifier = Modifier
                    .size(size * 1.3f)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor, Color.Transparent)
                        )
                    )
            )

            // 角色繪製
            Canvas(
                modifier = Modifier
                    .size(size)
                    .then(
                        if (state == CharacterState.THINKING) {
                            Modifier // rotation handled in drawScope
                        } else Modifier
                    )
            ) {
                val rotation = if (state == CharacterState.THINKING) thinkRotation else 0f

                rotate(rotation) {
                    drawXiaoJu(
                        state = state,
                        blinkProgress = blinkProgress,
                        mouthOpenAmount = mouthOpenAmount,
                        listenScale = listenScale
                    )
                }
            }
        }

        // 狀態文字氣泡
        message?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            SpeechBubble(
                message = msg,
                state = state
            )
        }
    }
}

// ==================== 繪製函數 ====================

private fun DrawScope.drawXiaoJu(
    state: CharacterState,
    blinkProgress: Float,
    mouthOpenAmount: Float,
    listenScale: Float
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val radius = size.minDimension / 2 * 0.85f

    // 1. 身體（圓形，漸層橘色）
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF8A70),  // 亮橘色
                Color(0xFFFF6B4A),  // 主橘色
                Color(0xFFE55530)   // 深橘色
            ),
            center = Offset(centerX - radius * 0.2f, centerY - radius * 0.2f),
            radius = radius * 1.2f
        ),
        center = Offset(centerX, centerY),
        radius = radius
    )

    // 2. 腮紅（兩邊粉色圓圈）
    val blushRadius = radius * 0.15f
    val blushY = centerY + radius * 0.15f
    val blushColor = Color(0xFFFFB4A2).copy(alpha = 0.6f)

    // 左腮紅
    drawCircle(
        color = blushColor,
        center = Offset(centerX - radius * 0.55f, blushY),
        radius = blushRadius
    )
    // 右腮紅
    drawCircle(
        color = blushColor,
        center = Offset(centerX + radius * 0.55f, blushY),
        radius = blushRadius
    )

    // 3. 眼睛
    val eyeY = centerY - radius * 0.1f
    val eyeSpacing = radius * 0.35f
    val eyeRadius = radius * 0.18f

    // 眼睛高度（眨眼效果）
    val eyeHeight = eyeRadius * 2 * (1 - blinkProgress * 0.9f)

    // 根據狀態調整眼睛
    val eyeScale = when (state) {
        CharacterState.LISTENING -> 1.2f
        CharacterState.HAPPY -> 1.1f
        CharacterState.SAD -> 0.9f
        else -> 1f
    }

    // 左眼
    drawEye(
        center = Offset(centerX - eyeSpacing, eyeY),
        radius = eyeRadius * eyeScale,
        height = eyeHeight * eyeScale,
        state = state
    )
    // 右眼
    drawEye(
        center = Offset(centerX + eyeSpacing, eyeY),
        radius = eyeRadius * eyeScale,
        height = eyeHeight * eyeScale,
        state = state
    )

    // 4. 嘴巴
    val mouthY = centerY + radius * 0.35f
    drawMouth(
        center = Offset(centerX, mouthY),
        width = radius * 0.5f,
        state = state,
        openAmount = mouthOpenAmount
    )

    // 5. 耳朵（聆聽狀態時更明顯）
    if (state == CharacterState.LISTENING) {
        drawEars(
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            scale = listenScale
        )
    }

    // 6. 高光
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        center = Offset(centerX - radius * 0.3f, centerY - radius * 0.35f),
        radius = radius * 0.15f
    )
}

private fun DrawScope.drawEye(
    center: Offset,
    radius: Float,
    height: Float,
    state: CharacterState
) {
    // 眼白
    drawOval(
        color = Color.White,
        topLeft = Offset(center.x - radius, center.y - height / 2),
        size = Size(radius * 2, height)
    )

    // 瞳孔
    val pupilRadius = radius * 0.55f
    val pupilOffset = when (state) {
        CharacterState.THINKING -> Offset(radius * 0.1f, 0f)
        CharacterState.LISTENING -> Offset(0f, -radius * 0.1f)
        else -> Offset.Zero
    }

    if (height > radius * 0.3f) {  // 只有眼睛夠開時才畫瞳孔
        // 瞳孔（深色）
        drawCircle(
            color = Color(0xFF2D1810),
            center = center + pupilOffset,
            radius = pupilRadius.coerceAtMost(height / 2 * 0.8f)
        )

        // 瞳孔高光
        drawCircle(
            color = Color.White,
            center = center + pupilOffset + Offset(-pupilRadius * 0.3f, -pupilRadius * 0.3f),
            radius = pupilRadius * 0.3f
        )
    }

    // 難過狀態：眉毛下垂
    if (state == CharacterState.SAD) {
        val path = Path().apply {
            moveTo(center.x - radius, center.y - height / 2 - radius * 0.3f)
            quadraticTo(
                center.x, center.y - height / 2 - radius * 0.1f,
                center.x + radius, center.y - height / 2 - radius * 0.5f
            )
        }
        drawPath(path, Color(0xFF4A3728), style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.15f))
    }
}

private fun DrawScope.drawMouth(
    center: Offset,
    width: Float,
    state: CharacterState,
    openAmount: Float
) {
    val path = Path()

    when (state) {
        CharacterState.SPEAKING -> {
            // 說話：開口橢圓形
            val mouthHeight = width * 0.4f * openAmount
            drawOval(
                color = Color(0xFF8B4513),
                topLeft = Offset(center.x - width / 2, center.y - mouthHeight / 2),
                size = Size(width, mouthHeight)
            )
            // 舌頭
            drawOval(
                color = Color(0xFFFF6B6B),
                topLeft = Offset(center.x - width * 0.3f, center.y),
                size = Size(width * 0.6f, mouthHeight * 0.4f)
            )
        }

        CharacterState.HAPPY -> {
            // 開心：大大的笑容
            path.apply {
                moveTo(center.x - width * 0.6f, center.y - width * 0.1f)
                quadraticTo(
                    center.x, center.y + width * 0.5f,
                    center.x + width * 0.6f, center.y - width * 0.1f
                )
            }
            drawPath(
                path,
                Color(0xFF8B4513),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = width * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        CharacterState.SAD -> {
            // 難過：下彎的嘴
            path.apply {
                moveTo(center.x - width * 0.4f, center.y + width * 0.1f)
                quadraticTo(
                    center.x, center.y - width * 0.2f,
                    center.x + width * 0.4f, center.y + width * 0.1f
                )
            }
            drawPath(
                path,
                Color(0xFF8B4513),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = width * 0.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        else -> {
            // 默認：微笑
            path.apply {
                moveTo(center.x - width * 0.4f, center.y)
                quadraticTo(
                    center.x, center.y + width * 0.35f,
                    center.x + width * 0.4f, center.y
                )
            }
            drawPath(
                path,
                Color(0xFF8B4513),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = width * 0.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.drawEars(
    centerX: Float,
    centerY: Float,
    radius: Float,
    scale: Float
) {
    val earWidth = radius * 0.25f * scale
    val earHeight = radius * 0.4f * scale

    // 左耳
    val leftEarPath = Path().apply {
        moveTo(centerX - radius * 0.7f, centerY - radius * 0.5f)
        quadraticTo(
            centerX - radius * 0.9f, centerY - radius * 0.9f,
            centerX - radius * 0.6f, centerY - radius * 0.8f
        )
        lineTo(centerX - radius * 0.7f, centerY - radius * 0.5f)
        close()
    }
    drawPath(leftEarPath, Color(0xFFFF6B4A))

    // 右耳
    val rightEarPath = Path().apply {
        moveTo(centerX + radius * 0.7f, centerY - radius * 0.5f)
        quadraticTo(
            centerX + radius * 0.9f, centerY - radius * 0.9f,
            centerX + radius * 0.6f, centerY - radius * 0.8f
        )
        lineTo(centerX + radius * 0.7f, centerY - radius * 0.5f)
        close()
    }
    drawPath(rightEarPath, Color(0xFFFF6B4A))
}

// ==================== 對話氣泡 ====================

@Composable
private fun SpeechBubble(
    message: String,
    state: CharacterState
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (state) {
            CharacterState.HAPPY -> HappyYellowLight
            CharacterState.SAD -> Color(0xFFE0E0E0)
            CharacterState.SPEAKING -> SoftGreenLight
            else -> Color.White
        },
        animationSpec = tween(300),
        label = "bubbleColor"
    )

    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .shadow(8.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(
                color = backgroundColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = message,
            style = WarmTypography.bodyLarge,
            color = WarmBrown,
            textAlign = TextAlign.Center
        )
    }
}

// ==================== 語音波形指示器 ====================

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
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
    }
}

// ==================== Easing 函數 ====================

private val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
private val EaseInOutQuad = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
