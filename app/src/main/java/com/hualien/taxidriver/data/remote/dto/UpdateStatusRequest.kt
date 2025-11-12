package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UpdateStatusRequest(
    @SerializedName("availability")
    val availability: String // OFFLINE, REST, AVAILABLE, ON_TRIP
)
