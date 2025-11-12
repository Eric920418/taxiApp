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
 * 司機端訂單列表 ViewModel
 */
class DriverOrdersViewModel : ViewModel() {

    private val orderRepository = OrderRepository()

    private val _uiState = MutableStateFlow(DriverOrdersUiState())
    val uiState: StateFlow<DriverOrdersUiState> = _uiState.asStateFlow()

    /**
     * 加載司機的訂單列表
     */
    fun loadOrders(driverId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = orderRepository.getOrders(driverId = driverId)

                result.onSuccess { orders ->
                    _uiState.value = _uiState.value.copy(
                        allOrders = orders,
                        isLoading = false,
                        error = null
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "無法載入訂單"
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
     * 根據狀態篩選訂單
     */
    fun getOrdersByStatus(status: String?): List<Order> {
        return when (status) {
            "active" -> _uiState.value.allOrders.filter {
                it.statusString in listOf("ACCEPTED", "ARRIVED", "ON_TRIP", "SETTLING")
            }
            "completed" -> _uiState.value.allOrders.filter {
                it.statusString == "DONE"
            }
            "cancelled" -> _uiState.value.allOrders.filter {
                it.statusString == "CANCELLED"
            }
            else -> _uiState.value.allOrders
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
 * 司機訂單列表 UI 狀態
 */
data class DriverOrdersUiState(
    val allOrders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
