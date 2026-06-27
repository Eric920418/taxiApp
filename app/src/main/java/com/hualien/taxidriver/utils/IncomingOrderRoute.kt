package com.hualien.taxidriver.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 通知/全螢幕頁帶進來的待處理 orderId 中轉站。
 *
 * MainActivity 從 intent extra 取得 orderId 後寫入這裡；HomeScreen 觀察到非空就
 * 呼叫 HomeViewModel.fetchOrderById 補抓該單顯示卡片，然後清空。
 * 用 Compose state 讓 HomeScreen 能自動 recompose。
 */
object IncomingOrderRoute {
    var pendingOrderId by mutableStateOf<String?>(null)
}
