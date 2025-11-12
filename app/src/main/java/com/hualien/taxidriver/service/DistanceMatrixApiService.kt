package com.hualien.taxidriver.service

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Distance Matrix API 服務
 * 用於計算多個起點到多個終點的距離和時間矩陣
 *
 * 主要應用場景：
 * 1. 計算多個司機到乘客的距離和預估到達時間
 * 2. 智能司機匹配（找最近的司機）
 * 3. 批量距離查詢優化
 */
class DistanceMatrixApiService(private val context: Context) {

    companion object {
        private const val TAG = "DistanceMatrixService"
        private const val BASE_URL = "https://maps.googleapis.com/"

        // 從 AndroidManifest 讀取 API Key
        private fun getApiKey(context: Context): String {
            return try {
                val appInfo = context.packageManager.getApplicationInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_META_DATA
                )
                appInfo.metaData.getString("com.google.android.geo.API_KEY") ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get API key", e)
                ""
            }
        }
    }

    private val apiKey = getApiKey(context)

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(DistanceMatrixApi::class.java)

    /**
     * 計算單個起點到單個終點的距離和時間
     *
     * @param origin 起點座標
     * @param destination 終點座標
     * @return DistanceMatrixElement 包含距離和時間資訊
     */
    suspend fun getDistance(
        origin: LatLng,
        destination: LatLng
    ): Result<DistanceMatrixElement> = withContext(Dispatchers.IO) {
        try {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destStr = "${destination.latitude},${destination.longitude}"

            Log.d(TAG, "Calculating distance from $originStr to $destStr")

            val response = api.getDistanceMatrix(
                origins = originStr,
                destinations = destStr,
                key = apiKey,
                language = "zh-TW",
                region = "TW"
            )

            if (response.status != "OK") {
                Log.e(TAG, "Distance Matrix API error: ${response.status}, ${response.errorMessage}")
                return@withContext Result.failure(
                    Exception("距離計算失敗: ${response.errorMessage ?: response.status}")
                )
            }

            if (response.rows.isEmpty() || response.rows[0].elements.isEmpty()) {
                Log.e(TAG, "No distance data returned")
                return@withContext Result.failure(Exception("找不到距離資料"))
            }

            val element = response.rows[0].elements[0]

            if (element.status != "OK") {
                Log.e(TAG, "Element status error: ${element.status}")
                return@withContext Result.failure(Exception("無法計算距離"))
            }

            val result = DistanceMatrixElement(
                distanceMeters = element.distance.value,
                distanceText = element.distance.text,
                durationSeconds = element.duration.value,
                durationText = element.duration.text
            )

            Log.d(TAG, "Distance calculation success: ${result.distanceText}, ${result.durationText}")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate distance", e)
            Result.failure(e)
        }
    }

    /**
     * 計算多個起點到多個終點的距離矩陣
     *
     * @param origins 起點列表
     * @param destinations 終點列表
     * @return 距離矩陣 List<List<DistanceMatrixElement>>
     *         外層 List 對應 origins，內層 List 對應 destinations
     */
    suspend fun getDistanceMatrix(
        origins: List<LatLng>,
        destinations: List<LatLng>
    ): Result<List<List<DistanceMatrixElement>>> = withContext(Dispatchers.IO) {
        try {
            // 將座標列表轉換為 API 需要的格式
            val originsStr = origins.joinToString("|") { "${it.latitude},${it.longitude}" }
            val destinationsStr = destinations.joinToString("|") { "${it.latitude},${it.longitude}" }

            Log.d(TAG, "Calculating distance matrix: ${origins.size} origins x ${destinations.size} destinations")

            val response = api.getDistanceMatrix(
                origins = originsStr,
                destinations = destinationsStr,
                key = apiKey,
                language = "zh-TW",
                region = "TW"
            )

            if (response.status != "OK") {
                Log.e(TAG, "Distance Matrix API error: ${response.status}")
                return@withContext Result.failure(Exception("距離矩陣計算失敗"))
            }

            // 解析結果矩陣
            val matrix = response.rows.map { row ->
                row.elements.map { element ->
                    if (element.status == "OK") {
                        DistanceMatrixElement(
                            distanceMeters = element.distance.value,
                            distanceText = element.distance.text,
                            durationSeconds = element.duration.value,
                            durationText = element.duration.text
                        )
                    } else {
                        // 無法計算的情況（例如無法到達）
                        DistanceMatrixElement(
                            distanceMeters = Int.MAX_VALUE,
                            distanceText = "無法到達",
                            durationSeconds = Int.MAX_VALUE,
                            durationText = "無法到達"
                        )
                    }
                }
            }

            Log.d(TAG, "Distance matrix calculation success")
            Result.success(matrix)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate distance matrix", e)
            Result.failure(e)
        }
    }

    /**
     * 計算多個司機到單個乘客位置的距離和時間
     * （乘客端用於顯示附近司機的 ETA）
     *
     * @param driverLocations 司機位置列表
     * @param passengerLocation 乘客位置
     * @return Map<索引, DistanceMatrixElement>
     */
    suspend fun calculateDriverETAs(
        driverLocations: List<LatLng>,
        passengerLocation: LatLng
    ): Result<Map<Int, DistanceMatrixElement>> = withContext(Dispatchers.IO) {
        try {
            if (driverLocations.isEmpty()) {
                return@withContext Result.success(emptyMap())
            }

            val result = getDistanceMatrix(
                origins = driverLocations,
                destinations = listOf(passengerLocation)
            )

            result.onSuccess { matrix ->
                // 將矩陣轉換為 Map（司機索引 -> 距離資訊）
                val etaMap = matrix.mapIndexed { index, row ->
                    index to row[0]
                }.toMap()

                return@withContext Result.success(etaMap)
            }

            result.onFailure { error ->
                return@withContext Result.failure(error)
            }

            Result.failure(Exception("未知錯誤"))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate driver ETAs", e)
            Result.failure(e)
        }
    }

    /**
     * 找出最近的司機
     *
     * @param driverLocations 司機位置列表
     * @param passengerLocation 乘客位置
     * @return Pair<司機索引, DistanceMatrixElement> 最近的司機及其距離資訊
     */
    suspend fun findNearestDriver(
        driverLocations: List<LatLng>,
        passengerLocation: LatLng
    ): Result<Pair<Int, DistanceMatrixElement>> = withContext(Dispatchers.IO) {
        try {
            val etasResult = calculateDriverETAs(driverLocations, passengerLocation)

            etasResult.onSuccess { etaMap ->
                if (etaMap.isEmpty()) {
                    return@withContext Result.failure(Exception("沒有可用的司機"))
                }

                // 找出距離最短的司機
                val nearest = etaMap.minByOrNull { it.value.durationSeconds }

                if (nearest == null) {
                    return@withContext Result.failure(Exception("找不到最近的司機"))
                }

                Log.d(TAG, "Nearest driver: index ${nearest.key}, ETA ${nearest.value.durationText}")
                return@withContext Result.success(nearest.toPair())
            }

            etasResult.onFailure { error ->
                return@withContext Result.failure(error)
            }

            Result.failure(Exception("未知錯誤"))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to find nearest driver", e)
            Result.failure(e)
        }
    }
}

/**
 * Distance Matrix API Retrofit 介面
 */
private interface DistanceMatrixApi {
    @GET("maps/api/distancematrix/json")
    suspend fun getDistanceMatrix(
        @Query("origins") origins: String,
        @Query("destinations") destinations: String,
        @Query("key") key: String,
        @Query("language") language: String = "zh-TW",
        @Query("region") region: String = "TW",
        @Query("mode") mode: String = "driving"
    ): DistanceMatrixResponse
}

/**
 * 距離矩陣元素（單個起點到終點的資訊）
 */
data class DistanceMatrixElement(
    val distanceMeters: Int,        // 距離（公尺）
    val distanceText: String,       // 距離文字（例如：5.2 公里）
    val durationSeconds: Int,       // 時間（秒）
    val durationText: String        // 時間文字（例如：12 分鐘）
)

// ===== Distance Matrix API 回應資料類（用於 Gson 解析）=====

private data class DistanceMatrixResponse(
    @SerializedName("rows") val rows: List<RowResponse>,
    @SerializedName("status") val status: String,
    @SerializedName("error_message") val errorMessage: String?
)

private data class RowResponse(
    @SerializedName("elements") val elements: List<ElementResponse>
)

private data class ElementResponse(
    @SerializedName("status") val status: String,
    @SerializedName("distance") val distance: TextValueResponse,
    @SerializedName("duration") val duration: TextValueResponse
)
