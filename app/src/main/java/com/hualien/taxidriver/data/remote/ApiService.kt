package com.hualien.taxidriver.data.remote

import com.hualien.taxidriver.data.remote.dto.*
import com.hualien.taxidriver.data.remote.dto.EarningsResponse
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
     * 取得司機收入統計（舊版，已棄用）
     */
    @Deprecated("請改用 getEarningsStats")
    @GET("drivers/{driverId}/earnings")
    suspend fun getEarnings(
        @Path("driverId") driverId: String,
        @Query("date") date: String? = null // yyyyMMdd
    ): Response<List<DailyEarning>>

    /**
     * 取得司機收入統計（新版）
     * @param period today|week|month
     */
    @GET("earnings/{driverId}")
    suspend fun getEarningsStats(
        @Path("driverId") driverId: String,
        @Query("period") period: String = "today"
    ): Response<EarningsResponse>

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
     * 智能派單 V2：必須提供 rejectionReason
     */
    @PATCH("orders/{orderId}/reject")
    suspend fun rejectOrder(
        @Path("orderId") orderId: String,
        @Body request: RejectOrderRequest
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

    // ==================== 評分相關 ====================

    /**
     * 提交評分
     */
    @POST("ratings")
    suspend fun submitRating(
        @Body request: SubmitRatingRequest
    ): Response<SubmitRatingResponse>

    /**
     * 檢查是否已評分
     */
    @GET("ratings/check/{orderId}/{fromType}/{fromId}")
    suspend fun checkRating(
        @Path("orderId") orderId: String,
        @Path("fromType") fromType: String,
        @Path("fromId") fromId: String
    ): Response<CheckRatingResponse>

    // ==================== FCM Token 相關 ====================

    /**
     * 更新司機 FCM Token
     */
    @PUT("drivers/{driverId}/fcm-token")
    suspend fun updateFcmToken(
        @Path("driverId") driverId: String,
        @Body request: UpdateFcmTokenRequest
    ): Response<UpdateFcmTokenResponse>

    /**
     * 刪除司機 FCM Token（登出時使用）
     */
    @DELETE("drivers/{driverId}/fcm-token")
    suspend fun deleteFcmToken(
        @Path("driverId") driverId: String
    ): Response<DeleteFcmTokenResponse>

    // ==================== 語音助理相關 ====================

    /**
     * 語音轉錄 + 意圖解析
     * 使用 multipart/form-data 上傳音檔
     */
    @Multipart
    @POST("whisper/transcribe")
    suspend fun transcribeAudio(
        @Part audio: okhttp3.MultipartBody.Part,
        @Part("driverId") driverId: okhttp3.RequestBody,
        @Part("currentStatus") currentStatus: okhttp3.RequestBody,
        @Part("currentOrderId") currentOrderId: okhttp3.RequestBody?,
        @Part("currentOrderStatus") currentOrderStatus: okhttp3.RequestBody?,
        @Part("pickupAddress") pickupAddress: okhttp3.RequestBody?,
        @Part("destinationAddress") destinationAddress: okhttp3.RequestBody?
    ): Response<VoiceTranscribeResponse>

    /**
     * 查詢語音服務用量
     */
    @GET("whisper/usage")
    suspend fun getVoiceUsage(): Response<VoiceUsageResponse>

    /**
     * 語音服務健康檢查
     */
    @GET("whisper/health")
    suspend fun checkVoiceHealth(): Response<Map<String, Any>>
}
