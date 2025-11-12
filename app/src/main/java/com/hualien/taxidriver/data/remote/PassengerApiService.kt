package com.hualien.taxidriver.data.remote

import com.hualien.taxidriver.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * 乘客端 API 服務接口
 * 對應服務器的 /api/passengers 路由
 */
interface PassengerApiService {

    /**
     * 乘客登錄/註冊
     * POST /api/passengers/login
     */
    @POST("passengers/login")
    suspend fun login(
        @Body request: PassengerLoginRequest
    ): Response<PassengerLoginResponse>

    /**
     * 查詢附近司機
     * GET /api/passengers/nearby-drivers
     */
    @GET("passengers/nearby-drivers")
    suspend fun getNearbyDrivers(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("radius") radius: Int = 5000
    ): Response<NearbyDriversResponse>

    /**
     * 創建叫車訂單
     * POST /api/passengers/request-ride
     */
    @POST("passengers/request-ride")
    suspend fun requestRide(
        @Body request: RideRequest
    ): Response<RideResponse>

    /**
     * 取消訂單
     * POST /api/passengers/cancel-order
     */
    @POST("passengers/cancel-order")
    suspend fun cancelOrder(
        @Body request: CancelOrderRequest
    ): Response<CancelOrderResponse>

    /**
     * 查詢乘客的訂單歷史
     * GET /api/passengers/:passengerId/orders
     */
    @GET("passengers/{passengerId}/orders")
    suspend fun getOrderHistory(
        @Path("passengerId") passengerId: String,
        @Query("status") status: String? = null
    ): Response<OrderHistoryResponse>
}
