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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.domain.model.OrderStatus
import com.hualien.taxidriver.service.LocationService
import com.hualien.taxidriver.ui.components.FareDialog
import com.hualien.taxidriver.viewmodel.HomeViewModel

/**
 * 主頁面 - 司機狀態 + 訂單管理
 */
@Composable
fun HomeScreen(
    driverId: String,
    driverName: String,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 司機狀態卡片
        DriverStatusCard(
            driverStatus = uiState.driverStatus,
            driverName = driverName
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 今日統計卡片
        TodayStatsCard()

        Spacer(modifier = Modifier.height(16.dp))

        // 當前訂單卡片
        uiState.currentOrder?.let { order ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                        Text(
                            text = "🚗 當前訂單",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (order.status) {
                                OrderStatus.OFFERED -> "新訂單"
                                OrderStatus.ACCEPTED -> "已接單"
                                OrderStatus.ARRIVED -> "已到達"
                                OrderStatus.ON_TRIP -> "行程中"
                                OrderStatus.SETTLING -> "結算中"
                                else -> order.status.name
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 乘客資訊
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "乘客",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = order.passengerName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "電話",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = order.passengerPhone ?: "未提供電話",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 上車點
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "上車點",
                                    tint = Color(0xFF4CAF50)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "上車點",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = order.pickup.address ?: "未提供地址",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 目的地
                    order.destination?.let { dest ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "目的地",
                                        tint = Color(0xFFF44336)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "目的地",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = dest.address ?: "未提供地址",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 導航按鈕
                    if (order.status == OrderStatus.ACCEPTED || order.status == OrderStatus.ARRIVED) {
                        Button(
                            onClick = {
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2)
                            )
                        ) {
                            Text("🗺️ 開始導航到上車點")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 訂單操作按鈕
                    when (order.status) {
                        OrderStatus.OFFERED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.rejectOrder(order.orderId, driverId)
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isLoading
                                ) {
                                    Text("拒絕")
                                }
                                Button(
                                    onClick = {
                                        viewModel.acceptOrder(order.orderId, driverId, driverName)
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isLoading
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text("接受")
                                    }
                                }
                            }
                        }

                        OrderStatus.ACCEPTED -> {
                            Button(
                                onClick = { viewModel.markArrived(order.orderId) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoading
                            ) {
                                Text("已到達上車點")
                            }
                        }

                        OrderStatus.ARRIVED -> {
                            Button(
                                onClick = { viewModel.startTrip(order.orderId) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoading
                            ) {
                                Text("開始行程")
                            }
                        }

                        OrderStatus.ON_TRIP -> {
                            Button(
                                onClick = { viewModel.endTrip(order.orderId) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoading
                            ) {
                                Text("結束行程")
                            }
                        }

                        OrderStatus.SETTLING -> {
                            Button(
                                onClick = {
                                    currentOrderIdForFare = order.orderId
                                    showFareDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoading
                            ) {
                                Text("提交車資")
                            }
                        }

                        else -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 狀態切換按鈕
        StatusControlButtons(
            currentStatus = uiState.driverStatus,
            onStatusChange = { newStatus ->
                viewModel.updateDriverStatus(driverId, newStatus)
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
 * 司機狀態卡片
 */
@Composable
fun DriverStatusCard(
    driverStatus: DriverAvailability,
    driverName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (driverStatus) {
                DriverAvailability.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant
                DriverAvailability.REST -> MaterialTheme.colorScheme.tertiaryContainer
                DriverAvailability.AVAILABLE -> MaterialTheme.colorScheme.primaryContainer
                DriverAvailability.ON_TRIP -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "目前狀態",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (driverStatus) {
                    DriverAvailability.OFFLINE -> "🔴 離線"
                    DriverAvailability.REST -> "🟡 休息中"
                    DriverAvailability.AVAILABLE -> "🟢 可接單"
                    DriverAvailability.ON_TRIP -> "🔵 載客中"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = driverName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 今日統計卡片
 */
@Composable
fun TodayStatsCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 今日統計",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "訂單數", value = "0")
                StatItem(label = "收入", value = "NT$ 0")
                StatItem(label = "里程", value = "0 km")
            }
        }
    }
}

/**
 * 統計項目
 */
@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 狀態控制按鈕
 */
@Composable
fun StatusControlButtons(
    currentStatus: DriverAvailability,
    onStatusChange: (DriverAvailability) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { onStatusChange(DriverAvailability.OFFLINE) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (currentStatus == DriverAvailability.OFFLINE)
                    MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
        ) {
            Text("離線")
        }

        OutlinedButton(
            onClick = { onStatusChange(DriverAvailability.REST) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (currentStatus == DriverAvailability.REST)
                    MaterialTheme.colorScheme.tertiaryContainer
                else Color.Transparent
            )
        ) {
            Text("休息")
        }

        Button(
            onClick = { onStatusChange(DriverAvailability.AVAILABLE) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentStatus == DriverAvailability.AVAILABLE)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text("可接單")
        }
    }
}
