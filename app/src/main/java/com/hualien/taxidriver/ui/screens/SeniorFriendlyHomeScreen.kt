package com.hualien.taxidriver.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.domain.model.OrderStatus
import com.hualien.taxidriver.service.LocationService
import com.hualien.taxidriver.ui.components.FareDialog
import com.hualien.taxidriver.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

/**
 * 針對中老年人優化的主頁面
 * 特點：
 * 1. 更大的字體和按鈕
 * 2. 更清晰的視覺層次
 * 3. 簡化的介面元素
 * 4. 高對比度顏色
 * 5. 操作確認機制
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeniorFriendlyHomeScreen(
    driverId: String,
    driverName: String,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 操作確認對話框狀態
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<() -> Unit>({}) }
    var confirmMessage by remember { mutableStateOf("") }

    // 處理錯誤訊息
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // 車資對話框狀態
    var showFareDialog by remember { mutableStateOf(false) }
    var currentOrderIdForFare by remember { mutableStateOf<String?>(null) }

    // 位置權限
    var hasLocationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // 啟動時請求權限
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // 建立 WebSocket 連接（司機上線）並設置初始狀態
    LaunchedEffect(Unit) {
        viewModel.connectWebSocket(driverId)
        // 自動設置為可接單狀態
        viewModel.updateDriverStatus(driverId, DriverAvailability.AVAILABLE)
    }

    // 清理資源
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnectWebSocket()
        }
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // 頂部狀態欄 - 更大更清晰
        SeniorFriendlyStatusBar(
            driverStatus = uiState.driverStatus,
            driverName = driverName
        )

        // 主要內容區域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 當前訂單 - 簡化顯示
            uiState.currentOrder?.let { order ->
                SeniorFriendlyOrderCard(
                    order = order,
                    onAccept = {
                        confirmMessage = "確定要接受此訂單嗎？"
                        confirmAction = {
                            viewModel.acceptOrder(order.orderId, driverId, driverName)
                        }
                        showConfirmDialog = true
                    },
                    onReject = {
                        confirmMessage = "確定要拒絕此訂單嗎？"
                        confirmAction = {
                            viewModel.rejectOrder(order.orderId, driverId)
                        }
                        showConfirmDialog = true
                    },
                    onMarkArrived = {
                        confirmMessage = "確認已到達上車點？"
                        confirmAction = {
                            viewModel.markArrived(order.orderId)
                        }
                        showConfirmDialog = true
                    },
                    onStartTrip = {
                        confirmMessage = "確認開始行程？"
                        confirmAction = {
                            viewModel.startTrip(order.orderId)
                        }
                        showConfirmDialog = true
                    },
                    onEndTrip = {
                        confirmMessage = "確認結束行程？"
                        confirmAction = {
                            viewModel.endTrip(order.orderId)
                        }
                        showConfirmDialog = true
                    },
                    onSubmitFare = {
                        currentOrderIdForFare = order.orderId
                        showFareDialog = true
                    },
                    onNavigate = {
                        val uri = Uri.parse(
                            "google.navigation:q=${order.pickup.latitude},${order.pickup.longitude}"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "請先安裝Google Maps", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isLoading = uiState.isLoading
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 狀態切換 - 大按鈕設計
            SeniorFriendlyStatusControl(
                currentStatus = uiState.driverStatus,
                onStatusChange = { newStatus ->
                    when (newStatus) {
                        DriverAvailability.AVAILABLE -> {
                            confirmMessage = "確認要開始接單嗎？"
                            confirmAction = {
                                viewModel.updateDriverStatus(driverId, newStatus)
                            }
                            showConfirmDialog = true
                        }
                        DriverAvailability.REST -> {
                            confirmMessage = "確認要暫停接單嗎？"
                            confirmAction = {
                                viewModel.updateDriverStatus(driverId, newStatus)
                            }
                            showConfirmDialog = true
                        }
                        DriverAvailability.OFFLINE -> {
                            confirmMessage = "確認要結束工作嗎？"
                            confirmAction = {
                                viewModel.updateDriverStatus(driverId, newStatus)
                            }
                            showConfirmDialog = true
                        }
                        else -> {
                            viewModel.updateDriverStatus(driverId, newStatus)
                        }
                    }
                }
            )
        }
    }

    // 確認對話框
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    "操作確認",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    confirmMessage,
                    fontSize = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmAction()
                        showConfirmDialog = false
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        "確定",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(60.dp)
                ) {
                    Text(
                        "取消",
                        fontSize = 20.sp
                    )
                }
            }
        )
    }

    // 車資對話框
    if (showFareDialog) {
        FareDialog(
            onDismiss = { showFareDialog = false },
            onConfirm = { meterAmount ->
                currentOrderIdForFare?.let { orderId ->
                    viewModel.submitFare(orderId, driverId, meterAmount)
                }
                showFareDialog = false
                currentOrderIdForFare = null
            }
        )
    }
}

/**
 * 針對中老年人優化的狀態欄
 */
@Composable
fun SeniorFriendlyStatusBar(
    driverStatus: DriverAvailability,
    driverName: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (driverStatus) {
                DriverAvailability.OFFLINE -> Color(0xFFE0E0E0)
                DriverAvailability.REST -> Color(0xFFFFF9C4)
                DriverAvailability.AVAILABLE -> Color(0xFFC8E6C9)
                DriverAvailability.ON_TRIP -> Color(0xFFBBDEFB)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (driverStatus) {
                    DriverAvailability.OFFLINE -> "離線中"
                    DriverAvailability.REST -> "休息中"
                    DriverAvailability.AVAILABLE -> "可接單"
                    DriverAvailability.ON_TRIP -> "載客中"
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = when (driverStatus) {
                    DriverAvailability.OFFLINE -> Color(0xFF616161)
                    DriverAvailability.REST -> Color(0xFFF57C00)
                    DriverAvailability.AVAILABLE -> Color(0xFF2E7D32)
                    DriverAvailability.ON_TRIP -> Color(0xFF1565C0)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = driverName,
                fontSize = 20.sp,
                color = Color(0xFF424242)
            )
        }
    }
}

/**
 * 簡化的訂單卡片
 */
@Composable
fun SeniorFriendlyOrderCard(
    order: com.hualien.taxidriver.domain.model.Order,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onMarkArrived: () -> Unit,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit,
    onSubmitFare: () -> Unit,
    onNavigate: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 訂單狀態標題
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "當前訂單",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (order.status) {
                        OrderStatus.OFFERED -> Color(0xFFFF9800)
                        OrderStatus.ACCEPTED -> Color(0xFF2196F3)
                        OrderStatus.ARRIVED -> Color(0xFF9C27B0)
                        OrderStatus.ON_TRIP -> Color(0xFF4CAF50)
                        OrderStatus.SETTLING -> Color(0xFFFF5722)
                        else -> Color.Gray
                    }
                ) {
                    Text(
                        text = when (order.status) {
                            OrderStatus.OFFERED -> "新訂單"
                            OrderStatus.ACCEPTED -> "已接單"
                            OrderStatus.ARRIVED -> "已到達"
                            OrderStatus.ON_TRIP -> "行程中"
                            OrderStatus.SETTLING -> "結算中"
                            else -> order.status.name
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                thickness = 2.dp
            )

            // 乘客資訊 - 放大顯示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "乘客",
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF1976D2)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = order.passengerName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "電話",
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF1976D2)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = order.passengerPhone ?: "無電話",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 上車點 - 更清晰的顯示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "上車點",
                        modifier = Modifier.size(36.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "上車點",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = order.pickup.address ?: "未提供地址",
                            fontSize = 20.sp,
                            lineHeight = 28.sp
                        )
                    }
                }
            }

            // 目的地（如果有）
            order.destination?.let { dest ->
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "目的地",
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "目的地",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dest.address ?: "未提供地址",
                                fontSize = 20.sp,
                                lineHeight = 28.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 操作按鈕 - 超大按鈕
            when (order.status) {
                OrderStatus.OFFERED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                            enabled = !isLoading,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF44336)
                            )
                        ) {
                            Text(
                                "拒絕",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = onAccept,
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    "接受",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                OrderStatus.ACCEPTED -> {
                    // 導航按鈕
                    Button(
                        onClick = onNavigate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "導航",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "導航到上車點",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onMarkArrived,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0)
                        )
                    ) {
                        Text(
                            "已到達上車點",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OrderStatus.ARRIVED -> {
                    Button(
                        onClick = onStartTrip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "開始",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "開始行程",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OrderStatus.ON_TRIP -> {
                    Button(
                        onClick = onEndTrip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "結束",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "結束行程",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OrderStatus.SETTLING -> {
                    Button(
                        onClick = onSubmitFare,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Text(
                            "提交車資",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                else -> {}
            }
        }
    }
}

/**
 * 簡化的狀態控制按鈕
 */
@Composable
fun SeniorFriendlyStatusControl(
    currentStatus: DriverAvailability,
    onStatusChange: (DriverAvailability) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "工作狀態切換",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // 開始接單按鈕
        Button(
            onClick = { onStatusChange(DriverAvailability.AVAILABLE) },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentStatus == DriverAvailability.AVAILABLE)
                    Color(0xFF2E7D32) else Color(0xFF81C784)
            ),
            enabled = currentStatus != DriverAvailability.ON_TRIP
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "開始",
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "開始接單",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 暫停休息按鈕
        Button(
            onClick = { onStatusChange(DriverAvailability.REST) },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentStatus == DriverAvailability.REST)
                    Color(0xFFE65100) else Color(0xFFFFB74D)
            ),
            enabled = currentStatus != DriverAvailability.ON_TRIP
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "休息",
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "暫停休息",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 結束工作按鈕
        Button(
            onClick = { onStatusChange(DriverAvailability.OFFLINE) },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentStatus == DriverAvailability.OFFLINE)
                    Color(0xFF424242) else Color(0xFF9E9E9E)
            ),
            enabled = currentStatus != DriverAvailability.ON_TRIP
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "離線",
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "結束工作",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}