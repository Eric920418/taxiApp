package com.hualien.taxidriver.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.domain.model.OrderStatus

/**
 * 導航 URI / Intent 共用邏輯 — 司機端各畫面共用，確保多點導航一致。
 *
 * 規則：
 *   - ON_TRIP（行程中）：導航到目的地；若有中途停靠點 → 用 Google Maps dir URL 多點路線；
 *     若目的地為 null → 退回導航到上車點。
 *   - 其它狀態（前往上車點）：導航到上車點。
 */
object NavigationUtils {

    /**
     * 依訂單狀態建立導航 URI。
     * @param forcePickup true 時強制導航到上車點（長輩版「導航到上車點」按鈕用）
     */
    fun buildNavigationUri(order: Order, forcePickup: Boolean = false): Uri {
        val dest = order.destination
        val waypoints = order.waypoints
            .filter { it.lat != null && it.lng != null }
            .sortedBy { it.sequence }

        val goToDestination = !forcePickup && order.status == OrderStatus.ON_TRIP && dest != null

        return if (goToDestination && dest != null) {
            if (waypoints.isNotEmpty()) {
                // 多點路線：上車點 → 各停靠 → 目的地
                val waypointStr = waypoints.joinToString("|") { "${it.lat},${it.lng}" }
                val url = "https://www.google.com/maps/dir/?api=1" +
                    "&origin=${order.pickup.latitude},${order.pickup.longitude}" +
                    "&destination=${dest.latitude},${dest.longitude}" +
                    "&waypoints=$waypointStr" +
                    "&travelmode=driving"
                Uri.parse(url)
            } else {
                Uri.parse("google.navigation:q=${dest.latitude},${dest.longitude}")
            }
        } else {
            // 前往上車點（或目的地未知）
            Uri.parse("google.navigation:q=${order.pickup.latitude},${order.pickup.longitude}")
        }
    }

    /**
     * 啟動 Google Maps 導航。多點 URL 用一般 ACTION_VIEW（不鎖 package），
     * 單點 google.navigation 維持鎖 Google Maps package。
     */
    fun startNavigation(context: Context, order: Order, forcePickup: Boolean = false) {
        val uri = buildNavigationUri(order, forcePickup)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        // 只有 google.navigation: scheme 鎖 Google Maps；https dir URL 讓系統挑（仍會優先 Maps）
        if (uri.scheme == "google.navigation" || uri.toString().startsWith("google.navigation")) {
            intent.setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 多點 URL 失敗時退回不鎖 package 再試一次
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: Exception) {
                Toast.makeText(context, "請先安裝 Google Maps", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
