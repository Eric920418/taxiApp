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

    /**
     * GoGoCha 折扣接受度：司機願意對客人讓利最高 NT$ N 元。
     * 影響 Queue 排班媒合：訂單 discount_amount ≤ 此值才會被派；高的（願意讓利多的）優先派。
     *
     * 兩種來源：
     *   - 車隊司機 (fleetPartnerId != null)：強制 = fleetDefaultDiscountAmount，自己不能改
     *   - 外部司機：自己選 5 段 chip
     */
    @SerializedName("maxAcceptableDiscountAmount")
    val maxAcceptableDiscountAmount: Int = 0,

    /** 司機 PRIMARY_FLEET partner id；null = 外部司機（無車隊綁定） */
    @SerializedName("fleetPartnerId")
    val fleetPartnerId: String? = null,

    /** 車隊顯示名稱（給 App UI 用），如「花蓮大豐車隊」 */
    @SerializedName("fleetPartnerName")
    val fleetPartnerName: String? = null,

    /** 車隊統一折扣金額；null = 此司機未綁車隊 */
    @SerializedName("fleetDefaultDiscountAmount")
    val fleetDefaultDiscountAmount: Int? = null,
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
