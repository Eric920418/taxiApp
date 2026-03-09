package com.hualien.taxidriver.utils

import android.content.Context
import android.util.Log
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.data.remote.dto.VoiceChatError
import com.hualien.taxidriver.data.remote.dto.VoiceChatMessage
import com.hualien.taxidriver.data.remote.dto.VoiceChatState
import com.hualien.taxidriver.service.VoiceRecorderService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 語音對講管理器
 * 處理司機/乘客之間的語音訊息傳遞
 *
 * 工作流程：
 * 1. 按住說話 → 開始錄音
 * 2. 放開按鈕 → 停止錄音 → 上傳轉錄 → 發送訊息
 * 3. 接收訊息 → TTS 播報 → 顯示聊天氣泡
 */
class VoiceChatManager(
    private val context: Context,
    private val voiceAssistant: VoiceAssistant,
    private val voiceRecorderService: VoiceRecorderService,
    private val webSocketManager: WebSocketManager = WebSocketManager.getInstance()
) {
    companion object {
        private const val TAG = "VoiceChatManager"
        private const val MAX_RECORDING_SECONDS = 30  // 最長錄音時間
        private const val MAX_HISTORY_SIZE = 50       // 最多保留的訊息數量
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 對講狀態
    private val _state = MutableStateFlow(VoiceChatState.IDLE)
    val state: StateFlow<VoiceChatState> = _state.asStateFlow()

    // 錄音振幅（用於 UI 動畫）
    val amplitude: StateFlow<Int> = voiceRecorderService.amplitude

    // 對話記錄
    private val _chatHistory = MutableStateFlow<List<VoiceChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<VoiceChatMessage>> = _chatHistory.asStateFlow()

    // 錯誤狀態
    private val _error = MutableStateFlow<VoiceChatError?>(null)
    val error: StateFlow<VoiceChatError?> = _error.asStateFlow()

    // 是否正在錄音
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // 錄音計時器
    private var recordingJob: Job? = null
    private var audioFile: File? = null

    // 當前用戶資訊（需要在使用前設置）
    private var currentOrderId: String? = null
    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private var currentUserType: String? = null  // "driver" or "passenger"

    /**
     * 設置當前用戶資訊
     * 必須在使用對講功能前調用
     */
    fun setCurrentUser(
        orderId: String,
        userId: String,
        userName: String,
        userType: String
    ) {
        currentOrderId = orderId
        currentUserId = userId
        currentUserName = userName
        currentUserType = userType
        Log.d(TAG, "設置用戶: $userName ($userType), 訂單: $orderId")
    }

    /**
     * 開始錄音（按住按鈕時調用）
     */
    fun startRecording() {
        if (_isRecording.value) {
            Log.w(TAG, "已經在錄音中")
            return
        }

        Log.d(TAG, "========== 開始錄音 ==========")
        _state.value = VoiceChatState.RECORDING
        _isRecording.value = true
        _error.value = null

        // 設置錄音超時保護
        recordingJob = scope.launch {
            delay(MAX_RECORDING_SECONDS * 1000L)
            if (_isRecording.value) {
                Log.w(TAG, "錄音超時，自動停止")
                withContext(Dispatchers.Main) {
                    stopRecordingAndSend()
                }
            }
        }

        // 開始錄音，完成時保存文件
        voiceRecorderService.startRecording { file ->
            audioFile = file
            Log.d(TAG, "錄音完成: ${file.absolutePath}, 大小: ${file.length()} bytes")
        }
    }

    /**
     * 停止錄音並發送（放開按鈕時調用）
     */
    fun stopRecordingAndSend() {
        if (!_isRecording.value) {
            return
        }

        Log.d(TAG, "========== 停止錄音 ==========")
        recordingJob?.cancel()
        _isRecording.value = false

        // 停止錄音
        voiceRecorderService.stopRecording()

        // 處理音檔
        scope.launch {
            processAndSendAudio()
        }
    }

    /**
     * 取消錄音（不發送）
     */
    fun cancelRecording() {
        Log.d(TAG, "取消錄音")
        recordingJob?.cancel()
        _isRecording.value = false
        voiceRecorderService.stopRecording()
        audioFile?.delete()
        audioFile = null
        _state.value = VoiceChatState.IDLE
    }

    /**
     * 處理並發送音檔
     */
    private suspend fun processAndSendAudio() {
        val file = audioFile
        val orderId = currentOrderId
        val userId = currentUserId
        val userName = currentUserName
        val userType = currentUserType

        if (file == null || !file.exists() || file.length() == 0L) {
            Log.e(TAG, "音檔不存在或為空")
            _error.value = VoiceChatError.RecordingFailed
            _state.value = VoiceChatState.ERROR
            return
        }

        if (orderId == null || userId == null || userName == null || userType == null) {
            Log.e(TAG, "用戶資訊未設置")
            _error.value = VoiceChatError.Unknown("請先設置用戶資訊")
            _state.value = VoiceChatState.ERROR
            return
        }

        try {
            _state.value = VoiceChatState.PROCESSING

            // 上傳語音轉錄
            val transcribedText = transcribeAudio(file, userId, userType)

            if (transcribedText.isNullOrBlank()) {
                Log.e(TAG, "語音識別結果為空")
                _error.value = VoiceChatError.TranscriptionFailed
                _state.value = VoiceChatState.ERROR
                voiceAssistant.speak("抱歉，無法識別語音，請再說一次", VoiceAssistant.Priority.HIGH)
                return
            }

            Log.d(TAG, "語音識別結果: $transcribedText")
            _state.value = VoiceChatState.SENDING

            // 發送訊息
            webSocketManager.sendVoiceMessage(
                orderId = orderId,
                senderId = userId,
                senderName = userName,
                senderType = userType,
                messageText = transcribedText
            )

            // 添加到本地記錄
            val message = VoiceChatMessage(
                orderId = orderId,
                senderId = userId,
                senderType = userType,
                senderName = userName,
                messageText = transcribedText
            )
            addToHistory(message)

            _state.value = VoiceChatState.IDLE
            Log.d(TAG, "✅ 訊息發送成功")

        } catch (e: Exception) {
            Log.e(TAG, "處理音檔失敗", e)
            _error.value = VoiceChatError.SendFailed
            _state.value = VoiceChatState.ERROR
            voiceAssistant.speak("發送失敗，請重試", VoiceAssistant.Priority.HIGH)
        } finally {
            // 清理音檔
            file.delete()
            audioFile = null
        }
    }

    /**
     * 上傳語音轉錄
     */
    private suspend fun transcribeAudio(file: File, userId: String, userType: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val requestFile = file.asRequestBody("audio/m4a".toMediaType())
                val audioPart = MultipartBody.Part.createFormData("audio", file.name, requestFile)

                if (userType == VoiceChatMessage.SENDER_TYPE_DRIVER) {
                    // 司機端使用司機 API
                    val response = RetrofitClient.apiService.transcribeAudio(
                        audio = audioPart,
                        driverId = userId.toRequestBody("text/plain".toMediaType()),
                        currentStatus = "ON_TRIP".toRequestBody("text/plain".toMediaType()),
                        currentOrderId = currentOrderId?.toRequestBody("text/plain".toMediaType()),
                        currentOrderStatus = "ACCEPTED".toRequestBody("text/plain".toMediaType()),
                        pickupAddress = null,
                        destinationAddress = null
                    )

                    if (response.isSuccessful) {
                        val body = response.body()
                        Log.d(TAG, "司機端轉錄響應: $body")
                        // 從 VoiceCommand 中提取 rawText
                        body?.command?.rawText
                    } else {
                        Log.e(TAG, "司機端轉錄 API 失敗: ${response.code()} - ${response.message()}")
                        null
                    }
                } else {
                    // 乘客端使用乘客 API
                    val response = RetrofitClient.passengerApiService.transcribePassengerAudio(
                        audio = audioPart,
                        passengerId = userId.toRequestBody("text/plain".toMediaType()),
                        hasActiveOrder = "true".toRequestBody("text/plain".toMediaType()),
                        orderStatus = "ACCEPTED".toRequestBody("text/plain".toMediaType()),
                        currentPickupAddress = null,
                        currentDestinationAddress = null,
                        driverName = null,
                        driverPhone = null
                    )

                    if (response.isSuccessful) {
                        val body = response.body()
                        Log.d(TAG, "乘客端轉錄響應: $body")
                        // 從 PassengerVoiceCommand 中提取 rawText
                        body?.command?.rawText
                    } else {
                        Log.e(TAG, "乘客端轉錄 API 失敗: ${response.code()} - ${response.message()}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "轉錄請求失敗", e)
                null
            }
        }
    }

    /**
     * 接收並處理對方發來的訊息
     * 由 ViewModel 在監聽到 WebSocket 訊息時調用
     */
    fun receiveMessage(message: VoiceChatMessage) {
        Log.d(TAG, "收到語音訊息: ${message.senderName}說「${message.messageText}」")

        // 添加到記錄
        addToHistory(message)

        // 播報訊息
        val announcement = "${message.senderName}說：${message.messageText}"
        voiceAssistant.speak(announcement, VoiceAssistant.Priority.HIGH)
    }

    /**
     * 添加訊息到歷史記錄
     */
    private fun addToHistory(message: VoiceChatMessage) {
        val current = _chatHistory.value.toMutableList()
        current.add(message)

        // 限制記錄數量
        if (current.size > MAX_HISTORY_SIZE) {
            current.removeAt(0)
        }

        _chatHistory.value = current
    }

    /**
     * 清除對話記錄（訂單結束時調用）
     */
    fun clearHistory() {
        Log.d(TAG, "清除對話記錄")
        _chatHistory.value = emptyList()
        currentOrderId = null
        currentUserId = null
        currentUserName = null
        currentUserType = null
    }

    /**
     * 清除錯誤狀態
     */
    fun clearError() {
        _error.value = null
        if (_state.value == VoiceChatState.ERROR) {
            _state.value = VoiceChatState.IDLE
        }
    }

    /**
     * 判斷訊息是否為自己發送
     */
    fun isFromMe(message: VoiceChatMessage): Boolean {
        return message.senderId == currentUserId
    }

    /**
     * 釋放資源
     */
    fun release() {
        scope.cancel()
        recordingJob?.cancel()
        audioFile?.delete()
    }
}
