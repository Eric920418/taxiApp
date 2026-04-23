package com.hualien.taxidriver.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.hualien.taxidriver.MainActivity
import com.hualien.taxidriver.R

/**
 * 乘客端通知輔助類
 *
 * 功能：
 * 1. 司機到達上車點通知（高優先級，震動 + 聲音）
 * 2. 訂單狀態變更通知
 * 3. 行程完成通知
 */
object PassengerNotificationHelper {

    private const val TAG = "PassengerNotification"

    // 通知頻道 ID
    const val CHANNEL_ID_ARRIVAL = "passenger_arrival_notifications"
    const val CHANNEL_ID_ORDER_STATUS = "passenger_order_status"

    // 通知 ID
    private const val NOTIFICATION_ID_ARRIVAL = 2001
    private const val NOTIFICATION_ID_ORDER_STATUS = 2002

    // 追蹤上次通知時間，防止短時間內重複通知
    private var lastArrivalNotificationTime = 0L
    private const val MIN_NOTIFICATION_INTERVAL = 30_000L // 30 秒

    /**
     * 初始化通知頻道（應在 Application 或 MainActivity 中調用）
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 司機到達通知頻道 - 最高優先級
            val arrivalChannel = NotificationChannel(
                CHANNEL_ID_ARRIVAL,
                "司機到達通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "當司機到達上車點時通知您"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500) // 連續三次震動

                // 設置聲音
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )

                enableLights(true)
                lightColor = android.graphics.Color.GREEN
            }

            // 訂單狀態通知頻道 - 默認優先級
            val statusChannel = NotificationChannel(
                CHANNEL_ID_ORDER_STATUS,
                "訂單狀態更新",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "訂單狀態變更通知"
            }

            notificationManager.createNotificationChannels(listOf(arrivalChannel, statusChannel))
            android.util.Log.d(TAG, "✅ 乘客通知頻道已創建")
        }
    }

    /**
     * 顯示「司機已到達」通知
     *
     * @param context Context
     * @param driverName 司機姓名
     * @param plateNumber 車牌號碼（可選）
     * @param orderId 訂單 ID
     */
    fun showDriverArrivedNotification(
        context: Context,
        driverName: String,
        plateNumber: String? = null,
        orderId: String? = null
    ) {
        // 防止短時間內重複通知
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastArrivalNotificationTime < MIN_NOTIFICATION_INTERVAL) {
            android.util.Log.d(TAG, "跳過重複的到達通知")
            return
        }
        lastArrivalNotificationTime = currentTime

        val title = "司機已到達！"
        val body = if (plateNumber != null) {
            "${driverName}（${plateNumber}）已到達上車點，請準備上車"
        } else {
            "${driverName}已到達上車點，請準備上車"
        }

        // 創建點擊後打開 App 的 Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            orderId?.let { putExtra("orderId", it) }
            putExtra("notificationType", "driver_arrived")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ARRIVAL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 建立通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ARRIVAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            // 全螢幕 Intent（用於 heads-up 通知）
            .setFullScreenIntent(pendingIntent, true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ARRIVAL, notification)

        // 額外震動（確保用戶注意到）
        triggerVibration(context)

        android.util.Log.d(TAG, "✅ 司機到達通知已顯示: $driverName")
    }

    /**
     * 顯示訂單狀態變更通知
     */
    fun showOrderStatusNotification(
        context: Context,
        title: String,
        body: String,
        orderId: String? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            orderId?.let { putExtra("orderId", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ORDER_STATUS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ORDER_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ORDER_STATUS, notification)

        android.util.Log.d(TAG, "✅ 訂單狀態通知已顯示: $title")
    }

    /**
     * 顯示行程完成通知
     */
    fun showTripCompletedNotification(
        context: Context,
        fareAmount: String? = null,
        orderId: String? = null
    ) {
        val title = "行程已完成"
        val body = if (fareAmount != null) {
            "本次車資：$fareAmount 元\n感謝您使用 GoGoCha！"
        } else {
            "感謝您使用 GoGoCha！"
        }

        showOrderStatusNotification(context, title, body, orderId)
    }

    /**
     * 取消所有乘客通知
     */
    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_ARRIVAL)
        notificationManager.cancel(NOTIFICATION_ID_ORDER_STATUS)
    }

    /**
     * 觸發震動
     */
    private fun triggerVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500, 200, 500),
                    -1 // 不重複
                ))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500, 200, 500),
                        -1
                    ))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "震動失敗", e)
        }
    }

    /**
     * 重置到達通知追蹤（用於新訂單時）
     */
    fun resetArrivalTracking() {
        lastArrivalNotificationTime = 0L
    }
}
