package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日收入資料
 */
data class DailyEarning(
    @SerializedName("date")
    val date: String, // yyyyMMdd

    @SerializedName("totalAmount")
    val totalAmount: Int = 0,

    @SerializedName("trips")
    val trips: Int = 0,

    @SerializedName("distanceKm")
    val distanceKm: Double = 0.0,

    @SerializedName("minutes")
    val minutes: Int = 0,

    @SerializedName("orders")
    val orders: List<String> = emptyList()
) {
    /**
     * 格式化日期顯示
     */
    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MM/dd (E)", Locale.TRADITIONAL_CHINESE)
            val parsedDate = inputFormat.parse(date)
            parsedDate?.let { outputFormat.format(it) } ?: date
        } catch (e: Exception) {
            date
        }
    }

    /**
     * 格式化金額
     */
    fun getFormattedAmount(): String = "NT$ $totalAmount"

    /**
     * 平均每趟收入
     */
    fun getAveragePerTrip(): Int {
        return if (trips > 0) totalAmount / trips else 0
    }

    /**
     * 格式化距離
     */
    fun getFormattedDistance(): String = String.format("%.1f km", distanceKm)

    /**
     * 格式化時長
     */
    fun getFormattedDuration(): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return "${hours}h ${mins}m"
    }
}
