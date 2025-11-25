package com.hualien.taxidriver.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.hualien.taxidriver.domain.model.DriverAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 電池優化管理器
 *
 * 功能：
 * 1. 根據司機狀態動態調整定位頻率
 * 2. 監聽電池狀態變化
 * 3. 根據電池電量和省電模式優化定位策略
 *
 * 定位頻率策略：
 * - 載客中 (ON_TRIP): 高頻率（5秒），高精度
 * - 可接單 (AVAILABLE): 中頻率（15秒），平衡精度
 * - 休息中 (REST): 低頻率（60秒），節省電量
 * - 離線 (OFFLINE): 停止定位
 *
 * 電池優化策略：
 * - 電量 > 50%: 正常模式
 * - 電量 20-50%: 節能模式（頻率降低 50%）
 * - 電量 < 20% 或省電模式: 極省電模式（頻率降低 75%）
 */
class BatteryOptimizationManager(private val context: Context) {

    companion object {
        private const val TAG = "BatteryOptManager"

        // 基礎定位頻率（毫秒）
        const val HIGH_FREQUENCY_INTERVAL = 5000L       // 載客中：5秒
        const val MEDIUM_FREQUENCY_INTERVAL = 15000L    // 可接單：15秒
        const val LOW_FREQUENCY_INTERVAL = 60000L       // 休息中：60秒
        const val VERY_LOW_FREQUENCY_INTERVAL = 300000L // 極省電：5分鐘

        // 電池閾值
        private const val BATTERY_HIGH_THRESHOLD = 50   // 高電量閾值
        private const val BATTERY_LOW_THRESHOLD = 20    // 低電量閾值
    }

    // 當前定位配置
    private val _locationConfig = MutableStateFlow(LocationConfig.default())
    val locationConfig: StateFlow<LocationConfig> = _locationConfig.asStateFlow()

    // 電池狀態
    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    // 電池狀態廣播接收器
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryState()
        }
    }

    // 省電模式廣播接收器
    private val powerSaveModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryState()
        }
    }

    private var isRegistered = false
    private var currentDriverStatus: DriverAvailability = DriverAvailability.OFFLINE

    /**
     * 開始監聽電池狀態
     */
    fun startMonitoring() {
        if (isRegistered) return

        try {
            // 註冊電池狀態變化監聽
            val batteryFilter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            }
            context.registerReceiver(batteryReceiver, batteryFilter)

            // 註冊省電模式變化監聽
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val powerSaveFilter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                context.registerReceiver(powerSaveModeReceiver, powerSaveFilter)
            }

            isRegistered = true
            Log.d(TAG, "✅ 電池監聽已啟動")

            // 初始化狀態
            updateBatteryState()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 註冊電池監聽失敗", e)
        }
    }

    /**
     * 停止監聽電池狀態
     */
    fun stopMonitoring() {
        if (!isRegistered) return

        try {
            context.unregisterReceiver(batteryReceiver)
            context.unregisterReceiver(powerSaveModeReceiver)
            isRegistered = false
            Log.d(TAG, "✅ 電池監聽已停止")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 取消電池監聯失敗", e)
        }
    }

    /**
     * 更新司機狀態並重新計算定位配置
     */
    fun updateDriverStatus(status: DriverAvailability) {
        currentDriverStatus = status
        Log.d(TAG, "司機狀態更新: $status")
        recalculateLocationConfig()
    }

    /**
     * 更新電池狀態
     */
    private fun updateBatteryState() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val isCharging = batteryManager?.isCharging ?: false
        val isPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager?.isPowerSaveMode ?: false
        } else {
            false
        }

        val newState = BatteryState(
            level = level,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode
        )

        _batteryState.value = newState

        Log.d(TAG, "========== 電池狀態更新 ==========")
        Log.d(TAG, "電量: $level%")
        Log.d(TAG, "充電中: $isCharging")
        Log.d(TAG, "省電模式: $isPowerSaveMode")

        recalculateLocationConfig()
    }

    /**
     * 重新計算定位配置
     */
    private fun recalculateLocationConfig() {
        val battery = _batteryState.value

        // 基於司機狀態決定基礎頻率
        val baseInterval = when (currentDriverStatus) {
            DriverAvailability.ON_TRIP -> HIGH_FREQUENCY_INTERVAL
            DriverAvailability.AVAILABLE -> MEDIUM_FREQUENCY_INTERVAL
            DriverAvailability.REST -> LOW_FREQUENCY_INTERVAL
            DriverAvailability.OFFLINE -> 0L // 停止定位
        }

        // 如果是離線狀態，直接返回停止配置
        if (baseInterval == 0L) {
            _locationConfig.value = LocationConfig(
                updateInterval = 0L,
                fastestInterval = 0L,
                priority = Priority.PRIORITY_NO_POWER,
                shouldTrack = false,
                reason = "司機離線"
            )
            return
        }

        // 計算電池優化係數
        val batteryMultiplier = when {
            battery.isCharging -> 1.0  // 充電中不節省
            battery.isPowerSaveMode -> 4.0  // 省電模式 4x
            battery.level < BATTERY_LOW_THRESHOLD -> 3.0  // 低電量 3x
            battery.level < BATTERY_HIGH_THRESHOLD -> 1.5  // 中等電量 1.5x
            else -> 1.0  // 高電量正常
        }

        // 計算最終更新間隔
        val adjustedInterval = (baseInterval * batteryMultiplier).toLong()

        // 計算最快更新間隔（為主間隔的一半，最少 3 秒）
        val fastestInterval = maxOf(adjustedInterval / 2, 3000L)

        // 確定精度優先級
        val priority = when {
            currentDriverStatus == DriverAvailability.ON_TRIP -> Priority.PRIORITY_HIGH_ACCURACY
            battery.level < BATTERY_LOW_THRESHOLD -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else -> Priority.PRIORITY_HIGH_ACCURACY
        }

        val reason = buildString {
            append("狀態:$currentDriverStatus")
            if (battery.isCharging) append(", 充電中")
            if (battery.isPowerSaveMode) append(", 省電模式")
            if (battery.level < BATTERY_LOW_THRESHOLD) append(", 低電量")
        }

        val newConfig = LocationConfig(
            updateInterval = adjustedInterval,
            fastestInterval = fastestInterval,
            priority = priority,
            shouldTrack = true,
            reason = reason
        )

        _locationConfig.value = newConfig

        Log.d(TAG, "========== 定位配置更新 ==========")
        Log.d(TAG, "基礎間隔: ${baseInterval}ms")
        Log.d(TAG, "電池係數: ${batteryMultiplier}x")
        Log.d(TAG, "調整後間隔: ${adjustedInterval}ms")
        Log.d(TAG, "最快間隔: ${fastestInterval}ms")
        Log.d(TAG, "精度優先級: $priority")
        Log.d(TAG, "原因: $reason")
    }

    /**
     * 獲取當前建議的定位配置
     */
    fun getCurrentConfig(): LocationConfig = _locationConfig.value

    /**
     * 強制使用高頻率定位（用於緊急情況）
     */
    fun forceHighFrequency() {
        _locationConfig.value = LocationConfig(
            updateInterval = HIGH_FREQUENCY_INTERVAL,
            fastestInterval = 3000L,
            priority = Priority.PRIORITY_HIGH_ACCURACY,
            shouldTrack = true,
            reason = "強制高頻率模式"
        )
        Log.d(TAG, "⚡ 強制啟用高頻率定位")
    }
}

/**
 * 定位配置
 */
data class LocationConfig(
    val updateInterval: Long,      // 更新間隔（毫秒）
    val fastestInterval: Long,     // 最快更新間隔
    val priority: Priority,        // 定位精度優先級
    val shouldTrack: Boolean,      // 是否應該追蹤
    val reason: String             // 配置原因（用於日誌）
) {
    companion object {
        fun default() = LocationConfig(
            updateInterval = BatteryOptimizationManager.MEDIUM_FREQUENCY_INTERVAL,
            fastestInterval = 5000L,
            priority = Priority.PRIORITY_HIGH_ACCURACY,
            shouldTrack = false,
            reason = "默認配置"
        )
    }
}

/**
 * 電池狀態
 */
data class BatteryState(
    val level: Int = 100,           // 電量百分比
    val isCharging: Boolean = false, // 是否充電中
    val isPowerSaveMode: Boolean = false // 是否省電模式
)

/**
 * 定位優先級（對應 Google Location Services Priority）
 */
enum class Priority {
    PRIORITY_HIGH_ACCURACY,         // 高精度（最耗電）
    PRIORITY_BALANCED_POWER_ACCURACY, // 平衡精度和電量
    PRIORITY_LOW_POWER,             // 低功耗
    PRIORITY_NO_POWER               // 不使用位置（被動監聽）
}
