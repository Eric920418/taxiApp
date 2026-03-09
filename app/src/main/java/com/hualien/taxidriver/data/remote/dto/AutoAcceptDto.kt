package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 自動接單設定
 */
data class AutoAcceptSettings(
    @SerializedName("enabled")
    val enabled: Boolean = false,
    @SerializedName("maxPickupDistanceKm")
    val maxPickupDistanceKm: Double = 5.0,
    @SerializedName("minFareAmount")
    val minFareAmount: Int = 0,
    @SerializedName("minTripDistanceKm")
    val minTripDistanceKm: Double = 0.0,
    @SerializedName("activeHours")
    val activeHours: List<Int> = emptyList(),
    @SerializedName("blacklistedZones")
    val blacklistedZones: List<String> = emptyList(),
    @SerializedName("smartModeEnabled")
    val smartModeEnabled: Boolean = true,
    @SerializedName("autoAcceptThreshold")
    val autoAcceptThreshold: Int = 70,
    @SerializedName("dailyAutoAcceptLimit")
    val dailyAutoAcceptLimit: Int = 30,
    @SerializedName("cooldownMinutes")
    val cooldownMinutes: Int = 5,
    @SerializedName("consecutiveLimit")
    val consecutiveLimit: Int = 5
)

/**
 * 自動接單設定響應
 */
data class AutoAcceptSettingsResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("settings")
    val settings: AutoAcceptSettings?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("error")
    val error: String?
)

/**
 * 更新自動接單設定請求
 */
data class UpdateAutoAcceptSettingsRequest(
    @SerializedName("enabled")
    val enabled: Boolean? = null,
    @SerializedName("maxPickupDistanceKm")
    val maxPickupDistanceKm: Double? = null,
    @SerializedName("minFareAmount")
    val minFareAmount: Int? = null,
    @SerializedName("minTripDistanceKm")
    val minTripDistanceKm: Double? = null,
    @SerializedName("activeHours")
    val activeHours: List<Int>? = null,
    @SerializedName("blacklistedZones")
    val blacklistedZones: List<String>? = null,
    @SerializedName("smartModeEnabled")
    val smartModeEnabled: Boolean? = null,
    @SerializedName("autoAcceptThreshold")
    val autoAcceptThreshold: Int? = null,
    @SerializedName("dailyAutoAcceptLimit")
    val dailyAutoAcceptLimit: Int? = null,
    @SerializedName("cooldownMinutes")
    val cooldownMinutes: Int? = null,
    @SerializedName("consecutiveLimit")
    val consecutiveLimit: Int? = null
)

/**
 * 自動接單統計
 */
data class AutoAcceptStats(
    @SerializedName("today")
    val today: TodayAutoAcceptStats,
    @SerializedName("last7Days")
    val last7Days: WeeklyAutoAcceptStats
)

/**
 * 今日自動接單統計
 */
data class TodayAutoAcceptStats(
    @SerializedName("autoAcceptCount")
    val autoAcceptCount: Int = 0,
    @SerializedName("manualAcceptCount")
    val manualAcceptCount: Int = 0,
    @SerializedName("blockedCount")
    val blockedCount: Int = 0,
    @SerializedName("consecutiveAutoAccepts")
    val consecutiveAutoAccepts: Int = 0,
    @SerializedName("lastAutoAcceptAt")
    val lastAutoAcceptAt: String? = null
)

/**
 * 過去七天自動接單統計
 */
data class WeeklyAutoAcceptStats(
    @SerializedName("totalAutoAccepts")
    val totalAutoAccepts: Int = 0,
    @SerializedName("totalManual")
    val totalManual: Int = 0,
    @SerializedName("totalBlocked")
    val totalBlocked: Int = 0,
    @SerializedName("avgScore")
    val avgScore: Double = 0.0,
    @SerializedName("completionRate")
    val completionRate: Int = 0
)

/**
 * 自動接單統計響應
 */
data class AutoAcceptStatsResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("stats")
    val stats: AutoAcceptStats?,
    @SerializedName("error")
    val error: String?
)

/**
 * 訂單中的自動接單資訊
 */
data class OrderAutoAcceptInfo(
    @SerializedName("score")
    val score: Double = 0.0,
    @SerializedName("allowed")
    val allowed: Boolean = false,
    @SerializedName("blockReason")
    val blockReason: String? = null
)

/**
 * 訂單中的熱區資訊
 */
data class OrderHotZoneInfo(
    @SerializedName("zoneName")
    val zoneName: String,
    @SerializedName("surgeMultiplier")
    val surgeMultiplier: Double = 1.0
)
