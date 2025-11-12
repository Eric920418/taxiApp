package com.hualien.taxidriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.data.repository.OrderRepository
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.domain.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Home頁面的UI狀態
 */
data class HomeUiState(
    val driverStatus: DriverAvailability = DriverAvailability.OFFLINE,
    val currentOrder: Order? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Home頁面ViewModel
 */
class HomeViewModel : ViewModel() {

    private val repository = OrderRepository()
    private val webSocketManager = WebSocketManager.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        android.util.Log.d("HomeViewModel", "========== HomeViewModel 初始化 ==========")

        // 監聽 WebSocket 連接狀態
        viewModelScope.launch {
            android.util.Log.d("HomeViewModel", "開始監聽 WebSocket 連接狀態...")
            webSocketManager.isConnected.collect { isConnected ->
                android.util.Log.d("HomeViewModel", "========== WebSocket 連接狀態變化 ==========")
                android.util.Log.d("HomeViewModel", "連接狀態: ${if (isConnected) "✅ 已連接" else "❌ 未連接"}")
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
    }

    /**
     * 連接WebSocket（司機上線）
     */
    fun connectWebSocket(driverId: String) {
        android.util.Log.d("HomeViewModel", "========== 連接WebSocket ==========")
        android.util.Log.d("HomeViewModel", "司機ID: $driverId")
        webSocketManager.connect(driverId)
    }

    /**
     * 斷開WebSocket（司機離線）
     */
    fun disconnectWebSocket() {
        webSocketManager.disconnect()
    }

    /**
     * 更新司機狀態
     */
    fun updateDriverStatus(driverId: String, status: DriverAvailability) {
        android.util.Log.d("HomeViewModel", "========== 更新司機狀態 ==========")
        android.util.Log.d("HomeViewModel", "司機ID: $driverId")
        android.util.Log.d("HomeViewModel", "新狀態: $status")

        viewModelScope.launch {
            // 先更新UI
            _uiState.value = _uiState.value.copy(
                driverStatus = status,
                isLoading = true
            )

            try {
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

            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.submitFare(orderId, meterAmount, appDistanceMeters = null, appDurationSeconds = null)
                .onSuccess { updatedOrder ->
                    android.util.Log.d("HomeViewModel", "✅ 車資提交成功，訂單已完成")

                    // 車資提交成功，訂單完成，清除當前訂單並恢復司機狀態為可接單
                    _uiState.value = _uiState.value.copy(
                        currentOrder = null,
                        driverStatus = DriverAvailability.AVAILABLE,
                        isLoading = false,
                        error = null
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
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        disconnectWebSocket()
    }
}
