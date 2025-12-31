package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * FCM Token 更新請求
 */
data class UpdateFcmTokenRequest(
    @SerializedName("fcmToken")
    val fcmToken: String,
    @SerializedName("deviceInfo")
    val deviceInfo: DeviceInfo? = null
)

/**
 * 設備資訊
 */
data class DeviceInfo(
    @SerializedName("model")
    val model: String,
    @SerializedName("os")
    val os: String = "Android",
    @SerializedName("version")
    val version: String
)

/**
 * FCM Token 更新響應
 */
data class UpdateFcmTokenResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String?,
    @SerializedName("driverId")
    val driverId: String?,
    @SerializedName("updatedAt")
    val updatedAt: String?,
    @SerializedName("error")
    val error: String?
)

/**
 * FCM Token 刪除響應
 */
data class DeleteFcmTokenResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String?,
    @SerializedName("error")
    val error: String?
)
