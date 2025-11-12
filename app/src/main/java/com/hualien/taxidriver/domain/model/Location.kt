package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 地理位置資料模型
 */
data class Location(
    @SerializedName("lat")
    val latitude: Double,

    @SerializedName("lng")
    val longitude: Double,

    @SerializedName("address")
    val address: String? = null
) {
    /**
     * 轉換為Google Maps的LatLng
     */
    fun toLatLng() = com.google.android.gms.maps.model.LatLng(latitude, longitude)

    companion object {
        /**
         * 花蓮預設位置
         */
        val DEFAULT = Location(
            latitude = 23.9871,
            longitude = 121.6015,
            address = "花蓮縣"
        )
    }
}
