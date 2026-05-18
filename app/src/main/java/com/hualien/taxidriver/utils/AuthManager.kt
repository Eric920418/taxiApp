package com.hualien.taxidriver.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.domain.model.UserRole
import com.hualien.taxidriver.service.FcmTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 登入狀態 — Firebase Auth 為唯一真實來源
 *
 * Loading：尚未從 FirebaseAuth.AuthStateListener 收到首次回呼（App 剛啟動）
 * Authenticated：Firebase 有 currentUser
 * Unauthenticated：Firebase 沒有 currentUser
 *
 * 本地 DataStore (driver_preferences / user_role) 降為「營運資料快取」
 * 不再參與「是否已登入」的判斷。
 */
sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val firebaseUid: String) : AuthState()
    data object Unauthenticated : AuthState()
}

/**
 * 集中式認證管理器
 *
 * 設計重點：
 * 1. Singleton，風格與 DataStoreManager 一致
 * 2. authState 用 FirebaseAuth.AuthStateListener 包成 StateFlow，任何登入/登出都會觸發
 * 3. logout()/forceLogoutBlocking() 集中清理順序；所有登出呼叫點只走這兩個入口
 * 4. 內部 CoroutineScope 是 Application-scoped（SupervisorJob + Dispatchers.IO）
 *    不會因為 UI（ProfileScreen）離開組合而被取消 — 解決舊版 rememberCoroutineScope 會被 cancel 的問題
 */
class AuthManager private constructor(
    private val appContext: Context,
    private val dataStoreManager: DataStoreManager,
    private val roleManager: RoleManager,
) {
    companion object {
        private const val TAG = "AuthManager"
        private const val FCM_CLEAR_TIMEOUT_MS = 3_000L

        @Volatile
        private var instance: AuthManager? = null

        fun init(
            context: Context,
            dataStoreManager: DataStoreManager,
            roleManager: RoleManager,
        ): AuthManager = instance ?: synchronized(this) {
            instance ?: AuthManager(
                context.applicationContext,
                dataStoreManager,
                roleManager,
            ).also { instance = it }
        }

        fun getInstance(): AuthManager =
            instance ?: error("AuthManager.init() must be called in MainActivity.onCreate()")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val firebaseAuth = FirebaseAuth.getInstance()

    val authState: StateFlow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            val next = if (user == null) {
                AuthState.Unauthenticated
            } else {
                AuthState.Authenticated(user.uid)
            }
            Log.d(TAG, "authState → $next")
            trySend(next)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AuthState.Loading,
    )

    /**
     * 啟動時的 reconcile：修復上次 App 被殺或登出流程中斷導致的不一致狀態
     *
     * 兩種 orphan：
     *  - Firebase 有 user、但 DataStore 無司機資料 → 視為半登入，強制 logout
     *  - Firebase 無 user、但 DataStore isLoggedIn=true → 只清 DataStore（RoleManager 保留 currentRole）
     */
    @Suppress("DEPRECATION") // reconcile 刻意讀 legacy isLoggedIn flag 以偵測 orphan 狀態
    suspend fun reconcileAtStartup() {
        val fbUser = firebaseAuth.currentUser
        val role = roleManager.currentRole.first()
        val legacyLoggedIn = dataStoreManager.isLoggedIn.first()
        val cachedDriverId = dataStoreManager.driverId.first()

        when {
            fbUser != null && role == UserRole.DRIVER && cachedDriverId.isNullOrEmpty() -> {
                Log.w(TAG, "reconcile: Firebase 有 user 但司機資料遺失 → forceLogout")
                logoutInternal(reason = "startup_missing_driver_data")
            }
            fbUser == null && legacyLoggedIn -> {
                Log.w(TAG, "reconcile: Firebase 無 user 但 DataStore isLoggedIn=true → 清 DataStore")
                runCatching { dataStoreManager.clearLoginData() }
                    .onFailure { Log.e(TAG, "reconcile clearLoginData failed", it) }
            }
            else -> Log.d(TAG, "reconcile: 狀態一致，無需動作")
        }

        // 已認證的司機每次 App 啟動都主動推 FCM token 到 server（idempotent PATCH）
        // 不依賴 Firebase onNewToken 因為它只在 token 真的變化時才觸發，
        // 已配對好的 token 不會重發 → 害 server fcm_token 永遠是空 (今天 33 司機 0 token bug)
        if (fbUser != null && role == UserRole.DRIVER && !cachedDriverId.isNullOrEmpty()) {
            runCatching {
                withTimeoutOrNull(FCM_CLEAR_TIMEOUT_MS) {
                    FcmTokenManager.syncTokenAfterLogin(appContext, cachedDriverId)
                }
            }.onFailure { Log.e(TAG, "FCM sync 失敗 (不影響啟動)", it) }
        }
    }

    /**
     * 使用者主動登出（從 ProfileScreen / PassengerProfileScreen 呼叫）
     *
     * 注意：呼叫端可以用 rememberCoroutineScope().launch { logout() } 觸發，
     * 但實際清理工作是在 AuthManager 內部的 Application scope 執行，
     * UI 消失不會取消清理。
     */
    suspend fun logout() = logoutInternal(reason = "user_initiated")

    /**
     * 同步版本，給 OkHttp Authenticator（TokenRefreshAuthenticator）用。
     * OkHttp Authenticator 是同步介面，在 background 執行緒上 runBlocking 是安全的。
     */
    fun forceLogoutBlocking(reason: String) {
        runBlocking(Dispatchers.IO) {
            logoutInternal(reason = reason)
        }
    }

    private suspend fun logoutInternal(reason: String) {
        Log.w(TAG, "logout start: reason=$reason")

        runCatching {
            val ws = WebSocketManager.getInstance()
            ws.cancelReconnect()
            ws.disconnect()
        }.onFailure { Log.e(TAG, "websocket disconnect failed", it) }

        val driverId = runCatching { dataStoreManager.driverId.first() }.getOrNull()
        if (!driverId.isNullOrEmpty()) {
            runCatching {
                withTimeoutOrNull(FCM_CLEAR_TIMEOUT_MS) {
                    FcmTokenManager.clearTokenOnLogout(appContext, driverId)
                }
            }.onFailure { Log.e(TAG, "fcm clear failed", it) }
        }

        runCatching { firebaseAuth.signOut() }
            .onFailure { Log.e(TAG, "firebase signOut failed", it) }

        runCatching { dataStoreManager.clearLoginData() }
            .onFailure { Log.e(TAG, "datastore clear failed", it) }

        runCatching { roleManager.logout() }
            .onFailure { Log.e(TAG, "role clear failed", it) }

        Log.w(TAG, "logout done: reason=$reason")
    }
}
