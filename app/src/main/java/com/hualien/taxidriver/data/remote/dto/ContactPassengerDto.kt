package com.hualien.taxidriver.data.remote.dto

/**
 * 司機聯絡客人 — request body
 * 對應後端 POST /api/orders/:orderId/contact-passenger
 */
data class ContactPassengerRequest(
    val driverId: String,
    val message: String,
    val preset: String? = null,  // 'arrived' | 'where_are_you' | 'pick_up_phone' | 'custom'
)

/**
 * 司機聯絡客人 — response
 *
 * channel 分支：
 *   - 'APP'  → 已透過 App 訊息推給客人
 *   - 'LINE' → 已透過 LINE Bot 推給客人
 *   - 'TEL'  → 客人沒 app 沒 LINE / 或 APP 客人離線；passengerPhone 必填，client 用 Intent.ACTION_DIAL 撥號
 *
 * passengerPhone 在 PHONE source 或 APP 離線 fallback 時帶回，給 UI 啟動撥號 intent。
 */
data class ContactPassengerResponse(
    val success: Boolean,
    val channel: String? = null,
    val passengerPhone: String? = null,
    val message: String? = null,
    val error: String? = null,
)
