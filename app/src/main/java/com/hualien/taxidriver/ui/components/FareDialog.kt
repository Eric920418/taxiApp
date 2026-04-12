package com.hualien.taxidriver.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.max

/**
 * 車資確認對話框（預填預估車資，可修改後一鍵送出）
 * 支援愛心卡補貼明細顯示
 */
@Composable
fun FareDialog(
    onDismiss: () -> Unit,
    onConfirm: (meterAmount: Int, photoUri: Uri?) -> Unit,
    initialAmount: Int? = null,
    subsidyType: String = "NONE",
    subsidyConfirmed: Boolean = false,
    subsidyAmount: Int = 0
) {
    var amount by remember { mutableStateOf(initialAmount?.toString() ?: "") }
    val hasSubsidy = subsidyType == "LOVE_CARD" && subsidyConfirmed && subsidyAmount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("確認車資")
        },
        text = {
            Column {
                if (initialAmount != null && initialAmount > 0) {
                    Text(
                        text = "預估車資已自動帶入，可直接送出或修改",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "請輸入跳表金額",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("車資金額 (NT\$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                // 愛心卡補貼明細
                if (hasSubsidy) {
                    val meterValue = amount.toIntOrNull() ?: 0
                    val actualSubsidy = min(subsidyAmount, meterValue)
                    val passengerPays = max(0, meterValue - actualSubsidy)

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("跳表金額", style = MaterialTheme.typography.bodyMedium)
                        Text("NT$ $meterValue", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("愛心卡補助", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
                        Text("-NT$ $actualSubsidy", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "乘客支付",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "NT$ $passengerPays",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "補助 NT$ $actualSubsidy 向政府申請核銷",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val meterAmount = amount.toIntOrNull()
                    if (meterAmount != null && meterAmount > 0) {
                        onConfirm(meterAmount, null)
                    }
                },
                enabled = amount.toIntOrNull() != null && amount.toIntOrNull()!! > 0
            ) {
                Text("送出", fontSize = 18.sp)
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
 * 簡化版車資輸入對話框（無拍照）
 * 用於向後兼容
 */
@Composable
fun SimpleFareDialog(
    onDismiss: () -> Unit,
    onConfirm: (meterAmount: Int) -> Unit
) {
    FareDialog(
        onDismiss = onDismiss,
        onConfirm = { amount, _ -> onConfirm(amount) }
    )
}
