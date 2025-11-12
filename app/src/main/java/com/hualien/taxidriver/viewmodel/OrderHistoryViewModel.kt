package com.hualien.taxidriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.repository.PassengerRepository
import com.hualien.taxidriver.domain.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 訂單歷史 ViewModel
 */
class OrderHistoryViewModel : ViewModel() {

    private val passengerRepository = PassengerRepository()

    private val _uiState = MutableStateFlow(OrderHistoryUiState())
    val uiState: StateFlow<OrderHistoryUiState> = _uiState.asStateFlow()

    /**
     * 加載訂單歷史
     */
    fun loadOrderHistory(passengerId: String, status: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = passengerRepository.getOrderHistory(
                    passengerId = passengerId,
                    status = status
                )

                result.onSuccess { orders ->
                    _uiState.value = _uiState.value.copy(
                        orders = orders,
                        isLoading = false,
                        error = null
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "無法載入訂單歷史"
                    )
                }
            } catch (e: Exception) {
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
 * 訂單歷史 UI 狀態
 */
data class OrderHistoryUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
