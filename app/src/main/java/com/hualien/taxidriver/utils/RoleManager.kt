package com.hualien.taxidriver.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hualien.taxidriver.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 角色管理工具
 * 負責存儲和讀取用戶當前角色
 */
class RoleManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_role")
        private val CURRENT_ROLE_KEY = stringPreferencesKey("current_role")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_PHONE_KEY = stringPreferencesKey("user_phone")
    }

    /**
     * 獲取當前角色
     */
    val currentRole: Flow<UserRole?> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_ROLE_KEY]?.let { roleString ->
            try {
                UserRole.valueOf(roleString)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * 獲取用戶ID
     */
    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    /**
     * 獲取用戶名稱
     */
    val userName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME_KEY]
    }

    /**
     * 設置當前角色
     */
    suspend fun setCurrentRole(role: UserRole, userId: String, userName: String, phone: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_ROLE_KEY] = role.name
            preferences[USER_ID_KEY] = userId
            preferences[USER_NAME_KEY] = userName
            preferences[USER_PHONE_KEY] = phone
        }
    }

    /**
     * 切換角色
     */
    suspend fun switchRole(newRole: UserRole) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_ROLE_KEY] = newRole.name
        }
    }

    /**
     * 登出（清除所有資料）
     */
    suspend fun logout() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
