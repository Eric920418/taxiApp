package com.hualien.taxidriver.data.repository

import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.domain.model.Order

/**
 * 訂單Repository
 */
class OrderRepository {

    private val apiService = RetrofitClient.apiService

    /**
     * 接受訂單
     */
    suspend fun acceptOrder(
        orderId: String,
        driverId: String,
        driverName: String
    ): Result<Order> {
        return try {
            val response = apiService.acceptOrder(
                orderId = orderId,
                body = mapOf(
                    "driverId" to driverId,
                    "driverName" to driverName
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val orderResponse = response.body()!!
                Result.success(orderResponse.order)
            } else {
                Result.failure(Exception("接單失敗：${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 拒絕訂單
     */
    suspend fun rejectOrder(
        orderId: String,
        driverId: String,
        reason: String
    ): Result<Unit> {
        return try {
            val response = apiService.rejectOrder(
                orderId = orderId,
                body = mapOf(
                    "driverId" to driverId,
                    "reason" to reason
                )
            )

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("拒單失敗：${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 更新訂單狀態
     */
    suspend fun updateOrderStatus(
        orderId: String,
        status: String
    ): Result<Order> {
        return try {
            val response = apiService.updateOrderStatus(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.UpdateOrderStatusRequest(
                    status = status,
                    driverId = null // 可選
                )
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("狀態更新失敗：${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 提交車資
     */
    suspend fun submitFare(
        orderId: String,
        meterAmount: Int,
        appDistanceMeters: Int?,
        appDurationSeconds: Int?
    ): Result<Order> {
        return try {
            val response = apiService.submitFare(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.SubmitFareRequest(
                    meterAmount = meterAmount,
                    appDistanceMeters = appDistanceMeters ?: 0,
                    appDurationSeconds = appDurationSeconds ?: 0,
                    photoUrl = null // TODO: 未來實作拍照功能
                )
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("車資提交失敗：${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 取得訂單列表
     */
    suspend fun getOrders(driverId: String): Result<List<Order>> {
        return try {
            val response = apiService.getOrders(driverId = driverId)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.orders)
            } else {
                Result.failure(Exception("取得訂單失敗"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 更新司機狀態
     */
    suspend fun updateDriverStatus(
        driverId: String,
        status: com.hualien.taxidriver.domain.model.DriverAvailability
    ): Result<com.hualien.taxidriver.domain.model.Driver> {
        return try {
            android.util.Log.d("OrderRepository", "========== 更新司機狀態API ==========")
            android.util.Log.d("OrderRepository", "司機ID: $driverId")
            android.util.Log.d("OrderRepository", "狀態: $status")

            val response = apiService.updateDriverStatus(
                driverId = driverId,
                request = com.hualien.taxidriver.data.remote.dto.UpdateStatusRequest(
                    availability = status.name
                )
            )

            android.util.Log.d("OrderRepository", "HTTP狀態碼: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                android.util.Log.d("OrderRepository", "✅ 狀態更新成功")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "狀態更新失敗：HTTP ${response.code()}"
                android.util.Log.e("OrderRepository", "❌ $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("OrderRepository", "❌ 網路錯誤: ${e.message}")
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }
}
