package com.hualien.taxidriver.utils

import com.hualien.taxidriver.BuildConfig

/**
 * 全域常數
 */
object Constants {
    // Server連線設定
    const val BASE_URL = BuildConfig.SERVER_URL
    const val WS_URL = BuildConfig.WS_URL

    // 定位相關
    const val LOCATION_UPDATE_INTERVAL = 5000L // 5秒
    const val LOCATION_FASTEST_INTERVAL = 3000L // 3秒
    const val MIN_DISPLACEMENT = 10f // 10公尺

    // 權限請求碼
    const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    const val CAMERA_PERMISSION_REQUEST_CODE = 1002

    // 通知
    const val LOCATION_SERVICE_CHANNEL_ID = "location_service"
    const val LOCATION_SERVICE_NOTIFICATION_ID = 1001
    const val ORDER_NOTIFICATION_CHANNEL_ID = "order_notifications"

    // SharedPreferences Key
    const val PREF_DRIVER_ID = "driver_id"
    const val PREF_DRIVER_NAME = "driver_name"
    const val PREF_DRIVER_PHONE = "driver_phone"
    const val PREF_IS_LOGGED_IN = "is_logged_in"

    // API超時設定
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    // WebSocket重連
    const val WS_RECONNECT_DELAY = 5000L
    const val WS_MAX_RECONNECT_ATTEMPTS = 10

    // 花蓮預設座標
    const val DEFAULT_LATITUDE = 23.9871
    const val DEFAULT_LONGITUDE = 121.6015
    const val DEFAULT_ZOOM = 14f
}
