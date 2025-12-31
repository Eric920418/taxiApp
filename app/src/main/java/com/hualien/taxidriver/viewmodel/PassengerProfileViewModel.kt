package com.hualien.taxidriver.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.PassengerProfileDto
import com.hualien.taxidriver.data.remote.dto.UpdatePassengerRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 乘客個人資料 ViewModel
 */
class PassengerProfileViewModel : ViewModel() {

    companion object {
        private const val TAG = "PassengerProfileVM"
    }

    private val apiService = RetrofitClient.passengerApiService

    private val _uiState = MutableStateFlow(PassengerProfileUiState())
    val uiState: StateFlow<PassengerProfileUiState> = _uiState.asStateFlow()

    /**
     * 加載乘客資料
     */
    fun loadProfile(passengerId: String) {
        viewModelScope.launch {
            Log.d(TAG, "========== 加載乘客資料 ==========")
            Log.d(TAG, "乘客ID: $passengerId")

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.getPassengerProfile(passengerId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val profile = response.body()!!.passenger
                    Log.d(TAG, "✅ 乘客資料加載成功: ${profile?.name}")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        profile = profile
                    )
                } else {
                    val errorMsg = response.body()?.error ?: "載入失敗：HTTP ${response.code()}"
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
     * 更新乘客資料
     */
    fun updateProfile(passengerId: String, name: String?, email: String?) {
        viewModelScope.launch {
            Log.d(TAG, "========== 更新乘客資料 ==========")
            Log.d(TAG, "乘客ID: $passengerId, 姓名: $name, Email: $email")

            _uiState.value = _uiState.value.copy(isUpdating = true, updateError = null)

            try {
                val request = UpdatePassengerRequest(name = name, email = email)
                val response = apiService.updatePassengerProfile(passengerId, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val updatedProfile = response.body()!!.passenger
                    Log.d(TAG, "✅ 乘客資料更新成功")

                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateError = null,
                        updateSuccess = true,
                        profile = updatedProfile
                    )
                } else {
                    val errorMsg = response.body()?.error ?: "更新失敗：HTTP ${response.code()}"
                    Log.e(TAG, "❌ $errorMsg")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateError = errorMsg
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 網路錯誤: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    updateError = "網路錯誤：${e.message}"
                )
            }
        }
    }

    /**
     * 清除更新成功狀態
     */
    fun clearUpdateSuccess() {
        _uiState.value = _uiState.value.copy(updateSuccess = false)
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, updateError = null)
    }
}

/**
 * 乘客個人資料 UI 狀態
 */
data class PassengerProfileUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val profile: PassengerProfileDto? = null,
    // 更新相關
    val isUpdating: Boolean = false,
    val updateError: String? = null,
    val updateSuccess: Boolean = false
)
