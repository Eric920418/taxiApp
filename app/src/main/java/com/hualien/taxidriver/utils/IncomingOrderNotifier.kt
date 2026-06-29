package com.hualien.taxidriver.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hualien.taxidriver.IncomingOrderActivity
import com.hualien.taxidriver.R

/**
 * 新訂單「全螢幕接單」通知 — FCM 背景通道 與 LocationService WS 背景備援 共用。
 *
 * 用 setFullScreenIntent 指向 IncomingOrderActivity：鎖屏/螢幕關時系統會拉起全螢幕、
 * 否則降級為 heads-up（仍可點開接單）。以 orderId 去重，避免 WS+FCM 同單彈兩次。
 *
 * 背景不可直接 startActivity（Android 10+ 限制），一律走 full-screen intent 由系統拉起。
 */
object IncomingOrderNotifier {
    private const val TAG = "IncomingOrderNotifier"
    const val CHANNEL_ID = "order_notifications"
    const val EXTRA_ORDER_ID = "orderId"
    const val EXTRA_PASSENGER_NAME = "passengerName"
    const val EXTRA_PICKUP = "pickup"

    private const val DEDUP_WINDOW_MS = 60_000L
    private val recentShown = HashMap<String, Long>()

    @Synchronized
    private fun shouldShow(orderId: String): Boolean {
        val now = System.currentTimeMillis()
        // 清掉過期紀錄，避免無限長大
        recentShown.entries.removeAll { now - it.value > DEDUP_WINDOW_MS }
        val last = recentShown[orderId]
        if (last != null && now - last < DEDUP_WINDOW_MS) return false
        recentShown[orderId] = now
        return true
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, "訂單通知", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "新訂單（全螢幕接單）"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun activityIntent(context: Context, orderId: String, passengerName: String?, pickup: String?): Intent {
        return Intent(context, IncomingOrderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ORDER_ID, orderId)
            passengerName?.let { putExtra(EXTRA_PASSENGER_NAME, it) }
            pickup?.let { putExtra(EXTRA_PICKUP, it) }
        }
    }

    /**
     * 顯示全螢幕接單通知。force=true 略過去重。
     */
    fun showIncomingOrder(
        context: Context,
        orderId: String,
        passengerName: String?,
        pickup: String?,
        force: Boolean = false,
    ) {
        if (orderId.isBlank()) return
        if (!force && !shouldShow(orderId)) {
            Log.d(TAG, "去重略過：$orderId")
            return
        }
        ensureChannel(context)

        val pi = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            activityIntent(context, orderId, passengerName, pickup),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val body = "${passengerName ?: "乘客"} 在 ${pickup ?: "附近"} 叫車"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("新訂單！")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true) // ★ 全螢幕（未授權自動降級 heads-up）
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(orderId.hashCode(), notification)
        Log.d(TAG, "已 post 全螢幕接單通知：$orderId")
    }

    fun cancel(context: Context, orderId: String) {
        if (orderId.isBlank()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(orderId.hashCode())
    }
}
