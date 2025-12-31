package com.hualien.taxidriver.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.data.remote.dto.DriverContext
import com.hualien.taxidriver.data.remote.dto.VoiceCommand
import com.hualien.taxidriver.data.remote.dto.VoiceTranscribeResponse
import com.hualien.taxidriver.data.repository.OrderRepository
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.service.VoiceRecorderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 待評分的訂單資訊
 */
data class PendingRating(
    val orderId: String,
    val passengerId: String,
    val passengerName: String
)

/**
 * Home頁面的UI狀態
 */
data class HomeUiState(
    val driverStatus: DriverAvailability = DriverAvailability.OFFLINE,
    val currentOrder: Order? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // 評分相關
    val pendingRating: PendingRating? = null,
    val isSubmittingRating: Boolean = false,
    // 語音指令相關
    val lastTranscription: String? = null,
    val lastVoiceCommand: VoiceCommand? = null,
    val voiceError: String? = null
)

/**
 * Home頁面ViewModel
 */
class HomeViewModel : ViewModel() {

    private val repository = OrderRepository()
    private val webSocketManager = WebSocketManager.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 追蹤 WebSocket 連接狀態，防止重複連接
    private var connectedDriverId: String? = null
    private var isConnecting = false

    init {
        android.util.Log.d("HomeViewModel", "========== HomeViewModel 初始化 ==========")

        // 監聽 WebSocket 連接狀態
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "開始監聽 WebSocket 連接狀態...")
            webSocketManager.isConnected.collect { isConnected ->
                android.util.Log.d("HomeViewModel", "========== WebSocket 連接狀態變化 ==========")
                android.util.Log.d("HomeViewModel", "連接狀態: ${if (isConnected) "✅ 已連接" else "❌ 未連接"}")

                // 連接斷開時清除連接狀態
                if (!isConnected) {
                    connectedDriverId = null
                    isConnecting = false
                }
            }
        }

        // 監聽WebSocket訂單推送
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "開始監聽 WebSocket 訂單推送...")
            webSocketManager.orderOffer.collect { order ->
                if (order != null) {
                    android.util.Log.d("HomeViewModel", "========== 收到訂單推送 ==========")
                    android.util.Log.d("HomeViewModel", "訂單ID: ${order.orderId}")
                    android.util.Log.d("HomeViewModel", "乘客: ${order.passengerName}")
                    android.util.Log.d("HomeViewModel", "電話: ${order.passengerPhone}")
                    android.util.Log.d("HomeViewModel", "上車點: ${order.pickup.address}")
                    android.util.Log.d("HomeViewModel", "狀態: ${order.status}")

                    _uiState.value = _uiState.value.copy(
                        currentOrder = order,
                        error = null
                    )

                    android.util.Log.d("HomeViewModel", "✅ UI 狀態已更新，訂單卡片應該顯示")
                } else {
                    android.util.Log.d("HomeViewModel", "收到 null 訂單（可能是清除訂單）")
                }
            }
        }

        // 監聽訂單狀態更新（包括乘客取消訂單）
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "開始監聽 WebSocket 訂單狀態更新...")
            webSocketManager.orderStatusUpdate.collect { updatedOrder ->
                if (updatedOrder != null) {
                    android.util.Log.d("HomeViewModel", "========== 收到訂單狀態更新 ==========")
                    android.util.Log.d("HomeViewModel", "訂單ID: ${updatedOrder.orderId}")
                    android.util.Log.d("HomeViewModel", "新狀態: ${updatedOrder.status}")

                    val currentOrder = _uiState.value.currentOrder

                    // 如果是當前訂單的狀態更新
                    if (currentOrder != null && currentOrder.orderId == updatedOrder.orderId) {
                        when (updatedOrder.status.name) {
                            "CANCELLED" -> {
                                // 乘客取消訂單，清除當前訂單
                                android.util.Log.d("HomeViewModel", "⚠️ 乘客已取消訂單，清除訂單卡片")
                                _uiState.value = _uiState.value.copy(
                                    currentOrder = null,
                                    error = "乘客已取消訂單"
                                )
                                // 同時清除 WebSocket 的訂單 offer
                                webSocketManager.clearOrderOffer()
                            }
                            else -> {
                                // 其他狀態更新，更新當前訂單
                                android.util.Log.d("HomeViewModel", "✅ 訂單狀態已更新")
                                _uiState.value = _uiState.value.copy(
                                    currentOrder = updatedOrder,
                                    error = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 連接WebSocket（司機上線）
     * 優化版：防止重複連接
     */
    fun connectWebSocket(driverId: String) {
        // 防止重複連接：如果已經連接到相同的司機ID，跳過
        if (connectedDriverId == driverId && webSocketManager.isConnected.value) {
            android.util.Log.w("HomeViewModel", "⚠️ WebSocket 已連接到司機 $driverId，跳過重複連接")
            return
        }

        // 防止重複連接：如果正在連接中，跳過
        if (isConnecting) {
            android.util.Log.w("HomeViewModel", "⚠️ WebSocket 正在連接中，跳過重複連接請求")
            return
        }

        android.util.Log.d("HomeViewModel", "========== 連接WebSocket ==========")
        android.util.Log.d("HomeViewModel", "司機ID: $driverId")

        isConnecting = true
        viewModelScope.launch {
            try {
                webSocketManager.connect(driverId)
                connectedDriverId = driverId
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ WebSocket 連接失敗", e)
                _uiState.value = _uiState.value.copy(
                    error = "WebSocket 連接失敗: ${e.message}"
                )
            } finally {
                isConnecting = false
            }
        }
    }

    /**
     * 斷開WebSocket（司機離線）
     */
    fun disconnectWebSocket() {
        viewModelScope.launch {
            webSocketManager.disconnect()
        }
    }

    /**
     * 更新司機狀態
     */
    fun updateDriverStatus(driverId: String, status: DriverAvailability) {
        android.util.Log.d("HomeViewModel", "========== 更新司機狀態 ==========")
        android.util.Log.d("HomeViewModel", "司機ID: $driverId")
        android.util.Log.d("HomeViewModel", "當前狀態: ${_uiState.value.driverStatus}")
        android.util.Log.d("HomeViewModel", "新狀態: $status")

        viewModelScope.launch {
            val currentStatus = _uiState.value.driverStatus

            // 先更新UI
            _uiState.value = _uiState.value.copy(
                driverStatus = status,
                isLoading = true
            )

            try {
                // 【關鍵修復1】如果從 OFFLINE 切換到其他狀態，先重新連接 WebSocket
                if (currentStatus == DriverAvailability.OFFLINE && status != DriverAvailability.OFFLINE) {
                    android.util.Log.d("HomeViewModel", "🔌 從離線狀態切回，重新連接 WebSocket")
                    webSocketManager.connect(driverId)

                    // 給一點時間讓 WebSocket 連接建立
                    kotlinx.coroutines.delay(500)
                }

                // 【關鍵修復2】通過 WebSocket 實時通知 server 狀態變化
                if (status == DriverAvailability.OFFLINE) {
                    // 離線前先發送狀態更新事件
                    android.util.Log.d("HomeViewModel", "📡 先發送離線狀態事件，再斷開連接")
                    webSocketManager.updateDriverStatus(driverId, status.name)

                    // 給一點時間讓事件發送出去
                    kotlinx.coroutines.delay(300)
                } else {
                    // 其他狀態變化立即發送
                    android.util.Log.d("HomeViewModel", "📡 發送狀態更新事件")
                    webSocketManager.updateDriverStatus(driverId, status.name)
                }

                // 呼叫API更新server端的司機狀態
                val result = repository.updateDriverStatus(driverId, status)

                result.onSuccess { driver ->
                    android.util.Log.d("HomeViewModel", "✅ Server端狀態已更新")
                    android.util.Log.d("HomeViewModel", "Server返回狀態: ${driver.availability}")

                    _uiState.value = _uiState.value.copy(
                        driverStatus = status,
                        isLoading = false,
                        error = null
                    )

                    // 如果狀態是OFFLINE，斷開WebSocket
                    if (status == DriverAvailability.OFFLINE) {
                        android.util.Log.d("HomeViewModel", "斷開WebSocket連接")
                        disconnectWebSocket()
                    }
                }.onFailure { error ->
                    android.util.Log.e("HomeViewModel", "❌ 更新狀態失敗: ${error.message}")
                    // 更新失敗，恢復原狀態
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "狀態更新失敗：${error.message}"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ 狀態更新異常: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "狀態更新異常：${e.message}"
                )
            }
        }
    }

    /**
     * 接受訂單
     */
    fun acceptOrder(orderId: String, driverId: String, driverName: String) {
        android.util.Log.d("HomeViewModel", "========== 接受訂單 ==========")
        android.util.Log.d("HomeViewModel", "訂單ID: $orderId")
        android.util.Log.d("HomeViewModel", "司機ID: $driverId")
        android.util.Log.d("HomeViewModel", "司機姓名: $driverName")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.acceptOrder(orderId, driverId, driverName)
                .onSuccess { order ->
                    android.util.Log.d("HomeViewModel", "✅ 接單成功")
                    android.util.Log.d("HomeViewModel", "訂單狀態: ${order.status}")

                    _uiState.value = _uiState.value.copy(
                        currentOrder = order,
                        driverStatus = DriverAvailability.ON_TRIP,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    android.util.Log.e("HomeViewModel", "❌ 接單失敗: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "接單失敗"
                    )
                }
        }
    }

    /**
     * 拒絕訂單
     */
    fun rejectOrder(orderId: String, driverId: String, reason: String = "司機忙碌") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.rejectOrder(orderId, driverId, reason)
                .onSuccess {
                    // 清除當前訂單
                    _uiState.value = _uiState.value.copy(
                        currentOrder = null,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "拒單失敗"
                    )
                }
        }
    }

    /**
     * 更新訂單狀態 - 標記已到達上車點
     */
    fun markArrived(orderId: String) {
        updateOrderStatus(orderId, "ARRIVED")
    }

    /**
     * 更新訂單狀態 - 開始行程
     */
    fun startTrip(orderId: String) {
        updateOrderStatus(orderId, "ON_TRIP")
    }

    /**
     * 更新訂單狀態 - 結束行程（進入結算）
     */
    fun endTrip(orderId: String) {
        updateOrderStatus(orderId, "SETTLING")
    }

    /**
     * 更新訂單狀態（通用方法）
     */
    private fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.updateOrderStatus(orderId, newStatus)
                .onSuccess { updatedOrder ->
                    _uiState.value = _uiState.value.copy(
                        currentOrder = updatedOrder,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "狀態更新失敗"
                    )
                }
        }
    }

    /**
     * 提交車資
     */
    fun submitFare(orderId: String, driverId: String, meterAmount: Int) {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "========== 提交車資 ==========")
            android.util.Log.d("HomeViewModel", "訂單ID: $orderId")
            android.util.Log.d("HomeViewModel", "車資: $meterAmount")

            // 保存當前訂單資訊用於評分
            val currentOrder = _uiState.value.currentOrder
            val passengerId = currentOrder?.passengerId ?: ""
            val passengerName = currentOrder?.passengerName ?: "乘客"

            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.submitFare(orderId, meterAmount, appDistanceMeters = null, appDurationSeconds = null)
                .onSuccess { updatedOrder ->
                    android.util.Log.d("HomeViewModel", "✅ 車資提交成功，訂單已完成")

                    // 車資提交成功，訂單完成，清除當前訂單並恢復司機狀態為可接單
                    // 同時設置待評分訂單
                    _uiState.value = _uiState.value.copy(
                        currentOrder = null,
                        driverStatus = DriverAvailability.AVAILABLE,
                        isLoading = false,
                        error = null,
                        pendingRating = PendingRating(
                            orderId = orderId,
                            passengerId = passengerId,
                            passengerName = passengerName
                        )
                    )

                    // 同步更新server端的司機狀態
                    android.util.Log.d("HomeViewModel", "正在將司機狀態恢復為可接單...")
                    repository.updateDriverStatus(driverId, DriverAvailability.AVAILABLE)
                        .onSuccess {
                            android.util.Log.d("HomeViewModel", "✅ 司機狀態已恢復為可接單")
                        }
                        .onFailure { error ->
                            android.util.Log.e("HomeViewModel", "⚠️ 狀態恢復失敗: ${error.message}")
                        }
                }
                .onFailure { error ->
                    android.util.Log.e("HomeViewModel", "❌ 車資提交失敗: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "車資提交失敗"
                    )
                }
        }
    }

    /**
     * 提交評分
     */
    fun submitRating(driverId: String, rating: Int, comment: String?) {
        val pendingRating = _uiState.value.pendingRating ?: return

        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "========== 提交評分 ==========")
            android.util.Log.d("HomeViewModel", "訂單ID: ${pendingRating.orderId}")
            android.util.Log.d("HomeViewModel", "評分: $rating")

            _uiState.value = _uiState.value.copy(isSubmittingRating = true)

            try {
                val request = com.hualien.taxidriver.data.remote.dto.SubmitRatingRequest(
                    orderId = pendingRating.orderId,
                    fromType = "driver",
                    fromId = driverId,
                    toType = "passenger",
                    toId = pendingRating.passengerId,
                    rating = rating,
                    comment = comment
                )

                val response = com.hualien.taxidriver.data.remote.RetrofitClient.apiService.submitRating(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    android.util.Log.d("HomeViewModel", "✅ 評分提交成功")
                    _uiState.value = _uiState.value.copy(
                        pendingRating = null,
                        isSubmittingRating = false
                    )
                } else {
                    android.util.Log.e("HomeViewModel", "❌ 評分提交失敗: ${response.body()?.error}")
                    _uiState.value = _uiState.value.copy(
                        pendingRating = null,
                        isSubmittingRating = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ 評分提交異常: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    pendingRating = null,
                    isSubmittingRating = false
                )
            }
        }
    }

    /**
     * 跳過評分
     */
    fun skipRating() {
        _uiState.value = _uiState.value.copy(pendingRating = null)
    }

    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ==================== 語音指令相關 ====================

    /**
     * 上傳音檔並獲取語音指令
     */
    fun transcribeAudio(audioFile: File, driverId: String) {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "========== 上傳語音檔案 ==========")
            android.util.Log.d("HomeViewModel", "檔案: ${audioFile.absolutePath}")
            android.util.Log.d("HomeViewModel", "大小: ${audioFile.length()} bytes")

            try {
                // 準備 multipart 請求
                val audioRequestBody = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val audioPart = MultipartBody.Part.createFormData(
                    "audio",
                    audioFile.name,
                    audioRequestBody
                )

                val driverIdBody = driverId.toRequestBody("text/plain".toMediaTypeOrNull())
                val currentStatusBody = _uiState.value.driverStatus.name.toRequestBody("text/plain".toMediaTypeOrNull())

                // 當前訂單資訊
                val currentOrder = _uiState.value.currentOrder
                val currentOrderIdBody = currentOrder?.orderId?.toRequestBody("text/plain".toMediaTypeOrNull())
                val currentOrderStatusBody = currentOrder?.status?.name?.toRequestBody("text/plain".toMediaTypeOrNull())
                val pickupAddressBody = currentOrder?.pickup?.address?.toRequestBody("text/plain".toMediaTypeOrNull())
                val destinationAddressBody = currentOrder?.destination?.address?.toRequestBody("text/plain".toMediaTypeOrNull())

                // 呼叫 API
                val response = RetrofitClient.apiService.transcribeAudio(
                    audio = audioPart,
                    driverId = driverIdBody,
                    currentStatus = currentStatusBody,
                    currentOrderId = currentOrderIdBody,
                    currentOrderStatus = currentOrderStatusBody,
                    pickupAddress = pickupAddressBody,
                    destinationAddress = destinationAddressBody
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true && result.command != null) {
                        android.util.Log.d("HomeViewModel", "✅ 語音轉錄成功")
                        android.util.Log.d("HomeViewModel", "指令: ${result.command.action}")
                        android.util.Log.d("HomeViewModel", "轉錄: ${result.command.transcription}")
                        android.util.Log.d("HomeViewModel", "信心度: ${result.command.confidence}")

                        _uiState.value = _uiState.value.copy(
                            lastTranscription = result.command.transcription,
                            lastVoiceCommand = result.command,
                            voiceError = null
                        )
                    } else {
                        android.util.Log.e("HomeViewModel", "❌ 語音轉錄失敗: ${result?.error}")
                        _uiState.value = _uiState.value.copy(
                            voiceError = result?.error ?: "語音辨識失敗"
                        )
                    }
                } else {
                    android.util.Log.e("HomeViewModel", "❌ API 錯誤: ${response.code()}")
                    _uiState.value = _uiState.value.copy(
                        voiceError = "伺服器錯誤 (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ 上傳失敗: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    voiceError = "上傳失敗: ${e.message}"
                )
            } finally {
                // 清理臨時檔案
                try {
                    audioFile.delete()
                } catch (e: Exception) {
                    // 忽略刪除錯誤
                }
            }
        }
    }

    /**
     * 清除語音指令狀態
     */
    fun clearVoiceCommand() {
        _uiState.value = _uiState.value.copy(
            lastVoiceCommand = null,
            lastTranscription = null,
            voiceError = null
        )
    }

    /**
     * 清除語音錯誤
     */
    fun clearVoiceError() {
        _uiState.value = _uiState.value.copy(voiceError = null)
    }

    override fun onCleared() {
        super.onCleared()
        disconnectWebSocket()
    }
}
