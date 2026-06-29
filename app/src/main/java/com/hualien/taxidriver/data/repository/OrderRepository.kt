package com.hualien.taxidriver.data.repository

import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.RejectOrderRequest
import com.hualien.taxidriver.domain.model.Order
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

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
                // 將 OrderDto 轉換為 domain Order
                Result.success(orderResponse.order.toDomainOrder())
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
                request = RejectOrderRequest(
                    driverId = driverId,
                    rejectionReason = reason
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
                // 將 OrderDto 轉換為 domain Order
                Result.success(response.body()!!.toDomainOrder())
            } else {
                Result.failure(Exception("狀態更新失敗：${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 司機等候中：推播 LINE 提醒客人剩 N 分鐘會自動取消
     */
    suspend fun notifyPassengerWaiting(orderId: String, remainingMinutes: Int): Result<Unit> {
        return try {
            val response = apiService.notifyPassengerWaiting(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.NotifyWaitingRequest(
                    remainingMinutes = remainingMinutes
                )
            )
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("等候提醒推播失敗：${response.message()}"))
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 司機聯絡客人 — 依 source 自動分派通道
     * 回完整 response 給 UI 判斷 (channel='TEL' / passengerPhone != null → Intent.DIAL)
     */
    suspend fun contactPassenger(
        orderId: String,
        driverId: String,
        message: String,
        preset: String? = null,
    ): Result<com.hualien.taxidriver.data.remote.dto.ContactPassengerResponse> {
        return try {
            val response = apiService.contactPassenger(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.ContactPassengerRequest(
                    driverId = driverId,
                    message = message,
                    preset = preset,
                )
            )
            // 即使 HTTP 422 (APP 離線 fallback) 也要拿到 body 處理 passengerPhone
            val body = response.body()
            if (body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("聯絡客人失敗：${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 模組 4：上傳「找不到客人」拍照存證
     * 必須在 cancelNoShow 之前呼叫；後端會驗 order.status === 'ARRIVED'
     */
    suspend fun uploadNoShowEvidence(
        orderId: String,
        driverId: String,
        photoFile: java.io.File,
        gpsLat: Double?,
        gpsLng: Double?,
        waitedMinutes: Int?,
        notes: String?,
    ): Result<com.hualien.taxidriver.data.remote.dto.NoShowEvidenceResponse> {
        return try {
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestFile = photoFile.asRequestBody(mediaType)
            val photoPart = okhttp3.MultipartBody.Part.createFormData("photo", photoFile.name, requestFile)
            val plainText = "text/plain".toMediaTypeOrNull()
            val driverIdPart = driverId.toRequestBody(plainText)
            val latPart = gpsLat?.toString()?.toRequestBody(plainText)
            val lngPart = gpsLng?.toString()?.toRequestBody(plainText)
            val waitedPart = waitedMinutes?.toString()?.toRequestBody(plainText)
            val notesPart = notes?.takeIf { it.isNotBlank() }?.toRequestBody(plainText)

            val response = apiService.uploadNoShowEvidence(
                orderId = orderId,
                photo = photoPart,
                driverId = driverIdPart,
                gpsLat = latPart,
                gpsLng = lngPart,
                waitedMinutes = waitedPart,
                notes = notesPart,
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                val errBody = response.errorBody()?.string() ?: "上傳失敗：${response.code()}"
                Result.failure(Exception(errBody))
            }
        } catch (e: Exception) {
            Result.failure(Exception("拍照上傳網路錯誤：${e.message}"))
        }
    }

    /**
     * 客人未到：取消訂單並記錄 no-show
     */
    suspend fun cancelNoShow(
        orderId: String,
        driverId: String,
        waitedMinutes: Int?
    ): Result<Unit> {
        return try {
            val response = apiService.cancelNoShow(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.CancelNoShowRequest(
                    driverId = driverId,
                    waitedMinutes = waitedMinutes
                )
            )
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("標記 no-show 失敗：${response.message()}"))
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 司機請 LINE 客人重發上車位置
     */
    suspend fun requestRelocation(
        orderId: String,
        driverId: String,
        suggestLandmark: Boolean = false,
    ): Result<com.hualien.taxidriver.data.remote.dto.RequestRelocationResponse> {
        return try {
            val response = apiService.requestRelocation(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.RequestRelocationRequest(
                    driverId = driverId,
                    suggestLandmark = suggestLandmark
                )
            )
            if (response.isSuccessful) {
                Result.success(
                    response.body()
                        ?: com.hualien.taxidriver.data.remote.dto.RequestRelocationResponse(success = true)
                )
            } else {
                val errBody = response.errorBody()?.string()
                Result.failure(Exception(errBody ?: "請求失敗：${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 愛心卡確認/取消
     */
    suspend fun updateOrderSubsidy(
        orderId: String,
        driverId: String,
        action: String // "CONFIRM" or "CANCEL"
    ): Result<Order> {
        return try {
            val response = apiService.updateOrderSubsidy(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.UpdateSubsidyRequest(
                    driverId = driverId,
                    action = action
                )
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.toDomainOrder())
            } else {
                Result.failure(Exception("愛心卡操作失敗：${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 司機補行程資料（補/改目的地、補備註、中途停靠點）
     *
     * 三種更新至少帶一種，可同時帶；錯誤完整透傳給 UI 顯示。
     */
    suspend fun driverUpdateOrder(
        orderId: String,
        driverId: String,
        dest: com.hualien.taxidriver.data.remote.dto.DriverUpdateDest? = null,
        specialNotes: String? = null,
        waypoints: List<com.hualien.taxidriver.data.remote.dto.DriverUpdateWaypoint>? = null,
        reason: String? = null,
    ): Result<com.hualien.taxidriver.data.remote.dto.DriverUpdateResponse> {
        return try {
            val response = apiService.driverUpdateOrder(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.DriverUpdateRequest(
                    driverId = driverId,
                    dest = dest,
                    specialNotes = specialNotes,
                    waypoints = waypoints,
                    reason = reason,
                )
            )
            val body = response.body()
            if (response.isSuccessful && body != null && body.success) {
                Result.success(body)
            } else {
                // 完整透傳後端錯誤訊息（{ error }）
                val errBody = response.errorBody()?.string()
                val errMsg = errBody
                    ?.let { runCatching { com.google.gson.Gson().fromJson(it, com.hualien.taxidriver.data.remote.dto.DriverUpdateResponse::class.java)?.error }.getOrNull() }
                    ?: body?.error
                    ?: "補行程資料失敗：HTTP ${response.code()}"
                Result.failure(Exception(errMsg))
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
        distanceKm: Double? = null,
        durationMinutes: Int? = null
    ): Result<Order> {
        return try {
            val response = apiService.submitFare(
                orderId = orderId,
                request = com.hualien.taxidriver.data.remote.dto.SubmitFareRequest(
                    meterAmount = meterAmount,
                    distance = distanceKm,
                    duration = durationMinutes,
                    photoUrl = null // TODO: 未來實作拍照功能
                )
            )

            if (response.isSuccessful && response.body() != null) {
                // 將 OrderDto 轉換為 domain Order
                Result.success(response.body()!!.toDomainOrder())
            } else {
                Result.failure(Exception("車資提交失敗：${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("網路錯誤：${e.message}"))
        }
    }

    /**
     * 取得單一訂單（背景接單補抓用 — 點通知/開回 App 時用 orderId 補抓該單顯示卡片）
     */
    suspend fun getOrderById(orderId: String): Result<Order> {
        return try {
            val response = apiService.getOrder(orderId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.toDomainOrder())
            } else {
                Result.failure(Exception("取得訂單失敗：HTTP ${response.code()}"))
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
                // 將 OrderDto 列表轉換為 domain Order 列表
                val orders = response.body()!!.orders.map { it.toDomainOrder() }
                Result.success(orders)
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
