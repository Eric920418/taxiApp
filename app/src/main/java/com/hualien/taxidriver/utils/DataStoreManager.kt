package com.hualien.taxidriver.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "driver_preferences")

class DataStoreManager(private val context: Context) {

    // Token 緩存，避免 runBlocking
    private var cachedToken: String? = null

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_DRIVER_ID = stringPreferencesKey(Constants.PREF_DRIVER_ID)
        private val KEY_DRIVER_NAME = stringPreferencesKey(Constants.PREF_DRIVER_NAME)
        private val KEY_DRIVER_PHONE = stringPreferencesKey(Constants.PREF_DRIVER_PHONE)
        private val KEY_DRIVER_PLATE = stringPreferencesKey("driver_plate")
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey(Constants.PREF_IS_LOGGED_IN)
    }

    // 保存登錄信息
    suspend fun saveLoginData(
        token: String,
        driverId: String,
        name: String,
        phone: String,
        plate: String
    ) {
        // 緩存 token 避免 AuthInterceptor 中使用 runBlocking
        cachedToken = token

        context.dataStore.edit { preferences ->
            preferences[KEY_TOKEN] = token
            preferences[KEY_DRIVER_ID] = driverId
            preferences[KEY_DRIVER_NAME] = name
            preferences[KEY_DRIVER_PHONE] = phone
            preferences[KEY_DRIVER_PLATE] = plate
            preferences[KEY_IS_LOGGED_IN] = true
        }
    }

    // 獲取 Token
    val token: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOKEN]
    }

    // 獲取司機 ID
    val driverId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DRIVER_ID]
    }

    // 獲取司機姓名
    val driverName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DRIVER_NAME]
    }

    // 獲取司機電話
    val driverPhone: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DRIVER_PHONE]
    }

    // 獲取司機車牌
    val driverPlate: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DRIVER_PLATE]
    }

    // 獲取登錄狀態
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_LOGGED_IN] ?: false
    }

    // 登出時清除所有數據
    suspend fun clearLoginData() {
        // 清除緩存的 token
        cachedToken = null

        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // 同步獲取 Token（用於攔截器）
    suspend fun getTokenSync(): String? {
        var token: String? = null
        context.dataStore.edit { preferences ->
            token = preferences[KEY_TOKEN]
        }
        return token
    }

    /**
     * 獲取緩存的 Token（非 suspend，用於 AuthInterceptor）
     * 避免使用 runBlocking
     */
    fun getCachedToken(): String? = cachedToken

    /**
     * 初始化時從 DataStore 加載 token 到緩存
     * 應該在應用啟動時調用
     */
    suspend fun initializeTokenCache() {
        cachedToken = token.first()
    }

    /**
     * 更新 Token（用於 Token 刷新）
     * 同時更新緩存和 DataStore
     */
    suspend fun updateToken(newToken: String) {
        // 更新緩存
        cachedToken = newToken

        // 更新 DataStore
        context.dataStore.edit { preferences ->
            preferences[KEY_TOKEN] = newToken
        }
    }
}
