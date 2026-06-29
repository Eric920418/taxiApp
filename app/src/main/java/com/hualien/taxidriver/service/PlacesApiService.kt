package com.hualien.taxidriver.service

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Places API (New) 服務類
 * 提供地址自動完成搜尋和地點詳細資訊功能
 */
class PlacesApiService(context: Context) {

    // ⚠️ nullable：Places 初始化 / createClient 在金鑰缺失或無效時會丟例外。
    // 舊版直接在 init 丟 → 在 Compose 主執行緒炸掉（司機「輸入住址閃退」根因）。
    // 改成失敗時為 null，呼叫端一律走 Result.failure 並降級到在地地標 DB。
    private val placesClient: PlacesClient?

    /** Places 線上服務是否可用（金鑰有效且 client 建立成功）。false → 呼叫端降級到在地地標。 */
    val isAvailable: Boolean get() = placesClient != null

    companion object {
        private const val TAG = "PlacesApiService"

        // 花蓮縣的地理範圍（用於限制搜尋結果）
        private val HUALIEN_BOUNDS = RectangularBounds.newInstance(
            LatLng(23.5, 121.3),  // 西南角
            LatLng(24.5, 121.9)   // 東北角
        )
    }

    init {
        // 任何初始化失敗都讓 placesClient = null（降級），絕不把例外丟進 Compose/main → 閃退
        placesClient = try {
            val apiKey = getApiKey(context)
            if (apiKey.isBlank()) {
                // 金鑰缺失：完全不初始化，避免之後發出註定失敗的 async Task
                //（Places SDK 的 Task 例外在 main looper 無從捕捉 → 直接閃退）
                Log.e(TAG, "Places API 金鑰缺失（manifest com.google.android.geo.API_KEY 為空），停用線上地址搜尋，降級到在地地標")
                null
            } else {
                if (!Places.isInitialized()) {
                    Places.initialize(context.applicationContext, apiKey)
                }
                Places.createClient(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Places 初始化失敗，停用線上地址搜尋，降級到在地地標", e)
            null
        }
    }

    /**
     * 從 AndroidManifest.xml 讀取 API Key（任何讀取失敗都回空字串，不丟例外）
     */
    private fun getApiKey(context: Context): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "讀取 Places API 金鑰失敗", e)
            ""
        }
    }

    /**
     * 地址自動完成搜尋
     *
     * @param query 使用者輸入的搜尋字串
     * @param bias 搜尋偏好位置（可選，用於優先顯示附近結果）
     * @return 搜尋建議列表
     */
    suspend fun searchPlaces(
        query: String,
        bias: LatLng? = null
    ): Result<List<PlacePrediction>> = suspendCancellableCoroutine { continuation ->
        try {
            if (query.isBlank()) {
                continuation.resume(Result.success(emptyList()))
                return@suspendCancellableCoroutine
            }

            // 服務未啟用（金鑰缺失/初始化失敗）→ 直接 failure，呼叫端降級到在地地標
            val client = placesClient ?: run {
                continuation.resume(Result.failure(IllegalStateException("Places 服務未啟用（金鑰缺失或初始化失敗），改用在地地標")))
                return@suspendCancellableCoroutine
            }

            // 每次搜尋都建立新的 session token，避免連續不同搜尋被 Google 合併
            val currentToken = AutocompleteSessionToken.newInstance()

            val requestBuilder = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(currentToken)
                .setQuery(query)
                .setCountries(listOf("TW"))  // 限制在台灣
                .setLocationRestriction(HUALIEN_BOUNDS)  // 嚴格限制在花蓮範圍（不只是偏好）

            // 如果有提供偏好位置，優先顯示附近結果
            bias?.let {
                requestBuilder.setOrigin(it)
            }

            val request = requestBuilder.build()

            client.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    val predictions = response.autocompletePredictions.map { prediction ->
                        PlacePrediction(
                            placeId = prediction.placeId,
                            primaryText = prediction.getPrimaryText(null).toString(),
                            secondaryText = prediction.getSecondaryText(null)?.toString() ?: "",
                            fullText = prediction.getFullText(null).toString()
                        )
                    }
                    Log.d(TAG, "找到 ${predictions.size} 個搜尋結果")
                    continuation.resume(Result.success(predictions))
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "地址搜尋失敗", exception)
                    continuation.resumeWithException(exception)
                }

        } catch (e: Exception) {
            Log.e(TAG, "搜尋發生錯誤", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * 根據 Place ID 獲取地點詳細資訊
     *
     * @param placeId Google Places API 的 Place ID
     * @return 地點詳細資訊
     */
    suspend fun getPlaceDetails(placeId: String): Result<PlaceDetails> =
        suspendCancellableCoroutine { continuation ->
            try {
                val client = placesClient ?: run {
                    continuation.resume(Result.failure(IllegalStateException("Places 服務未啟用（金鑰缺失或初始化失敗）")))
                    return@suspendCancellableCoroutine
                }

                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.ADDRESS,
                    Place.Field.LAT_LNG,
                    Place.Field.PHONE_NUMBER,
                    Place.Field.TYPES,
                    Place.Field.OPENING_HOURS
                )

                val request = FetchPlaceRequest.builder(placeId, placeFields).build()

                client.fetchPlace(request)
                    .addOnSuccessListener { response ->
                        val place = response.place
                        val details = PlaceDetails(
                            placeId = place.id ?: "",
                            name = place.name ?: "",
                            address = place.address ?: "",
                            latLng = place.latLng,
                            phoneNumber = place.phoneNumber,
                            types = place.placeTypes?.map { it.toString() } ?: emptyList()
                        )
                        Log.d(TAG, "獲取地點詳情成功: ${details.name}")
                        continuation.resume(Result.success(details))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "獲取地點詳情失敗", exception)
                        continuation.resumeWithException(exception)
                    }

            } catch (e: Exception) {
                Log.e(TAG, "獲取地點詳情發生錯誤", e)
                continuation.resumeWithException(e)
            }
        }
}

/**
 * 地點搜尋建議
 */
data class PlacePrediction(
    val placeId: String,
    val primaryText: String,      // 主要文字（例如：花蓮火車站）
    val secondaryText: String,     // 次要文字（例如：花蓮市國聯一路）
    val fullText: String           // 完整文字
)

/**
 * 地點詳細資訊
 */
data class PlaceDetails(
    val placeId: String,
    val name: String,
    val address: String,
    val latLng: LatLng?,
    val phoneNumber: String?,
    val types: List<String>
)
