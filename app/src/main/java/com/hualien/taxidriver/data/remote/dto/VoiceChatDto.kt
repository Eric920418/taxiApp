package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * 語音對講訊息
 * 用於司機和乘客之間的語音訊息傳遞
 */
data class VoiceChatMessage(
    @SerializedName("messageId")
    val messageId: String = UUID.randomUUID().toString(),

    @SerializedName("orderId")
    val orderId: String,

    @SerializedName("senderId")
    val senderId: String,

    @SerializedName("senderType")
    val senderType: String,  // "driver" or "passenger"

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("messageText")
    val messageText: String,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val SENDER_TYPE_DRIVER = "driver"
        const val SENDER_TYPE_PASSENGER = "passenger"
    }

    /**
     * 是否為司機發送
     */
    fun isFromDriver(): Boolean = senderType == SENDER_TYPE_DRIVER

    /**
     * 是否為乘客發送
     */
    fun isFromPassenger(): Boolean = senderType == SENDER_TYPE_PASSENGER

    /**
     * 格式化時間顯示（HH:mm）
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

/**
 * 語音對講狀態
 */
enum class VoiceChatState {
    IDLE,           // 閒置
    RECORDING,      // 錄音中
    PROCESSING,     // 處理中（上傳轉錄）
    SENDING,        // 發送中
    ERROR           // 錯誤
}

/**
 * 語音對講錯誤類型
 */
sealed class VoiceChatError {
    object RecordingFailed : VoiceChatError()
    object TranscriptionFailed : VoiceChatError()
    object SendFailed : VoiceChatError()
    object NetworkError : VoiceChatError()
    data class Unknown(val errorMessage: String) : VoiceChatError()

    fun getErrorText(): String = when (this) {
        is RecordingFailed -> "錄音失敗，請重試"
        is TranscriptionFailed -> "語音識別失敗，請重試"
        is SendFailed -> "發送失敗，請重試"
        is NetworkError -> "網路連線異常"
        is Unknown -> errorMessage
    }
}
