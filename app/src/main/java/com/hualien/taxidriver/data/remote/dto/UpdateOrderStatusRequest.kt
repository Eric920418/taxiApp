package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UpdateOrderStatusRequest(
    @SerializedName("status")
    val status: String, // ACCEPTED, ARRIVED, ON_TRIP, SETTLING, DONE, CANCELLED

    @SerializedName("driverId")
    val driverId: String? = null
)
