package com.hualien.taxidriver.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.hualien.taxidriver.utils.DataStoreManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Token 刷新認證器
 *
 * 功能：
 * 1. 自動檢測 401 Unauthorized 錯誤
 * 2. 使用 Firebase Auth 刷新 ID token
 * 3. 重試失敗的請求
 * 4. 避免無限重試（最多重試 3 次）
 *
 * 注意：這個類會在 OkHttp 收到 401 響應時自動被調用
 */
class TokenRefreshAuthenticator(
    private val dataStoreManager: DataStoreManager
) : Authenticator {

    companion object {
        private const val TAG = "TokenRefreshAuth"
        private const val MAX_RETRY_COUNT = 3
    }

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // 檢查重試次數，避免無限循環
        val retryCount = responseCount(response)
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.e(TAG, "❌ Token 刷新失敗：已重試 $retryCount 次，放棄重試")
            return null
        }

        Log.w(TAG, "⚠️ 收到 401 Unauthorized，嘗試刷新 Token（第 ${retryCount + 1} 次）")

        // 獲取 Firebase Auth 實例
        val firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            Log.e(TAG, "❌ 無法刷新 Token：用戶未登入")
            // 用戶未登入，清除本地數據並強制重新登入
            runBlocking {
                dataStoreManager.clearLoginData()
            }
            return null
        }

        try {
            // 使用 runBlocking 等待 Firebase token 刷新（同步操作）
            val newToken = runBlocking {
                try {
                    // 強制刷新 Firebase ID token
                    Log.d(TAG, "正在刷新 Firebase ID token...")
                    val tokenResult = currentUser.getIdToken(true).await()
                    val token = tokenResult.token

                    if (token.isNullOrEmpty()) {
                        Log.e(TAG, "❌ 刷新 Token 失敗：返回的 token 為空")
                        return@runBlocking null
                    }

                    // 保存新的 token 到 DataStore
                    dataStoreManager.updateToken(token)
                    Log.d(TAG, "✅ Token 刷新成功")

                    token
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 刷新 Token 失敗", e)
                    null
                }
            }

            // 如果 token 刷新失敗，返回 null（放棄重試）
            if (newToken == null) {
                Log.e(TAG, "❌ Token 刷新失敗，放棄重試")
                return null
            }

            // 使用新的 token 重新構建請求
            Log.d(TAG, "🔄 使用新 Token 重試請求")
            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Token 刷新過程發生異常", e)
            return null
        }
    }

    /**
     * 計算這個請求已經重試了多少次
     */
    private fun responseCount(response: Response): Int {
        var count = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) {
            count++
            priorResponse = priorResponse.priorResponse
        }
        return count
    }
}
