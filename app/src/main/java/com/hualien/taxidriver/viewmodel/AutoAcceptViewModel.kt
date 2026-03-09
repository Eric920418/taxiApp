package com.hualien.taxidriver.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.AutoAcceptSettings
import com.hualien.taxidriver.data.remote.dto.AutoAcceptStats
import com.hualien.taxidriver.data.remote.dto.UpdateAutoAcceptSettingsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AI 自動接單設定 ViewModel
 */
class AutoAcceptViewModel : ViewModel() {

    companion object {
        private const val TAG = "AutoAcceptViewModel"
    }

    private val apiService = RetrofitClient.apiService

    private val _settings = MutableStateFlow<AutoAcceptSettings?>(null)
    val settings: StateFlow<AutoAcceptSettings?> = _settings.asStateFlow()

    private val _stats = MutableStateFlow<AutoAcceptStats?>(null)
    val stats: StateFlow<AutoAcceptStats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 載入自動接單設定
     */
    fun loadSettings(driverId: String) {
        viewModelScope.launch {
            Log.d(TAG, "========== 載入自動接單設定 ==========")
            Log.d(TAG, "司機ID: $driverId")

            _isLoading.value = true
            _error.value = null

            try {
                val response = apiService.getAutoAcceptSettings(driverId)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    if (data.success && data.settings != null) {
                        Log.d(TAG, "自動接單設定載入成功")
                        Log.d(TAG, "啟用狀態: ${data.settings.enabled}")
                        Log.d(TAG, "智慧模式: ${data.settings.smartModeEnabled}")
                        _settings.value = data.settings
                    } else {
                        Log.e(TAG, "載入失敗: ${data.error}")
                        _error.value = data.error ?: "載入設定失敗"
                    }
                } else {
                    val errorMsg = "載入失敗：HTTP ${response.code()}"
                    Log.e(TAG, errorMsg)
                    _error.value = errorMsg
                }
            } catch (e: Exception) {
                Log.e(TAG, "網路錯誤: ${e.message}")
                _error.value = "網路錯誤：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 載入自動接單統計
     */
    fun loadStats(driverId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getAutoAcceptStats(driverId)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    if (data.success && data.stats != null) {
                        Log.d(TAG, "自動接單統計載入成功")
                        Log.d(TAG, "今日自動接單: ${data.stats.today.autoAcceptCount}")
                        _stats.value = data.stats
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "載入統計失敗: ${e.message}")
            }
        }
    }

    /**
     * 更新啟用狀態
     */
    suspend fun updateEnabled(driverId: String, enabled: Boolean) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(enabled = enabled))
    }

    /**
     * 更新智慧模式
     */
    suspend fun updateSmartMode(driverId: String, enabled: Boolean) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(smartModeEnabled = enabled))
    }

    /**
     * 更新閾值
     */
    suspend fun updateThreshold(driverId: String, threshold: Int) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(autoAcceptThreshold = threshold))
    }

    /**
     * 更新最大接送距離
     */
    suspend fun updateMaxDistance(driverId: String, distance: Double) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(maxPickupDistanceKm = distance))
    }

    /**
     * 更新最低車資
     */
    suspend fun updateMinFare(driverId: String, fare: Int) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(minFareAmount = fare))
    }

    /**
     * 更新最短行程距離
     */
    suspend fun updateMinTripDistance(driverId: String, distance: Double) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(minTripDistanceKm = distance))
    }

    /**
     * 更新每日上限
     */
    suspend fun updateDailyLimit(driverId: String, limit: Int) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(dailyAutoAcceptLimit = limit))
    }

    /**
     * 更新冷卻時間
     */
    suspend fun updateCooldown(driverId: String, minutes: Int) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(cooldownMinutes = minutes))
    }

    /**
     * 更新連續接單上限
     */
    suspend fun updateConsecutiveLimit(driverId: String, limit: Int) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(consecutiveLimit = limit))
    }

    /**
     * 更新啟用時段
     */
    suspend fun updateActiveHours(driverId: String, hours: List<Int>) {
        updateSettings(driverId, UpdateAutoAcceptSettingsRequest(activeHours = hours))
    }

    /**
     * 通用設定更新方法
     */
    private suspend fun updateSettings(driverId: String, request: UpdateAutoAcceptSettingsRequest) {
        Log.d(TAG, "更新自動接單設定: $request")

        try {
            val response = apiService.updateAutoAcceptSettings(driverId, request)

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                if (data.success && data.settings != null) {
                    Log.d(TAG, "設定更新成功")
                    _settings.value = data.settings
                    _error.value = null
                } else {
                    Log.e(TAG, "更新失敗: ${data.error}")
                    _error.value = data.error ?: "更新設定失敗"
                }
            } else {
                val errorMsg = "更新失敗：HTTP ${response.code()}"
                Log.e(TAG, errorMsg)
                _error.value = errorMsg
            }
        } catch (e: Exception) {
            Log.e(TAG, "網路錯誤: ${e.message}")
            _error.value = "網路錯誤：${e.message}"
        }
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _error.value = null
    }
}
