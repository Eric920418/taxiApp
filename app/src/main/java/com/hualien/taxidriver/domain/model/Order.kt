package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 訂單資料模型
 */
data class Order(
    @SerializedName("orderId")
    val orderId: String,

    @SerializedName("passengerId")
    val passengerId: String,

    @SerializedName("passengerName")
    val passengerName: String = "乘客",

    @SerializedName("passengerPhone")
    val passengerPhone: String? = null,

    @SerializedName("driverId")
    val driverId: String? = null,

    @SerializedName("driverName")
    val driverName: String? = null,

    @SerializedName("driverPhone")
    val driverPhone: String? = null,

    @SerializedName("pickup")
    val pickup: Location,

    @SerializedName("destination")
    val destination: Location? = null,

    @SerializedName("status")
    val statusString: String = "WAITING",

    @SerializedName("paymentType")
    val paymentType: PaymentType = PaymentType.CASH,

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("acceptedAt")
    val acceptedAt: Long? = null,

    @SerializedName("arrivedAt")
    val arrivedAt: Long? = null,

    @SerializedName("startedAt")
    val startedAt: Long? = null,

    @SerializedName("completedAt")
    val completedAt: Long? = null,

    @SerializedName("cancelledBy")
    val cancelledBy: String? = null,

    @SerializedName("fare")
    val fare: Fare? = null
) {
    /**
     * 取得狀態enum
     */
    val status: OrderStatus
        get() = OrderStatus.fromString(statusString)

    /**
     * 是否可以接單
     */
    fun canAccept(): Boolean = status == OrderStatus.OFFERED

    /**
     * 是否可以拒單
     */
    fun canReject(): Boolean = status == OrderStatus.OFFERED

    /**
     * 是否可以標記已到達
     */
    fun canMarkArrived(): Boolean = status == OrderStatus.ACCEPTED

    /**
     * 是否可以開始行程
     */
    fun canStartTrip(): Boolean = status == OrderStatus.ARRIVED

    /**
     * 是否可以結束行程
     */
    fun canEndTrip(): Boolean = status == OrderStatus.ON_TRIP

    /**
     * 是否可以結算
     */
    fun canSettle(): Boolean = status == OrderStatus.SETTLING

    /**
     * 計算行程時長（分鐘）
     */
    fun getTripDurationMinutes(): Int? {
        return if (startedAt != null && completedAt != null) {
            ((completedAt - startedAt) / 60000).toInt()
        } else null
    }
}
