package com.hualien.taxidriver.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualien.taxidriver.data.remote.dto.VoiceChatMessage
import com.hualien.taxidriver.data.remote.dto.VoiceChatState

/**
 * 單個聊天氣泡
 */
@Composable
fun VoiceChatBubble(
    message: VoiceChatMessage,
    isFromMe: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isFromMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val alignment = if (isFromMe) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        // 發送者名稱（對方訊息才顯示）
        if (!isFromMe) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }

        // 訊息氣泡
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromMe) 16.dp else 4.dp,
                bottomEnd = if (isFromMe) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                Text(
                    text = message.getFormattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * 聊天記錄列表
 */
@Composable
fun VoiceChatHistory(
    messages: List<VoiceChatMessage>,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 新訊息時自動滾動到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "按住下方按鈕說話",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(messages) { message ->
                VoiceChatBubble(
                    message = message,
                    isFromMe = message.senderId == currentUserId
                )
            }
        }
    }
}

/**
 * 語音對講按鈕（按住說話）
 */
@Composable
fun VoiceChatButton(
    isRecording: Boolean,
    state: VoiceChatState,
    amplitude: Int,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")

    // 錄音時的脈動動畫
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // 根據振幅計算額外縮放
    val amplitudeScale = if (isRecording) {
        1f + (amplitude / 32767f) * 0.3f
    } else {
        1f
    }

    // 按鈕顏色
    val buttonColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            isRecording -> MaterialTheme.colorScheme.error
            state == VoiceChatState.PROCESSING -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(200),
        label = "color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // 狀態提示
        val stateText = when {
            isRecording -> "正在說話..."
            state == VoiceChatState.PROCESSING -> "識別中..."
            state == VoiceChatState.SENDING -> "發送中..."
            else -> "按住說話"
        }

        Text(
            text = stateText,
            style = MaterialTheme.typography.labelMedium,
            color = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 按住說話按鈕
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .scale(if (isRecording) scale * amplitudeScale else 1f)
                .clip(CircleShape)
                .background(buttonColor)
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onPress = {
                                onPress()
                                tryAwaitRelease()
                                onRelease()
                            }
                        )
                    }
                }
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "語音對講",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * 完整的語音對講面板
 */
@Composable
fun VoiceChatPanel(
    messages: List<VoiceChatMessage>,
    currentUserId: String,
    isRecording: Boolean,
    state: VoiceChatState,
    amplitude: Int,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 頂部：拖曳指示條 + 關閉按鈕
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 拖曳指示條（居中）
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        .align(Alignment.Center)
                )

                // 關閉按鈕（右上角）
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "關閉對講",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 標題
            Text(
                text = "語音對講",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 聊天記錄
            VoiceChatHistory(
                messages = messages,
                currentUserId = currentUserId
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 分隔線
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 32.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 語音按鈕
            VoiceChatButton(
                isRecording = isRecording,
                state = state,
                amplitude = amplitude,
                onPress = onStartRecording,
                onRelease = onStopRecording,
                enabled = enabled
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 迷你語音對講按鈕（用於訂單卡片內）
 */
@Composable
fun MiniVoiceChatButton(
    onClick: () -> Unit,
    hasUnread: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "語音對講",
                modifier = Modifier.size(24.dp)
            )
        }

        // 未讀標記
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}
