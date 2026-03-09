package com.hualien.taxidriver.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.hualien.taxidriver.R
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.domain.model.DriverAvailability
import com.hualien.taxidriver.utils.BatteryOptimizationManager
import com.hualien.taxidriver.utils.LocationConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * 定位前景服務
 * 優化版：動態調整定位頻率以節省電池
 *
 * 電池優化策略：
 * - 載客中：高頻率（5秒）
 * - 可接單：中頻率（15秒）
 * - 休息中：低頻率（60秒）
 * - 離線：停止定位
 */
class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private val webSocketManager = WebSocketManager.getInstance()

    private var driverId: String? = null
    private var currentOrderId: String? = null  // 當前進行中的訂單ID，用於軌跡追蹤
    private var lastReportedLocation: Location? = null
    private var lastReportTime: Long = 0
    private var currentConfig: LocationConfig? = null

    // Coroutine scope for config updates
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var configJob: Job? = null

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "location_service_channel"
        private const val NOTIFICATION_ID = 1

        const val EXTRA_DRIVER_ID = "driver_id"
        const val EXTRA_DRIVER_STATUS = "driver_status"
        const val EXTRA_ORDER_ID = "order_id"  // 訂單ID，用於軌跡追蹤

        // 位移過濾
        private const val MIN_DISPLACEMENT_METERS = 10f // 最小位移10米才更新
        private const val MIN_TIME_BETWEEN_UPDATES = 3000L // 最少3秒間隔
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "========== LocationService 創建 ==========")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        batteryOptimizationManager = BatteryOptimizationManager(this)

        // 定位回調（優化版：添加位移過濾和時間控制）
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val currentTime = System.currentTimeMillis()

                    // 檢查是否應該更新位置
                    if (shouldUpdateLocation(location, currentTime)) {
                        // 透過WebSocket回報位置（帶入訂單ID用於軌跡追蹤）
                        driverId?.let { id ->
                            webSocketManager.updateLocation(
                                driverId = id,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                speed = location.speed,
                                bearing = location.bearing,
                                orderId = currentOrderId  // 軌跡追蹤
                            )
                        }

                        // 更新最後報告的位置和時間
                        lastReportedLocation = location
                        lastReportTime = currentTime

                        android.util.Log.d(
                            TAG,
                            "位置更新: ${location.latitude}, ${location.longitude} (速度: ${location.speed}m/s)"
                        )
                    }
                }
            }
        }

        createNotificationChannel()

        // 開始監聽電池狀態
        batteryOptimizationManager.startMonitoring()

        // 監聯定位配置變化並動態調整
        configJob = serviceScope.launch {
            batteryOptimizationManager.locationConfig.collectLatest { config ->
                if (config != currentConfig) {
                    android.util.Log.d(TAG, "⚡ 定位配置變化: ${config.reason}")
                    android.util.Log.d(TAG, "   更新間隔: ${config.updateInterval}ms")
                    currentConfig = config
                    applyLocationConfig(config)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        driverId = intent?.getStringExtra(EXTRA_DRIVER_ID)
        val statusString = intent?.getStringExtra(EXTRA_DRIVER_STATUS)
        currentOrderId = intent?.getStringExtra(EXTRA_ORDER_ID)

        android.util.Log.d(TAG, "========== LocationService 啟動 ==========")
        android.util.Log.d(TAG, "司機ID: $driverId")
        android.util.Log.d(TAG, "司機狀態: $statusString")
        android.util.Log.d(TAG, "訂單ID: $currentOrderId")

        // 更新電池優化管理器的司機狀態
        val driverStatus = statusString?.let {
            try {
                DriverAvailability.valueOf(it)
            } catch (e: Exception) {
                DriverAvailability.AVAILABLE
            }
        } ?: DriverAvailability.AVAILABLE

        batteryOptimizationManager.updateDriverStatus(driverStatus)

        // 啟動前景服務
        startForeground(NOTIFICATION_ID, createNotification())

        // 開始定位更新
        startLocationUpdates()

        return START_STICKY
    }

    /**
     * 更新司機狀態（供外部調用）
     */
    fun updateDriverStatus(status: DriverAvailability) {
        android.util.Log.d(TAG, "更新司機狀態: $status")
        batteryOptimizationManager.updateDriverStatus(status)
    }

    /**
     * 更新當前訂單ID（供外部調用，用於軌跡追蹤）
     * @param orderId 訂單ID，傳 null 表示結束訂單
     */
    fun updateCurrentOrderId(orderId: String?) {
        android.util.Log.d(TAG, "更新訂單ID: $orderId")
        currentOrderId = orderId
    }

    private fun startLocationUpdates() {
        // 檢查權限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.e(TAG, "❌ 缺少定位權限，停止服務")
            stopSelf()
            return
        }

        val config = batteryOptimizationManager.getCurrentConfig()
        applyLocationConfig(config)
    }

    /**
     * 應用定位配置
     */
    private fun applyLocationConfig(config: LocationConfig) {
        // 如果不應該追蹤，停止定位更新
        if (!config.shouldTrack || config.updateInterval <= 0) {
            android.util.Log.d(TAG, "🛑 停止定位追蹤: ${config.reason}")
            fusedLocationClient.removeLocationUpdates(locationCallback)
            return
        }

        // 檢查權限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // 映射優先級
        val priority = when (config.priority) {
            com.hualien.taxidriver.utils.Priority.PRIORITY_HIGH_ACCURACY ->
                Priority.PRIORITY_HIGH_ACCURACY
            com.hualien.taxidriver.utils.Priority.PRIORITY_BALANCED_POWER_ACCURACY ->
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            com.hualien.taxidriver.utils.Priority.PRIORITY_LOW_POWER ->
                Priority.PRIORITY_LOW_POWER
            com.hualien.taxidriver.utils.Priority.PRIORITY_NO_POWER ->
                Priority.PRIORITY_PASSIVE
        }

        val locationRequest = LocationRequest.Builder(
            priority,
            config.updateInterval
        ).apply {
            setMinUpdateIntervalMillis(config.fastestInterval)
            setWaitForAccurateLocation(false)
            setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
        }.build()

        // 先移除舊的更新，再設置新的
        fusedLocationClient.removeLocationUpdates(locationCallback)
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        android.util.Log.d(TAG, "✅ 定位配置已應用: ${config.updateInterval}ms, ${config.priority}")
    }

    /**
     * 檢查是否應該更新位置
     * 基於位移距離和時間間隔過濾
     */
    private fun shouldUpdateLocation(newLocation: Location, currentTime: Long): Boolean {
        // 第一次更新總是發送
        if (lastReportedLocation == null || lastReportTime == 0L) {
            return true
        }

        // 檢查時間間隔
        val timeDelta = currentTime - lastReportTime
        if (timeDelta < MIN_TIME_BETWEEN_UPDATES) {
            return false
        }

        // 檢查位移距離
        val distance = newLocation.distanceTo(lastReportedLocation!!)
        val config = currentConfig

        // 如果移動超過閾值，或者時間超過最大間隔（避免長時間不更新）
        return distance >= MIN_DISPLACEMENT_METERS ||
                (config != null && timeDelta >= config.updateInterval)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "定位服務",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "持續回報司機位置"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("花蓮計程車 - 定位服務運行中")
            .setContentText("正在回報您的位置")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "========== LocationService 銷毀 ==========")

        // 停止定位更新
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // 停止電池監聽
        batteryOptimizationManager.stopMonitoring()

        // 取消 coroutine
        configJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
