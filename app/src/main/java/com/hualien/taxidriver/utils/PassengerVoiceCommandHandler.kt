package com.hualien.taxidriver.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.hualien.taxidriver.data.remote.dto.PassengerVoiceAction
import com.hualien.taxidriver.data.remote.dto.PassengerVoiceCommand

/**
 * 乘客端語音指令處理器
 * 將解析後的語音指令轉換為實際的應用操作
 */
class PassengerVoiceCommandHandler(
    private val context: Context,
    private val voiceAssistant: VoiceAssistant
) {
    companion object {
        private const val TAG = "PassengerVoiceHandler"
        private const val MIN_CONFIDENCE = 0.5f
    }

    /**
     * 指令執行結果
     */
    sealed class CommandResult {
        data class BookRide(
            val destinationQuery: String,
            val message: String
        ) : CommandResult()

        data class SetDestination(
            val destinationQuery: String,
            val message: String
        ) : CommandResult()

        data class SetPickup(
            val pickupQuery: String,
            val message: String
        ) : CommandResult()

        data class CancelOrder(val message: String) : CommandResult()
        data class CallDriver(val phoneNumber: String?, val message: String) : CommandResult()
        data class CheckStatus(val message: String) : CommandResult()
        data class NeedConfirmation(val message: String, val command: PassengerVoiceCommand) : CommandResult()
        data class Error(val message: String) : CommandResult()
        object Ignored : CommandResult()

        // 語音自動流程 - 確認/拒絕
        object Confirmed : CommandResult()   // 用戶說「對」「是」確認
        object Rejected : CommandResult()    // 用戶說「不對」「不是」拒絕

        // 需要更多輸入（缺少目的地）
        data class NeedDestination(val message: String) : CommandResult()
    }

    /**
     * 指令執行回調介面
     */
    interface CommandCallback {
        // 叫車相關
        fun onBookRide(destinationQuery: String)
        fun onSetDestination(destinationQuery: String)
        fun onSetPickup(pickupQuery: String)
        fun onCancelOrder()

        // 通訊相關
        fun onCallDriver()

        // 查詢相關
        fun onCheckStatus()

        // 取得當前狀態
        fun hasActiveOrder(): Boolean
        fun getOrderStatus(): String?
        fun getDriverPhone(): String?
        fun getDriverName(): String?
    }

    private var callback: CommandCallback? = null

    /**
     * 設置指令回調
     */
    fun setCallback(callback: CommandCallback) {
        this.callback = callback
    }

    /**
     * 處理語音指令
     * @param command 解析後的語音指令
     * @return 執行結果
     */
    fun handleCommand(command: PassengerVoiceCommand): CommandResult {
        Log.d(TAG, "處理乘客指令: ${command.action}, 信心度: ${command.confidence}")

        // 檢查信心度
        if (command.confidence < MIN_CONFIDENCE) {
            val message = "抱歉，我沒有聽清楚，請再說一次"
            voiceAssistant.speak(message)
            return CommandResult.Ignored
        }

        // 信心度中等時需要確認
        if (command.confidence < 0.7f && command.action != PassengerVoiceAction.UNKNOWN) {
            val confirmMessage = getConfirmationMessage(command)
            voiceAssistant.speak(confirmMessage)
            return CommandResult.NeedConfirmation(confirmMessage, command)
        }

        return executeCommand(command)
    }

    /**
     * 確認並執行指令
     */
    fun confirmAndExecute(command: PassengerVoiceCommand): CommandResult {
        return executeCommand(command)
    }

    /**
     * 執行指令
     */
    private fun executeCommand(command: PassengerVoiceCommand): CommandResult {
        val cb = callback ?: run {
            Log.w(TAG, "未設置回調")
            return CommandResult.Error("系統錯誤")
        }

        return when (command.action) {
            PassengerVoiceAction.BOOK_RIDE -> handleBookRide(command, cb)
            PassengerVoiceAction.SET_DESTINATION -> handleSetDestination(command, cb)
            PassengerVoiceAction.SET_PICKUP -> handleSetPickup(command, cb)
            PassengerVoiceAction.CANCEL_ORDER -> handleCancelOrder(cb)
            PassengerVoiceAction.CALL_DRIVER -> handleCallDriver(cb)
            PassengerVoiceAction.CHECK_STATUS -> handleCheckStatus(cb)
            PassengerVoiceAction.CONFIRM -> handleConfirm()
            PassengerVoiceAction.REJECT -> handleReject()
            PassengerVoiceAction.UNKNOWN -> handleUnknown(command)
        }
    }

    /**
     * 用戶確認（語音自動流程）
     */
    private fun handleConfirm(): CommandResult {
        Log.d(TAG, "用戶確認操作")
        voiceAssistant.speak("好的")
        return CommandResult.Confirmed
    }

    /**
     * 用戶拒絕（語音自動流程）
     */
    private fun handleReject(): CommandResult {
        Log.d(TAG, "用戶拒絕操作")
        voiceAssistant.speak("好的，已取消")
        return CommandResult.Rejected
    }

    /**
     * 叫車（包含目的地）
     */
    private fun handleBookRide(command: PassengerVoiceCommand, cb: CommandCallback): CommandResult {
        // 檢查是否已有進行中的訂單
        if (cb.hasActiveOrder()) {
            val message = "您已有進行中的訂單，請先完成或取消"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        val destinationQuery = command.params.destinationQuery
        if (destinationQuery.isNullOrBlank()) {
            // 缺少目的地，需要用戶繼續說明
            val message = "請告訴我您要去哪裡"
            // 注意：不在這裡播放語音，讓 ViewModel 用 speakWithCallback 來處理，以便播放完成後自動開始錄音
            return CommandResult.NeedDestination(message)
        }

        cb.onBookRide(destinationQuery)
        val message = "好的，正在搜尋「$destinationQuery」"
        voiceAssistant.speak(message)
        return CommandResult.BookRide(destinationQuery, message)
    }

    /**
     * 設置目的地
     */
    private fun handleSetDestination(command: PassengerVoiceCommand, cb: CommandCallback): CommandResult {
        if (cb.hasActiveOrder()) {
            val message = "您已有進行中的訂單，無法修改目的地"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        val destinationQuery = command.params.destinationQuery
        if (destinationQuery.isNullOrBlank()) {
            val message = "請告訴我目的地"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onSetDestination(destinationQuery)
        val message = "已設置目的地為「$destinationQuery」"
        voiceAssistant.speak(message)
        return CommandResult.SetDestination(destinationQuery, message)
    }

    /**
     * 設置上車點
     */
    private fun handleSetPickup(command: PassengerVoiceCommand, cb: CommandCallback): CommandResult {
        if (cb.hasActiveOrder()) {
            val message = "您已有進行中的訂單，無法修改上車點"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        val pickupQuery = command.params.pickupQuery
        if (pickupQuery.isNullOrBlank()) {
            val message = "請告訴我上車地點"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onSetPickup(pickupQuery)
        val message = "已設置上車點為「$pickupQuery」"
        voiceAssistant.speak(message)
        return CommandResult.SetPickup(pickupQuery, message)
    }

    /**
     * 取消訂單
     */
    private fun handleCancelOrder(cb: CommandCallback): CommandResult {
        if (!cb.hasActiveOrder()) {
            val message = "目前沒有進行中的訂單"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        val orderStatus = cb.getOrderStatus()
        // 只有 WAITING 狀態可以取消
        if (orderStatus != "WAITING" && orderStatus != "PENDING") {
            val message = "訂單已被司機接受，無法取消"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onCancelOrder()
        val message = "好的，正在取消訂單"
        voiceAssistant.speak(message)
        return CommandResult.CancelOrder(message)
    }

    /**
     * 聯絡司機
     */
    private fun handleCallDriver(cb: CommandCallback): CommandResult {
        if (!cb.hasActiveOrder()) {
            val message = "目前沒有進行中的訂單"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        val driverPhone = cb.getDriverPhone()
        val driverName = cb.getDriverName() ?: "司機"

        if (driverPhone.isNullOrBlank()) {
            val message = "還沒有司機接單，無法聯絡"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        // 通知 UI 進行撥打
        cb.onCallDriver()
        val message = "正在撥打電話給 $driverName"
        voiceAssistant.speak(message)
        return CommandResult.CallDriver(driverPhone, message)
    }

    /**
     * 查詢訂單狀態
     */
    private fun handleCheckStatus(cb: CommandCallback): CommandResult {
        if (!cb.hasActiveOrder()) {
            val message = "目前沒有進行中的訂單"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onCheckStatus()
        val orderStatus = cb.getOrderStatus()
        val driverName = cb.getDriverName()

        val statusMessage = when (orderStatus) {
            "WAITING", "PENDING" -> "正在等待司機接單"
            "ACCEPTED" -> "${driverName ?: "司機"} 已接單，正在前往上車點"
            "ARRIVED" -> "${driverName ?: "司機"} 已到達上車點，請準備上車"
            "PICKED_UP", "ON_TRIP" -> "行程進行中"
            else -> "訂單狀態：$orderStatus"
        }

        voiceAssistant.speak(statusMessage)
        return CommandResult.CheckStatus(statusMessage)
    }

    /**
     * 未知指令
     */
    private fun handleUnknown(command: PassengerVoiceCommand): CommandResult {
        val message = "抱歉，我不太理解「${command.transcription}」，您可以說：\n" +
                "• 「去火車站」叫車\n" +
                "• 「取消訂單」取消\n" +
                "• 「司機在哪」查詢狀態"
        voiceAssistant.speak("抱歉，我不太理解，請再說一次")
        return CommandResult.Error(message)
    }

    /**
     * 生成確認訊息
     */
    private fun getConfirmationMessage(command: PassengerVoiceCommand): String {
        return when (command.action) {
            PassengerVoiceAction.BOOK_RIDE -> {
                val dest = command.params.destinationQuery ?: "指定地點"
                "您是要叫車去「$dest」嗎？"
            }
            PassengerVoiceAction.SET_DESTINATION -> {
                val dest = command.params.destinationQuery ?: "指定地點"
                "您是要設置目的地為「$dest」嗎？"
            }
            PassengerVoiceAction.SET_PICKUP -> {
                val pickup = command.params.pickupQuery ?: "指定地點"
                "您是要在「$pickup」上車嗎？"
            }
            PassengerVoiceAction.CANCEL_ORDER -> "您是要取消訂單嗎？"
            PassengerVoiceAction.CALL_DRIVER -> "您是要聯絡司機嗎？"
            PassengerVoiceAction.CHECK_STATUS -> "您是要查詢訂單狀態嗎？"
            PassengerVoiceAction.CONFIRM -> "您確定嗎？"
            PassengerVoiceAction.REJECT -> "您要取消嗎？"
            PassengerVoiceAction.UNKNOWN -> "抱歉，我沒有聽清楚"
        }
    }

    /**
     * 播報訂單狀態變更
     */
    fun announceOrderUpdate(status: String, driverName: String? = null) {
        val message = when (status) {
            "ACCEPTED" -> "${driverName ?: "司機"} 已接受您的訂單，正在前往上車點"
            "ARRIVED" -> "${driverName ?: "司機"} 已到達上車點，請前往上車"
            "PICKED_UP", "ON_TRIP" -> "行程已開始"
            "COMPLETED" -> "行程已結束，感謝您的搭乘"
            "CANCELLED" -> "訂單已取消"
            else -> "訂單狀態已更新"
        }
        voiceAssistant.speak(message)
    }

    /**
     * 播報司機位置更新
     */
    fun announceDriverApproaching(minutes: Int) {
        if (minutes <= 2) {
            voiceAssistant.speak("司機即將到達")
        } else if (minutes <= 5) {
            voiceAssistant.speak("司機約 $minutes 分鐘後到達")
        }
    }

    /**
     * 播報錯誤
     */
    fun announceError(error: String) {
        voiceAssistant.speak(error)
    }

    /**
     * 播報歡迎訊息
     */
    fun announceWelcome() {
        voiceAssistant.speak("您好，請說出您要去的地方，例如「去火車站」")
    }
}
