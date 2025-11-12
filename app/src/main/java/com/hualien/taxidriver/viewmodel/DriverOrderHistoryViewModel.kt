package com.hualien.taxidriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.repository.OrderRepository
import com.hualien.taxidriver.domain.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 司機端訂單歷史 ViewModel
 */
class DriverOrderHistoryViewModel : ViewModel() {

    private val orderRepository = OrderRepository()

    private val _uiState = MutableStateFlow(DriverOrderHistoryUiState())
    val uiState: StateFlow<DriverOrderHistoryUiState> = _uiState.asStateFlow()

    /**
     * 加載訂單歷史
     */
    fun loadOrderHistory(driverId: String) {
        viewModelScope.launch {
            android.util.Log.d("DriverOrderHistoryVM", "========== 加載司機訂單歷史 ==========")
            android.util.Log.d("DriverOrderHistoryVM", "司機ID: $driverId")

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = orderRepository.getOrders(driverId)

                result.onSuccess { orders ->
                    android.util.Log.d("DriverOrderHistoryVM", "✅ 訂單加載成功，共 ${orders.size} 筆")
                    _uiState.value = _uiState.value.copy(
                        orders = orders,
                        isLoading = false,
                        error = null
                    )
                }.onFailure { error ->
                    android.util.Log.e("DriverOrderHistoryVM", "❌ 訂單加載失敗: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "無法載入訂單歷史"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverOrderHistoryVM", "❌ 網路異常: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "網路錯誤"
                )
            }
        }
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * 司機端訂單歷史 UI 狀態
 */
data class DriverOrderHistoryUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
