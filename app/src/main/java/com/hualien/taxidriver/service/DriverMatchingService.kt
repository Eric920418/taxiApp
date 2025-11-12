package com.hualien.taxidriver.service

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.hualien.taxidriver.viewmodel.NearbyDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 智能司機匹配服務
 * 結合直線距離和實際路程距離，為乘客匹配最佳司機
 */
class DriverMatchingService(context: Context) {

    private val distanceMatrixService = DistanceMatrixApiService(context)

    companion object {
        private const val TAG = "DriverMatchingService"

        /**
         * 計算兩點之間的直線距離（公尺）- Haversine 公式
         */
        fun calculateStraightDistance(point1: LatLng, point2: LatLng): Double {
            val R = 6371000.0 // 地球半徑（公尺）

            val lat1Rad = Math.toRadians(point1.latitude)
            val lat2Rad = Math.toRadians(point2.latitude)
            val deltaLat = Math.toRadians(point2.latitude - point1.latitude)
            val deltaLng = Math.toRadians(point2.longitude - point1.longitude)

            val a = sin(deltaLat / 2).pow(2) +
                    cos(lat1Rad) * cos(lat2Rad) *
                    sin(deltaLng / 2).pow(2)

            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return R * c
        }

        /**
         * 格式化距離顯示
         */
        fun formatDistance(meters: Double): String {
            return if (meters >= 1000) {
                String.format("%.1f 公里", meters / 1000)
            } else {
                "${meters.toInt()} 公尺"
            }
        }
    }

    /**
     * 為司機列表添加 ETA 資訊（預估到達時間）
     *
     * @param drivers 附近司機列表
     * @param passengerLocation 乘客位置
     * @return 包含 ETA 資訊的司機列表
     */
    suspend fun enrichDriversWithETA(
        drivers: List<NearbyDriver>,
        passengerLocation: LatLng
    ): Result<List<DriverWithETA>> = withContext(Dispatchers.IO) {
        try {
            if (drivers.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // 先計算直線距離，過濾太遠的司機（超過 10 公里）
            val nearbyDrivers = drivers.filter { driver ->
                val straightDistance = calculateStraightDistance(driver.location, passengerLocation)
                straightDistance <= 10000 // 10 公里內
            }

            if (nearbyDrivers.isEmpty()) {
                Log.w(TAG, "No drivers within 10km radius")
                return@withContext Result.success(emptyList())
            }

            // 計算實際路程距離和時間
            val driverLocations = nearbyDrivers.map { it.location }
            val etaResult = distanceMatrixService.calculateDriverETAs(
                driverLocations,
                passengerLocation
            )

            etaResult.onSuccess { etaMap ->
                val driversWithETA = nearbyDrivers.mapIndexed { index, driver ->
                    val eta = etaMap[index] ?: DistanceMatrixElement(
                        distanceMeters = Int.MAX_VALUE,
                        distanceText = "未知",
                        durationSeconds = Int.MAX_VALUE,
                        durationText = "未知"
                    )

                    DriverWithETA(
                        driver = driver,
                        distanceMeters = eta.distanceMeters,
                        distanceText = eta.distanceText,
                        etaSeconds = eta.durationSeconds,
                        etaText = eta.durationText
                    )
                }

                // 按照 ETA 排序（最快到達的在前面）
                val sorted = driversWithETA.sortedBy { it.etaSeconds }

                Log.d(TAG, "Enriched ${sorted.size} drivers with ETA")
                return@withContext Result.success(sorted)
            }

            etaResult.onFailure { error ->
                Log.e(TAG, "Failed to get driver ETAs", error)
                // 如果 API 失敗，使用直線距離作為 fallback
                val fallbackDrivers = nearbyDrivers.map { driver ->
                    val straightDistance = calculateStraightDistance(driver.location, passengerLocation)
                    DriverWithETA(
                        driver = driver,
                        distanceMeters = straightDistance.toInt(),
                        distanceText = formatDistance(straightDistance),
                        etaSeconds = (straightDistance / 10).toInt(), // 假設平均速度 10 m/s
                        etaText = "約 ${(straightDistance / 600).toInt() + 1} 分鐘"
                    )
                }.sortedBy { it.distanceMeters }

                return@withContext Result.success(fallbackDrivers)
            }

            Result.failure(Exception("未知錯誤"))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich drivers with ETA", e)
            Result.failure(e)
        }
    }

    /**
     * 找出最適合的司機（最快到達）
     *
     * @param drivers 附近司機列表
     * @param passengerLocation 乘客位置
     * @return 最佳司機及其 ETA 資訊
     */
    suspend fun findBestDriver(
        drivers: List<NearbyDriver>,
        passengerLocation: LatLng
    ): Result<DriverWithETA?> = withContext(Dispatchers.IO) {
        try {
            val result = enrichDriversWithETA(drivers, passengerLocation)

            result.onSuccess { driversWithETA ->
                if (driversWithETA.isEmpty()) {
                    return@withContext Result.success(null)
                }

                // 第一個就是最快到達的（已排序）
                val bestDriver = driversWithETA.first()
                Log.d(TAG, "Best driver: ${bestDriver.driver.driverName}, ETA: ${bestDriver.etaText}")

                return@withContext Result.success(bestDriver)
            }

            result.onFailure { error ->
                return@withContext Result.failure(error)
            }

            Result.success(null)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to find best driver", e)
            Result.failure(e)
        }
    }

    /**
     * 批量計算司機到多個訂單的距離
     * （司機端用於顯示訂單列表的距離）
     *
     * @param driverLocation 司機當前位置
     * @param orderLocations 訂單位置列表
     * @return Map<訂單索引, DistanceMatrixElement>
     */
    suspend fun calculateOrderDistances(
        driverLocation: LatLng,
        orderLocations: List<LatLng>
    ): Result<Map<Int, DistanceMatrixElement>> = withContext(Dispatchers.IO) {
        try {
            if (orderLocations.isEmpty()) {
                return@withContext Result.success(emptyMap())
            }

            val result = distanceMatrixService.getDistanceMatrix(
                origins = listOf(driverLocation),
                destinations = orderLocations
            )

            result.onSuccess { matrix ->
                if (matrix.isEmpty() || matrix[0].isEmpty()) {
                    return@withContext Result.success(emptyMap())
                }

                // 將矩陣轉換為 Map（訂單索引 -> 距離資訊）
                val distanceMap = matrix[0].mapIndexed { index, element ->
                    index to element
                }.toMap()

                Log.d(TAG, "Calculated distances to ${distanceMap.size} orders")
                return@withContext Result.success(distanceMap)
            }

            result.onFailure { error ->
                return@withContext Result.failure(error)
            }

            Result.failure(Exception("未知錯誤"))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate order distances", e)
            Result.failure(e)
        }
    }
}

/**
 * 包含 ETA 資訊的司機資料
 */
data class DriverWithETA(
    val driver: NearbyDriver,       // 原始司機資料
    val distanceMeters: Int,        // 實際路程距離（公尺）
    val distanceText: String,       // 距離文字
    val etaSeconds: Int,            // 預估到達時間（秒）
    val etaText: String             // ETA 文字（例如：5 分鐘）
)
