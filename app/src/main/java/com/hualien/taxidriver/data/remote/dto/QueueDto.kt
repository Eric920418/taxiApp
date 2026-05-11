package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.hualien.taxidriver.domain.model.QueueZone

/**
 * GET /api/queue/zones response
 */
data class QueueZonesResponse(
    @SerializedName("zones")
    val zones: List<QueueZone>,
)

/**
 * POST /api/queue/join request
 */
data class QueueJoinRequest(
    @SerializedName("driver_id")
    val driverId: String,

    @SerializedName("zone_id")
    val zoneId: String,

    @SerializedName("current_lat")
    val currentLat: Double,

    @SerializedName("current_lng")
    val currentLng: Double,

    @SerializedName("max_acceptable_discount_amount")
    val maxAcceptableDiscountAmount: Int? = 0,
)

data class QueueJoinResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("entry_id")
    val entryId: Long? = null,

    @SerializedName("joined_at")
    val joinedAt: String? = null,
)

/**
 * POST /api/queue/leave request
 */
data class QueueLeaveRequest(
    @SerializedName("driver_id")
    val driverId: String,

    @SerializedName("reason")
    val reason: String? = "MANUAL",
)

/**
 * PATCH /api/drivers/:id/discount request
 */
data class UpdateDiscountRequest(
    @SerializedName("maxAcceptableDiscountAmount")
    val maxAcceptableDiscountAmount: Int,
)

data class UpdateDiscountResponse(
    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("max_acceptable_discount_amount")
    val maxAcceptableDiscountAmount: Int = 0,
)
