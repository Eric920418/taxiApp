package com.hualien.taxidriver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.data.remote.dto.DailyBreakdown
import com.hualien.taxidriver.data.remote.dto.EarningsOrder
import com.hualien.taxidriver.data.remote.dto.WeeklyBreakdown
import com.hualien.taxidriver.viewmodel.EarningsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 收入統計畫面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsScreen(driverId: String) {
    val viewModel: EarningsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selectedPeriod by remember { mutableIntStateOf(0) }
    val periods = listOf("今日", "本週", "本月")

    // 初始化加載
    LaunchedEffect(driverId) {
        viewModel.loadEarnings(driverId, "today")
    }

    // 切換期間時重新加載
    LaunchedEffect(selectedPeriod) {
        viewModel.switchPeriod(driverId, selectedPeriod)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收入統計") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.switchPeriod(driverId, selectedPeriod) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 時間選擇
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        periods.forEachIndexed { index, period ->
                            FilterChip(
                                selected = selectedPeriod == index,
                                onClick = { selectedPeriod = index },
                                label = { Text(period) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 錯誤提示
                if (uiState.error != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "錯誤",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = uiState.error ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.switchPeriod(driverId, selectedPeriod) }) {
                                    Text("重試")
                                }
                            }
                        }
                    }
                }

                // 主要統計卡片
                item {
                    EarningsSummaryCard(
                        period = periods[selectedPeriod],
                        totalAmount = uiState.totalAmount,
                        orderCount = uiState.orderCount,
                        totalDistance = uiState.totalDistance,
                        totalDuration = uiState.totalDuration,
                        averageFare = uiState.averageFare
                    )
                }

                // 詳細列表標題
                item {
                    Text(
                        text = when (selectedPeriod) {
                            0 -> "今日訂單明細"
                            1 -> "每日收入統計"
                            2 -> "每週收入統計"
                            else -> "明細"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 根據期間顯示不同內容
                when (selectedPeriod) {
                    0 -> {
                        // 今日訂單列表
                        if (uiState.todayOrders.isEmpty() && !uiState.isLoading) {
                            item {
                                EmptyStateCard(message = "今日尚無完成訂單")
                            }
                        } else {
                            items(uiState.todayOrders) { order ->
                                TodayOrderCard(order = order)
                            }
                        }
                    }
                    1 -> {
                        // 每日統計
                        if (uiState.dailyBreakdown.isEmpty() && !uiState.isLoading) {
                            item {
                                EmptyStateCard(message = "本週尚無收入記錄")
                            }
                        } else {
                            items(uiState.dailyBreakdown) { daily ->
                                DailyBreakdownCard(daily = daily)
                            }
                        }
                    }
                    2 -> {
                        // 每週統計
                        if (uiState.weeklyBreakdown.isEmpty() && !uiState.isLoading) {
                            item {
                                EmptyStateCard(message = "本月尚無收入記錄")
                            }
                        } else {
                            items(uiState.weeklyBreakdown) { weekly ->
                                WeeklyBreakdownCard(weekly = weekly)
                            }
                        }
                    }
                }

                // 底部留白
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

/**
 * 收入摘要卡片
 */
@Composable
fun EarningsSummaryCard(
    period: String,
    totalAmount: Int,
    orderCount: Int,
    totalDistance: Double,
    totalDuration: Double,
    averageFare: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${period}收入",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "NT$ $totalAmount",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (averageFare > 0) {
                Text(
                    text = "平均每單 NT$ ${String.format("%.0f", averageFare)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    value = "$orderCount",
                    label = "訂單數"
                )
                StatItem(
                    icon = Icons.Default.Place,
                    value = String.format("%.1f km", totalDistance),
                    label = "總里程"
                )
                StatItem(
                    icon = Icons.Default.DateRange,
                    value = String.format("%.1f h", totalDuration),
                    label = "總時長"
                )
            }
        }
    }
}

/**
 * 統計項目
 */
@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 今日訂單卡片
 */
@Composable
fun TodayOrderCard(order: EarningsOrder) {
    val isoFormat = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val displayFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = order.completedAt?.let { dateStr ->
        try {
            isoFormat.parse(dateStr)?.let { displayFormat.format(it) }
        } catch (e: Exception) {
            null
        }
    } ?: "--:--"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "訂單 ${order.orderId.takeLast(6)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    order.distance?.let {
                        Text(
                            text = String.format("%.1f km", it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = "NT$ ${order.fare ?: 0}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

/**
 * 每日統計卡片
 */
@Composable
fun DailyBreakdownCard(daily: DailyBreakdown) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = daily.date,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${daily.orders} 筆訂單",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "NT$ ${daily.amount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

/**
 * 每週統計卡片
 */
@Composable
fun WeeklyBreakdownCard(weekly: WeeklyBreakdown) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = weekly.week,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${weekly.orders} 筆訂單",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "NT$ ${weekly.amount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

/**
 * 空狀態卡片
 */
@Composable
fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "無資料",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
