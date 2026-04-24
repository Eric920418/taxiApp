package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 司機等候中：請求後端推播 LINE 給客人
 */
data class NotifyWaitingRequest(
    @SerializedName("remainingMinutes")
    val remainingMinutes: Int
)

/**
 * 客人未到：標記 no-show 並取消訂單
 */
data class CancelNoShowRequest(
    @SerializedName("driverId")
    val driverId: String,

    @SerializedName("waitedMinutes")
    val waitedMinutes: Int? = null
)
