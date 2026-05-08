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
    val stats: DriverStats? = null,

    /**
     * 班次設定（admin 後台設）。為空陣列視為 24/7 在班（向後相容）。
     * dispatcher 派單時會檢查此欄位 — 不在班次時間的司機不會收到 order:offer。
     */
    @SerializedName("shifts")
    val shifts: List<ShiftSlot> = emptyList(),
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
