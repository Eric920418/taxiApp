package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 司機資料模型
 */
data class Driver(
    @SerializedName("driverId")
    val driverId: String,

    @SerializedName("phone")
    val phone: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("plate")
    val plate: String, // 車牌

    @SerializedName("availability")
    val availability: DriverAvailability = DriverAvailability.OFFLINE,

    @SerializedName("location")
    val location: Location? = null,

    @SerializedName("lastHeartbeat")
    val lastHeartbeat: Long = System.currentTimeMillis(),

    @SerializedName("stats")
    val stats: DriverStats? = null
)

/**
 * 司機統計資料
 */
data class DriverStats(
    @SerializedName("totalTrips")
    val totalTrips: Int = 0,

    @SerializedName("acceptanceRate")
    val acceptanceRate: Double = 0.0,

    @SerializedName("cancelRate")
    val cancelRate: Double = 0.0
) {
    /**
     * 格式化接單率
     */
    fun getFormattedAcceptanceRate(): String {
        return String.format("%.1f%%", acceptanceRate * 100)
    }

    /**
     * 格式化取消率
     */
    fun getFormattedCancelRate(): String {
        return String.format("%.1f%%", cancelRate * 100)
    }
}
