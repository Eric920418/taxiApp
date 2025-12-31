package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 提交評分請求
 */
data class SubmitRatingRequest(
    @SerializedName("orderId")
    val orderId: String,
    @SerializedName("fromType")
    val fromType: String,  // driver | passenger
    @SerializedName("fromId")
    val fromId: String,
    @SerializedName("toType")
    val toType: String,    // driver | passenger
    @SerializedName("toId")
    val toId: String,
    @SerializedName("rating")
    val rating: Int,       // 1-5
    @SerializedName("comment")
    val comment: String? = null
)

/**
 * 提交評分響應
 */
data class SubmitRatingResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String?,
    @SerializedName("rating")
    val rating: RatingDto?,
    @SerializedName("error")
    val error: String?
)

/**
 * 評分詳情
 */
data class RatingDto(
    @SerializedName("ratingId")
    val ratingId: String,
    @SerializedName("orderId")
    val orderId: String,
    @SerializedName("fromType")
    val fromType: String,
    @SerializedName("fromId")
    val fromId: String,
    @SerializedName("toType")
    val toType: String,
    @SerializedName("toId")
    val toId: String,
    @SerializedName("rating")
    val rating: Int,
    @SerializedName("comment")
    val comment: String?
)

/**
 * 檢查評分響應
 */
data class CheckRatingResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("hasRated")
    val hasRated: Boolean,
    @SerializedName("rating")
    val rating: RatingDto?,
    @SerializedName("error")
    val error: String?
)
