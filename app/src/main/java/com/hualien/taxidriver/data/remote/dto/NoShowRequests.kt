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

/**
 * 司機請客人重發上車位置（LINE 訂單專用）
 */
data class RequestRelocationRequest(
    @SerializedName("driverId")
    val driverId: String
)
