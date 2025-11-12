package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SubmitFareRequest(
    @SerializedName("meterAmount")
    val meterAmount: Int,

    @SerializedName("appDistanceMeters")
    val appDistanceMeters: Int,

    @SerializedName("appDurationSeconds")
    val appDurationSeconds: Int,

    @SerializedName("photoUrl")
    val photoUrl: String? = null,

    @SerializedName("meterSource")
    val meterSource: String = "MANUAL"
)
