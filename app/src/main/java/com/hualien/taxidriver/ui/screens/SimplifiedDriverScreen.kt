package com.hualien.taxidriver.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.net.Uri
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
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.domain.model.OrderStatus
import com.hualien.taxidriver.ui.components.OrderTagRowLarge
import com.hualien.taxidriver.ui.components.FareDialog
import com.hualien.taxidriver.ui.components.MiniVoiceChatButton
import com.hualien.taxidriver.ui.components.RatingDialog
import com.hualien.taxidriver.ui.components.VoiceChatPanel
import com.hualien.taxidriver.utils.VoiceAssistant
import com.hualien.taxidriver.utils.VoiceCommandHandler
import com.hualien.taxidriver.utils.formatKilometers
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

    // 語音指令處理器
    val voiceCommandHandler = remember { VoiceCommandHandler(context, voiceAssistant) }

    // UI狀態
    val uiState by viewModel.uiState.collectAsState()
    var showFareDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // 語音對講狀態
    val voiceChatHistory by viewModel.voiceChatHistory.collectAsState()
    val voiceChatState by viewModel.voiceChatState.collectAsState()
    val isVoiceChatRecording by viewModel.voiceChatRecording.collectAsState()
    val voiceChatAmplitude by viewModel.voiceChatAmplitude.collectAsState()
    val showVoiceChatPanel by viewModel.showVoiceChatPanel.collectAsState()
    val voiceChatUnreadCount by viewModel.voiceChatUnreadCount.collectAsState()

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

    // 初始化 ViewModel 語音服務（用於語音接單）
    LaunchedEffect(driverId, driverName) {
        viewModel.initVoiceServices(context, driverId, driverName)
        viewModel.initVoiceChat(context)  // 初始化語音對講
    }

    // 當訂單被接受時，設置語音對講用戶資訊
    LaunchedEffect(uiState.currentOrder?.orderId, uiState.currentOrder?.status) {
        val order = uiState.currentOrder
        if (order != null && order.status in listOf(OrderStatus.ACCEPTED, OrderStatus.ARRIVED, OrderStatus.ON_TRIP)) {
            viewModel.setupVoiceChatUser(order.orderId, driverId, driverName)
        }
    }

    // WebSocket 生命週期由 ViewModel.onCleared() 管理，不在 UI 層斷開
    // 避免畫面切換/重組時誤觸發斷線

    // 顯示錯誤提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            voiceAssistant.speak("操作失敗，${error}")
        }
    }

    // 設置語音指令處理器回調
    // 注意：語音指令不需要立即更新 SmartOrderManager 狀態
    // 因為 ViewModel 的 API 調用成功後會更新 uiState.currentOrder
    // 然後 LaunchedEffect(uiState.currentOrder) 會自動同步到 SmartOrderManager
    LaunchedEffect(Unit) {
        voiceCommandHandler.setCallback(object : VoiceCommandHandler.CommandCallback {
            override fun onAcceptOrder(orderId: String?) {
                (orderState as? SmartOrderState.WaitingForAccept)?.let { state ->
                    viewModel.acceptOrder(state.order.orderId, driverId, driverName)
                    // 狀態由 ViewModel 更新後自動同步
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
                    // 狀態由 ViewModel 更新後自動同步
                }
            }

            override fun onStartTrip(orderId: String?) {
                (orderState as? SmartOrderState.ArrivedAtPickup)?.let { state ->
                    viewModel.startTrip(state.order.orderId)
                    // 狀態由 ViewModel 更新後自動同步
                }
            }

            override fun onEndTrip(orderId: String?) {
                (orderState as? SmartOrderState.OnTrip)?.let { state ->
                    viewModel.endTrip(state.order.orderId)
                    // 狀態由 ViewModel 更新後自動同步
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
            val result = voiceCommandHandler.handleCommand(command)
            viewModel.clearVoiceCommand()
        }
    }

    // 處理語音錯誤
    LaunchedEffect(uiState.voiceError) {
        uiState.voiceError?.let { error ->
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

            // 語音提示新訂單（使用 ViewModel 的語音接單功能）
            when (order.status) {
                com.hualien.taxidriver.domain.model.OrderStatus.OFFERED -> {
                    // 使用 ViewModel 的 announceNewOrder 進行語音接單播報
                    // 播報完會自動開始語音監聽
                    viewModel.announceNewOrder(order)
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
                driverStatus = uiState.driverStatus,
                onConfirmLoveCard = { orderId -> viewModel.confirmLoveCard(orderId, driverId) },
                onCancelLoveCard = { orderId -> viewModel.cancelLoveCard(orderId, driverId) }
            )

            // 中間：智能大按鈕
            SmartActionButton(
                orderState = orderState,
                isProcessing = isProcessing || uiState.isLoading, // 結合兩種載入狀態
                pulseScale = pulseScale,
                onClick = {
                    // 防止重複點擊
                    if (isProcessing || uiState.isLoading) return@SmartActionButton

                    scope.launch {
                        isProcessing = true

                        // 獲取下一步操作（不改變狀態）
                        val nextAction = smartOrderManager.getNextAction()
                        handleNextAction(
                            nextAction = nextAction,
                            viewModel = viewModel,
                            orderState = orderState,
                            voiceAssistant = voiceAssistant,
                            onShowFareDialog = { showFareDialog = true }
                        )

                        // 等待 API 完成或超時（最多 10 秒）
                        var waitTime = 0
                        while (uiState.isLoading && waitTime < 10000) {
                            delay(100)
                            waitTime += 100
                        }

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

        // 語音監聽指示器（語音接單時顯示）
        AnimatedVisibility(
            visible = uiState.isVoiceListening,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) {
            VoiceListeningIndicator()
        }

        // 一鍵撥號浮動按鈕（接單後顯示）
        val currentOrderState = orderState  // 捕獲委託屬性的值以支援 smart cast
        val showCallButton = when (currentOrderState) {
            is SmartOrderState.NavigatingToPickup,
            is SmartOrderState.ArrivedAtPickup,
            is SmartOrderState.OnTrip -> true
            else -> false
        }

        val passengerPhone = when (currentOrderState) {
            is SmartOrderState.NavigatingToPickup -> currentOrderState.order.passengerPhone
            is SmartOrderState.ArrivedAtPickup -> currentOrderState.order.passengerPhone
            is SmartOrderState.OnTrip -> currentOrderState.order.passengerPhone
            else -> null
        }

        // 底部操作按鈕（語音對講 + 撥號）
        AnimatedVisibility(
            visible = showCallButton && passengerPhone != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // 語音對講按鈕（接單後顯示）
                if (viewModel.canUseVoiceChat()) {
                    FloatingActionButton(
                        onClick = { viewModel.showVoiceChatPanel() },
                        containerColor = if (voiceChatUnreadCount > 0)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "語音對講",
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = if (voiceChatUnreadCount > 0) "新訊息" else "對講",
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 撥號按鈕
                FloatingActionButton(
                    onClick = {
                        passengerPhone?.let { phone ->
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$phone")
                            }
                            context.startActivity(intent)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(72.dp) // 大按鈕適合中老年人
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "撥打乘客電話",
                            modifier = Modifier.size(28.dp)
                        )
                        Text("撥打", fontSize = 12.sp)
                    }
                }
            }
        }

        // 語音對講面板
        if (showVoiceChatPanel) {
            VoiceChatPanel(
                messages = voiceChatHistory,
                currentUserId = driverId,
                isRecording = isVoiceChatRecording,
                state = voiceChatState,
                amplitude = voiceChatAmplitude,
                onStartRecording = { viewModel.startVoiceChatRecording() },
                onStopRecording = { viewModel.stopVoiceChatRecording() },
                onClose = { viewModel.hideVoiceChatPanel() },
                enabled = viewModel.canUseVoiceChat(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            )
        }
    }

    // 車資對話框
    if (showFareDialog) {
        val fareOrder = (orderState as? SmartOrderState.WaitingForPayment)?.order
        FareDialog(
            onDismiss = { showFareDialog = false },
            onConfirm = { meterAmount, photoUri ->
                fareOrder?.let { order ->
                    viewModel.submitFare(order.orderId, driverId, meterAmount)
                    smartOrderManager.completeOrder()
                }
                showFareDialog = false
            },
            subsidyType = fareOrder?.subsidyType ?: "NONE",
            subsidyConfirmed = fareOrder?.subsidyConfirmed ?: false,
            subsidyAmount = com.hualien.taxidriver.utils.FareCalculator.loveCardSubsidyAmount
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
    driverStatus: DriverAvailability,
    onConfirmLoveCard: (String) -> Unit = {},
    onCancelLoveCard: (String) -> Unit = {}
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

                    // 訂單標籤（來源/補貼/寵物）
                    if (orderState.order.source != null && orderState.order.source != "APP" ||
                        orderState.order.subsidyType != null && orderState.order.subsidyType != "NONE") {
                        OrderTagRowLarge(order = orderState.order)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 電話訂單顯示來電號碼
                    if (orderState.order.isPhoneOrder()) {
                        orderState.order.customerPhone?.let { phone ->
                            Text(
                                text = "📞 $phone",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8C00),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

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
                                    text = distance.formatKilometers(),
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
                                    text = distance.formatKilometers(),
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

                        // 預估車資
                        orderState.order.estimatedFare?.let { fare ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "💰 車資",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$$fare",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = "預估",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is SmartOrderState.ArrivedAtPickup -> {
                    val order = orderState.order
                    // 愛心卡確認區塊
                    if (order.subsidyType == "LOVE_CARD") {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!order.subsidyConfirmed) {
                            // 尚未確認 — 顯示確認/取消按鈕
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF3E0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "乘客聲明持有愛心卡",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Text(
                                        text = "請確認乘客出示實體卡片",
                                        fontSize = 16.sp,
                                        color = Color(0xFF795548)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { onConfirmLoveCard(order.orderId) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF4CAF50)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("已確認卡片", fontSize = 16.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { onCancelLoveCard(order.orderId) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("乘客無卡", fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            // 已確認
                            Text(
                                text = "✓ 愛心卡已確認",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = driverName,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
