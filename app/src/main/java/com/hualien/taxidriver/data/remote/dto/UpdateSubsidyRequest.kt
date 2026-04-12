package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UpdateSubsidyRequest(
    @SerializedName("driverId")
    val driverId: String,

    @SerializedName("action")
    val action: String // "CONFIRM" or "CANCEL"
)
