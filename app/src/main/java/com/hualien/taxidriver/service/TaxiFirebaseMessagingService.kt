package com.hualien.taxidriver.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hualien.taxidriver.MainActivity
import com.hualien.taxidriver.R
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.DeviceInfo
import com.hualien.taxidriver.data.remote.dto.UpdateFcmTokenRequest
import com.hualien.taxidriver.utils.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Firebase Cloud Messaging 服務
 *
 * 功能：
 * 1. 接收並處理推播通知
 * 2. 管理 FCM Token 更新
 * 3. 顯示不同類型的通知（新訂單、狀態更新等）
 */
class TaxiFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"

        // 通知頻道 ID
        const val CHANNEL_ID_ORDER = "order_notifications"
        const val CHANNEL_ID_STATUS = "status_notifications"
        const val CHANNEL_ID_GENERAL = "general_notifications"

        // 通知類型
        const val TYPE_NEW_ORDER = "new_order"
        const val TYPE_ORDER_CANCELLED = "order_cancelled"
        const val TYPE_ORDER_STATUS = "order_status"
        const val TYPE_SYSTEM = "system"

        // 通知 ID 計數器
        private var notificationId = 0
        fun getNextNotificationId(): Int = notificationId++
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * 當收到新的 FCM Token 時調用
     * 需要將 Token 發送到後端伺服器
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "========== FCM Token 更新 ==========")
        Log.d(TAG, "新 Token: $token")

        // 將 Token 發送到後端
        sendTokenToServer(token)
    }

    /**
     * 當收到推播訊息時調用
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "========== 收到推播訊息 ==========")
        Log.d(TAG, "From: ${remoteMessage.from}")

        // 處理資料訊息（data payload）
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // 處理通知訊息（notification payload）
        remoteMessage.notification?.let {
            Log.d(TAG, "Notification Title: ${it.title}")
            Log.d(TAG, "Notification Body: ${it.body}")
            showNotification(it.title ?: "", it.body ?: "", CHANNEL_ID_GENERAL)
        }
    }

    /**
     * 處理資料訊息
     */
    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: TYPE_SYSTEM
        val title = data["title"] ?: "GoGoCha"
        val body = data["body"] ?: ""
        val orderId = data["orderId"]

        Log.d(TAG, "訊息類型: $type")

        when (type) {
            TYPE_NEW_ORDER -> {
                // 新訂單通知 - 使用高優先級
                val passengerName = data["passengerName"] ?: "乘客"
                val pickup = data["pickup"] ?: "未知地點"
                showOrderNotification(
                    title = "新訂單！",
                    body = "$passengerName 在 $pickup 叫車",
                    orderId = orderId,
                    isHighPriority = true
                )
            }

            TYPE_ORDER_CANCELLED -> {
                // 訂單取消通知
                showOrderNotification(
                    title = "訂單已取消",
                    body = body.ifEmpty { "乘客已取消訂單" },
                    orderId = orderId,
                    isHighPriority = false
                )
            }

            TYPE_ORDER_STATUS -> {
                // 訂單狀態更新
                showNotification(
                    title = title,
                    body = body,
                    channelId = CHANNEL_ID_STATUS
                )
            }

            TYPE_SYSTEM -> {
                // 系統通知
                showNotification(
                    title = title,
                    body = body,
                    channelId = CHANNEL_ID_GENERAL
                )
            }

            else -> {
                // 默認處理
                showNotification(title, body, CHANNEL_ID_GENERAL)
            }
        }
    }

    /**
     * 顯示訂單相關通知
     */
    private fun showOrderNotification(
        title: String,
        body: String,
        orderId: String?,
        isHighPriority: Boolean
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            orderId?.let { putExtra("orderId", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_ORDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        // 高優先級通知（新訂單）使用 heads-up 顯示
        if (isHighPriority) {
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVibrate(longArrayOf(0, 500, 200, 500)) // 震動模式
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(getNextNotificationId(), notificationBuilder.build())

        Log.d(TAG, "✅ 訂單通知已顯示: $title")
    }

    /**
     * 顯示一般通知
     */
    private fun showNotification(title: String, body: String, channelId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(getNextNotificationId(), notificationBuilder.build())

        Log.d(TAG, "✅ 通知已顯示: $title")
    }

    /**
     * 創建通知頻道（Android 8.0+）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 訂單通知頻道 - 高優先級
            val orderChannel = NotificationChannel(
                CHANNEL_ID_ORDER,
                "訂單通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "新訂單和訂單狀態更新"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            // 狀態通知頻道 - 默認優先級
            val statusChannel = NotificationChannel(
                CHANNEL_ID_STATUS,
                "狀態通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "司機狀態和系統狀態更新"
            }

            // 一般通知頻道 - 低優先級
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "一般通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "促銷和其他一般訊息"
            }

            notificationManager.createNotificationChannels(
                listOf(orderChannel, statusChannel, generalChannel)
            )

            Log.d(TAG, "✅ 通知頻道已創建")
        }
    }

    /**
     * 將 FCM Token 發送到後端伺服器
     */
    private fun sendTokenToServer(token: String) {
        serviceScope.launch {
            try {
                // 先保存到本地
                val dataStore = DataStoreManager.getInstance(applicationContext)
                dataStore.saveFcmToken(token)

                // 取得當前登入的司機 ID
                val driverId = dataStore.getDriverId()

                if (driverId.isNullOrEmpty()) {
                    Log.d(TAG, "用戶尚未登入，FCM Token 已保存到本地，等待登入後同步")
                    return@launch
                }

                // 發送到後端
                val deviceInfo = DeviceInfo(
                    model = Build.MODEL,
                    os = "Android",
                    version = Build.VERSION.RELEASE
                )

                val request = UpdateFcmTokenRequest(
                    fcmToken = token,
                    deviceInfo = deviceInfo
                )

                val response = RetrofitClient.apiService.updateFcmToken(driverId, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "✅ FCM Token 已同步到伺服器")
                } else {
                    Log.e(TAG, "FCM Token 同步失敗: ${response.body()?.error}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "發送 FCM Token 失敗", e)
            }
        }
    }
}

/**
 * FCM Token 管理器
 * 提供獲取當前 Token 的工具方法
 */
object FcmTokenManager {
    private const val TAG = "FcmTokenManager"

    /**
     * 獲取當前 FCM Token
     */
    fun getCurrentToken(onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "FCM Token: $token")
                    onSuccess(token)
                } else {
                    Log.e(TAG, "獲取 FCM Token 失敗", task.exception)
                    task.exception?.let { onError(it) }
                }
            }
    }

    /**
     * 登入後同步 FCM Token 到伺服器
     * 應該在用戶成功登入後調用
     */
    suspend fun syncTokenAfterLogin(context: Context, driverId: String) {
        try {
            val dataStore = DataStoreManager.getInstance(context)

            // 先檢查是否有本地保存的 Token
            var fcmToken = dataStore.getFcmToken()

            // 如果沒有，則獲取新的
            if (fcmToken.isNullOrEmpty()) {
                fcmToken = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .token.await()
                dataStore.saveFcmToken(fcmToken)
            }

            // 發送到伺服器
            val deviceInfo = DeviceInfo(
                model = Build.MODEL,
                os = "Android",
                version = Build.VERSION.RELEASE
            )

            val request = UpdateFcmTokenRequest(
                fcmToken = fcmToken,
                deviceInfo = deviceInfo
            )

            val response = RetrofitClient.apiService.updateFcmToken(driverId, request)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✅ FCM Token 已同步到伺服器 (登入後)")
            } else {
                Log.e(TAG, "FCM Token 同步失敗: ${response.body()?.error}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "登入後同步 FCM Token 失敗", e)
        }
    }

    /**
     * 登出時清除伺服器上的 FCM Token
     */
    suspend fun clearTokenOnLogout(context: Context, driverId: String) {
        try {
            val response = RetrofitClient.apiService.deleteFcmToken(driverId)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✅ FCM Token 已從伺服器刪除")
            }

            // 同時清除本地
            DataStoreManager.getInstance(context).clearFcmToken()

        } catch (e: Exception) {
            Log.e(TAG, "刪除 FCM Token 失敗", e)
        }
    }

    /**
     * 訂閱主題（例如：所有司機）
     */
    fun subscribeToTopic(topic: String) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ 已訂閱主題: $topic")
                } else {
                    Log.e(TAG, "訂閱主題失敗: $topic", task.exception)
                }
            }
    }

    /**
     * 取消訂閱主題
     */
    fun unsubscribeFromTopic(topic: String) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .unsubscribeFromTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ 已取消訂閱主題: $topic")
                } else {
                    Log.e(TAG, "取消訂閱主題失敗: $topic", task.exception)
                }
            }
    }

}
