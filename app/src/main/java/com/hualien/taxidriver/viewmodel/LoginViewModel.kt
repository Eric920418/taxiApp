package com.hualien.taxidriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hualien.taxidriver.data.remote.dto.LoginResponse
import com.hualien.taxidriver.data.repository.AuthRepository
import com.hualien.taxidriver.utils.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登入狀態
 */
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val response: LoginResponse) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

/**
 * 登入ViewModel
 */
class LoginViewModel(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * 登入
     */
    fun login(phone: String, password: String) {
        // 驗證輸入
        if (phone.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("請輸入手機號碼和密碼")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            repository.login(phone, password)
                .onSuccess { response ->
                    // 保存登錄信息到 DataStore
                    dataStoreManager.saveLoginData(
                        token = response.token,
                        driverId = response.driverId,
                        name = response.name,
                        phone = response.phone,
                        plate = response.plate
                    )
                    _uiState.value = LoginUiState.Success(response)
                }
                .onFailure { error ->
                    _uiState.value = LoginUiState.Error(error.message ?: "登入失敗")
                }
        }
    }

    /**
     * 重置狀態
     */
    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
