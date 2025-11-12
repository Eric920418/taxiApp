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
        private const val GPS_TIMEOUT_MS = 10000L  // GPS 超時時間 10 秒
        private const val GPS_ACCURACY_THRESHOLD = 50.0  // GPS 精度閾值（公尺）
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

    /**
     * 開始混合定位
     */
    suspend fun startLocationUpdates() = withContext(Dispatchers.Main) {
        try {
            _locationState.value = LocationState.Loading

            // 檢查權限
            if (!hasLocationPermission()) {
                _locationState.value = LocationState.Error("缺少定位權限")
                return@withContext
            }

            // 策略 1: 先用 Geolocation API 快速獲取初始位置
            Log.d(TAG, "Step 1: Getting initial location from Geolocation API...")
            scope.launch {
                getGeolocationQuickly()
            }

            // 策略 2: 同時啟動 GPS 定位
            Log.d(TAG, "Step 2: Starting GPS location updates...")
            startGpsLocationUpdates()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
            _locationState.value = LocationState.Error("定位失敗: ${e.message}")
        }
    }

    /**
     * 使用 Geolocation API 快速獲取初始位置
     */
    private suspend fun getGeolocationQuickly() {
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
                    Log.d(TAG, "Geolocation API success: ${geolocation.location}, accuracy: ${geolocation.accuracy}m")
                }
            }.onFailure { error ->
                Log.w(TAG, "Geolocation API failed", error)
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

            // 建立位置請求
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L  // 每 5 秒更新一次
            ).apply {
                setMinUpdateIntervalMillis(2000L)  // 最快 2 秒更新
                setMaxUpdateDelayMillis(10000L)    // 最多延遲 10 秒
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
                        Log.w(TAG, "GPS location unavailable, falling back to Geolocation API")
                        // GPS 不可用，使用 Geolocation API 作為備援
                        scope.launch {
                            getGeolocationQuickly()
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

            // 設置 GPS 超時
            scope.launch {
                delay(GPS_TIMEOUT_MS)
                val currentState = _locationState.value
                if (currentState is LocationState.Loading || currentState is LocationState.Idle) {
                    Log.w(TAG, "GPS timeout, using Geolocation API")
                    getGeolocationQuickly()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GPS location updates", e)
        }
    }

    /**
     * 處理 GPS 位置
     */
    private fun handleGpsLocation(location: Location) {
        val accuracy = location.accuracy.toDouble()
        val latLng = LatLng(location.latitude, location.longitude)

        // 檢查 GPS 精度
        if (accuracy > GPS_ACCURACY_THRESHOLD) {
            Log.w(TAG, "GPS accuracy poor: ${accuracy}m, falling back to Geolocation API")
            scope.launch {
                getGeolocationQuickly()
            }
            return
        }

        // GPS 定位成功且精度足夠
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
        getGeolocationQuickly()
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
