package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 乘客登錄請求
 */
data class PassengerLoginRequest(
    @SerializedName("phone")
    val phone: String,
    @SerializedName("password")
    val password: String? = null
)

/**
 * 乘客登錄響應
 */
data class PassengerLoginResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("passenger")
    val passenger: PassengerDto?,
    @SerializedName("error")
    val error: String?
)

/**
 * 乘客信息
 */
data class PassengerDto(
    @SerializedName("passengerId")
    val passengerId: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("name")
    val name: String
)

/**
 * 附近司機響應
 */
data class NearbyDriversResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("drivers")
    val drivers: List<NearbyDriverDto>,
    @SerializedName("count")
    val count: Int,
    @SerializedName("error")
    val error: String?
)

/**
 * 附近司機信息
 */
data class NearbyDriverDto(
    @SerializedName("driverId")
    val driverId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("location")
    val location: LocationDto,
    @SerializedName("rating")
    val rating: Float,
    @SerializedName("distance")
    val distance: Int, // 公尺
    @SerializedName("eta")
    val eta: Int // 預估到達時間（分鐘）
)

/**
 * 位置信息
 */
data class LocationDto(
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double,
    @SerializedName("address")
    val address: String? = null
)

/**
 * 叫車請求
 */
data class RideRequest(
    @SerializedName("passengerId")
    val passengerId: String,
    @SerializedName("passengerName")
    val passengerName: String,
    @SerializedName("passengerPhone")
    val passengerPhone: String,
    @SerializedName("pickupLat")
    val pickupLat: Double,
    @SerializedName("pickupLng")
    val pickupLng: Double,
    @SerializedName("pickupAddress")
    val pickupAddress: String,
    @SerializedName("destLat")
    val destLat: Double? = null,
    @SerializedName("destLng")
    val destLng: Double? = null,
    @SerializedName("destAddress")
    val destAddress: String? = null,
    @SerializedName("paymentType")
    val paymentType: String = "CASH"
)

/**
 * 叫車響應
 */
data class RideResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("order")
    val order: OrderDto?,
    @SerializedName("offeredTo")
    val offeredTo: List<String>? = null, // 推送訂單的司機ID列表（可為空）
    @SerializedName("message")
    val message: String?,
    @SerializedName("error")
    val error: String?
)

/**
 * 訂單信息（DTO）
 */
data class OrderDto(
    @SerializedName("orderId")
    val orderId: String,
    @SerializedName("passengerId")
    val passengerId: String? = null,
    @SerializedName("passengerName")
    val passengerName: String? = null,
    @SerializedName("passengerPhone")
    val passengerPhone: String? = null,
    @SerializedName("driverId")
    val driverId: String? = null,
    @SerializedName("driverName")
    val driverName: String? = null,
    @SerializedName("driverPhone")
    val driverPhone: String? = null,
    @SerializedName("pickup")
    val pickup: LocationDetailDto,
    @SerializedName("destination")
    val destination: LocationDetailDto? = null,
    @SerializedName("status")
    val status: String,
    @SerializedName("paymentType")
    val paymentType: String,
    @SerializedName("fare")
    val fare: Double? = null,
    @SerializedName("distance")
    val distance: Double? = null,
    @SerializedName("createdAt")
    val createdAt: Long,
    @SerializedName("acceptedAt")
    val acceptedAt: Long? = null,
    @SerializedName("completedAt")
    val completedAt: Long? = null
)

/**
 * 詳細位置信息（包含地址）
 */
data class LocationDetailDto(
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double,
    @SerializedName("address")
    val address: String
)

/**
 * 取消訂單請求
 */
data class CancelOrderRequest(
    @SerializedName("orderId")
    val orderId: String,
    @SerializedName("passengerId")
    val passengerId: String,
    @SerializedName("reason")
    val reason: String = "乘客取消"
)

/**
 * 取消訂單響應
 */
data class CancelOrderResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String?,
    @SerializedName("error")
    val error: String?
)

/**
 * 訂單歷史響應
 */
data class OrderHistoryResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("orders")
    val orders: List<OrderDto>,
    @SerializedName("total")
    val total: Int,
    @SerializedName("error")
    val error: String?
)
