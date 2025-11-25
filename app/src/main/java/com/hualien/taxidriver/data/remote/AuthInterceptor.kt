package com.hualien.taxidriver.data.remote

import com.hualien.taxidriver.utils.DataStoreManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 認證攔截器 - 自動在請求中添加 Token
 * 優化版：使用緩存的 token，避免 runBlocking
 */
class AuthInterceptor(private val dataStoreManager: DataStoreManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 獲取緩存的 Token（非阻塞）
        val token = dataStoreManager.getCachedToken()

        // 如果沒有 Token 或者是登錄請求，直接放行
        if (token.isNullOrEmpty() || originalRequest.url.encodedPath.contains("/login")) {
            return chain.proceed(originalRequest)
        }

        // 添加 Authorization Header
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
