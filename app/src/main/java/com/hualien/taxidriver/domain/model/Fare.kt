package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 車資資料
 */
data class Fare(
    @SerializedName("meterAmount")
    val meterAmount: Int?,

    @SerializedName("appDistanceMeters")
    val appDistanceMeters: Int = 0,

    @SerializedName("appDurationSeconds")
    val appDurationSeconds: Int = 0,

    @SerializedName("photoUrl")
    val photoUrl: String? = null,

    @SerializedName("meterSource")
    val meterSource: String = "MANUAL"
) {
    /**
     * 格式化金額顯示
     */
    fun getFormattedAmount(): String {
        return meterAmount?.let { "NT$ $it" } ?: "未設定"
    }

    /**
     * 格式化距離顯示
     */
    fun getFormattedDistance(): String {
        val km = appDistanceMeters / 1000.0
        return String.format("%.1f km", km)
    }

    /**
     * 格式化時長顯示
     */
    fun getFormattedDuration(): String {
        val minutes = appDurationSeconds / 60
        return "$minutes 分鐘"
    }
}
