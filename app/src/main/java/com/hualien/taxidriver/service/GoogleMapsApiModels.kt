package com.hualien.taxidriver.service

import com.google.gson.annotations.SerializedName

/**
 * Google Maps API 共享数据类
 * 用于 Directions API, Distance Matrix API, Geolocation API 等
 */

/**
 * 文本和数值响应（用于距离、时间等）
 */
data class TextValueResponse(
    @SerializedName("text") val text: String,
    @SerializedName("value") val value: Int
)

/**
 * 位置响应（用于经纬度）
 */
data class LocationResponse(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double
)
