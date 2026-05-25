package com.hualien.taxidriver.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlin.math.min
import kotlin.math.max

/**
 * 車資確認對話框（v1.5.2 自製鍵盤版）
 *
 * 設計重點：
 * - 暗色背景 + 自製 3x4 數字鍵盤（不用 Android 系統鍵盤，避免遮擋）
 * - 第一次按數字 → 自動清空 initialAmount 預填值（避免「2001」這種錯接）
 * - 取消綠/紅大按鈕，老人友善大字
 * - 保留低速駐車費建議 + 愛心卡補貼明細（有資料才顯示）
 */
@Composable
fun FareDialog(
    onDismiss: () -> Unit,
    onConfirm: (meterAmount: Int, photoUri: Uri?) -> Unit,
    initialAmount: Int? = null,
    subsidyType: String = "NONE",
    subsidyConfirmed: Boolean = false,
    subsidyAmount: Int = 0,
    /** 行程內累計低速秒數（駐車費）— null 不顯示 */
    slowTrafficSeconds: Int? = null,
    /** 依當下費率算出的「建議加收」金額（元） */
    slowTrafficSuggestedFare: Int? = null,
) {
    // 預填金額 → String 顯示。第一次按任意數字鍵會清空換新值（避免接成 2001）
    var amount by remember { mutableStateOf(initialAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var isFirstInput by remember { mutableStateOf(initialAmount != null && initialAmount > 0) }

    val hasSubsidy = subsidyType == "LOVE_CARD" && subsidyConfirmed && subsidyAmount > 0
    val meterValue = amount.toIntOrNull() ?: 0
    val isValid = meterValue > 0

    // ===== 鍵盤回呼 =====
    val onDigit: (String) -> Unit = { d ->
        if (isFirstInput) {
            amount = d
            isFirstInput = false
        } else if (amount.length < 5) { // 最多 5 位 = 99999
            // 開頭是 0 且尚無小數點時不要累加 0（避免 00012）
            amount = if (amount == "0") d else amount + d
        }
    }
    val onClear: () -> Unit = {
        amount = ""
        isFirstInput = false
    }
    val onBackspace: () -> Unit = {
        if (amount.isNotEmpty()) {
            amount = amount.dropLast(1)
            isFirstInput = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        containerColor = Color(0xFF1C1C1E), // iOS-like 深色背景
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = "確認車資",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isFirstInput) {
                        "已帶入預估金額，按任意數字鍵覆寫"
                    } else {
                        "請輸入跳表金額"
                    },
                    fontSize = 16.sp,
                    color = Color(0xFFBDBDBD),
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // ===== 金額顯示框（不可編輯，視覺類似 calculator display）=====
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2C2C2E))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = if (amount.isEmpty()) "0" else amount,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (amount.isEmpty()) Color(0xFF6E6E72) else Color.White,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 低速計時建議（駐車費）
                if (slowTrafficSeconds != null && slowTrafficSeconds > 0 && (slowTrafficSuggestedFare ?: 0) > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF332B1A))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "低速計時 ${slowTrafficSeconds} 秒",
                                fontSize = 14.sp,
                                color = Color(0xFFBDBDBD)
                            )
                            Text(
                                text = "（依花蓮縣府公告駐車費）",
                                fontSize = 12.sp,
                                color = Color(0xFF8E8E93)
                            )
                        }
                        Text(
                            text = "建議加收 NT$ ${slowTrafficSuggestedFare}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB74D)
                        )
                    }
                }

                // 愛心卡補貼明細
                if (hasSubsidy) {
                    val actualSubsidy = min(subsidyAmount, meterValue)
                    val passengerPays = max(0, meterValue - actualSubsidy)

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF3A3A3C))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("跳表金額", fontSize = 16.sp, color = Color.White)
                        Text("NT$ $meterValue", fontSize = 16.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("愛心卡補助", fontSize = 16.sp, color = Color(0xFF81C784))
                        Text("-NT$ $actualSubsidy", fontSize = 16.sp, color = Color(0xFF81C784))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF3A3A3C))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("乘客支付", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "NT$ $passengerPays",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "補助 NT$ $actualSubsidy 向政府申請核銷",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ===== 數字鍵盤 =====
                NumericKeypad(
                    onDigit = onDigit,
                    onClear = onClear,
                    onBackspace = onBackspace,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onConfirm(meterValue, null) },
                enabled = isValid,
                modifier = Modifier
                    .height(60.dp)
                    .widthIn(min = 140.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color(0xFF3A3A3C),
                    contentColor = Color.White,
                    disabledContentColor = Color(0xFF6E6E72)
                )
            ) {
                Text(
                    text = "送出",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(60.dp)
                    .widthIn(min = 100.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1976D2)
                )
            ) {
                Text(
                    text = "取消",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

/**
 * 3x4 數字鍵盤
 * 排版：
 *   1 2 3
 *   4 5 6
 *   7 8 9
 *   C 0 ⌫
 */
@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { digit ->
                    DigitKey(
                        text = digit,
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        // 最後一列：清除 C | 0 | 退格 ⌫
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionKey(
                text = "清除 C",
                onClick = onClear,
                modifier = Modifier.weight(1f)
            )
            DigitKey(
                text = "0",
                onClick = { onDigit("0") },
                modifier = Modifier.weight(1f)
            )
            ActionKey(
                text = "退格 ⌫",
                onClick = onBackspace,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 數字鍵 — 深灰底白字
 */
@Composable
private fun DigitKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF3A3A3C),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 操作鍵（清除 / 退格）— 淺底深字，跟數字鍵 hierarchy 區分
 */
@Composable
private fun ActionKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE8E8EA),
            contentColor = Color(0xFF1976D2)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 簡化版車資輸入對話框（無拍照）— 向後兼容
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
