package com.hualien.taxidriver.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.data.remote.dto.DriverContext
import com.hualien.taxidriver.data.remote.dto.VoiceAction
import com.hualien.taxidriver.data.remote.dto.VoiceChatMessage
import com.hualien.taxidriver.data.remote.dto.VoiceChatState
import com.hualien.taxidriver.data.remote.dto.VoiceCommand
import com.hualien.taxidriver.data.remote.dto.VoiceTranscribeResponse
import com.hualien.taxidriver.data.repository.OrderRepository
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.domain.model.OrderStatus
import com.hualien.taxidriver.service.LocationService
import com.hualien.taxidriver.service.VoiceRecorderService
import com.hualien.taxidriver.utils.DataStoreManager
import com.hualien.taxidriver.utils.SlowTrafficTimer
import com.hualien.taxidriver.utils.VoiceAssistant
import com.hualien.taxidriver.utils.VoiceChatManager
import com.hualien.taxidriver.utils.VoiceCommandHandler
import kotlinx.coroutines.delay
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
    val queuedOrder: Order? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // 評分相關
    val pendingRating: PendingRating? = null,
    val isSubmittingRating: Boolean = false,
    // 語音指令相關
    val lastTranscription: String? = null,
    val lastVoiceCommand: VoiceCommand? = null,
    val voiceError: String? = null,
    // 自動接單相關
    val autoAcceptEnabled: Boolean = false,
    val lastAutoAcceptOrder: String? = null,  // 最後自動接單的訂單ID
    val autoAcceptMessage: String? = null,    // 自動接單提示訊息
    // 語音接單相關
    val isVoiceListening: Boolean = false,           // 是否正在等待語音輸入
    val lastAnnouncedOrderId: String? = null,        // 最後播報的訂單ID（避免重複播報）
    val voiceAutoListenEnabled: Boolean = true,      // 是否啟用語音自動監聯（新訂單播報後）
    // 今日統計
    val todayOrderCount: Int = 0,
    val todayEarnings: Int = 0,
    val todayDistance: Double = 0.0,
    // 低速計時（駐車費）— SETTLING 時由 SlowTrafficTimer 提供，給 FareDialog 顯示「建議加收」
    // null 表示行程尚未結束或無資料
    val slowTrafficSeconds: Int? = null
)

/**
 * Home頁面ViewModel
 */
class HomeViewModel : ViewModel() {

    private val repository = OrderRepository()
    private val webSocketManager = WebSocketManager.getInstance()

    // 低速計時器（駐車費）— start() 在 ON_TRIP, stop() 在 SETTLING, snapshot 給 FareDialog
    private val slowTrafficTimer = SlowTrafficTimer()

    // DataStoreManager — 由 initSlowTrafficPersist(context) 注入，用於跨 App 重啟保留累計值
    private var dataStoreManager: DataStoreManager? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 追蹤 WebSocket 連接狀態，防止重複連接
    private var connectedDriverId: String? = null
    private var isConnecting = false

    // 語音服務（需要透過 initVoiceServices 初始化）
    private var voiceAssistant: VoiceAssistant? = null
    private var voiceRecorderService: VoiceRecorderService? = null
    private var voiceCommandHandler: VoiceCommandHandler? = null
    private var voiceServicesInitialized = false

    // 當前司機資訊（用於語音接單）
    private var currentDriverId: String? = null
    private var currentDriverName: String? = null

    // ==================== 語音對講相關 ====================
    private var voiceChatManager: VoiceChatManager? = null

    // 語音對講歷史記錄
    private val _voiceChatHistory = MutableStateFlow<List<VoiceChatMessage>>(emptyList())
    val voiceChatHistory: StateFlow<List<VoiceChatMessage>> = _voiceChatHistory.asStateFlow()

    // 語音對講狀態
    private val _voiceChatState = MutableStateFlow(VoiceChatState.IDLE)
    val voiceChatState: StateFlow<VoiceChatState> = _voiceChatState.asStateFlow()

    // 是否正在錄音
    private val _voiceChatRecording = MutableStateFlow(false)
    val voiceChatRecording: StateFlow<Boolean> = _voiceChatRecording.asStateFlow()

    // 錄音振幅
    private val _voiceChatAmplitude = MutableStateFlow(0)
    val voiceChatAmplitude: StateFlow<Int> = _voiceChatAmplitude.asStateFlow()

    // 是否顯示對講面板
    private val _showVoiceChatPanel = MutableStateFlow(false)
    val showVoiceChatPanel: StateFlow<Boolean> = _showVoiceChatPanel.asStateFlow()

    // 未讀訊息數量
    private val _voiceChatUnreadCount = MutableStateFlow(0)
    val voiceChatUnreadCount: StateFlow<Int> = _voiceChatUnreadCount.asStateFlow()

    init {
        android.util.Log.d("HomeViewModel", "========== HomeViewModel 初始化 ==========")

        // 監聽 LocationService 速度推送，餵給 SlowTrafficTimer
        // timer 自己用 active flag 控制要不要累計（行程內才算），不需要在這裡判斷
        viewModelScope.launch {
            LocationService.speedFlow.collect { speed ->
                if (speed != null) slowTrafficTimer.onSpeedUpdate(speed)
            }
        }

        // 監聯 WebSocket 連接狀態
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "開始監聽 WebSocket 連接狀態...")
            webSocketManager.isConnected.collect { isConnected ->
                android.util.Log.d("HomeViewModel", "========== WebSocket 連接狀態變化 ==========")
                android.util.Log.d("HomeViewModel", "連接狀態: ${if (isConnected) "✅ 已連接" else "❌ 未連接"}")
                android.util.Log.d("HomeViewModel", "connectedDriverId: $connectedDriverId, isConnecting: $isConnecting")

                // 注意：斷線時不清除 connectedDriverId，讓 WebSocketManager 自動重連
                // 只在手動斷開時（disconnectWebSocket）才清除
                if (!isConnected) {
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

                    // 忽略已完成或已取消的訂單（防止重複出現）
                    if (order.status == OrderStatus.DONE || order.status == OrderStatus.CANCELLED) {
                        android.util.Log.w("HomeViewModel", "⚠️ 忽略已完成/取消的訂單: ${order.orderId}")
                        webSocketManager.clearOrderOffer()
                        return@collect
                    }

                    if (order.isQueuedOrder()) {
                        _uiState.value = _uiState.value.copy(
                            queuedOrder = order,
                            error = null
                        )
                        android.util.Log.d("HomeViewModel", "✅ 已更新下一單: ${order.orderId}")
                    } else {
                        // 如果已有進行中的主單，不覆蓋（防止誤操作）
                        val existingOrder = _uiState.value.currentOrder
                        if (existingOrder != null &&
                            existingOrder.status != OrderStatus.WAITING &&
                            existingOrder.status != OrderStatus.OFFERED) {
                            android.util.Log.w("HomeViewModel", "⚠️ 已有進行中主單 ${existingOrder.orderId}，忽略新推送")
                            return@collect
                        }

                        _uiState.value = _uiState.value.copy(
                            currentOrder = order,
                            error = null
                        )

                        android.util.Log.d("HomeViewModel", "✅ UI 狀態已更新，主單卡片應該顯示")
                    }
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
                    val queuedOrder = _uiState.value.queuedOrder

                    // 如果是當前訂單的狀態更新
                    if (currentOrder != null && currentOrder.orderId == updatedOrder.orderId) {
                        when (updatedOrder.status.name) {
                            "CANCELLED" -> {
                                android.util.Log.d("HomeViewModel", "⚠️ 主單已取消，處理交接")
                                handleCurrentOrderCleared("乘客已取消訂單")
                            }
                            "DONE" -> handleCurrentOrderCleared(null)
                            else -> {
                                // 其他狀態更新，更新當前訂單
                                android.util.Log.d("HomeViewModel", "✅ 訂單狀態已更新")
                                _uiState.value = _uiState.value.copy(
                                    currentOrder = updatedOrder,
                                    error = null
                                )
                            }
                        }
                    } else if (queuedOrder != null && queuedOrder.orderId == updatedOrder.orderId) {
                        when (updatedOrder.status) {
                            OrderStatus.CANCELLED, OrderStatus.DONE -> {
                                android.util.Log.d("HomeViewModel", "⚠️ 下一單已取消/結束，清除下一單")
                                _uiState.value = _uiState.value.copy(
                                    queuedOrder = null,
                                    error = if (updatedOrder.status == OrderStatus.CANCELLED) "下一單已取消" else _uiState.value.error
                                )
                            }
                            OrderStatus.ACCEPTED, OrderStatus.ARRIVED, OrderStatus.ON_TRIP, OrderStatus.SETTLING -> {
                                android.util.Log.d("HomeViewModel", "✅ 下一單已升為主單")
                                _uiState.value = _uiState.value.copy(
                                    currentOrder = updatedOrder,
                                    queuedOrder = null,
                                    driverStatus = DriverAvailability.ON_TRIP,
                                    error = null
                                )
                            }
                            else -> {
                                android.util.Log.d("HomeViewModel", "✅ 下一單狀態已更新")
                                _uiState.value = _uiState.value.copy(
                                    queuedOrder = updatedOrder,
                                    error = null
                                )
                            }
                        }
                    }
                }
            }
        }

        // 監聽語音對講訊息
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "開始監聽語音對講訊息...")
            webSocketManager.voiceChatMessage.collect { message ->
                if (message != null) {
                    android.util.Log.d("HomeViewModel", "收到語音對講訊息: ${message.senderName}說「${message.messageText}」")

                    // 只處理來自乘客的訊息（避免處理自己發送的）
                    if (message.isFromPassenger()) {
                        voiceChatManager?.receiveMessage(message)

                        // 如果面板未開啟，增加未讀計數
                        if (!_showVoiceChatPanel.value) {
                            _voiceChatUnreadCount.value = _voiceChatUnreadCount.value + 1
                        }
                    }

                    // 清除 WebSocket 中的訊息，避免重複處理
                    webSocketManager.clearVoiceChatMessage()
                }
            }
        }

        // 監聽電話叫車催單通知
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "開始監聯催單通知...")
            webSocketManager.orderUrge.collect { urgeInfo ->
                if (urgeInfo != null) {
                    android.util.Log.d("HomeViewModel", "⚡ 收到催單: 訂單 ${urgeInfo.orderId}")

                    // 語音提示催單
                    voiceAssistant?.speak("注意，乘客催促訂單，請盡快前往")

                    _uiState.value = _uiState.value.copy(
                        error = "乘客催促：${urgeInfo.message}"
                    )

                    // 清除催單通知
                    webSocketManager.clearOrderUrge()
                }
            }
        }
    }

    /**
     * 確認電話訂單目的地
     */
    fun confirmDestination(orderId: String, confirmedAddress: String? = null) {
        viewModelScope.launch {
            try {
                val currentOrder = _uiState.value.currentOrder ?: return@launch

                val data = org.json.JSONObject().apply {
                    put("orderId", orderId)
                    put("driverId", currentDriverId ?: "")
                    if (confirmedAddress != null) {
                        put("confirmedAddress", confirmedAddress)
                    }
                    currentOrder.destination?.let { dest ->
                        put("confirmedAddress", dest.address ?: "")
                        put("confirmedLat", dest.latitude)
                        put("confirmedLng", dest.longitude)
                    }
                }

                // 呼叫 API 更新訂單狀態（目的地確認為本地狀態更新）
                RetrofitClient.apiService.updateOrderStatus(
                    orderId,
                    com.hualien.taxidriver.data.remote.dto.UpdateOrderStatusRequest(
                        status = currentOrder.statusString,
                        driverId = currentDriverId
                    )
                )

                // 更新本地訂單狀態
                _uiState.value = _uiState.value.copy(
                    currentOrder = currentOrder.copy(destinationConfirmed = true),
                    error = null
                )

                android.util.Log.d("HomeViewModel", "✅ 目的地已確認: $orderId")
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "確認目的地失敗", e)
                _uiState.value = _uiState.value.copy(
                    error = "確認目的地失敗: ${e.message}"
                )
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

        // 【重要】連接前先清除舊訂單，防止殘留
        android.util.Log.d("HomeViewModel", "清除舊訂單狀態...")
        _uiState.value = _uiState.value.copy(currentOrder = null, queuedOrder = null)

        isConnecting = true
        viewModelScope.launch {
            try {
                webSocketManager.connect(driverId)
                connectedDriverId = driverId

                // 連接成功後，從服務器獲取當前進行中的訂單（恢復訂單狀態）
                fetchActiveOrder(driverId)
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
     * 獲取當前進行中的訂單（用於 APP 重開時恢復訂單狀態）
     */
    private fun fetchActiveOrder(driverId: String) {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "========== 獲取活動訂單 ==========")
            android.util.Log.d("HomeViewModel", "司機ID: $driverId")

            try {
                // 嘗試獲取各種進行中狀態的主單
                val activeStatuses = listOf("ACCEPTED", "ARRIVED", "ON_TRIP", "SETTLING")
                var activeOrder: Order? = null

                for (status in activeStatuses) {
                    val response = RetrofitClient.apiService.getOrders(
                        driverId = driverId,
                        status = status
                    )

                    if (response.isSuccessful) {
                        val orderDtos = response.body()?.orders ?: emptyList()
                        if (orderDtos.isNotEmpty()) {
                            activeOrder = orderDtos.first().toDomainOrder()
                            android.util.Log.d("HomeViewModel", "✅ 找到活動主單: ${activeOrder.orderId}, 狀態: ${activeOrder.status}")
                            break
                        }
                    }
                }

                val queuedResponse = RetrofitClient.apiService.getOrders(
                    driverId = driverId,
                    status = OrderStatus.QUEUED.name
                )
                val queuedOrder = if (queuedResponse.isSuccessful) {
                    queuedResponse.body()?.orders?.firstOrNull()?.toDomainOrder()
                } else {
                    null
                }

                if (activeOrder != null || queuedOrder != null) {
                    // Phase C+1a：恢復 ON_TRIP 訂單時，從 DataStore 讀回 SlowTrafficTimer 累計值繼續計時
                    if (activeOrder?.status == OrderStatus.ON_TRIP) {
                        val restoredSec = dataStoreManager?.getIdleSeconds(activeOrder.orderId) ?: 0
                        if (restoredSec > 0) {
                            android.util.Log.d("HomeViewModel", "🔄 恢復低速計時: ${activeOrder.orderId} → $restoredSec 秒")
                        }
                        slowTrafficTimer.start(activeOrder.orderId, restoredSeconds = restoredSec)
                    }
                    _uiState.value = _uiState.value.copy(
                        currentOrder = activeOrder,
                        queuedOrder = queuedOrder,
                        driverStatus = if (activeOrder != null || queuedOrder != null) DriverAvailability.ON_TRIP else _uiState.value.driverStatus
                    )
                    return@launch
                }

                android.util.Log.d("HomeViewModel", "📭 無活動訂單與下一單")
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ 獲取活動訂單失敗: ${e.message}", e)
            }
        }
    }

    /**
     * 斷開WebSocket（司機離線）
     */
    fun disconnectWebSocket() {
        android.util.Log.d("HomeViewModel", "========== 手動斷開 WebSocket ==========")
        connectedDriverId = null
        isConnecting = false
        webSocketManager.cancelReconnect()  // 停止自動重連
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

        // 立即停止語音播報、語音錄音和清除語音狀態（不管 API 是否成功）
        voiceAssistant?.stop()
        voiceRecorderService?.cancelRecording()
        clearVoiceOrderState()
        webSocketManager.clearOrderOffer()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val pendingQueuedOrder = _uiState.value.queuedOrder?.takeIf { it.orderId == orderId }

            repository.acceptOrder(orderId, driverId, driverName)
                .onSuccess { order ->
                    android.util.Log.d("HomeViewModel", "✅ 接單成功")
                    android.util.Log.d("HomeViewModel", "訂單狀態: ${order.status}")

                    if (pendingQueuedOrder != null || order.isQueuedOrder()) {
                        _uiState.value = _uiState.value.copy(
                            queuedOrder = mergeQueuedOrderMetadata(order, pendingQueuedOrder),
                            driverStatus = DriverAvailability.ON_TRIP,
                            isLoading = false,
                            error = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            currentOrder = order,
                            driverStatus = DriverAvailability.ON_TRIP,
                            isLoading = false,
                            error = null
                        )
                    }
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
        // 立即停止語音播報、語音錄音和清除語音狀態（不管 API 是否成功）
        voiceAssistant?.stop()
        voiceRecorderService?.cancelRecording()
        clearVoiceOrderState()

        // 立即清除訂單（樂觀更新），防止 Whisper 非同步回調中
        // currentOrder?.status == OFFERED 仍為 true，導致語音循環重啟
        val isQueuedReject = _uiState.value.queuedOrder?.orderId == orderId
        _uiState.value = _uiState.value.copy(
            currentOrder = if (isQueuedReject) _uiState.value.currentOrder else null,
            queuedOrder = if (isQueuedReject) null else _uiState.value.queuedOrder,
            isLoading = true
        )

        // 清除 WebSocket 層的訂單，防止 StateFlow 殘留數據在 ViewModel 重建時觸發重複播報
        if (!isQueuedReject) {
            webSocketManager.clearOrderOffer()
        }

        viewModelScope.launch {
            repository.rejectOrder(orderId, driverId, reason)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
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
     * 啟動 SlowTrafficTimer，行程中累計速度 < 5 km/h 的秒數
     * Phase C+1a：傳 orderId 給 timer，每 5 秒持久化到 DataStore
     */
    fun startTrip(orderId: String) {
        slowTrafficTimer.start(orderId)
        updateOrderStatus(orderId, "ON_TRIP")
    }

    /**
     * 更新訂單狀態 - 結束行程（進入結算）
     * 停止 SlowTrafficTimer 並 snapshot 累計秒數，給 FareDialog 顯示「建議加收」
     */
    fun endTrip(orderId: String) {
        slowTrafficTimer.stop()
        val seconds = slowTrafficTimer.snapshotSeconds()
        _uiState.value = _uiState.value.copy(slowTrafficSeconds = seconds)
        android.util.Log.d("HomeViewModel", "✅ 行程結束 — 低速累計 $seconds 秒")
        updateOrderStatus(orderId, "SETTLING")
    }

    /**
     * 更新訂單狀態（通用方法）
     */
    private fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val isQueuedUpdate = _uiState.value.queuedOrder?.orderId == orderId

            repository.updateOrderStatus(orderId, newStatus)
                .onSuccess { updatedOrder ->
                    _uiState.value = if (isQueuedUpdate || updatedOrder.isQueuedOrder()) {
                        _uiState.value.copy(
                            queuedOrder = updatedOrder,
                            isLoading = false,
                            error = null
                        )
                    } else {
                        _uiState.value.copy(
                            currentOrder = updatedOrder,
                            isLoading = false,
                            error = null
                        )
                    }
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
     * 確認愛心卡（司機已目視確認實體卡片）
     */
    fun confirmLoveCard(orderId: String, driverId: String) {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "確認愛心卡: $orderId")
            repository.updateOrderSubsidy(orderId, driverId, "CONFIRM")
                .onSuccess { updatedOrder ->
                    _uiState.value = _uiState.value.copy(currentOrder = updatedOrder, error = null)
                    android.util.Log.d("HomeViewModel", "愛心卡已確認")
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(error = "愛心卡確認失敗: ${err.message}")
                }
        }
    }

    /**
     * 取消愛心卡（乘客無法出示卡片，改一般計費）
     */
    fun cancelLoveCard(orderId: String, driverId: String) {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "取消愛心卡: $orderId")
            repository.updateOrderSubsidy(orderId, driverId, "CANCEL")
                .onSuccess { updatedOrder ->
                    _uiState.value = _uiState.value.copy(currentOrder = updatedOrder, error = null)
                    android.util.Log.d("HomeViewModel", "愛心卡已取消，改為一般計費")
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(error = "取消愛心卡失敗: ${err.message}")
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

            // 計算行程距離和時長
            // 優先用預估距離(tripDistance)，若無則用 FareCalculator 由跳錶金額反推（粗估）
            // 反推會自動依當下時間套日/夜費率，但不考慮春節 / 低速計時 surcharge
            val distanceKm = currentOrder?.tripDistance
                ?: com.hualien.taxidriver.utils.FareCalculator.estimateDistanceFromFare(meterAmount)
            val durationMinutes = currentOrder?.startedAt?.let { startedAt ->
                ((System.currentTimeMillis() - startedAt) / 60000).toInt()
            }

            android.util.Log.d("HomeViewModel", "行程距離: $distanceKm km (${if (currentOrder?.tripDistance != null) "預估" else "反推"})")
            android.util.Log.d("HomeViewModel", "行程時長: ${durationMinutes ?: "未知"} 分鐘")

            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.submitFare(orderId, meterAmount, distanceKm, durationMinutes)
                .onSuccess { updatedOrder ->
                    android.util.Log.d("HomeViewModel", "✅ 車資提交成功，訂單已完成")

                    // 車資提交成功，訂單完成，清除當前訂單並恢復司機狀態為可接單
                    // 同時設置待評分訂單
                    // 清空 slowTrafficSeconds + DataStore 對應 orderId 紀錄（避免累積垃圾）
                    dataStoreManager?.clearIdleSeconds(orderId)
                    _uiState.value = _uiState.value.copy(
                        currentOrder = null,
                        queuedOrder = _uiState.value.queuedOrder,
                        driverStatus = if (_uiState.value.queuedOrder != null) DriverAvailability.ON_TRIP else DriverAvailability.AVAILABLE,
                        isLoading = false,
                        slowTrafficSeconds = null,
                        error = null,
                        pendingRating = PendingRating(
                            orderId = orderId,
                            passengerId = passengerId,
                            passengerName = passengerName
                        )
                    )

                    if (_uiState.value.queuedOrder != null) {
                        promoteQueuedOrderToCurrent("主單完成，自動切換到下一單")
                    }

                    // 同步更新server端的司機狀態
                    if (_uiState.value.currentOrder == null) {
                        android.util.Log.d("HomeViewModel", "正在將司機狀態恢復為可接單...")
                        repository.updateDriverStatus(driverId, DriverAvailability.AVAILABLE)
                            .onSuccess {
                                android.util.Log.d("HomeViewModel", "✅ 司機狀態已恢復為可接單")
                            }
                            .onFailure { error ->
                                android.util.Log.e("HomeViewModel", "⚠️ 狀態恢復失敗: ${error.message}")
                            }
                    }

                    // 刷新今日統計
                    loadTodayStats(driverId)
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

    private fun mergeQueuedOrderMetadata(updatedOrder: Order, originalOrder: Order?): Order {
        return updatedOrder.copy(
            statusString = if (updatedOrder.status == OrderStatus.ACCEPTED) OrderStatus.QUEUED.name else updatedOrder.statusString,
            queuePosition = updatedOrder.queuePosition ?: originalOrder?.queuePosition ?: 2,
            queuedAfterOrderId = updatedOrder.queuedAfterOrderId ?: originalOrder?.queuedAfterOrderId ?: _uiState.value.currentOrder?.orderId,
            predictedHandoverAt = updatedOrder.predictedHandoverAt ?: originalOrder?.predictedHandoverAt,
            assignmentMode = updatedOrder.assignmentMode ?: originalOrder?.assignmentMode ?: "STACKED_1P1"
        )
    }

    private fun handleCurrentOrderCleared(message: String?) {
        webSocketManager.clearOrderOffer()
        if (_uiState.value.queuedOrder != null) {
            promoteQueuedOrderToCurrent(message)
        } else {
            _uiState.value = _uiState.value.copy(
                currentOrder = null,
                driverStatus = DriverAvailability.AVAILABLE,
                error = message
            )
        }
    }

    private fun promoteQueuedOrderToCurrent(message: String?) {
        val queuedOrder = _uiState.value.queuedOrder ?: return
        _uiState.value = _uiState.value.copy(
            currentOrder = queuedOrder.promoteQueuedToCurrent(),
            queuedOrder = null,
            driverStatus = DriverAvailability.ON_TRIP,
            error = message
        )
    }

    // ==================== 今日統計相關 ====================

    /**
     * 加載今日統計數據
     */
    fun loadTodayStats(driverId: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getEarningsStats(driverId, "today")
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        todayOrderCount = data.earnings.orderCount,
                        todayEarnings = data.earnings.totalAmount,
                        todayDistance = data.earnings.totalDistance
                    )
                    android.util.Log.d("HomeViewModel", "✅ 今日統計加載成功: 訂單${data.earnings.orderCount}, 收入${data.earnings.totalAmount}, 里程${data.earnings.totalDistance}km")
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ 今日統計加載失敗: ${e.message}")
            }
        }
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

    // ==================== 語音接單相關 ====================

    /**
     * 初始化低速計時器持久化（Phase C+1a）
     * UI 層在拿到 Context 後呼叫一次（idempotent）。
     * 設定 timer 的 onPersist callback，每 5 秒寫一次 DataStore，
     * App 被殺重啟時 fetchActiveOrder() 可從 DataStore 讀回累計值繼續計時。
     */
    fun initSlowTrafficPersist(context: Context) {
        if (dataStoreManager != null) return
        val dsm = DataStoreManager.getInstance(context)
        dataStoreManager = dsm
        slowTrafficTimer.onPersist = { orderId, seconds ->
            viewModelScope.launch {
                try {
                    dsm.saveIdleSeconds(orderId, seconds)
                } catch (e: Exception) {
                    android.util.Log.w("HomeViewModel", "持久化低速秒數失敗: ${e.message}")
                }
            }
        }
        android.util.Log.d("HomeViewModel", "✅ 低速計時器持久化已啟用")
    }

    /**
     * 初始化語音服務
     * 必須在 UI 層傳入 Context 後呼叫
     */
    fun initVoiceServices(context: Context, driverId: String, driverName: String) {
        if (voiceServicesInitialized) {
            android.util.Log.d("HomeViewModel", "語音服務已初始化，跳過")
            return
        }

        android.util.Log.d("HomeViewModel", "========== 初始化語音服務 ==========")
        currentDriverId = driverId
        currentDriverName = driverName

        voiceAssistant = VoiceAssistant(context)
        voiceRecorderService = VoiceRecorderService(context)
        voiceCommandHandler = VoiceCommandHandler(context, voiceAssistant!!).apply {
            setCallback(createVoiceCommandCallback())
        }

        voiceServicesInitialized = true
        android.util.Log.d("HomeViewModel", "✅ 語音服務初始化完成")
    }

    /**
     * 建立語音指令回調
     */
    private fun createVoiceCommandCallback(): VoiceCommandHandler.CommandCallback {
        return object : VoiceCommandHandler.CommandCallback {
            override fun onAcceptOrder(orderId: String?) {
                val id = orderId ?: _uiState.value.currentOrder?.orderId ?: return
                val driverId = currentDriverId ?: return
                val driverName = currentDriverName ?: "司機"
                acceptOrder(id, driverId, driverName)
            }

            override fun onRejectOrder(orderId: String?, reason: String?) {
                val id = orderId ?: _uiState.value.currentOrder?.orderId ?: return
                val driverId = currentDriverId ?: return
                rejectOrder(id, driverId, reason ?: "司機不便接單")
            }

            override fun onMarkArrived(orderId: String?) {
                val id = orderId ?: _uiState.value.currentOrder?.orderId ?: return
                markArrived(id)
            }

            override fun onStartTrip(orderId: String?) {
                val id = orderId ?: _uiState.value.currentOrder?.orderId ?: return
                startTrip(id)
            }

            override fun onEndTrip(orderId: String?) {
                val id = orderId ?: _uiState.value.currentOrder?.orderId ?: return
                endTrip(id)
            }

            override fun onUpdateStatus(status: DriverAvailability) {
                val driverId = currentDriverId ?: return
                updateDriverStatus(driverId, status)
            }

            override fun onQueryEarnings() {
                // 查詢收入（可連接到 EarningsViewModel）
                android.util.Log.d("HomeViewModel", "語音查詢收入")
            }

            override fun onNavigate(destination: String?) {
                // 開啟導航（可連接到外部導航 App）
                android.util.Log.d("HomeViewModel", "語音導航到: $destination")
            }

            override fun onEmergency() {
                // 緊急求助
                android.util.Log.d("HomeViewModel", "語音緊急求助")
            }

            override fun getCurrentOrder(): Order? = _uiState.value.currentOrder

            override fun getCurrentStatus(): DriverAvailability = _uiState.value.driverStatus
        }
    }

    /**
     * 播報新訂單（語音接單核心方法）
     * 在新訂單到達時呼叫
     */
    fun announceNewOrder(order: Order) {
        if (!voiceServicesInitialized) {
            android.util.Log.w("HomeViewModel", "語音服務未初始化，跳過播報")
            return
        }

        // 避免重複播報同一訂單
        if (order.orderId == _uiState.value.lastAnnouncedOrderId) {
            android.util.Log.d("HomeViewModel", "訂單 ${order.orderId} 已播報過，跳過")
            return
        }

        android.util.Log.d("HomeViewModel", "========== 播報新訂單 ==========")
        android.util.Log.d("HomeViewModel", "訂單ID: ${order.orderId}")

        val pickup = order.pickup.address ?: "未知地點"
        val destination = order.destination?.address ?: "未指定目的地"
        val fare = order.estimatedFare
        val tripDistance = order.tripDistance
        val etaToPickup = order.etaToPickup

        val message = buildString {
            append("您有新訂單，")

            // 訂單來源
            if (order.isPhoneOrder()) {
                append("電話叫車，")
            }

            // 補貼類型
            val subsidyName = order.getSubsidyDisplayName()
            if (subsidyName.isNotEmpty()) {
                append("${subsidyName}，")
            }

            // 寵物資訊
            val petName = order.getPetDisplayName()
            if (petName.isNotEmpty()) {
                append("${petName}，")
            }

            // 到客人距離
            etaToPickup?.let { eta ->
                append("${eta}分鐘車程，")
            }

            // 從哪到哪
            append("從 $pickup 到 $destination")

            // 行程距離
            tripDistance?.let { distance ->
                append("，行程約 ${String.format("%.1f", distance)} 公里")
            }

            // 預估車資
            fare?.let { append("，預估車資${it}元") }

            // 電話訂單需確認目的地
            if (order.needsDestinationConfirmation()) {
                append("，注意目的地需確認")
            }

            append("。說「接」接單，說「不要」拒絕")
        }

        // 更新狀態：標記已播報
        _uiState.value = _uiState.value.copy(
            lastAnnouncedOrderId = order.orderId
        )

        // 播報訂單，完成後自動開始語音監聽
        voiceAssistant?.speakWithCallback(
            message = message,
            priority = VoiceAssistant.Priority.URGENT,
            onComplete = {
                if (_uiState.value.voiceAutoListenEnabled &&
                    _uiState.value.currentOrder?.status == OrderStatus.OFFERED) {
                    android.util.Log.d("HomeViewModel", "播報完成，準備開始語音監聽")
                    // 使用 viewModelScope 確保在正確的協程上下文中執行
                    viewModelScope.launch {
                        // 短暫延遲，避免錄到 TTS 回音
                        kotlinx.coroutines.delay(300)
                        // 再次確認訂單仍為 OFFERED（防止延遲期間訂單被拒絕/接受）
                        if (_uiState.value.currentOrder?.status == OrderStatus.OFFERED) {
                            android.util.Log.d("HomeViewModel", "開始語音監聽")
                            startVoiceListeningForOrder()
                        }
                    }
                }
            }
        )
    }

    /**
     * 開始語音監聽（等待司機語音回應）
     */
    fun startVoiceListeningForOrder() {
        if (!voiceServicesInitialized) {
            android.util.Log.w("HomeViewModel", "⚠️ 語音服務未初始化，無法開始監聽")
            return
        }

        if (voiceRecorderService == null) {
            android.util.Log.e("HomeViewModel", "❌ voiceRecorderService 為 null")
            return
        }

        android.util.Log.d("HomeViewModel", "========== 開始語音監聽 ==========")
        android.util.Log.d("HomeViewModel", "當前訂單: ${_uiState.value.currentOrder?.orderId}")

        _uiState.value = _uiState.value.copy(
            isVoiceListening = true
        )

        voiceRecorderService?.startRecording { audioFile ->
            android.util.Log.d("HomeViewModel", "✅ 錄音完成: ${audioFile.name} (${audioFile.length()} bytes)")
            _uiState.value = _uiState.value.copy(isVoiceListening = false)

            // 上傳音檔進行識別
            currentDriverId?.let { driverId ->
                android.util.Log.d("HomeViewModel", "上傳識別，司機ID: $driverId")
                transcribeAudioForOrder(audioFile, driverId)
            } ?: run {
                android.util.Log.e("HomeViewModel", "❌ currentDriverId 為 null，無法識別")
            }
        }
    }

    /**
     * 停止語音監聽
     */
    fun stopVoiceListening() {
        voiceRecorderService?.stopRecording()
        _uiState.value = _uiState.value.copy(isVoiceListening = false)
    }

    /**
     * 取消語音監聽
     */
    fun cancelVoiceListening() {
        voiceRecorderService?.cancelRecording()
        _uiState.value = _uiState.value.copy(isVoiceListening = false)
    }

    /**
     * 專門用於訂單語音識別的方法
     */
    private fun transcribeAudioForOrder(audioFile: File, driverId: String) {
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "========== 語音識別（訂單模式）==========")

            // 如果訂單已不是 OFFERED 狀態（例如已接單/已拒絕），跳過識別
            if (_uiState.value.currentOrder?.status != OrderStatus.OFFERED) {
                android.util.Log.d("HomeViewModel", "⚠️ 訂單已非 OFFERED 狀態，跳過語音識別")
                try { audioFile.delete() } catch (_: Exception) {}
                return@launch
            }

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
                        android.util.Log.d("HomeViewModel", "✅ 語音識別成功")
                        android.util.Log.d("HomeViewModel", "指令: ${result.command.action}")
                        android.util.Log.d("HomeViewModel", "轉錄: ${result.command.transcription}")
                        android.util.Log.d("HomeViewModel", "信心度: ${result.command.confidence}")

                        // 處理語音指令
                        handleVoiceOrderCommand(result.command)
                    } else {
                        android.util.Log.e("HomeViewModel", "❌ 語音識別失敗: ${result?.error}")
                        handleVoiceRecognitionError("抱歉沒聽清楚")
                    }
                } else {
                    android.util.Log.e("HomeViewModel", "❌ API 錯誤: ${response.code()}")
                    handleVoiceRecognitionError("識別失敗")
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ 語音識別異常: ${e.message}", e)
                handleVoiceRecognitionError("識別失敗")
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
     * 處理語音識別到的訂單指令
     */
    private fun handleVoiceOrderCommand(command: VoiceCommand) {
        android.util.Log.d("HomeViewModel", "========== 處理語音訂單指令 ==========")
        android.util.Log.d("HomeViewModel", "動作: ${command.action}, 信心度: ${command.confidence}")

        // 優先檢查是否有待確認的指令
        val pendingCommand = _uiState.value.lastVoiceCommand
        if (pendingCommand != null) {
            android.util.Log.d("HomeViewModel", "有待確認指令: ${pendingCommand.action}")

            // 判斷是否為確認：說「接」「接單」「好」「對」等都算確認
            val isAcceptConfirmation = pendingCommand.action == VoiceAction.ACCEPT_ORDER &&
                (command.action == VoiceAction.ACCEPT_ORDER || isConfirmation(command.transcription))
            val isRejectConfirmation = pendingCommand.action == VoiceAction.REJECT_ORDER &&
                (command.action == VoiceAction.REJECT_ORDER || isConfirmation(command.transcription))

            when {
                isAcceptConfirmation -> {
                    android.util.Log.d("HomeViewModel", "✅ 確認接單")
                    voiceAssistant?.speak("好的，已接受訂單", VoiceAssistant.Priority.HIGH)
                    voiceCommandHandler?.confirmAndExecute(pendingCommand)
                    clearVoiceOrderState()
                    return
                }
                isRejectConfirmation -> {
                    android.util.Log.d("HomeViewModel", "✅ 確認拒單")
                    voiceAssistant?.speak("好的，已拒絕訂單", VoiceAssistant.Priority.HIGH)
                    voiceCommandHandler?.confirmAndExecute(pendingCommand)
                    clearVoiceOrderState()
                    return
                }
                isRejection(command.transcription) -> {
                    android.util.Log.d("HomeViewModel", "取消操作")
                    voiceAssistant?.speak("取消操作", VoiceAssistant.Priority.NORMAL)
                    clearVoiceOrderState()
                    return
                }
                // 如果用戶在確認接單時說了「不要」或「拒絕」，切換到拒單
                pendingCommand.action == VoiceAction.ACCEPT_ORDER && command.action == VoiceAction.REJECT_ORDER -> {
                    android.util.Log.d("HomeViewModel", "切換到拒單")
                    voiceAssistant?.speak("好的，已拒絕訂單", VoiceAssistant.Priority.HIGH)
                    voiceCommandHandler?.confirmAndExecute(command)
                    clearVoiceOrderState()
                    return
                }
                // 如果用戶在確認拒單時說了「接」或「接單」，切換到接單
                pendingCommand.action == VoiceAction.REJECT_ORDER && command.action == VoiceAction.ACCEPT_ORDER -> {
                    android.util.Log.d("HomeViewModel", "切換到接單")
                    voiceAssistant?.speak("好的，已接受訂單", VoiceAssistant.Priority.HIGH)
                    voiceCommandHandler?.confirmAndExecute(command)
                    clearVoiceOrderState()
                    return
                }
            }
        }

        // 沒有待確認指令，正常處理
        when (command.action) {
            VoiceAction.ACCEPT_ORDER -> {
                if (command.confidence >= 0.6f) {
                    // 信心度足夠，直接接單（降低閾值從0.7到0.6）
                    voiceAssistant?.speak("好的，已接受訂單", VoiceAssistant.Priority.HIGH)
                    voiceCommandHandler?.confirmAndExecute(command)
                    clearVoiceOrderState()
                } else if (command.confidence >= 0.4f) {
                    // 低信心度，需要確認（降低閾值從0.5到0.4）
                    voiceAssistant?.speakWithCallback(
                        message = "您是要接受這個訂單嗎？說「接」確認",
                        priority = VoiceAssistant.Priority.HIGH,
                        onComplete = {
                            // 保存待確認的指令，等待下次語音輸入
                            _uiState.value = _uiState.value.copy(
                                lastVoiceCommand = command
                            )
                            startVoiceListeningForOrder()
                        }
                    )
                } else {
                    handleVoiceRecognitionError("沒聽清楚，請再說一次")
                }
            }
            VoiceAction.REJECT_ORDER -> {
                if (command.confidence >= 0.6f) {
                    // 信心度足夠，直接拒單
                    voiceAssistant?.speak("好的，已拒絕訂單", VoiceAssistant.Priority.HIGH)
                    voiceCommandHandler?.confirmAndExecute(command)
                    clearVoiceOrderState()
                } else if (command.confidence >= 0.4f) {
                    // 低信心度，需要確認
                    voiceAssistant?.speakWithCallback(
                        message = "您是要拒絕這個訂單嗎？說「不要」確認",
                        priority = VoiceAssistant.Priority.HIGH,
                        onComplete = {
                            _uiState.value = _uiState.value.copy(
                                lastVoiceCommand = command
                            )
                            startVoiceListeningForOrder()
                        }
                    )
                } else {
                    handleVoiceRecognitionError("沒聽清楚，請再說一次")
                }
            }
            VoiceAction.UNKNOWN -> {
                handleVoiceRecognitionError("請說「接」接單或「不要」拒絕")
            }
            else -> {
                // 其他指令（如導航、狀態更新等），交給 VoiceCommandHandler 處理
                voiceCommandHandler?.handleCommand(command)
            }
        }
    }

    /**
     * 判斷是否為確認語音
     */
    private fun isConfirmation(text: String): Boolean {
        val confirmKeywords = listOf(
            "對", "是", "好", "確認", "沒錯", "嗯", "可以", "行", "收到",
            "接", "接單", "接受", "我接", "好的", "OK", "yes", "ok"
        )
        return confirmKeywords.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 判斷是否為拒絕語音
     */
    private fun isRejection(text: String): Boolean {
        val rejectKeywords = listOf(
            "不對", "不是", "取消", "算了", "不要", "拒絕", "不接", "no", "cancel"
        )
        return rejectKeywords.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 處理語音識別錯誤
     */
    private fun handleVoiceRecognitionError(message: String) {
        voiceAssistant?.speakWithCallback(
            message = message,
            priority = VoiceAssistant.Priority.NORMAL,
            onComplete = {
                // 錯誤後重新開始監聽
                if (_uiState.value.currentOrder?.status == OrderStatus.OFFERED) {
                    startVoiceListeningForOrder()
                }
            }
        )
    }

    /**
     * 清除語音訂單狀態
     */
    private fun clearVoiceOrderState() {
        _uiState.value = _uiState.value.copy(
            isVoiceListening = false,
            lastVoiceCommand = null
        )
    }

    /**
     * 設定語音自動監聽開關
     */
    fun setVoiceAutoListenEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(voiceAutoListenEnabled = enabled)
    }

    /**
     * 取得語音錄製狀態 Flow（供 UI 使用）
     */
    fun getVoiceRecordingState() = voiceRecorderService?.state

    /**
     * 取得語音振幅 Flow（供 UI 使用）
     */
    fun getVoiceAmplitude() = voiceRecorderService?.amplitude

    // ==================== 語音對講公開方法 ====================

    /**
     * 初始化語音對講功能
     * 需要在 UI 層傳入 Context 後呼叫
     */
    fun initVoiceChat(context: Context) {
        if (voiceChatManager != null) {
            android.util.Log.d("HomeViewModel", "語音對講已初始化，跳過")
            return
        }

        android.util.Log.d("HomeViewModel", "========== 初始化語音對講 ==========")

        val assistant = voiceAssistant ?: VoiceAssistant(context).also { voiceAssistant = it }
        val recorder = voiceRecorderService ?: VoiceRecorderService(context).also { voiceRecorderService = it }

        voiceChatManager = VoiceChatManager(
            context = context,
            voiceAssistant = assistant,
            voiceRecorderService = recorder,
            webSocketManager = webSocketManager
        )

        // 監聽 VoiceChatManager 的狀態
        viewModelScope.launch {
            voiceChatManager?.chatHistory?.collect { history ->
                _voiceChatHistory.value = history
            }
        }

        viewModelScope.launch {
            voiceChatManager?.state?.collect { state ->
                _voiceChatState.value = state
            }
        }

        viewModelScope.launch {
            voiceChatManager?.isRecording?.collect { recording ->
                _voiceChatRecording.value = recording
            }
        }

        viewModelScope.launch {
            voiceChatManager?.amplitude?.collect { amplitude ->
                _voiceChatAmplitude.value = amplitude
            }
        }

        android.util.Log.d("HomeViewModel", "✅ 語音對講初始化完成")
    }

    /**
     * 設置當前訂單的對講用戶資訊
     */
    fun setupVoiceChatUser(orderId: String, driverId: String, driverName: String) {
        voiceChatManager?.setCurrentUser(
            orderId = orderId,
            userId = driverId,
            userName = driverName,
            userType = VoiceChatMessage.SENDER_TYPE_DRIVER
        )
    }

    /**
     * 開始語音對講錄音
     */
    fun startVoiceChatRecording() {
        android.util.Log.d("HomeViewModel", "開始語音對講錄音")
        voiceChatManager?.startRecording()
    }

    /**
     * 停止語音對講錄音並發送
     */
    fun stopVoiceChatRecording() {
        android.util.Log.d("HomeViewModel", "停止語音對講錄音並發送")
        voiceChatManager?.stopRecordingAndSend()
    }

    /**
     * 取消語音對講錄音
     */
    fun cancelVoiceChatRecording() {
        android.util.Log.d("HomeViewModel", "取消語音對講錄音")
        voiceChatManager?.cancelRecording()
    }

    /**
     * 顯示語音對講面板
     */
    fun showVoiceChatPanel() {
        android.util.Log.d("HomeViewModel", "顯示語音對講面板")
        _showVoiceChatPanel.value = true
        _voiceChatUnreadCount.value = 0  // 清除未讀計數
    }

    /**
     * 隱藏語音對講面板
     */
    fun hideVoiceChatPanel() {
        android.util.Log.d("HomeViewModel", "隱藏語音對講面板")
        _showVoiceChatPanel.value = false
    }

    /**
     * 判斷是否可以使用語音對講
     * 只有在訂單已接單且正在進行中時才能使用
     */
    fun canUseVoiceChat(): Boolean {
        val order = _uiState.value.currentOrder ?: return false
        val validStatuses = listOf(
            OrderStatus.ACCEPTED,
            OrderStatus.ARRIVED,
            OrderStatus.ON_TRIP
        )
        return order.status in validStatuses
    }

    /**
     * 清除語音對講歷史（訂單結束時調用）
     */
    fun clearVoiceChatHistory() {
        voiceChatManager?.clearHistory()
        _voiceChatUnreadCount.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        disconnectWebSocket()
        // 釋放語音資源
        voiceAssistant?.release()
        voiceRecorderService?.release()
        voiceChatManager?.release()
    }
}
