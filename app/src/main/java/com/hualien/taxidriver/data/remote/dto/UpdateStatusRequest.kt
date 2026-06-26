package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UpdateStatusRequest(
    @SerializedName("availability")
    val availability: String // OFFLINE, REST, AVAILABLE, ON_TRIP
)

/** 「完成訂單後自動排班」開關更新請求 */
data class UpdateAutoQueueRequest(
    @SerializedName("enabled")
    val enabled: Boolean
)
