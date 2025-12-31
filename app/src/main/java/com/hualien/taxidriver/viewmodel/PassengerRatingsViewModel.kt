package com.hualien.taxidriver.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.PassengerRatingDto
import com.hualien.taxidriver.data.remote.dto.RatingSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 乘客評價 ViewModel
 */
class PassengerRatingsViewModel : ViewModel() {

    companion object {
        private const val TAG = "PassengerRatingsVM"
    }

    private val _uiState = MutableStateFlow(PassengerRatingsUiState())
    val uiState: StateFlow<PassengerRatingsUiState> = _uiState.asStateFlow()

    /**
     * 載入乘客評價
     */
    fun loadRatings(passengerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val response = RetrofitClient.passengerApiService.getPassengerRatings(passengerId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            ratings = body.ratings,
                            summary = body.summary,
                            error = null
                        )
                    }
                    Log.d(TAG, "載入評價成功: ${body.ratings.size} 則")
                } else {
                    val errorMsg = response.body()?.error ?: "載入評價失敗"
                    _uiState.update {
                        it.copy(isLoading = false, error = errorMsg)
                    }
                    Log.e(TAG, "載入評價失敗: $errorMsg")
                }

            } catch (e: Exception) {
                Log.e(TAG, "載入評價異常", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "網路錯誤: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * 刷新評價
     */
    fun refresh(passengerId: String) {
        loadRatings(passengerId)
    }
}

/**
 * 乘客評價 UI 狀態
 */
data class PassengerRatingsUiState(
    val isLoading: Boolean = false,
    val ratings: List<PassengerRatingDto> = emptyList(),
    val summary: RatingSummaryDto? = null,
    val error: String? = null
)
