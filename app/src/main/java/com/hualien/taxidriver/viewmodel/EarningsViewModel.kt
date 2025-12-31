package com.hualien.taxidriver.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.DailyBreakdown
import com.hualien.taxidriver.data.remote.dto.EarningsOrder
import com.hualien.taxidriver.data.remote.dto.WeeklyBreakdown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 收入統計 ViewModel
 */
class EarningsViewModel : ViewModel() {

    companion object {
        private const val TAG = "EarningsViewModel"
    }

    private val apiService = RetrofitClient.apiService

    private val _uiState = MutableStateFlow(EarningsUiState())
    val uiState: StateFlow<EarningsUiState> = _uiState.asStateFlow()

    /**
     * 加載收入統計
     * @param driverId 司機ID
     * @param period today|week|month
     */
    fun loadEarnings(driverId: String, period: String = "today") {
        viewModelScope.launch {
            Log.d(TAG, "========== 加載收入統計 ==========")
            Log.d(TAG, "司機ID: $driverId, 期間: $period")

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.getEarningsStats(driverId, period)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    Log.d(TAG, "✅ 收入統計加載成功")
                    Log.d(TAG, "總收入: NT$ ${data.earnings.totalAmount}")
                    Log.d(TAG, "訂單數: ${data.earnings.orderCount}")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        currentPeriod = period,
                        totalAmount = data.earnings.totalAmount,
                        orderCount = data.earnings.orderCount,
                        totalDistance = data.earnings.totalDistance,
                        totalDuration = data.earnings.totalDuration,
                        averageFare = data.earnings.averageFare,
                        todayOrders = data.earnings.orders ?: emptyList(),
                        dailyBreakdown = data.earnings.dailyBreakdown ?: emptyList(),
                        weeklyBreakdown = data.earnings.weeklyBreakdown ?: emptyList()
                    )
                } else {
                    val errorMsg = "載入失敗：HTTP ${response.code()}"
                    Log.e(TAG, "❌ $errorMsg")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 網路錯誤: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "網路錯誤：${e.message}"
                )
            }
        }
    }

    /**
     * 切換期間並重新加載
     */
    fun switchPeriod(driverId: String, periodIndex: Int) {
        val period = when (periodIndex) {
            0 -> "today"
            1 -> "week"
            2 -> "month"
            else -> "today"
        }
        loadEarnings(driverId, period)
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * 收入統計 UI 狀態
 */
data class EarningsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPeriod: String = "today",
    // 統計數據
    val totalAmount: Int = 0,
    val orderCount: Int = 0,
    val totalDistance: Double = 0.0,
    val totalDuration: Double = 0.0,  // 小時
    val averageFare: Double = 0.0,
    // 今日訂單（period=today）
    val todayOrders: List<EarningsOrder> = emptyList(),
    // 每日統計（period=week）
    val dailyBreakdown: List<DailyBreakdown> = emptyList(),
    // 每週統計（period=month）
    val weeklyBreakdown: List<WeeklyBreakdown> = emptyList()
)
