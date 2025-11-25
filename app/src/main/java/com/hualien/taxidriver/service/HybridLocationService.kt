package com.hualien.taxidriver.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * 混合定位服務
 * 結合 GPS (Fused Location Provider) 和 Geolocation API
 *
 * 定位策略：
 * 1. 啟動時先用 Geolocation API 快速取得初始位置
 * 2. 同時啟動 GPS 定位
 * 3. GPS 有結果後優先使用 GPS（精度高）
 * 4. GPS 信號弱或失敗時切換回 Geolocation API
 * 5. 室內或 GPS 不可用時自動使用 Geolocation API
 */
class HybridLocationService(private val context: Context) {

    companion object {
        private const val TAG = "HybridLocationService"
        private const val GPS_TIMEOUT_MS = 8000L  // GPS 超時時間 8 秒
        private const val GPS_ACCURACY_THRESHOLD = 50.0  // GPS 精度閾值（公尺）
        private const val GEOLOCATION_COOLDOWN_MS = 30000L  // Geolocation API 冷卻時間 30 秒
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geolocationService = GeolocationApiService(context)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 當前位置狀態
    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    // GPS 位置回調
    private var locationCallback: LocationCallback? = null

    // Geolocation API 節流控制
    private var lastGeolocationCallTime = 0L
    private var isGpsWorking = false

    /**
     * 開始混合定位
     * 優化版：使用優先級策略減少 API 調用
     */
    suspend fun startLocationUpdates() = withContext(Dispatchers.Main) {
        try {
            _locationState.value = LocationState.Loading

            // 檢查權限
            if (!hasLocationPermission()) {
                _locationState.value = LocationState.Error("缺少定位權限")
                return@withContext
            }

            // 優化策略：優先使用 GPS，失敗才用 Geolocation API
            Log.d(TAG, "Starting optimized hybrid location...")

            // Step 1: 先嘗試 GPS
            startGpsLocationUpdates()

            // Step 2: 設定超時，如果 GPS 在 8 秒內沒有結果，才調用 Geolocation API
            scope.launch {
                delay(GPS_TIMEOUT_MS)
                if (!isGpsWorking && _locationState.value !is LocationState.Success) {
                    Log.d(TAG, "GPS timeout, falling back to Geolocation API")
                    getGeolocationAsBackup()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
            _locationState.value = LocationState.Error("定位失敗: ${e.message}")
        }
    }

    /**
     * 使用 Geolocation API 作為備援
     * 優化：添加節流控制，避免頻繁調用
     */
    private suspend fun getGeolocationAsBackup() {
        // 檢查冷卻時間
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGeolocationCallTime < GEOLOCATION_COOLDOWN_MS) {
            Log.d(TAG, "Geolocation API in cooldown, skipping...")
            return
        }

        lastGeolocationCallTime = currentTime

        try {
            val result = geolocationService.getCurrentLocation()

            result.onSuccess { geolocation ->
                // 只在還沒有更好的位置時才更新
                val currentState = _locationState.value
                if (currentState !is LocationState.Success ||
                    currentState.source == LocationSource.IP ||
                    currentState.accuracy > geolocation.accuracy
                ) {
                    _locationState.value = LocationState.Success(
                        location = geolocation.location,
                        accuracy = geolocation.accuracy,
                        source = geolocation.source
                    )
                    Log.d(TAG, "Geolocation API backup success: ${geolocation.location}, accuracy: ${geolocation.accuracy}m")
                }
            }.onFailure { error ->
                Log.w(TAG, "Geolocation API backup failed", error)
                // 如果 GPS 也失敗，設置錯誤狀態
                if (!isGpsWorking) {
                    _locationState.value = LocationState.Error("所有定位方式都失敗")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geolocation API error", e)
        }
    }

    /**
     * 啟動 GPS 定位更新
     */
    @Suppress("MissingPermission")
    private fun startGpsLocationUpdates() {
        try {
            if (!hasLocationPermission()) {
                return
            }

            // 建立位置請求（優化：降低更新頻率）
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000L  // 每 10 秒更新一次
            ).apply {
                setMinUpdateIntervalMillis(5000L)  // 最快 5 秒更新
                setMaxUpdateDelayMillis(15000L)    // 最多延遲 15 秒
                setMinUpdateDistanceMeters(10f)    // 移動 10 米才更新
            }.build()

            // 建立位置回調
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return

                    // GPS 定位成功
                    handleGpsLocation(location)
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
                        Log.w(TAG, "GPS location unavailable")
                        isGpsWorking = false
                        // GPS 不可用，使用 Geolocation API 作為備援（有節流）
                        scope.launch {
                            getGeolocationAsBackup()
                        }
                    }
                }
            }

            // 開始接收位置更新
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            // 移除重複的 GPS 超時處理（已經在 startLocationUpdates 中處理）

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GPS location updates", e)
        }
    }

    /**
     * 處理 GPS 位置
     * 優化：減少 Geolocation API 調用
     */
    private fun handleGpsLocation(location: Location) {
        val accuracy = location.accuracy.toDouble()
        val latLng = LatLng(location.latitude, location.longitude)

        // GPS 工作中
        isGpsWorking = true

        // 檢查 GPS 精度
        if (accuracy > GPS_ACCURACY_THRESHOLD) {
            Log.w(TAG, "GPS accuracy poor: ${accuracy}m")

            // 只有在沒有任何位置時才使用 Geolocation API
            val currentState = _locationState.value
            if (currentState !is LocationState.Success) {
                scope.launch {
                    getGeolocationAsBackup()
                }
            }
            // 即使精度差，也更新 GPS 位置（總比沒有好）
        }

        // 更新 GPS 定位（不管精度如何）
        _locationState.value = LocationState.Success(
            location = latLng,
            accuracy = accuracy,
            source = LocationSource.GPS
        )

        Log.d(TAG, "GPS location updated: $latLng, accuracy: ${accuracy}m")
    }

    /**
     * 獲取最後已知位置（用於快速啟動）
     */
    suspend fun getLastKnownLocation(): LatLng? = withContext(Dispatchers.Main) {
        try {
            if (!hasLocationPermission()) {
                return@withContext null
            }

            @Suppress("MissingPermission")
            val location = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                null
            }

            if (location != null) {
                LatLng(location.latitude, location.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last known location", e)
            null
        }
    }

    /**
     * 強制使用 Geolocation API（例如在室內）
     */
    suspend fun forceGeolocation() {
        _locationState.value = LocationState.Loading
        // 強制調用時重置冷卻時間
        lastGeolocationCallTime = 0L
        getGeolocationAsBackup()
    }

    /**
     * 停止定位更新
     */
    fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
        scope.cancel()
        _locationState.value = LocationState.Idle
        Log.d(TAG, "Location updates stopped")
    }

    /**
     * 檢查定位權限
     */
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * 定位狀態
 */
sealed class LocationState {
    object Idle : LocationState()
    object Loading : LocationState()

    data class Success(
        val location: LatLng,
        val accuracy: Double,
        val source: LocationSource
    ) : LocationState()

    data class Error(val message: String) : LocationState()
}

// Task extension is already provided by kotlinx-coroutines-play-services dependency
