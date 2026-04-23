package com.hualien.taxidriver.utils

import android.util.Log
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.DayFareDto
import com.hualien.taxidriver.data.remote.dto.FareConfigData
import com.hualien.taxidriver.data.remote.dto.NightFareDto
import com.hualien.taxidriver.data.remote.dto.SpringFestivalDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.ceil

// 花蓮縣府公告以台北時區為準。明確指定避免裝置時區（旅外）導致計費錯誤。
private val TAIPEI: ZoneId = ZoneId.of("Asia/Taipei")

/**
 * 計程車車資計算工具
 *
 * 對齊花蓮縣政府計程車費率公告（與 Server FareConfigService 同步）：
 *   - 日費率：起跳 100/1000m、每跳 5/230m、低速 120 秒/5 元
 *   - 夜費率（22:00–06:00）：起跳 100/834m、每跳 5/192m、低速 100 秒/5 元
 *   - 春節：全日套夜費率 + 每趟加收 50 元
 *
 * 費率從 Server 動態取得（GET /api/config/fare），admin 可在 Web 後台調整。
 * Server 未連線時 fallback 到本檔內預設值。
 */
object FareCalculator {

    private const val TAG = "FareCalculator"

    data class DayFare(
        val basePrice: Int = 100,
        val baseDistanceMeters: Int = 1000,
        val jumpDistanceMeters: Int = 230,
        val jumpPrice: Int = 5,
        val slowTrafficSeconds: Int = 120,
        val slowTrafficPrice: Int = 5
    )

    data class NightFare(
        val basePrice: Int = 100,
        val baseDistanceMeters: Int = 834,
        val jumpDistanceMeters: Int = 192,
        val jumpPrice: Int = 5,
        val slowTrafficSeconds: Int = 100,
        val slowTrafficPrice: Int = 5,
        val startHour: Int = 22,
        val endHour: Int = 6
    )

    data class SpringFestival(
        val enabled: Boolean = false,
        val startDate: String = "2026-02-16",
        val endDate: String = "2026-02-22",
        val perTripSurcharge: Int = 50
    )

    data class FareConfig(
        val day: DayFare = DayFare(),
        val night: NightFare = NightFare(),
        val springFestival: SpringFestival = SpringFestival()
    )

    private val defaultConfig = FareConfig()

    private var currentConfig: FareConfig = defaultConfig
    private var configLoaded = false

    /** 愛心卡每趟補貼金額（從 Server 載入，fallback 73） */
    var loveCardSubsidyAmount: Int = 73
        private set

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

    fun updateConfig(serverConfig: FareConfigData) {
        currentConfig = FareConfig(
            day = serverConfig.day.toDay(),
            night = serverConfig.night.toNight(),
            springFestival = serverConfig.springFestival.toSpringFestival()
        )
        loveCardSubsidyAmount = serverConfig.loveCardSubsidyAmount
        configLoaded = true
    }

    private fun DayFareDto.toDay() = DayFare(
        basePrice = basePrice,
        baseDistanceMeters = baseDistanceMeters,
        jumpDistanceMeters = jumpDistanceMeters,
        jumpPrice = jumpPrice,
        slowTrafficSeconds = slowTrafficSeconds,
        slowTrafficPrice = slowTrafficPrice
    )

    private fun NightFareDto.toNight() = NightFare(
        basePrice = basePrice,
        baseDistanceMeters = baseDistanceMeters,
        jumpDistanceMeters = jumpDistanceMeters,
        jumpPrice = jumpPrice,
        slowTrafficSeconds = slowTrafficSeconds,
        slowTrafficPrice = slowTrafficPrice,
        startHour = startHour,
        endHour = endHour
    )

    private fun SpringFestivalDto.toSpringFestival() = SpringFestival(
        enabled = enabled,
        startDate = startDate,
        endDate = endDate,
        perTripSurcharge = perTripSurcharge
    )

    fun getConfig(): FareConfig = currentConfig

    fun isConfigLoaded(): Boolean = configLoaded

    /**
     * 計算車資（跳錶制）
     *
     * @param distanceMeters 行駛距離（公尺）
     * @param at 計算當下時間（未指定則用 now），可指定以測試夜間/春節
     * @param slowTrafficSeconds 低速累積秒數（Phase A 預設 0，Phase C 接 GPS）
     * @param config 費率設定（預設使用 currentConfig）
     */
    fun calculateFare(
        distanceMeters: Int,
        at: ZonedDateTime = ZonedDateTime.now(TAIPEI),
        slowTrafficSeconds: Int = 0,
        config: FareConfig = currentConfig
    ): FareResult {
        val taipei = at.withZoneSameInstant(TAIPEI)
        val isSpringFestival = isSpringFestival(taipei.toLocalDate(), config.springFestival)
        val isNight = isNightTime(taipei.toLocalTime(), config.night)
        val useNightSchedule = isSpringFestival || isNight

        val basePrice: Int
        val baseDistanceMeters: Int
        val jumpDistanceMeters: Int
        val jumpPrice: Int
        val slowSecPerUnit: Int
        val slowPricePerUnit: Int
        if (useNightSchedule) {
            basePrice = config.night.basePrice
            baseDistanceMeters = config.night.baseDistanceMeters
            jumpDistanceMeters = config.night.jumpDistanceMeters
            jumpPrice = config.night.jumpPrice
            slowSecPerUnit = config.night.slowTrafficSeconds
            slowPricePerUnit = config.night.slowTrafficPrice
        } else {
            basePrice = config.day.basePrice
            baseDistanceMeters = config.day.baseDistanceMeters
            jumpDistanceMeters = config.day.jumpDistanceMeters
            jumpPrice = config.day.jumpPrice
            slowSecPerUnit = config.day.slowTrafficSeconds
            slowPricePerUnit = config.day.slowTrafficPrice
        }

        val extraDistanceMeters = maxOf(0, distanceMeters - baseDistanceMeters)
        val meterJumps = if (extraDistanceMeters > 0) {
            ceil(extraDistanceMeters.toDouble() / jumpDistanceMeters).toInt()
        } else {
            0
        }
        val distanceFare = meterJumps * jumpPrice

        val slowTrafficUnits = if (slowTrafficSeconds > 0) slowTrafficSeconds / slowSecPerUnit else 0
        val slowTrafficFare = slowTrafficUnits * slowPricePerUnit

        val springFestivalSurcharge = if (isSpringFestival) config.springFestival.perTripSurcharge else 0

        val totalFare = basePrice + distanceFare + slowTrafficFare + springFestivalSurcharge

        return FareResult(
            baseFare = basePrice,
            distanceFare = distanceFare,
            slowTrafficFare = slowTrafficFare,
            springFestivalSurcharge = springFestivalSurcharge,
            totalFare = totalFare,
            distanceKm = distanceMeters / 1000.0,
            isNightTime = isNight,
            isSpringFestival = isSpringFestival,
            appliedSchedule = if (useNightSchedule) Schedule.NIGHT else Schedule.DAY
        )
    }

    private fun isNightTime(at: LocalTime, night: NightFare): Boolean {
        val hour = at.hour
        return if (night.startHour > night.endHour) {
            hour >= night.startHour || hour < night.endHour
        } else {
            hour in night.startHour until night.endHour
        }
    }

    private fun isSpringFestival(at: LocalDate, sf: SpringFestival): Boolean {
        if (!sf.enabled) return false
        val today = at.toString()  // ISO YYYY-MM-DD
        return today in sf.startDate..sf.endDate
    }

    /**
     * 由總車資反推「估算距離」（公里）
     *
     * 用途：當行程沒有 tripDistance（乘客未指定目的地、現叫車）時，從司機輸入的
     *      最終跳錶金額反推距離給「今日統計里程」用。**不適合用於精確計費**（反推
     *      固有精度問題：fare = 105 可能跑 1001m～1230m 任一）。
     *
     * 演算法：
     *   amount <= basePrice → 起跳距離
     *   amount > basePrice  → baseDistance + jumps × jumpDistance
     *                         （取「剛跳到第 N 跳」的距離 = 上界，貼近實際跑的距離）
     *
     * 限制：
     *   - 假設 amount 不含春節加成、不含低速計時、不含其他 surcharge
     *     呼叫端要先扣掉這些（目前 HomeViewModel 反推情境是裸跳錶，符合）
     *   - 用 at 判斷套日/夜費率；at 預設 now，台北時區
     */
    fun estimateDistanceFromFare(
        amount: Int,
        at: ZonedDateTime = ZonedDateTime.now(TAIPEI),
        config: FareConfig = currentConfig
    ): Double {
        val taipei = at.withZoneSameInstant(TAIPEI)
        val isNight = isNightTime(taipei.toLocalTime(), config.night)
        val isSpringFestival = isSpringFestival(taipei.toLocalDate(), config.springFestival)
        val useNightSchedule = isSpringFestival || isNight

        val basePrice: Int
        val baseDistanceMeters: Int
        val jumpDistanceMeters: Int
        val jumpPrice: Int
        if (useNightSchedule) {
            basePrice = config.night.basePrice
            baseDistanceMeters = config.night.baseDistanceMeters
            jumpDistanceMeters = config.night.jumpDistanceMeters
            jumpPrice = config.night.jumpPrice
        } else {
            basePrice = config.day.basePrice
            baseDistanceMeters = config.day.baseDistanceMeters
            jumpDistanceMeters = config.day.jumpDistanceMeters
            jumpPrice = config.day.jumpPrice
        }

        if (amount <= basePrice) return baseDistanceMeters / 1000.0

        val extraFare = amount - basePrice
        val jumps = extraFare / jumpPrice  // 整數除法
        val estimatedMeters = baseDistanceMeters + jumps * jumpDistanceMeters
        return estimatedMeters / 1000.0
    }

    /**
     * 由低速秒數計算「建議加收」金額（駐車費）
     *
     * 依當下時間自動套日/夜 schedule 的 slowTrafficSeconds 與 slowTrafficPrice。
     * 公式：floor(seconds / threshold) × price
     *
     * 用途：FareDialog 顯示給司機的建議加收金額；司機自行決定是否加上去。
     * 回傳 0 表示不到一個門檻（不夠加錢）— 呼叫端可選擇不顯示或顯示 0。
     */
    fun suggestSlowTrafficFare(
        seconds: Int,
        at: ZonedDateTime = ZonedDateTime.now(TAIPEI),
        config: FareConfig = currentConfig
    ): Int {
        if (seconds <= 0) return 0
        val taipei = at.withZoneSameInstant(TAIPEI)
        val isNight = isNightTime(taipei.toLocalTime(), config.night)
        val isSF = isSpringFestival(taipei.toLocalDate(), config.springFestival)
        // DayFare / NightFare 沒共同 supertype，分支取屬性
        val (threshold, price) = if (isNight || isSF) {
            config.night.slowTrafficSeconds to config.night.slowTrafficPrice
        } else {
            config.day.slowTrafficSeconds to config.day.slowTrafficPrice
        }
        return (seconds / threshold) * price
    }

    /**
     * 估算車資範圍（最小 = 日費率短程，最大 = 夜費率長程）
     */
    fun estimateFareRange(
        minDistanceMeters: Int,
        maxDistanceMeters: Int,
        config: FareConfig = currentConfig
    ): Pair<Int, Int> {
        val today = LocalDate.now(TAIPEI)
        val dayMin = calculateFare(
            distanceMeters = minDistanceMeters,
            at = ZonedDateTime.of(today, LocalTime.NOON, TAIPEI),
            config = config
        ).totalFare
        val nightMax = calculateFare(
            distanceMeters = maxDistanceMeters,
            at = ZonedDateTime.of(today, LocalTime.of(23, 0), TAIPEI),
            config = config
        ).totalFare
        return Pair(dayMin, nightMax)
    }

    fun FareResult.formatDisplay(): String = buildString {
        append("預估車資：NT$ $totalFare\n")
        append("├─ 起跳價：NT$ $baseFare\n")
        if (distanceFare > 0) append("├─ 里程費：NT$ $distanceFare\n")
        if (slowTrafficFare > 0) append("├─ 低速計時：NT$ $slowTrafficFare\n")
        if (springFestivalSurcharge > 0) append("├─ 春節加成：NT$ $springFestivalSurcharge\n")
        append("└─ 距離：${String.format("%.2f", distanceKm)} 公里")
        when {
            isSpringFestival -> append("\n   （春節期間 — 全日套夜費率）")
            isNightTime -> append("\n   （夜間時段適用夜費率）")
        }
    }
}

enum class Schedule { DAY, NIGHT }

data class FareResult(
    val baseFare: Int,                 // 起跳價
    val distanceFare: Int,             // 里程費
    val slowTrafficFare: Int,          // 低速計時費（Phase C 才會 > 0）
    val springFestivalSurcharge: Int,  // 春節每趟加收
    val totalFare: Int,                // 總車資
    val distanceKm: Double,            // 距離（公里）
    val isNightTime: Boolean,          // 是否夜間時段
    val isSpringFestival: Boolean,     // 是否春節期間
    val appliedSchedule: Schedule      // 實際套用的費率組（DAY 或 NIGHT）
)
