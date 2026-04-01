package com.hualien.taxidriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.PhoneReviewCallDto
import com.hualien.taxidriver.data.remote.dto.PhoneReviewRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhoneReviewUiState(
    val calls: List<PhoneReviewCallDto> = emptyList(),
    val reviewCount: Int = 0,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class PhoneReviewViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneReviewUiState())
    val uiState: StateFlow<PhoneReviewUiState> = _uiState.asStateFlow()

    private val apiService = RetrofitClient.apiService

    /**
     * 載入待審核數量（用於首頁 badge）
     */
    fun loadReviewCount(driverId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getPhoneReviewCount(driverId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(
                        reviewCount = response.body()!!.count
                    )
                }
            } catch (e: Exception) {
                // 靜默失敗，不影響主畫面
            }
        }
    }

    /**
     * 載入待審核列表
     */
    fun loadReviews(driverId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getPhoneReviews(driverId)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        calls = body.calls,
                        reviewCount = body.total,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "載入失敗: ${response.message()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "網路錯誤: ${e.message}"
                )
            }
        }
    }

    /**
     * 審核電話（核准或拒絕）
     */
    fun submitReview(
        driverId: String,
        callId: String,
        action: String,
        editedFields: Map<String, Any?>? = null,
        note: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null)
            try {
                val request = PhoneReviewRequest(
                    action = action,
                    editedFields = editedFields,
                    note = note
                )
                val response = apiService.submitPhoneReview(driverId, callId, request)
                if (response.isSuccessful && response.body() != null) {
                    val msg = if (action == "APPROVED") "已核准，訂單建立中" else "已拒絕"
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = msg
                    )
                    // 重新載入列表
                    loadReviews(driverId)
                } else {
                    val errorBody = response.errorBody()?.string() ?: response.message()
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = "操作失敗: $errorBody"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = "網路錯誤: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}