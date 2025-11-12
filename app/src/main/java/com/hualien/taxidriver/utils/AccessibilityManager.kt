package com.hualien.taxidriver.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 無障礙設定管理器
 * 管理中老年人友善模式的相關設定
 */
class AccessibilityManager(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "accessibility_settings"
    )

    companion object {
        private val SENIOR_MODE_KEY = booleanPreferencesKey("senior_mode")
        private val TEXT_SCALE_KEY = floatPreferencesKey("text_scale")
        private val HIGH_CONTRAST_KEY = booleanPreferencesKey("high_contrast")
        private val SIMPLIFIED_UI_KEY = booleanPreferencesKey("simplified_ui")
        private val VOICE_FEEDBACK_KEY = booleanPreferencesKey("voice_feedback")
        private val CONFIRM_ACTIONS_KEY = booleanPreferencesKey("confirm_actions")
        private val LARGE_BUTTONS_KEY = booleanPreferencesKey("large_buttons")

        const val DEFAULT_TEXT_SCALE = 1.0f
        const val SENIOR_TEXT_SCALE = 1.3f
    }

    /**
     * 是否啟用中老年人模式
     */
    val isSeniorMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SENIOR_MODE_KEY] ?: false
    }

    /**
     * 文字縮放比例
     */
    val textScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[TEXT_SCALE_KEY] ?: DEFAULT_TEXT_SCALE
    }

    /**
     * 是否使用高對比度
     */
    val isHighContrast: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HIGH_CONTRAST_KEY] ?: false
    }

    /**
     * 是否使用簡化介面
     */
    val isSimplifiedUI: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SIMPLIFIED_UI_KEY] ?: false
    }

    /**
     * 是否啟用語音反饋
     */
    val isVoiceFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VOICE_FEEDBACK_KEY] ?: false
    }

    /**
     * 是否需要操作確認
     */
    val isConfirmActionsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CONFIRM_ACTIONS_KEY] ?: false
    }

    /**
     * 是否使用大按鈕
     */
    val isLargeButtonsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LARGE_BUTTONS_KEY] ?: false
    }

    /**
     * 切換中老年人模式
     */
    suspend fun toggleSeniorMode() {
        context.dataStore.edit { preferences ->
            val currentMode = preferences[SENIOR_MODE_KEY] ?: false
            preferences[SENIOR_MODE_KEY] = !currentMode

            // 當開啟中老年人模式時，自動調整相關設定
            if (!currentMode) {
                preferences[TEXT_SCALE_KEY] = SENIOR_TEXT_SCALE
                preferences[HIGH_CONTRAST_KEY] = true
                preferences[SIMPLIFIED_UI_KEY] = true
                preferences[CONFIRM_ACTIONS_KEY] = true
                preferences[LARGE_BUTTONS_KEY] = true
            } else {
                preferences[TEXT_SCALE_KEY] = DEFAULT_TEXT_SCALE
                preferences[HIGH_CONTRAST_KEY] = false
                preferences[SIMPLIFIED_UI_KEY] = false
                preferences[CONFIRM_ACTIONS_KEY] = false
                preferences[LARGE_BUTTONS_KEY] = false
            }
        }
    }

    /**
     * 設定中老年人模式
     */
    suspend fun setSeniorMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SENIOR_MODE_KEY] = enabled

            if (enabled) {
                preferences[TEXT_SCALE_KEY] = SENIOR_TEXT_SCALE
                preferences[HIGH_CONTRAST_KEY] = true
                preferences[SIMPLIFIED_UI_KEY] = true
                preferences[CONFIRM_ACTIONS_KEY] = true
                preferences[LARGE_BUTTONS_KEY] = true
            } else {
                preferences[TEXT_SCALE_KEY] = DEFAULT_TEXT_SCALE
                preferences[HIGH_CONTRAST_KEY] = false
                preferences[SIMPLIFIED_UI_KEY] = false
                preferences[CONFIRM_ACTIONS_KEY] = false
                preferences[LARGE_BUTTONS_KEY] = false
            }
        }
    }

    /**
     * 設定文字縮放比例
     */
    suspend fun setTextScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[TEXT_SCALE_KEY] = scale
        }
    }

    /**
     * 設定高對比度模式
     */
    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIGH_CONTRAST_KEY] = enabled
        }
    }

    /**
     * 設定簡化介面
     */
    suspend fun setSimplifiedUI(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SIMPLIFIED_UI_KEY] = enabled
        }
    }

    /**
     * 設定語音反饋
     */
    suspend fun setVoiceFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_FEEDBACK_KEY] = enabled
        }
    }

    /**
     * 設定操作確認
     */
    suspend fun setConfirmActions(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CONFIRM_ACTIONS_KEY] = enabled
        }
    }

    /**
     * 設定大按鈕模式
     */
    suspend fun setLargeButtons(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LARGE_BUTTONS_KEY] = enabled
        }
    }

    /**
     * 重置所有設定
     */
    suspend fun resetSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}