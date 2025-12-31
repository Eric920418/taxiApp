package com.hualien.taxidriver.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hualien.taxidriver.service.VoiceRecorderService

/**
 * 語音指令按鈕組件
 * 支援錄音狀態顯示和波形動畫
 */
@Composable
fun VoiceCommandButton(
    recordingState: VoiceRecorderService.RecordingState,
    amplitude: Int,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current

    // 權限檢查
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            onStartRecording()
        }
    }

    // 動畫狀態
    val isRecording = recordingState == VoiceRecorderService.RecordingState.Recording
    val isProcessing = recordingState == VoiceRecorderService.RecordingState.Processing

    // 按鈕縮放動畫（根據振幅）
    val scale by animateFloatAsState(
        targetValue = if (isRecording) {
            1f + (amplitude / 32767f) * 0.3f
        } else {
            1f
        },
        animationSpec = tween(100),
        label = "scale"
    )

    // 按鈕顏色動畫
    val buttonColor by animateColorAsState(
        targetValue = when {
            isRecording -> MaterialTheme.colorScheme.error
            isProcessing -> MaterialTheme.colorScheme.tertiary
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "buttonColor"
    )

    // 脈衝動畫（處理中）
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 外圈波紋效果（錄音中）
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale * 1.2f)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }

        // 處理中脈衝
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .background(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }

        // 主按鈕
        FloatingActionButton(
            onClick = {
                when {
                    !hasPermission -> {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    isRecording -> {
                        onStopRecording()
                    }
                    isProcessing -> {
                        // 處理中不可操作
                    }
                    else -> {
                        onStartRecording()
                    }
                }
            },
            modifier = Modifier
                .size(64.dp)
                .scale(if (isRecording) scale else 1f),
            containerColor = buttonColor,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
                !hasPermission -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "需要麥克風權限",
                        modifier = Modifier.size(28.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (isRecording) "停止錄音" else "開始語音指令",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * 大尺寸語音按鈕（適合老年人使用）
 */
@Composable
fun LargeVoiceCommandButton(
    recordingState: VoiceRecorderService.RecordingState,
    amplitude: Int,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current

    // 權限檢查
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            onStartRecording()
        }
    }

    val isRecording = recordingState == VoiceRecorderService.RecordingState.Recording
    val isProcessing = recordingState == VoiceRecorderService.RecordingState.Processing

    // 振幅動畫
    val scale by animateFloatAsState(
        targetValue = if (isRecording) {
            1f + (amplitude / 32767f) * 0.2f
        } else {
            1f
        },
        animationSpec = tween(100),
        label = "scale"
    )

    // 顏色
    val buttonColor = when {
        isRecording -> MaterialTheme.colorScheme.error
        isProcessing -> MaterialTheme.colorScheme.tertiary
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    // 狀態文字
    val statusText = when {
        isRecording -> "正在聆聽..."
        isProcessing -> "處理中..."
        !hasPermission -> "需要麥克風權限"
        else -> "按住說話"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 大按鈕
        Box(
            contentAlignment = Alignment.Center
        ) {
            // 外圈效果
            if (isRecording || isProcessing) {
                val infiniteTransition = rememberInfiniteTransition(label = "outerRing")
                val ringScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ringScale"
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(ringScale)
                        .background(
                            color = buttonColor.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                )
            }

            // 主按鈕
            Button(
                onClick = {
                    when {
                        !hasPermission -> {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        isRecording -> {
                            onStopRecording()
                        }
                        isProcessing -> {
                            // 不可操作
                        }
                        else -> {
                            onStartRecording()
                        }
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                ),
                enabled = enabled && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (hasPermission) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = statusText,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // 狀態文字
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        // 振幅指示器（錄音中顯示）
        if (isRecording) {
            AmplitudeIndicator(
                amplitude = amplitude,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(8.dp)
            )
        }
    }
}

/**
 * 振幅指示器
 */
@Composable
private fun AmplitudeIndicator(
    amplitude: Int,
    modifier: Modifier = Modifier
) {
    val normalizedAmplitude = (amplitude / 32767f).coerceIn(0f, 1f)

    val animatedWidth by animateFloatAsState(
        targetValue = normalizedAmplitude,
        animationSpec = tween(50),
        label = "amplitudeWidth"
    )

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedWidth)
                .background(
                    color = MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.small
                )
        )
    }
}

/**
 * 語音指令卡片（包含按鈕和提示）- 司機端
 */
@Composable
fun VoiceCommandCard(
    recordingState: VoiceRecorderService.RecordingState,
    amplitude: Int,
    lastTranscription: String?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 標題
            Text(
                text = "語音助理",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 語音按鈕
            LargeVoiceCommandButton(
                recordingState = recordingState,
                amplitude = amplitude,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onCancelRecording = onCancelRecording,
                enabled = enabled
            )

            // 上次識別結果
            if (!lastTranscription.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "「$lastTranscription」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // 使用提示
            Text(
                text = "試著說：「接」「不要」「到了」「出發」「結束」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 乘客端語音指令卡片
 */
@Composable
fun PassengerVoiceCommandCard(
    recordingState: VoiceRecorderService.RecordingState,
    amplitude: Int,
    lastTranscription: String?,
    hasActiveOrder: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 標題
            Text(
                text = "語音叫車",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 語音按鈕
            LargeVoiceCommandButton(
                recordingState = recordingState,
                amplitude = amplitude,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onCancelRecording = onCancelRecording,
                enabled = enabled
            )

            // 上次識別結果
            if (!lastTranscription.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "「$lastTranscription」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // 根據狀態顯示不同的提示
            val hintText = if (hasActiveOrder) {
                "試著說：「司機在哪」「取消訂單」「打給司機」"
            } else {
                "試著說：「去火車站」「去太魯閣」「去慈濟醫院」"
            }

            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 乘客端浮動語音按鈕（顯示在地圖上）
 */
@Composable
fun PassengerFloatingVoiceButton(
    recordingState: VoiceRecorderService.RecordingState,
    amplitude: Int,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            onStartRecording()
        }
    }

    val isRecording = recordingState == VoiceRecorderService.RecordingState.Recording
    val isProcessing = recordingState == VoiceRecorderService.RecordingState.Processing

    // 振幅動畫
    val scale by animateFloatAsState(
        targetValue = if (isRecording) {
            1f + (amplitude / 32767f) * 0.3f
        } else {
            1f
        },
        animationSpec = tween(100),
        label = "scale"
    )

    // 顏色
    val buttonColor by animateColorAsState(
        targetValue = when {
            isRecording -> MaterialTheme.colorScheme.error
            isProcessing -> MaterialTheme.colorScheme.tertiary
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "buttonColor"
    )

    // 脈衝動畫
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 外圈效果
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(scale * 1.3f)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = pulseAlpha),
                        shape = CircleShape
                    )
            )
        }

        // 主按鈕
        FloatingActionButton(
            onClick = {
                when {
                    !hasPermission -> {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    isRecording -> {
                        onStopRecording()
                    }
                    isProcessing -> {
                        // 處理中不可操作
                    }
                    else -> {
                        onStartRecording()
                    }
                }
            },
            modifier = Modifier
                .size(56.dp)
                .scale(if (isRecording) scale else 1f),
            containerColor = buttonColor,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
                !hasPermission -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "需要麥克風權限",
                        modifier = Modifier.size(24.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (isRecording) "停止錄音" else "語音叫車",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
