package com.hualien.taxidriver.data.repository

import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.dto.QueueJoinRequest
import com.hualien.taxidriver.data.remote.dto.QueueLeaveRequest
import com.hualien.taxidriver.domain.model.QueueMyStatus
import com.hualien.taxidriver.domain.model.QueueZone

/**
 * 排班 API repository
 */
class QueueRepository {
    private val api = RetrofitClient.apiService

    suspend fun getZones(): Result<List<QueueZone>> {
        return try {
            val res = api.getQueueZones()
            if (res.isSuccessful) Result.success(res.body()?.zones ?: emptyList())
            else Result.failure(Exception("載入排班區失敗：${res.message()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyStatus(driverId: String): Result<QueueMyStatus> {
        return try {
            val res = api.getQueueMyStatus(driverId)
            if (res.isSuccessful) Result.success(res.body()!!)
            else Result.failure(Exception("查詢排班狀態失敗：${res.message()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinQueue(
        driverId: String,
        zoneId: String,
        currentLat: Double,
        currentLng: Double,
        maxAcceptableDiscountAmount: Int = 0,
    ): Result<Unit> {
        return try {
            val res = api.joinQueue(
                QueueJoinRequest(driverId, zoneId, currentLat, currentLng, maxAcceptableDiscountAmount)
            )
            if (res.isSuccessful) Result.success(Unit)
            else {
                val errBody = res.errorBody()?.string()
                Result.failure(Exception(errBody ?: "加入排班失敗：${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveQueue(driverId: String, reason: String = "MANUAL"): Result<Unit> {
        return try {
            val res = api.leaveQueue(QueueLeaveRequest(driverId, reason))
            if (res.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("退出排班失敗：${res.message()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDiscount(driverId: String, maxAcceptableAmt: Int): Result<Int> {
        return try {
            val res = api.updateDriverCommission(
                driverId,
                com.hualien.taxidriver.data.remote.dto.UpdateDiscountRequest(maxAcceptableAmt)
            )
            if (res.isSuccessful) Result.success(res.body()?.maxAcceptableDiscountAmount ?: maxAcceptableAmt)
            else {
                val errBody = res.errorBody()?.string()
                Result.failure(Exception(errBody ?: "更新失敗：${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
