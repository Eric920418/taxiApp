package com.hualien.taxidriver.data.repository

import com.google.android.gms.maps.model.LatLng
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.*
import com.hualien.taxidriver.domain.model.Location
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.viewmodel.NearbyDriver

/**
 * 乘客端 Repository
 * 封裝所有乘客相關的 API 調用
 */
class PassengerRepository {

    private val apiService = RetrofitClient.passengerApiService

    /**
     * 乘客登錄/註冊
     */
    suspend fun login(phone: String, password: String? = null): Result<PassengerInfo> {
        return try {
            val response = apiService.login(
                PassengerLoginRequest(phone = phone, password = password)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val passenger = response.body()!!.passenger
                if (passenger != null) {
                    Result.success(
                        PassengerInfo(
                            passengerId = passenger.passengerId,
                            phone = passenger.phone,
                            name = passenger.name
                        )
                    )
                } else {
                    Result.failure(Exception("登錄失敗：無法獲取用戶信息"))
                }
            } else {
                Result.failure(Exception("登錄失敗：${response.body()?.error ?: response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 查詢附近司機
     */
    suspend fun getNearbyDrivers(
        latitude: Double,
        longitude: Double,
        radius: Int = 5000
    ): Result<List<NearbyDriver>> {
        return try {
            val response = apiService.getNearbyDrivers(
                latitude = latitude,
                longitude = longitude,
                radius = radius
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val drivers = response.body()!!.drivers.map { dto ->
                    NearbyDriver(
                        driverId = dto.driverId,
                        driverName = dto.name,
                        location = LatLng(dto.location.lat, dto.location.lng),
                        rating = dto.rating
                    )
                }
                Result.success(drivers)
            } else {
                Result.failure(Exception("查詢失敗：${response.body()?.error ?: response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 發送叫車請求
     */
    suspend fun requestRide(
        passengerId: String,
        passengerName: String,
        passengerPhone: String,
        pickupLat: Double,
        pickupLng: Double,
        pickupAddress: String,
        destLat: Double? = null,
        destLng: Double? = null,
        destAddress: String? = null,
        paymentType: String = "CASH"
    ): Result<RideRequestResult> {
        return try {
            val request = RideRequest(
                passengerId = passengerId,
                passengerName = passengerName,
                passengerPhone = passengerPhone,
                pickupLat = pickupLat,
                pickupLng = pickupLng,
                pickupAddress = pickupAddress,
                destLat = destLat,
                destLng = destLng,
                destAddress = destAddress,
                paymentType = paymentType
            )

            android.util.Log.d("PassengerRepository", "========== API請求開始 ==========")
            android.util.Log.d("PassengerRepository", "API端點: POST /api/passengers/request-ride")
            android.util.Log.d("PassengerRepository", "請求參數:")
            android.util.Log.d("PassengerRepository", "  - passengerId: $passengerId")
            android.util.Log.d("PassengerRepository", "  - passengerName: $passengerName")
            android.util.Log.d("PassengerRepository", "  - passengerPhone: $passengerPhone")
            android.util.Log.d("PassengerRepository", "  - pickupLat: $pickupLat")
            android.util.Log.d("PassengerRepository", "  - pickupLng: $pickupLng")
            android.util.Log.d("PassengerRepository", "  - pickupAddress: $pickupAddress")
            android.util.Log.d("PassengerRepository", "  - destLat: $destLat")
            android.util.Log.d("PassengerRepository", "  - destLng: $destLng")
            android.util.Log.d("PassengerRepository", "  - destAddress: $destAddress")
            android.util.Log.d("PassengerRepository", "  - paymentType: $paymentType")

            val response = apiService.requestRide(request)

            android.util.Log.d("PassengerRepository", "========== API響應 ==========")
            android.util.Log.d("PassengerRepository", "HTTP狀態碼: ${response.code()}")
            android.util.Log.d("PassengerRepository", "響應成功: ${response.isSuccessful}")

            if (response.isSuccessful) {
                val body = response.body()
                android.util.Log.d("PassengerRepository", "響應body: $body")
                android.util.Log.d("PassengerRepository", "success欄位: ${body?.success}")
                android.util.Log.d("PassengerRepository", "message欄位: ${body?.message}")
                android.util.Log.d("PassengerRepository", "error欄位: ${body?.error}")
                android.util.Log.d("PassengerRepository", "order欄位: ${body?.order}")
                android.util.Log.d("PassengerRepository", "offeredTo欄位: ${body?.offeredTo}")

                if (body?.success == true) {
                    val order = body.order?.let { mapOrderDtoToOrder(it) }

                    if (order != null) {
                        android.util.Log.d("PassengerRepository", "✅ 訂單創建成功")
                        android.util.Log.d("PassengerRepository", "訂單ID: ${order.orderId}")
                        Result.success(
                            RideRequestResult(
                                order = order,
                                offeredToDrivers = body.offeredTo ?: emptyList(),
                                message = body.message ?: "叫車請求已發送"
                            )
                        )
                    } else {
                        android.util.Log.e("PassengerRepository", "❌ 訂單對象為null")
                        Result.failure(Exception("叫車失敗：無法創建訂單"))
                    }
                } else {
                    android.util.Log.e("PassengerRepository", "❌ success=false")
                    Result.failure(Exception("叫車失敗：${body?.error ?: "未知錯誤"}"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("PassengerRepository", "❌ HTTP錯誤: ${response.code()}")
                android.util.Log.e("PassengerRepository", "錯誤訊息: ${response.message()}")
                android.util.Log.e("PassengerRepository", "錯誤body: $errorBody")
                Result.failure(Exception("叫車失敗：HTTP ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("PassengerRepository", "❌ 網路異常")
            android.util.Log.e("PassengerRepository", "異常類型: ${e.javaClass.simpleName}")
            android.util.Log.e("PassengerRepository", "異常訊息: ${e.message}")
            android.util.Log.e("PassengerRepository", "異常堆疊:", e)
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 取消訂單
     */
    suspend fun cancelOrder(
        orderId: String,
        passengerId: String,
        reason: String = "乘客取消"
    ): Result<String> {
        return try {
            val response = apiService.cancelOrder(
                CancelOrderRequest(
                    orderId = orderId,
                    passengerId = passengerId,
                    reason = reason
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.message ?: "訂單已取消")
            } else {
                Result.failure(Exception("取消失敗：${response.body()?.error ?: response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 查詢訂單歷史
     */
    suspend fun getOrderHistory(
        passengerId: String,
        status: String? = null
    ): Result<List<Order>> {
        return try {
            val response = apiService.getOrderHistory(
                passengerId = passengerId,
                status = status
            )

            when {
                response.isSuccessful && response.body()?.success == true -> {
                    val orders = response.body()!!.orders.map { mapOrderDtoToOrder(it) }
                    Result.success(orders)
                }
                response.code() == 404 -> {
                    // 404 Not Found - 新用戶沒有訂單，返回空列表
                    Result.success(emptyList())
                }
                else -> {
                    Result.failure(Exception("查詢失敗：${response.body()?.error ?: response.message()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 將 OrderDto 轉換為 Order Domain Model
     */
    private fun mapOrderDtoToOrder(dto: OrderDto): Order {
        return Order(
            orderId = dto.orderId,
            passengerId = dto.passengerId ?: "",
            passengerName = dto.passengerName ?: "乘客",
            passengerPhone = dto.passengerPhone,
            driverId = dto.driverId,
            driverName = dto.driverName,
            driverPhone = dto.driverPhone,
            pickup = Location(
                latitude = dto.pickup.lat,
                longitude = dto.pickup.lng,
                address = dto.pickup.address
            ),
            destination = dto.destination?.let {
                Location(
                    latitude = it.lat,
                    longitude = it.lng,
                    address = it.address
                )
            },
            statusString = dto.status,
            paymentType = try {
                com.hualien.taxidriver.domain.model.PaymentType.valueOf(dto.paymentType)
            } catch (e: Exception) {
                com.hualien.taxidriver.domain.model.PaymentType.CASH
            },
            fare = dto.fare?.let { fareAmount ->
                com.hualien.taxidriver.domain.model.Fare(
                    meterAmount = fareAmount.toInt(),
                    appDistanceMeters = dto.distance?.times(1000)?.toInt() ?: 0
                )
            },
            createdAt = dto.createdAt,
            acceptedAt = dto.acceptedAt,
            completedAt = dto.completedAt
        )
    }
}

/**
 * 乘客信息
 */
data class PassengerInfo(
    val passengerId: String,
    val phone: String,
    val name: String
)

/**
 * 叫車請求結果
 */
data class RideRequestResult(
    val order: Order,
    val offeredToDrivers: List<String>,
    val message: String
)
