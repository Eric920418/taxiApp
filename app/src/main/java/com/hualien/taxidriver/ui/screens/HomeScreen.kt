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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.input.pointer.pointerInput
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
import com.hualien.taxidriver.ui.components.DiscountPreferenceSheetContent
import com.hualien.taxidriver.ui.components.FareDialog
import com.hualien.taxidriver.ui.components.QueueZoneSheetContent
import com.hualien.taxidriver.ui.components.MiniVoiceChatButton
import com.hualien.taxidriver.ui.components.RatingDialog
import com.hualien.taxidriver.ui.components.OrderTagRow
import com.hualien.taxidriver.ui.components.VoiceChatPanel
import com.hualien.taxidriver.utils.formatKilometers
import com.hualien.taxidriver.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
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

// ====== 狀態顏色統一規則（長輩友善一眼辨識）======
// 已接單=綠 | 已到達=紫 | 行程中=藍 | 結算中=橘 | 已完成=深綠
private fun orderStatusColor(status: OrderStatus): Color = when (status) {
    OrderStatus.OFFERED -> Color(0xFF2196F3)   // 新訂單：亮藍
    OrderStatus.ACCEPTED -> Color(0xFF4CAF50)  // 已接單：綠
    OrderStatus.ARRIVED -> Color(0xFF9C27B0)   // 已到達：紫
    OrderStatus.ON_TRIP -> Color(0xFF1976D2)   // 行程中：深藍
    OrderStatus.SETTLING -> Color(0xFFFF9800)  // 結算中：橘
    OrderStatus.DONE -> Color(0xFF2E7D32)      // 已完成：深綠
    else -> Color(0xFF757575)                  // 其他：灰
}

private fun orderStatusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.OFFERED -> "新訂單"
    OrderStatus.ACCEPTED -> "已接單"
    OrderStatus.ARRIVED -> "已到達"
    OrderStatus.ON_TRIP -> "行程中"
    OrderStatus.SETTLING -> "結算中"
    OrderStatus.DONE -> "已完成"
    else -> status.name
}

/**
 * 格式化乘客顯示名稱 — 隱藏 LINE_xxx 系統 ID，改顯示友善名稱
 */
private fun formatPassengerDisplay(order: Order): String {
    val phone = order.passengerPhone ?: ""
    val name = order.passengerName
    val isFakePhone = phone.startsWith("LINE_") || phone.startsWith("PHONE_")

    return when {
        order.source == "LINE" -> if (name.isNotBlank() && name != "乘客") "LINE 乘客：$name" else "LINE 乘客"
        order.source == "PHONE" && !isFakePhone -> "電話訂單：$phone"
        order.source == "PHONE" -> "電話訂單"
        !isFakePhone && phone.isNotBlank() -> phone
        name.isNotBlank() && name != "乘客" -> name
        else -> "乘客"
    }
}

/**
 * 判斷電話是否為真實號碼（非 LINE_/PHONE_ 開頭的假電話）
 */
private fun isRealPhoneNumber(phone: String?): Boolean {
    if (phone.isNullOrBlank()) return false
    return !phone.startsWith("LINE_") && !phone.startsWith("PHONE_")
}

/**
 * 主頁面 - 司機狀態 + 訂單管理（大按鈕版本，適合老年司機）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    driverId: String,
    driverName: String,
    viewModel: HomeViewModel = viewModel(),
    onNavigateToOrders: (() -> Unit)? = null,
    onNavigateToEarnings: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null,
    onNavigateToPhoneReview: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // v1.3.0：sheet / dialog state — 主畫面 8 按鈕觸發
    var showQueueSheet by remember { mutableStateOf(false) }
    var showDiscountSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val discountSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()

    // 電話客服審核
    val phoneReviewVm: PhoneReviewViewModel = viewModel()
    val phoneReviewState by phoneReviewVm.uiState.collectAsState()
    LaunchedEffect(driverId) {
        phoneReviewVm.loadReviewCount(driverId)
        viewModel.refreshQueue()  // 載入排班區 + 自己的排班狀態
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

    // 流程簡化：訂單進入 SETTLING 狀態時自動彈車資輸入視窗
    // 用 orderId 去重，避免關閉後又被相同狀態重新觸發
    var autoOpenedFareForOrder by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.currentOrder?.orderId, uiState.currentOrder?.status) {
        val order = uiState.currentOrder
        if (order != null &&
            order.status == OrderStatus.SETTLING &&
            autoOpenedFareForOrder != order.orderId
        ) {
            currentOrderIdForFare = order.orderId
            fareDialogInitialAmount = order.estimatedFare
            showFareDialog = true
            autoOpenedFareForOrder = order.orderId
        }
    }

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

    // 初始化服務（idempotent，內部會跳過重複 init）
    LaunchedEffect(driverId, driverName) {
        viewModel.initSlowTrafficPersist(context)  // Phase C+1a：低速計時 DataStore 持久化
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

                // 班次狀態 banner（admin 設了排班才顯示；24/7 在班的司機看不到）
                ShiftStatusBanner(shifts = uiState.shifts)

                // 整合式 Top Bar：返回（長按登出）+ 名字 + 今日 1單 / $XXX / X.X公里
                CompactTopBar(
                    driverName = driverName,
                    orderCount = uiState.todayOrderCount,
                    earnings = uiState.todayEarnings,
                    distance = uiState.todayDistance,
                    onLongPressLogout = { showLogoutDialog = true },
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

                MainActionGrid(
                    currentStatus = uiState.driverStatus,
                    queueZoneName = uiState.myQueueStatus?.zoneName,
                    queuePosition = uiState.myQueueStatus?.position,
                    inQueue = uiState.myQueueStatus?.inQueue == true,
                    discountAmount = uiState.maxAcceptableDiscountAmount,
                    fleetPartnerName = uiState.fleetPartnerName,
                    fleetDefaultDiscountAmount = uiState.fleetDefaultDiscountAmount,
                    onStatusChange = { newStatus ->
                        viewModel.updateDriverStatus(driverId, newStatus)
                    },
                    onClickQueue = { showQueueSheet = true },
                    onClickDiscount = { showDiscountSheet = true },
                    onClickOrders = { onNavigateToOrders?.invoke() },
                    onClickEarnings = { onNavigateToEarnings?.invoke() },
                    onClickProfile = { onNavigateToProfile?.invoke() },
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
                CompactTopBar(
                    driverName = driverName,
                    orderCount = uiState.todayOrderCount,
                    earnings = uiState.todayEarnings,
                    distance = uiState.todayDistance,
                    onLongPressLogout = { showLogoutDialog = true },
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
                        // ===== 1. 狀態（最顯眼：大字 + 色塊）=====
                        Surface(
                            color = orderStatusColor(currentOrder.status).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = orderStatusLabel(currentOrder.status),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = orderStatusColor(currentOrder.status)
                                )
                                // 訂單來源標示
                                if (currentOrder.source != null) {
                                    Text(
                                        text = currentOrder.getSourceDisplayName(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (currentOrder.source) {
                                            "LINE" -> Color(0xFF00C300)
                                            "PHONE" -> Color(0xFFFF8C00)
                                            else -> StatusBlue
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ===== 2. 上車點（放大 22sp）=====
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "上車",
                                    fontSize = 18.sp,
                                    color = SubText
                                )
                                Text(
                                    text = currentOrder.pickup.address ?: "未提供地址",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DarkText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // ===== 3. 目的地 =====
                        currentOrder.destination?.let { dest ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "目的地",
                                        fontSize = 18.sp,
                                        color = SubText
                                    )
                                    Text(
                                        text = dest.address ?: "未提供地址",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DarkText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // ===== 4. 聯絡方式（只顯示真實電話，LINE 假電話顯示友善名稱）=====
                        run {
                            val realPhone = listOfNotNull(currentOrder.passengerPhone, currentOrder.customerPhone)
                                .firstOrNull { isRealPhoneNumber(it) }
                            val displayText = formatPassengerDisplay(currentOrder)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = ActionBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayText,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DarkText
                                    )
                                    if (realPhone != null) {
                                        Text(
                                            text = realPhone,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ActionBlue
                                        )
                                    }
                                }
                            }
                        }

                        // ===== 5. 特殊需求 / 備註（補貼/寵物）=====
                        if (currentOrder.source != null && currentOrder.source != "APP") {
                            OrderTagRow(
                                order = currentOrder,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // ===== 6. 客人備註（LIFF 叫車時填寫）=====
                        // 醒目橘底卡片：例「老人家腳不便請多等」「有行李箱」「大狗同行」
                        // 司機看到這資訊就知道要做哪些準備
                        if (!currentOrder.notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, Color(0xFFFF9800))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("📝", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "客人備註",
                                            fontSize = 14.sp,
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = currentOrder.notes,
                                            fontSize = 18.sp,
                                            color = Color(0xFF212121),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 距離和時間資訊（字體放大）
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
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    currentOrder.distanceToPickup?.let { distance ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🚗 到客人", fontSize = 15.sp, color = SubText)
                                            Text(
                                                text = distance.formatKilometers(),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkText
                                            )
                                            val etaMinutes = currentOrder.googleEtaSeconds?.let { it / 60 } ?: currentOrder.etaToPickup
                                            etaMinutes?.let { eta ->
                                                Text("約 $eta 分鐘到", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = StatusBlue)
                                            }
                                        }
                                    }

                                    if (currentOrder.distanceToPickup != null && (currentOrder.tripDistance != null || currentOrder.estimatedFare != null)) {
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(52.dp)
                                                .background(Color(0xFFE0E0E0))
                                        )
                                    }

                                    currentOrder.tripDistance?.let { distance ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("📍 行程", fontSize = 15.sp, color = SubText)
                                            Text(
                                                text = distance.formatKilometers(),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkText
                                            )
                                            currentOrder.estimatedTripDuration?.let { duration ->
                                                Text("約 $duration 分鐘", fontSize = 15.sp, color = SubText)
                                            }
                                        }
                                    }

                                    currentOrder.estimatedFare?.let { fare ->
                                        if (currentOrder.tripDistance != null) {
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(52.dp)
                                                    .background(Color(0xFFE0E0E0))
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("💰 車資", fontSize = 15.sp, color = SubText)
                                            Text(
                                                text = "NT$ $fare",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4CAF50)
                                            )
                                            Text("預估", fontSize = 13.sp, color = SubText)
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

                // ====== 訂單操作按鈕（長輩友善：每個狀態只保留一個主要大按鈕）======
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
                                        .height(80.dp),
                                    enabled = !uiState.isLoading,
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE53935))
                                ) {
                                    Text("拒絕", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                                }
                                Button(
                                    onClick = {
                                        viewModel.acceptOrder(currentOrder.orderId, driverId, driverName)
                                    },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(80.dp),
                                    enabled = !uiState.isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = ButtonActiveGreen),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(26.dp),
                                            color = Color.White
                                        )
                                    } else {
                                        Text("接受訂單", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        OrderStatus.ACCEPTED -> {
                            Button(
                                onClick = { viewModel.markArrived(currentOrder.orderId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = orderStatusColor(OrderStatus.ACCEPTED)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("已到達上車點", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OrderStatus.ARRIVED -> {
                            val waitingSec = uiState.noShowRemainingSeconds
                            val isWaiting = waitingSec != null

                            if (isWaiting) {
                                // ===== 等候倒數中 UI =====
                                val mm = waitingSec!! / 60
                                val ss = waitingSec % 60
                                val timeText = String.format("%d:%02d", mm, ss)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF9800))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "等候客人中",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                        Text(
                                            text = timeText,
                                            fontSize = 52.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                        Text(
                                            text = "倒數結束自動取消，已通知客人",
                                            fontSize = 14.sp,
                                            color = SubText
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.cancelNoShowNow(currentOrder.orderId, driverId) },
                                        modifier = Modifier.weight(1f).height(72.dp),
                                        enabled = !uiState.noShowCancelling,
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE53935))
                                    ) {
                                        Text("立即取消", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                                    }
                                    Button(
                                        onClick = { viewModel.cancelNoShowWaiting() },
                                        modifier = Modifier.weight(1.3f).height(72.dp),
                                        enabled = !uiState.noShowCancelling,
                                        colors = ButtonDefaults.buttonColors(containerColor = orderStatusColor(OrderStatus.ACCEPTED)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("客人來了", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                // ===== 正常 ARRIVED：主按鈕「開始行程」+ 次按鈕「客人未到」=====
                                Button(
                                    onClick = { viewModel.startTrip(currentOrder.orderId) },
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    enabled = !uiState.isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = orderStatusColor(OrderStatus.ARRIVED)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("開始行程", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = { viewModel.startNoShowWaiting(currentOrder.orderId, driverId) },
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF9800))
                                ) {
                                    Text(
                                        "客人未到？開始等候",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                }
                            }
                        }

                        OrderStatus.ON_TRIP -> {
                            Button(
                                onClick = { viewModel.endTrip(currentOrder.orderId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                enabled = !uiState.isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("結束行程", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // SETTLING：不顯示按鈕，由 LaunchedEffect 自動彈車資輸入窗
                        OrderStatus.SETTLING -> {
                            Text(
                                text = "請輸入車資",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = orderStatusColor(OrderStatus.SETTLING),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
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
            val currentOrder = uiState.currentOrder
            val slowSec = uiState.slowTrafficSeconds
            val slowSuggested = slowSec?.let { com.hualien.taxidriver.utils.FareCalculator.suggestSlowTrafficFare(it) }
                ?.takeIf { it > 0 }
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
                initialAmount = fareDialogInitialAmount,
                subsidyType = currentOrder?.subsidyType ?: "NONE",
                subsidyConfirmed = currentOrder?.subsidyConfirmed ?: false,
                subsidyAmount = com.hualien.taxidriver.utils.FareCalculator.loveCardSubsidyAmount,
                slowTrafficSeconds = slowSec,
                slowTrafficSuggestedFare = slowSuggested,
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

        // ====== 排班區選擇 sheet（主畫面「排班」按鈕觸發）======
        if (showQueueSheet) {
            val fusedLocationClient = remember {
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            }
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = queueSheetState,
            ) {
                QueueZoneSheetContent(
                    zones = uiState.queueZones,
                    myStatus = uiState.myQueueStatus,
                    onJoin = { zoneId ->
                        try {
                            @Suppress("MissingPermission")
                            val task = fusedLocationClient.lastLocation
                            task.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    viewModel.joinQueueZone(zoneId, loc.latitude, loc.longitude)
                                } else {
                                    Toast.makeText(context, "尚未取得 GPS 位置，請稍候再試", Toast.LENGTH_SHORT).show()
                                }
                            }
                            task.addOnFailureListener {
                                Toast.makeText(context, "GPS 取得失敗：${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: SecurityException) {
                            Toast.makeText(context, "請先授予位置權限", Toast.LENGTH_SHORT).show()
                        }
                        sheetScope.launch { queueSheetState.hide() }
                            .invokeOnCompletion { showQueueSheet = false }
                    },
                    onLeave = {
                        viewModel.leaveQueueZone()
                        sheetScope.launch { queueSheetState.hide() }
                            .invokeOnCompletion { showQueueSheet = false }
                    },
                )
            }
        }

        // ====== 折扣偏好 sheet（主畫面「折扣」按鈕觸發）======
        if (showDiscountSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDiscountSheet = false },
                sheetState = discountSheetState,
            ) {
                DiscountPreferenceSheetContent(
                    currentAmount = uiState.maxAcceptableDiscountAmount,
                    fleetPartnerName = uiState.fleetPartnerName,
                    onChange = { amt ->
                        viewModel.updateDiscountPreference(amt)
                        sheetScope.launch { discountSheetState.hide() }
                            .invokeOnCompletion { showDiscountSheet = false }
                    },
                )
            }
        }

        // ====== 登出確認對話框（CompactTopBar 長按觸發）======
        if (showLogoutDialog) {
            LogoutConfirmDialog(
                onConfirm = {
                    showLogoutDialog = false
                    onLogout?.invoke()
                },
                onDismiss = { showLogoutDialog = false },
            )
        }
    }
}

// ================================================================
// 新 UI 組件
// ================================================================

/**
 * v1.3.0 整合式 Top Bar
 *
 * 結構：[長按登出區｜返回箭頭+名字] | 1單 | $XXX | X.X公里
 * 老人友善：
 *  - 長按 800ms 才登出（短按無作用，避免誤觸）
 *  - 統計三段用 weight 平均分佈、中央對齊
 *  - 字級壓低（16/11sp），讓三段資訊在一條 bar 不擁擠
 */
@Composable
private fun CompactTopBar(
    driverName: String,
    orderCount: Int,
    earnings: Int,
    distance: Double,
    onLongPressLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(HeaderGradientStart, HeaderGradientEnd)
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左：長按登出區（返回箭頭 + 名字）
        Row(
            modifier = Modifier
                .weight(1.4f)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPressLogout() })
                }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "長按登出",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = driverName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 分隔線
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )

        TopBarStat(value = "${orderCount}單", label = "今日", modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )

        TopBarStat(value = "$$earnings", label = "收入", modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )

        TopBarStat(value = "${String.format("%.1f", distance)}km", label = "里程", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TopBarStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
        )
    }
}

/**
 * v1.3.0 主畫面 4x2 大按鈕網格
 *
 * 排版：
 *   row 0: [排班]      [離線]
 *   row 1: [接單]      [休息]
 *   row 2: [訂單]      [收入]
 *   row 3: [我的]      [折扣]
 *
 * 視覺規則：
 *   - 互斥狀態（離線/接單/休息）：active 時綠底 + 勾號 + 白字
 *   - 排班：未加入白底；已加入綠底 + 勾號 + 顯示「#3 花蓮車站」
 *   - 折扣：白底 + 副標顯示當前值「≤20元」或「全價」
 *   - 訂單/收入/我的：純功能入口，白底
 */
@Composable
private fun MainActionGrid(
    currentStatus: DriverAvailability,
    queueZoneName: String?,
    queuePosition: Int?,
    inQueue: Boolean,
    discountAmount: Int,
    fleetPartnerName: String?,
    fleetDefaultDiscountAmount: Int?,
    onStatusChange: (DriverAvailability) -> Unit,
    onClickQueue: () -> Unit,
    onClickDiscount: () -> Unit,
    onClickOrders: () -> Unit,
    onClickEarnings: () -> Unit,
    onClickProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFleetDriver = fleetPartnerName != null
    val displayDiscount = if (isFleetDriver) (fleetDefaultDiscountAmount ?: 0) else discountAmount

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // row 0: 排班 | 離線
        MainActionRow {
            MainActionButton(
                label = "排班",
                subLabel = if (inQueue) (queueZoneName ?: "") else null,
                subLabel2 = if (inQueue) "#${queuePosition ?: "-"}" else null,
                isActive = inQueue,
                onClick = onClickQueue,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MainActionButton(
                label = "離線",
                isActive = currentStatus == DriverAvailability.OFFLINE,
                activeColor = Color(0xFF78909C),
                onClick = { onStatusChange(DriverAvailability.OFFLINE) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        // row 1: 接單 | 休息
        MainActionRow {
            MainActionButton(
                label = "接單",
                isActive = currentStatus == DriverAvailability.AVAILABLE,
                activeColor = ButtonActiveGreen,
                onClick = { onStatusChange(DriverAvailability.AVAILABLE) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MainActionButton(
                label = "休息",
                isActive = currentStatus == DriverAvailability.REST,
                activeColor = StatusOrange,
                onClick = { onStatusChange(DriverAvailability.REST) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        // row 2: 訂單 | 收入
        MainActionRow {
            MainActionButton(
                label = "訂單",
                onClick = onClickOrders,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MainActionButton(
                label = "收入",
                onClick = onClickEarnings,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        // row 3: 我的 | 折扣
        MainActionRow {
            MainActionButton(
                label = "我的",
                onClick = onClickProfile,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MainActionButton(
                label = "折扣",
                subLabel = if (displayDiscount == 0) "全價" else "≤${displayDiscount}元",
                onClick = onClickDiscount,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun MainActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * 8 按鈕通用樣板：純大字（老人友善，不用圖示）
 *
 * 排版模式（依 subLabel / subLabel2 自動決定）：
 *   - 單行：32sp 巨大主標（離線/接單/休息/訂單/收入/我的）
 *   - 二行：26sp 主標 + 16sp 副標（折扣 / ≤20元）
 *   - 三行：22sp 主標 + 18sp 副標1 + 22sp 副標2 粗體（排班 / 花蓮車站 / #3）
 *
 * Active 時 activeColor 整片高亮 + 主標前綴 ✓（雙重視覺回饋取代原圖示）
 */
@Composable
private fun MainActionButton(
    label: String,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    subLabel2: String? = null,
    isActive: Boolean = false,
    activeColor: Color = ButtonActiveGreen,
    onClick: () -> Unit,
) {
    val mainFontSize = when {
        subLabel2 != null -> 22.sp
        subLabel != null -> 26.sp
        else -> 32.sp
    }

    Card(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) activeColor else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isActive) "✓ $label" else label,
                    fontSize = mainFontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White else DarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subLabel != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subLabel,
                        fontSize = if (subLabel2 != null) 18.sp else 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) Color.White.copy(alpha = 0.92f) else SubText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subLabel2 != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subLabel2,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else DarkText,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 登出確認對話框（CompactTopBar 長按返回箭頭觸發）
 */
@Composable
private fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("確定要登出嗎？", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = { Text("登出後需要重新用手機號碼登入。", fontSize = 16.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("登出", color = Color(0xFFD32F2F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 16.sp)
            }
        }
    )
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

/**
 * 班次狀態 banner — 顯示「上班中／離下班 X 分」或「不在班次時間」。
 * shifts 為空（24/7 在班）時整段不顯示，給沒設排班的司機保留乾淨畫面。
 *
 * mirror server-side 的 ShiftChecker 邏輯（util.ShiftChecker）。
 */
@Composable
private fun ShiftStatusBanner(shifts: List<com.hualien.taxidriver.domain.model.ShiftSlot>) {
    if (shifts.isEmpty()) return
    val active = shifts.any { it.isActive }
    if (!active) return

    val isOnShift = com.hualien.taxidriver.util.ShiftChecker.isOnShift(shifts)
    if (isOnShift) {
        val minutesLeft = com.hualien.taxidriver.util.ShiftChecker.minutesUntilShiftEnd(shifts)
        val timeLabel = minutesLeft?.let {
            val h = it / 60
            val m = it % 60
            if (h > 0) "離下班還有 $h 小時 $m 分" else "離下班還有 $m 分"
        } ?: ""
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2E7D32))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "✓ 上班中  $timeLabel",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        val minutesUntilStart = com.hualien.taxidriver.util.ShiftChecker.minutesUntilNextShiftStart(shifts)
        val nextLabel = minutesUntilStart?.let {
            val h = it / 60
            val m = it % 60
            if (h > 0) "（${h} 小時 ${m} 分後上班）" else "（${m} 分後上班）"
        } ?: ""
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE65100))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "⚠️ 不在班次時間，無法接單  $nextLabel",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
