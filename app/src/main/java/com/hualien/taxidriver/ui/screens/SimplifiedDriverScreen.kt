package com.hualien.taxidriver.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.*
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.manager.NextAction
import com.hualien.taxidriver.manager.SmartOrderManager
import com.hualien.taxidriver.manager.SmartOrderState
import com.hualien.taxidriver.data.remote.dto.VoiceAction
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.domain.model.OrderStatus
import com.hualien.taxidriver.service.VoiceRecorderService
import com.hualien.taxidriver.ui.components.FareDialog
import com.hualien.taxidriver.ui.components.RatingDialog
import com.hualien.taxidriver.ui.components.VoiceCommandButton
import com.hualien.taxidriver.utils.VoiceAssistant
import com.hualien.taxidriver.utils.VoiceCommandHandler
import com.hualien.taxidriver.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 極簡化司機介面
 * 特點：
 * 1. 單一大按鈕操作
 * 2. GPS自動偵測狀態
 * 3. 語音提示
 * 4. 智能狀態轉換
 */
@Composable
fun SimplifiedDriverScreen(
    driverId: String,
    driverName: String,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 智能訂單管理器
    val smartOrderManager = remember { SmartOrderManager() }
    val orderState by smartOrderManager.orderState.collectAsState()

    // 語音助理（TTS 播報）
    val voiceAssistant = remember { VoiceAssistant(context) }

    // 語音錄製服務
    val voiceRecorderService = remember { VoiceRecorderService(context) }
    val recordingState by voiceRecorderService.state.collectAsState()
    val amplitude by voiceRecorderService.amplitude.collectAsState()

    // 語音指令處理器
    val voiceCommandHandler = remember { VoiceCommandHandler(context, voiceAssistant) }

    // UI狀態
    val uiState by viewModel.uiState.collectAsState()
    var showFareDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // 位置服務
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // 位置權限
    var hasLocationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // 動畫狀態
    val pulseAnimation = rememberInfiniteTransition()
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // 建立 WebSocket 連接（司機上線）
    // 使用 driverId 作為 key，確保只在 driverId 變化時才重新連接
    LaunchedEffect(driverId) {
        viewModel.connectWebSocket(driverId)
    }

    // 清理資源
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnectWebSocket()
            voiceRecorderService.release()
        }
    }

    // 設置語音指令處理器回調
    LaunchedEffect(Unit) {
        voiceCommandHandler.setCallback(object : VoiceCommandHandler.CommandCallback {
            override fun onAcceptOrder(orderId: String?) {
                (orderState as? SmartOrderState.WaitingForAccept)?.let { state ->
                    viewModel.acceptOrder(state.order.orderId, driverId, driverName)
                    smartOrderManager.executeNextAction()
                }
            }

            override fun onRejectOrder(orderId: String?, reason: String?) {
                (orderState as? SmartOrderState.WaitingForAccept)?.let { state ->
                    viewModel.rejectOrder(state.order.orderId, driverId, reason ?: "司機不便接單")
                    smartOrderManager.reset()
                }
            }

            override fun onMarkArrived(orderId: String?) {
                (orderState as? SmartOrderState.NavigatingToPickup)?.let { state ->
                    viewModel.markArrived(state.order.orderId)
                    smartOrderManager.executeNextAction()
                }
            }

            override fun onStartTrip(orderId: String?) {
                (orderState as? SmartOrderState.ArrivedAtPickup)?.let { state ->
                    viewModel.startTrip(state.order.orderId)
                    smartOrderManager.executeNextAction()
                }
            }

            override fun onEndTrip(orderId: String?) {
                (orderState as? SmartOrderState.OnTrip)?.let { state ->
                    viewModel.endTrip(state.order.orderId)
                    smartOrderManager.executeNextAction()
                }
            }

            override fun onUpdateStatus(status: DriverAvailability) {
                viewModel.updateDriverStatus(driverId, status)
            }

            override fun onQueryEarnings() {
                voiceAssistant.speak("收入查詢功能開發中")
            }

            override fun onNavigate(destination: String?) {
                val currentOrder = uiState.currentOrder
                val navDest = destination ?: when (currentOrder?.status) {
                    OrderStatus.ACCEPTED, OrderStatus.ARRIVED -> currentOrder.pickup.address
                    OrderStatus.ON_TRIP -> currentOrder.destination?.address
                    else -> null
                }
                if (navDest != null) {
                    voiceAssistant.speak("正在開啟導航")
                    // TODO: 實際開啟導航
                } else {
                    voiceAssistant.speak("沒有可導航的目的地")
                }
            }

            override fun onEmergency() {
                voiceAssistant.speak("緊急求助功能開發中")
            }

            override fun getCurrentOrder(): Order? = uiState.currentOrder

            override fun getCurrentStatus(): DriverAvailability = uiState.driverStatus
        })
    }

    // 處理語音指令結果
    LaunchedEffect(uiState.lastVoiceCommand) {
        uiState.lastVoiceCommand?.let { command ->
            voiceRecorderService.resetState()
            val result = voiceCommandHandler.handleCommand(command)
            viewModel.clearVoiceCommand()
        }
    }

    // 處理語音錯誤
    LaunchedEffect(uiState.voiceError) {
        uiState.voiceError?.let { error ->
            voiceRecorderService.resetState()
            voiceAssistant.speak(error)
            viewModel.clearVoiceError()
        }
    }

    // 啟動位置追蹤
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            startLocationTracking(fusedLocationClient, smartOrderManager)
        }
    }

    // 處理訂單更新
    LaunchedEffect(uiState.currentOrder) {
        uiState.currentOrder?.let { order ->
            smartOrderManager.setOrder(order)

            // 語音提示新訂單
            when (order.status) {
                com.hualien.taxidriver.domain.model.OrderStatus.OFFERED -> {
                    voiceAssistant.speak("新訂單，前往${order.pickup.address}")
                }
                else -> {}
            }
        }
    }

    // 處理狀態變化
    LaunchedEffect(orderState) {
        val currentState = orderState
        when (currentState) {
            is SmartOrderState.NavigatingToPickup -> {
                if (currentState.nearPickup) {
                    voiceAssistant.speak("即將到達上車點")
                }
            }
            is SmartOrderState.ArrivedAtPickup -> {
                voiceAssistant.speak("已到達上車點，等待乘客上車")
            }
            is SmartOrderState.OnTrip -> {
                if (currentState.nearDestination) {
                    voiceAssistant.speak("即將到達目的地")
                }
            }
            is SmartOrderState.WaitingForPayment -> {
                voiceAssistant.speak("行程結束，請收取車資")
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 頂部：狀態顯示
            StatusDisplay(
                orderState = orderState,
                driverName = driverName,
                driverStatus = uiState.driverStatus
            )

            // 中間：智能大按鈕
            SmartActionButton(
                orderState = orderState,
                isProcessing = isProcessing,
                pulseScale = pulseScale,
                onClick = {
                    scope.launch {
                        isProcessing = true

                        // 執行下一步操作
                        val nextAction = smartOrderManager.executeNextAction()
                        handleNextAction(
                            nextAction = nextAction,
                            viewModel = viewModel,
                            orderState = orderState,
                            voiceAssistant = voiceAssistant,
                            onShowFareDialog = { showFareDialog = true }
                        )

                        delay(1000)
                        isProcessing = false
                    }
                }
            )

            // 底部：輔助資訊
            AssistantInfo(orderState = orderState)
        }

        // 自動提示浮層
        AnimatedVisibility(
            visible = shouldShowAutoHint(orderState),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            AutoHintBanner(orderState = orderState)
        }

        // 語音指令按鈕（右下角）
        VoiceCommandButton(
            recordingState = recordingState,
            amplitude = amplitude,
            onStartRecording = {
                voiceRecorderService.startRecording { audioFile ->
                    // 錄音完成，上傳到 server
                    viewModel.transcribeAudio(audioFile, driverId)
                }
            },
            onStopRecording = {
                voiceRecorderService.stopRecording()
            },
            onCancelRecording = {
                voiceRecorderService.cancelRecording()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            enabled = uiState.driverStatus != DriverAvailability.OFFLINE
        )
    }

    // 車資對話框
    if (showFareDialog) {
        FareDialog(
            onDismiss = { showFareDialog = false },
            onConfirm = { meterAmount, photoUri ->
                (orderState as? SmartOrderState.WaitingForPayment)?.let { state ->
                    // TODO: 未來實作照片上傳功能
                    viewModel.submitFare(state.order.orderId, driverId, meterAmount)
                    smartOrderManager.completeOrder()
                }
                showFareDialog = false
            }
        )
    }

    // 評分對話框 - 訂單完成後顯示
    uiState.pendingRating?.let { pendingRating ->
        RatingDialog(
            title = "評價乘客",
            targetName = pendingRating.passengerName,
            isDriver = false,
            onDismiss = { viewModel.skipRating() },
            onSubmit = { rating, comment ->
                viewModel.submitRating(driverId, rating, comment)
            }
        )
    }
}

/**
 * 狀態顯示區
 */
@Composable
fun StatusDisplay(
    orderState: SmartOrderState,
    driverName: String,
    driverStatus: DriverAvailability
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (orderState) {
                is SmartOrderState.NoOrder -> Color(0xFFE0E0E0)
                is SmartOrderState.WaitingForAccept -> Color(0xFFFFF9C4)
                is SmartOrderState.NavigatingToPickup -> Color(0xFFE3F2FD)
                is SmartOrderState.ArrivedAtPickup -> Color(0xFFE8F5E9)
                is SmartOrderState.OnTrip -> Color(0xFFBBDEFB)
                is SmartOrderState.WaitingForPayment -> Color(0xFFFFE0B2)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 狀態圖標
            Icon(
                imageVector = when (orderState) {
                    is SmartOrderState.NoOrder -> Icons.Default.Person
                    is SmartOrderState.WaitingForAccept -> Icons.Default.Notifications
                    is SmartOrderState.NavigatingToPickup -> Icons.Default.LocationOn
                    is SmartOrderState.ArrivedAtPickup -> Icons.Default.CheckCircle
                    is SmartOrderState.OnTrip -> Icons.Default.PlayArrow
                    is SmartOrderState.WaitingForPayment -> Icons.Default.DateRange
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 主要狀態文字
            Text(
                text = when (orderState) {
                    is SmartOrderState.NoOrder -> "待命中"
                    is SmartOrderState.WaitingForAccept -> "新訂單"
                    is SmartOrderState.NavigatingToPickup -> {
                        if (orderState.nearPickup) "即將到達" else "前往上車點"
                    }
                    is SmartOrderState.ArrivedAtPickup -> "等待乘客"
                    is SmartOrderState.OnTrip -> {
                        if (orderState.nearDestination) "即將到達目的地" else "載客中"
                    }
                    is SmartOrderState.WaitingForPayment -> "收費中"
                },
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // 輔助資訊
            when (orderState) {
                is SmartOrderState.NavigatingToPickup -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "距離: ${formatDistance(orderState.distanceToPickup)}",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (orderState.nearPickup) {
                        Text(
                            text = "✓ GPS偵測已接近",
                            fontSize = 20.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                is SmartOrderState.OnTrip -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    orderState.order.destination?.let {
                        Text(
                            text = "前往: ${it.address}",
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (orderState.nearDestination) {
                        Text(
                            text = "✓ GPS偵測即將到達",
                            fontSize = 20.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                is SmartOrderState.WaitingForAccept -> {
                    // 顯示距離和時間資訊
                    Spacer(modifier = Modifier.height(8.dp))

                    // 上車點地址
                    Text(
                        text = "📍 ${orderState.order.pickup.address ?: "未知地址"}",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 距離和時間資訊（一行顯示）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 到客人距離
                        orderState.order.distanceToPickup?.let { distance ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "🚗 到客人",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${distance} km",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                orderState.order.etaToPickup?.let { eta ->
                                    Text(
                                        text = "約 $eta 分鐘",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 行程距離（如果有目的地）
                        orderState.order.tripDistance?.let { distance ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "📍 行程",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${distance} km",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                orderState.order.estimatedTripDuration?.let { duration ->
                                    Text(
                                        text = "約 $duration 分鐘",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = driverName,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 智能操作按鈕
 */
@Composable
fun SmartActionButton(
    orderState: SmartOrderState,
    isProcessing: Boolean,
    pulseScale: Float,
    onClick: () -> Unit
) {
    val buttonColor = when (orderState) {
        is SmartOrderState.NoOrder -> Color(0xFF9E9E9E)
        is SmartOrderState.WaitingForAccept -> Color(0xFF4CAF50)
        is SmartOrderState.NavigatingToPickup -> {
            if (orderState.nearPickup) Color(0xFF2196F3) else Color(0xFF1976D2)
        }
        is SmartOrderState.ArrivedAtPickup -> Color(0xFF4CAF50)
        is SmartOrderState.OnTrip -> {
            if (orderState.nearDestination) Color(0xFFFF9800) else Color(0xFF9C27B0)
        }
        is SmartOrderState.WaitingForPayment -> Color(0xFFFF5722)
    }

    val buttonText = when (orderState) {
        is SmartOrderState.NoOrder -> "等待訂單"
        is SmartOrderState.WaitingForAccept -> "接受訂單"
        is SmartOrderState.NavigatingToPickup -> {
            if (orderState.nearPickup) "確認到達" else "導航中"
        }
        is SmartOrderState.ArrivedAtPickup -> "開始載客"
        is SmartOrderState.OnTrip -> {
            if (orderState.nearDestination) "結束行程" else "載客中"
        }
        is SmartOrderState.WaitingForPayment -> "收取車資"
    }

    val buttonIcon = when (orderState) {
        is SmartOrderState.NoOrder -> Icons.Default.Face
        is SmartOrderState.WaitingForAccept -> Icons.Default.CheckCircle
        is SmartOrderState.NavigatingToPickup -> Icons.Default.LocationOn
        is SmartOrderState.ArrivedAtPickup -> Icons.Default.PlayArrow
        is SmartOrderState.OnTrip -> Icons.Default.Star
        is SmartOrderState.WaitingForPayment -> Icons.Default.DateRange
    }

    Box(
        contentAlignment = Alignment.Center
    ) {
        // 背景脈動效果
        if (orderState !is SmartOrderState.NoOrder && !isProcessing) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.2f))
            )
        }

        // 主按鈕
        ElevatedButton(
            onClick = onClick,
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = buttonColor,
                disabledContainerColor = buttonColor.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 12.dp,
                pressedElevation = 6.dp
            ),
            enabled = orderState !is SmartOrderState.NoOrder && !isProcessing
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(60.dp),
                        color = Color.White,
                        strokeWidth = 6.dp
                    )
                } else {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = buttonText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 輔助資訊
 */
@Composable
fun AssistantInfo(orderState: SmartOrderState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "智能助理",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (orderState) {
                    is SmartOrderState.NoOrder ->
                        "系統待命中，有新訂單會自動通知"
                    is SmartOrderState.WaitingForAccept ->
                        "點擊按鈕接受訂單，系統會導航到上車點"
                    is SmartOrderState.NavigatingToPickup -> {
                        if (orderState.nearPickup) {
                            "GPS偵測您已接近上車點，點擊確認到達"
                        } else {
                            "正在導航到上車點，接近時會自動提醒"
                        }
                    }
                    is SmartOrderState.ArrivedAtPickup ->
                        "乘客上車後，點擊開始載客"
                    is SmartOrderState.OnTrip -> {
                        if (orderState.nearDestination) {
                            "GPS偵測即將到達目的地，準備結束行程"
                        } else {
                            "載客中，系統會自動偵測接近目的地"
                        }
                    }
                    is SmartOrderState.WaitingForPayment ->
                        "點擊按鈕輸入車資金額"
                },
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )
        }
    }
}

/**
 * 自動提示橫幅
 */
@Composable
fun AutoHintBanner(orderState: SmartOrderState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF4CAF50),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = when (orderState) {
                    is SmartOrderState.NavigatingToPickup ->
                        if (orderState.nearPickup) "已接近上車點" else ""
                    is SmartOrderState.OnTrip ->
                        if (orderState.nearDestination) "即將到達目的地" else ""
                    else -> ""
                },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 判斷是否顯示自動提示
 */
fun shouldShowAutoHint(orderState: SmartOrderState): Boolean {
    return when (orderState) {
        is SmartOrderState.NavigatingToPickup -> orderState.nearPickup
        is SmartOrderState.OnTrip -> orderState.nearDestination
        else -> false
    }
}

/**
 * 處理下一步操作
 */
suspend fun handleNextAction(
    nextAction: NextAction,
    viewModel: HomeViewModel,
    orderState: SmartOrderState,
    voiceAssistant: VoiceAssistant,
    onShowFareDialog: () -> Unit
) {
    when (nextAction) {
        NextAction.Accepted -> {
            (orderState as? SmartOrderState.WaitingForAccept)?.let { state ->
                viewModel.acceptOrder(
                    state.order.orderId,
                    "driverId",
                    "driverName"
                )
                voiceAssistant.speak("訂單已接受，開始導航")
            }
        }
        NextAction.Arrived -> {
            (orderState as? SmartOrderState.NavigatingToPickup)?.let { state ->
                viewModel.markArrived(state.order.orderId)
                voiceAssistant.speak("已到達上車點")
            }
        }
        NextAction.TripStarted -> {
            (orderState as? SmartOrderState.ArrivedAtPickup)?.let { state ->
                viewModel.startTrip(state.order.orderId)
                voiceAssistant.speak("行程開始")
            }
        }
        NextAction.TripEnded -> {
            (orderState as? SmartOrderState.OnTrip)?.let { state ->
                viewModel.endTrip(state.order.orderId)
                voiceAssistant.speak("行程結束，請收費")
            }
        }
        NextAction.SubmitFare -> {
            onShowFareDialog()
        }
        NextAction.NavigateToPickup -> {
            voiceAssistant.speak("請繼續前往上車點")
        }
        NextAction.ShowDestination -> {
            voiceAssistant.speak("載客中")
        }
        NextAction.NoAction -> {}
    }
}

/**
 * 啟動位置追蹤
 */
@SuppressLint("MissingPermission")
fun startLocationTracking(
    fusedLocationClient: FusedLocationProviderClient,
    smartOrderManager: SmartOrderManager
) {
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
        .setMinUpdateIntervalMillis(1000)
        .build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                smartOrderManager.updateLocation(location)
            }
        }
    }

    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        null
    )
}

/**
 * 格式化距離
 */
fun formatDistance(meters: Double): String {
    return if (meters < 1000) {
        "${meters.toInt()}公尺"
    } else {
        String.format("%.1f公里", meters / 1000)
    }
}