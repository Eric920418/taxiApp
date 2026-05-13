package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 排班區（圓形範圍）
 */
data class QueueZone(
    @SerializedName("zone_id")
    val zoneId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("center_lat")
    val centerLat: Double,

    @SerializedName("center_lng")
    val centerLng: Double,

    @SerializedName("radius_meters")
    val radiusMeters: Int,

    /** 該 zone 當前 ACTIVE 排班司機數 */
    @SerializedName("active_drivers")
    val activeDrivers: Int = 0,
)

/**
 * 司機自己的排班狀態
 */
data class QueueMyStatus(
    @SerializedName("in_queue")
    val inQueue: Boolean,

    @SerializedName("entry_id")
    val entryId: Long? = null,

    @SerializedName("zone_id")
    val zoneId: String? = null,

    @SerializedName("zone_name")
    val zoneName: String? = null,

    @SerializedName("joined_at")
    val joinedAt: String? = null,

    @SerializedName("minutes_in_queue")
    val minutesInQueue: Int? = null,

    @SerializedName("max_acceptable_discount_amount")
    val maxAcceptableDiscountAmount: Int? = null,

    /** 在該排班區的順位（1-based，依 joined_at FIFO） */
    @SerializedName("position")
    val position: Int? = null,
)
