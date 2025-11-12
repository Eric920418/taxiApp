package com.hualien.taxidriver.utils

import java.time.LocalTime
import kotlin.math.ceil

/**
 * 計程車車資計算工具
 *
 * 花蓮計程車費率（可根據實際情況調整）：
 * - 起跳價：100 元（1.5 公里內）
 * - 續跳：每 250 公尺 5 元
 * - 延滯計時：時速低於 5 公里時，每 100 秒 5 元
 * - 夜間加成：23:00-06:00 加收 20%
 */
object FareCalculator {

    /**
     * 費率設定
     */
    data class FareConfig(
        val basePrice: Int = 100,              // 起跳價（元）
        val baseDistanceMeters: Int = 1500,    // 起跳距離（公尺）
        val extraPricePerUnit: Int = 5,        // 續跳單價（元）
        val extraDistanceMeters: Int = 250,    // 續跳距離單位（公尺）
        val nightSurchargeRate: Double = 0.2,  // 夜間加成比例（20%）
        val nightStartHour: Int = 23,          // 夜間開始時間
        val nightEndHour: Int = 6              // 夜間結束時間
    )

    private val defaultConfig = FareConfig()

    /**
     * 計算車資
     *
     * @param distanceMeters 行駛距離（公尺）
     * @param isNightTime 是否為夜間時段（可選，預設根據當前時間判斷）
     * @param config 費率設定（可選，使用預設花蓮費率）
     * @return 計算結果
     */
    fun calculateFare(
        distanceMeters: Int,
        isNightTime: Boolean? = null,
        config: FareConfig = defaultConfig
    ): FareResult {

        // 基本車資計算
        var fare = config.basePrice

        // 如果超過起跳距離，計算續跳費用
        if (distanceMeters > config.baseDistanceMeters) {
            val extraDistance = distanceMeters - config.baseDistanceMeters
            val extraUnits = ceil(extraDistance.toDouble() / config.extraDistanceMeters).toInt()
            fare += extraUnits * config.extraPricePerUnit
        }

        // 判斷是否為夜間時段
        val nightTime = isNightTime ?: isCurrentlyNightTime(
            config.nightStartHour,
            config.nightEndHour
        )

        // 夜間加成
        val nightSurcharge = if (nightTime) {
            (fare * config.nightSurchargeRate).toInt()
        } else {
            0
        }

        val totalFare = fare + nightSurcharge

        return FareResult(
            baseFare = config.basePrice,
            distanceFare = fare - config.basePrice,
            nightSurcharge = nightSurcharge,
            totalFare = totalFare,
            distanceKm = distanceMeters / 1000.0,
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
        config: FareConfig = defaultConfig
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
