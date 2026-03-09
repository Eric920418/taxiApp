package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 訂單列表回應
 * 使用 OrderDto 來處理 ISO 8601 日期格式
 */
data class OrderListResponse(
    @SerializedName("orders")
    val orders: List<OrderDto>,
    @SerializedName("total")
    val total: Int
)

/**
 * 接受訂單回應
 * 使用 OrderDto 來處理 ISO 8601 日期格式
 */
data class AcceptOrderResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("order")
    val order: OrderDto
)

/**
 * 拒絕訂單回應
 */
data class RejectOrderResponse(
    val success: Boolean,
    val message: String
)

/**
 * 智能派單 V2：拒絕訂單請求
 * @param driverId 司機 ID
 * @param rejectionReason 拒單原因（必填）
 *        可選值：TOO_FAR, LOW_FARE, UNWANTED_AREA, OFF_DUTY, OTHER
 */
data class RejectOrderRequest(
    val driverId: String,
    val rejectionReason: String
)

// ==================== 收入統計相關 ====================

/**
 * 收入統計回應
 */
data class EarningsResponse(
    val success: Boolean,
    val driverId: String,
    val earnings: EarningsData
)

/**
 * 收入數據
 */
data class EarningsData(
    val period: String,
    val totalAmount: Int,
    val orderCount: Int,
    val totalDistance: Double,
    val totalDuration: Double,  // 小時
    val averageFare: Double,
    // 今日訂單列表（period=today 時有值）
    val orders: List<EarningsOrder>? = null,
    // 每日統計（period=week 時有值）
    val dailyBreakdown: List<DailyBreakdown>? = null,
    // 每週統計（period=month 時有值）
    val weeklyBreakdown: List<WeeklyBreakdown>? = null
)

/**
 * 收入訂單項目
 */
data class EarningsOrder(
    val orderId: String,
    val fare: Int?,
    val distance: Double?,
    val duration: Double?,  // 小時
    val completedAt: String? = null  // ISO 8601 格式
)

/**
 * 每日統計
 */
data class DailyBreakdown(
    val date: String,
    val amount: Int,
    val orders: Int
)

/**
 * 每週統計
 */
data class WeeklyBreakdown(
    val week: String,
    val amount: Int,
    val orders: Int
)
