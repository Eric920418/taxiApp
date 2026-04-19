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

    /**
     * 查詢乘客個人資料
     * GET /api/passengers/:passengerId
     */
    @GET("passengers/{passengerId}")
    suspend fun getPassengerProfile(
        @Path("passengerId") passengerId: String
    ): Response<PassengerProfileResponse>

    /**
     * 更新乘客個人資料
     * PATCH /api/passengers/:passengerId
     */
    @PATCH("passengers/{passengerId}")
    suspend fun updatePassengerProfile(
        @Path("passengerId") passengerId: String,
        @Body request: UpdatePassengerRequest
    ): Response<PassengerProfileResponse>

    /**
     * 獲取乘客的評價列表
     * GET /api/ratings/passenger/:passengerId
     */
    @GET("ratings/passenger/{passengerId}")
    suspend fun getPassengerRatings(
        @Path("passengerId") passengerId: String
    ): Response<PassengerRatingsResponse>

    /**
     * 乘客對司機評分
     * POST /api/ratings
     */
    @POST("ratings")
    suspend fun submitRating(
        @Body request: SubmitRatingRequest
    ): Response<SubmitRatingResponse>

    // ==================== 語音助理相關 ====================

    /**
     * 乘客端語音轉錄 + 意圖解析
     * 使用 multipart/form-data 上傳音檔
     * POST /api/whisper/transcribe-passenger
     */
    @Multipart
    @POST("whisper/transcribe-passenger")
    suspend fun transcribePassengerAudio(
        @Part audio: okhttp3.MultipartBody.Part,
        @Part("passengerId") passengerId: okhttp3.RequestBody,
        @Part("hasActiveOrder") hasActiveOrder: okhttp3.RequestBody,
        @Part("orderStatus") orderStatus: okhttp3.RequestBody?,
        @Part("currentPickupAddress") currentPickupAddress: okhttp3.RequestBody?,
        @Part("currentDestinationAddress") currentDestinationAddress: okhttp3.RequestBody?,
        @Part("driverName") driverName: okhttp3.RequestBody?,
        @Part("driverPhone") driverPhone: okhttp3.RequestBody?
    ): Response<PassengerVoiceTranscribeResponse>

    /**
     * 同步地標清單（公開讀，不需 token）
     * GET /api/landmarks/sync?since=<timestamp>
     */
    @GET("landmarks/sync")
    suspend fun syncLandmarks(
        @Query("since") since: String? = null
    ): Response<LandmarkSyncResponse>
}
