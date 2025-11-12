package com.hualien.taxidriver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.domain.model.OrderStatus
import com.hualien.taxidriver.utils.AddressUtils
import com.hualien.taxidriver.viewmodel.DriverOrderHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 訂單列表畫面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(driverId: String) {
    val viewModel: DriverOrderHistoryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("全部", "進行中", "已完成", "已取消")

    // 初始化加載訂單
    LaunchedEffect(driverId) {
        viewModel.loadOrderHistory(driverId)
    }

    // 根據選中的標籤過濾訂單
    val filteredOrders = remember(uiState.orders, selectedTab) {
        when (selectedTab) {
            0 -> uiState.orders // 全部
            1 -> uiState.orders.filter {
                it.status in listOf(OrderStatus.OFFERED, OrderStatus.ACCEPTED, OrderStatus.ARRIVED, OrderStatus.ON_TRIP, OrderStatus.SETTLING)
            }
            2 -> uiState.orders.filter { it.status == OrderStatus.DONE }
            3 -> uiState.orders.filter { it.status == OrderStatus.CANCELLED }
            else -> uiState.orders
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的訂單") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 分頁標籤
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // 訂單列表
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading -> {
                        // 加載中
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.error != null -> {
                        // 錯誤狀態
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "錯誤",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.error ?: "發生錯誤",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadOrderHistory(driverId) }) {
                                Text("重試")
                            }
                        }
                    }
                    filteredOrders.isEmpty() -> {
                        // 空狀態
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "訂單",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = when (selectedTab) {
                                    0 -> "暫無訂單"
                                    1 -> "目前沒有進行中的訂單"
                                    2 -> "尚無已完成訂單"
                                    else -> "無取消訂單"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "完成的訂單會顯示在這裡",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        // 訂單列表
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredOrders) { order ->
                                DriverOrderCard(order = order)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 司機端訂單卡片
 */
@Composable
fun DriverOrderCard(order: Order) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val statusColor = when (order.status) {
        OrderStatus.DONE -> Color(0xFF4CAF50)
        OrderStatus.CANCELLED -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.primary
    }
    val statusText = when (order.status) {
        OrderStatus.IDLE -> "閒置"
        OrderStatus.WAITING -> "等待中"
        OrderStatus.OFFERED -> "派單中"
        OrderStatus.ACCEPTED -> "已接單"
        OrderStatus.ARRIVED -> "已到達"
        OrderStatus.ON_TRIP -> "行程中"
        OrderStatus.SETTLING -> "結算中"
        OrderStatus.DONE -> "已完成"
        OrderStatus.CANCELLED -> "已取消"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 頂部：訂單編號和狀態
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "訂單 ${order.orderId.takeLast(8)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 乘客信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "乘客",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = order.passengerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (order.passengerPhone != null) {
                    Text(
                        text = "• ${order.passengerPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 上車點
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "上車點",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "上車點",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = AddressUtils.shortenAddress(order.pickup.address ?: "未知地址", 35),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 目的地（如果有）
            order.destination?.let { dest ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "目的地",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "目的地",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AddressUtils.shortenAddress(dest.address ?: "未知地址", 35),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 底部：車資和時間
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 時間
                Text(
                    text = dateFormat.format(Date(order.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 車資（如果有）
                if (order.fare != null) {
                    Text(
                        text = "NT$ ${order.fare.meterAmount ?: 0}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
