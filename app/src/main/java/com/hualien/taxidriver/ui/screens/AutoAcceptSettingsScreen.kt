package com.hualien.taxidriver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.viewmodel.AutoAcceptViewModel
import kotlinx.coroutines.launch

/**
 * AI 自動接單設定畫面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoAcceptSettingsScreen(
    driverId: String,
    onBackClick: () -> Unit = {},
    viewModel: AutoAcceptViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()

    // 收集狀態
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // 載入資料
    LaunchedEffect(driverId) {
        viewModel.loadSettings(driverId)
        viewModel.loadStats(driverId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "AI 自動接單",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (isLoading && settings == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 錯誤提示
                error?.let { errorMsg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF44336)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(errorMsg, color = Color(0xFFC62828))
                        }
                    }
                }

                // 主開關卡片
                MainSwitchCard(
                    enabled = settings?.enabled ?: false,
                    onEnabledChange = { enabled ->
                        scope.launch {
                            viewModel.updateEnabled(driverId, enabled)
                        }
                    }
                )

                // 今日統計卡片
                stats?.today?.let { todayStats ->
                    TodayStatsCard(
                        autoAcceptCount = todayStats.autoAcceptCount,
                        manualAcceptCount = todayStats.manualAcceptCount,
                        blockedCount = todayStats.blockedCount
                    )
                }

                // 智慧模式設定
                if (settings?.enabled == true) {
                    SmartModeCard(
                        smartModeEnabled = settings?.smartModeEnabled ?: true,
                        autoAcceptThreshold = settings?.autoAcceptThreshold ?: 70,
                        onSmartModeChange = { enabled ->
                            scope.launch {
                                viewModel.updateSmartMode(driverId, enabled)
                            }
                        },
                        onThresholdChange = { threshold ->
                            scope.launch {
                                viewModel.updateThreshold(driverId, threshold)
                            }
                        }
                    )

                    // 條件設定
                    ConditionsCard(
                        maxPickupDistanceKm = settings?.maxPickupDistanceKm ?: 5.0,
                        minFareAmount = settings?.minFareAmount ?: 0,
                        minTripDistanceKm = settings?.minTripDistanceKm ?: 0.0,
                        onMaxDistanceChange = { distance ->
                            scope.launch {
                                viewModel.updateMaxDistance(driverId, distance)
                            }
                        },
                        onMinFareChange = { fare ->
                            scope.launch {
                                viewModel.updateMinFare(driverId, fare)
                            }
                        },
                        onMinTripDistanceChange = { distance ->
                            scope.launch {
                                viewModel.updateMinTripDistance(driverId, distance)
                            }
                        }
                    )

                    // 風控設定
                    RiskControlCard(
                        dailyLimit = settings?.dailyAutoAcceptLimit ?: 30,
                        cooldownMinutes = settings?.cooldownMinutes ?: 5,
                        consecutiveLimit = settings?.consecutiveLimit ?: 5,
                        onDailyLimitChange = { limit ->
                            scope.launch {
                                viewModel.updateDailyLimit(driverId, limit)
                            }
                        },
                        onCooldownChange = { minutes ->
                            scope.launch {
                                viewModel.updateCooldown(driverId, minutes)
                            }
                        },
                        onConsecutiveLimitChange = { limit ->
                            scope.launch {
                                viewModel.updateConsecutiveLimit(driverId, limit)
                            }
                        }
                    )

                    // 時段設定
                    ActiveHoursCard(
                        activeHours = settings?.activeHours ?: emptyList(),
                        onActiveHoursChange = { hours ->
                            scope.launch {
                                viewModel.updateActiveHours(driverId, hours)
                            }
                        }
                    )
                }

                // 使用說明
                InfoCard()
            }
        }
    }
}

@Composable
private fun MainSwitchCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (enabled) "AI 自動接單已開啟" else "AI 自動接單已關閉",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (enabled)
                    "系統將根據您的偏好自動接受符合條件的訂單"
                else
                    "開啟後，AI 將根據評分自動幫您接單",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onEnabledChange(!enabled) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) Color(0xFFF44336) else Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = if (enabled) "關閉自動接單" else "開啟自動接單",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TodayStatsCard(
    autoAcceptCount: Int,
    manualAcceptCount: Int,
    blockedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "今日統計",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "自動接單",
                    value = autoAcceptCount.toString(),
                    color = Color(0xFF4CAF50)
                )
                StatItem(
                    label = "手動接單",
                    value = manualAcceptCount.toString(),
                    color = Color(0xFF2196F3)
                )
                StatItem(
                    label = "已阻擋",
                    value = blockedCount.toString(),
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmartModeCard(
    smartModeEnabled: Boolean,
    autoAcceptThreshold: Int,
    onSmartModeChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "智慧模式",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 智慧模式開關
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "使用 AI 預測",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "根據歷史數據預測接單成功率",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = smartModeEnabled,
                    onCheckedChange = onSmartModeChange
                )
            }

            if (smartModeEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // 閾值設定
                Text(
                    text = "自動接單分數閾值：$autoAcceptThreshold 分",
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = autoAcceptThreshold.toFloat(),
                    onValueChange = { onThresholdChange(it.toInt()) },
                    valueRange = 50f..100f,
                    steps = 9
                )

                Text(
                    text = "分數越高，自動接單的標準越嚴格",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConditionsCard(
    maxPickupDistanceKm: Double,
    minFareAmount: Int,
    minTripDistanceKm: Double,
    onMaxDistanceChange: (Double) -> Unit,
    onMinFareChange: (Int) -> Unit,
    onMinTripDistanceChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "接單條件",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 最大接送距離
            ConditionSlider(
                label = "最大接送距離",
                value = maxPickupDistanceKm.toFloat(),
                valueText = "${String.format("%.1f", maxPickupDistanceKm)} 公里",
                range = 1f..20f,
                onValueChange = { onMaxDistanceChange(it.toDouble()) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 最低車資
            ConditionSlider(
                label = "最低車資",
                value = minFareAmount.toFloat(),
                valueText = "NT$ $minFareAmount",
                range = 0f..500f,
                onValueChange = { onMinFareChange(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 最短行程距離
            ConditionSlider(
                label = "最短行程距離",
                value = minTripDistanceKm.toFloat(),
                valueText = "${String.format("%.1f", minTripDistanceKm)} 公里",
                range = 0f..10f,
                onValueChange = { onMinTripDistanceChange(it.toDouble()) }
            )
        }
    }
}

@Composable
private fun ConditionSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 14.sp
            )
            Text(
                text = valueText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun RiskControlCard(
    dailyLimit: Int,
    cooldownMinutes: Int,
    consecutiveLimit: Int,
    onDailyLimitChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onConsecutiveLimitChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "風控設定",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 每日上限
            ConditionSlider(
                label = "每日自動接單上限",
                value = dailyLimit.toFloat(),
                valueText = "$dailyLimit 單",
                range = 5f..50f,
                onValueChange = { onDailyLimitChange(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 冷卻時間
            ConditionSlider(
                label = "連續自動接單冷卻時間",
                value = cooldownMinutes.toFloat(),
                valueText = "$cooldownMinutes 分鐘",
                range = 0f..30f,
                onValueChange = { onCooldownChange(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 連續接單上限
            ConditionSlider(
                label = "連續自動接單上限",
                value = consecutiveLimit.toFloat(),
                valueText = "$consecutiveLimit 單",
                range = 1f..10f,
                onValueChange = { onConsecutiveLimitChange(it.toInt()) }
            )
        }
    }
}

@Composable
private fun ActiveHoursCard(
    activeHours: List<Int>,
    onActiveHoursChange: (List<Int>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "啟用時段",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展開")
                }
            }

            Text(
                text = if (activeHours.isEmpty()) "全天候啟用" else "已設定 ${activeHours.size} 個時段",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // 時段選擇網格
                val timeSlots = listOf(
                    0 to "00-06",
                    6 to "06-09",
                    9 to "09-12",
                    12 to "12-15",
                    15 to "15-18",
                    18 to "18-21",
                    21 to "21-24"
                )

                Column {
                    timeSlots.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (startHour, label) ->
                                val endHour = if (startHour == 21) 24 else startHour + 3
                                val hoursInSlot = (startHour until endHour).toList()
                                val isSelected = hoursInSlot.all { it in activeHours } || activeHours.isEmpty()

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val newHours = if (activeHours.isEmpty()) {
                                            // 全選 -> 取消這個時段
                                            (0..23).filter { it !in hoursInSlot }
                                        } else if (isSelected) {
                                            // 取消選擇
                                            activeHours.filter { it !in hoursInSlot }
                                        } else {
                                            // 加入選擇
                                            (activeHours + hoursInSlot).distinct().sorted()
                                        }
                                        onActiveHoursChange(newHours.ifEmpty { emptyList() })
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { onActiveHoursChange(emptyList()) }) {
                        Text("全選")
                    }
                    TextButton(onClick = { onActiveHoursChange((6..22).toList()) }) {
                        Text("日間 (6-22)")
                    }
                    TextButton(onClick = { onActiveHoursChange((0..5).toList() + (22..23).toList()) }) {
                        Text("夜間")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "使用說明",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = buildString {
                    appendLine("AI 自動接單會根據以下因素計算分數：")
                    appendLine("1. 接送距離：距離越近，分數越高")
                    appendLine("2. 預估車資：車資越高，分數越高")
                    appendLine("3. 行程距離：符合您偏好的行程")
                    appendLine("4. 歷史數據：您過去的接單模式")
                    appendLine()
                    append("當分數達到您設定的閾值時，系統會自動接單。風控機制可防止過度疲勞。")
                },
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}
