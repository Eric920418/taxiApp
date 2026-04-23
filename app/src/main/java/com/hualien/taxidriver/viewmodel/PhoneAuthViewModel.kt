package com.hualien.taxidriver.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.PhoneVerifyRequest
import com.hualien.taxidriver.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Firebase Phone Auth 狀態
 */
sealed class PhoneAuthState {
    object Idle : PhoneAuthState()
    object SendingCode : PhoneAuthState()
    data class CodeSent(val verificationId: String) : PhoneAuthState()
    object Verifying : PhoneAuthState()
    data class VerificationSuccess(val firebaseUid: String, val phone: String) : PhoneAuthState()
    data class Error(val message: String) : PhoneAuthState()
}

/**
 * 後端登入狀態
 */
sealed class BackendAuthState {
    object Idle : BackendAuthState()
    object Loading : BackendAuthState()
    data class DriverSuccess(
        val driverId: String,
        val name: String,
        val phone: String,
        val plate: String,
        val token: String
    ) : BackendAuthState()
    data class PassengerSuccess(
        val passengerId: String,
        val name: String,
        val phone: String
    ) : BackendAuthState()
    data class Error(val message: String) : BackendAuthState()
}

/**
 * Firebase Phone Authentication ViewModel
 */
class PhoneAuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val apiService = RetrofitClient.apiService

    private val _phoneAuthState = MutableStateFlow<PhoneAuthState>(PhoneAuthState.Idle)
    val phoneAuthState: StateFlow<PhoneAuthState> = _phoneAuthState.asStateFlow()

    private val _backendAuthState = MutableStateFlow<BackendAuthState>(BackendAuthState.Idle)
    val backendAuthState: StateFlow<BackendAuthState> = _backendAuthState.asStateFlow()

    private var storedVerificationId: String? = null
    private var storedPhone: String? = null

    // 開發模式：設為 true 跳過 Firebase 驗證（不需要計費）
    // 正式環境必須設為 false！
    private val DEVELOPMENT_MODE = false

    /**
     * 發送驗證碼到手機
     */
    fun sendVerificationCode(phone: String, activity: Activity) {
        // 驗證手機號碼格式
        if (!isValidPhoneNumber(phone)) {
            _phoneAuthState.value = PhoneAuthState.Error("請輸入有效的台灣手機號碼")
            return
        }

        _phoneAuthState.value = PhoneAuthState.SendingCode
        storedPhone = phone

        // 🔧 開發模式：跳過 Firebase，直接模擬驗證碼發送成功
        if (DEVELOPMENT_MODE) {
            android.util.Log.w("PhoneAuth", "⚠️ 開發模式：跳過 Firebase Phone Auth")
            android.util.Log.d("PhoneAuth", "原始號碼: $phone")

            // 模擬驗證碼已發送
            storedVerificationId = "DEV_MODE_VERIFICATION_ID"
            _phoneAuthState.value = PhoneAuthState.CodeSent("DEV_MODE_VERIFICATION_ID")
            return
        }

        // 格式化為國際格式 +886
        val formattedPhone = formatPhoneNumber(phone)

        // Log 發送資訊
        android.util.Log.d("PhoneAuth", "開始發送驗證碼")
        android.util.Log.d("PhoneAuth", "原始號碼: $phone")
        android.util.Log.d("PhoneAuth", "格式化號碼: $formattedPhone")
        android.util.Log.d("PhoneAuth", "Firebase App: ${auth.app.name}")
        android.util.Log.d("PhoneAuth", "Firebase Project: ${auth.app.options.projectId}")

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // 自動驗證成功（某些情況下會觸發）
                    viewModelScope.launch {
                        signInWithCredential(credential)
                    }
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    // Log 完整錯誤（DEBUG 用）
                    android.util.Log.e("PhoneAuth", "onVerificationFailed", e)
                    android.util.Log.e("PhoneAuth", "Error type: ${e.javaClass.name}")
                    android.util.Log.e("PhoneAuth", "Error message: ${e.message}")

                    val errorMsg = when (e) {
                        is com.google.firebase.FirebaseTooManyRequestsException -> {
                            "發送過於頻繁，請稍後再試（Firebase 每日 SMS 配額可能已用盡）"
                        }
                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> {
                            "手機號碼格式錯誤：${e.message}"
                        }
                        is com.google.firebase.auth.FirebaseAuthException -> {
                            // 針對常見的配置錯誤給可操作提示
                            val hint = when (e.errorCode) {
                                "ERROR_APP_NOT_AUTHORIZED" ->
                                    "\n\n此錯誤通常是 Firebase Console 沒有登記此 App 的 SHA-1 指紋。\n請管理員到 Firebase Console → 專案設定 → Android 應用程式 → 新增指紋，加入此 App 簽章的 SHA-1。"
                                "ERROR_INVALID_APP_CREDENTIAL" ->
                                    "\n\n此錯誤通常是 App Check 或 reCAPTCHA 設定問題，請聯繫管理員。"
                                "ERROR_QUOTA_EXCEEDED" ->
                                    "\n\nFirebase SMS 配額已用盡（免費方案每日 10 則，付費 Blaze 方案才無限）。"
                                "ERROR_MISSING_APP_CREDENTIAL" ->
                                    "\n\nApp 缺少驗證憑證，可能 google-services.json 損壞或版本不符。"
                                else -> ""
                            }
                            "Firebase 驗證失敗（${e.errorCode}）\n${e.message}$hint"
                        }
                        else -> {
                            "驗證失敗：${e.javaClass.simpleName}\n${e.message}"
                        }
                    }

                    _phoneAuthState.value = PhoneAuthState.Error(errorMsg)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    android.util.Log.d("PhoneAuth", "驗證碼已發送")
                    android.util.Log.d("PhoneAuth", "Verification ID: $verificationId")

                    storedVerificationId = verificationId
                    _phoneAuthState.value = PhoneAuthState.CodeSent(verificationId)
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * 驗證輸入的驗證碼
     */
    fun verifyCode(code: String) {
        if (storedVerificationId == null) {
            _phoneAuthState.value = PhoneAuthState.Error("請先發送驗證碼")
            return
        }

        if (code.length != 6) {
            _phoneAuthState.value = PhoneAuthState.Error("請輸入 6 位數驗證碼")
            return
        }

        _phoneAuthState.value = PhoneAuthState.Verifying

        // 🔧 開發模式：跳過 Firebase，直接模擬登入成功
        if (DEVELOPMENT_MODE) {
            android.util.Log.w("PhoneAuth", "⚠️ 開發模式：跳過 Firebase 驗證")
            android.util.Log.d("PhoneAuth", "輸入驗證碼: $code")

            // 模擬 Firebase UID
            val mockFirebaseUid = "DEV_UID_${storedPhone?.takeLast(4)}"

            viewModelScope.launch {
                kotlinx.coroutines.delay(500) // 模擬網路延遲

                android.util.Log.d("PhoneAuth", "開發模式驗證成功")
                android.util.Log.d("PhoneAuth", "模擬 Firebase UID: $mockFirebaseUid")

                _phoneAuthState.value = PhoneAuthState.VerificationSuccess(
                    firebaseUid = mockFirebaseUid,
                    phone = storedPhone ?: ""
                )
            }
            return
        }

        val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, code)

        viewModelScope.launch {
            signInWithCredential(credential)
        }
    }

    /**
     * 使用憑證登入 Firebase
     */
    private suspend fun signInWithCredential(credential: PhoneAuthCredential) {
        try {
            android.util.Log.d("PhoneAuth", "開始使用憑證登入 Firebase")

            val result = auth.signInWithCredential(credential).await()
            val user = result.user

            if (user != null) {
                android.util.Log.d("PhoneAuth", "Firebase 登入成功")
                android.util.Log.d("PhoneAuth", "Firebase UID: ${user.uid}")
                android.util.Log.d("PhoneAuth", "Phone Number: ${user.phoneNumber}")

                _phoneAuthState.value = PhoneAuthState.VerificationSuccess(
                    firebaseUid = user.uid,
                    phone = storedPhone ?: user.phoneNumber ?: ""
                )
            } else {
                android.util.Log.e("PhoneAuth", "Firebase 返回的 user 為 null")
                _phoneAuthState.value = PhoneAuthState.Error(
                    "Firebase 驗證失敗：signInWithCredential 回傳 user 為 null。\n" +
                        "這通常代表 Firebase Auth 配置問題：\n" +
                        "• 請管理員確認 Firebase Console 已登記此 App 的 SHA-1 指紋\n" +
                        "• 或 Firebase Phone Provider 是否啟用"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneAuth", "憑證驗證失敗", e)
            android.util.Log.e("PhoneAuth", "錯誤類型: ${e.javaClass.name}")
            android.util.Log.e("PhoneAuth", "錯誤訊息: ${e.message}")

            _phoneAuthState.value = PhoneAuthState.Error("驗證碼錯誤或已過期：${e.message}")
        }
    }

    /**
     * 向後端驗證並取得司機資料
     */
    fun verifyWithBackendDriver(firebaseUid: String, phone: String) {
        _backendAuthState.value = BackendAuthState.Loading

        viewModelScope.launch {
            try {
                val response = apiService.phoneVerifyDriver(
                    PhoneVerifyRequest(
                        phone = phone,
                        firebaseUid = firebaseUid
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _backendAuthState.value = BackendAuthState.DriverSuccess(
                        driverId = body.driverId,
                        name = body.name,
                        phone = body.phone,
                        plate = body.plate,
                        token = body.token
                    )
                } else {
                    val errorMsg = when (response.code()) {
                        404 -> "此手機號碼尚未註冊為司機，請聯繫管理員"
                        else -> "登入失敗：${response.message()}"
                    }
                    _backendAuthState.value = BackendAuthState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _backendAuthState.value = BackendAuthState.Error("網路錯誤：${e.message}")
            }
        }
    }

    /**
     * 向後端驗證並取得乘客資料
     */
    fun verifyWithBackendPassenger(firebaseUid: String, phone: String, name: String? = null) {
        _backendAuthState.value = BackendAuthState.Loading

        viewModelScope.launch {
            try {
                val response = apiService.phoneVerifyPassenger(
                    PhoneVerifyRequest(
                        phone = phone,
                        firebaseUid = firebaseUid,
                        name = name
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _backendAuthState.value = BackendAuthState.PassengerSuccess(
                        passengerId = body.passengerId,
                        name = body.name,
                        phone = body.phone
                    )
                } else {
                    _backendAuthState.value = BackendAuthState.Error("登入失敗：${response.message()}")
                }
            } catch (e: Exception) {
                _backendAuthState.value = BackendAuthState.Error("網路錯誤：${e.message}")
            }
        }
    }

    /**
     * 重置狀態
     */
    fun resetState() {
        _phoneAuthState.value = PhoneAuthState.Idle
        _backendAuthState.value = BackendAuthState.Idle
        storedVerificationId = null
        storedPhone = null
    }

    /**
     * 驗證台灣手機號碼格式
     */
    private fun isValidPhoneNumber(phone: String): Boolean {
        val regex = Regex("^09\\d{8}$")
        return regex.matches(phone)
    }

    /**
     * 格式化為國際格式 +886
     */
    private fun formatPhoneNumber(phone: String): String {
        // 09xxxxxxxx -> +886 9xxxxxxxx
        return if (phone.startsWith("09")) {
            "+886${phone.substring(1)}"
        } else {
            phone
        }
    }
}
