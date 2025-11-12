package com.hualien.taxidriver.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.hualien.taxidriver.R
import com.hualien.taxidriver.data.remote.WebSocketManager

/**
 * 定位前景服務
 * 持續回報司機位置
 */
class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val webSocketManager = WebSocketManager.getInstance()

    private var driverId: String? = null

    companion object {
        private const val CHANNEL_ID = "location_service_channel"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_DRIVER_ID = "driver_id"

        // 定位更新間隔（毫秒）
        private const val UPDATE_INTERVAL = 5000L // 5秒
        private const val FASTEST_UPDATE_INTERVAL = 3000L // 最快3秒
    }

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 定位回調
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // 透過WebSocket回報位置
                    driverId?.let { id ->
                        webSocketManager.updateLocation(
                            driverId = id,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            speed = location.speed,
                            bearing = location.bearing
                        )
                    }

                    android.util.Log.d(
                        "LocationService",
                        "位置更新: ${location.latitude}, ${location.longitude}"
                    )
                }
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        driverId = intent?.getStringExtra(EXTRA_DRIVER_ID)

        // 啟動前景服務
        startForeground(NOTIFICATION_ID, createNotification())

        // 開始定位更新
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        // 檢查權限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
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
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
