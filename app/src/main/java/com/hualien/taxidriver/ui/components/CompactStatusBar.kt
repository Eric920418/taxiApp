package com.hualien.taxidriver.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualien.taxidriver.domain.model.QueueMyStatus
import com.hualien.taxidriver.domain.model.QueueZone

/**
 * 排班區選擇 sheet — 列出所有 zone，未排班可加入、已排班可退出。
 * v1.3.0 起由 HomeScreen 直接呼叫（原 CompactStatusBar 父元件已移除，改用主畫面 8 按鈕）。
 */
@Composable
internal fun QueueZoneSheetContent(
    zones: List<QueueZone>,
    myStatus: QueueMyStatus?,
    autoQueueAfterTrip: Boolean,
    onToggleAutoQueue: (Boolean) -> Unit,
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
            text = if (inQueue)
                "您目前已加入「${myStatus?.zoneName ?: ""}」排班，順位 #${myStatus?.position ?: "-"}（已 ${myStatus?.minutesInQueue ?: 0} 分鐘）"
            else
                "選擇要加入的排班區（需 GPS 在區域內）",
            fontSize = 13.sp,
            color = Color(0xFF666666),
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 完成訂單後自動排班開關（司機自控；排班區只在完成訂單/手動排班時判斷一次，之後不隨 GPS 換區）
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "完成訂單後自動排班",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0),
                    )
                    Text(
                        text = "開啟後，每趟完成會依你目前位置自動排入該區；不在任何排班區則維持自由。",
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = autoQueueAfterTrip,
                    onCheckedChange = onToggleAutoQueue,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

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
internal fun DiscountPreferenceSheetContent(
    currentAmount: Int,
    fleetPartnerName: String? = null,
    onChange: (Int) -> Unit,
) {
    val tiers = listOf(0, 10, 20, 30, 40)
    val isFleetDriver = fleetPartnerName != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (isFleetDriver) "🔒 車隊統一折扣" else "💰 願意給客人折扣",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFleetDriver) Color(0xFF455A64) else Color(0xFFE65100),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isFleetDriver)
                "您屬於「${fleetPartnerName}」，折扣由車隊統一設定為 NT$ $currentAmount 元，不可自行調整。如需更改請聯絡車隊。"
            else
                "您能接受的最高折扣（NT$ 元）。讓利多的優先派單，0 = 全價單也接。",
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
                    enabled = !isFleetDriver,
                    onClick = { if (!selected && !isFleetDriver) onChange(amt) },
                    label = {
                        Text(
                            text = if (amt == 0) "不打折" else "≤${amt}元",
                            fontSize = 13.sp,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isFleetDriver) Color(0xFF607D8B) else Color(0xFFFF6F00),
                        selectedLabelColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
