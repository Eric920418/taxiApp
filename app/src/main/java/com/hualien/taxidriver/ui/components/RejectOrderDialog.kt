package com.hualien.taxidriver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 拒單原因
 */
enum class RejectionReason(val code: String, val displayName: String, val description: String) {
    TOO_FAR("TOO_FAR", "距離太遠", "到上車點距離超出接受範圍"),
    LOW_FARE("LOW_FARE", "車資太低", "預估車資不符合期望"),
    UNWANTED_AREA("UNWANTED_AREA", "不想去該區域", "目的地區域不在服務範圍"),
    OFF_DUTY("OFF_DUTY", "準備下班", "即將結束今日營業"),
    OTHER("OTHER", "其他原因", "其他未列出的原因")
}

/**
 * 拒單原因選擇對話框
 * 智能派單 V2：強制選擇拒單原因
 */
@Composable
fun RejectOrderDialog(
    orderId: String,
    distanceToPickup: Double? = null,
    estimatedFare: Int? = null,
    onDismiss: () -> Unit,
    onConfirm: (reason: RejectionReason) -> Unit
) {
    var selectedReason by remember { mutableStateOf<RejectionReason?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("選擇拒單原因")
        },
        text = {
            Column {
                // 訂單資訊摘要
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "訂單資訊",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        if (distanceToPickup != null) {
                            Text(
                                text = "到客人距離: ${String.format("%.1f", distanceToPickup)} 公里",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (estimatedFare != null) {
                            Text(
                                text = "預估車資: NT$ $estimatedFare",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "請選擇拒單原因（必填）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 拒單原因選項
                RejectionReason.entries.forEach { reason ->
                    ReasonOption(
                        reason = reason,
                        isSelected = selectedReason == reason,
                        onClick = { selectedReason = reason }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedReason?.let { onConfirm(it) }
                },
                enabled = selectedReason != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("確認拒單")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 拒單原因選項卡片
 */
@Composable
private fun ReasonOption(
    reason: RejectionReason,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.outlinedCardColors(
            containerColor = backgroundColor
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderColor)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 選擇指示器
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已選擇",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reason.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = reason.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 快速拒單確認對話框（不需選擇原因，用於舊版兼容）
 */
@Composable
fun QuickRejectDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("確認拒單") },
        text = { Text("確定要拒絕這筆訂單嗎？") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("拒單")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
