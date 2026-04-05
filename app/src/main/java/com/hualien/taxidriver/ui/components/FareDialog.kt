package com.hualien.taxidriver.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 車資確認對話框（預填預估車資，可修改後一鍵送出）
 */
@Composable
fun FareDialog(
    onDismiss: () -> Unit,
    onConfirm: (meterAmount: Int, photoUri: Uri?) -> Unit,
    initialAmount: Int? = null
) {
    var amount by remember { mutableStateOf(initialAmount?.toString() ?: "") }

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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                )
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
