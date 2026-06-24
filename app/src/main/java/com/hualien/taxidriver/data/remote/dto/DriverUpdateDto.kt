package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 司機補行程資料 — request body
 * 對應後端 PATCH /api/orders/:orderId/driver-update
 *
 * 三種更新至少帶一種，可同時帶：
 *   - dest：補/改目的地
 *   - special_notes：補備註
 *   - waypoints：完整中途停靠點陣列（送出「既有 + 新點」整組）
 *   - reason：可選，本次更新原因
 */
data class DriverUpdateRequest(
    @SerializedName("driverId")
    val driverId: String,
    @SerializedName("dest")
    val dest: DriverUpdateDest? = null,
    @SerializedName("special_notes")
    val specialNotes: String? = null,
    @SerializedName("waypoints")
    val waypoints: List<DriverUpdateWaypoint>? = null,
    @SerializedName("reason")
    val reason: String? = null
)

/**
 * 目的地（補/改目的地用）
 */
data class DriverUpdateDest(
    @SerializedName("address")
    val address: String,
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double
)

/**
 * 中途停靠點（送出用，無 sequence — 後端依陣列順序排）
 */
data class DriverUpdateWaypoint(
    @SerializedName("address")
    val address: String,
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double,
    @SerializedName("note")
    val note: String? = null
)

/**
 * 司機補行程資料 — response
 * { success: true, order: { ... } }
 */
data class DriverUpdateResponse(
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("order")
    val order: DriverUpdateOrder? = null,
    @SerializedName("error")
    val error: String? = null
)

/**
 * 後端回傳的精簡訂單（補資料後的最新狀態）
 */
data class DriverUpdateOrder(
    @SerializedName("orderId")
    val orderId: String,
    @SerializedName("destination")
    val destination: LocationDetailDto? = null,
    @SerializedName("destinationConfirmed")
    val destinationConfirmed: Boolean? = null,
    @SerializedName("dropoffOriginal")
    val dropoffOriginal: String? = null,
    @SerializedName("dropoffFinal")
    val dropoffFinal: String? = null,
    @SerializedName("specialNotes")
    val specialNotes: String? = null,
    @SerializedName("waypoints")
    val waypoints: List<WaypointDto>? = null
)
