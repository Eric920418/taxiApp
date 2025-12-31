package com.hualien.taxidriver.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.model.LatLng
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Google Geolocation API 服務
 * 使用 WiFi 接入點和基站信息進行定位（不依賴 GPS）
 *
 * 應用場景：
 * 1. GPS 不可用時的備援定位
 * 2. 室內定位增強
 * 3. 快速初始定位
 */
class GeolocationApiService(private val context: Context) {

    companion object {
        private const val TAG = "GeolocationApiService"
        private const val BASE_URL = "https://www.googleapis.com/"

        // 用量監控常數
        private const val COST_WARNING_THRESHOLD = 800  // 80% 免費額度（每月 10,000 次）
        private const val COST_DANGER_THRESHOLD = 950   // 95% 免費額度
        private const val ESTIMATED_COST_PER_REQUEST = 0.005  // 美元（每次請求約 $0.005）

        // 從 AndroidManifest 讀取 API Key
        private fun getApiKey(context: Context): String {
            return try {
                val appInfo = context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA
                )
                appInfo.metaData.getString("com.google.android.geo.API_KEY") ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get API key", e)
                ""
            }
        }

        // 用量統計（使用 SharedPreferences 持久化）
        @Volatile
        private var totalCallCount = 0L
        private var dailyCallCount = 0
        private var lastResetDate = ""
    }

    private val apiKey = getApiKey(context)
    private val prefs = context.getSharedPreferences("geolocation_usage", Context.MODE_PRIVATE)

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GeolocationApi::class.java)

    init {
        // 載入用量統計
        loadUsageStats()
    }

    /**
     * 使用網路信號（WiFi + 基站）進行定位
     * 優化版：添加用量監控和成本追蹤
     *
     * @return GeolocationResult 包含位置和精度
     */
    suspend fun getCurrentLocation(): Result<GeolocationResult> = withContext(Dispatchers.IO) {
        try {
            // 收集 WiFi 和基站信息
            val wifiAccessPoints = collectWifiAccessPoints()
            val cellTowers = collectCellTowers()

            Log.d(TAG, "Collected ${wifiAccessPoints.size} WiFi APs and ${cellTowers.size} cell towers")

            if (wifiAccessPoints.isEmpty() && cellTowers.isEmpty()) {
                return@withContext Result.failure(
                    Exception("無法收集 WiFi 或基站信息，請檢查權限設定")
                )
            }

            // 發送請求到 Geolocation API
            val request = GeolocationRequest(
                considerIp = true,  // 也考慮 IP 地址
                wifiAccessPoints = wifiAccessPoints,
                cellTowers = cellTowers
            )

            // ===== 用量追蹤：API 調用前記錄 =====
            incrementUsageCount()

            val response = api.geolocate(apiKey, request)

            if (response.location != null) {
                val result = GeolocationResult(
                    location = LatLng(response.location.lat, response.location.lng),
                    accuracy = response.accuracy ?: 0.0,
                    source = determineSource(wifiAccessPoints, cellTowers)
                )

                Log.d(TAG, "Geolocation success: ${result.location}, accuracy: ${result.accuracy}m, source: ${result.source}")

                // ===== 用量追蹤：成功調用後記錄統計 =====
                logUsageStats()

                Result.success(result)
            } else {
                Log.e(TAG, "No location in response")
                Result.failure(Exception("無法取得位置"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Geolocation failed", e)
            Result.failure(e)
        }
    }

    /**
     * 載入用量統計
     */
    private fun loadUsageStats() {
        totalCallCount = prefs.getLong("total_call_count", 0)
        dailyCallCount = prefs.getInt("daily_call_count", 0)
        lastResetDate = prefs.getString("last_reset_date", getCurrentDate()) ?: getCurrentDate()

        // 檢查是否需要重置每日計數
        val currentDate = getCurrentDate()
        if (currentDate != lastResetDate) {
            Log.d(TAG, "📅 新的一天，重置每日計數（前一日: $lastResetDate, 調用次數: $dailyCallCount）")
            dailyCallCount = 0
            lastResetDate = currentDate
            saveUsageStats()
        }

        Log.d(TAG, "📊 Geolocation API 用量統計已載入：總計 $totalCallCount 次，今日 $dailyCallCount 次")
    }

    /**
     * 增加用量計數
     */
    private fun incrementUsageCount() {
        totalCallCount++
        dailyCallCount++
        saveUsageStats()
    }

    /**
     * 保存用量統計
     */
    private fun saveUsageStats() {
        prefs.edit().apply {
            putLong("total_call_count", totalCallCount)
            putInt("daily_call_count", dailyCallCount)
            putString("last_reset_date", lastResetDate)
            apply()
        }
    }

    /**
     * 記錄用量統計日誌（包含成本估算和警告）
     */
    private fun logUsageStats() {
        val estimatedMonthlyCalls = dailyCallCount * 30  // 估算月用量
        val estimatedMonthlyCost = estimatedMonthlyCalls * ESTIMATED_COST_PER_REQUEST

        Log.d(TAG, "========== Geolocation API 用量報告 ==========")
        Log.d(TAG, "📊 累計調用次數: $totalCallCount")
        Log.d(TAG, "📅 今日調用次數: $dailyCallCount")
        Log.d(TAG, "📈 估算月用量: $estimatedMonthlyCalls 次")
        Log.d(TAG, "💰 估算月成本: $${"%.2f".format(estimatedMonthlyCost)} USD")

        // 成本警告
        when {
            dailyCallCount * 30 >= COST_DANGER_THRESHOLD -> {
                Log.w(TAG, "🚨 警告：估算月用量已達 ${(dailyCallCount * 30 / 10.0).toInt()}% 免費額度！")
                Log.w(TAG, "🚨 建議：立即優化定位策略或增加冷卻時間")
            }
            dailyCallCount * 30 >= COST_WARNING_THRESHOLD -> {
                Log.w(TAG, "⚠️ 注意：估算月用量已達 ${(dailyCallCount * 30 / 10.0).toInt()}% 免費額度")
                Log.w(TAG, "⚠️ 建議：監控用量並考慮優化")
            }
            else -> {
                Log.d(TAG, "✅ 用量正常（${(dailyCallCount * 30 / 10.0).toInt()}% 免費額度）")
            }
        }
        Log.d(TAG, "==========================================")
    }

    /**
     * 獲取當前日期（YYYY-MM-DD）
     */
    private fun getCurrentDate(): String {
        val calendar = java.util.Calendar.getInstance()
        return "${calendar.get(java.util.Calendar.YEAR)}-" +
                "${(calendar.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')}-" +
                "${calendar.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}"
    }

    /**
     * 獲取用量統計（供外部查詢）
     */
    fun getUsageStats(): UsageStats {
        return UsageStats(
            totalCalls = totalCallCount,
            todayCalls = dailyCallCount,
            estimatedMonthlyCalls = dailyCallCount * 30,
            estimatedMonthlyCost = dailyCallCount * 30 * ESTIMATED_COST_PER_REQUEST
        )
    }

    /**
     * 收集 WiFi 接入點信息
     */
    @Suppress("DEPRECATION")
    private fun collectWifiAccessPoints(): List<WifiAccessPoint> {
        try {
            // 檢查權限
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "No location permission for WiFi scanning")
                return emptyList()
            }

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager == null || !wifiManager.isWifiEnabled) {
                Log.w(TAG, "WiFi is not available or disabled")
                return emptyList()
            }

            // 開始掃描（Android 9+ 有限制）
            wifiManager.startScan()

            // 獲取掃描結果
            val scanResults = wifiManager.scanResults ?: emptyList()

            return scanResults.mapNotNull { result ->
                try {
                    WifiAccessPoint(
                        macAddress = result.BSSID,
                        signalStrength = result.level,
                        signalToNoiseRatio = 0  // Android 不提供此資訊
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse WiFi result", e)
                    null
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect WiFi access points", e)
            return emptyList()
        }
    }

    /**
     * 收集基站信息
     */
    private fun collectCellTowers(): List<CellTower> {
        try {
            // 檢查權限
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "No location permission for cell tower info")
                return emptyList()
            }

            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager == null) {
                Log.w(TAG, "TelephonyManager not available")
                return emptyList()
            }

            val cellInfoList: List<CellInfo> = telephonyManager.allCellInfo ?: emptyList()

            return cellInfoList.mapNotNull { cellInfo ->
                try {
                    when (cellInfo) {
                        is CellInfoGsm -> {
                            val identity = cellInfo.cellIdentity
                            CellTower(
                                cellId = identity.cid,
                                locationAreaCode = identity.lac,
                                mobileCountryCode = identity.mcc,
                                mobileNetworkCode = identity.mnc
                            )
                        }
                        is CellInfoLte -> {
                            val identity = cellInfo.cellIdentity
                            CellTower(
                                cellId = identity.ci,
                                locationAreaCode = identity.tac,
                                mobileCountryCode = identity.mcc,
                                mobileNetworkCode = identity.mnc
                            )
                        }
                        is CellInfoWcdma -> {
                            val identity = cellInfo.cellIdentity
                            CellTower(
                                cellId = identity.cid,
                                locationAreaCode = identity.lac,
                                mobileCountryCode = identity.mcc,
                                mobileNetworkCode = identity.mnc
                            )
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse cell info", e)
                    null
                }
            }.filter { tower ->
                // 過濾無效的基站信息
                tower.cellId != Int.MAX_VALUE &&
                tower.locationAreaCode != Int.MAX_VALUE &&
                tower.mobileCountryCode != Int.MAX_VALUE
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect cell towers", e)
            return emptyList()
        }
    }

    /**
     * 判斷定位來源
     */
    private fun determineSource(wifiAccessPoints: List<WifiAccessPoint>, cellTowers: List<CellTower>): LocationSource {
        return when {
            wifiAccessPoints.isNotEmpty() && cellTowers.isNotEmpty() -> LocationSource.WIFI_AND_CELL
            wifiAccessPoints.isNotEmpty() -> LocationSource.WIFI
            cellTowers.isNotEmpty() -> LocationSource.CELL
            else -> LocationSource.IP
        }
    }
}

/**
 * Geolocation API Retrofit 介面
 */
private interface GeolocationApi {
    @POST("geolocation/v1/geolocate")
    suspend fun geolocate(
        @Query("key") key: String,
        @Body request: GeolocationRequest
    ): GeolocationResponse
}

/**
 * 定位結果
 */
data class GeolocationResult(
    val location: LatLng,       // 位置座標
    val accuracy: Double,       // 精度（公尺）
    val source: LocationSource  // 定位來源
)

/**
 * 定位來源
 */
enum class LocationSource {
    GPS,              // GPS 定位
    WIFI,             // WiFi 定位
    CELL,             // 基站定位
    WIFI_AND_CELL,    // WiFi + 基站
    IP,               // IP 地址定位（最不準確）
    HYBRID            // 混合定位
}

// ===== Geolocation API 請求/回應資料類 =====

private data class GeolocationRequest(
    @SerializedName("considerIp") val considerIp: Boolean = true,
    @SerializedName("wifiAccessPoints") val wifiAccessPoints: List<WifiAccessPoint> = emptyList(),
    @SerializedName("cellTowers") val cellTowers: List<CellTower> = emptyList()
)

private data class WifiAccessPoint(
    @SerializedName("macAddress") val macAddress: String,
    @SerializedName("signalStrength") val signalStrength: Int,
    @SerializedName("signalToNoiseRatio") val signalToNoiseRatio: Int = 0
)

private data class CellTower(
    @SerializedName("cellId") val cellId: Int,
    @SerializedName("locationAreaCode") val locationAreaCode: Int,
    @SerializedName("mobileCountryCode") val mobileCountryCode: Int,
    @SerializedName("mobileNetworkCode") val mobileNetworkCode: Int
)

private data class GeolocationResponse(
    @SerializedName("location") val location: LocationResponse?,
    @SerializedName("accuracy") val accuracy: Double?
)

// LocationResponse 已在 GoogleMapsApiModels.kt 中定義

/**
 * 用量統計資料
 */
data class UsageStats(
    val totalCalls: Long,           // 累計調用次數
    val todayCalls: Int,            // 今日調用次數
    val estimatedMonthlyCalls: Int, // 估算月用量
    val estimatedMonthlyCost: Double // 估算月成本（美元）
)
