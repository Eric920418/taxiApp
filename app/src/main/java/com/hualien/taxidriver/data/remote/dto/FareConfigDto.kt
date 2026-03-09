package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 車資費率配置 DTO
 * 從 Server 取得動態費率配置
 */
data class FareConfigResponse(
    val success: Boolean,
    val data: FareConfigData?
)

data class FareConfigData(
    @SerializedName("basePrice")
    val basePrice: Int,              // 起跳價（元）

    @SerializedName("baseDistanceMeters")
    val baseDistanceMeters: Int,     // 起跳距離（公尺）

    @SerializedName("jumpDistanceMeters")
    val jumpDistanceMeters: Int,     // 每跳距離（公尺）

    @SerializedName("jumpPrice")
    val jumpPrice: Int,              // 每跳價格（元）

    @SerializedName("nightSurchargeRate")
    val nightSurchargeRate: Double,  // 夜間加成比例

    @SerializedName("nightStartHour")
    val nightStartHour: Int,         // 夜間開始時間

    @SerializedName("nightEndHour")
    val nightEndHour: Int            // 夜間結束時間
)
