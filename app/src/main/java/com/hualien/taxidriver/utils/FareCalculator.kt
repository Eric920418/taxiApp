package com.hualien.taxidriver.utils

import android.util.Log
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.FareConfigData
import java.time.LocalTime
import kotlin.math.ceil

/**
 * 計程車車資計算工具
 *
 * 費率從 Server 動態取得，可在 .env 中調整：
 * - FARE_BASE_PRICE: 起跳價（預設 100 元）
 * - FARE_BASE_DISTANCE_METERS: 起跳距離（預設 1250 公尺）
 * - FARE_JUMP_DISTANCE_METERS: 每跳距離（預設 200 公尺）
 * - FARE_JUMP_PRICE: 每跳價格（預設 5 元）
 * - FARE_NIGHT_SURCHARGE_RATE: 夜間加成比例（預設 0.2）
 * - FARE_NIGHT_START_HOUR: 夜間開始時間（預設 23）
 * - FARE_NIGHT_END_HOUR: 夜間結束時間（預設 6）
 */
object FareCalculator {

    private const val TAG = "FareCalculator"

    /**
     * 費率設定
     */
    data class FareConfig(
        val basePrice: Int = 100,              // 起跳價（元）
        val baseDistanceMeters: Int = 1250,    // 起跳距離（公尺）= 1.25 公里
        val meterJumpDistanceMeters: Int = 200, // 每跳距離（公尺）
        val meterJumpPrice: Int = 5,           // 每跳價格（元）
        val nightSurchargeRate: Double = 0.2,  // 夜間加成比例（20%）
        val nightStartHour: Int = 23,          // 夜間開始時間
        val nightEndHour: Int = 6              // 夜間結束時間
    )

    // 預設配置（fallback）
    private val defaultConfig = FareConfig()

    // 當前使用的配置（可由 Server 更新）
    private var currentConfig: FareConfig = defaultConfig
    private var configLoaded = false

    /**
     * 從 Server 取得費率配置並更新
     * 建議在 Application 或 MainActivity 啟動時呼叫
     */
    suspend fun loadConfigFromServer(): Boolean {
        return try {
            val response = RetrofitClient.apiService.getFareConfig()
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data?.let { serverConfig ->
                    updateConfig(serverConfig)
                    Log.i(TAG, "費率配置已從 Server 載入: $currentConfig")
                    true
                } ?: false
            } else {
                Log.w(TAG, "取得費率配置失敗，使用預設值")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "載入費率配置異常，使用預設值: ${e.message}")
            false
        }
    }

    /**
     * 從 Server 回應更新配置
     */
    fun updateConfig(serverConfig: FareConfigData) {
        currentConfig = FareConfig(
            basePrice = serverConfig.basePrice,
            baseDistanceMeters = serverConfig.baseDistanceMeters,
            meterJumpDistanceMeters = serverConfig.jumpDistanceMeters,
            meterJumpPrice = serverConfig.jumpPrice,
            nightSurchargeRate = serverConfig.nightSurchargeRate,
            nightStartHour = serverConfig.nightStartHour,
            nightEndHour = serverConfig.nightEndHour
        )
        configLoaded = true
    }

    /**
     * 取得當前費率配置
     */
    fun getConfig(): FareConfig = currentConfig

    /**
     * 費率是否已從 Server 載入
     */
    fun isConfigLoaded(): Boolean = configLoaded

    /**
     * 四捨五入到最接近的 5 元
     * 例：21 → 20, 23 → 25, 27 → 25, 28 → 30
     */
    private fun roundToNearest5(value: Int): Int {
        return ((value + 2) / 5) * 5
    }

    /**
     * 計算車資
     *
     * 計算公式（跳錶制）：
     * - 起跳距離內：起跳價
     * - 超過後每跳一次加價
     * - 車資尾數只會是 0 或 5
     *
     * @param distanceMeters 行駛距離（公尺）
     * @param isNightTime 是否為夜間時段（可選，預設根據當前時間判斷）
     * @param config 費率設定（可選，使用當前配置，優先使用 Server 配置）
     * @return 計算結果
     */
    fun calculateFare(
        distanceMeters: Int,
        isNightTime: Boolean? = null,
        config: FareConfig = currentConfig
    ): FareResult {

        // 轉換為公里（用於顯示）
        val distanceKm = distanceMeters / 1000.0

        // 計算超出起跳距離的部分
        val extraDistanceMeters = maxOf(0, distanceMeters - config.baseDistanceMeters)

        // 計算跳錶次數（無條件進位，超過就跳）
        val meterJumps = if (extraDistanceMeters > 0) {
            ceil(extraDistanceMeters.toDouble() / config.meterJumpDistanceMeters).toInt()
        } else {
            0
        }

        // 里程費 = 跳錶次數 × 每跳價格（尾數只會是 0 或 5）
        val distanceFare = meterJumps * config.meterJumpPrice
        val fare = config.basePrice + distanceFare

        // 判斷是否為夜間時段
        val nightTime = isNightTime ?: isCurrentlyNightTime(
            config.nightStartHour,
            config.nightEndHour
        )

        // 夜間加成（四捨五入到 5 元，確保尾數為 0 或 5）
        val nightSurcharge = if (nightTime) {
            val rawSurcharge = fare * config.nightSurchargeRate
            roundToNearest5(rawSurcharge.toInt())
        } else {
            0
        }

        val totalFare = fare + nightSurcharge

        return FareResult(
            baseFare = config.basePrice,
            distanceFare = distanceFare,
            nightSurcharge = nightSurcharge,
            totalFare = totalFare,
            distanceKm = distanceKm,
            isNightTime = nightTime
        )
    }

    /**
     * 判斷當前是否為夜間時段
     */
    private fun isCurrentlyNightTime(nightStartHour: Int, nightEndHour: Int): Boolean {
        return try {
            val now = LocalTime.now()
            val currentHour = now.hour

            // 跨日情況（例如 23:00 - 06:00）
            if (nightStartHour > nightEndHour) {
                currentHour >= nightStartHour || currentHour < nightEndHour
            } else {
                currentHour in nightStartHour until nightEndHour
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 根據距離範圍估算車資（返回最小值和最大值）
     * 用於在不確定確切距離時提供參考
     */
    fun estimateFareRange(
        minDistanceMeters: Int,
        maxDistanceMeters: Int,
        config: FareConfig = currentConfig
    ): Pair<Int, Int> {
        val minFare = calculateFare(minDistanceMeters, false, config).totalFare
        val maxFare = calculateFare(maxDistanceMeters, true, config).totalFare
        return Pair(minFare, maxFare)
    }

    /**
     * 格式化車資顯示
     */
    fun FareResult.formatDisplay(): String {
        return buildString {
            append("預估車資：NT$ $totalFare\n")
            append("├─ 起跳價：NT$ $baseFare\n")
            if (distanceFare > 0) {
                append("├─ 里程費：NT$ $distanceFare\n")
            }
            if (nightSurcharge > 0) {
                append("├─ 夜間加成：NT$ $nightSurcharge\n")
            }
            append("└─ 距離：${String.format("%.2f", distanceKm)} 公里")
            if (isNightTime) {
                append("\n   （夜間時段 23:00-06:00）")
            }
        }
    }
}

/**
 * 車資計算結果
 */
data class FareResult(
    val baseFare: Int,          // 起跳價
    val distanceFare: Int,      // 里程費
    val nightSurcharge: Int,    // 夜間加成
    val totalFare: Int,         // 總車資
    val distanceKm: Double,     // 距離（公里）
    val isNightTime: Boolean    // 是否夜間
)
