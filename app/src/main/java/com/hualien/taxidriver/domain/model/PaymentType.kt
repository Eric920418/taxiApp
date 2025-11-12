package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 支付方式
 */
enum class PaymentType {
    @SerializedName("CASH")
    CASH,

    @SerializedName("LOVE_CARD_PHYSICAL")
    LOVE_CARD_PHYSICAL,

    @SerializedName("OTHER")
    OTHER;

    fun getDisplayName(): String = when (this) {
        CASH -> "現金"
        LOVE_CARD_PHYSICAL -> "愛心卡"
        OTHER -> "其他"
    }

    fun getIcon(): String = when (this) {
        CASH -> "💵"
        LOVE_CARD_PHYSICAL -> "💳"
        OTHER -> "💰"
    }
}
