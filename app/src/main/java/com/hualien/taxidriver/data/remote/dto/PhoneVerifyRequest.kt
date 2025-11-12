package com.hualien.taxidriver.data.remote.dto

/**
 * Firebase Phone 驗證請求
 */
data class PhoneVerifyRequest(
    val phone: String,
    val firebaseUid: String,
    val name: String? = null
)

/**
 * 司機端登入回應
 */
data class DriverPhoneVerifyResponse(
    val success: Boolean,
    val token: String,
    val driverId: String,
    val name: String,
    val phone: String,
    val plate: String,
    val availability: String? = null,
    val rating: Float? = null,
    val totalTrips: Int? = null
)

/**
 * 乘客端登入回應
 */
data class PassengerPhoneVerifyResponse(
    val success: Boolean,
    val passengerId: String,
    val name: String,
    val phone: String,
    val totalRides: Int? = null,
    val rating: Float? = null
)
