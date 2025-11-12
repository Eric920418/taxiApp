package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UpdateLocationRequest(
    @SerializedName("lat")
    val latitude: Double,

    @SerializedName("lng")
    val longitude: Double,

    @SerializedName("speed")
    val speed: Float = 0f,

    @SerializedName("bearing")
    val bearing: Float = 0f,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
