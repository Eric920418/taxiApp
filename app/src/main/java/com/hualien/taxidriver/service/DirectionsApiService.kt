package com.hualien.taxidriver.service

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.annotations.SerializedName
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Directions API 服務
 * 用於計算兩點之間的路線、距離、時間和導航指引
 */
class DirectionsApiService(private val context: Context) {

    companion object {
        private const val TAG = "DirectionsApiService"
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

    private val api = retrofit.create(DirectionsApi::class.java)

    /**
     * 計算兩點之間的路線
     *
     * @param origin 起點座標
     * @param destination 終點座標
     * @param alternatives 是否返回多條替代路線（預設 false）
     * @return DirectionsResult 包含路線資訊
     */
    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        alternatives: Boolean = false
    ): Result<DirectionsResult> = withContext(Dispatchers.IO) {
        try {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destStr = "${destination.latitude},${destination.longitude}"

            Log.d(TAG, "Requesting directions from $originStr to $destStr")

            val response = api.getDirections(
                origin = originStr,
                destination = destStr,
                key = apiKey,
                language = "zh-TW",
                region = "TW",
                alternatives = alternatives
            )

            if (response.status != "OK") {
                Log.e(TAG, "Directions API error: ${response.status}, ${response.errorMessage}")
                return@withContext Result.failure(
                    Exception("路線計算失敗: ${response.errorMessage ?: response.status}")
                )
            }

            if (response.routes.isEmpty()) {
                Log.e(TAG, "No routes found")
                return@withContext Result.failure(Exception("找不到路線"))
            }

            // 解析第一條路線
            val route = response.routes[0]
            val leg = route.legs[0]

            // 解碼 polyline
            val polylinePoints = try {
                PolyUtil.decode(route.overviewPolyline.points)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode polyline", e)
                emptyList()
            }

            val result = DirectionsResult(
                distanceMeters = leg.distance.value,
                distanceText = leg.distance.text,
                durationSeconds = leg.duration.value,
                durationText = leg.duration.text,
                startAddress = leg.startAddress,
                endAddress = leg.endAddress,
                polylinePoints = polylinePoints,
                steps = leg.steps.map { step ->
                    NavigationStep(
                        distanceMeters = step.distance.value,
                        distanceText = step.distance.text,
                        durationSeconds = step.duration.value,
                        durationText = step.duration.text,
                        instruction = step.htmlInstructions.stripHtml(),
                        startLocation = LatLng(
                            step.startLocation.lat,
                            step.startLocation.lng
                        ),
                        endLocation = LatLng(
                            step.endLocation.lat,
                            step.endLocation.lng
                        ),
                        polyline = try {
                            PolyUtil.decode(step.polyline.points)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    )
                }
            )

            Log.d(TAG, "Directions success: ${result.distanceText}, ${result.durationText}")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get directions", e)
            Result.failure(e)
        }
    }

    /**
     * 移除 HTML 標籤
     */
    private fun String.stripHtml(): String {
        return this
            .replace("<b>", "")
            .replace("</b>", "")
            .replace("<div.*?>".toRegex(), "\n")
            .replace("</div>", "")
            .replace("<.*?>".toRegex(), "")
            .trim()
    }
}

/**
 * Directions API Retrofit 介面
 */
private interface DirectionsApi {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") key: String,
        @Query("language") language: String = "zh-TW",
        @Query("region") region: String = "TW",
        @Query("alternatives") alternatives: Boolean = false,
        @Query("mode") mode: String = "driving"
    ): DirectionsResponse
}

/**
 * 路線計算結果
 */
data class DirectionsResult(
    val distanceMeters: Int,           // 距離（公尺）
    val distanceText: String,          // 距離文字（例如：5.2 公里）
    val durationSeconds: Int,          // 時間（秒）
    val durationText: String,          // 時間文字（例如：12 分鐘）
    val startAddress: String,          // 起點地址
    val endAddress: String,            // 終點地址
    val polylinePoints: List<LatLng>,  // 路線座標點（用於在地圖上繪製）
    val steps: List<NavigationStep>    // 導航步驟
)

/**
 * 單個導航步驟
 */
data class NavigationStep(
    val distanceMeters: Int,
    val distanceText: String,
    val durationSeconds: Int,
    val durationText: String,
    val instruction: String,           // 導航指示（例如：「向北行駛 100 公尺」）
    val startLocation: LatLng,
    val endLocation: LatLng,
    val polyline: List<LatLng>
)

// ===== Directions API 回應資料類（用於 Gson 解析）=====

private data class DirectionsResponse(
    @SerializedName("routes") val routes: List<RouteResponse>,
    @SerializedName("status") val status: String,
    @SerializedName("error_message") val errorMessage: String?
)

private data class RouteResponse(
    @SerializedName("legs") val legs: List<LegResponse>,
    @SerializedName("overview_polyline") val overviewPolyline: PolylineResponse
)

private data class LegResponse(
    @SerializedName("distance") val distance: TextValueResponse,
    @SerializedName("duration") val duration: TextValueResponse,
    @SerializedName("start_address") val startAddress: String,
    @SerializedName("end_address") val endAddress: String,
    @SerializedName("steps") val steps: List<StepResponse>
)

private data class StepResponse(
    @SerializedName("distance") val distance: TextValueResponse,
    @SerializedName("duration") val duration: TextValueResponse,
    @SerializedName("html_instructions") val htmlInstructions: String,
    @SerializedName("polyline") val polyline: PolylineResponse,
    @SerializedName("start_location") val startLocation: LocationResponse,
    @SerializedName("end_location") val endLocation: LocationResponse
)

private data class PolylineResponse(
    @SerializedName("points") val points: String
)
