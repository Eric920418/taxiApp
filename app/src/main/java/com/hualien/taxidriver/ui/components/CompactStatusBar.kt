package com.hualien.taxidriver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualien.taxidriver.domain.model.QueueMyStatus
import com.hualien.taxidriver.domain.model.QueueZone
import kotlinx.coroutines.launch

/**
 * GoGoCha 司機端緊湊狀態列
 *
 * 取代原本三層浮卡（班次 / 抽成 / 排班），改為一行兩塊小卡：
 *   - 左：排班區（未加入顯示「加入排班」、已加入顯示「✓ zone | 已 N 分」）
 *   - 右：折扣接受度（顯示「≤ N 元」或「全價單也接」）
 *
 * 點擊任一塊展開對應 ModalBottomSheet 顯示完整選項。
 * 大幅減少首頁垂直佔用空間。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactStatusBar(
    queueZones: List<QueueZone>,
    myQueueStatus: QueueMyStatus?,
    currentDiscountAmount: Int,
    onJoinQueue: (zoneId: String) -> Unit,
    onLeaveQueue: () -> Unit,
    onChangeDiscount: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    var showDiscountSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val discountSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val inQueue = myQueueStatus?.inQueue == true

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 左：排班
        CompactBlock(
            modifier = Modifier.weight(1f),
            containerColor = if (inQueue) Color(0xFFFFF3E0) else Color(0xFFE3F2FD),
            titleColor = if (inQueue) Color(0xFFE65100) else Color(0xFF1565C0),
            icon = "📍",
            title = if (inQueue) "排班中" else "排班",
            subtitle = when {
                inQueue -> "${myQueueStatus?.zoneName ?: ""}｜已 ${myQueueStatus?.minutesInQueue ?: 0} 分"
                queueZones.isEmpty() -> "尚無可選區域"
                else -> "點擊選擇區域"
            },
            onClick = { showQueueSheet = true },
            enabled = queueZones.isNotEmpty() || inQueue,
        )

        // 右：折扣
        CompactBlock(
            modifier = Modifier.weight(1f),
            containerColor = Color(0xFFFFF8E1),
            titleColor = Color(0xFFE65100),
            icon = "💰",
            title = if (currentDiscountAmount == 0) "不打折" else "≤ $currentDiscountAmount 元",
            subtitle = if (currentDiscountAmount == 0) "全價單也接" else "讓利多的優先派",
            onClick = { showDiscountSheet = true },
        )
    }

    // ============ 排班 sheet ============
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = queueSheetState,
        ) {
            QueueZoneSheetContent(
                zones = queueZones,
                myStatus = myQueueStatus,
                onJoin = { zoneId ->
                    onJoinQueue(zoneId)
                    scope.launch { queueSheetState.hide() }
                        .invokeOnCompletion { showQueueSheet = false }
                },
                onLeave = {
                    onLeaveQueue()
                    scope.launch { queueSheetState.hide() }
                        .invokeOnCompletion { showQueueSheet = false }
                },
            )
        }
    }

    // ============ 折扣 sheet ============
    if (showDiscountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDiscountSheet = false },
            sheetState = discountSheetState,
        ) {
            DiscountPreferenceSheetContent(
                currentAmount = currentDiscountAmount,
                onChange = { amt ->
                    onChangeDiscount(amt)
                    scope.launch { discountSheetState.hide() }
                        .invokeOnCompletion { showDiscountSheet = false }
                },
            )
        }
    }
}

@Composable
private fun CompactBlock(
    modifier: Modifier,
    containerColor: Color,
    titleColor: Color,
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        modifier = modifier
            .height(64.dp)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor else Color(0xFFF5F5F5),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) titleColor else Color(0xFF999999),
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (enabled) Color(0xFF666666) else Color(0xFFAAAAAA),
                )
            }
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "展開",
                tint = if (enabled) titleColor else Color(0xFF999999),
            )
        }
    }
}

/**
 * 排班區選擇 sheet — 列出所有 zone，未排班可加入、已排班可退出。
 */
@Composable
private fun QueueZoneSheetContent(
    zones: List<QueueZone>,
    myStatus: QueueMyStatus?,
    onJoin: (zoneId: String) -> Unit,
    onLeave: () -> Unit,
) {
    val inQueue = myStatus?.inQueue == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = "📍 排班區",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1565C0),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (inQueue) "您目前已加入「${myStatus?.zoneName ?: ""}」排班，已 ${myStatus?.minutesInQueue ?: 0} 分鐘"
                   else "選擇要加入的排班區（需 GPS 在區域內）",
            fontSize = 13.sp,
            color = Color(0xFF666666),
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (inQueue) {
            Button(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
            ) {
                Text("退出排班", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (zones.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("目前沒有可選的排班區", color = Color(0xFF999999), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(zones) { z ->
                    val isMyZone = inQueue && myStatus?.zoneId == z.zoneId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !inQueue) { onJoin(z.zoneId) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMyZone) Color(0xFFFFF3E0) else Color(0xFFF8F9FA),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isMyZone) "✓ ${z.name}" else z.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMyZone) Color(0xFFE65100) else Color(0xFF333333),
                                )
                                Text(
                                    text = "目前 ${z.activeDrivers} 位司機排班",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666),
                                )
                            }
                            if (!inQueue) {
                                OutlinedButton(onClick = { onJoin(z.zoneId) }) {
                                    Text("加入", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 折扣偏好 sheet — 5 段 chip (0/10/20/30/40 元)
 */
@Composable
private fun DiscountPreferenceSheetContent(
    currentAmount: Int,
    onChange: (Int) -> Unit,
) {
    val tiers = listOf(0, 10, 20, 30, 40)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = "💰 願意給客人折扣",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE65100),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "您能接受的最高折扣（NT$ 元）。讓利多的優先派單，0 = 全價單也接。",
            fontSize = 13.sp,
            color = Color(0xFF666666),
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tiers.forEach { amt ->
                val selected = amt == currentAmount
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onChange(amt) },
                    label = {
                        Text(
                            text = if (amt == 0) "不打折" else "≤${amt}元",
                            fontSize = 13.sp,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 目前選擇預覽
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (currentAmount == 0)
                        "目前設定：全價單也接"
                    else
                        "目前設定：最多接受讓利 NT$ $currentAmount 元",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (currentAmount) {
                        0 -> "可接所有訂單（不限折扣）"
                        10 -> "可接訂單折扣 0~10 元"
                        20 -> "可接訂單折扣 0~20 元"
                        30 -> "可接訂單折扣 0~30 元"
                        40 -> "可接訂單折扣 0~40 元（最多）"
                        else -> ""
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
