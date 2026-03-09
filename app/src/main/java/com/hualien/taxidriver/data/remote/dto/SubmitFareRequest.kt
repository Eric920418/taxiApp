package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SubmitFareRequest(
    @SerializedName("meterAmount")
    val meterAmount: Int,

    @SerializedName("distance")
    val distance: Double? = null,  // 公里數（後端期望 distance）

    @SerializedName("duration")
    val duration: Int? = null,  // 分鐘數（後端期望 duration）

    @SerializedName("photoUrl")
    val photoUrl: String? = null
)
