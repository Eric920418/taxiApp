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
    val fare: Fare? = null,

    // === 訂單派發時的距離和預估時間資訊 ===

    /** 司機到上車點的距離（公里） */
    @SerializedName("distanceToPickup")
    val distanceToPickup: Double? = null,

    /** 司機到上車點的預估時間（分鐘） */
    @SerializedName("etaToPickup")
    val etaToPickup: Int? = null,

    /** 上車點到目的地的距離（公里），無目的地則為 null */
    @SerializedName("tripDistance")
    val tripDistance: Double? = null,

    /** 行程預估時間（分鐘），無目的地則為 null */
    @SerializedName("estimatedTripDuration")
    val estimatedTripDuration: Int? = null,

    // === 智能派單系統 V2 新增欄位 ===

    /** 第幾批派單（1-5）*/
    @SerializedName("batchNumber")
    val batchNumber: Int? = null,

    /** 預估車資（整數，新台幣）*/
    @SerializedName("estimatedFare")
    val estimatedFare: Int? = null,

    /** Google API 計算的精確 ETA（秒）*/
    @SerializedName("googleEtaSeconds")
    val googleEtaSeconds: Int? = null,

    /** 回應截止時間戳（毫秒），超過此時間訂單將轉給下一批司機 */
    @SerializedName("responseDeadline")
    val responseDeadline: Long? = null,

    /** 派單方式：LAYERED（分層派單）/ BROADCAST（廣播）*/
    @SerializedName("dispatchMethod")
    val dispatchMethod: String? = null,

    // === 1+1 疊單相容欄位 ===

    /** 隊列位置：1=當前單，2=下一單 */
    @SerializedName("queuePosition")
    val queuePosition: Int? = null,

    /** 下一單所依附的前一張訂單 ID */
    @SerializedName("queuedAfterOrderId")
    val queuedAfterOrderId: String? = null,

    /** 預估前單交接時間（毫秒） */
    @SerializedName("predictedHandoverAt")
    val predictedHandoverAt: Long? = null,

    /** 指派模式：SINGLE / STACKED_1P1 */
    @SerializedName("assignmentMode")
    val assignmentMode: String? = null,

    // === 電話叫車系統擴展欄位 ===

    /** 訂單來源：APP / PHONE / LINE */
    @SerializedName("source")
    val source: String? = "APP",

    /** 補貼類型：SENIOR_CARD / LOVE_CARD / PENDING / NONE */
    @SerializedName("subsidyType")
    val subsidyType: String? = "NONE",

    /** 司機是否已確認實體卡片 */
    @SerializedName("subsidyConfirmed")
    val subsidyConfirmed: Boolean = false,

    /** 實際補貼金額（元） */
    @SerializedName("subsidyAmount")
    val subsidyAmount: Int = 0,

    /** 寵物攜帶：YES / NO / UNKNOWN */
    @SerializedName("petPresent")
    val petPresent: String? = "UNKNOWN",

    /** 寵物籠具：YES / NO / UNKNOWN */
    @SerializedName("petCarrier")
    val petCarrier: String? = "UNKNOWN",

    /** 寵物備註 */
    @SerializedName("petNote")
    val petNote: String? = null,

    /** 來電號碼（電話訂單） */
    @SerializedName("customerPhone")
    val customerPhone: String? = null,

    /** 目的地是否已確認（電話訂單需司機確認） */
    @SerializedName("destinationConfirmed")
    val destinationConfirmed: Boolean = false,

    /** 3CX 通話 ID */
    @SerializedName("callId")
    val callId: String? = null
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
     * 是否為 1+1 的下一單
     */
    fun isQueuedOrder(): Boolean {
        return status == OrderStatus.QUEUED || queuePosition == 2 || assignmentMode == "STACKED_1P1" && status == OrderStatus.OFFERED
    }

    /**
     * 是否為目前執行中的主單
     */
    fun isPrimaryOrder(): Boolean = !isQueuedOrder()

    /**
     * 計算行程時長（分鐘）
     */
    fun getTripDurationMinutes(): Int? {
        return if (startedAt != null && completedAt != null) {
            ((completedAt - startedAt) / 60000).toInt()
        } else null
    }

    /**
     * 檢查回應時間是否已過期（智能派單 V2）
     */
    fun isResponseExpired(): Boolean {
        return responseDeadline?.let { deadline ->
            System.currentTimeMillis() > deadline
        } ?: false
    }

    /**
     * 取得剩餘回應時間（秒）
     */
    fun getRemainingResponseSeconds(): Int {
        return responseDeadline?.let { deadline ->
            val remaining = (deadline - System.currentTimeMillis()) / 1000
            maxOf(0, remaining.toInt())
        } ?: 0
    }

    /**
     * 取得 Google ETA 的分鐘表示
     */
    fun getGoogleEtaMinutes(): Int? {
        return googleEtaSeconds?.let { (it / 60).coerceAtLeast(1) }
    }

    /**
     * 下一單預估交接剩餘秒數
     */
    fun getRemainingHandoverSeconds(): Int {
        return predictedHandoverAt?.let { handoverAt ->
            val remaining = (handoverAt - System.currentTimeMillis()) / 1000
            maxOf(0, remaining.toInt())
        } ?: 0
    }

    /**
     * 將下一單升為當前單的本地表示
     */
    fun promoteQueuedToCurrent(): Order {
        return copy(
            statusString = OrderStatus.ACCEPTED.name,
            queuePosition = 1
        )
    }

    // === 電話叫車輔助方法 ===

    /** 是否為電話訂單 */
    fun isPhoneOrder(): Boolean = source == "PHONE"

    /** 是否需要司機確認目的地（電話訂單且尚未確認） */
    fun needsDestinationConfirmation(): Boolean = isPhoneOrder() && !destinationConfirmed

    /** 取得訂單來源顯示名稱 */
    fun getSourceDisplayName(): String = when (source) {
        "PHONE" -> "電話叫車"
        "LINE" -> "LINE 叫車"
        else -> "APP 叫車"
    }

    /** 取得補貼類型顯示名稱 */
    fun getSubsidyDisplayName(): String = when (subsidyType) {
        "SENIOR_CARD" -> "敬老卡"
        "LOVE_CARD" -> "愛心卡"
        "PENDING" -> "待確認"
        else -> ""
    }

    /** 是否為愛心卡訂單且尚未確認卡片 */
    fun needsSubsidyConfirmation(): Boolean =
        subsidyType == "LOVE_CARD" && !subsidyConfirmed

    /** 是否為已確認的補貼訂單 */
    fun hasConfirmedSubsidy(): Boolean =
        subsidyType in listOf("LOVE_CARD", "SENIOR_CARD") && subsidyConfirmed

    /** 取得寵物狀態顯示名稱 */
    fun getPetDisplayName(): String = when {
        petPresent == "YES" && petCarrier == "YES" -> "寵物(有籠)"
        petPresent == "YES" && petCarrier == "NO" -> "寵物(無籠)"
        petPresent == "YES" -> "有寵物"
        petPresent == "UNKNOWN" && source == "PHONE" -> "寵物待確認"
        else -> ""
    }
}
