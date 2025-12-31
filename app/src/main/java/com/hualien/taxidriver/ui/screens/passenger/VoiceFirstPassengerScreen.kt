package com.hualien.taxidriver.ui.screens.passenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.service.VoiceRecorderService
import com.hualien.taxidriver.ui.components.CharacterState
import com.hualien.taxidriver.ui.components.PickupMapSelector
import com.hualien.taxidriver.ui.components.VoiceWaveIndicator
import com.hualien.taxidriver.ui.components.XiaoJuCharacter
import com.hualien.taxidriver.ui.theme.*
import com.hualien.taxidriver.viewmodel.OrderStatus
import com.hualien.taxidriver.viewmodel.PassengerViewModel
import com.hualien.taxidriver.viewmodel.VoiceAutoflowState
import com.hualien.taxidriver.viewmodel.VoiceRecordingStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 語音優先乘客介面
 *
 * 設計理念：
 * - 以「小橘」角色為中心的對話式介面
 * - 語音為主要互動方式，觸控為備選
 * - 打破傳統表單式 UI，強調情感連結
 * - 適合老年人使用：大字體、高對比、簡化流程
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceFirstPassengerScreen(
    passengerId: String,
    passengerName: String,
    passengerPhone: String,
    viewModel: PassengerViewModel = viewModel(),  // 可從外部傳入共享的 ViewModel
    onNavigateToMap: () -> Unit = {},      // 切換到地圖模式（傳統介面）
    onNavigateToSettings: () -> Unit = {}   // 設置頁面
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // 權限處理
    var hasRecordPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasRecordPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        android.util.Log.d("VoiceFirstScreen", "權限結果: 錄音=$hasRecordPermission, 定位=$hasLocationPermission")
    }

    // 靜音檢測
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val isMuted = remember(uiState.isSpeaking) {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume == 0 || audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }
    var showMuteWarning by remember { mutableStateOf(false) }

    // 首次使用引導（使用 SharedPreferences 記錄是否已看過）
    val sharedPrefs = context.getSharedPreferences("voice_first_prefs", Context.MODE_PRIVATE)
    var showFirstTimeGuide by remember {
        mutableStateOf(!sharedPrefs.getBoolean("has_seen_guide", false))
    }

    // 檢查靜音並顯示提示（只在小橘說話時顯示，說完後自動隱藏）
    LaunchedEffect(uiState.isSpeaking, isMuted) {
        if (uiState.isSpeaking && isMuted) {
            showMuteWarning = true
        } else if (!uiState.isSpeaking) {
            // 小橘說完話後，延遲一點再隱藏
            kotlinx.coroutines.delay(2000)
            showMuteWarning = false
        }
    }

    // 初始化
    LaunchedEffect(Unit) {
        android.util.Log.d("VoiceFirstScreen", "========== VoiceFirstPassengerScreen 初始化 ==========")
        android.util.Log.d("VoiceFirstScreen", "乘客ID: $passengerId")
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        viewModel.setPassengerId(passengerId)
        viewModel.connectWebSocket(passengerId)
        viewModel.startLocationUpdates()
        viewModel.updateNearbyDrivers()
    }

    // 計算角色狀態
    val characterState = when {
        uiState.voiceRecordingStatus == VoiceRecordingStatus.RECORDING -> CharacterState.LISTENING
        uiState.voiceRecordingStatus == VoiceRecordingStatus.PROCESSING -> CharacterState.THINKING
        uiState.voiceAutoflowState == VoiceAutoflowState.SEARCHING_DESTINATION -> CharacterState.THINKING
        uiState.voiceAutoflowState == VoiceAutoflowState.CONFIRMING_DESTINATION -> CharacterState.SPEAKING
        uiState.orderStatus == OrderStatus.WAITING -> CharacterState.WAITING
        uiState.orderStatus == OrderStatus.ACCEPTED -> CharacterState.HAPPY
        uiState.orderStatus in listOf(OrderStatus.ARRIVED, OrderStatus.ON_TRIP) -> CharacterState.HAPPY
        uiState.orderStatus == OrderStatus.COMPLETED -> CharacterState.HAPPY
        uiState.orderStatus == OrderStatus.CANCELLED -> CharacterState.SAD
        uiState.error != null -> CharacterState.SAD
        else -> CharacterState.IDLE
    }

    // 計算角色訊息（優先顯示對話泡泡內容）
    val characterMessage = when {
        // 如果小橘正在說話，優先顯示對話泡泡
        uiState.currentSpeechText != null -> uiState.currentSpeechText
        uiState.voiceRecordingStatus == VoiceRecordingStatus.RECORDING -> "我在聽..."
        uiState.voiceRecordingStatus == VoiceRecordingStatus.PROCESSING -> "讓我想想..."
        uiState.voiceAutoflowState == VoiceAutoflowState.SEARCHING_DESTINATION -> "正在幫您找地點..."
        uiState.voiceAutoflowState == VoiceAutoflowState.CONFIRMING_DESTINATION ->
            "您是要去「${uiState.pendingDestinationDetails?.name ?: "這裡"}」嗎？"
        uiState.orderStatus == OrderStatus.WAITING -> "正在為您找司機..."
        uiState.orderStatus == OrderStatus.ACCEPTED ->
            "${uiState.currentOrder?.driverName ?: "司機"}已接單！\n正在前往中"
        uiState.orderStatus == OrderStatus.ARRIVED -> "司機已到達上車點！"
        uiState.orderStatus == OrderStatus.ON_TRIP -> "正在前往目的地..."
        uiState.orderStatus == OrderStatus.COMPLETED -> "到達目的地啦！\n感謝您的搭乘"
        uiState.error != null -> "抱歉，${uiState.error}"
        uiState.currentOrder == null -> "您好！\n說出您想去的地方吧"
        else -> null
    }

    // 主題背景
    WarmCompanionTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            CreamWhite,
                            CreamDark
                        )
                    )
                )
        ) {
            // 主要內容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 頂部工具列
                TopBar(
                    onMapClick = onNavigateToMap,
                    onSettingsClick = onNavigateToSettings,
                    nearbyDriverCount = uiState.nearbyDrivers.size
                )

                // 中央區域：角色 + 訊息
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 小橘角色
                        XiaoJuCharacter(
                            state = characterState,
                            size = 220.dp,
                            message = characterMessage,
                            amplitude = uiState.voiceAmplitude
                        )

                        // 語音波形（錄音時顯示）
                        AnimatedVisibility(
                            visible = uiState.voiceRecordingStatus == VoiceRecordingStatus.RECORDING,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            VoiceWaveIndicator(
                                amplitude = uiState.voiceAmplitude,
                                modifier = Modifier
                                    .padding(top = 24.dp)
                                    .width(150.dp),
                                color = HappyYellow
                            )
                        }

                        // 訂單資訊卡片（有訂單時顯示）
                        AnimatedVisibility(
                            visible = uiState.currentOrder != null &&
                                    uiState.orderStatus !in listOf(OrderStatus.IDLE, OrderStatus.COMPLETED, OrderStatus.CANCELLED),
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            OrderInfoCard(
                                order = uiState.currentOrder,
                                orderStatus = uiState.orderStatus,
                                onCancelClick = { viewModel.cancelOrder(passengerId) },
                                onCallDriver = { phoneNumber ->
                                    // 撥打電話
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:$phoneNumber")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)
                            )
                        }
                    }
                }

                // 底部操作區
                BottomActionArea(
                    voiceRecordingStatus = uiState.voiceRecordingStatus,
                    voiceAutoflowState = uiState.voiceAutoflowState,
                    orderStatus = uiState.orderStatus,
                    hasRecordPermission = hasRecordPermission,
                    onStartRecording = { viewModel.startVoiceRecording() },
                    onStopRecording = { viewModel.stopVoiceRecording() },
                    onConfirmDestination = { viewModel.confirmDestination() },
                    onRejectDestination = { viewModel.rejectDestination() },
                    onQuickBook = {
                        // 使用當前位置快速叫車
                        uiState.currentLocation?.let { location ->
                            viewModel.setPickupLocation(location, "我的位置")
                            viewModel.requestTaxi(passengerId, passengerName, passengerPhone)
                        }
                    },
                    hasCurrentLocation = uiState.currentLocation != null,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            // 上車點選擇器（全屏覆蓋）
            if (uiState.showPickupMapSelector) {
                PickupMapSelector(
                    initialLocation = uiState.currentLocation,
                    destinationName = uiState.destinationAddress,
                    onPickupConfirmed = { latLng, address ->
                        viewModel.confirmPickupAndBook(
                            pickupLatLng = latLng,
                            pickupAddress = address,
                            passengerId = passengerId,
                            passengerName = passengerName,
                            passengerPhone = passengerPhone
                        )
                    },
                    onCancel = { viewModel.cancelVoiceAutoflow() }
                )
            }

            // 靜音提醒（點擊可關閉）
            AnimatedVisibility(
                visible = showMuteWarning,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Surface(
                    color = Color(0xFFFF9800),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp,
                    onClick = { showMuteWarning = false }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "請開啟聲音，\n小橘才能和您說話喔！",
                                style = WarmTypography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "點擊關閉",
                            style = WarmTypography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 首次使用引導
            AnimatedVisibility(
                visible = showFirstTimeGuide && uiState.currentOrder == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable {
                            // 關閉引導並記錄
                            showFirstTimeGuide = false
                            sharedPrefs.edit().putBoolean("has_seen_guide", true).apply()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        // 手指按住圖示
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(80.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "按住下方按鈕說話",
                            style = WarmTypography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "例如：「我要去火車站」",
                            style = WarmTypography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Surface(
                            color = WarmCoral,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = "點擊任意處開始",
                                style = WarmTypography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 清理資源
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationUpdates()
        }
    }
}

// ==================== 頂部工具列 ====================

@Composable
private fun TopBar(
    onMapClick: () -> Unit,
    onSettingsClick: () -> Unit,
    nearbyDriverCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 切換到地圖模式
        IconButton(
            onClick = {
                android.util.Log.d("VoiceFirstScreen", "點擊地圖按鈕 - 準備導航到地圖模式")
                onMapClick()
            },
            modifier = Modifier
                .size(56.dp)
                .background(Color.White.copy(alpha = 0.9f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Outlined.Map,
                contentDescription = "地圖模式",
                tint = WarmBrown,
                modifier = Modifier.size(28.dp)
            )
        }

        // 附近司機數量
        if (nearbyDriverCount > 0) {
            Surface(
                color = SoftGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalTaxi,
                        contentDescription = null,
                        tint = SoftGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$nearbyDriverCount 位司機在線",
                        style = WarmTypography.bodyMedium,
                        color = SoftGreenDark,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 設置按鈕
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(56.dp)
                .background(Color.White.copy(alpha = 0.9f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "設置",
                tint = WarmBrown,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ==================== 底部操作區 ====================

@Composable
private fun BottomActionArea(
    voiceRecordingStatus: VoiceRecordingStatus,
    voiceAutoflowState: VoiceAutoflowState,
    orderStatus: OrderStatus,
    hasRecordPermission: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onConfirmDestination: () -> Unit,
    onRejectDestination: () -> Unit,
    onQuickBook: () -> Unit,
    hasCurrentLocation: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            // 等待確認目的地
            voiceAutoflowState == VoiceAutoflowState.CONFIRMING_DESTINATION -> {
                ConfirmationButtons(
                    onConfirm = onConfirmDestination,
                    onReject = onRejectDestination
                )
            }

            // 有訂單進行中
            orderStatus !in listOf(OrderStatus.IDLE, OrderStatus.COMPLETED, OrderStatus.CANCELLED) -> {
                // 訂單進行中不顯示語音按鈕
            }

            // 正常狀態：語音按鈕
            else -> {
                VoiceButton(
                    isRecording = voiceRecordingStatus == VoiceRecordingStatus.RECORDING,
                    isProcessing = voiceRecordingStatus == VoiceRecordingStatus.PROCESSING,
                    hasPermission = hasRecordPermission,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording
                )

                // 快速叫車按鈕（不說話時的備選）
                if (voiceRecordingStatus == VoiceRecordingStatus.IDLE && hasCurrentLocation) {
                    QuickBookButton(onClick = onQuickBook)
                }

                // 提示文字
                Text(
                    text = if (voiceRecordingStatus == VoiceRecordingStatus.IDLE) {
                        "按住說話，或點擊上方按鈕"
                    } else if (voiceRecordingStatus == VoiceRecordingStatus.RECORDING) {
                        "鬆開發送"
                    } else {
                        "處理中..."
                    },
                    style = WarmTypography.bodyMedium,
                    color = WarmBrownMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==================== 語音按鈕 ====================

@Composable
private fun VoiceButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    hasPermission: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    // 使用 rememberUpdatedState 確保回調總是最新的
    val currentOnStartRecording by rememberUpdatedState(onStartRecording)
    val currentOnStopRecording by rememberUpdatedState(onStopRecording)
    val currentHasPermission by rememberUpdatedState(hasPermission)
    val currentIsProcessing by rememberUpdatedState(isProcessing)

    // 追蹤按壓狀態
    var isPressed by remember { mutableStateOf(false) }

    // 動畫
    val scale by animateFloatAsState(
        targetValue = when {
            isRecording -> 1.2f
            isPressed -> 0.95f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isRecording -> HappyYellow
            isProcessing -> Color(0xFF64B5F6)
            else -> WarmCoral
        },
        label = "color"
    )

    // 脈動動畫
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        // 脈動光暈（錄音時）
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(WarmDimensions.voiceButtonSize.dp * pulseScale * 1.3f)
                    .clip(CircleShape)
                    .background(HappyYellow.copy(alpha = 0.3f))
            )
        }

        // 主按鈕
        Box(
            modifier = Modifier
                .size(WarmDimensions.voiceButtonSize.dp)
                .scale(scale)
                .shadow(
                    elevation = if (isRecording) 16.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = backgroundColor.copy(alpha = 0.4f),
                    spotColor = backgroundColor.copy(alpha = 0.4f)
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = 0.8f)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            android.util.Log.d("VoiceButton", "onPress 觸發, hasPermission=$currentHasPermission, isProcessing=$currentIsProcessing")

                            if (currentHasPermission && !currentIsProcessing) {
                                isPressed = true
                                android.util.Log.d("VoiceButton", "開始錄音...")
                                currentOnStartRecording()

                                val released = tryAwaitRelease()
                                android.util.Log.d("VoiceButton", "鬆開按鈕, released=$released")

                                isPressed = false
                                currentOnStopRecording()
                                android.util.Log.d("VoiceButton", "停止錄音")
                            } else {
                                android.util.Log.w("VoiceButton", "無法開始錄音: 權限=$currentHasPermission, 處理中=$currentIsProcessing")
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White,
                    strokeWidth = 4.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "語音輸入",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

// ==================== 確認按鈕組 ====================

@Composable
private fun ConfirmationButtons(
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 拒絕按鈕
        LargeActionButton(
            icon = Icons.Default.Close,
            label = "不對",
            containerColor = Color.White,
            contentColor = SoftRed,
            onClick = onReject
        )

        // 確認按鈕
        LargeActionButton(
            icon = Icons.Default.Check,
            label = "對",
            containerColor = SoftGreen,
            contentColor = Color.White,
            onClick = onConfirm
        )
    }
}

// ==================== 快速叫車按鈕 ====================

@Composable
private fun QuickBookButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = WarmCoral,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "在這裡叫車",
                style = WarmTypography.bodyLarge,
                color = WarmBrown,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==================== 大型操作按鈕 ====================

@Composable
private fun LargeActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(100.dp),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = WarmTypography.labelLarge.copy(fontSize = 20.sp),
                color = contentColor
            )
        }
    }
}

// ==================== 訂單資訊卡片 ====================

@Composable
private fun OrderInfoCard(
    order: com.hualien.taxidriver.domain.model.Order?,
    orderStatus: OrderStatus,
    onCancelClick: () -> Unit,
    onCallDriver: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    order ?: return

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 司機資訊（已接單後顯示）
            if (orderStatus != OrderStatus.WAITING && order.driverName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 司機頭像
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(WarmCoral.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = WarmCoral,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = order.driverName,
                            style = WarmTypography.headlineLarge.copy(fontSize = 22.sp),
                            color = WarmBrown
                        )
                        order.driverPhone?.let { phone ->
                            Text(
                                text = phone,
                                style = WarmTypography.bodyMedium,
                                color = WarmBrownMuted
                            )
                        }
                    }

                    // 撥打電話按鈕
                    order.driverPhone?.let { phone ->
                        IconButton(
                            onClick = { onCallDriver(phone) },
                            modifier = Modifier
                                .size(48.dp)
                                .background(SoftGreen, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "撥打電話",
                                tint = Color.White
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = DividerColor
                )
            }

            // 地點資訊
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 上車點
                order.pickup?.let { pickup ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.TripOrigin,
                            contentDescription = null,
                            tint = SoftGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "上車點",
                                style = WarmTypography.bodySmall,
                                color = WarmBrownMuted
                            )
                            Text(
                                text = pickup.address ?: "未知地址",
                                style = WarmTypography.bodyMedium,
                                color = WarmBrown
                            )
                        }
                    }
                }

                // 目的地
                order.destination?.let { dest ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = WarmCoral,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "目的地",
                                style = WarmTypography.bodySmall,
                                color = WarmBrownMuted
                            )
                            Text(
                                text = dest.address ?: "未知地址",
                                style = WarmTypography.bodyMedium,
                                color = WarmBrown
                            )
                        }
                    }
                }
            }

            // 取消按鈕（等待接單時顯示）
            if (orderStatus == OrderStatus.WAITING) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onCancelClick,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "取消叫車",
                        style = WarmTypography.bodyMedium,
                        color = SoftRed
                    )
                }
            }
        }
    }
}
