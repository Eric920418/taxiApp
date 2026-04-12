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
    val paymentType: String = "CASH",
    // 乘客端計算的道路距離和車資（Google Directions API）
    @SerializedName("tripDistanceMeters")
    val tripDistanceMeters: Int? = null,
    @SerializedName("estimatedFare")
    val estimatedFare: Int? = null,
    // 愛心卡/敬老卡補貼類型
    @SerializedName("subsidyType")
    val subsidyType: String = "NONE"
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
 * 注意：日期欄位為 ISO 8601 格式字串（如 "2026-01-01T10:27:19.735Z"）
 * 此 DTO 用於接收後端 API 響應，然後轉換為 domain Order 模型
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
    val paymentType: String? = null,
    @SerializedName("fare")
    val fare: FareDto? = null,
    @SerializedName("distance")
    val distance: Double? = null,

    // === 日期欄位 (ISO 8601 格式) ===
    @SerializedName("createdAt")
    val createdAt: String? = null,
    @SerializedName("acceptedAt")
    val acceptedAt: String? = null,
    @SerializedName("arrivedAt")
    val arrivedAt: String? = null,
    @SerializedName("startedAt")
    val startedAt: String? = null,
    @SerializedName("completedAt")
    val completedAt: String? = null,

    // === 取消相關 ===
    @SerializedName("cancelledBy")
    val cancelledBy: String? = null,

    // === 訂單派發時的距離和預估時間資訊 ===
    @SerializedName("distanceToPickup")
    val distanceToPickup: Double? = null,
    @SerializedName("etaToPickup")
    val etaToPickup: Int? = null,
    @SerializedName("tripDistance")
    val tripDistance: Double? = null,
    @SerializedName("estimatedTripDuration")
    val estimatedTripDuration: Int? = null,

    // === 智能派單系統 V2 新增欄位 ===
    @SerializedName("batchNumber")
    val batchNumber: Int? = null,
    @SerializedName("estimatedFare")
    val estimatedFare: Int? = null,
    @SerializedName("googleEtaSeconds")
    val googleEtaSeconds: Int? = null,
    @SerializedName("responseDeadline")
    val responseDeadline: Long? = null,
    @SerializedName("dispatchMethod")
    val dispatchMethod: String? = null,

    // === 1+1 疊單相容欄位 ===
    @SerializedName("queuePosition")
    val queuePosition: Int? = null,
    @SerializedName("queuedAfterOrderId")
    val queuedAfterOrderId: String? = null,
    @SerializedName("predictedHandoverAt")
    val predictedHandoverAt: Long? = null,
    @SerializedName("assignmentMode")
    val assignmentMode: String? = null,

    // === 電話叫車系統擴展欄位 ===
    @SerializedName("source")
    val source: String? = null,
    @SerializedName("subsidyType")
    val subsidyType: String? = null,
    @SerializedName("subsidyConfirmed")
    val subsidyConfirmed: Boolean? = null,
    @SerializedName("subsidyAmount")
    val subsidyAmount: Int? = null,
    @SerializedName("petPresent")
    val petPresent: String? = null,
    @SerializedName("petCarrier")
    val petCarrier: String? = null,
    @SerializedName("petNote")
    val petNote: String? = null,
    @SerializedName("customerPhone")
    val customerPhone: String? = null,
    @SerializedName("destinationConfirmed")
    val destinationConfirmed: Boolean? = null,
    @SerializedName("callId")
    val callId: String? = null
) {
    /**
     * 將 ISO 8601 日期字串轉換為時間戳（毫秒）
     */
    fun getCreatedAtTimestamp(): Long? {
        return createdAt?.let { parseIsoDate(it) }
    }

    fun getAcceptedAtTimestamp(): Long? {
        return acceptedAt?.let { parseIsoDate(it) }
    }

    fun getArrivedAtTimestamp(): Long? {
        return arrivedAt?.let { parseIsoDate(it) }
    }

    fun getStartedAtTimestamp(): Long? {
        return startedAt?.let { parseIsoDate(it) }
    }

    fun getCompletedAtTimestamp(): Long? {
        return completedAt?.let { parseIsoDate(it) }
    }

    private fun parseIsoDate(isoDate: String): Long? {
        return try {
            java.time.Instant.parse(isoDate).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 將 DTO 轉換為 domain Order 模型
     */
    fun toDomainOrder(): com.hualien.taxidriver.domain.model.Order {
        return com.hualien.taxidriver.domain.model.Order(
            orderId = orderId,
            passengerId = passengerId ?: "",
            passengerName = passengerName ?: "乘客",
            passengerPhone = passengerPhone,
            driverId = driverId,
            driverName = driverName,
            driverPhone = driverPhone,
            pickup = com.hualien.taxidriver.domain.model.Location(
                latitude = pickup.lat,
                longitude = pickup.lng,
                address = pickup.address
            ),
            destination = destination?.let {
                com.hualien.taxidriver.domain.model.Location(
                    latitude = it.lat,
                    longitude = it.lng,
                    address = it.address
                )
            },
            statusString = status,
            paymentType = try {
                paymentType?.let { com.hualien.taxidriver.domain.model.PaymentType.valueOf(it) }
                    ?: com.hualien.taxidriver.domain.model.PaymentType.CASH
            } catch (e: Exception) {
                com.hualien.taxidriver.domain.model.PaymentType.CASH
            },
            createdAt = getCreatedAtTimestamp() ?: System.currentTimeMillis(),
            acceptedAt = getAcceptedAtTimestamp(),
            arrivedAt = getArrivedAtTimestamp(),
            startedAt = getStartedAtTimestamp(),
            completedAt = getCompletedAtTimestamp(),
            cancelledBy = cancelledBy,
            fare = fare?.let { fareDto ->
                com.hualien.taxidriver.domain.model.Fare(
                    meterAmount = fareDto.meterAmount,
                    appDistanceMeters = fareDto.appDistanceMeters ?: 0
                )
            },
            distanceToPickup = distanceToPickup,
            etaToPickup = etaToPickup,
            tripDistance = tripDistance,
            estimatedTripDuration = estimatedTripDuration,
            batchNumber = batchNumber,
            estimatedFare = estimatedFare,
            googleEtaSeconds = googleEtaSeconds,
            responseDeadline = responseDeadline,
            dispatchMethod = dispatchMethod,
            queuePosition = queuePosition,
            queuedAfterOrderId = queuedAfterOrderId,
            predictedHandoverAt = predictedHandoverAt,
            assignmentMode = assignmentMode,
            source = source ?: "APP",
            subsidyType = subsidyType ?: "NONE",
            subsidyConfirmed = subsidyConfirmed ?: false,
            subsidyAmount = subsidyAmount ?: 0,
            petPresent = petPresent ?: "UNKNOWN",
            petCarrier = petCarrier ?: "UNKNOWN",
            petNote = petNote,
            customerPhone = customerPhone,
            destinationConfirmed = destinationConfirmed ?: false,
            callId = callId
        )
    }
}

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
 * 車資信息
 */
data class FareDto(
    @SerializedName("meterAmount")
    val meterAmount: Int,
    @SerializedName("appDistanceMeters")
    val appDistanceMeters: Int? = null
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

// ==================== 乘客個人資料相關 ====================

/**
 * 乘客個人資料響應
 */
data class PassengerProfileResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("passenger")
    val passenger: PassengerProfileDto?,
    @SerializedName("error")
    val error: String?
)

/**
 * 乘客個人資料
 */
data class PassengerProfileDto(
    @SerializedName("passengerId")
    val passengerId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("email")
    val email: String?,
    @SerializedName("rating")
    val rating: Float,
    @SerializedName("totalTrips")
    val totalTrips: Int,
    @SerializedName("createdAt")
    val createdAt: String? = null  // ISO 8601 格式
)

/**
 * 更新乘客資料請求
 */
data class UpdatePassengerRequest(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("email")
    val email: String? = null
)

// ==================== 評價相關 ====================

/**
 * 乘客評價列表響應
 */
data class PassengerRatingsResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("ratings")
    val ratings: List<PassengerRatingDto>,
    @SerializedName("summary")
    val summary: RatingSummaryDto?,
    @SerializedName("error")
    val error: String?
)

/**
 * 乘客收到的評價
 */
data class PassengerRatingDto(
    @SerializedName("ratingId")
    val ratingId: String,
    @SerializedName("orderId")
    val orderId: String,
    @SerializedName("driverId")
    val driverId: String,
    @SerializedName("driverName")
    val driverName: String,
    @SerializedName("rating")
    val rating: Int,
    @SerializedName("comment")
    val comment: String?,
    @SerializedName("createdAt")
    val createdAt: String? = null,  // ISO 8601 格式
    @SerializedName("tripDate")
    val tripDate: String? = null    // ISO 8601 格式
)

/**
 * 評價統計摘要
 */
data class RatingSummaryDto(
    @SerializedName("averageRating")
    val averageRating: Float,
    @SerializedName("totalRatings")
    val totalRatings: Int,
    @SerializedName("fiveStars")
    val fiveStars: Int,
    @SerializedName("fourStars")
    val fourStars: Int,
    @SerializedName("threeStars")
    val threeStars: Int,
    @SerializedName("twoStars")
    val twoStars: Int,
    @SerializedName("oneStar")
    val oneStar: Int
)
