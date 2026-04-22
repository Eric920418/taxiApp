package com.hualien.taxidriver.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.max

/**
 * 車資確認對話框（長輩友善版）
 *
 * 設計重點：
 * - 字體全面放大（標題 26sp、提示 18sp、輸入框 32sp）
 * - 輸入框只接受數字（自動過濾非數字字元）
 * - 送出按鈕未輸入時 disabled 視覺明顯
 * - 自動彈出數字鍵盤
 * - 支援愛心卡補貼明細
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
    // 用 TextFieldValue 以便控制選取範圍（預填金額開啟時全選，方便直接覆蓋）
    val initialText = initialAmount?.toString() ?: ""
    var amount by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(0, initialText.length) // 預設全選預填值
            )
        )
    }
    val hasSubsidy = subsidyType == "LOVE_CARD" && subsidyConfirmed && subsidyAmount > 0
    val meterValue = amount.text.toIntOrNull() ?: 0
    val isValid = meterValue > 0

    // 自動聚焦 + 自動彈出數字鍵盤（長輩不用再點一次輸入框）
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(150) // 等 Dialog 動畫結束再聚焦，避免聚焦失敗
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        title = {
            Text(
                text = "確認車資",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121)
            )
        },
        text = {
            Column {
                // 提示文案
                Text(
                    text = if (initialAmount != null && initialAmount > 0) {
                        "系統已自動帶入預估金額\n可直接送出，或修改為實際跳表金額"
                    } else {
                        "請輸入跳表金額"
                    },
                    fontSize = 18.sp,
                    color = Color(0xFF757575),
                    lineHeight = 26.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 車資輸入框（大字、數字過濾、自動聚焦、預填全選）
                OutlinedTextField(
                    value = amount,
                    onValueChange = { new ->
                        // 只保留數字，最多 5 位（避免異常大數字）
                        val filtered = new.text.filter { it.isDigit() }.take(5)
                        // 若文字被截斷，把 selection 夾回合法範圍
                        amount = new.copy(
                            text = filtered,
                            selection = TextRange(filtered.length.coerceAtMost(new.selection.end))
                        )
                    },
                    label = {
                        Text(
                            "車資金額 (NT$)",
                            fontSize = 18.sp
                        )
                    },
                    prefix = {
                        Text(
                            "$ ",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF212121)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50),
                        focusedLabelColor = Color(0xFF4CAF50)
                    )
                )

                // 愛心卡補貼明細
                if (hasSubsidy) {
                    val actualSubsidy = min(subsidyAmount, meterValue)
                    val passengerPays = max(0, meterValue - actualSubsidy)

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("跳表金額", fontSize = 18.sp)
                        Text("NT$ $meterValue", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("愛心卡補助", fontSize = 18.sp, color = Color(0xFF4CAF50))
                        Text("-NT$ $actualSubsidy", fontSize = 18.sp, color = Color(0xFF4CAF50))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "乘客支付",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "NT$ $passengerPays",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "補助 NT$ $actualSubsidy 向政府申請核銷",
                        fontSize = 15.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) onConfirm(meterValue, null)
                },
                enabled = isValid,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color(0xFFBDBDBD)
                )
            ) {
                Text(
                    text = "送出",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(56.dp)
            ) {
                Text(
                    text = "取消",
                    fontSize = 20.sp,
                    color = Color(0xFF757575)
                )
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
