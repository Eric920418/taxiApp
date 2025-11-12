package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 司機狀態
 */
enum class DriverAvailability {
    @SerializedName("OFFLINE")
    OFFLINE,

    @SerializedName("REST")
    REST,

    @SerializedName("AVAILABLE")
    AVAILABLE,

    @SerializedName("ON_TRIP")
    ON_TRIP;

    fun getDisplayName(): String = when (this) {
        OFFLINE -> "離線"
        REST -> "休息中"
        AVAILABLE -> "可接單"
        ON_TRIP -> "載客中"
    }

    fun getColor(): androidx.compose.ui.graphics.Color = when (this) {
        OFFLINE -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // Gray
        REST -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
        AVAILABLE -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
        ON_TRIP -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
    }

    /**
     * 是否可以接單
     */
    fun canReceiveOrders(): Boolean = this == AVAILABLE
}
