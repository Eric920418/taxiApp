package com.hualien.taxidriver.data.repository

import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.LoginRequest
import com.hualien.taxidriver.data.remote.dto.LoginResponse

/**
 * 認證Repository
 */
class AuthRepository {

    private val apiService = RetrofitClient.apiService

    /**
     * 登入
     */
    suspend fun login(phone: String, password: String): Result<LoginResponse> {
        return try {
            val response = apiService.login(LoginRequest(phone, password))

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = when (response.code()) {
                    400 -> "請輸入手機號碼和密碼"
                    401 -> "密碼錯誤"
                    404 -> "找不到此司機帳號"
                    else -> "登入失敗：${response.message()}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }
}
