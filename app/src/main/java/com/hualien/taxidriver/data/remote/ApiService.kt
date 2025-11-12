package com.hualien.taxidriver.data.remote

import com.hualien.taxidriver.data.remote.dto.*
import com.hualien.taxidriver.domain.model.DailyEarning
import com.hualien.taxidriver.domain.model.Driver
import com.hualien.taxidriver.domain.model.Order
import retrofit2.Response
import retrofit2.http.*

/**
 * API接口定義
 */
interface ApiService {

    // ==================== 認證相關 ====================

    /**
     * Firebase Phone Auth - 司機端驗證
     */
    @POST("auth/phone-verify-driver")
    suspend fun phoneVerifyDriver(@Body request: PhoneVerifyRequest): Response<DriverPhoneVerifyResponse>

    /**
     * Firebase Phone Auth - 乘客端驗證
     */
    @POST("auth/phone-verify-passenger")
    suspend fun phoneVerifyPassenger(@Body request: PhoneVerifyRequest): Response<PassengerPhoneVerifyResponse>

    // ==================== 司機相關 ====================

    /**
     * 司機登入（已棄用，請改用 phoneVerifyDriver）
     */
    @Deprecated("請改用 Firebase Phone Authentication")
    @POST("drivers/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    /**
     * 更新司機狀態
     */
    @PATCH("drivers/{driverId}/status")
    suspend fun updateDriverStatus(
        @Path("driverId") driverId: String,
        @Body request: UpdateStatusRequest
    ): Response<Driver>

    /**
     * 取得司機資訊
     */
    @GET("drivers/{driverId}")
    suspend fun getDriverInfo(
        @Path("driverId") driverId: String
    ): Response<Driver>

    /**
     * 取得司機收入統計
     */
    @GET("drivers/{driverId}/earnings")
    suspend fun getEarnings(
        @Path("driverId") driverId: String,
        @Query("date") date: String? = null // yyyyMMdd
    ): Response<List<DailyEarning>>

    // ==================== 訂單相關 ====================

    /**
     * 取得訂單列表
     */
    @GET("orders")
    suspend fun getOrders(
        @Query("driverId") driverId: String? = null,
        @Query("status") status: String? = null
    ): Response<OrderListResponse>

    /**
     * 取得單一訂單
     */
    @GET("orders/{orderId}")
    suspend fun getOrder(
        @Path("orderId") orderId: String
    ): Response<Order>

    /**
     * 接受訂單
     */
    @PATCH("orders/{orderId}/accept")
    suspend fun acceptOrder(
        @Path("orderId") orderId: String,
        @Body body: Map<String, String>
    ): Response<AcceptOrderResponse>

    /**
     * 拒絕訂單
     */
    @PATCH("orders/{orderId}/reject")
    suspend fun rejectOrder(
        @Path("orderId") orderId: String,
        @Body body: Map<String, String>
    ): Response<RejectOrderResponse>

    /**
     * 更新訂單狀態
     */
    @PATCH("orders/{orderId}/status")
    suspend fun updateOrderStatus(
        @Path("orderId") orderId: String,
        @Body request: UpdateOrderStatusRequest
    ): Response<Order>

    /**
     * 上傳車資結算
     */
    @POST("orders/{orderId}/fare")
    suspend fun submitFare(
        @Path("orderId") orderId: String,
        @Body request: SubmitFareRequest
    ): Response<Order>

    // ==================== 定位相關 ====================

    /**
     * 更新司機定位
     */
    @POST("drivers/{driverId}/location")
    suspend fun updateLocation(
        @Path("driverId") driverId: String,
        @Body request: UpdateLocationRequest
    ): Response<Unit>
}
