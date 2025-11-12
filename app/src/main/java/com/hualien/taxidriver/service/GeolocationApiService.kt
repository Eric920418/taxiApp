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
    }

    private val apiKey = getApiKey(context)

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GeolocationApi::class.java)

    /**
     * 使用網路信號（WiFi + 基站）進行定位
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

            val response = api.geolocate(apiKey, request)

            if (response.location != null) {
                val result = GeolocationResult(
                    location = LatLng(response.location.lat, response.location.lng),
                    accuracy = response.accuracy ?: 0.0,
                    source = determineSource(wifiAccessPoints, cellTowers)
                )

                Log.d(TAG, "Geolocation success: ${result.location}, accuracy: ${result.accuracy}m, source: ${result.source}")
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
