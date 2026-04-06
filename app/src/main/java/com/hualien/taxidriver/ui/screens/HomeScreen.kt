package com.hualien.taxidriver.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.domain.model.OrderStatus
import com.hualien.taxidriver.service.LocationService
import com.hualien.taxidriver.ui.components.FareDialog
import com.hualien.taxidriver.ui.components.MiniVoiceChatButton
import com.hualien.taxidriver.ui.components.RatingDialog
import com.hualien.taxidriver.ui.components.OrderTagRow
import com.hualien.taxidriver.ui.components.VoiceChatPanel
import com.hualien.taxidriver.utils.formatKilometers
import com.hualien.taxidriver.viewmodel.HomeViewModel
import com.hualien.taxidriver.viewmodel.PhoneReviewViewModel

// ====== 新 UI 顏色定義 ======
private val HeaderGradientStart = Color(0xFF1976D2)
private val HeaderGradientEnd = Color(0xFF1565C0)
private val ButtonActiveGreen = Color(0xFF4CAF50)
private val ButtonInactiveBg = Color(0xFFF5F5F5)
private val OrderCardYellow = Color(0xFFFFF8E1)
private val ActionBlue = Color(0xFF1976D2)
private val ScreenBackground = Color(0xFFECEFF1)
private val StatusGreen = Color(0xFF4CAF50)
private val StatusOrange = Color(0xFFFF9800)
private val StatusGray = Color(0xFF9E9E9E)
private val StatusBlue = Color(0xFF2196F3)
private val DarkText = Color(0xFF212121)
private val SubText = Color(0xFF757575)

/**
 * 主頁面 - 司機狀態 + 訂單管理（大按鈕版本，適合老年司機）
 */
@Composable
fun HomeScreen(
    driverId: String,
    driverName: String,
    viewModel: HomeViewModel = viewModel(),
    onNavigateToOrders: (() -> Unit)? = null,
    onNavigateToPhoneReview: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 電話客服審核
    val phoneReviewVm: PhoneReviewViewModel = viewModel()
    val phoneReviewState by phoneReviewVm.uiState.collectAsState()
    LaunchedEffect(driverId) {
        phoneReviewVm.loadReviewCount(driverId)
    }

    // 語音對講狀態
    val voiceChatHistory by viewModel.voiceChatHistory.collectAsState()
    val voiceChatState by viewModel.voiceChatState.collectAsState()
    val isVoiceChatRecording by viewModel.voiceChatRecording.collectAsState()
    val voiceChatAmplitude by viewModel.voiceChatAmplitude.collectAsState()
    val showVoiceChatPanel by viewModel.showVoiceChatPanel.collectAsState()
    val voiceChatUnreadCount by viewModel.voiceChatUnreadCount.collectAsState()

    // 處理錯誤訊息
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // 車資對話框狀態
    var showFareDialog by remember { mutableStateOf(false) }
    var fareDialogInitialAmount by remember { mutableStateOf<Int?>(null) }
    var currentOrderIdForFare by remember { mutableStateOf<String?>(null) }

    // 位置權限和錄音權限
    var hasLocationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // 啟動時請求權限（包含錄音權限，用於語音接單）
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    // 初始化語音服務
    LaunchedEffect(driverId, driverName) {
        viewModel.initVoiceServices(context, driverId, driverName)
        viewModel.initVoiceChat(context)
    }

    // 當訂單被接受時，設置語音對講用戶資訊
    LaunchedEffect(uiState.currentOrder?.orderId, uiState.currentOrder?.status) {
        val order = uiState.currentOrder
        if (order != null && order.status in listOf(OrderStatus.ACCEPTED, OrderStatus.ARRIVED, OrderStatus.ON_TRIP)) {
            viewModel.setupVoiceChatUser(order.orderId, driverId, driverName)
        }
    }

    // 建立 WebSocket 連接（司機上線）並設置初始狀態
    LaunchedEffect(driverId) {
        viewModel.connectWebSocket(driverId)
        viewModel.updateDriverStatus(driverId, DriverAvailability.AVAILABLE)
        viewModel.loadTodayStats(driverId)
    }

    // 監聽新訂單並自動播報（語音接單核心）
    LaunchedEffect(uiState.currentOrder) {
        val order = uiState.currentOrder
        if (order != null && order.status == OrderStatus.OFFERED) {
            viewModel.announceNewOrder(order)
        }
    }

    // WebSocket 生命週期由 ViewModel.onCleared() 管理，不在 UI 層斷開

    // 根據司機狀態啟動/停止定位服務
    LaunchedEffect(uiState.driverStatus, hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect

        val needLocationService = uiState.driverStatus == DriverAvailability.AVAILABLE ||
                uiState.driverStatus == DriverAvailability.ON_TRIP

        val intent = Intent(context, LocationService::class.java).apply {
            putExtra(LocationService.EXTRA_DRIVER_ID, driverId)
        }

        if (needLocationService) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
    }

    // ====== WebSocket 連線狀態監聽 ======
    val wsManager = com.hualien.taxidriver.data.remote.WebSocketManager.getInstance()
    val isWsConnected by wsManager.isConnected.collectAsState()

    // ====== UI 佈局 ======
    Box(modifier = Modifier.fillMaxSize()) {
        val currentOrder = uiState.currentOrder

        if (currentOrder == null) {
            // ====== 無訂單：固定佈局 + 2x2 網格 ======
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScreenBackground)
            ) {
                // 連線中斷警告橫幅
                if (!isWsConnected && uiState.driverStatus != DriverAvailability.OFFLINE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFD32F2F))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "連線中斷，重新連接中...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                NewTopBar(title = driverName)

                Spacer(modifier = Modifier.height(12.dp))

                NewStatsBar(
                    status = uiState.driverStatus,
                    orderCount = uiState.todayOrderCount,
                    earnings = uiState.todayEarnings,
                    distance = uiState.todayDistance,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 電話客服審核提示
                if (phoneReviewState.reviewCount > 0 && onNavigateToPhoneReview != null) {
                    Card(
                        onClick = { onNavigateToPhoneReview() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${phoneReviewState.reviewCount} 通電話需要人工處理",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "AI 辨識不了，點擊幫忙處理",
                                    fontSize = 14.sp,
                                    color = Color(0xFF757575)
                                )
                            }
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF1976D2)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                NewStatusGrid(
                    currentStatus = uiState.driverStatus,
                    onStatusChange = { newStatus ->
                        viewModel.updateDriverStatus(driverId, newStatus)
                    },
                    onNavigateToOrders = onNavigateToOrders,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            // ====== 有訂單：可滾動佈局 ======
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScreenBackground)
                    .verticalScroll(rememberScrollState())
            ) {
                NewTopBar(title = "返回主頁")

                Spacer(modifier = Modifier.height(12.dp))

                NewStatsBar(
                    status = uiState.driverStatus,
                    orderCount = uiState.todayOrderCount,
                    earnings = uiState.todayEarnings,
                    distance = uiState.todayDistance,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ====== 訂單資訊卡 ======
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OrderCardYellow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 標題行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🚕", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "目前訂單",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                // 訂單來源標示
                                if (currentOrder.source != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "- ${currentOrder.getSourceDisplayName()}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (currentOrder.source) {
                                            "LINE" -> Color(0xFF00C300)
                                            "PHONE" -> Color(0xFFFF8C00)
                                            else -> StatusBlue
                                        }
                                    )
                                }
                            }
                            Surface(
                                color = when (currentOrder.status) {
                                    OrderStatus.OFFERED -> StatusBlue
                                    OrderStatus.ACCEPTED -> StatusGreen
                                    OrderStatus.ARRIVED -> Color(0xFF9C27B0)
                                    OrderStatus.ON_TRIP -> Color(0xFF00BCD4)
                                    OrderStatus.SETTLING -> StatusOrange
                                    else -> StatusGray
                                }.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = when (currentOrder.status) {
                                        OrderStatus.OFFERED -> "新訂單"
                                        OrderStatus.ACCEPTED -> "已接單"
                                        OrderStatus.ARRIVED -> "已到達"
                                        OrderStatus.ON_TRIP -> "行程中"
                                        OrderStatus.SETTLING -> "結算中"
                                        else -> currentOrder.status.name
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (currentOrder.status) {
                                        OrderStatus.OFFERED -> StatusBlue
                                        OrderStatus.ACCEPTED -> StatusGreen
                                        OrderStatus.ARRIVED -> Color(0xFF9C27B0)
                                        OrderStatus.ON_TRIP -> Color(0xFF00BCD4)
                                        OrderStatus.SETTLING -> StatusOrange
                                        else -> StatusGray
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 訂單標籤（來源/補貼/寵物）
                        if (currentOrder.source != null && currentOrder.source != "APP") {
                            OrderTagRow(
                                order = currentOrder,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // 電話號碼
                        val displayPhone = currentOrder.passengerPhone ?: currentOrder.customerPhone
                        displayPhone?.let { phone ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = ActionBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = phone,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DarkText
                                )
                            }
                        }

                        // 電話訂單來電號碼（如果與顯示電話不同）
                        if (currentOrder.isPhoneOrder() && !currentOrder.customerPhone.isNullOrEmpty()
                            && currentOrder.customerPhone != displayPhone
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color(0xFFFF8C00),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "來電: ${currentOrder.customerPhone}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFF8C00)
                                )
                            }
                        }

                        // 上車點
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "上車",
                                    fontSize = 14.sp,
                                    color = SubText
                                )
                                Text(
                                    text = currentOrder.pickup.address ?: "未提供地址",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DarkText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 目的地
                        currentOrder.destination?.let { dest ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "目的地",
                                        fontSize = 14.sp,
                                        color = SubText
                                    )
                                    Text(
                                        text = dest.address ?: "未提供地址",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DarkText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 距離和時間資訊
                        if (currentOrder.distanceToPickup != null || currentOrder.tripDistance != null || currentOrder.estimatedFare != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    currentOrder.distanceToPickup?.let { distance ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🚗 到客人", fontSize = 12.sp, color = SubText)
                                            Text(
                                                text = distance.formatKilometers(),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkText
                                            )
                                            // ETA：優先用 googleEtaSeconds（更精確），fallback 到 etaToPickup
                                            val etaMinutes = currentOrder.googleEtaSeconds?.let { it / 60 } ?: currentOrder.etaToPickup
                                            etaMinutes?.let { eta ->
                                                Text("約 $eta 分鐘到", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusBlue)
                                            }
                                        }
                                    }

                                    if (currentOrder.distanceToPickup != null && (currentOrder.tripDistance != null || currentOrder.estimatedFare != null)) {
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(40.dp)
                                                .background(Color(0xFFE0E0E0))
                                        )
                                    }

                                    currentOrder.tripDistance?.let { distance ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("📍 行程", fontSize = 12.sp, color = SubText)
                                            Text(
                                                text = distance.formatKilometers(),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkText
                                            )
                                            currentOrder.estimatedTripDuration?.let { duration ->
                                                Text("約 $duration 分鐘", fontSize = 12.sp, color = SubText)
                                            }
                                        }
                                    }

                                    currentOrder.estimatedFare?.let { fare ->
                                        if (currentOrder.tripDistance != null) {
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(40.dp)
                                                    .background(Color(0xFFE0E0E0))
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("💰 車資", fontSize = 12.sp, color = SubText)
                                            Text(
                                                text = "NT$ $fare",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4CAF50)
                                            )
                                            Text("預估", fontSize = 12.sp, color = SubText)
                                        }
                                    }
                                }
                            }
                        }

                        // 電話訂單：上車地點未確認警告
                        val pickupUnclear = currentOrder.isPhoneOrder() &&
                                (currentOrder.pickup.address?.contains("待確認") == true ||
                                        currentOrder.pickup.address.isNullOrEmpty())
                        if (pickupUnclear) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                border = BorderStroke(2.dp, Color(0xFFF44336)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFC62828),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "上車地點未確認",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFC62828)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "客人未說明位置，請回撥確認",
                                        fontSize = 14.sp,
                                        color = Color(0xFFC62828)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val callbackPhone = currentOrder.customerPhone ?: currentOrder.passengerPhone
                                    callbackPhone?.let { phone ->
                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                                    data = Uri.parse("tel:$phone")
                                                }
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFF44336)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("回撥確認地址：$phone", fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 電話訂單：目的地確認按鈕
                        if (currentOrder.needsDestinationConfirmation()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "電話訂單 - 請確認目的地",
                                        fontSize = 14.sp,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.confirmDestination(currentOrder.orderId) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("確認目的地", fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ====== 撥打乘客 + 開始導航 按鈕列 ======
                if (currentOrder.status in listOf(
                        OrderStatus.ACCEPTED, OrderStatus.ARRIVED, OrderStatus.ON_TRIP
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 撥打乘客
                        val callPhone = currentOrder.passengerPhone ?: currentOrder.customerPhone
                        Button(
                            onClick = {
                                callPhone?.let { phone ->
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:$phone")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                            shape = RoundedCornerShape(12.dp),
                            enabled = callPhone != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("撥打乘客", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        // 開始導航
                        Button(
                            onClick = {
                                val navTarget = if (currentOrder.status == OrderStatus.ON_TRIP) {
                                    currentOrder.destination?.let { dest ->
                                        "google.navigation:q=${dest.latitude},${dest.longitude}"
                                    }
                                } else {
                                    "google.navigation:q=${currentOrder.pickup.latitude},${currentOrder.pickup.longitude}"
                                }
                                navTarget?.let { target ->
                                    val uri = Uri.parse(target)
                                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "請先安裝Google Maps", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("開始導航", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 語音對講按鈕
                    if (viewModel.canUseVoiceChat()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            MiniVoiceChatButton(
                                onClick = { viewModel.showVoiceChatPanel() },
                                hasUnread = voiceChatUnreadCount > 0
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ====== 訂單操作按鈕 ======
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    when (currentOrder.status) {
                        OrderStatus.OFFERED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.rejectOrder(currentOrder.orderId, driverId)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp),
                                    enabled = !uiState.isLoading,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE53935))
                                ) {
                                    Text("拒絕", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                                }
                                Button(
                                    onClick = {
                                        viewModel.acceptOrder(currentOrder.orderId, driverId, driverName)
                                    },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(72.dp),
                                    enabled = !uiState.isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = ButtonActiveGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White
                                        )
                                    } else {
                                        Text("接受訂單", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        OrderStatus.ACCEPTED -> {
                            Button(
                                onClick = { viewModel.markArrived(currentOrder.orderId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = ButtonActiveGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("已到達上車點", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OrderStatus.ARRIVED -> {
                            Button(
                                onClick = { viewModel.startTrip(currentOrder.orderId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = ButtonActiveGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("開始行程", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OrderStatus.ON_TRIP -> {
                            Button(
                                onClick = { viewModel.endTrip(currentOrder.orderId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("結束行程", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OrderStatus.SETTLING -> {
                            Button(
                                onClick = {
                                    currentOrderIdForFare = currentOrder.orderId
                                    fareDialogInitialAmount = currentOrder.estimatedFare
                                    showFareDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("提交車資", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> {}
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ====== 下一單預覽 ======
                uiState.queuedOrder?.let { queuedOrder ->
                    QueuedOrderCard(
                        order = queuedOrder,
                        driverId = driverId,
                        driverName = driverName,
                        isLoading = uiState.isLoading,
                        onAccept = { viewModel.acceptOrder(queuedOrder.orderId, driverId, driverName) },
                        onReject = { viewModel.rejectOrder(queuedOrder.orderId, driverId) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ====== 車資對話框 ======
        if (showFareDialog) {
            FareDialog(
                onDismiss = { showFareDialog = false },
                onConfirm = { meterAmount, _ ->
                    currentOrderIdForFare?.let { orderId ->
                        viewModel.submitFare(orderId, driverId, meterAmount)
                    }
                    showFareDialog = false
                    currentOrderIdForFare = null
                    fareDialogInitialAmount = null
                },
                initialAmount = fareDialogInitialAmount
            )
        }

        // ====== 評分對話框 ======
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

        // ====== 語音監聽指示器 ======
        AnimatedVisibility(
            visible = uiState.isVoiceListening,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            VoiceListeningIndicator()
        }

        // ====== 語音對講面板 ======
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
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ================================================================
// 新 UI 組件
// ================================================================

/**
 * 藍色漸層頂部欄
 */
@Composable
private fun NewTopBar(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(HeaderGradientStart, HeaderGradientEnd)
                )
            )
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 今日統計欄
 */
@Composable
private fun NewStatsBar(
    status: DriverAvailability,
    orderCount: Int,
    earnings: Int,
    distance: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 狀態行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "今日",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkText
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                DriverAvailability.AVAILABLE -> StatusGreen
                                DriverAvailability.REST -> StatusOrange
                                DriverAvailability.ON_TRIP -> StatusBlue
                                DriverAvailability.OFFLINE -> StatusGray
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (status) {
                        DriverAvailability.AVAILABLE -> "可接單中"
                        DriverAvailability.REST -> "休息中"
                        DriverAvailability.ON_TRIP -> "載客中"
                        DriverAvailability.OFFLINE -> "已離線"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (status) {
                        DriverAvailability.AVAILABLE -> StatusGreen
                        DriverAvailability.REST -> StatusOrange
                        DriverAvailability.ON_TRIP -> StatusBlue
                        DriverAvailability.OFFLINE -> StatusGray
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 統計行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${orderCount}單",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color(0xFFE0E0E0))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$earnings",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = "收入",
                        fontSize = 12.sp,
                        color = SubText
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color(0xFFE0E0E0))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${String.format("%.1f", distance)}公里",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }
            }
        }
    }
}

/**
 * 2x2 狀態按鈕網格（無訂單時顯示）
 */
@Composable
private fun NewStatusGrid(
    currentStatus: DriverAvailability,
    onStatusChange: (DriverAvailability) -> Unit,
    onNavigateToOrders: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 上排：離線 + 休息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusGridButton(
                label = "離線",
                isActive = currentStatus == DriverAvailability.OFFLINE,
                activeColor = Color(0xFF78909C),
                onClick = { onStatusChange(DriverAvailability.OFFLINE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StatusGridButton(
                label = "休息",
                isActive = currentStatus == DriverAvailability.REST,
                activeColor = StatusOrange,
                onClick = { onStatusChange(DriverAvailability.REST) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // 下排：可接單 + 訂單
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusGridButton(
                label = "可接單",
                isActive = currentStatus == DriverAvailability.AVAILABLE,
                activeColor = ButtonActiveGreen,
                showCheckmark = currentStatus == DriverAvailability.AVAILABLE,
                onClick = { onStatusChange(DriverAvailability.AVAILABLE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StatusGridButton(
                label = "訂單",
                isActive = false,
                activeColor = ActionBlue,
                onClick = { onNavigateToOrders?.invoke() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * 單個網格按鈕
 */
@Composable
private fun StatusGridButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    showCheckmark: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) activeColor else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (showCheckmark) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = label,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White else DarkText
                )
            }
        }
    }
}

/**
 * 底部狀態指示器（不可點擊，僅顯示當前狀態）
 */
@Composable
private fun StatusIndicatorBar(
    status: DriverAvailability,
    modifier: Modifier = Modifier
) {
    val bgColor = when (status) {
        DriverAvailability.AVAILABLE -> ButtonActiveGreen
        DriverAvailability.ON_TRIP -> StatusBlue
        DriverAvailability.REST -> StatusOrange
        DriverAvailability.OFFLINE -> StatusGray
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (status) {
                    DriverAvailability.AVAILABLE -> "可接單"
                    DriverAvailability.ON_TRIP -> "載客中"
                    DriverAvailability.REST -> "休息中"
                    DriverAvailability.OFFLINE -> "已離線"
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "目前狀態",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 下一單預覽卡片
 */
@Composable
private fun QueuedOrderCard(
    order: Order,
    driverId: String,
    driverName: String,
    isLoading: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val handoverMinutes = remember(order.predictedHandoverAt) {
        order.predictedHandoverAt?.let {
            ((it - System.currentTimeMillis()) / 60000).toInt().coerceAtLeast(0)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🧾 下一單",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (order.status) {
                            OrderStatus.OFFERED -> "等待您確認"
                            OrderStatus.QUEUED -> "已預掛"
                            else -> order.status.getDisplayName()
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
                Surface(
                    color = order.status.getColor().copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = when (order.status) {
                            OrderStatus.OFFERED -> "待接受"
                            OrderStatus.QUEUED -> "已鎖定"
                            else -> order.status.getDisplayName()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = order.status.getColor(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            order.queuedAfterOrderId?.let { previousOrderId ->
                Text(
                    text = "接在前單後：$previousOrderId",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            handoverMinutes?.let { minutes ->
                Text(
                    text = if (minutes == 0) "預估可立即交接" else "預估約 $minutes 分鐘後接手",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "上車點",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = order.pickup.address ?: "未提供地址",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            order.destination?.let { destination ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "目的地",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = destination.address ?: "未提供地址",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (order.estimatedFare != null || order.etaToPickup != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    order.etaToPickup?.let { eta ->
                        Text(
                            text = "到客人約 $eta 分鐘",
                            fontSize = 14.sp
                        )
                    }
                    order.estimatedFare?.let { fare ->
                        Text(
                            text = "預估 NT$ $fare",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OrderTagRow(
                order = order,
                modifier = Modifier.padding(top = 12.dp)
            )

            if (order.status == OrderStatus.OFFERED) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("拒絕下一單", fontSize = 16.sp)
                    }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("接受下一單", fontSize = 16.sp)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "前單若取消或完成，系統會重新計算是否直接交接給您。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 語音監聽指示器
 */
@Composable
fun VoiceListeningIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "listening")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "聆聽中...",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
