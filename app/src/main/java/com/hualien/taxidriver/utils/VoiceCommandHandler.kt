package com.hualien.taxidriver.utils

import android.content.Context
import android.util.Log
import com.hualien.taxidriver.data.remote.dto.VoiceAction
import com.hualien.taxidriver.data.remote.dto.VoiceCommand
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.domain.model.OrderStatus

/**
 * 語音指令處理器
 * 將解析後的語音指令轉換為實際的應用操作
 */
class VoiceCommandHandler(
    private val context: Context,
    private val voiceAssistant: VoiceAssistant
) {
    companion object {
        private const val TAG = "VoiceCommandHandler"
        private const val MIN_CONFIDENCE = 0.5f
    }

    /**
     * 指令執行結果
     */
    sealed class CommandResult {
        data class Success(val message: String) : CommandResult()
        data class NeedConfirmation(val message: String, val command: VoiceCommand) : CommandResult()
        data class Error(val message: String) : CommandResult()
        object Ignored : CommandResult()
    }

    /**
     * 指令執行回調介面
     */
    interface CommandCallback {
        // 訂單相關
        fun onAcceptOrder(orderId: String?)
        fun onRejectOrder(orderId: String?, reason: String?)
        fun onMarkArrived(orderId: String?)
        fun onStartTrip(orderId: String?)
        fun onEndTrip(orderId: String?)

        // 狀態相關
        fun onUpdateStatus(status: DriverAvailability)

        // 查詢相關
        fun onQueryEarnings()

        // 導航相關
        fun onNavigate(destination: String?)

        // 緊急相關
        fun onEmergency()

        // 取得當前狀態
        fun getCurrentOrder(): Order?
        fun getCurrentStatus(): DriverAvailability
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
    fun handleCommand(command: VoiceCommand): CommandResult {
        Log.d(TAG, "處理指令: ${command.action}, 信心度: ${command.confidence}")

        // 檢查信心度
        if (command.confidence < MIN_CONFIDENCE) {
            val message = "抱歉，我沒有聽清楚，請再說一次"
            voiceAssistant.speak(message)
            return CommandResult.Ignored
        }

        // 信心度中等時需要確認
        if (command.confidence < 0.7f && command.action != VoiceAction.UNKNOWN) {
            val confirmMessage = getConfirmationMessage(command)
            voiceAssistant.speak(confirmMessage)
            return CommandResult.NeedConfirmation(confirmMessage, command)
        }

        return executeCommand(command)
    }

    /**
     * 確認並執行指令
     */
    fun confirmAndExecute(command: VoiceCommand): CommandResult {
        return executeCommand(command)
    }

    /**
     * 執行指令
     */
    private fun executeCommand(command: VoiceCommand): CommandResult {
        val cb = callback ?: run {
            Log.w(TAG, "未設置回調")
            return CommandResult.Error("系統錯誤")
        }

        return when (command.action) {
            VoiceAction.ACCEPT_ORDER -> handleAcceptOrder(command, cb)
            VoiceAction.REJECT_ORDER -> handleRejectOrder(command, cb)
            VoiceAction.MARK_ARRIVED -> handleMarkArrived(command, cb)
            VoiceAction.START_TRIP -> handleStartTrip(command, cb)
            VoiceAction.END_TRIP -> handleEndTrip(command, cb)
            VoiceAction.UPDATE_STATUS -> handleUpdateStatus(command, cb)
            VoiceAction.QUERY_EARNINGS -> handleQueryEarnings(cb)
            VoiceAction.NAVIGATE -> handleNavigate(command, cb)
            VoiceAction.EMERGENCY -> handleEmergency(cb)
            VoiceAction.UNKNOWN -> handleUnknown(command)
        }
    }

    /**
     * 接受訂單
     */
    private fun handleAcceptOrder(command: VoiceCommand, cb: CommandCallback): CommandResult {
        val currentOrder = cb.getCurrentOrder()
        val orderId = command.params.orderId ?: currentOrder?.orderId

        if (orderId == null) {
            val message = "目前沒有待接的訂單"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        // 檢查訂單狀態（OFFERED 表示已派單待接受）
        if (currentOrder?.status != OrderStatus.OFFERED) {
            val message = "這個訂單無法接受"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onAcceptOrder(orderId)
        val message = "好的，已接受訂單"
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 拒絕訂單
     */
    private fun handleRejectOrder(command: VoiceCommand, cb: CommandCallback): CommandResult {
        val currentOrder = cb.getCurrentOrder()
        val orderId = command.params.orderId ?: currentOrder?.orderId

        if (orderId == null) {
            val message = "目前沒有可拒絕的訂單"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        val reason = command.params.rejectionReason ?: "司機不便接單"
        cb.onRejectOrder(orderId, reason)
        val message = "好的，已拒絕訂單"
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 標記已到達
     */
    private fun handleMarkArrived(command: VoiceCommand, cb: CommandCallback): CommandResult {
        val currentOrder = cb.getCurrentOrder()
        val orderId = command.params.orderId ?: currentOrder?.orderId

        if (orderId == null) {
            val message = "目前沒有進行中的訂單"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        if (currentOrder?.status != OrderStatus.ACCEPTED) {
            val message = "訂單狀態不正確，無法標記到達"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onMarkArrived(orderId)
        val message = "已標記到達上車點"
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 開始行程
     */
    private fun handleStartTrip(command: VoiceCommand, cb: CommandCallback): CommandResult {
        val currentOrder = cb.getCurrentOrder()
        val orderId = command.params.orderId ?: currentOrder?.orderId

        if (orderId == null) {
            val message = "目前沒有進行中的訂單"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        if (currentOrder?.status != OrderStatus.ARRIVED) {
            val message = "請先標記已到達上車點"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onStartTrip(orderId)
        val message = "行程開始，祝您一路平安"
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 結束行程
     */
    private fun handleEndTrip(command: VoiceCommand, cb: CommandCallback): CommandResult {
        val currentOrder = cb.getCurrentOrder()
        val orderId = command.params.orderId ?: currentOrder?.orderId

        if (orderId == null) {
            val message = "目前沒有進行中的訂單"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        if (currentOrder?.status != OrderStatus.ON_TRIP) {
            val message = "行程尚未開始，無法結束"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onEndTrip(orderId)
        val message = "行程已結束"
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 更新司機狀態
     */
    private fun handleUpdateStatus(command: VoiceCommand, cb: CommandCallback): CommandResult {
        val statusStr = command.params.status?.uppercase()
        val newStatus = when (statusStr) {
            "AVAILABLE", "ONLINE", "上線" -> DriverAvailability.AVAILABLE
            "OFFLINE", "下線" -> DriverAvailability.OFFLINE
            "REST", "休息" -> DriverAvailability.REST
            else -> {
                val message = "無法識別的狀態，請說上線、下線或休息"
                voiceAssistant.speak(message)
                return CommandResult.Error(message)
            }
        }

        // 檢查是否有進行中的訂單
        val currentOrder = cb.getCurrentOrder()
        if (currentOrder != null && newStatus == DriverAvailability.OFFLINE) {
            val message = "您有進行中的訂單，無法下線"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onUpdateStatus(newStatus)
        val message = when (newStatus) {
            DriverAvailability.AVAILABLE -> "已切換為可接單狀態"
            DriverAvailability.OFFLINE -> "已下線"
            DriverAvailability.REST -> "已切換為休息中"
            else -> "狀態已更新"
        }
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 查詢收入
     */
    private fun handleQueryEarnings(cb: CommandCallback): CommandResult {
        cb.onQueryEarnings()
        val message = "正在查詢您的收入"
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 導航
     */
    private fun handleNavigate(command: VoiceCommand, cb: CommandCallback): CommandResult {
        val destination = command.params.destination

        if (destination.isNullOrBlank()) {
            // 如果沒有指定目的地，使用當前訂單的目的地
            val currentOrder = cb.getCurrentOrder()
            if (currentOrder != null) {
                val orderDest = when (currentOrder.status) {
                    OrderStatus.ACCEPTED, OrderStatus.ARRIVED -> currentOrder.pickup.address
                    OrderStatus.ON_TRIP -> currentOrder.destination?.address
                    else -> null
                }
                if (orderDest != null) {
                    cb.onNavigate(orderDest)
                    val message = "正在開啟導航"
                    voiceAssistant.speak(message)
                    return CommandResult.Success(message)
                }
            }
            val message = "請告訴我要導航到哪裡"
            voiceAssistant.speak(message)
            return CommandResult.Error(message)
        }

        cb.onNavigate(destination)
        val message = "正在導航到 $destination"
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 緊急處理
     */
    private fun handleEmergency(cb: CommandCallback): CommandResult {
        cb.onEmergency()
        val message = "正在啟動緊急求助"
        voiceAssistant.speak(message)
        return CommandResult.Success(message)
    }

    /**
     * 未知指令
     */
    private fun handleUnknown(command: VoiceCommand): CommandResult {
        val message = "抱歉，我不太理解「${command.transcription}」，請再說一次或換個說法"
        voiceAssistant.speak(message)
        return CommandResult.Error(message)
    }

    /**
     * 生成確認訊息
     */
    private fun getConfirmationMessage(command: VoiceCommand): String {
        return when (command.action) {
            VoiceAction.ACCEPT_ORDER -> "您是要接受這個訂單嗎？"
            VoiceAction.REJECT_ORDER -> "您是要拒絕這個訂單嗎？"
            VoiceAction.MARK_ARRIVED -> "您是要標記已到達嗎？"
            VoiceAction.START_TRIP -> "您是要開始行程嗎？"
            VoiceAction.END_TRIP -> "您是要結束行程嗎？"
            VoiceAction.UPDATE_STATUS -> "您是要更新狀態嗎？"
            VoiceAction.QUERY_EARNINGS -> "您是要查詢收入嗎？"
            VoiceAction.NAVIGATE -> "您是要開啟導航嗎？"
            VoiceAction.EMERGENCY -> "您是要啟動緊急求助嗎？"
            VoiceAction.UNKNOWN -> "抱歉，我沒有聽清楚"
        }
    }

    /**
     * 播報訂單資訊
     */
    fun announceOrder(order: Order) {
        val pickup = order.pickup.address ?: "未知地點"
        val destination = order.destination?.address ?: "未知目的地"
        val message = "收到新訂單，從 $pickup 到 $destination。說「接」接受，說「不要」拒絕"
        voiceAssistant.speak(message)
    }

    /**
     * 播報到達提醒
     */
    fun announceArrival(isPickup: Boolean) {
        val message = if (isPickup) {
            "您已接近上車點，請準備停車"
        } else {
            "您已接近目的地"
        }
        voiceAssistant.speak(message)
    }

    /**
     * 播報錯誤
     */
    fun announceError(error: String) {
        voiceAssistant.speak(error)
    }
}
