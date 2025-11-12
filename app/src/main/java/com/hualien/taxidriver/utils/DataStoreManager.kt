package com.hualien.taxidriver.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "driver_preferences")

class DataStoreManager(private val context: Context) {

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
}
