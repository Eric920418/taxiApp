package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 車資費率配置 DTO
 * 對齊 Server 巢狀結構（day / night / springFestival / loveCardSubsidyAmount）
 */
data class FareConfigResponse(
    val success: Boolean,
    val data: FareConfigData?
)

data class FareConfigData(
    @SerializedName("day") val day: DayFareDto,
    @SerializedName("night") val night: NightFareDto,
    @SerializedName("springFestival") val springFestival: SpringFestivalDto,
    @SerializedName("loveCardSubsidyAmount") val loveCardSubsidyAmount: Int = 73
)

data class DayFareDto(
    @SerializedName("basePrice") val basePrice: Int,
    @SerializedName("baseDistanceMeters") val baseDistanceMeters: Int,
    @SerializedName("jumpDistanceMeters") val jumpDistanceMeters: Int,
    @SerializedName("jumpPrice") val jumpPrice: Int,
    @SerializedName("slowTrafficSeconds") val slowTrafficSeconds: Int,
    @SerializedName("slowTrafficPrice") val slowTrafficPrice: Int
)

data class NightFareDto(
    @SerializedName("basePrice") val basePrice: Int,
    @SerializedName("baseDistanceMeters") val baseDistanceMeters: Int,
    @SerializedName("jumpDistanceMeters") val jumpDistanceMeters: Int,
    @SerializedName("jumpPrice") val jumpPrice: Int,
    @SerializedName("slowTrafficSeconds") val slowTrafficSeconds: Int,
    @SerializedName("slowTrafficPrice") val slowTrafficPrice: Int,
    @SerializedName("startHour") val startHour: Int,
    @SerializedName("endHour") val endHour: Int
)

data class SpringFestivalDto(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("startDate") val startDate: String,  // YYYY-MM-DD
    @SerializedName("endDate") val endDate: String,
    @SerializedName("perTripSurcharge") val perTripSurcharge: Int
)
