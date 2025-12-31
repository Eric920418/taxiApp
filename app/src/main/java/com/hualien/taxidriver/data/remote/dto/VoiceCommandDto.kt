package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 語音指令動作類型
 */
enum class VoiceAction {
    @SerializedName("ACCEPT_ORDER")
    ACCEPT_ORDER,

    @SerializedName("REJECT_ORDER")
    REJECT_ORDER,

    @SerializedName("MARK_ARRIVED")
    MARK_ARRIVED,

    @SerializedName("START_TRIP")
    START_TRIP,

    @SerializedName("END_TRIP")
    END_TRIP,

    @SerializedName("UPDATE_STATUS")
    UPDATE_STATUS,

    @SerializedName("QUERY_EARNINGS")
    QUERY_EARNINGS,

    @SerializedName("NAVIGATE")
    NAVIGATE,

    @SerializedName("EMERGENCY")
    EMERGENCY,

    @SerializedName("UNKNOWN")
    UNKNOWN
}

/**
 * 語音指令參數
 */
data class VoiceCommandParams(
    @SerializedName("orderId")
    val orderId: String? = null,

    @SerializedName("rejectionReason")
    val rejectionReason: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("destination")
    val destination: String? = null,

    @SerializedName("query")
    val query: String? = null
)

/**
 * 語音指令
 */
data class VoiceCommand(
    @SerializedName("action")
    val action: VoiceAction,

    @SerializedName("params")
    val params: VoiceCommandParams = VoiceCommandParams(),

    @SerializedName("confidence")
    val confidence: Float,

    @SerializedName("rawText")
    val rawText: String,

    @SerializedName("transcription")
    val transcription: String
) {
    /**
     * 判斷是否為高信心度指令
     */
    fun isHighConfidence(): Boolean = confidence >= 0.7f

    /**
     * 判斷是否為可執行指令
     */
    fun isExecutable(): Boolean = action != VoiceAction.UNKNOWN && confidence >= 0.5f
}

/**
 * 語音轉錄 API 回應
 */
data class VoiceTranscribeResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("command")
    val command: VoiceCommand? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("processingTimeMs")
    val processingTimeMs: Long? = null
)

/**
 * 語音服務用量統計
 */
data class VoiceUsageStats(
    @SerializedName("dailyMinutes")
    val dailyMinutes: Float,

    @SerializedName("dailyTokens")
    val dailyTokens: Int,

    @SerializedName("monthlyMinutes")
    val monthlyMinutes: Float,

    @SerializedName("monthlyTokens")
    val monthlyTokens: Int,

    @SerializedName("monthlyEstimatedCostUSD")
    val monthlyEstimatedCostUSD: Float
)

/**
 * 語音用量 API 回應
 */
data class VoiceUsageResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("usage")
    val usage: VoiceUsageStats? = null,

    @SerializedName("limits")
    val limits: VoiceUsageLimits? = null,

    @SerializedName("error")
    val error: String? = null
)

/**
 * 語音用量限制
 */
data class VoiceUsageLimits(
    @SerializedName("dailyMinutes")
    val dailyMinutes: Int,

    @SerializedName("monthlyBudgetUSD")
    val monthlyBudgetUSD: Int
)

/**
 * 司機上下文（用於意圖解析）
 */
data class DriverContext(
    val driverId: String,
    val currentStatus: String,
    val currentOrderId: String? = null,
    val currentOrderStatus: String? = null,
    val pickupAddress: String? = null,
    val destinationAddress: String? = null
)

// ==================== 乘客端語音類型 ====================

/**
 * 乘客端語音指令動作類型
 */
enum class PassengerVoiceAction {
    @SerializedName("BOOK_RIDE")
    BOOK_RIDE,          // 叫車（包含目的地）

    @SerializedName("SET_DESTINATION")
    SET_DESTINATION,    // 設置目的地

    @SerializedName("SET_PICKUP")
    SET_PICKUP,         // 設置上車點

    @SerializedName("CANCEL_ORDER")
    CANCEL_ORDER,       // 取消訂單

    @SerializedName("CALL_DRIVER")
    CALL_DRIVER,        // 聯絡司機

    @SerializedName("CHECK_STATUS")
    CHECK_STATUS,       // 查詢訂單狀態

    @SerializedName("CONFIRM")
    CONFIRM,            // 確認操作（語音自動流程）

    @SerializedName("REJECT")
    REJECT,             // 拒絕/否定操作（語音自動流程）

    @SerializedName("UNKNOWN")
    UNKNOWN
}

/**
 * 乘客端語音指令參數
 */
data class PassengerVoiceCommandParams(
    @SerializedName("destinationQuery")
    val destinationQuery: String? = null,   // 目的地搜尋關鍵字

    @SerializedName("pickupQuery")
    val pickupQuery: String? = null,        // 上車點搜尋關鍵字

    @SerializedName("orderId")
    val orderId: String? = null
)

/**
 * 乘客端語音指令
 */
data class PassengerVoiceCommand(
    @SerializedName("action")
    val action: PassengerVoiceAction,

    @SerializedName("params")
    val params: PassengerVoiceCommandParams = PassengerVoiceCommandParams(),

    @SerializedName("confidence")
    val confidence: Float,

    @SerializedName("rawText")
    val rawText: String,

    @SerializedName("transcription")
    val transcription: String
) {
    /**
     * 判斷是否為高信心度指令
     */
    fun isHighConfidence(): Boolean = confidence >= 0.7f

    /**
     * 判斷是否為可執行指令
     */
    fun isExecutable(): Boolean = action != PassengerVoiceAction.UNKNOWN && confidence >= 0.5f
}

/**
 * 乘客端語音轉錄 API 回應
 */
data class PassengerVoiceTranscribeResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("command")
    val command: PassengerVoiceCommand? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("processingTimeMs")
    val processingTimeMs: Long? = null
)

/**
 * 乘客上下文（用於意圖解析）
 */
data class PassengerContext(
    val passengerId: String,
    val hasActiveOrder: Boolean,
    val orderStatus: String? = null,
    val currentPickupAddress: String? = null,
    val currentDestinationAddress: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null
)
