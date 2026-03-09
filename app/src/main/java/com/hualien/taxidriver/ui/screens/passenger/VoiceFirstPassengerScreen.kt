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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.data.remote.dto.VoiceChatState
import com.hualien.taxidriver.service.VoiceRecorderService
import com.hualien.taxidriver.ui.components.PickupMapSelector
import com.hualien.taxidriver.ui.components.VoiceChatPanel
import com.hualien.taxidriver.ui.components.VoiceWaveIndicator
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
 * - 語音為主要互動方式，觸控為備選
 * - 簡潔清晰的狀態顯示
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

    // 語音對講狀態
    val voiceChatHistory by viewModel.voiceChatHistory.collectAsState()
    val voiceChatState by viewModel.voiceChatState.collectAsState()
    val isVoiceChatRecording by viewModel.voiceChatRecording.collectAsState()
    val voiceChatAmplitude by viewModel.voiceChatAmplitude.collectAsState()
    val showVoiceChatPanel by viewModel.showVoiceChatPanel.collectAsState()
    val voiceChatUnreadCount by viewModel.voiceChatUnreadCount.collectAsState()

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

    // 訂單完成對話框狀態
    var showCompletedDialog by remember { mutableStateOf(false) }
    var completedOrder by remember { mutableStateOf<com.hualien.taxidriver.domain.model.Order?>(null) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var ratingSubmitted by remember { mutableStateOf(false) }

    // 檢查靜音並顯示提示（語音播放時顯示，播完後自動隱藏）
    LaunchedEffect(uiState.isSpeaking, isMuted) {
        if (uiState.isSpeaking && isMuted) {
            showMuteWarning = true
        } else if (!uiState.isSpeaking) {
            // 語音播完後，延遲一點再隱藏
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
        viewModel.connectWebSocket(passengerId) { hasActiveOrder ->
            // 只有在「確認沒有進行中訂單」時才播放歡迎語，避免重開 app 後狀態互撞
            if (hasActiveOrder == false) {
                viewModel.playWelcomeGreeting()
            } else {
                android.util.Log.d("VoiceFirstScreen", "跳過歡迎語：hasActiveOrder=$hasActiveOrder")
            }
        }
        viewModel.startLocationUpdates()
        viewModel.updateNearbyDrivers()
    }

    // 監聽訂單完成狀態
    LaunchedEffect(uiState.orderStatus) {
        if (uiState.orderStatus == OrderStatus.COMPLETED && !ratingSubmitted && !showCompletedDialog) {
            uiState.currentOrder?.let { order ->
                android.util.Log.d("VoiceFirstScreen", "訂單完成，顯示評價對話框: ${order.orderId}")
                completedOrder = order
                showCompletedDialog = true
            }
        }
    }

    // 狀態訊息
    val statusMessage = when {
        // 優先顯示語音回覆
        uiState.currentSpeechText != null -> uiState.currentSpeechText
        uiState.voiceRecordingStatus == VoiceRecordingStatus.RECORDING -> "我在聽..."
        uiState.voiceRecordingStatus == VoiceRecordingStatus.PROCESSING -> "讓我想想..."
        // 錯誤訊息優先級提高，讓用戶能看到錯誤
        uiState.error != null -> "抱歉，${uiState.error}"
        uiState.voiceAutoflowState == VoiceAutoflowState.SEARCHING_DESTINATION -> "正在幫您找地點..."
        uiState.voiceAutoflowState == VoiceAutoflowState.ASKING_TRAIN_TIME -> "趕火車是幾點的？"
        uiState.voiceAutoflowState == VoiceAutoflowState.CONFIRMING_DESTINATION -> {
            val destName = uiState.pendingDestinationDetails?.name ?: "這裡"
            val fareInfo = uiState.fareEstimate?.let { fare ->
                val distanceKm = String.format("%.1f", fare.distanceKm)
                if (fare.isNightTime) {
                    "\n距離約 $distanceKm 公里\n預估車資 ${fare.totalFare} 元（含夜間加成）"
                } else {
                    "\n距離約 $distanceKm 公里\n預估車資 ${fare.totalFare} 元"
                }
            } ?: ""
            "您是要去「$destName」嗎？$fareInfo"
        }
        uiState.voiceAutoflowState == VoiceAutoflowState.BOOKING ||
        uiState.orderStatus == OrderStatus.REQUESTING -> "正在為您叫車..."
        uiState.orderStatus == OrderStatus.WAITING -> "正在為您找司機..."
        uiState.orderStatus == OrderStatus.ACCEPTED ->
            "${uiState.currentOrder?.driverName ?: "司機"}已接單！\n正在前往中"
        uiState.orderStatus == OrderStatus.ARRIVED -> "司機已到達上車點！"
        uiState.orderStatus == OrderStatus.ON_TRIP -> "正在前往目的地..."
        uiState.orderStatus == OrderStatus.COMPLETED -> "到達目的地啦！\n感謝您的搭乘"
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
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        // 狀態圖示
                        val statusIcon = when {
                            uiState.voiceRecordingStatus == VoiceRecordingStatus.RECORDING -> Icons.Default.Mic
                            uiState.voiceRecordingStatus == VoiceRecordingStatus.PROCESSING -> Icons.Default.HourglassTop
                            uiState.voiceAutoflowState == VoiceAutoflowState.SEARCHING_DESTINATION -> Icons.Default.Search
                            uiState.voiceAutoflowState == VoiceAutoflowState.ASKING_TRAIN_TIME -> Icons.Default.Train
                            uiState.voiceAutoflowState == VoiceAutoflowState.BOOKING ||
                            uiState.orderStatus == OrderStatus.REQUESTING -> Icons.Default.HourglassTop
                            uiState.orderStatus == OrderStatus.WAITING -> Icons.Default.LocalTaxi
                            uiState.orderStatus == OrderStatus.ACCEPTED -> Icons.Default.CheckCircle
                            uiState.orderStatus in listOf(OrderStatus.ARRIVED, OrderStatus.ON_TRIP) -> Icons.Default.DirectionsCar
                            uiState.orderStatus == OrderStatus.COMPLETED -> Icons.Default.Done
                            uiState.error != null -> Icons.Default.Error
                            else -> Icons.Default.RecordVoiceOver
                        }

                        val iconColor = when {
                            uiState.voiceRecordingStatus == VoiceRecordingStatus.RECORDING -> HappyYellow
                            uiState.error != null -> SoftRed
                            uiState.orderStatus == OrderStatus.ACCEPTED -> SoftGreen
                            uiState.orderStatus == OrderStatus.COMPLETED -> SoftGreen
                            else -> WarmCoral
                        }

                        // 圖示（帶動畫）
                        val iconScale by animateFloatAsState(
                            targetValue = if (uiState.voiceRecordingStatus == VoiceRecordingStatus.RECORDING) 1.2f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "iconScale"
                        )

                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier
                                .size(80.dp)
                                .scale(iconScale)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 語音波形（錄音時顯示）
                        AnimatedVisibility(
                            visible = uiState.voiceRecordingStatus == VoiceRecordingStatus.RECORDING,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            VoiceWaveIndicator(
                                amplitude = uiState.voiceAmplitude,
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .width(150.dp),
                                color = HappyYellow
                            )
                        }

                        // 狀態文字
                        statusMessage?.let { message ->
                            Text(
                                text = message,
                                style = WarmTypography.headlineLarge,  // 改小字體避免爆版
                                color = WarmBrown,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                maxLines = 4,  // 限制行數
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )
                        }

                        // 訂單資訊卡片（有訂單時顯示）
                        val showOrderCard = uiState.currentOrder != null &&
                                uiState.orderStatus !in listOf(OrderStatus.IDLE, OrderStatus.COMPLETED, OrderStatus.CANCELLED)
                        android.util.Log.d("VoiceFirstScreen", "訂單卡片: 顯示=$showOrderCard, 訂單=${uiState.currentOrder?.orderId}, 狀態=${uiState.orderStatus}")

                        AnimatedVisibility(
                            visible = showOrderCard,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            OrderInfoCard(
                                order = uiState.currentOrder,
                                orderStatus = uiState.orderStatus,
                                onCancelClick = {
                                    android.util.Log.d("VoiceFirstScreen", "========== onCancelClick 被調用 ==========")
                                    android.util.Log.d("VoiceFirstScreen", "passengerId: $passengerId")
                                    viewModel.cancelOrder(passengerId)
                                },
                                onCallDriver = { phoneNumber ->
                                    // 撥打電話
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:$phoneNumber")
                                    }
                                    context.startActivity(intent)
                                },
                                // 語音對講
                                canUseVoiceChat = viewModel.canUseVoiceChat(),
                                voiceChatUnreadCount = voiceChatUnreadCount,
                                onVoiceChatClick = { viewModel.showVoiceChatPanel() },
                                isLoading = uiState.isLoading,
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
                                text = "請開啟聲音\n才能聽到語音回覆",
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

            // 語音對講面板
            if (showVoiceChatPanel) {
                VoiceChatPanel(
                    messages = voiceChatHistory,
                    currentUserId = passengerId,
                    isRecording = isVoiceChatRecording,
                    state = voiceChatState,
                    amplitude = voiceChatAmplitude,
                    onStartRecording = { viewModel.startVoiceChatRecording() },
                    onStopRecording = { viewModel.stopVoiceChatRecording() },
                    onClose = { viewModel.hideVoiceChatPanel() },
                    enabled = viewModel.canUseVoiceChat(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
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

    // 訂單完成對話框（簡化版，適合語音模式）
    if (showCompletedDialog && completedOrder != null) {
        AlertDialog(
            onDismissRequest = { /* 不允許點擊外部關閉 */ },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = null,
                        tint = SoftGreen,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "行程已完成",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 車資信息
                    completedOrder?.fare?.let { fare ->
                        Text(
                            "車資 NT$ ${fare.meterAmount}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SoftGreen
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        "感謝您的搭乘！",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                // 評價司機按鈕
                if (!ratingSubmitted && completedOrder?.driverId != null) {
                    Button(
                        onClick = { showRatingDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmCoral
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("評價司機", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCompletedDialog = false
                        completedOrder = null
                        ratingSubmitted = false
                        viewModel.clearOrder()
                    }
                ) {
                    Text(if (ratingSubmitted) "繼續叫車" else "稍後評價")
                }
            }
        )
    }

    // 司機評價對話框
    if (showRatingDialog && completedOrder != null) {
        // 保存訂單資訊（避免在回調中使用可能已被清空的 completedOrder）
        val orderIdToRate = completedOrder?.orderId ?: ""
        val driverIdToRate = completedOrder?.driverId ?: ""
        val driverNameToRate = completedOrder?.driverName ?: "司機"

        com.hualien.taxidriver.ui.components.RatingDialog(
            title = "評價司機",
            targetName = driverNameToRate,
            isDriver = true,
            onDismiss = { showRatingDialog = false },
            onSubmit = { rating, comment ->
                viewModel.submitDriverRating(
                    passengerId = passengerId,
                    orderId = orderIdToRate,
                    driverId = driverIdToRate,
                    rating = rating,
                    comment = comment,
                    onSuccess = {
                        android.util.Log.d("VoiceFirstScreen", "評價成功，關閉所有對話框")
                        // 重要：先設置本地狀態（同步），再清除 ViewModel 訂單
                        // 這樣即使 clearOrder() 觸發重組，本地狀態已經是正確的
                        ratingSubmitted = true
                        showRatingDialog = false
                        showCompletedDialog = false
                        completedOrder = null
                        viewModel.clearOrder()
                    },
                    onError = { errorMessage ->
                        android.util.Log.e("VoiceFirstScreen", "評價失敗: $errorMessage")
                        showRatingDialog = false
                    }
                )
            }
        )
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

            // 正在叫車（BOOKING 狀態）
            voiceAutoflowState == VoiceAutoflowState.BOOKING -> {
                // 叫車中不顯示語音按鈕，顯示載入提示
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = WarmCoral,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在為您叫車...",
                    style = WarmTypography.bodyLarge,
                    color = WarmBrown
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
            label = "算了",
            containerColor = Color.White,
            contentColor = SoftRed,
            onClick = onReject
        )

        // 確認按鈕
        LargeActionButton(
            icon = Icons.Default.Check,
            label = "好",
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
    canUseVoiceChat: Boolean = false,
    voiceChatUnreadCount: Int = 0,
    onVoiceChatClick: () -> Unit = {},
    isLoading: Boolean = false,
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
                // 司機名字和頭像
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 司機頭像
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(WarmCoral.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = WarmCoral,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 司機名字
                    Text(
                        text = order.driverName,
                        style = WarmTypography.headlineLarge,
                        color = WarmBrown,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // 操作按鈕區（對講 + 撥號）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 語音對講按鈕
                        if (canUseVoiceChat) {
                            IconButton(
                                onClick = onVoiceChatClick,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (voiceChatUnreadCount > 0) WarmCoral else WarmBrown.copy(alpha = 0.8f),
                                        CircleShape
                                    )
                            ) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "語音對講",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    // 未讀標記
                                    if (voiceChatUnreadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .clip(CircleShape)
                                                .background(Color.Red)
                                        )
                                    }
                                }
                            }
                        }

                        // 撥打電話按鈕
                        order.driverPhone?.let { phone ->
                            IconButton(
                                onClick = { onCallDriver(phone) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(SoftGreen, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "撥打電話",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
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
                                color = WarmBrown,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
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
                                color = WarmBrown,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 取消按鈕（等待接單時顯示）
            android.util.Log.d("OrderInfoCard", "當前訂單狀態: $orderStatus, 是否顯示取消按鈕: ${orderStatus == OrderStatus.WAITING}, isLoading: $isLoading")
            if (orderStatus == OrderStatus.WAITING) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!isLoading) {
                            android.util.Log.d("OrderInfoCard", "========== 取消按鈕被點擊 ==========")
                            onCancelClick()
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SoftRed.copy(alpha = 0.1f),
                        contentColor = SoftRed,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
                        disabledContentColor = Color.Gray
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SoftRed,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "取消中...",
                            style = WarmTypography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "取消叫車",
                            style = WarmTypography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
